"""양자화 모델 품질/속도 비교 스크립트.

FP32, FP16, INT8 모델에 동일 입력을 추론하여
임베딩 cosine similarity와 추론 latency를 비교한다.

사용법:
    python scripts/compare_quantized_models.py \
        --model-dir models/adaface \
        --basename adaface_ir101_webface12m

필요 의존성 (venv-export):
    pip install onnxruntime numpy
"""

from __future__ import annotations

import argparse
import json
import time
from pathlib import Path

import numpy as np


def cosine_similarity(a: np.ndarray, b: np.ndarray) -> float:
    return float(np.dot(a, b) / (np.linalg.norm(a) * np.linalg.norm(b)))


def benchmark(session, input_name: str, output_name: str, dummy: np.ndarray, n_warmup: int = 5, n_runs: int = 50):
    for _ in range(n_warmup):
        session.run([output_name], {input_name: dummy})

    times = []
    for _ in range(n_runs):
        start = time.perf_counter()
        session.run([output_name], {input_name: dummy})
        times.append(time.perf_counter() - start)

    return {
        "mean_ms": np.mean(times) * 1000,
        "std_ms": np.std(times) * 1000,
        "p50_ms": np.percentile(times, 50) * 1000,
        "p95_ms": np.percentile(times, 95) * 1000,
    }


def load_and_infer(model_path: str, dummy: np.ndarray):
    import onnxruntime as ort

    session = ort.InferenceSession(model_path, providers=["CPUExecutionProvider"])
    input_name = session.get_inputs()[0].name
    output_name = session.get_outputs()[0].name

    embedding = session.run([output_name], {input_name: dummy})[0][0]
    latency = benchmark(session, input_name, output_name, dummy)
    size_mb = Path(model_path).stat().st_size / (1024 * 1024)

    return embedding, latency, size_mb


def main() -> None:
    parser = argparse.ArgumentParser(description="양자화 모델 비교")
    parser.add_argument("--model-dir", default="models/adaface", help="모델 디렉터리")
    parser.add_argument("--basename", default="adaface_ir101_webface12m", help="모델 기본 이름")
    parser.add_argument("--output", default=None, help="JSON 리포트 출력 경로 (없으면 콘솔만)")
    args = parser.parse_args()

    variants = {
        "fp32": Path(args.model_dir) / f"{args.basename}.onnx",
        "fp16": Path(args.model_dir) / f"{args.basename}_fp16.onnx",
        "int8": Path(args.model_dir) / f"{args.basename}_int8.onnx",
    }

    available = {k: v for k, v in variants.items() if v.exists()}
    if "fp32" not in available:
        print("Error: FP32 기준 모델이 없습니다.")
        return

    print(f"발견된 모델: {', '.join(available.keys())}\n")

    np.random.seed(42)
    dummy = np.random.randn(1, 3, 112, 112).astype(np.float32)

    results = {}
    embeddings = {}
    for variant, path in available.items():
        print(f"[{variant.upper()}] {path}")
        emb, latency, size_mb = load_and_infer(str(path), dummy)
        embeddings[variant] = emb
        results[variant] = {"size_mb": round(size_mb, 1), "latency": {k: round(v, 2) for k, v in latency.items()}}
        print(f"  크기: {size_mb:.1f} MB | 평균: {latency['mean_ms']:.2f} ms | p95: {latency['p95_ms']:.2f} ms")

    print("\n--- Cosine Similarity vs FP32 ---")
    fp32_emb = embeddings["fp32"]
    for variant, emb in embeddings.items():
        sim = cosine_similarity(fp32_emb, emb)
        results[variant]["cosine_vs_fp32"] = round(sim, 6)
        status = "PASS" if sim > 0.95 else "WARN"
        print(f"  {variant.upper():5s}: {sim:.6f}  [{status}]")

    report = {"variants": results}

    if args.output:
        Path(args.output).parent.mkdir(parents=True, exist_ok=True)
        with open(args.output, "w", encoding="utf-8") as f:
            json.dump(report, f, indent=2, ensure_ascii=False)
        print(f"\n리포트 저장: {args.output}")

    print("\n완료.")


if __name__ == "__main__":
    main()
