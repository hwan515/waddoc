from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

try:
    import numpy as np
    from app.services.idv_model_registry import _prepare_adaface_input
except ModuleNotFoundError:  # pragma: no cover - local test env may not include runtime deps
    np = None
    _prepare_adaface_input = None


@unittest.skipIf(np is None, "numpy is required for model registry tests")
class AdaFacePreprocessTest(unittest.TestCase):
    def test_prepare_adaface_input_preserves_bgr_channel_order(self) -> None:
        image = np.zeros((112, 112, 3), dtype=np.uint8)
        image[:, :, 0] = 0
        image[:, :, 1] = 127
        image[:, :, 2] = 255

        prepared = _prepare_adaface_input(image)

        self.assertEqual(prepared.shape, (1, 3, 112, 112))
        self.assertAlmostEqual(float(prepared[0, 0, 0, 0]), -1.0, places=5)
        self.assertAlmostEqual(float(prepared[0, 1, 0, 0]), (127.0 / 255.0 - 0.5) / 0.5, places=5)
        self.assertAlmostEqual(float(prepared[0, 2, 0, 0]), 1.0, places=5)


if __name__ == "__main__":
    unittest.main()
