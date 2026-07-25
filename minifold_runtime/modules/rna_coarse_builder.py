import numpy as np
import math

def build_coarse_rna_3d(sequence, dot_bracket, pairs):
    n = len(sequence)
    coords = []
    
    # Very naive 3D generation:
    # Arrange bases in a circle or spiral
    radius = max(10.0, n * 1.5 / (2 * math.pi))
    
    for i in range(n):
        angle = i * (2 * math.pi / n)
        x = radius * math.cos(angle)
        y = radius * math.sin(angle)
        z = i * 0.5
        coords.append([x, y, z])
        
    # To make pairs closer, we can just average their positions (naive spring relaxation)
    coords = np.array(coords)
    for _ in range(50):
        new_coords = coords.copy()
        # Backbone continuity
        for i in range(1, n):
            d = coords[i] - coords[i-1]
            dist = np.linalg.norm(d) + 1e-9
            if dist > 6.0:
                shift = d * (dist - 6.0) / dist * 0.1
                new_coords[i] -= shift
                new_coords[i-1] += shift
        # Pairing proximity
        for (i, j) in pairs:
            d = coords[i] - coords[j]
            dist = np.linalg.norm(d) + 1e-9
            if dist > 15.0:
                shift = d * (dist - 15.0) / dist * 0.2
                new_coords[i] -= shift
                new_coords[j] += shift
        coords = new_coords
        
    return coords

def write_rna_pdb(sequence, coords, out_path):
    with open(out_path, "w", encoding="utf-8") as f:
        f.write("REMARK Coarse RNA Model\n")
        for i, (res, pos) in enumerate(zip(sequence, coords)):
            # Use 'A', 'C', 'G', 'U' as residue names for RNA
            atom_name = "P"
            res_name = res
            chain_id = "A"
            res_seq = i + 1
            x, y, z = pos
            f.write(f"ATOM  {i+1:5d}  {atom_name:<3s} {res_name:>3s} {chain_id}{res_seq:4d}    {x:8.3f}{y:8.3f}{z:8.3f}  1.00  0.00           P\n")
        f.write("TER\nEND\n")
