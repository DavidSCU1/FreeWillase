from __future__ import annotations

import json
import math
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple

import numpy as np

from .quality import rama_pass_rate, summarize_structure, tm_score_proxy


BACKBONE_ATOMS = ("N", "CA", "C", "O")


def _normalize_sequence(sequence: str) -> str:
    lines = (sequence or "").splitlines()
    if lines and lines[0].startswith(">"):
        lines = lines[1:]
    return "".join(line.strip() for line in lines).replace(" ", "").upper()


def _safe_read_json(path: Path) -> Optional[Dict[str, Any]]:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except Exception:
        return None


def _clip01(value: Optional[float]) -> Optional[float]:
    if value is None:
        return None
    try:
        return float(max(0.0, min(1.0, value)))
    except Exception:
        return None


def _to_percent(value: Optional[float]) -> Optional[int]:
    value = _clip01(value)
    if value is None:
        return None
    return int(round(value * 100))


def _score_level(score: int) -> str:
    if score >= 85:
        return "优秀"
    if score >= 72:
        return "良好"
    if score >= 58:
        return "可用"
    return "谨慎使用"


def _parse_pdb_backbone(pdb_path: Path) -> List[Dict[str, Any]]:
    residues: List[Dict[str, Any]] = []
    current_key: Optional[Tuple[str, str, str]] = None
    current: Optional[Dict[str, Any]] = None

    with pdb_path.open("r", encoding="utf-8", errors="replace") as handle:
        for raw_line in handle:
            if not raw_line.startswith("ATOM"):
                continue
            atom_name = raw_line[12:16].strip()
            if atom_name not in BACKBONE_ATOMS:
                continue

            chain_id = raw_line[21].strip() or "_"
            residue_id = raw_line[22:26].strip()
            insertion_code = raw_line[26].strip()
            key = (chain_id, residue_id, insertion_code)

            try:
                coord = np.array([
                    float(raw_line[30:38].strip()),
                    float(raw_line[38:46].strip()),
                    float(raw_line[46:54].strip()),
                ], dtype=float)
            except Exception:
                continue

            if key != current_key:
                current_key = key
                current = {
                    "chain": chain_id,
                    "residue_id": residue_id,
                    "atoms": {},
                }
                residues.append(current)

            if current is not None:
                current["atoms"][atom_name] = coord

    return residues


def _dihedral(a: np.ndarray, b: np.ndarray, c: np.ndarray, d: np.ndarray) -> float:
    b0 = a - b
    b1 = c - b
    b2 = d - c
    b1_norm = b1 / (np.linalg.norm(b1) + 1e-9)
    v = b0 - np.dot(b0, b1_norm) * b1_norm
    w = b2 - np.dot(b2, b1_norm) * b1_norm
    x = np.dot(v, w)
    y = np.dot(np.cross(b1_norm, v), w)
    return float(math.atan2(y, x))


def _estimate_rama_score(residues: List[Dict[str, Any]], sequence: str) -> Optional[float]:
    if len(residues) < 3:
        return None

    sequence = _normalize_sequence(sequence)
    phi_values: List[float] = []
    psi_values: List[float] = []
    seq_window: List[str] = []
    sequence_offset = 0

    chain_groups: Dict[str, List[Dict[str, Any]]] = {}
    for residue in residues:
        chain_groups.setdefault(residue["chain"], []).append(residue)

    for chain_id in sorted(chain_groups.keys()):
        chain_residues = chain_groups[chain_id]
        chain_len = len(chain_residues)
        chain_sequence = sequence[sequence_offset:sequence_offset + chain_len]
        sequence_offset += chain_len

        for index in range(1, chain_len - 1):
            prev_atoms = chain_residues[index - 1]["atoms"]
            curr_atoms = chain_residues[index]["atoms"]
            next_atoms = chain_residues[index + 1]["atoms"]
            required = ("C", "N", "CA", "C", "N")
            if any(atom not in prev_atoms for atom in ("C",)):
                continue
            if any(atom not in curr_atoms for atom in ("N", "CA", "C")):
                continue
            if any(atom not in next_atoms for atom in ("N",)):
                continue

            phi = _dihedral(prev_atoms["C"], curr_atoms["N"], curr_atoms["CA"], curr_atoms["C"])
            psi = _dihedral(curr_atoms["N"], curr_atoms["CA"], curr_atoms["C"], next_atoms["N"])
            phi_values.append(phi)
            psi_values.append(psi)
            seq_window.append(chain_sequence[index] if index < len(chain_sequence) else "A")

    if not phi_values or not psi_values:
        return None

    try:
        return float(rama_pass_rate(phi_values, psi_values, "".join(seq_window)))
    except Exception:
        return None


