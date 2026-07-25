import math
import os
import re
import shutil
import subprocess
import tempfile
from typing import Dict, List, Sequence, Tuple


CANONICAL_PAIR_SCORES = {
    "CG": 3.25,
    "GC": 3.25,
    "AU": 2.35,
    "UA": 2.35,
    "GU": 1.15,
    "UG": 1.15,
}


def normalize_sequence(sequence: str) -> str:
    normalized = sequence.upper().replace("T", "U").strip()
    invalid = sorted(set(normalized) - set("ACGUN"))
    if invalid:
        raise ValueError(f"RNA sequence contains invalid characters: {''.join(invalid)}")
    return normalized


def can_pair(base_left: str, base_right: str) -> bool:
    return base_left + base_right in CANONICAL_PAIR_SCORES


def _pair_score(base_left: str, base_right: str, gu_scale: float = 1.0) -> float:
    pair = base_left + base_right
    score = CANONICAL_PAIR_SCORES.get(pair, 0.0)
    if pair in {"GU", "UG"}:
        score *= gu_scale
    return score


def _pairs_to_dot_bracket(length: int, pairs: Sequence[Tuple[int, int]]) -> str:
    dot_bracket = ["."] * length
    for left, right in pairs:
        dot_bracket[left] = "("
        dot_bracket[right] = ")"
    return "".join(dot_bracket)


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


def _stem_count(pairs: Sequence[Tuple[int, int]]) -> int:
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


def _loop_penalty(loop_size: int, min_loop: int, loop_scale: float) -> float:
    adjusted = max(0, loop_size - min_loop)
    return adjusted * loop_scale


def _weighted_fold(
    sequence: str,
    min_loop: int,
    stacking_bonus: float,
    gu_scale: float,
    loop_scale: float,
    bifurcation_penalty: float = 0.18,
) -> Tuple[str, List[Tuple[int, int]], float]:
    length = len(sequence)
    if length == 0:
        return "", [], 0.0

    dp = [[0.0 for _ in range(length)] for _ in range(length)]

    for span in range(1, length):
        for left in range(length - span):
            right = left + span
            best = max(dp[left + 1][right], dp[left][right - 1])

            if right - left > min_loop and can_pair(sequence[left], sequence[right]):
                pair_value = _pair_score(sequence[left], sequence[right], gu_scale)
                loop_penalty = _loop_penalty(right - left - 1, min_loop, loop_scale)
                stack_value = 0.0
                if left + 1 < right - 1 and can_pair(sequence[left + 1], sequence[right - 1]):
                    stack_value = stacking_bonus
                best = max(best, dp[left + 1][right - 1] + pair_value + stack_value - loop_penalty)

            for split in range(left, right):
                best = max(best, dp[left][split] + dp[split + 1][right] - bifurcation_penalty)

            dp[left][right] = best

    pairs: List[Tuple[int, int]] = []

    def traceback(left: int, right: int) -> None:
        if left >= right:
            return

        current = dp[left][right]
        if abs(current - dp[left + 1][right]) < 1e-6:
            traceback(left + 1, right)
            return
        if abs(current - dp[left][right - 1]) < 1e-6:
            traceback(left, right - 1)
            return

        if right - left > min_loop and can_pair(sequence[left], sequence[right]):
            pair_value = _pair_score(sequence[left], sequence[right], gu_scale)
            loop_penalty = _loop_penalty(right - left - 1, min_loop, loop_scale)
            stack_value = 0.0
            if left + 1 < right - 1 and can_pair(sequence[left + 1], sequence[right - 1]):
                stack_value = stacking_bonus
            paired_score = dp[left + 1][right - 1] + pair_value + stack_value - loop_penalty
            if abs(current - paired_score) < 1e-6:
                pairs.append((left, right))
                traceback(left + 1, right - 1)
                return

        for split in range(left, right):
            bifurcated_score = dp[left][split] + dp[split + 1][right] - bifurcation_penalty
            if abs(current - bifurcated_score) < 1e-6:
                traceback(left, split)
                traceback(split + 1, right)
                return

    traceback(0, length - 1)
    pairs.sort()
    return _pairs_to_dot_bracket(length, pairs), pairs, dp[0][length - 1]


