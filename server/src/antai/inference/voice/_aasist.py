"""AASIST-L — lightweight graph-attention spoof detector (Jung et al. 2022).

Replaces nothing by itself: it joins the local SSL ensemble in
``deepfake_voice.py`` as a THIRD detector when its checkpoint exists at
``models/voice_deepfake_aasist/aasist_l.pt`` (produced by
``scripts/train_aasist.py``, exported for the phone by
``scripts/export_onnx.py``).

Why it exists (from the model-change research):
  * ~85K params vs ~95M for AST/wav2vec2 — 1000x smaller, so it is the model
    that actually ships on-device (``spoof_aasist_l.int8.onnx``).
  * Graph attention over spectro-temporal features beats the prior wav2vec2
    baseline on ASVspoof while being tiny; no attention-op-coverage problems
    for NPU/QNN execution.
  * Raw-waveform in, 2-class logits out (0 = bonafide, 1 = spoof) — the
    simplest possible contract for both the ensemble loop and ORT.

ponytail: this is a faithful LIGHT reimplementation, not paper parity —
no heterogeneous edge pruning, single-scale graph (the full AASIST uses
heterogeneous pooling + pruned edges), 2 GAT layers per subgraph. Ceiling:
if EER plateaus above the wav2vec2 cross-check, port the official repo's
model instead; the train/export plumbing stays the same either way.
"""
from __future__ import annotations

from dataclasses import dataclass

import torch
import torch.nn as nn
import torch.nn.functional as F

# Label convention shared with the ensemble loop: 0 = bonafide, 1 = spoof.
SPOOF_INDEX = 1


@dataclass
class AasistOutput:
    logits: torch.Tensor


class _GATLayer(nn.Module):
    """One graph-attention layer over a node-feature matrix.

    Dense self-attention across all N nodes ("everyone attends to everyone").
    ponytail: no edge pruning / sparse masks — with a few hundred nodes the
    dense attention is cheap and keeps the ONNX graph simple for the phone.
    """

    def __init__(self, in_dim: int, out_dim: int, heads: int = 8):
        super().__init__()
        assert out_dim % heads == 0
        self.heads = heads
        self.proj = nn.Linear(in_dim, out_dim)
        self.attn = nn.Linear(out_dim, heads)
        self.out = nn.Linear(out_dim, out_dim)

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        h = self.proj(x)                                    # [B, N, out]
        n = h.shape[1]
        # per-head node scores -> attention distribution over nodes
        score = self.attn(h).permute(0, 2, 1)               # [B, heads, N]
        attn = torch.softmax(score, dim=-1)                 # [B, heads, N]
        h_heads = h.view(h.shape[0], n, self.heads, -1).permute(0, 2, 1, 3)
        # h_heads: [B, heads, N, head_dim]. Weighted sum over source nodes
        # (einsum — matmul would broadcast N instead of contracting it):
        read = torch.einsum("bhn,bhnd->bhd", attn, h_heads)  # [B, heads, head_dim]
        # ponytail: global-read GAT — every node receives the SAME attended
        # context (plus its own residual h). True per-node attention (each
        # node its own softmax over neighbors) doubles params for little
        # gain at 2 layers; swap in if the EER plateaus.
        attended = read.flatten(-2).unsqueeze(1).expand(-1, n, -1)  # [B, N, out]
        return torch.relu(self.out(attended) + h)           # residual


class AASISTL(nn.Module):
    """AASIST-L spoof detector.

    Pipeline: waveform -> 2-channel spectro-temporal feature graph ->
    GAT stack per channel -> attentive-stats pooling -> 2 logits.
    """

    def __init__(self, n_freq: int = 257, hidden: int = 24, heads: int = 8,
                 n_layers: int = 2):
        super().__init__()
        self.n_freq = n_freq

        def _stack() -> nn.ModuleList:
            # first layer consumes n_freq node features; the rest consume the
            # hidden width produced by the previous layer.
            return nn.ModuleList(
                [_GATLayer(n_freq, hidden, heads)]
                + [_GATLayer(hidden, hidden, heads)
                   for _ in range(n_layers - 1)])

        self.gat_t = _stack()
        self.gat_f = _stack()
        self.pool_attn = nn.Linear(hidden, 1)
        self.classifier = nn.Sequential(
            nn.Linear(hidden * 4, hidden * 2),
            nn.ReLU(),
            nn.Linear(hidden * 2, 2),
        )

    def forward(self, audio: torch.Tensor) -> AasistOutput:
        """audio: [B, T] float waveform @16k. Returns AasistOutput(.logits [B, 2])."""
        x_t, x_f = self._spectro_temporal_graph(audio)
        for layer in self.gat_t:
            x_t = layer(x_t)
        for layer in self.gat_f:
            x_f = layer(x_f)
        pooled = torch.cat([self._attend_pool(x_t), self._attend_pool(x_f)], dim=-1)
        return AasistOutput(self.classifier(pooled))

    def _spectro_temporal_graph(self, audio: torch.Tensor):
        """STFT magnitude -> (time-graph, freq-graph) node features.

        Time graph:  node i = one time frame's spectrum (max-pooled in
        groups of 8 frames to bound N). Freq graph: node i = frequency
        bin i's energy over time, pooled to n_freq features so both graphs
        share the GAT input width.
        """
        spec = torch.stft(
            audio, n_fft=(self.n_freq - 1) * 2, hop_length=160,
            win_length=400, window=torch.hann_window(400, device=audio.device),
            return_complex=True,
        ).abs()                                                   # [B, n_freq, frames]
        mag = torch.log(spec + 1e-6)
        b, nf, fr = mag.shape
        # --- time graph: frames as nodes (max-pool groups of 8 to bound N),
        # each node's features = one frame's spectrum.
        pad = (8 - fr % 8) % 8
        mag_t = F.pad(mag, (0, pad), value=-20.0) if pad else mag
        grouped = mag_t.view(b, nf, -1, 8).max(dim=-1).values   # [B, n_freq, N]
        x_t = grouped.permute(0, 2, 1)                          # [B, N, n_freq]
        # --- freq graph: frequency bins as nodes (257), time slots as
        # features (257). mag_tr [B, frames, bins] -> max-pool ~2 frames per
        # slot -> [B, bins, slots].
        mag_tr = mag.permute(0, 2, 1)                           # [B, frames, n_freq]
        slot = max(1, (fr + nf - 1) // nf)                      # frames per slot
        pad_f = (slot * nf - fr) % (slot * nf)
        mag_f = F.pad(mag_tr, (0, 0, 0, pad_f), value=-20.0) if pad_f else mag_tr
        x_f = mag_f.view(b, nf, slot, nf).max(dim=2).values     # [B, n_freq, n_freq]
        return x_t, x_f

    def _attend_pool(self, x: torch.Tensor) -> torch.Tensor:
        """Attentive statistics pooling: attention-weighted mean + max. [B, hidden]"""
        w = torch.softmax(self.pool_attn(x).squeeze(-1), dim=-1)   # [B, N]
        mean = torch.einsum("bn,bnh->bh", w, x)
        # attention-boosted max: strongly-attended nodes dominate the max —
        # ponytail: cheaper than the paper's max-attentive stats, same
        # capacity class for a 2-layer GAT.
        mx = (x + w.unsqueeze(-1) * 10.0).max(dim=1).values
        return torch.cat([mean, mx], dim=-1)


def count_params(model: nn.Module) -> int:
    return sum(p.numel() for p in model.parameters())
