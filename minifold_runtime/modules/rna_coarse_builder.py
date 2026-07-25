import math
from typing import Dict, List, Optional, Sequence, Tuple

import numpy as np


RNA_BACKBONE_TARGET = 5.8
PAIR_TARGET = 17.0
A_FORM_RISE = 2.7
A_FORM_RADIUS = 7.5
A_FORM_TWIST = math.radians(32.7)
STACK_TARGET = 4.8


def _normalize(vector: np.ndarray) -> np.ndarray:
    norm = float(np.linalg.norm(vector))
    if norm < 1e-8:
        return np.array([1.0, 0.0, 0.0], dtype=float)
    return vector / norm


def _orthogonal(vector: np.ndarray) -> np.ndarray:
    vector = _normalize(vector)
    trial = np.array([0.0, 0.0, 1.0], dtype=float)
    if abs(float(np.dot(vector, trial))) > 0.9:
        trial = np.array([0.0, 1.0, 0.0], dtype=float)
    orthogonal = np.cross(vector, trial)
    return _normalize(orthogonal)


def _pair_map(pairs: Sequence[Tuple[int, int]]) -> Dict[int, int]:
    mapping: Dict[int, int] = {}
    for left, right in pairs:
        mapping[left] = right
        mapping[right] = left
    return mapping


def _pair_strength(
    pair_probabilities: Optional[Dict[Tuple[int, int], float]],
    left: int,
    right: int,
) -> float:
    if not pair_probabilities:
        return 1.0
    return float(pair_probabilities.get((min(left, right), max(left, right)), 0.75))


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


def _compactness_target(length: int) -> float:
    return max(14.0, min(30.0, 10.0 + length * 0.24))


def _initial_trace(length: int) -> np.ndarray:
    coords = np.zeros((length, 3), dtype=float)
    for index in range(length):
        coords[index] = np.array(
            [
                index * RNA_BACKBONE_TARGET * 0.42,
                3.0 * math.sin(index * 0.52),
                2.2 * math.cos(index * 0.31),
            ],
            dtype=float,
        )
    return coords


def _place_stems(coords: np.ndarray, stems: Sequence[Sequence[Tuple[int, int]]]) -> np.ndarray:
    assigned = np.zeros(len(coords), dtype=bool)
    total = max(len(stems), 1)
    cluster_radius = 7.5 + total * 1.6

    for stem_index, stem in enumerate(stems):
        if not stem:
            continue
        center_index = 0.5 * (stem[0][0] + stem[0][1])
        angle = 0.0 if total == 1 else (2.0 * math.pi * stem_index / total)
        origin = np.array(
            [
                center_index * RNA_BACKBONE_TARGET * 0.12,
                math.cos(angle) * cluster_radius,
                math.sin(angle) * cluster_radius * 0.78,
            ],
            dtype=float,
        )
        axis = _normalize(np.array([1.0, 0.11 * math.cos(angle), 0.11 * math.sin(angle)]))
        normal = _orthogonal(axis)
        binormal = _normalize(np.cross(axis, normal))

        for offset, (left, right) in enumerate(stem):
            phase = offset * A_FORM_TWIST
            radial = math.cos(phase) * normal + math.sin(phase) * binormal
            axis_offset = axis * (offset * A_FORM_RISE)
            coords[left] = origin + axis_offset + radial * A_FORM_RADIUS
            coords[right] = origin + axis_offset - radial * A_FORM_RADIUS
            assigned[left] = True
            assigned[right] = True

    return assigned


def _segment_types(length: int, stems: Sequence[Sequence[Tuple[int, int]]]) -> List[str]:
    labels = ["loop"] * length
    for stem in stems:
        for left, right in stem:
            labels[left] = "stem"
            labels[right] = "stem"
    return labels


def _classify_unpaired_segment(
    start: int,
    end: int,
    length: int,
    pair_lookup: Dict[int, int],
) -> str:
    if start == 0 or end == length - 1:
        return "external"

    left_anchor = start - 1
    right_anchor = end + 1
    left_partner = pair_lookup.get(left_anchor)
    right_partner = pair_lookup.get(right_anchor)

    if left_partner == right_anchor and right_partner == left_anchor:
        return "hairpin"
    if left_partner is not None and right_partner is not None:
        if left_partner > right_anchor and right_partner < left_anchor:
            return "internal"
        return "junction"
    return "external"