def _candidate_settings(env_text: str) -> List[Dict[str, float]]:
    text = env_text.lower()
    magnesium_like = "mg" in text or "镁" in text or "divalent" in text
    return [
        {"label": "ThermoBalanced", "min_loop": 3, "stacking_bonus": 0.85, "gu_scale": 0.95, "loop_scale": 0.12},
        {"label": "StemFavored", "min_loop": 3, "stacking_bonus": 1.10, "gu_scale": 0.90, "loop_scale": 0.08},
        {"label": "LoopTolerant", "min_loop": 4, "stacking_bonus": 0.70, "gu_scale": 1.05, "loop_scale": 0.05},
        {"label": "Compact", "min_loop": 5, "stacking_bonus": 1.00, "gu_scale": 0.85, "loop_scale": 0.15},
        {
            "label": "MgStabilized" if magnesium_like else "GUFriendly",
            "min_loop": 3,
            "stacking_bonus": 1.20 if magnesium_like else 0.75,
            "gu_scale": 1.12 if not magnesium_like else 0.92,
            "loop_scale": 0.06 if magnesium_like else 0.10,
        },
    ]


def _find_rnafold_executable() -> str:
    candidates = [
        os.environ.get("MINIFOLD_RNAFOLD"),
        shutil.which("RNAfold.exe"),
        shutil.which("RNAfold"),
    ]
    for candidate in candidates:
        if candidate and os.path.exists(candidate):
            return candidate
    return ""


def _run_external_rnafold(sequence: str) -> Dict[str, object]:
    executable = _find_rnafold_executable()
    if not executable:
        return {}

    with tempfile.TemporaryDirectory(prefix="minifold_rnafold_") as temp_dir:
        fasta_path = os.path.join(temp_dir, "input.fa")
        with open(fasta_path, "w", encoding="utf-8") as handle:
            handle.write(">input\n")
            handle.write(sequence + "\n")

        completed = subprocess.run(
            [executable, "--noPS", fasta_path],
            cwd=temp_dir,
            capture_output=True,
            text=True,
            check=False,
            encoding="utf-8",
        )
        if completed.returncode != 0:
            return {}

        lines = [line.strip() for line in completed.stdout.splitlines() if line.strip()]
        if len(lines) < 2:
            return {}

        structure_line = lines[-1]
        match = re.match(r"^([().]+)\s+\(([-0-9.]+)\)$", structure_line)
        if not match:
            return {}

        dot_bracket = match.group(1)
        mfe = float(match.group(2))
        pairs = _dot_bracket_to_pairs(dot_bracket)
        return {
            "dot_bracket": dot_bracket,
            "pairs": pairs,
            "type": "RNAfold_MFE",
            "score": len(pairs) * 2.0 + max(0.0, -mfe) * 0.35,
            "freeEnergy": mfe,
            "stemCount": _stem_count(pairs),
            "source": "external",
        }


def _aggregate_pair_probabilities(
    candidates: Sequence[Dict[str, object]],
    length: int,
    temperature: float = 1.8,
) -> Tuple[Dict[Tuple[int, int], float], float]:
    if not candidates:
        return {}, 0.0

    max_score = max(float(candidate["score"]) for candidate in candidates)
    weights: List[float] = []
    probability_map: Dict[Tuple[int, int], float] = {}

    for candidate in candidates:
        shifted = float(candidate["score"]) - max_score
        weight = math.exp(shifted / max(0.5, temperature))
        weights.append(weight)
        for left, right in candidate["pairs"]:
            pair = (min(left, right), max(left, right))
            probability_map[pair] = probability_map.get(pair, 0.0) + weight

    weight_sum = sum(weights) or 1.0
    for pair in list(probability_map.keys()):
        probability_map[pair] /= weight_sum

    ensemble_diversity = sum(probability * (1.0 - probability) for probability in probability_map.values())
    return probability_map, ensemble_diversity


