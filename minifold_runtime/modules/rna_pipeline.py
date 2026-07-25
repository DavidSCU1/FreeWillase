import os
import json
import traceback
import random

from modules.rna_ss_predictor import generate_rna_candidates
from modules.rna_coarse_builder import build_coarse_rna_3d, write_rna_pdb

def run_pipeline(
    fasta,
    outdir,
    env_text,
    use_igpu,
    target_chains=None,
    backend="auto",
    log_callback=print,
):
    """
    Core MiniFold RNA pipeline execution logic.
    """
    try:
        os.makedirs(outdir, exist_ok=True)
        log_callback(f"读取 FASTA: {fasta}")
        
        with open(fasta, "r", encoding="utf-8") as f:
            lines = f.readlines()
            
        sequence = ""
        for line in lines:
            if not line.startswith(">"):
                sequence += line.strip()
                
        sequence = sequence.upper().replace("T", "U")
        log_callback(f"处理 RNA 序列: 长度 {len(sequence)}")
        
        input_base = os.path.basename(fasta)
        prefix = os.path.splitext(input_base)[0]
        workdir = os.path.join(outdir, prefix)
        three_d_dir = os.path.join(workdir, "3d_structures")
        os.makedirs(three_d_dir, exist_ok=True)
        
        log_callback("生成 RNA 二级结构候选...")
        candidates = generate_rna_candidates(sequence, num=3)
        best_candidate = candidates[0]
        dot_bracket = best_candidate["dot_bracket"]
        pairs = best_candidate["pairs"]
        log_callback(f"最佳候选 (Score: {best_candidate['score']}):\n{dot_bracket}")
        
        log_callback("组装粗粒度 RNA 3D 模型...")
        pdb_name = f"{prefix}_rna_model_1.pdb"
        pdb_path = os.path.join(three_d_dir, pdb_name)
        
        coords = build_coarse_rna_3d(sequence, dot_bracket, pairs)
        write_rna_pdb(sequence, coords, pdb_path)
            
        log_callback("生成分析报告...")
        report_path = os.path.join(workdir, f"{prefix}_report.md")
        with open(report_path, "w", encoding="utf-8") as f:
            f.write(f"# {prefix} RNA 结构预测报告\n\n")
            f.write(f"## 基本信息\n- 序列长度: {len(sequence)}\n")
            f.write(f"- 二级结构: `{dot_bracket}`\n")
            f.write(f"- 碱基配对数: {len(pairs)}\n\n")
            f.write("## 结构模型\n")
            f.write(f"| 模型 | 文件 |\n|---|---|\n")
            f.write(f"| Model 1 | [View 3D](3d_structures/{pdb_name}) |\n")
            
        log_callback("==== 所有任务已完成 ====")
        
    except Exception as e:
        log_callback(f"发生未捕获异常:\n{traceback.format_exc()}")
