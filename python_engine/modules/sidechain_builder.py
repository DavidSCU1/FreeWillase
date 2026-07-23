import numpy as np
import math

SC_BONDS = {
    'CB': {'len': 1.53, 'angle': math.radians(110.1)},
    'CG': {'len': 1.52, 'angle': math.radians(113.8)},
    'CD': {'len': 1.52, 'angle': math.radians(113.0)},
}

def place_atom_nerf(a, b, c, bond_len, bond_angle, torsion):
    bc = (c - b) / np.linalg.norm(c - b)
    ab = b - a
    n = np.cross(ab, bc)
    n /= np.linalg.norm(n)
    M = np.array([bc, np.cross(n, bc), n]).T
    d_local = np.array([
        -bond_len * np.cos(bond_angle),
        bond_len * np.sin(bond_angle) * np.cos(torsion),
        bond_len * np.sin(bond_angle) * np.sin(torsion)
    ])
    return c + M @ d_local

def build_sidechain(aa_code, n_coord, ca_coord, c_coord, chi_angles=None):
    if aa_code == "G": return {}
    u_ca_n = (n_coord - ca_coord) / np.linalg.norm(n_coord - ca_coord)
    u_ca_c = (c_coord - ca_coord) / np.linalg.norm(c_coord - ca_coord)
    n_plane = np.cross(u_ca_n, u_ca_c)
    n_plane /= np.linalg.norm(n_plane)
    cb_vec = -0.5366 * u_ca_n - 0.5366 * u_ca_c - 0.6517 * n_plane
    cb_coord = ca_coord + 1.53 * (cb_vec / np.linalg.norm(cb_vec))
    atoms = {'CB': cb_coord}
    
    # Simplified: only build CB for most, unless specific logic is added
    # For MiniFold-v1, a high-quality CB-level model is often enough for initial prediction
    return atoms

def pack_sidechain(aa_code, n_coord, ca_coord, c_coord, local_environment_atoms=None):
    return build_sidechain(aa_code, n_coord, ca_coord, c_coord)
