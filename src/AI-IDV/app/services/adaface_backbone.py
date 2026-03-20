from __future__ import annotations

from collections import namedtuple

import torch
import torch.nn as nn


class Flatten(nn.Module):
    def forward(self, inputs: torch.Tensor) -> torch.Tensor:
        return inputs.view(inputs.size(0), -1)


class BasicBlockIR(nn.Module):
    def __init__(self, in_channel: int, depth: int, stride: int) -> None:
        super().__init__()
        if in_channel == depth:
            self.shortcut_layer = nn.MaxPool2d(1, stride)
        else:
            self.shortcut_layer = nn.Sequential(
                nn.Conv2d(in_channel, depth, (1, 1), stride, bias=False),
                nn.BatchNorm2d(depth),
            )

        self.res_layer = nn.Sequential(
            nn.BatchNorm2d(in_channel),
            nn.Conv2d(in_channel, depth, (3, 3), (1, 1), 1, bias=False),
            nn.BatchNorm2d(depth),
            nn.PReLU(depth),
            nn.Conv2d(depth, depth, (3, 3), stride, 1, bias=False),
            nn.BatchNorm2d(depth),
        )

    def forward(self, inputs: torch.Tensor) -> torch.Tensor:
        shortcut = self.shortcut_layer(inputs)
        residual = self.res_layer(inputs)
        return residual + shortcut


class Bottleneck(namedtuple("Block", ["in_channel", "depth", "stride"])):
    pass


def _get_block(in_channel: int, depth: int, num_units: int, stride: int = 2) -> list[Bottleneck]:
    return [Bottleneck(in_channel, depth, stride)] + [Bottleneck(depth, depth, 1) for _ in range(num_units - 1)]


def _get_blocks(num_layers: int) -> list[list[Bottleneck]]:
    block_map = {
        18: [
            _get_block(64, 64, 2),
            _get_block(64, 128, 2),
            _get_block(128, 256, 2),
            _get_block(256, 512, 2),
        ],
        34: [
            _get_block(64, 64, 3),
            _get_block(64, 128, 4),
            _get_block(128, 256, 6),
            _get_block(256, 512, 3),
        ],
        50: [
            _get_block(64, 64, 3),
            _get_block(64, 128, 4),
            _get_block(128, 256, 14),
            _get_block(256, 512, 3),
        ],
        100: [
            _get_block(64, 64, 3),
            _get_block(64, 128, 13),
            _get_block(128, 256, 30),
            _get_block(256, 512, 3),
        ],
    }
    if num_layers not in block_map:
        raise ValueError(f"Unsupported AdaFace architecture depth: {num_layers}")
    return block_map[num_layers]


class Backbone(nn.Module):
    def __init__(self, num_layers: int) -> None:
        super().__init__()
        self.input_layer = nn.Sequential(
            nn.Conv2d(3, 64, (3, 3), 1, 1, bias=False),
            nn.BatchNorm2d(64),
            nn.PReLU(64),
        )
        blocks = _get_blocks(num_layers)
        modules: list[nn.Module] = []
        for block in blocks:
            for bottleneck in block:
                modules.append(BasicBlockIR(bottleneck.in_channel, bottleneck.depth, bottleneck.stride))
        self.body = nn.Sequential(*modules)
        self.output_layer = nn.Sequential(
            nn.BatchNorm2d(512),
            nn.Dropout(0.4),
            Flatten(),
            nn.Linear(512 * 7 * 7, 512),
            nn.BatchNorm1d(512, affine=False),
        )

    def forward(self, inputs: torch.Tensor) -> tuple[torch.Tensor, torch.Tensor]:
        outputs = self.input_layer(inputs)
        outputs = self.body(outputs)
        outputs = self.output_layer(outputs)
        norms = torch.norm(outputs, 2, 1, True)
        normalized = torch.div(outputs, norms)
        return normalized, norms


def build_model(model_name: str) -> Backbone:
    depth_map = {
        "ir_18": 18,
        "ir_34": 34,
        "ir_50": 50,
        "ir_101": 100,
    }
    if model_name not in depth_map:
        raise ValueError(f"Unsupported AdaFace architecture: {model_name}")
    return Backbone(num_layers=depth_map[model_name])
