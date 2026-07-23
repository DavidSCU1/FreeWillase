import os
import json
from typing import List, Dict, Any, Optional, Tuple
import requests
import concurrent.futures

def _ark_endpoint() -> str:
    return os.environ.get("ARK_API_URL", "https://ark.cn-beijing.volces.com/api/v3/chat/completions")

def _ark_headers(api_key: str) -> Dict[str, str]:
    return {"Content-Type": "application/json", "Authorization": f"Bearer {api_key}"}

def get_default_models() -> List[str]:
    return [
        "doubao-seed-1-6-251015",
        "deepseek-v3-2-251201",
        "doubao-1-5-pro-256k-250115",
        "kimi-k2-thinking-251104",
        "deepseek-r1-250528",
    ]

def ark_eval_case(model: str, sequence: str, environment: Optional[str], chains: List[str], req_text: Optional[str] = None, api_key: str = None, timeout: int = 120) -> Tuple[Optional[float], Optional[str]]:
    if not api_key:
        return None, "API Key is required"
    url = _ark_endpoint()
    headers = _ark_headers(api_key)
    payload = {
        "model": model,
        "messages": [
            {"role": "system", "content": "仅返回一个0-1的数字。"},
            {"role": "user", "content": json.dumps({
                "sequence": sequence,
                "environment": environment or "",
                "chains": chains or [],
                "requirements": req_text or "",
                "instruction": "Estimate probability (0-1) for the CASE; return only a number"
            }, ensure_ascii=False)}
        ]
    }
    try:
        r = requests.post(url, headers=headers, json=payload, timeout=timeout)
        if r.status_code != 200:
            return None, f"HTTP {r.status_code}: {r.text[:100]}"
        data = r.json()
        s = (data["choices"][0]["message"]["content"] or "").strip()
        try:
            return float(s), None
        except Exception:
            for line in s.splitlines():
                line = line.strip()
                try:
                    return float(line), None
                except Exception:
                    pass
            return None, f"Parse Error: {s[:50]}"
    except Exception as e:
        return None, str(e)

def ark_refine_structure(model: str, sequence: str, environment: str, chains: List[str], api_key: str, timeout: int = 300) -> Tuple[Optional[List[str]], Optional[str]]:
    if not api_key:
        return None, "API Key is required"
    url = _ark_endpoint()
    headers = _ark_headers(api_key)
    
    prompt = f"""
Given the protein sequence (length {len(sequence)}) and environment description: "{environment}", 
please REFINE the following secondary structure prediction to be more physically realistic for this environment.

Current prediction (chains):
{json.dumps(chains)}

Rules:
1. Return ONLY a JSON list of strings (chains).
2. The total length of residues (H/E/C) must exactly match the input sequence length.
3. Consider the environment: e.g., if membrane, helices might be preferred; if high temp, structure might be more compact.
4. Do not output any explanation, just the JSON list.
"""
    payload = {
        "model": model,
        "messages": [
            {"role": "system", "content": "You are a protein structure expert. Output only valid JSON."},
            {"role": "user", "content": prompt}
        ]
    }
    
    try:
        r = requests.post(url, headers=headers, json=payload, timeout=timeout)
        if r.status_code != 200:
            return None, f"HTTP {r.status_code}"
        data = r.json()
        s = (data["choices"][0]["message"]["content"] or "").strip()
        
        # Simple extraction
        if "```" in s:
            import re
            match = re.search(r'\[.*\]', s, re.DOTALL)
            if match:
                s = match.group(0)
        
        refined = json.loads(s)
        if isinstance(refined, list):
            return refined, None
        return None, "Invalid format"
    except Exception as e:
        return None, str(e)

def ark_vote_cases(models: List[str], sequence: str, environment: Optional[str], cases: List[Dict[str, Any]], api_key: str) -> Dict[str, Any]:
    if not api_key:
        return {"best_idx": 0, "best_score": 0.0, "cases": []}
    
    results = []
    with concurrent.futures.ThreadPoolExecutor(max_workers=5) as executor:
        for idx, case in enumerate(cases):
            futures = [executor.submit(ark_eval_case, m, sequence, environment, case.get("chains", []), api_key=api_key) for m in models]
            scores = []
            for f in concurrent.futures.as_completed(futures):
                score, err = f.result()
                if score is not None:
                    scores.append(score)
            
            avg = sum(scores) / len(scores) if scores else 0.0
            results.append({"idx": idx, "avg": avg})
            
    best = max(results, key=lambda x: x["avg"])
    return {"best_idx": best["idx"], "best_score": best["avg"], "cases": results}

def ark_analyze_sequence(sequence: str, api_key: str) -> str:
    if not api_key:
        return "API Key missing for analysis"
    url = _ark_endpoint()
    headers = _ark_headers(api_key)
    prompt = f"Analyze protein sequence: {sequence}\nProvide domains, function, and active sites."
    payload = {
        "model": "doubao-pro-4k", 
        "messages": [{"role": "user", "content": prompt}]
    }
    try:
        r = requests.post(url, headers=headers, json=payload, timeout=60)
        return r.json()["choices"][0]["message"]["content"]
    except Exception as e:
        return f"Analysis failed: {e}"
