"""Per-participant video frame sampler (5-8 fps) for the CV pipeline."""
from __future__ import annotations

import asyncio
import logging
import time

import numpy as np

from ..config import get_config

log = logging.getLogger(__name__)

FrameHandler = object  # callable(speaker_id:int, frame:np.ndarray, meta:dict) -> Awaitable


class FrameSampler:
    def __init__(self, session_key: str, on_frame: FrameHandler):
        cfg = get_config()
        self.session_key = session_key
        self.fps = cfg.pipeline.video_fps
        self.on_frame = on_frame
        self._last: dict[int, float] = {}

    async def push_frame(self, participant_id: int, frame) -> None:
        """Called from the SFU video tap. frame is an av.VideoFrame."""
        now = time.time()
        last = self._last.get(participant_id, 0.0)
        if now - last < 1.0 / self.fps:
            return
        self._last[participant_id] = now
        arr = _frame_to_ndarray(frame)
        if arr is None:
            return
        await self.on_frame(participant_id, arr, {"session_key": self.session_key})


def _frame_to_ndarray(frame) -> np.ndarray | None:
    try:
        arr = frame.to_ndarray(format="bgr24")
        return np.asarray(arr)
    except Exception:
        return None
