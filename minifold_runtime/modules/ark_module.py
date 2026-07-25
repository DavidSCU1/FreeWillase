import os
import json
import re
from typing import List, Dict, Any, Optional, Tuple
import requests
from openai import OpenAI

def _ark_base_url() -> str:
    return os.environ.get("ARK_API_URL", "https://ark.cn-beijing.volces.com/api/v3").strip().strip("`").strip()

def _ark_endpoint() -> str:
    base = _ark_base_url().rstrip("/")
    if base.endswith("/chat/completions"):
        return base
    return f"{base}/chat/completions"

def _ark_headers(api_key: Optional[str]) -> Dict[str, str]:
    key = api_key or os.environ.get("ARK_API_KEY", "")
    return {"Content-Type": "application/json", "Authorization": f"Bearer {key}"} if key else {"Content-Type": "application/json"}

def _ark_client(api_key: Optional[str] = None) -> OpenAI:
    key = api_key or os.environ.get("ARK_API_KEY", "")
    return OpenAI(base_url=_ark_base_url(), api_key=key)

def _model_uses_responses_api(model: str) -> bool:
    return model in {"doubao-seed-evolving"}


def _env_int(name: str, default: int, minimum: int = 1) -> int:
    try:
        value = int(str(os.environ.get(name, default)).strip())
        return max(minimum, value)
    except Exception:
        return default

def _extract_response_text(result: Any) -> str:
    output_text = getattr(result, "output_text", None)
    if isinstance(output_text, str) and output_text.strip():
        return output_text.strip()

    choices = getattr(result, "choices", None)
    if choices:
        first = choices[0]
        message = getattr(first, "message", None)
        if message is not None:
            content = getattr(message, "content", None)
            if isinstance(content, str):
                return content.strip()
            if isinstance(content, list):
                parts = []
                for item in content:
                    text = getattr(item, "text", None)
                    if isinstance(text, str):
                        parts.append(text)
                    elif isinstance(item, dict):
                        inner = item.get("text")
                        if isinstance(inner, str):
                            parts.append(inner)
                if parts:
                    return "".join(parts).strip()
        delta = getattr(first, "delta", None)
        if delta is not None:
            content = getattr(delta, "content", None)
            if isinstance(content, str):
                return content.strip()

    if hasattr(result, "model_dump"):
        dumped = result.model_dump()
        if isinstance(dumped, dict):
            if isinstance(dumped.get("output_text"), str):
                return dumped["output_text"].strip()
            choices = dumped.get("choices")
            if isinstance(choices, list) and choices:
                msg = (choices[0] or {}).get("message") or {}
                content = msg.get("content")
                if isinstance(content, str):
                    return content.strip()
    return ""

def _ark_text_completion(
    model: str,
    system_content: str,
    user_content: str,
    api_key: Optional[str] = None,
    timeout: int = 120,
) -> Tuple[Optional[str], Optional[str]]:
    try:
        client = _ark_client(api_key)
        if _model_uses_responses_api(model):
            result = client.responses.create(
                model=model,
                input=[
                    {
                        "role": "system",
                        "content": [{"type": "input_text", "text": system_content}],
                    },
                    {
                        "role": "user",
                        "content": [{"type": "input_text", "text": user_content}],
                    },
                ],
                timeout=timeout,
            )
        else:
            result = client.chat.completions.create(
                model=model,
                messages=[
                    {"role": "system", "content": system_content},
                    {"role": "user", "content": user_content},
                ],
                timeout=timeout,
            )
        content = _extract_response_text(result)
        if content:
            return content, None
        return None, "Empty response content"
    except Exception as exc:
        return None, str(exc)

def get_default_models() -> List[str]:
    env_models = os.environ.get("ARK_MODELS", "")
    if env_models:
        return [m.strip() for m in env_models.split(",") if m.strip()]
    return [
        "doubao-seed-1-6-251015",
        "deepseek-v3-2-251201",
    ]

