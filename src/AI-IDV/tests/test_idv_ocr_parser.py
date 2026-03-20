from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.services.idv_ocr_parser import IdvOcrParser


class IdvOcrParserTest(unittest.TestCase):
    def setUp(self) -> None:
        self.parser = IdvOcrParser()

    def test_parse_extracts_expected_fields(self) -> None:
        result = self.parser.parse(
            texts=[
                "주민등록증",
                "성명 홍길동",
                "580315-1234567",
                "주소 경북 울릉군 울릉읍 도동2길 66",
            ],
            scores=[0.99, 0.96, 0.97, 0.95],
        )

        self.assertEqual(result.name, "홍길동")
        self.assertEqual(result.rrn_masked, "580315-1******")
        self.assertEqual(result.address, "경북 울릉군 울릉읍 도동2길 66")
        self.assertGreater(result.confidence, 0.95)

    def test_mask_rrn_returns_none_for_invalid_input(self) -> None:
        self.assertIsNone(self.parser.mask_rrn("1234"))


if __name__ == "__main__":
    unittest.main()
