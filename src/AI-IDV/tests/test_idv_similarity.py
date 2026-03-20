from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

try:
    import numpy as np
    from app.services.idv_similarity import cosine_similarity, l2_normalize
except ModuleNotFoundError:  # pragma: no cover - local test env may not include runtime deps
    np = None
    cosine_similarity = None
    l2_normalize = None


@unittest.skipIf(np is None, "numpy is required for similarity tests")
class IdvSimilarityTest(unittest.TestCase):
    def test_l2_normalize_preserves_direction(self) -> None:
        vector = np.array([3.0, 4.0], dtype=np.float32)
        normalized = l2_normalize(vector)

        self.assertAlmostEqual(float(np.linalg.norm(normalized)), 1.0, places=5)
        self.assertAlmostEqual(float(normalized[0]), 0.6, places=5)
        self.assertAlmostEqual(float(normalized[1]), 0.8, places=5)

    def test_cosine_similarity_returns_one_for_same_vector(self) -> None:
        left = np.array([1.0, 2.0, 3.0], dtype=np.float32)
        right = np.array([1.0, 2.0, 3.0], dtype=np.float32)

        self.assertAlmostEqual(cosine_similarity(left, right), 1.0, places=5)


if __name__ == "__main__":
    unittest.main()