def _fill_unpaired_segments(coords: np.ndarray, assigned: np.ndarray, pair_lookup: Dict[int, int]) -> None:
    length = len(coords)
    index = 0
    while index < length:
        if assigned[index]:
            index += 1
            continue

        start = index
        while index < length and not assigned[index]:
            index += 1
        end = index - 1

        left_anchor = coords[start - 1] if start > 0 else np.array([(start - 1) * RNA_BACKBONE_TARGET, -8.0, 0.0], dtype=float)
        right_anchor = coords[end + 1] if end + 1 < length else np.array([(end + 1) * RNA_BACKBONE_TARGET, 8.0, 0.0], dtype=float)

        chord = right_anchor - left_anchor
        axis = _normalize(chord)
        normal = _orthogonal(axis)
        binormal = _normalize(np.cross(axis, normal))
        segment_type = _classify_unpaired_segment(start, end, length, pair_lookup)
        if segment_type == "hairpin":
            amplitude = 2.8 + 0.48 * (end - start + 1)
        elif segment_type == "internal":
            amplitude = 3.5 + 0.66 * (end - start + 1)
        elif segment_type == "junction":
            amplitude = 4.2 + 0.82 * (end - start + 1)
        else:
            amplitude = 3.6 + 0.58 * (end - start + 1)

        for offset, residue_index in enumerate(range(start, end + 1), start=1):
            t = offset / (end - start + 2)
            bow = math.sin(math.pi * t)
            twist = math.sin(2.0 * math.pi * t)
            coords[residue_index] = (
                left_anchor * (1.0 - t)
                + right_anchor * t
                + normal * (amplitude * bow)
                + binormal * ((0.20 if segment_type == "hairpin" else 0.32) * amplitude * twist)
            )


def _relax(
    coords: np.ndarray,
    pairs: Sequence[Tuple[int, int]],
    pair_probabilities: Optional[Dict[Tuple[int, int], float]] = None,
    iterations: int = 180,
) -> np.ndarray:
    coords = coords.copy()
    pair_list = [(min(i, j), max(i, j)) for i, j in pairs]
    pair_set = set(pair_list)
    stems = _group_stems(pair_list)
    residue_labels = _segment_types(len(coords), stems)
    length = len(coords)
    target_radius = _compactness_target(length)

    for _ in range(iterations):
        updated = coords.copy()

        for index in range(1, length):
            delta = coords[index] - coords[index - 1]
            distance = float(np.linalg.norm(delta)) + 1e-8
            direction = delta / distance
            shift = 0.12 * (distance - RNA_BACKBONE_TARGET)
            updated[index] -= direction * shift
            updated[index - 1] += direction * shift

        for index in range(1, length - 1):
            midpoint = 0.5 * (coords[index - 1] + coords[index + 1])
            stiffness = 0.08 if residue_labels[index] == "stem" else 0.05
            updated[index] += (midpoint - coords[index]) * stiffness

        center = np.mean(coords, axis=0)
        for index in range(length):
            pull_strength = 0.006 if residue_labels[index] == "stem" else 0.004
            updated[index] -= center * pull_strength
            radial = coords[index] - center
            radial_distance = float(np.linalg.norm(radial)) + 1e-8
            if radial_distance > target_radius:
                contract = (0.015 if residue_labels[index] == "loop" else 0.010) * (radial_distance - target_radius)
                updated[index] -= (radial / radial_distance) * contract

        for left, right in pair_list:
            delta = coords[right] - coords[left]
            distance = float(np.linalg.norm(delta)) + 1e-8
            direction = delta / distance
            strength = 0.05 + 0.07 * _pair_strength(pair_probabilities, left, right)
            shift = strength * (distance - PAIR_TARGET)
            updated[left] += direction * shift
            updated[right] -= direction * shift

        for stem in stems:
            for index in range(len(stem) - 1):
                left_a, right_a = stem[index]
                left_b, right_b = stem[index + 1]
                for first, second in [(left_a, left_b), (right_a, right_b)]:
                    delta = coords[second] - coords[first]
                    distance = float(np.linalg.norm(delta)) + 1e-8
                    direction = delta / distance
                    local_strength = min(
                        _pair_strength(pair_probabilities, left_a, right_a),
                        _pair_strength(pair_probabilities, left_b, right_b),
                    )
                    shift = (0.07 + 0.06 * local_strength) * (distance - STACK_TARGET)
                    updated[first] += direction * shift
                    updated[second] -= direction * shift

        if len(stems) > 1:
            stem_centers = [
                np.mean([coords[left] for left, _ in stem] + [coords[right] for _, right in stem], axis=0)
                for stem in stems
            ]
            for stem_index in range(len(stems) - 1):
                center_a = stem_centers[stem_index]
                center_b = stem_centers[stem_index + 1]
                delta = center_b - center_a
                distance = float(np.linalg.norm(delta)) + 1e-8
                if distance > 16.0:
                    direction = delta / distance
                    shift = 0.022 * (distance - 16.0)
                    for left, right in stems[stem_index]:
                        updated[left] += direction * shift
                        updated[right] += direction * shift
                    for left, right in stems[stem_index + 1]:
                        updated[left] -= direction * shift
                        updated[right] -= direction * shift

        for left in range(length):
            for right in range(left + 2, min(length, left + 8)):
                if (left, right) in pair_set or (right, left) in pair_set:
                    continue
                delta = coords[right] - coords[left]
                distance = float(np.linalg.norm(delta)) + 1e-8
                if distance < 6.0:
                    direction = delta / distance
                    repel = 0.06 * (6.0 - distance)
                    updated[left] -= direction * repel
                    updated[right] += direction * repel

        coords = 0.55 * coords + 0.45 * updated

    return coords