def _build_integrity_metrics(residues: List[Dict[str, Any]], sequence: str) -> Dict[str, Optional[float]]:
    sequence_length = len(_normalize_sequence(sequence))
    residue_count = len(residues)
    if residue_count == 0:
        return {
            "residue_coverage": 0.0,
            "backbone_completeness": 0.0,
            "integrity_score": 0.0,
        }

    backbone_ratios = []
    ca_count = 0
    for residue in residues:
        atoms = residue["atoms"]
        if "CA" in atoms:
            ca_count += 1
        backbone_ratios.append(sum(1 for atom in BACKBONE_ATOMS if atom in atoms) / len(BACKBONE_ATOMS))

    residue_coverage = 1.0 if sequence_length == 0 else min(ca_count / max(sequence_length, 1), 1.0)
    backbone_completeness = float(sum(backbone_ratios) / len(backbone_ratios))
    integrity_score = 0.55 * residue_coverage + 0.45 * backbone_completeness

    return {
        "residue_coverage": residue_coverage,
        "backbone_completeness": backbone_completeness,
        "integrity_score": integrity_score,
    }


def _load_selected_secondary_structure(workdir: Path) -> str:
    kept_candidates = sorted(workdir.glob("*_cases_kept.json"))
    if not kept_candidates:
        return ""

    kept_cases = _safe_read_json(kept_candidates[0])
    if not isinstance(kept_cases, list) or not kept_cases:
        return ""

    selected_case = max(
        (item for item in kept_cases if isinstance(item, dict)),
        key=lambda item: float(item.get("p", 0.0)),
        default=None,
    )
    if not selected_case:
        return ""

    case_value = selected_case.get("case")
    case_dir = workdir / ("case_final" if str(case_value) == "final" else f"case_{case_value}")
    files = selected_case.get("files") or []
    chains: List[str] = []
    for file_name in files:
        file_path = case_dir / str(file_name)
        if not file_path.exists():
            continue
        chains.append(file_path.read_text(encoding="utf-8").strip())
    return "".join(chains)


def _load_consensus_score(workdir: Path) -> Optional[float]:
    candidates: List[float] = []

    votes_files = sorted(workdir.glob("*_votes.json"))
    if votes_files:
        votes = _safe_read_json(votes_files[0]) or {}
        best_score = votes.get("best_score")
        if isinstance(best_score, (int, float)):
            candidates.append(float(best_score))

    kept_files = sorted(workdir.glob("*_cases_kept.json"))
    if kept_files:
        kept_cases = _safe_read_json(kept_files[0])
        if isinstance(kept_cases, list):
            for item in kept_cases:
                if isinstance(item, dict) and isinstance(item.get("p"), (int, float)):
                    candidates.append(float(item["p"]))

    if not candidates:
        return None
    return _clip01(sum(candidates) / len(candidates))


def _build_dimension(
    key: str,
    label: str,
    score: Optional[float],
    weight: float,
    description: str,
    detail: str,
) -> Optional[Dict[str, Any]]:
    percent = _to_percent(score)
    if percent is None:
        return None
    return {
        "key": key,
        "label": label,
        "score": percent,
        "weight": weight,
        "level": _score_level(percent),
        "description": description,
        "detail": detail,
    }