def get_model_weights(models: List[str]) -> List[float]:
    env_w = os.environ.get("ARK_MODEL_WEIGHTS", "")
    if env_w:
        parts = [p.strip() for p in env_w.split(",") if p.strip()]
        vals = []
        for i in range(len(models)):
            try:
                vals.append(float(parts[i]))
            except Exception:
                vals.append(1.0)
        s = sum(vals) or 1.0
        return [v / s for v in vals]
    return [1.0 / max(1, len(models))] * len(models)

def ark_eval_case(model: str, sequence: str, environment: Optional[str], chains: List[str], req_text: Optional[str] = None, api_key: Optional[str] = None, timeout: int = 120) -> Tuple[Optional[float], Optional[str]]:
    user_content = json.dumps({
        "sequence": sequence,
        "environment": environment or "",
        "chains": chains or [],
        "requirements": req_text or "",
        "instruction": "Estimate probability (0-1) for the CASE; return only a number"
    }, ensure_ascii=False)
    try:
        s, err = _ark_text_completion(model, "仅返回一个0-1的数字。", user_content, api_key=api_key, timeout=timeout)
        if err:
            return None, err
        s = (s or "").strip()
        try:
            return float(s), None
        except Exception:
            # Try parsing line by line
            for line in s.splitlines():
                line = line.strip()
                try:
                    return float(line), None
                except Exception:
                    pass
            # Try parsing JSON
            try:
                obj = json.loads(s)
                p = obj.get("p")
                if isinstance(p, (int, float)):
                    return float(p), None
            except Exception:
                pass
            return None, f"Parse Error: Could not extract float from '{s[:50]}...'"
    except Exception as e:
        return None, f"Network Error: {str(e)}"

def ark_audit_structure(model: str, summary: Dict[str, Any], api_key: Optional[str] = None, timeout: int = 120) -> Tuple[Optional[str], Optional[float], Optional[str]]:
    prompt = {
        "tokens": summary,
        "instruction": "Judge naturalness of protein structure. Return JSON: {'verdict': 'accept'|'reject', 'score': 0-1}."
    }
    try:
        s, err = _ark_text_completion(
            model,
            "Return only a short JSON with fields 'verdict' and 'score'.",
            json.dumps(prompt, ensure_ascii=False),
            api_key=api_key,
            timeout=timeout,
        )
        if err:
            return None, None, err
        s = (s or "").strip()
        try:
            obj = json.loads(s)
            verdict = obj.get("verdict")
            score = obj.get("score")
            if isinstance(verdict, str) and isinstance(score, (int, float)):
                return verdict.lower(), float(score), None
        except Exception:
            pass
        return None, None, "Parse Error"
    except Exception as e:
        return None, None, f"Network Error: {str(e)}"

