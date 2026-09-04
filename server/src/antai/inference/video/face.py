"""Face detection + landmarks via MediaPipe FaceMesh.

Used for (a) face presence/quality in video-call frames and (b) mouth-region
landmarks that feed the lip-sync check.
"""
from __future__ import annotations

import logging

import numpy as np

from ..hub import BaseEngine

log = logging.getLogger(__name__)


class FaceEngine(BaseEngine):
    name = "face"

    def _load(self) -> bool:
        try:
            import mediapipe as mp
            self._mp = mp
            self._face_mesh = mp.solutions.face_mesh.FaceMesh(
                static_image_mode=False, max_num_faces=2,
                refine_landmarks=True, min_detection_confidence=0.4)
            self.device = "cpu"
            return True
        except Exception as e:
            log.warning("face engine load failed: %s", e)
            return False

    def detect(self, frame_bgr: np.ndarray) -> dict:
        """Returns {faces:[{bbox, mouth:[(x,y)...], openness}], count, ready}."""
        if not self.ready():
            return {"faces": [], "count": 0, "ready": False}
        try:
            rgb = frame_bgr[:, :, ::-1]
            h, w = rgb.shape[:2]
            results = self._face_mesh.process(rgb)
            faces = []
            if results.multi_face_landmarks:
                for lm in results.multi_face_landmarks:
                    pts = np.array([(p.x * w, p.y * h) for p in lm.landmark])
                    x0, y0 = pts.min(axis=0)
                    x1, y1 = pts.max(axis=0)
                    # mouth landmarks: indices 61,291 (corners), 13,14 (top/bottom inner)
                    mouth = [pts[i] for i in (61, 291, 13, 14, 0)]
                    openness = float(np.linalg.norm(pts[13] - pts[14]))
                    faces.append({"bbox": [float(x0), float(y0), float(x1), float(y1)],
                                  "mouth": mouth, "openness": openness,
                                  "center": [float((x0 + x1) / 2), float((y0 + y1) / 2)]})
            return {"faces": faces, "count": len(faces), "ready": True}
        except Exception as e:
            log.debug("face detect error: %s", e)
            return {"faces": [], "count": 0, "ready": True}
