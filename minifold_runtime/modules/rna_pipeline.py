import json
import os
import traceback
import concurrent.futures

from modules.rna_ss_predictor import generate_rna_candidates
from modules.rna_coarse_builder import build_coarse_rna_3d, write_rna_pdb
from modules.rna_metrics import evaluate_rna_model
from modules.ark_module import (
    ark_analyze_rna_sequence,
    ark_refine_rna_structure,
    ark_vote_rna_cases,
    get_default_models,
)


def _deserialize_pair_probabilities(entries):
    probabilities = {}
    for entry in entries or []:
        probabilities[(int(entry["i"]), int(entry["j"]))] = float(entry["p"])
    return probabilities


def _dot_bracket_to_pairs(dot_bracket):
    stack = []
    pairs = []
    for index, char in enumerate(dot_bracket):
        if char == "(":
            stack.append(index)
        elif char == ")" and stack:
            pairs.append((stack.pop(), index))
    pairs.sort()
    return pairs


def _stem_count(pairs):
    ordered = sorted((min(i, j), max(i, j)) for i, j in pairs)
    if not ordered:
        return 0
    stems = 1
    for index in range(1, len(ordered)):
        prev_left, prev_right = ordered[index - 1]
        left, right = ordered[index]
        if left != prev_left + 1 or right != prev_right - 1:
            stems += 1
    return stems


def _ai_consensus_score(votes, case_index):
    for item in votes.get("cases", []):
        if item.get("idx") == case_index:
            return (float(item.get("avg", 0.0)) + float(item.get("med", 0.0))) / 2.0
    return None


def _build_refined_candidate(
    dot_bracket,
    pair_probabilities,
    label,
    env_text,
    base_ai_score=None,
):
    pairs = _dot_bracket_to_pairs(dot_bracket)
    stem_count = _stem_count(pairs)
    if pairs:
        support_values = [
            pair_probabilities.get((min(left, right), max(left, right)), 0.0)
            for left, right in pairs
        ]
        pair_probability_mean = sum(support_values) / max(1, len(support_values))
    else:
        pair_probability_mean = 0.0

    mg_like = "mg" in env_text.lower() or "镁" in env_text
    local_score = len(pairs) * (2.25 if mg_like else 2.0) + stem_count * 1.4 + pair_probability_mean * max(1, len(pairs))
    rank_score = local_score + pair_probability_mean * len(pairs) + (len(pairs) / max(1, stem_count) * 1.4 if pairs else 0.0)

    return {
        "dot_bracket": dot_bracket,
        "pairs": pairs,
        "type": label,
        "score": round(local_score, 4),
        "stemCount": stem_count,
        "source": "ark_refined",
        "pairProbabilityMean": round(pair_probability_mean, 4),
        "rankScore": round(rank_score, 4),
        "pairProbabilities": [
            {"i": left, "j": right, "p": round(pair_probabilities.get((left, right), 0.0), 4)}
            for left, right in sorted(pair_probabilities.keys(), key=lambda key: pair_probabilities[key], reverse=True)
        ],
        "ensembleDiversity": 0.0,
        "aiScore": None if base_ai_score is None else round(float(base_ai_score), 4),
    }


