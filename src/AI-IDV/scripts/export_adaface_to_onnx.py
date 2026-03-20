from __future__ import annotations

import argparse
from pathlib import Path
import sys

import torch


PROJECT_ROOT = Path(__file__).resolve().parents[1]
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))

from app.services.adaface_backbone import build_model


def normalize_state_dict_key(key: str) -> str:
    normalized = key
    if normalized.startswith("module."):
        normalized = normalized[7:]
    if normalized.startswith("model."):
        normalized = normalized[6:]
    return normalized


def should_keep_state_dict_key(key: str) -> bool:
    normalized = normalize_state_dict_key(key)
    return bool(normalized) and not normalized.startswith("head.")


class AdaFaceExportWrapper(torch.nn.Module):
    def __init__(self, model: torch.nn.Module) -> None:
        super().__init__()
        self.model = model

    def forward(self, inputs: torch.Tensor) -> torch.Tensor:
        embeddings, _ = self.model(inputs)
        return embeddings


def export_checkpoint_to_onnx(
    checkpoint_path: Path,
    output_path: Path,
    architecture: str,
    opset: int,
) -> None:
    device = torch.device("cpu")
    model = build_model(architecture)
    checkpoint = torch.load(str(checkpoint_path), map_location=device)
    state_dict = checkpoint.get("state_dict", checkpoint)
    model_state_dict = {
        normalize_state_dict_key(key): value
        for key, value in state_dict.items()
        if should_keep_state_dict_key(key)
    }
    model.load_state_dict(model_state_dict, strict=True)
    model.to(device)
    model.eval()

    wrapper = AdaFaceExportWrapper(model).eval()
    output_path.parent.mkdir(parents=True, exist_ok=True)

    dummy_input = torch.randn(1, 3, 112, 112, dtype=torch.float32, device=device)
    try:
        torch.onnx.export(
            wrapper,
            dummy_input,
            str(output_path),
            export_params=True,
            opset_version=opset,
            do_constant_folding=True,
            input_names=["input"],
            output_names=["embedding"],
            dynamic_axes={
                "input": {0: "batch_size"},
                "embedding": {0: "batch_size"},
            },
        )
    except ModuleNotFoundError as exc:
        if exc.name == "onnxscript":
            raise RuntimeError(
                "onnxscript is required for ONNX export. Install requirements-export.txt again."
            ) from exc
        raise


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Export AdaFace checkpoint to ONNX.")
    parser.add_argument(
        "--checkpoint",
        default="./models/adaface/adaface_ir101_webface12m.ckpt",
        help="Path to the AdaFace checkpoint file.",
    )
    parser.add_argument(
        "--output",
        default="./models/adaface/adaface_ir101_webface12m.onnx",
        help="Path to the exported ONNX file.",
    )
    parser.add_argument(
        "--architecture",
        default="ir_101",
        help="AdaFace backbone architecture name.",
    )
    parser.add_argument(
        "--opset",
        type=int,
        default=17,
        help="ONNX opset version.",
    )
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    checkpoint_path = Path(args.checkpoint).expanduser().resolve()
    output_path = Path(args.output).expanduser().resolve()

    if not checkpoint_path.exists():
        raise FileNotFoundError(f"AdaFace checkpoint not found: {checkpoint_path}")

    export_checkpoint_to_onnx(
        checkpoint_path=checkpoint_path,
        output_path=output_path,
        architecture=args.architecture,
        opset=args.opset,
    )
    print(f"Exported AdaFace ONNX model to {output_path}")


if __name__ == "__main__":
    main()