def _mea_structure(length: int, pair_probabilities: Dict[Tuple[int, int], float], gamma: float = 1.2) -> Tuple[str, List[Tuple[int, int]], float]:
    if length == 0:
        return "", [], 0.0

    paired_mass = [0.0] * length
    for (left, right), probability in pair_probabilities.items():
        paired_mass[left] += probability
        paired_mass[right] += probability

    unpaired_mass = [max(0.0, 1.0 - mass) for mass in paired_mass]
    dp = [[0.0 for _ in range(length)] for _ in range(length)]

    for span in range(length):
        for left in range(length - span):
            right = left + span
            best = unpaired_mass[left] + (dp[left + 1][right] if left + 1 <= right else 0.0)

            for pair_right in range(left + 1, right + 1):
                probability = pair_probabilities.get((left, pair_right), 0.0)
                if probability <= 0.0:
                    continue
                candidate = 2.0 * gamma * probability
                if left + 1 <= pair_right - 1:
                    candidate += dp[left + 1][pair_right - 1]
                if pair_right + 1 <= right:
                    candidate += dp[pair_right + 1][right]
                best = max(best, candidate)

            dp[left][right] = best

    pairs: List[Tuple[int, int]] = []

    def traceback(left: int, right: int) -> None:
        if left > right:
            return
        if left == right:
            return

        current = dp[left][right]
        skip_score = unpaired_mass[left] + (dp[left + 1][right] if left + 1 <= right else 0.0)
        if abs(current - skip_score) < 1e-6:
            traceback(left + 1, right)
            return

        for pair_right in range(left + 1, right + 1):
            probability = pair_probabilities.get((left, pair_right), 0.0)
            if probability <= 0.0:
                continue
            candidate = 2.0 * gamma * probability
            if left + 1 <= pair_right - 1:
                candidate += dp[left + 1][pair_right - 1]
            if pair_right + 1 <= right:
                candidate += dp[pair_right + 1][right]
            if abs(current - candidate) < 1e-6:
                pairs.append((left, pair_right))
                traceback(left + 1, pair_right - 1)
                traceback(pair_right + 1, right)
                return

    traceback(0, length - 1)
    pairs.sort()
    return _pairs_to_dot_bracket(length, pairs), pairs, dp[0][length - 1]


def _serialize_probability_map(pair_probabilities: Dict[Tuple[int, int], float]) -> List[Dict[str, float]]:
    serialized = []
    for (left, right), probability in sorted(pair_probabilities.items(), key=lambda item: item[1], reverse=True):
        serialized.append(
            {
                "i": left,
                "j": right,
                "p": round(float(probability), 4),
            }
        )
    return serialized


def generate_rna_candidates(sequence: str, num: int = 4, env_text: str = "") -> List[Dict[str, object]]:
    sequence = normalize_sequence(sequence)
    candidates: List[Dict[str, object]] = []

    external_candidate = _run_external_rnafold(sequence)
    if external_candidate:
        candidates.append(external_candidate)

    for setting in _candidate_settings(env_text):
        dot_bracket, pairs, score = _weighted_fold(
            sequence,
            min_loop=int(setting["min_loop"]),
            stacking_bonus=float(setting["stacking_bonus"]),
            gu_scale=float(setting["gu_scale"]),
            loop_scale=float(setting["loop_scale"]),
        )
        candidates.append(
            {
                "dot_bracket": dot_bracket,
                "pairs": pairs,
                "type": setting["label"],
                "score": score,
                "stemCount": _stem_count(pairs),
                "source": "internal",
            }
        )

    unique: Dict[str, Dict[str, object]] = {}
    for candidate in candidates:
        existing = unique.get(candidate["dot_bracket"])
        if existing is None or float(candidate["score"]) > float(existing["score"]):
            unique[candidate["dot_bracket"]] = candidate

    candidate_list = list(unique.values())
    pair_probabilities, ensemble_diversity = _aggregate_pair_probabilities(candidate_list, len(sequence))

    if pair_probabilities:
        mea_dot_bracket, mea_pairs, mea_score = _mea_structure(len(sequence), pair_probabilities)
        if mea_dot_bracket not in unique:
            candidate_list.append(
                {
                    "dot_bracket": mea_dot_bracket,
                    "pairs": mea_pairs,
                    "type": "MEA_Consensus",
                    "score": mea_score,
                    "stemCount": _stem_count(mea_pairs),
                    "source": "consensus",
                }
            )

    serialized_probabilities = _serialize_probability_map(pair_probabilities)
    for candidate in candidate_list:
        if candidate["pairs"]:
            support_values = [
                pair_probabilities.get((min(left, right), max(left, right)), 0.0)
                for left, right in candidate["pairs"]
            ]
            pair_probability_mean = sum(support_values) / max(1, len(support_values))
        else:
            pair_probability_mean = 0.0
        candidate["pairProbabilityMean"] = round(pair_probability_mean, 4)
        stem_bonus = (len(candidate["pairs"]) / max(1, candidate["stemCount"])) * 1.6 if candidate["pairs"] else 0.0
        candidate["rankScore"] = round(
            float(candidate["score"]) + pair_probability_mean * len(candidate["pairs"]) + stem_bonus,
            4,
        )
        candidate["pairProbabilities"] = serialized_probabilities
        candidate["ensembleDiversity"] = round(ensemble_diversity, 4)

    candidate_list.sort(key=lambda item: (float(item["rankScore"]), float(item["score"]), item["stemCount"]), reverse=True)
    return candidate_list[: max(1, num)]
