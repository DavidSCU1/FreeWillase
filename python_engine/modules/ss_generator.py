import random
from typing import List, Dict, Any, Tuple

# Fallback dictionaries for amino acid properties
HELIX_IDX = {c: v for c, v in zip("ACDEFGHIKLMNPQRSTVWY", [1.2,0.8,1.0,0.9,1.1,1.0,0.7,1.1,1.2,1.2,1.2,1.1,0.7,1.0,0.6,0.9,0.9,0.8,1.0,1.2])}
SHEET_IDX = {c: v for c, v in zip("ACDEFGHIKLMNPQRSTVWY", [0.8,0.9,0.9,1.1,1.0,0.9,1.1,0.7,0.8,1.3,1.3,0.8,1.0,1.2,0.6,0.9,0.9,1.3,1.2,1.2])}
HYDRO_IDX = {c: v for c, v in zip("ACDEFGHIKLMNPQRSTVWY", [1.8,-4.5,-3.5,-3.5,2.5,-3.5,-3.5,-0.4,-3.2,4.5,3.8,-3.9,1.9,-1.6,-1.6,-0.8,-0.7,-0.9,-1.3,4.2])}
BULK_IDX = {c: v for c, v in zip("ACDEFGHIKLMNPQRSTVWY", [88,173,114,111,135,148,138,60,153,166,166,146,124,189,112,99,122,205,181,140])}

def _compute_propensity_arrays(sequence: str) -> Tuple[list, list, list]:
    seq = sequence.upper()
    helix, sheet, coil = [], [], []
    for ch in seq:
        h = HELIX_IDX.get(ch, 0.8)
        e = SHEET_IDX.get(ch, 0.8)
        hyd = HYDRO_IDX.get(ch, 0.0)
        bulk = BULK_IDX.get(ch, 120.0)
        hp = h + max(0.0, hyd * 0.1)
        ep = e + max(0.0, (bulk - 120.0) * 0.002)
        cp = max(0.0, 1.0 - (hp + ep) * 0.3)
        if ch in ("P", "G"):
            hp *= 0.6
            ep *= 0.8
            cp += 0.3
        helix.append(hp)
        sheet.append(ep)
        coil.append(cp)
    return helix, sheet, coil

def _labels_from_propensity(sequence: str, win: int, h_bias: float, e_bias: float, stochastic: bool = False) -> List[str]:
    seq = sequence.upper()
    H, E, C = _compute_propensity_arrays(seq)
    n = len(seq)
    half = max(1, win // 2)
    out = []
    for i in range(n):
        l, r = max(0, i - half), min(n, i + half + 1)
        h_score = (sum(H[l:r]) / (r - l)) * (1.0 + h_bias)
        e_score = (sum(E[l:r]) / (r - l)) * (1.0 + e_bias)
        c_score = sum(C[l:r]) / (r - l)
        
        if stochastic:
            h_score *= random.uniform(0.7, 1.3)
            e_score *= random.uniform(0.7, 1.3)
            c_score *= random.uniform(0.7, 1.3)
            if random.random() < 0.05:
                choice = random.choice(["H", "E", "C"])
                if choice == "H": h_score += 10.0
                elif choice == "E": e_score += 10.0
                else: c_score += 10.0

        if h_score >= e_score and h_score >= c_score: out.append("H")
        elif e_score >= h_score and e_score >= c_score: out.append("E")
        else: out.append("C")
    return out

def _split_chains(ss: str, target_count: int = None) -> List[str]:
    if target_count == 1: return [ss]
    n = len(ss)
    if target_count and target_count > 1:
        # Simplified split for multiple chains
        parts = []
        part_len = n // target_count
        for i in range(target_count):
            start = i * part_len
            end = (i + 1) * part_len if i < target_count - 1 else n
            parts.append(ss[start:end])
        return parts
    return [ss]

def pybiomed_ss_candidates(sequence: str, num: int = 5, target_chains: int = None) -> Dict[str, Any]:
    seq = sequence.strip().upper()
    cases = []
    attempts = 0
    while len(cases) < num and attempts < num * 10:
        attempts += 1
        win = random.choice([5, 7, 9, 11, 13])
        h_thr = random.uniform(0.0, 0.2)
        e_thr = random.uniform(0.0, 0.2)
        labels = _labels_from_propensity(seq, win, h_thr, e_thr, stochastic=True)
        ss = "".join(labels)
        chains = _split_chains(ss, target_count=target_chains)
        if any(c["chains"] == chains for c in cases): continue
        cases.append({"chains": chains})
    return {"cases": cases}
