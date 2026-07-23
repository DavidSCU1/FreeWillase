import math
import numpy as np
from scipy.optimize import minimize

BOND_PARAMS = {
    "n_len": 1.329, "ca_len": 1.46, "c_len": 1.52,
    "ang_C_N_CA": math.radians(121.7), "ang_N_CA_C": math.radians(111.0), "ang_CA_C_N": math.radians(116.2),
}

def place_atom(a, b, c, bond_len, bond_angle, torsion):
    bc = c - b
    bc_u = bc / (np.linalg.norm(bc) + 1e-9)
    n = np.cross(b - a, bc_u)
    n_u = n / (np.linalg.norm(n) + 1e-9)
    nb = np.cross(n_u, bc_u)
    x = -bond_len * math.cos(bond_angle)
    y = bond_len * math.sin(bond_angle) * math.cos(torsion)
    z = bond_len * math.sin(bond_angle) * math.sin(torsion)
    return c + (bc_u * x) + (nb * y) + (n_u * z)

def build_backbone(sequence, phi, psi):
    p = BOND_PARAMS
    n0 = np.array([0.0, 0.0, 0.0])
    ca0 = np.array([p["ca_len"], 0.0, 0.0])
    c0 = place_atom(np.array([-1.0, 0.0, 0.0]), n0, ca0, p["c_len"], p["ang_N_CA_C"], 0.0)
    N, CA, C = [n0], [ca0], [c0]
    for i in range(1, len(sequence)):
        ni = place_atom(N[i-1], CA[i-1], C[i-1], p["n_len"], p["ang_CA_C_N"], psi[i-1])
        cai = place_atom(CA[i-1], C[i-1], ni, p["ca_len"], p["ang_C_N_CA"], math.pi)
        ci = place_atom(C[i-1], ni, cai, p["c_len"], p["ang_N_CA_C"], phi[i])
        N.append(ni); CA.append(cai); C.append(ci)
    return np.array(N), np.array(CA), np.array(C)

def write_pdb(sequence, N, CA, C, out_path):
    three_letter = {"A":"ALA","R":"ARG","N":"ASN","D":"ASP","C":"CYS","Q":"GLN","E":"GLU","G":"GLY","H":"HIS","I":"ILE","L":"LEU","K":"LYS","M":"MET","F":"PHE","P":"PRO","S":"SER","T":"THR","W":"TRP","Y":"TYR","V":"VAL"}
    with open(out_path, "w") as f:
        for i, aa in enumerate(sequence):
            resn = three_letter.get(aa, "UNK")
            for atom, pos in [("N", N[i]), ("CA", CA[i]), ("C", C[i])]:
                f.write(f"ATOM  {i*3+1:5d}  {atom:<3} {resn} A{i+1:4d}    {pos[0]:8.3f}{pos[1]:8.3f}{pos[2]:8.3f}  1.00  0.00\n")
        f.write("END\n")

def optimize_from_ss(sequence, chain_ss_list, output_pdb):
    ss = "".join(chain_ss_list)
    phi = np.array([-math.radians(57 if c == "H" else 119 if c == "E" else 60) for c in ss])
    psi = np.array([-math.radians(47 if c == "H" else -113 if c == "E" else 45) for c in ss])
    N, CA, C = build_backbone(sequence, phi, psi)
    write_pdb(sequence, N, CA, C, output_pdb)
    return True

def run_backbone_fold_multichain(sequence, chain_ss_list, output_pdb):
    return optimize_from_ss(sequence, chain_ss_list, output_pdb)
