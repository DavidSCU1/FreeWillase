import math
import numpy as np
from typing import Optional, Dict, Any

def _read_backbone(pdb_path):
    chains = []
    N, CA, C = [], [], []
    last_chain = None
    with open(pdb_path, "r", encoding="utf-8", errors="replace") as f:
        for line in f:
            if line.startswith("TER"):
                if CA:
                    chains.append({"N": np.array(N), "CA": np.array(CA), "C": np.array(C)})
                N, CA, C = [], [], []
                last_chain = None
                continue
            if not line.startswith("ATOM"):
                continue
            chain_id = line[21:22]
            if last_chain is None: last_chain = chain_id
            if chain_id != last_chain:
                if CA:
                    chains.append({"N": np.array(N), "CA": np.array(CA), "C": np.array(C)})
                N, CA, C = [], [], []
                last_chain = chain_id
            atom = line[12:16].strip()
            v = np.array([float(line[30:38]), float(line[38:46]), float(line[46:54])])
            if atom == "N": N.append(v)
            elif atom == "CA": CA.append(v)
            elif atom == "C": C.append(v)
    if CA:
        chains.append({"N": np.array(N), "CA": np.array(CA), "C": np.array(C)})
    return chains

def _dihedral(a, b, c, d):
    b0, b1, b2 = a - b, c - b, d - c
    b1 /= np.linalg.norm(b1) + 1e-9
    v = b0 - np.dot(b0, b1) * b1
    w = b2 - np.dot(b2, b1) * b1
    return math.degrees(math.atan2(np.dot(np.cross(b1, v), w), np.dot(v, w)))

def _rotate_block(points, axis_point, axis_dir, angle):
    axis = axis_dir / (np.linalg.norm(axis_dir) + 1e-9)
    c, s = math.cos(angle), math.sin(angle)
    u = axis
    R = np.array([
        [c + u[0]**2*(1-c), u[0]*u[1]*(1-c)-u[2]*s, u[0]*u[2]*(1-c)+u[1]*s],
        [u[1]*u[0]*(1-c)+u[2]*s, c+u[1]**2*(1-c), u[1]*u[2]*(1-c)-u[0]*s],
        [u[2]*u[0]*(1-c)-u[1]*s, u[2]*u[1]*(1-c)+u[0]*s, c+u[2]**2*(1-c)]
    ])
    return (points - axis_point) @ R.T + axis_point

def _ramachandran_adjust(chains, max_delta_deg=10.0):
    for ch in chains:
        N, CA, C = ch["N"], ch["CA"], ch["C"]
        n = len(CA)
        for i in range(n):
            if i > 0:
                phi = _dihedral(C[i-1], N[i], CA[i], C[i])
                dphi = math.radians(max(-max_delta_deg, min(max_delta_deg, -60.0 - phi)))
                C[i:] = _rotate_block(C[i:].copy(), N[i], CA[i]-N[i], dphi)
            if i < n - 1:
                psi = _dihedral(N[i], CA[i], C[i], N[i+1])
                dpsi = math.radians(max(-max_delta_deg, min(max_delta_deg, -45.0 - psi)))
                N[i+1:] = _rotate_block(N[i+1:].copy(), C[i], C[i]-CA[i], dpsi)
    return chains

def _write_backbone(pdb_in, chains, pdb_out):
    lines = []
    serial, rid = 1, 1
    for ci, ch in enumerate(chains):
        cid = chr(65 + (ci % 26))
        for i in range(len(ch["CA"])):
            for atom, pos in [("N", ch["N"][i]), ("CA", ch["CA"][i]), ("C", ch["C"][i])]:
                lines.append(f"ATOM  {serial:5d}  {atom:<3} UNK {cid}{rid:4d}    {pos[0]:8.3f}{pos[1]:8.3f}{pos[2]:8.3f}  1.00  0.00           {atom[0]}")
                serial += 1
            rid += 1
        lines.append("TER")
    lines.append("END")
    with open(pdb_out, "w") as f: f.write("\n".join(lines) + "\n")

def run_refinements(pdb_path, sequence, do_ramachandran=True):
    if do_ramachandran:
        chains = _read_backbone(pdb_path)
        chains = _ramachandran_adjust(chains)
        _write_backbone(pdb_path, chains, pdb_path)
        return True
    return False