def ark_refine_structure(model: str, sequence: str, environment: str, chains: List[str], target_chains: Optional[int] = None, api_key: Optional[str] = None, timeout: int = 300) -> Tuple[Optional[List[str]], Optional[str]]:
    """
    Asks the model to refine the SS structure based on environment description.
    Returns (refined_chains, error_msg).
    """
    chain_constraint = ""
    if target_chains is not None and target_chains > 0:
        chain_constraint = f"""
5. CRITICAL CONSTRAINT: You MUST return a JSON list containing EXACTLY {target_chains} string(s). 
   - Even if the structure suggests multiple domains, you MUST merge them into {target_chains} string(s).
   - If target is 1, return ["...sequence..."].
   - If target is 2, return ["...seq1...", "...seq2..."].
   - VIOLATION OF THIS COUNT WILL CAUSE FAILURE.
"""
    
    prompt = f"""
Given the protein sequence (length {len(sequence)}) and environment description: "{environment}", 
please REFINE the following secondary structure prediction to be more physically realistic for this environment.

Current prediction (chains):
{json.dumps(chains)}

Rules:
1. Return ONLY a JSON list of strings (chains).
2. The total length of residues (H/E/C) must exactly match the input sequence length.
3. Consider the environment: e.g., if membrane, helices might be preferred; if high temp, structure might be more compact.
4. Do not output any explanation, just the JSON list.
{chain_constraint}
"""
    # Retry logic
    max_retries = 3
    for attempt in range(max_retries):
        try:
            s, err = _ark_text_completion(
                model,
                "You are a protein structure expert. Output only valid JSON.",
                prompt,
                api_key=api_key,
                timeout=timeout,
            )
            if err:
                if attempt == max_retries - 1:
                    return None, f"Refine Error: {err}"
                continue # Retry on HTTP error
            s = (s or "").strip()
            
            # Robust JSON parsing
            # 1. Try extracting from markdown blocks ```json ... ``` or just ``` ... ```
            if "```" in s:
                parts = s.split("```")
                # Look for the part that looks like a list
                found_json = False
                for part in parts:
                    part = part.strip()
                    if part.startswith("json"): 
                        part = part[4:].strip()
                    if part.startswith("[") and part.endswith("]"):
                        s = part
                        found_json = True
                        break
                # If not found in blocks, maybe the whole string is messy but contains []
            
            # 2. If simple parse fails, try regex to find the first list [...]
            if not (s.startswith("[") and s.endswith("]")):
                import re
                match = re.search(r'\[.*\]', s, re.DOTALL)
                if match:
                    s = match.group(0)

            try:
                refined_chains = json.loads(s)
                if isinstance(refined_chains, list) and all(isinstance(x, str) for x in refined_chains):
                    return refined_chains, None
                else:
                    # Retry if format is wrong (e.g. object instead of list)
                    if attempt == max_retries - 1:
                        return None, f"Invalid format: Expected list of strings, got {type(refined_chains)}"
                    continue
            except Exception as e:
                # If last attempt, return detailed error with snippet
                if attempt == max_retries - 1:
                    snippet = s[:50] + "..." if len(s) > 50 else s
                    return None, f"Refine Parse Error: {str(e)} | Content: {snippet}"
                continue # Retry
                
        except requests.exceptions.Timeout:
            if attempt == max_retries - 1:
                return None, f"Refine Network Error: Request timed out after {timeout}s"
            # Retry
        except Exception as e:
            if attempt == max_retries - 1:
                return None, f"Refine Network Error: {str(e)}"
            # Retry
            
    return None, "Refine failed after retries"

import concurrent.futures

