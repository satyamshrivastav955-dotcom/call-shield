"""Cross-language contract check for voiceprint enrolment.

The risky seam is that the Android recorder writes the WAV bytes and the Python
server has to read them: nothing in either codebase catches a mismatch, and the
symptom on a phone would be a vague "couldn't register that recording".

So this rebuilds the exact bytes VoiceprintRecorder.wavOf() produces (44-byte
RIFF header, s16le mono 16 kHz) and pushes them through the server's real
decoder, then checks the enrolment guards fire on the recordings they are meant
to reject. Run: python server/setup/verify_enrollment.py
"""
import importlib.util
import math
import re
import struct
import sys
from pathlib import Path

SERVER = Path(__file__).resolve().parents[1]
VOICE = SERVER / "src/antai/inference/voice"

# audio_io is deliberately dependency-free, so it can be loaded on its own
# without dragging in the model hub / sqlalchemy.
spec = importlib.util.spec_from_file_location("audio_io", VOICE / "audio_io.py")
audio_io = importlib.util.module_from_spec(spec)
spec.loader.exec_module(audio_io)

SR = 16000
fails = []


def check(label, got, want):
    ok = got == want
    print(f"  {'PASS' if ok else 'FAIL'}  {label}: {got!r}" +
          ("" if ok else f" (expected {want!r})"))
    if not ok:
        fails.append(label)


def kotlin_wav(samples):
    """Byte-for-byte what VoiceprintRecorder.wavOf() writes."""
    pcm = b"".join(struct.pack("<h", max(-32768, min(32767, int(s)))) for s in samples)
    byte_rate = SR * 2
    return (b"RIFF" + struct.pack("<I", 36 + len(pcm)) + b"WAVE" +
            b"fmt " + struct.pack("<IHHIIHH", 16, 1, 1, SR, byte_rate, 2, 16) +
            b"data" + struct.pack("<I", len(pcm)) + pcm)


def tone(seconds, amplitude):
    n = int(SR * seconds)
    return [amplitude * 32767 * math.sin(2 * math.pi * 220 * i / SR) for i in range(n)]


# The guards live in speaker_verify, which imports the DB layer, so read the
# thresholds out of the source rather than importing it — a hard-coded copy here
# would silently stop testing the real values the moment someone tuned them.
src = (VOICE / "speaker_verify.py").read_text(encoding="utf-8")
MIN_SECONDS = float(re.search(r"MIN_ENROLL_SECONDS = ([\d.]+)", src).group(1))
MIN_RMS = float(re.search(r"MIN_ENROLL_RMS = ([\d.]+)", src).group(1))
print(f"thresholds read from speaker_verify.py: "
      f"MIN_ENROLL_SECONDS={MIN_SECONDS} MIN_ENROLL_RMS={MIN_RMS}\n")


def guard_verdict(wav_bytes):
    """Replays the enrol_audio guard chain against the real decoder."""
    import numpy as np
    audio, sr = audio_io.decode_audio_bytes(wav_bytes)
    if audio is None or not len(audio):
        return "undecodable_audio"
    sr = int(sr or SR)
    if len(audio) / float(sr) < MIN_SECONDS:
        return "too_short"
    rms = float(np.sqrt(np.mean(np.square(audio.astype(np.float32)))))
    if rms < MIN_RMS:
        return "too_quiet"
    return "ok"


print("1) the Android recorder's WAV is readable by the server decoder")
wav = kotlin_wav(tone(5.0, 0.3))
audio, sr = audio_io.decode_audio_bytes(wav)
check("decoded", audio is not None, True)
check("sample rate", sr, SR)
check("duration (s)", round(len(audio) / sr, 2) if audio is not None else None, 5.0)
check("stays in range", bool(audio is not None and abs(audio).max() <= 1.0), True)
check("header is 44 bytes", len(wav) - len(audio) * 2 if audio is not None else None, 44)

print("\n2) guards reject exactly the recordings that would poison the reference")
check("5.0 s at normal level", guard_verdict(kotlin_wav(tone(5.0, 0.30))), "ok")
check("1.0 s (user tapped twice)", guard_verdict(kotlin_wav(tone(1.0, 0.30))), "too_short")
check("5.0 s of silence      ", guard_verdict(kotlin_wav([0] * int(SR * 5))), "too_quiet")
check("5.0 s barely audible  ", guard_verdict(kotlin_wav(tone(5.0, 0.002))), "too_quiet")
check("garbage bytes         ", guard_verdict(b"not audio at all"), "undecodable_audio")
# The minimum must be reachable: a user who speaks for the advertised time and is
# then told "too short" would have no way to succeed.
check("exactly the minimum   ", guard_verdict(kotlin_wav(tone(MIN_SECONDS + 0.05, 0.25))), "ok")

print("\n3) a quiet-but-real voice is not rejected")
# Speech at conversational level through a phone mic lands well above the floor;
# a threshold set too high would reject soft-spoken users, so check the margin.
import numpy as np  # noqa: E402
a, s = audio_io.decode_audio_bytes(kotlin_wav(tone(5.0, 0.03)))
rms = float(np.sqrt(np.mean(np.square(a))))
print(f"  amplitude 3% of full scale -> rms {rms:.4f} (floor {MIN_RMS})")
check("3% amplitude accepted", rms >= MIN_RMS, True)

print()
if fails:
    print(f"FAILED: {len(fails)} check(s): {fails}")
    sys.exit(1)
print("all checks passed")
