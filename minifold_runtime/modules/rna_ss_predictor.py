import random
import numpy as np

def can_pair(b1, b2):
    pair = b1 + b2
    return pair in ["AU", "UA", "CG", "GC", "GU", "UG"]

def nussinov_dp(sequence, min_loop=3, noise_level=0.0):
    n = len(sequence)
    dp = np.zeros((n, n))
    
    # DP to find max base pairs
    for k in range(1, n):
        for i in range(n - k):
            j = i + k
            if j - i <= min_loop:
                dp[i][j] = dp[i][j-1]
                continue
                
            # Don't pair i and j
            score1 = dp[i][j-1]
            
            # Pair i and j
            score2 = 0
            if can_pair(sequence[i], sequence[j]):
                noise = random.uniform(0, noise_level)
                score2 = dp[i+1][j-1] + 1.0 + noise
                
            # Pair j with some t between i and j
            score3 = 0
            for t in range(i, j - min_loop):
                if can_pair(sequence[t], sequence[j]):
                    noise = random.uniform(0, noise_level)
                    score3 = max(score3, dp[i][t-1] + dp[t+1][j-1] + 1.0 + noise)
                    
            dp[i][j] = max(score1, score2, score3)
            
    # Backtracking to find the structure
    pairs = []
    def traceback(i, j):
        if i >= j:
            return
        if dp[i][j] == dp[i][j-1]:
            traceback(i, j-1)
            return
        
        for t in range(i, j - min_loop):
            if can_pair(sequence[t], sequence[j]):
                noise_bound = 1.0 + noise_level
                # Check if we paired t and j
                expected_score = dp[i][t-1] if t > 0 else 0
                expected_score += dp[t+1][j-1] + 1.0
                if abs(dp[i][j] - expected_score) <= noise_bound:
                    pairs.append((t, j))
                    traceback(i, t-1)
                    traceback(t+1, j-1)
                    return
        
        # Or i and j paired directly
        if can_pair(sequence[i], sequence[j]):
            pairs.append((i, j))
            traceback(i+1, j-1)
            
    traceback(0, n-1)
    
    dot_bracket = ["."] * n
    for p in pairs:
        dot_bracket[p[0]] = "("
        dot_bracket[p[1]] = ")"
        
    return "".join(dot_bracket), pairs

def generate_rna_candidates(sequence, num=3):
    candidates = []
    # 1. Base Nussinov (deterministic max pairs)
    db, pairs = nussinov_dp(sequence, min_loop=3, noise_level=0.0)
    candidates.append({"dot_bracket": db, "pairs": pairs, "type": "MFE_Proxy", "score": len(pairs)})
    
    # 2. Stochastic variants
    for _ in range(num - 1):
        db, pairs = nussinov_dp(sequence, min_loop=random.choice([3, 4, 5]), noise_level=0.5)
        candidates.append({"dot_bracket": db, "pairs": pairs, "type": "Stochastic", "score": len(pairs)})
        
    # Filter unique
    unique_candidates = []
    seen = set()
    for c in candidates:
        if c["dot_bracket"] not in seen:
            seen.add(c["dot_bracket"])
            unique_candidates.append(c)
            
    # Sort by number of pairs (score) descending
    unique_candidates.sort(key=lambda x: x["score"], reverse=True)
    return unique_candidates
