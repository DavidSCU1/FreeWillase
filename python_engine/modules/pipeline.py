import os
import json
from modules.ss_generator import pybiomed_ss_candidates
from modules.ark_module import ark_vote_cases, ark_refine_structure, ark_analyze_sequence
from modules.backbone_predictor import run_backbone_fold_multichain
from modules.refine import run_refinements

def run_pipeline(sequence: str, api_key: str, out_dir: str, env_text: str = ""):
    os.makedirs(out_dir, exist_ok=True)
    prefix = "pred"
    
    # 1. Generate SS candidates
    ss_result = pybiomed_ss_candidates(sequence, num=5)
    cases = ss_result.get("cases", [])
    
    # 2. AI Vote
    models = [
        "doubao-seed-1-6-251015",
        "deepseek-v3-2-251201",
    ]
    votes = ark_vote_cases(models, sequence, env_text, cases, api_key=api_key)
    best_idx = votes.get("best_idx", 0)
    best_case = cases[best_idx]
    
    # 3. AI Refine if env_text exists
    final_chains = best_case["chains"]
    if env_text:
        refined, err = ark_refine_structure(models[0], sequence, env_text, final_chains, api_key=api_key)
        if refined:
            final_chains = refined

    # 4. Backbone Fold
    pdb_path = os.path.join(out_dir, f"{prefix}.pdb")
    run_backbone_fold_multichain(sequence, final_chains, pdb_path)
    
    # 5. Physical Refine
    run_refinements(pdb_path, sequence, do_ramachandran=True)
    
    # 6. AI Analysis
    analysis = ark_analyze_sequence(sequence, api_key=api_key)
    
    with open(pdb_path, "r") as f:
        pdb_content = f.read()
        
    return {
        "pdb": pdb_content,
        "analysis": analysis,
        "chains": final_chains
    }
