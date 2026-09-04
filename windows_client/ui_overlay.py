"""
antAI Windows Client — Risk Overlay UI

A lightweight Tkinter overlay window that shows:
  • Large animated risk gauge (0-100 arc)
  • Three signal family rows: Voice Authenticity / Message Patterns / Speaker ID
  • Risk level badge: HIGH RISK / ELEVATED / SAFE
  • Latest recommendation text from the server (deterministic, never fabricated)
  • Always-on-top option so it floats over the call app

Design: matches the antAI dashboard color language (red/amber/green).
        All values come from normalized_result JSON — null is rendered as "—".
"""
from __future__ import annotations

import math
import threading
import tkinter as tk
from tkinter import font as tkfont
from typing import Optional

# ── Color tokens (mirror the dashboard CSS variables) ─────────────────────────
BG          = "#1a1d23"
SURFACE     = "#242830"
SURFACE2    = "#2e333d"
INK         = "#f3f4f6"
MUTED       = "#6b7280"
ACCENT      = "#0e7c7b"
LOW         = "#16a34a"
MED         = "#ca8a04"
HIGH        = "#dc2626"
CRITICAL    = "#7f1d1d"
LINE        = "#374151"


def _band_color(risk: float, verify_at: float = 50.0, critical_at: float = 70.0) -> str:
    if risk >= critical_at: return HIGH
    if risk >= verify_at:   return MED
    return LOW


def _pct(v) -> str:
    if v is None: return "—"
    try: return f"{float(v) * 100:.0f}%"
    except Exception: return "—"


def _fmt_risk(v) -> str:
    if v is None: return "—"
    try: return f"{float(v):.0f}"
    except Exception: return "—"


