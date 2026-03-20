from pathlib import Path
import os
import sys
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

try:
    from app.core.config import Settings
except ModuleNotFoundError:  # pragma: no cover - local test env may not include runtime deps
    Settings = None


@unittest.skipIf(Settings is None, "pydantic-settings is required for config tests")
class SettingsAliasTest(unittest.TestCase):
    def test_env_aliases_are_resolved(self) -> None:
        with patch.dict(
            os.environ,
            {
                "CORS_ORIGINS": "http://localhost:5173,http://localhost:3000",
                "IDV_DET_SIZE": "320,320",
            },
            clear=False,
        ):
            settings = Settings(_env_file=None)

        self.assertEqual(
            settings.cors_origins,
            ["http://localhost:5173", "http://localhost:3000"],
        )
        self.assertEqual(settings.idv_det_size, (320, 320))


if __name__ == "__main__":
    unittest.main()