def run_pipeline(
    fasta,
    outdir,
    env_text,
    use_igpu,
    target_chains=None,
    backend="auto",
    log_callback=print,
):
    """
    Core MiniFold RNA pipeline execution logic.
    """
    try:
        os.makedirs(outdir, exist_ok=True)
        log_callback(f"读取 FASTA: {fasta}")
        
        with open(fasta, "r", encoding="utf-8") as f:
            lines = f.readlines()
            
        sequence = ""
        for line in lines:
            if not line.startswith(">"):
                sequence += line.strip()
                
        sequence = sequence.upper().replace("T", "U")
        log_callback(f"处理 RNA 序列: 长度 {len(sequence)}")
        
        input_base = os.path.basename(fasta)
        prefix = os.path.splitext(input_base)[0]
        workdir = os.path.join(outdir, prefix)
        three_d_dir = os.path.join(workdir, "3d_structures")
        os.makedirs(three_d_dir, exist_ok=True)

        req_lines = [f"env={env_text or ''}"]
        if target_chains:
            req_lines.append(f"target_chains={target_chains}")
        req_text = "\n".join(req_lines)
        
        log_callback("生成 RNA 二级结构候选...")
        candidates = generate_rna_candidates(sequence, num=4, env_text=env_text)
        api_key = os.environ.get("ARK_API_KEY", "")
        models = get_default_models()
        local_best = candidates[0]
        votes = {"best_idx": -1, "best_score": 0.0, "cases": []}
        refined_votes = {"best_idx": -1, "best_score": 0.0, "cases": []}
        refined_candidates = []
        annotation = ""

        if api_key:
            log_callback(f"Ark RNA 投票启动：{len(models)} 个模型评审 {len(candidates)} 个候选...")
            votes = ark_vote_rna_cases(models, sequence, env_text, candidates, req_text=req_text, api_key=api_key)
            for index, candidate in enumerate(candidates):
                candidate["aiScore"] = _ai_consensus_score(votes, index)

            votes_path = os.path.join(workdir, f"{prefix}_rna_votes.json")
            with open(votes_path, "w", encoding="utf-8") as f:
                json.dump(votes, f, ensure_ascii=False, indent=2)

            sorted_cases = sorted(
                votes.get("cases", []),
                key=lambda item: (float(item.get("avg", 0.0)) + float(item.get("med", 0.0))) / 2.0,
                reverse=True,
            )
            refine_top_k = max(1, min(3, int(os.environ.get("MINIFOLD_RNA_REFINE_TOP_K", "2"))))
            top_indices = [item["idx"] for item in sorted_cases[:refine_top_k]]
            chief_model = models[0] if models else "doubao-seed-1-6-251015"
            refine_timeout = max(45, int(os.environ.get("MINIFOLD_RNA_REFINE_TIMEOUT", "120")))

            def refine_case(rank, case_index):
                seed_candidate = candidates[case_index]
                log_callback(f"Ark RNA 精修 {rank}/{len(top_indices)}：{chief_model} 正在重审 {seed_candidate['type']}...")
                refined_dot_bracket, refine_err = ark_refine_rna_structure(
                    chief_model,
                    sequence,
                    env_text or "general aqueous ribozyme environment",
                    seed_candidate["dot_bracket"],
                    api_key=api_key,
                    timeout=refine_timeout,
                )
                if not refined_dot_bracket:
                    return None, seed_candidate["type"], refine_err
                if refined_dot_bracket == seed_candidate["dot_bracket"]:
                    return "same", seed_candidate["type"], None
                return (
                    _build_refined_candidate(
                        refined_dot_bracket,
                        _deserialize_pair_probabilities(seed_candidate.get("pairProbabilities")),
                        f"ArkRefined_from_{seed_candidate['type']}",
                        env_text or "",
                        base_ai_score=seed_candidate.get("aiScore"),
                    ),
                    seed_candidate["type"],
                    None,
                )

            if top_indices:
                max_workers = min(len(top_indices), max(1, int(os.environ.get("MINIFOLD_RNA_REFINE_WORKERS", "2"))))
                with concurrent.futures.ThreadPoolExecutor(max_workers=max_workers) as executor:
                    future_map = {
                        executor.submit(refine_case, rank, case_index): (rank, case_index)
                        for rank, case_index in enumerate(top_indices, start=1)
                    }
                    for future in concurrent.futures.as_completed(future_map):
                        result, candidate_type, err = future.result()
                        if result == "same":
                            log_callback(f"  精修结果与原候选一致，已跳过：{candidate_type}")
                            continue
                        if result is None:
                            log_callback(f"  精修失败：{candidate_type} -> {err}")
                            continue
                        refined_candidates.append(result)

            if refined_candidates:
                jury_models = [model for model in models if model != chief_model] or models
                log_callback(f"Ark RNA 重审启动：{len(jury_models)} 个模型评审 {len(refined_candidates)} 个精修候选...")
                refined_votes = ark_vote_rna_cases(
                    jury_models,
                    sequence,
                    env_text,
                    refined_candidates,
                    req_text=req_text,
                    api_key=api_key,
                )
                for index, candidate in enumerate(refined_candidates):
                    candidate["aiScore"] = _ai_consensus_score(refined_votes, index)
                refined_votes_path = os.path.join(workdir, f"{prefix}_rna_refined_votes.json")
                with open(refined_votes_path, "w", encoding="utf-8") as f:
                    json.dump(refined_votes, f, ensure_ascii=False, indent=2)

            annotation = ark_analyze_rna_sequence(sequence, env_text, api_key=api_key)
        else:
            log_callback("Ark API Key 未配置，RNA 流程将仅使用本地候选与几何优化。")

        best_candidate = local_best
        ai_best_score = votes.get("best_score", 0.0) if votes else 0.0
        if votes.get("best_idx", -1) >= 0:
            best_candidate = candidates[votes["best_idx"]]
            ai_best_score = (best_candidate.get("aiScore") or votes.get("best_score", 0.0))
        if refined_candidates and refined_votes.get("best_idx", -1) >= 0:
            refined_best = refined_candidates[refined_votes["best_idx"]]
            refined_ai_score = refined_best.get("aiScore") or refined_votes.get("best_score", 0.0)
            if refined_ai_score >= ai_best_score - 0.03:
                best_candidate = refined_best
                ai_best_score = refined_ai_score

        dot_bracket = best_candidate["dot_bracket"]
        pairs = best_candidate["pairs"]
        pair_probabilities = _deserialize_pair_probabilities(best_candidate.get("pairProbabilities"))
        log_callback(
            f"最佳候选 ({best_candidate['type']}, Local Rank: {best_candidate.get('rankScore', best_candidate.get('score', 0.0))}, "
            f"AI: {ai_best_score:.3f}):\n{dot_bracket}"
        )
        
        log_callback("组装粗粒度 RNA 3D 模型...")
        pdb_name = f"{prefix}_rna_model_1.pdb"
        pdb_path = os.path.join(three_d_dir, pdb_name)
        
        coords = build_coarse_rna_3d(
            sequence,
            dot_bracket,
            pairs,
            pair_probabilities=pair_probabilities,
        )
        log_callback("评估 RNA 几何合理性...")
        metrics = evaluate_rna_model(
            sequence,
            coords,
            pairs,
            dot_bracket,
            env_text,
            pair_probabilities=pair_probabilities,
            ai_confidence=ai_best_score if api_key else None,
        )
        log_callback(
            f"合理性评分: {metrics['plausibilityScore']:.2f}/100 "
            f"({metrics['grade']})"
        )
        write_rna_pdb(sequence, coords, pdb_path, dot_bracket=dot_bracket, pairs=pairs)

        all_candidates = candidates + refined_candidates
        metrics["candidateCount"] = len(all_candidates)
        metrics["selectedCandidate"] = {
            "score": best_candidate["score"],
            "rankScore": best_candidate.get("rankScore", best_candidate["score"]),
            "type": best_candidate["type"],
            "pairProbabilityMean": best_candidate.get("pairProbabilityMean", 0.0),
            "aiScore": round(float(ai_best_score), 4) if api_key else None,
        }
        metrics["ensemble"] = {
            "ensembleDiversity": best_candidate.get("ensembleDiversity", 0.0),
            "topPairProbabilities": best_candidate.get("pairProbabilities", [])[:12],
        }
        metrics["aiOptimization"] = {
            "enabled": bool(api_key),
            "models": models if api_key else [],
            "initialBestScore": round(float(votes.get("best_score", 0.0)), 4),
            "refinedCandidateCount": len(refined_candidates),
            "refinedBestScore": round(float(refined_votes.get("best_score", 0.0)), 4) if refined_candidates else None,
            "selectedAiScore": round(float(ai_best_score), 4) if api_key else None,
        }
        metrics_path = os.path.join(workdir, f"{prefix}_metrics.json")
        with open(metrics_path, "w", encoding="utf-8") as f:
            json.dump(metrics, f, ensure_ascii=False, indent=2)
            
        log_callback("生成分析报告...")
        report_path = os.path.join(workdir, f"{prefix}_report.md")
        with open(report_path, "w", encoding="utf-8") as f:
            f.write(f"# {prefix} RNA 结构预测报告\n\n")
            f.write(f"## 基本信息\n- 序列长度: {len(sequence)}\n")
            f.write(f"- 二级结构: `{dot_bracket}`\n")
            f.write(f"- 碱基配对数: {len(pairs)}\n\n")
            f.write("## 二级结构候选\n")
            f.write(f"- 选中候选类型: **{best_candidate['type']}**\n")
            f.write(f"- 候选排序分: **{best_candidate.get('rankScore', best_candidate['score'])}**\n")
            f.write(f"- 平均配对支持度: **{best_candidate.get('pairProbabilityMean', 0.0)}**\n")
            f.write(f"- 集合多样性: **{best_candidate.get('ensembleDiversity', 0.0)}**\n\n")
            f.write("## AI 优化\n")
            if api_key:
                f.write(f"- 初筛投票最佳分: **{votes.get('best_score', 0.0):.4f}**\n")
                f.write(f"- 精修候选数: **{len(refined_candidates)}**\n")
                if refined_candidates:
                    f.write(f"- 精修重审最佳分: **{refined_votes.get('best_score', 0.0):.4f}**\n")
                f.write(f"- 最终采用 AI 共识: **{ai_best_score:.4f}**\n\n")
            else:
                f.write("- 未配置 Ark API Key，本次未执行 AI 投票/精修。\n\n")
            f.write("## 合理性评估\n")
            f.write(f"- 综合评分: **{metrics['plausibilityScore']} / 100**\n")
            f.write(f"- 等级: **{metrics['grade']}**\n")
            f.write(f"- 摘要: {metrics['summary']}\n\n")
            f.write("### 核心几何参数\n")
            f.write(f"- 主链距离均值: {metrics['geometry']['backboneDistanceMean']} A\n")
            f.write(f"- 配对距离均值: {metrics['geometry']['pairDistanceMean']} A\n")
            f.write(f"- 配对支持度均值: {metrics['geometry']['pairSupportMean']}\n")
            f.write(f"- AI 共识均值: {metrics['geometry']['aiConsensusMean']}\n")
            f.write(f"- 最小非键距离: {metrics['geometry']['minNonbondedDistance']} A\n")
            f.write(f"- 紧密碰撞数: {metrics['geometry']['closeContactCount']}\n")
            f.write(f"- 回转半径: {metrics['geometry']['radiusOfGyration']} A\n\n")
            if annotation:
                f.write("## RNA/核酶分析\n")
                f.write("```\n")
                f.write(annotation.strip() + "\n")
                f.write("```\n\n")
            f.write("## 结构模型\n")
            f.write(f"| 模型 | 文件 |\n|---|---|\n")
            f.write(f"| Model 1 | [View 3D](3d_structures/{pdb_name}) |\n")
            f.write("\n## 说明\n")
            f.write("- 当前流程已接入 Ark API 用于 RNA 候选投票与精修，但 3D 仍属于本地粗粒度几何优化器。\n")
            f.write("- 后续可继续增强 A-form 茎区、loop/junction 与 stacking 约束，并引入更强的核酶 motif 专项优化。\n")
            
        log_callback("==== 所有任务已完成 ====")
        
    except Exception as e:
        log_callback(f"发生未捕获异常:\n{traceback.format_exc()}")
