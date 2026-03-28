"""AdaFace ONNX 모델 FP16/INT8 양자화 스크립트.

사용법:
    # FP16 양자화
    python scripts/quantize_adaface_onnx.py \
        --input models/adaface/adaface_ir101_webface12m.onnx \
        --output models/adaface/adaface_ir101_webface12m_fp16.onnx \
        --mode fp16 --verify

    # INT8 동적 양자화
    python scripts/quantize_adaface_onnx.py \
        --input models/adaface/adaface_ir101_webface12m.onnx \
        --output models/adaface/adaface_ir101_webface12m_int8.onnx \
        --mode int8 --verify

필요 의존성 (venv-export):
    pip install onnx onnxruntime onnxconverter-common
"""

from __future__ import annotations

import argparse
import sys
import time
from pathlib import Path

import numpy as np
import onnx


def quantize_fp16(input_path: str, output_path: str) -> None:
    from onnxconverter_common import float16

    model = onnx.load(input_path)
    model_fp16 = float16.convert_float_to_float16(model, keep_io_types=True)
    onnx.save(model_fp16, output_path)


def quantize_int8(input_path: str, output_path: str) -> None:
    from onnxruntime.quantization import QuantType, quantize_dynamic

    quantize_dynamic(input_path, output_path, weight_type=QuantType.QInt8)


def verify_model(model_path: str) -> None:
    import onnxruntime as ort

    session = ort.InferenceSession(model_path, providers=["CPUExecutionProvider"])
    input_name = session.get_inputs()[0].name
    output_name = session.get_outputs()[0].name

    dummy = np.random.randn(1, 3, 112, 112).astype(np.float32)
    result = session.run([output_name], {input_name: dummy})[0]

    assert result.shape == (1, 512), f"Unexpected output shape: {result.shape}"
    assert np.isfinite(result).all(), "Output contains NaN or Inf"

    start = time.perf_counter()
    for _ in range(10):
        session.run([output_name], {input_name: dummy})
    elapsed = (time.perf_counter() - start) / 10

    orig_size = Path(model_path).stat().st_size / (1024 * 1024)
    print(f"  Output shape : {result.shape}")
    print(f"  Model size   : {orig_size:.1f} MB")
    print(f"  Avg latency  : {elapsed * 1000:.1f} ms (CPU, 10 runs)")


def main() -> None:
    parser = argparse.ArgumentParser(description="AdaFace ONNX 양자화")
    parser.add_argument("--input", required=True, help="FP32 ONNX 모델 경로")
    parser.add_argument("--output", required=True, help="양자화된 모델 출력 경로")
    parser.add_argument("--mode", required=True, choices=["fp16", "int8"], help="양자화 모드")
    parser.add_argument("--verify", action="store_true", help="양자화 후 추론 검증")
    args = parser.parse_args()

    if not Path(args.input).exists():
        print(f"Error: 입력 모델이 없습니다: {args.input}", file=sys.stderr)
        sys.exit(1)

    Path(args.output).parent.mkdir(parents=True, exist_ok=True)

    print(f"[{args.mode.upper()}] 양자화 시작: {args.input}")

    if args.mode == "fp16":
        quantize_fp16(args.input, args.output)
    else:
        quantize_int8(args.input, args.output)

    orig_mb = Path(args.input).stat().st_size / (1024 * 1024)
    quant_mb = Path(args.output).stat().st_size / (1024 * 1024)
    print(f"  원본 크기    : {orig_mb:.1f} MB")
    print(f"  양자화 크기  : {quant_mb:.1f} MB ({quant_mb / orig_mb * 100:.0f}%)")
    print(f"  출력 경로    : {args.output}")

    if args.verify:
        print("\n검증 중...")
        verify_model(args.output)
        print("검증 완료.")


if __name__ == "__main__":
    main()
