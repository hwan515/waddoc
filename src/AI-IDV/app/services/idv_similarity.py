from __future__ import annotations

import numpy as np


def l2_normalize(vector: np.ndarray) -> np.ndarray:
    norm = np.linalg.norm(vector)
    if norm == 0.0:
        return vector
    return vector / norm


def cosine_similarity(left: np.ndarray, right: np.ndarray) -> float:
    left_normalized = l2_normalize(left.astype(np.float32))
    right_normalized = l2_normalize(right.astype(np.float32))
    return float(np.dot(left_normalized, right_normalized))
