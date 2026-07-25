from typing import Dict, List, Optional, Sequence, Tuple

import numpy as np


RNA_BACKBONE_TARGET = 5.8
PAIR_TARGET = 17.0


def _group_stems(pairs: Sequence[Tuple[int, int]]) -> List[List[Tuple[int, int]]]:
    ordered = sorted((min(i, j), max(i, j)) for i, j in pairs)
    if not ordered:
        return []

    stems: List[List[Tuple[int, int]]] = []
    current = [ordered[0]]
    for left, right in ordered[1:]:
        prev_left, prev_right = current[-1]
        if left == prev_left + 1 and right == prev_right - 1:
            current.append((left, right))
        else:
            stems.append(current)
            current = [(left, right)]
    stems.append(current)
    return stems


def _loop_count(dot_bracket: str) -> int:
    loop_count = 0
    in_loop = False
    for char in dot_bracket:
        if char == "." and not in_loop:
            loop_count += 1
            in_loop = True
        elif char != ".":
            in_loop = False
    return loop_count


def _normalize(vector: np.ndarray) -> np.ndarray:
    norm = float(np.linalg.norm(vector))
    if norm < 1e-8:
        return np.zeros(3, dtype=float)
    return vector / norm


def _bounded_score(value: float, good: float, bad: float) -> float:
    if bad <= good:
        return 100.0
    if value <= good:
        return 100.0
    if value >= bad:
        return 0.0
    ratio = (value - good) / (bad - good)
    return max(0.0, 100.0 * (1.0 - ratio))