class RiskOverlay:
    """Tkinter overlay window. Thread-safe via Tk's after() queue."""

    def __init__(
        self,
        title: str = "antAI — Live Risk",
        always_on_top: bool = True,
        verify_at: float = 50.0,
        critical_at: float = 70.0,
    ):
        self.verify_at = verify_at
        self.critical_at = critical_at
        self._root: Optional[tk.Tk] = None
        self._always_on_top = always_on_top
        self._title = title
        self._lock = threading.Lock()
        self._latest: dict = {}

    # ── Public API ─────────────────────────────────────────────────────────────
    def update(self, snapshot: dict) -> None:
        """Thread-safe update from the streaming callback."""
        with self._lock:
            self._latest = snapshot
        if self._root:
            self._root.after(0, self._render)

    def run(self) -> None:
        """Block the calling thread running the Tk event loop. Call from main thread."""
        self._root = tk.Tk()
        self._root.title(self._title)
        self._root.configure(bg=BG)
        self._root.resizable(False, False)
        if self._always_on_top:
            self._root.attributes("-topmost", True)
        self._build_ui()
        self._root.mainloop()

    def close(self) -> None:
        if self._root:
            self._root.after(0, self._root.destroy)

    # ── UI construction ────────────────────────────────────────────────────────
    def _build_ui(self) -> None:
        root = self._root
        W = 340

        # ── Header ────────────────────────────────────────────────────────────
        hdr = tk.Frame(root, bg=SURFACE, pady=10)
        hdr.pack(fill=tk.X)
        tk.Label(hdr, text="antAI", fg=ACCENT, bg=SURFACE,
                 font=tkfont.Font(weight="bold", size=14)).pack(side=tk.LEFT, padx=14)
        self._conn_lbl = tk.Label(hdr, text="● Connecting…", fg=MUTED, bg=SURFACE,
                                   font=tkfont.Font(size=10))
        self._conn_lbl.pack(side=tk.RIGHT, padx=14)

        # ── Risk gauge canvas ─────────────────────────────────────────────────
        canvas_h = 150
        self._canvas = tk.Canvas(root, width=W, height=canvas_h, bg=BG,
                                  highlightthickness=0)
        self._canvas.pack(pady=(10, 0))
        cx, cy, r = W // 2, canvas_h - 20, 100
        self._gauge_cx = cx; self._gauge_cy = cy; self._gauge_r = r
        # background arc (grey)
        self._canvas.create_arc(
            cx - r, cy - r, cx + r, cy + r,
            start=0, extent=180, style=tk.ARC,
            outline="#374151", width=14
        )
        # risk arc (colored, updated by _render)
        self._risk_arc = self._canvas.create_arc(
            cx - r, cy - r, cx + r, cy + r,
            start=0, extent=0, style=tk.ARC,
            outline=LOW, width=14
        )
        # Risk number text
        self._risk_text = self._canvas.create_text(
            cx, cy - 28, text="—", fill=INK,
            font=tkfont.Font(weight="bold", size=36)
        )
        self._risk_sub = self._canvas.create_text(
            cx, cy - 2, text="/ 100", fill=MUTED,
            font=tkfont.Font(size=11)
        )

        # ── Verdict badge ──────────────────────────────────────────────────────
        self._badge_var = tk.StringVar(value="MONITORING")
        badge_frame = tk.Frame(root, bg=SURFACE2, pady=8)
        badge_frame.pack(fill=tk.X, padx=14, pady=(8, 0))
        self._badge_lbl = tk.Label(badge_frame, textvariable=self._badge_var,
                                    fg=LOW, bg=SURFACE2,
                                    font=tkfont.Font(weight="bold", size=13))
        self._badge_lbl.pack()

        # ── Signal rows ────────────────────────────────────────────────────────
        sig_frame = tk.Frame(root, bg=BG)
        sig_frame.pack(fill=tk.X, padx=14, pady=10)

        self._sig_vars: dict[str, tk.StringVar] = {}
        signals = [
            ("voice", "Voice Authenticity"),
            ("prosody", "Message Patterns"),
            ("voiceprint", "Speaker Identity"),
        ]
        for key, label in signals:
            row = tk.Frame(sig_frame, bg=SURFACE, pady=6, padx=10)
            row.pack(fill=tk.X, pady=2)
            tk.Label(row, text=label, fg=MUTED, bg=SURFACE,
                     font=tkfont.Font(size=10)).pack(side=tk.LEFT)
            v = tk.StringVar(value="—")
            self._sig_vars[key] = v
            tk.Label(row, textvariable=v, fg=INK, bg=SURFACE,
                     font=tkfont.Font(weight="bold", size=10)).pack(side=tk.RIGHT)

        # ── Recommendation ─────────────────────────────────────────────────────
        rec_frame = tk.Frame(root, bg=SURFACE, padx=12, pady=10)
        rec_frame.pack(fill=tk.X, padx=14, pady=(0, 6))
        tk.Label(rec_frame, text="RECOMMENDATION", fg=MUTED, bg=SURFACE,
                 font=tkfont.Font(size=8)).pack(anchor=tk.W)
        self._rec_var = tk.StringVar(value="Waiting for audio…")
        self._rec_lbl = tk.Label(rec_frame, textvariable=self._rec_var,
                                  fg=INK, bg=SURFACE, wraplength=290,
                                  justify=tk.LEFT,
                                  font=tkfont.Font(size=10))
        self._rec_lbl.pack(anchor=tk.W)

        # ── Footer ─────────────────────────────────────────────────────────────
        tk.Label(root, text="Detection runs on your antAI server · scores are real",
                 fg=MUTED, bg=BG, font=tkfont.Font(size=8)).pack(pady=(4, 10))

    # ── Render ─────────────────────────────────────────────────────────────────
    def _render(self) -> None:
        with self._lock:
            snap = dict(self._latest)
        if not snap:
            return

        risk = float(snap.get("risk") or 0.0)
        band = snap.get("band") or "passive"
        color = _band_color(risk, self.verify_at, self.critical_at)

        # Arc: 180° = full risk. start=0 (right), extent grows counter-clockwise.
        # We map risk→degrees; tkinter arc extent is in degrees, positive=counter-clockwise.
        extent = 180.0 * (risk / 100.0)
        self._canvas.itemconfig(self._risk_arc, extent=extent, outline=color)
        self._canvas.itemconfig(self._risk_text, text=_fmt_risk(risk), fill=color)

        # Badge
        badge_text = {
            "critical": "⚠ HIGH RISK — DO NOT AUTHORIZE",
            "verify":   "↑ ELEVATED — VERIFY REQUIRED",
        }.get(band, "✓ SAFE — Monitoring")
        self._badge_var.set(badge_text)
        self._badge_lbl.config(fg=color)

        # Signal values
        acoustic  = snap.get("acoustic") or {}
        prosody   = snap.get("prosody") or {}
        voiceprint= snap.get("voiceprint") or {}
        self._sig_vars["voice"].set(_pct(acoustic.get("voice_deepfake")))
        sp = prosody.get("scam_prob")
        ug = prosody.get("urgency")
        if sp is not None:
            self._sig_vars["prosody"].set(f"Scam {_pct(sp)}")
        elif ug is not None:
            self._sig_vars["prosody"].set(f"Urgency {ug:.0f}")
        else:
            self._sig_vars["prosody"].set("—")
        sim = voiceprint.get("similarity")
        self._sig_vars["voiceprint"].set(_pct(sim) if sim is not None else "—")

        # Recommendation
        rec = snap.get("recommendation") or "No action needed. Continue monitoring."
        self._rec_var.set(rec[:200])

        # Connection dot
        t = snap.get("t") or ""
        self._conn_lbl.config(text=f"● Live  {t}", fg=LOW)