def build_coarse_rna_3d(
    sequence: str,
    dot_bracket: str,
    pairs: Sequence[Tuple[int, int]],
    pair_probabilities: Optional[Dict[Tuple[int, int], float]] = None,
) -> np.ndarray:
    coords = _initial_trace(len(sequence))
    stems = _group_stems(pairs)
    pair_lookup = _pair_map(pairs)
    assigned = _place_stems(coords, stems)
    _fill_unpaired_segments(coords, assigned, pair_lookup)
    coords = _relax(coords, pairs, pair_probabilities=pair_probabilities)
    coords -= np.mean(coords, axis=0)
    return coords


def _residue_frame(coords: np.ndarray, residue_index: int, pair_lookup: Dict[int, int]) -> Tuple[np.ndarray, np.ndarray, np.ndarray, np.ndarray]:
    center = coords[residue_index]
    prev_center = coords[residue_index - 1] if residue_index > 0 else center - np.array([RNA_BACKBONE_TARGET, 0.0, 0.0])
    next_center = coords[residue_index + 1] if residue_index + 1 < len(coords) else center + np.array([RNA_BACKBONE_TARGET, 0.0, 0.0])

    tangent = _normalize(next_center - prev_center)
    if residue_index in pair_lookup:
        partner = coords[pair_lookup[residue_index]]
        pair_direction = partner - center
        normal = pair_direction - tangent * float(np.dot(pair_direction, tangent))
        normal = _normalize(normal)
    else:
        normal = _orthogonal(tangent)
    binormal = _normalize(np.cross(tangent, normal))
    normal = _normalize(np.cross(binormal, tangent))
    return center, tangent, normal, binormal


def _base_atom_name(residue: str) -> str:
    return "N9" if residue in {"A", "G"} else "N1"


def _atom_layout(residue: str, center: np.ndarray, tangent: np.ndarray, normal: np.ndarray, binormal: np.ndarray) -> List[Tuple[str, str, np.ndarray]]:
    atoms = [
        ("P", "P", center - 2.25 * tangent + 1.35 * normal),
        ("OP1", "O", center - 2.55 * tangent + 2.35 * normal + 0.65 * binormal),
        ("OP2", "O", center - 2.55 * tangent + 2.25 * normal - 0.65 * binormal),
        ("O5'", "O", center - 1.35 * tangent + 1.00 * normal),
        ("C5'", "C", center - 0.55 * tangent + 1.08 * normal),
        ("C4'", "C", center + 0.10 * tangent + 0.50 * normal),
        ("O4'", "O", center - 0.10 * tangent - 0.20 * normal - 0.85 * binormal),
        ("C3'", "C", center + 0.95 * tangent + 0.15 * normal),
        ("O3'", "O", center + 1.85 * tangent - 0.10 * normal),
        ("C2'", "C", center + 0.45 * tangent - 0.75 * normal),
        ("C1'", "C", center - 0.05 * tangent - 1.30 * normal),
        (_base_atom_name(residue), "N", center - 0.25 * tangent - 2.30 * normal + 0.25 * binormal),
    ]
    return atoms


def write_rna_pdb(sequence: str, coords: np.ndarray, out_path: str, dot_bracket: str = "", pairs: Sequence[Tuple[int, int]] = ()) -> None:
    pair_lookup = _pair_map(pairs)
    serial = 1

    with open(out_path, "w", encoding="utf-8") as handle:
        handle.write("REMARK MiniFold RNA coarse model with backbone atoms\n")
        if dot_bracket:
            handle.write(f"REMARK Secondary structure {dot_bracket}\n")

        for residue_index, residue in enumerate(sequence, start=1):
            center, tangent, normal, binormal = _residue_frame(coords, residue_index - 1, pair_lookup)
            atoms = _atom_layout(residue, center, tangent, normal, binormal)
            for atom_name, element, atom_pos in atoms:
                handle.write(
                    f"ATOM  {serial:5d} {atom_name:>4s} {residue:>3s} A{residue_index:4d}    "
                    f"{atom_pos[0]:8.3f}{atom_pos[1]:8.3f}{atom_pos[2]:8.3f}"
                    f"  1.00  0.00          {element:>2s}\n"
                )
                serial += 1

        handle.write("TER\nEND\n")