def evaluate_rna_model(
    sequence: str,
    coords: np.ndarray,
    pairs: Sequence[Tuple[int, int]],
    dot_bracket: str,
    env_text: str = "",
    pair_probabilities: Optional[Dict[Tuple[int, int], float]] = None,
    ai_confidence: Optional[float] = None,
) -> Dict[str, object]:
    if len(coords) == 0:
        return {
            "plausibilityScore": 0.0,
            "grade": "poor",
            "summary": "未生成有效坐标。",
        }

    backbone_distances = [
        float(np.linalg.norm(coords[index] - coords[index - 1]))
        for index in range(1, len(coords))
    ]
    pair_distances = [
        float(np.linalg.norm(coords[left] - coords[right]))
        for left, right in pairs
    ]

    min_nonbonded_distance = None
    close_contact_count = 0
    pair_set = {tuple(sorted(pair)) for pair in pairs}
    for left in range(len(coords)):
        for right in range(left + 2, len(coords)):
            if (left, right) in pair_set:
                continue
            distance = float(np.linalg.norm(coords[left] - coords[right]))
            if min_nonbonded_distance is None or distance < min_nonbonded_distance:
                min_nonbonded_distance = distance
            if distance < 4.0:
                close_contact_count += 1

    center = np.mean(coords, axis=0)
    radius_of_gyration = float(np.sqrt(np.mean(np.sum((coords - center) ** 2, axis=1))))

    smoothness_values = []
    stem_smoothness_values = []
    loop_smoothness_values = []
    paired_positions = {left for left, _ in pairs} | {right for _, right in pairs}
    for index in range(1, len(coords) - 1):
        second_diff = coords[index - 1] - 2.0 * coords[index] + coords[index + 1]
        value = float(np.linalg.norm(second_diff))
        smoothness_values.append(value)
        if index in paired_positions:
            stem_smoothness_values.append(value)
        else:
            loop_smoothness_values.append(value)

    stacking_cosines = []
    for stem in _group_stems(pairs):
        for index in range(len(stem) - 1):
            left_a, right_a = stem[index]
            left_b, right_b = stem[index + 1]
            vec_a = _normalize(coords[right_a] - coords[left_a])
            vec_b = _normalize(coords[right_b] - coords[left_b])
            if np.linalg.norm(vec_a) > 0 and np.linalg.norm(vec_b) > 0:
                stacking_cosines.append(float(np.dot(vec_a, vec_b)))

    backbone_mean = float(np.mean(backbone_distances)) if backbone_distances else 0.0
    backbone_std = float(np.std(backbone_distances)) if backbone_distances else 0.0
    pair_mean = float(np.mean(pair_distances)) if pair_distances else 0.0
    pair_std = float(np.std(pair_distances)) if pair_distances else 0.0
    smoothness_mean = float(np.mean(smoothness_values)) if smoothness_values else 0.0
    stem_smoothness_mean = float(np.mean(stem_smoothness_values)) if stem_smoothness_values else smoothness_mean
    loop_smoothness_mean = float(np.mean(loop_smoothness_values)) if loop_smoothness_values else smoothness_mean
    stacking_mean = float(np.mean(stacking_cosines)) if stacking_cosines else 0.0
    pair_support_mean = 0.0
    if pair_probabilities and pairs:
        pair_support_mean = float(
            np.mean(
                [
                    pair_probabilities.get((min(left, right), max(left, right)), 0.0)
                    for left, right in pairs
                ]
            )
        )

    backbone_score = 0.7 * _bounded_score(abs(backbone_mean - RNA_BACKBONE_TARGET), 0.2, 2.0) + 0.3 * _bounded_score(backbone_std, 0.35, 2.0)
    pair_score = _bounded_score(abs(pair_mean - PAIR_TARGET), 1.5, 9.0) if pair_distances else 65.0
    clash_score = _bounded_score(close_contact_count, 0.0, max(8.0, len(sequence) / 3.0))
    smoothness_score = (
        0.7 * _bounded_score(stem_smoothness_mean, 0.7, 4.2)
        + 0.3 * _bounded_score(loop_smoothness_mean, 1.3, 6.8)
    )
    stacking_score = ((stacking_mean + 1.0) / 2.0) * 100.0 if stacking_cosines else 60.0
    compactness_score = _bounded_score(radius_of_gyration, max(10.0, len(sequence) * 0.18), max(24.0, len(sequence) * 0.85))
    if ai_confidence is not None:
        ai_confidence = max(0.0, min(1.0, float(ai_confidence)))
    support_score = pair_support_mean * 100.0 if pair_probabilities and pairs else 60.0
    if ai_confidence is not None:
        support_score = support_score * 0.6 + ai_confidence * 100.0 * 0.4

    mg_bonus = 3.0 if "mg" in env_text.lower() or "镁" in env_text else 0.0
    plausibility_score = (
        backbone_score * 0.22
        + pair_score * 0.22
        + clash_score * 0.17
        + smoothness_score * 0.13
        + stacking_score * 0.10
        + compactness_score * 0.08
        + support_score * 0.08
        + mg_bonus
    )
    plausibility_score = max(0.0, min(100.0, plausibility_score))

    if plausibility_score >= 85:
        grade = "excellent"
    elif plausibility_score >= 72:
        grade = "good"
    elif plausibility_score >= 58:
        grade = "fair"
    else:
        grade = "poor"

    summary_parts = [
        f"主链步长均值 {backbone_mean:.2f} A",
        f"配对距离均值 {pair_mean:.2f} A" if pair_distances else "未形成稳定配对",
        f"最小非键距离 {(min_nonbonded_distance or 0.0):.2f} A",
        f"回转半径 {radius_of_gyration:.2f} A",
    ]
    if ai_confidence is not None:
        summary_parts.append(f"AI 共识 {ai_confidence * 100.0:.1f}")

    return {
        "plausibilityScore": round(plausibility_score, 2),
        "grade": grade,
        "summary": "；".join(summary_parts),
        "topology": {
            "sequenceLength": len(sequence),
            "pairCount": len(pairs),
            "stemCount": len(_group_stems(pairs)),
            "loopCount": _loop_count(dot_bracket),
            "dotBracket": dot_bracket,
        },
        "geometry": {
            "backboneDistanceMean": round(backbone_mean, 3),
            "backboneDistanceStd": round(backbone_std, 3),
            "pairDistanceMean": round(pair_mean, 3),
            "pairDistanceStd": round(pair_std, 3),
            "smoothnessMean": round(smoothness_mean, 3),
            "stemSmoothnessMean": round(stem_smoothness_mean, 3),
            "loopSmoothnessMean": round(loop_smoothness_mean, 3),
            "stackingAlignmentMean": round(stacking_mean, 3),
            "pairSupportMean": round(pair_support_mean, 3),
            "aiConsensusMean": round(float(ai_confidence or 0.0), 3),
            "radiusOfGyration": round(radius_of_gyration, 3),
            "minNonbondedDistance": round(float(min_nonbonded_distance or 0.0), 3),
            "closeContactCount": int(close_contact_count),
        },
        "scores": {
            "backbone": round(backbone_score, 2),
            "pairing": round(pair_score, 2),
            "clash": round(clash_score, 2),
            "smoothness": round(smoothness_score, 2),
            "stacking": round(stacking_score, 2),
            "compactness": round(compactness_score, 2),
            "pairSupport": round(support_score, 2),
        },
    }
