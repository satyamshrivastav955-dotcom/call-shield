"""Media relay tracks for the aiortc SFU.

Each remote track from one participant is fanned out to:
  1. a relay track that is ``replaceTrack``'d into the OTHER participant's peer
     connection (so media flows caller -> server -> callee), and
  2. the ingestion tap (a read-only copy used for scam/deepfake detection).

Why aiortc's MediaRelay (this replaced a hand-rolled fan-out):
  A single ``MediaRelay`` reads the source track ONCE and hands each frame to
  every subscriber, preserving the source frame's original ``pts`` /
  ``time_base``. That keeps the relayed media correctly paced. The previous
  custom fan-out rewrote ``pts`` from wall-clock time (video) and from a fixed
  48kHz/960-sample assumption (audio); when those assumptions did not match the
  real stream the media came out mis-paced, which is what made the relayed
  audio sound butchered and jittery and left video unusable.

  - The RELAY subscriber is ``buffered=True`` (unbounded FIFO) so the outgoing
    RtpSender receives every frame in order for smooth playback.
  - The TAP subscriber is decoupled from the relay: audio uses ``buffered=True``
    (we must not drop audio the ASR needs for continuous transcription); video
    uses ``buffered=False`` (latest-frame-only is fine for periodic sampling and
    can never back up).

  CRITICAL: the tap handler must return quickly and offload any heavy model
  inference off the event loop (``asyncio.to_thread``). MediaRelay's single
  reader runs on the shared asyncio loop, so a handler that blocks the loop
  would starve the relay and reintroduce the audio/video breakup.
"""
from __future__ import annotations

import asyncio
import logging
from typing import Awaitable, Callable, Optional

from aiortc.contrib.media import MediaRelay
from aiortc.mediastreams import MediaStreamError, MediaStreamTrack

log = logging.getLogger(__name__)


class _FanOut:
    """Fans a single source track out to a relay track and an ingestion tap."""

    def __init__(self, track: MediaStreamTrack):
        self._track = track
        self._kind = track.kind
        # ONE relay per source: a single __run_track reads the source once and
        # distributes each frame to the relay proxy AND the tap proxy.
        self._relay = MediaRelay()
        self._tap_tasks: list[asyncio.Task] = []
        self._tap_proxies: list[MediaStreamTrack] = []
        self._relay_proxies: list[MediaStreamTrack] = []

    def add_relay(self) -> MediaStreamTrack:
        """Return a proxy track to ``replaceTrack`` into the other peer.

        buffered=True: the outgoing sender gets every frame in order, with the
        source's original timing preserved (no pts rewriting).
        """
        proxy = self._relay.subscribe(self._track, buffered=True)
        self._relay_proxies.append(proxy)
        return proxy

    def add_tap(self, handler: Callable[[object], Awaitable[None]]) -> asyncio.Task:
        """Call ``handler(frame)`` for detection without ever blocking the relay.

        Audio: buffered=True  -> never drop audio (ASR needs it continuous).
        Video: buffered=False -> latest-frame-only (periodic sampling, can't back up).
        """
        buffered = self._kind == "audio"
        proxy = self._relay.subscribe(self._track, buffered=buffered)
        self._tap_proxies.append(proxy)
        task = asyncio.create_task(self._tap_loop(proxy, handler))
        self._tap_tasks.append(task)
        return task

    async def _tap_loop(self, proxy: MediaStreamTrack, handler) -> None:
        try:
            while True:
                try:
                    frame = await proxy.recv()
                except MediaStreamError:
                    break  # source ended
                try:
                    await handler(frame)
                except Exception:
                    log.exception("tap handler error")
        except asyncio.CancelledError:
            pass

    def stop(self) -> None:
        for task in self._tap_tasks:
            task.cancel()
        self._tap_tasks.clear()
        for proxy in (*self._tap_proxies, *self._relay_proxies):
            try:
                proxy.stop()
            except Exception:
                pass
        self._tap_proxies.clear()
        self._relay_proxies.clear()


def make_fanout(track: MediaStreamTrack) -> Optional[_FanOut]:
    if track.kind in ("audio", "video"):
        return _FanOut(track)
    return None