def ark_vote_cases(models: List[str], sequence: str, environment: Optional[str], cases: List[Dict[str, Any]], req_text: Optional[str] = None, api_key: Optional[str] = None) -> Dict[str, Any]:
    weights = get_model_weights(models)
    per_case = []
    
    # Flatten all tasks: (case_idx, model_idx, model_name)
    tasks = []
    for c_idx, case in enumerate(cases):
        for m_idx, m_name in enumerate(models):
            tasks.append((c_idx, m_idx, m_name))
            
    results_map = {} # (c_idx, m_idx) -> (score, error_msg)

    # Parallel execution
    with concurrent.futures.ThreadPoolExecutor(max_workers=10) as executor:
        future_to_task = {}
        for (c_idx, m_idx, m_name) in tasks:
            chains = cases[c_idx].get("chains") or []
            f = executor.submit(ark_eval_case, m_name, sequence, environment, chains, req_text=req_text, api_key=api_key)
            future_to_task[f] = (c_idx, m_idx)
            
        for future in concurrent.futures.as_completed(future_to_task):
            c_idx, m_idx = future_to_task[future]
            try:
                score, err = future.result()
                results_map[(c_idx, m_idx)] = (score, err)
            except Exception as e:
                results_map[(c_idx, m_idx)] = (None, str(e))

    # Aggregate results
    for idx, case in enumerate(cases):
        chains = case.get("chains") or []
        scores = []
        per_model = []
        
        for m_i, m in enumerate(models):
            res = results_map.get((idx, m_i))
            if res:
                p, err = res
                if p is not None:
                    w = weights[m_i]
                    per_model.append({"model": m, "p": float(p), "w": w})
                    scores.append((float(p), w))
                else:
                    # Record failure for visibility
                    per_model.append({"model": m, "p": -1.0, "error": err or "Unknown"})
            else:
                per_model.append({"model": m, "p": -1.0, "error": "No result"})
                
        if scores:
            total_w = sum(w for _, w in scores)
            avg = sum(s * w for s, w in scores) / (total_w if total_w > 0 else 1.0)
            med = sorted([s for s, _ in scores])[len(scores) // 2]
            print(f"[Ark Vote] Case {idx+1} Final: Avg={avg:.4f}, Median={med:.4f}")
        else:
            avg = 0.0
            med = 0.0
            print(f"[Ark Vote] Case {idx+1} Final: No valid votes.")
        per_case.append({"idx": idx, "chains": len(chains), "avg": avg, "med": med, "models": per_model})

    best_idx = -1
    best_score = -1.0
    for it in per_case:
        sc = (it["avg"] + it["med"]) / 2.0
        if sc > best_score:
            best_score = sc
            best_idx = it["idx"]
    return {"cases": per_case, "best_idx": best_idx, "best_score": best_score}

def ark_analyze_sequence(sequence: str, api_key: Optional[str] = None) -> str:
    """
    Uses Ark API to annotate the protein sequence.
    """
    prompt = f"""
    You are an expert bioinformatician. Analyze the following protein sequence:
    
    Sequence:
    {sequence}
    
    Please provide:
    1. Potential domain structure.
    2. Predicted function.
    3. Active sites or key residues.
    4. Subcellular localization (e.g. signal peptides).
    """
    try:
        content, err = _ark_text_completion(
            "deepseek-v3-2-251201",
            "你是人工智能助手.",
            prompt,
            api_key=api_key,
            timeout=60,
        )
        return content if content else f"LLM Analysis Failed: {err or 'empty response'}"
    except Exception as e:
        return f"LLM Analysis Failed: {e}"


def _extract_float_from_text(text: str) -> Optional[float]:
    if not text:
        return None
    text = text.strip()
    try:
        return float(text)
    except Exception:
        pass

    for line in text.splitlines():
        line = line.strip()
        try:
            return float(line)
        except Exception:
            pass

    match = re.search(r"(-?\d+(?:\.\d+)?)", text)
    if match:
        try:
            return float(match.group(1))
        except Exception:
            return None
    return None


def _extract_json_dict(text: str) -> Optional[Dict[str, Any]]:
    if not text:
        return None
    raw = text.strip()
    candidates = [raw]
    if "```" in raw:
        for part in raw.split("```"):
            part = part.strip()
            if part.startswith("json"):
                part = part[4:].strip()
            if part.startswith("{") and part.endswith("}"):
                candidates.append(part)
    if not (raw.startswith("{") and raw.endswith("}")):
        match = re.search(r"\{.*\}", raw, re.DOTALL)
        if match:
            candidates.append(match.group(0))

    for candidate in candidates:
        try:
            value = json.loads(candidate)
            if isinstance(value, dict):
                return value
        except Exception:
            continue
    return None


def _normalize_dot_bracket(value: str, expected_length: Optional[int] = None) -> Optional[str]:
    if value is None:
        return None
    candidate = "".join(ch for ch in str(value).strip() if ch in ".()")
    if not candidate:
        return None
    if expected_length is not None and len(candidate) != expected_length:
        return None

    balance = 0
    for ch in candidate:
        if ch == "(":
            balance += 1
        elif ch == ")":
            balance -= 1
            if balance < 0:
                return None
    if balance != 0:
        return None
    return candidate


def _repair_dot_bracket(value: str, expected_length: int) -> Optional[str]:
    if value is None:
        return None
    candidate = "".join(ch for ch in str(value).strip() if ch in ".()")
    if not candidate:
        return None

    if len(candidate) > expected_length:
        candidate = candidate[:expected_length]
    elif len(candidate) < expected_length:
        candidate = candidate + ("." * (expected_length - len(candidate)))

    chars = list(candidate)
    stack: List[int] = []
    for index, char in enumerate(chars):
        if char == "(":
            stack.append(index)
        elif char == ")":
            if stack:
                stack.pop()
            else:
                chars[index] = "."
    for index in stack:
        chars[index] = "."

    repaired = "".join(chars)
    return _normalize_dot_bracket(repaired, expected_length=expected_length)


def _dot_bracket_to_pairs(dot_bracket: str) -> List[Tuple[int, int]]:
    stack: List[int] = []
    pairs: List[Tuple[int, int]] = []
    for index, char in enumerate(dot_bracket):
        if char == "(":
            stack.append(index)
        elif char == ")" and stack:
            pairs.append((stack.pop(), index))
    pairs.sort()
    return pairs


def ark_eval_rna_case(
    model: str,
    sequence: str,
    environment: Optional[str],
    case: Dict[str, Any],
    req_text: Optional[str] = None,
    api_key: Optional[str] = None,
    timeout: int = 60,
) -> Tuple[Optional[float], Optional[str]]:
    user_content = json.dumps(
        {
            "sequence": sequence,
            "environment": environment or "",
            "requirements": req_text or "",
            "candidate": {
                "dot_bracket": case.get("dot_bracket", ""),
                "pair_count": len(case.get("pairs") or []),
                "stem_count": case.get("stemCount", 0),
                "type": case.get("type", ""),
                "rank_score": case.get("rankScore", case.get("score", 0.0)),
            },
            "instruction": (
                "Estimate the biological plausibility (0-1) of this RNA/ribozyme secondary-structure "
                "candidate for downstream 3D folding. Reward compact, catalytically plausible motifs and "
                "environment consistency. Penalize unstable long unstructured segments and unrealistic stem-loop topology. "
                "Return only one float."
            ),
        },
        ensure_ascii=False,
    )
    try:
        content, err = _ark_text_completion(
            model,
            "你是RNA结构评审专家。只返回0到1之间的一个数字，不要解释。",
            user_content,
            api_key=api_key,
            timeout=timeout,
        )
        if err:
            return None, err
        content = (content or "").strip()
        value = _extract_float_from_text(content)
        if value is None:
            return None, f"Parse Error: Could not parse score from '{content[:80]}'"
        return max(0.0, min(1.0, float(value))), None
    except Exception as exc:
        return None, f"Network Error: {exc}"


def ark_vote_rna_cases(
    models: List[str],
    sequence: str,
    environment: Optional[str],
    cases: List[Dict[str, Any]],
    req_text: Optional[str] = None,
    api_key: Optional[str] = None,
) -> Dict[str, Any]:
    weights = get_model_weights(models)
    per_case = []
    tasks = []
    for case_index, _ in enumerate(cases):
        for model_index, model_name in enumerate(models):
            tasks.append((case_index, model_index, model_name))

    results_map: Dict[Tuple[int, int], Tuple[Optional[float], Optional[str]]] = {}
    with concurrent.futures.ThreadPoolExecutor(max_workers=10) as executor:
        future_to_task = {}
        for case_index, model_index, model_name in tasks:
            future = executor.submit(
                ark_eval_rna_case,
                model_name,
                sequence,
                environment,
                cases[case_index],
                req_text=req_text,
                api_key=api_key,
            )
            future_to_task[future] = (case_index, model_index)

        for future in concurrent.futures.as_completed(future_to_task):
            case_index, model_index = future_to_task[future]
            try:
                results_map[(case_index, model_index)] = future.result()
            except Exception as exc:
                results_map[(case_index, model_index)] = (None, str(exc))

    for case_index, case in enumerate(cases):
        scores = []
        per_model = []
        for model_index, model_name in enumerate(models):
            score, err = results_map.get((case_index, model_index), (None, "No result"))
            if score is not None:
                weight = weights[model_index]
                scores.append((float(score), weight))
                per_model.append({"model": model_name, "p": float(score), "w": weight})
            else:
                per_model.append({"model": model_name, "p": -1.0, "error": err or "Unknown"})

        if scores:
            total_weight = sum(weight for _, weight in scores) or 1.0
            avg = sum(score * weight for score, weight in scores) / total_weight
            ordered = sorted(score for score, _ in scores)
            med = ordered[len(ordered) // 2]
        else:
            avg = 0.0
            med = 0.0
        per_case.append(
            {
                "idx": case_index,
                "avg": avg,
                "med": med,
                "type": case.get("type", ""),
                "dotBracket": case.get("dot_bracket", ""),
                "pairCount": len(case.get("pairs") or []),
                "stemCount": case.get("stemCount", 0),
                "models": per_model,
            }
        )

    best_idx = -1
    best_score = -1.0
    for item in per_case:
        score = (item["avg"] + item["med"]) / 2.0
        if score > best_score:
            best_score = score
            best_idx = item["idx"]

    return {"cases": per_case, "best_idx": best_idx, "best_score": best_score}


def ark_refine_rna_structure(
    model: str,
    sequence: str,
    environment: str,
    dot_bracket: str,
    api_key: Optional[str] = None,
    timeout: int = 120,
) -> Tuple[Optional[str], Optional[str]]:
    prompt = f"""
You are refining an RNA secondary structure for downstream 3D ribozyme folding.

Sequence length: {len(sequence)}
Sequence:
{sequence}

Environment:
{environment}

Current dot-bracket:
{dot_bracket}

Rules:
1. Return only compact JSON: {{"dot_bracket":"..."}}.
2. The dot-bracket must use only '.', '(' and ')'.
3. The output length must exactly equal the sequence length ({len(sequence)}).
4. Keep the structure physically plausible for RNA folding. Avoid impossible bracket balance.
5. Prefer motifs consistent with catalytic RNA / ribozyme folding if environment hints support that.
"""
    payload = {
        "model": model,
        "messages": [
            {"role": "system", "content": "你是RNA二级结构优化专家。只输出合法JSON。"},
            {"role": "user", "content": prompt},
        ],
    }

    retries = _env_int("MINIFOLD_RNA_REFINE_RETRIES", 2)
    for attempt in range(retries):
        try:
            content, err = _ark_text_completion(
                model,
                "你是RNA二级结构优化专家。只输出合法JSON。",
                prompt,
                api_key=api_key,
                timeout=timeout,
            )
            if err:
                if attempt == retries - 1:
                    return None, f"Refine Error: {err}"
                continue
            content = (content or "").strip()

            parsed = _extract_json_dict(content)
            candidate = None
            if parsed:
                candidate = parsed.get("dot_bracket")
            if candidate is None:
                match = re.search(r"[().]{%d}" % len(sequence), content)
                if match:
                    candidate = match.group(0)
            normalized = _normalize_dot_bracket(candidate or content, expected_length=len(sequence))
            if normalized:
                return normalized, None
            repaired = _repair_dot_bracket(candidate or content, expected_length=len(sequence))
            if repaired:
                return repaired, None
            if attempt == retries - 1:
                return None, f"Parse Error: {content[:120]}"
        except Exception as exc:
            if attempt == retries - 1:
                return None, f"Network Error: {exc}"
    return None, "RNA refinement failed after retries"


def ark_analyze_rna_sequence(sequence: str, environment: Optional[str] = None, api_key: Optional[str] = None) -> str:
    user_content = (
        "请分析以下RNA/核酶序列，给出可能的二级结构特征、潜在催化/结合区域、"
        "对Mg2+/环境的依赖提示，并用简洁中文返回。\n\n"
        f"Sequence:\n{sequence}\n\nEnvironment:\n{environment or ''}"
    )
    try:
        content, err = _ark_text_completion(
            "deepseek-v3-2-251201",
            "你是RNA与核酶分析助手。",
            user_content,
            api_key=api_key,
            timeout=_env_int("MINIFOLD_RNA_ANALYSIS_TIMEOUT", 25),
        )
        return content if content else f"LLM Analysis Failed: {err or 'empty response'}"
    except Exception as exc:
        return f"LLM Analysis Failed: {exc}"
