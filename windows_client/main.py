"""
antAI Windows Client — Entry Point

Launches three concurrent components:
  1. MicrophoneCapture  — reads PCM from the default mic in a thread
  2. StreamingClient    — sends PCM to the antAI server via WebSocket
  3. RiskOverlay        — Tkinter window on the main thread (Tk requires it)
  4. DesktopNotifier    — posts Windows toast notifications on threshold cross

Usage:
    # In the same LAN as the antAI server
    python main.py --server ws://192.168.1.10:8765 --scenario high_value_txn

    # Demo mode (no microphone required)
    python main.py --server ws://localhost:8765 --demo
"""
from __future__ import annotations

import argparse
import asyncio
import logging
import threading

from audio_capture import MicrophoneCapture
from notifications import DesktopNotifier
from streaming_client import StreamingClient
from ui_overlay import RiskOverlay

logging.basicConfig(
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
    level=logging.INFO
)
log = logging.getLogger("antai_win")


def parse_args() -> argparse.Namespace:
    ap = argparse.ArgumentParser(description="antAI Windows protection client")
    ap.add_argument("--server",   default="ws://localhost:8765",
                    help="antAI server WebSocket URL (default: ws://localhost:8765)")
    ap.add_argument("--scenario", default="high_value_txn",
                    choices=["routine_call", "high_value_txn", "privileged_access"],
                    help="Protection scenario (sets risk thresholds)")
    ap.add_argument("--rate",     type=int, default=16000,
                    help="Microphone sample rate in Hz (default: 16000)")
    ap.add_argument("--chunk",    type=float, default=2.0,
                    help="Audio chunk size in seconds (default: 2.0)")
    ap.add_argument("--verify-at",   type=float, default=50.0,
                    help="Risk score that triggers ELEVATED notification (default: 50)")
    ap.add_argument("--critical-at", type=float, default=70.0,
                    help="Risk score that triggers HIGH RISK notification (default: 70)")
    ap.add_argument("--no-overlay", action="store_true",
                    help="Run without the Tkinter overlay (headless / CLI mode)")
    ap.add_argument("--demo", action="store_true",
                    help="Use silence instead of a real microphone (for testing)")
    return ap.parse_args()


# ── Async worker ───────────────────────────────────────────────────────────────
async def capture_and_stream(
    args: argparse.Namespace,
    overlay: RiskOverlay | None,
    notifier: DesktopNotifier,
    stop_event: threading.Event,
) -> None:
    # Scenario thresholds derived from scenario name (mirrors server defaults)
    SCENARIO_THRESHOLDS = {
        "routine_call":      {"verify_at": 75, "critical_at": 90},
        "high_value_txn":    {"verify_at": 50, "critical_at": 70},
        "privileged_access": {"verify_at": 40, "critical_at": 60},
    }
    thresholds = SCENARIO_THRESHOLDS.get(args.scenario, {"verify_at": 50, "critical_at": 70})

    def on_update(snap: dict) -> None:
        risk = float(snap.get("risk") or 0.0)
        band = snap.get("band") or "passive"
        rec  = snap.get("recommendation") or ""
        if overlay:
            overlay.update(snap)
        notifier.update(risk, band, rec)

    client = StreamingClient(
        server_url=args.server,
        on_update=on_update,
    )

    connected = await client.start(sample_rate=args.rate, scenario=args.scenario)
    if not connected:
        log.error("Failed to connect. Check --server and that the antAI server is running.")
        if overlay:
            overlay.close()
        stop_event.set()
        return

    log.info("Connected. Protection active — scenario=%s verify_at=%d critical_at=%d",
             args.scenario, thresholds["verify_at"], thresholds["critical_at"])

    with MicrophoneCapture(sample_rate=args.rate, chunk_s=args.chunk) as cap:
        for chunk in cap:
            if stop_event.is_set():
                break
            await client.send_pcm(chunk)

    await client.stop()
    log.info("Session ended.")
    if overlay:
        overlay.close()
    stop_event.set()


def run_async_loop(coro, stop_event: threading.Event) -> None:
    """Run the async coroutine in a background thread."""
    asyncio.run(coro)
    stop_event.set()


def main() -> None:
    args = parse_args()

    # Scenario thresholds
    SCENARIO_THRESHOLDS = {
        "routine_call":      {"verify_at": 75.0, "critical_at": 90.0},
        "high_value_txn":    {"verify_at": 50.0, "critical_at": 70.0},
        "privileged_access": {"verify_at": 40.0, "critical_at": 60.0},
    }
    thresholds = SCENARIO_THRESHOLDS.get(args.scenario, {"verify_at": args.verify_at, "critical_at": args.critical_at})
    verify_at   = thresholds["verify_at"]
    critical_at = thresholds["critical_at"]

    notifier = DesktopNotifier(verify_at=verify_at, critical_at=critical_at)
    overlay: RiskOverlay | None = None

    stop_event = threading.Event()

    if not args.no_overlay:
        overlay = RiskOverlay(
            verify_at=verify_at,
            critical_at=critical_at,
            always_on_top=True
        )

    # Launch async capture+stream in a background thread
    coro = capture_and_stream(args, overlay, notifier, stop_event)
    bg = threading.Thread(
        target=run_async_loop,
        args=(coro, stop_event),
        daemon=True,
        name="antai-stream"
    )
    bg.start()

    if overlay and not args.no_overlay:
        # Tk must run on the main thread
        try:
            overlay.run()
        except KeyboardInterrupt:
            pass
    else:
        # Headless: just block until the stream ends
        try:
            stop_event.wait()
        except KeyboardInterrupt:
            stop_event.set()

    log.info("antAI client stopped.")


if __name__ == "__main__":
    main()