def generate_quality_assessment(task_dir: str | Path, sequence: str) -> Optional[Dict[str, Any]]:
    task_dir = Path(task_dir)
    workdir = task_dir / "input"
    three_d_dir = workdir / "3d_structures"
    pdb_files = sorted(item for item in three_d_dir.glob("*.pdb"))
    if not pdb_files:
        return None

    pdb_path = pdb_files[0]
    residues = _parse_pdb_backbone(pdb_path)
    if not residues:
        return None

    metrics_path = pdb_path.with_suffix(".metrics.json")
    raw_metrics = _safe_read_json(metrics_path) or {}
    integrity_metrics = _build_integrity_metrics(residues, sequence)
    ss_text = _load_selected_secondary_structure(workdir)
    summary = summarize_structure(str(pdb_path), _normalize_sequence(sequence), ss_text)
    consensus_score = _load_consensus_score(workdir)
    rama_score = _clip01(raw_metrics.get("ramachandran_pass_rate"))
    if rama_score is None:
        rama_score = _estimate_rama_score(residues, sequence)

    ca_coords = np.asarray([residue["atoms"]["CA"] for residue in residues if "CA" in residue["atoms"]], dtype=float)
    compactness_score = _clip01(raw_metrics.get("tm_score_proxy"))
    if compactness_score is None and len(ca_coords) > 0:
        compactness_score = _clip01(tm_score_proxy(ca_coords))

    ss_score = _clip01(summary.get("ss_conf"))
    core_stability = _clip01(summary.get("core_stability"))
    loop_uncertainty = _clip01(summary.get("loop_uncertainty"))
    integrity_score = _clip01(integrity_metrics.get("integrity_score"))

    dimensions = [
        _build_dimension(
            "integrity",
            "结构完整性",
            integrity_score,
            0.22,
            "检查骨架原子是否齐全、残基覆盖是否到位。",
            f"残基覆盖 {_to_percent(integrity_metrics.get('residue_coverage')) or 0} / 骨架完整 {_to_percent(integrity_metrics.get('backbone_completeness')) or 0}",
        ),
        _build_dimension(
            "geometry",
            "几何合理性",
            rama_score,
            0.24,
            "依据 Ramachandran 通过率判断主链扭角是否自然。",
            f"Ramachandran 通过率 {_to_percent(rama_score) or 0}",
        ),
        _build_dimension(
            "compactness",
            "折叠紧致度",
            compactness_score,
            0.2,
            "用几何紧致程度评估整体折叠是否收敛。",
            f"TM proxy {_to_percent(compactness_score) or 0}",
        ),
        _build_dimension(
            "secondary_structure",
            "二级结构一致性",
            ss_score,
            0.16,
            "对比二级结构候选与最终三维骨架的一致程度。",
            f"SS 一致性 {_to_percent(ss_score) or 0}",
        ),
        _build_dimension(
            "consensus",
            "候选共识度",
            consensus_score,
            0.18,
            "看候选评审阶段是否对当前解形成较强共识。",
            f"投票共识 {_to_percent(consensus_score) or 0}",
        ),
    ]
    dimensions = [item for item in dimensions if item is not None]
    if not dimensions:
        return None

    total_weight = sum(float(item["weight"]) for item in dimensions)
    overall = int(round(sum(item["score"] * float(item["weight"]) for item in dimensions) / max(total_weight, 1e-6)))

    warnings: List[str] = []
    if integrity_score is not None and integrity_score < 0.75:
        warnings.append("骨架覆盖或主链原子完整度偏低，建议先复核结构完整性。")
    if rama_score is not None and rama_score < 0.7:
        warnings.append("主链几何通过率偏低，结构局部可能存在不自然扭角。")
    if compactness_score is not None and compactness_score < 0.7:
        warnings.append("整体折叠收敛度一般，建议结合功能位点再做人工判断。")
    if consensus_score is not None and consensus_score < 0.65:
        warnings.append("候选评审共识不高，这次结果更适合作为探索性候选。")

    level = _score_level(overall)
    summary_text = {
        "优秀": "这次模型整体质量比较稳，可以作为优先候选继续分析或入库。",
        "良好": "这次模型质量总体不错，建议结合位点和功能背景一起判断。",
        "可用": "这次模型可作为参考结果，关键区域最好再做人工复核。",
        "谨慎使用": "这次模型分数偏低，更适合作为探索性结果，暂不建议直接当作高可信结构。",
    }[level]

    return {
        "overallScore": overall,
        "level": level,
        "summary": summary_text,
        "dimensions": dimensions,
        "warnings": warnings,
        "rawMetrics": {
            "residueCoverage": _to_percent(integrity_metrics.get("residue_coverage")),
            "backboneCompleteness": _to_percent(integrity_metrics.get("backbone_completeness")),
            "ramachandranPassRate": _to_percent(rama_score),
            "tmScoreProxy": _to_percent(compactness_score),
            "secondaryStructureConfidence": _to_percent(ss_score),
            "coreStability": _to_percent(core_stability),
            "loopUncertainty": _to_percent(loop_uncertainty),
            "consensusScore": _to_percent(consensus_score),
        },
    }
