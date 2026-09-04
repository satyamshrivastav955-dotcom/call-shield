"""Can antAI actually catch a cloned voice right now? Run this to find out.

This exists because "the AI voice is not getting detected" has three completely
different causes that look identical from the app: the hosted API silently
rejecting every segment, the local SSL weights not being loaded at all, or a
server process running code older than the fix. Guessing between them has cost
several rounds already, so this script asks each one directly and prints an
unambiguous answer.

What it checks, in order:
  1. WHICH CODE is on disk vs. running in your live server (the stale-process trap)
  2. Credentials and provider config (names and presence only — never values)
  3. The hosted Velma backend: connects for real and reports the exact close code
     for each end-of-stream framing until one is accepted
  4. The local SSL ensemble: whether the weights load, on what device
  5. Your own ground-truth samples in server/audio_samples: every real clip and
     every AI-cloned clip is scored the way a live call would be (in windows),
     then compared against the alert threshold

Run from anywhere:
    python server/setup/check_ai_voice.py
    python server/setup/check_ai_voice.py --skip-hosted        # local models only
    python server/setup/check_ai_voice.py --eos binary         # pin the framing
    python server/setup/check_ai_voice.py --samples path/to/wavs
    python server/setup/check_ai_voice.py --no-server          # skip the HTTP check

Ground truth comes from the filename: "..._ai_voice_..." / "..._ai_Voice_..." is
a clone, "..._real_voice_..." is genuine. Anything else is scored but excluded
from the accuracy tally, so an unlabelled file can never quietly become a pass.

Exit code is 0 only when a cloned voice would actually be flagged.
"""
from __future__ import annotations

import argparse
import os
import sys
import time
from pathlib import Path

SERVER_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(SERVER_ROOT / "src"))

BOLD, DIM, OFF = "\033[1m", "\033[2m", "\033[0m"
RED, GRN, YEL, CYN = "\033[31m", "\033[32m", "\033[33m", "\033[36m"
if os.name == "nt" and not os.environ.get("WT_SESSION"):
    # old consoles print the escapes literally, which is worse than no colour
    try:
        import colorama  # type: ignore
        colorama.just_fix_windows_console()
    except Exception:
        BOLD = DIM = OFF = RED = GRN = YEL = CYN = ""

CLONE_MARKERS = ("_ai_voice", "_ai voice", "ai_voice", "aivoice", "_tts", "clone")
REAL_MARKERS = ("_real_voice", "real_voice", "_real")


def head(text: str) -> None:
    print(f"\n{BOLD}=== {text} {'=' * max(0, 56 - len(text))}{OFF}")


def ok(msg: str) -> None:
    print(f"  {GRN}OK  {OFF} {msg}")


def bad(msg: str) -> None:
    print(f"  {RED}FAIL{OFF} {msg}")


def warn(msg: str) -> None:
    print(f"  {YEL}WARN{OFF} {msg}")


def info(msg: str) -> None:
    print(f"       {DIM}{msg}{OFF}")


# --------------------------------------------------------------- 1. which code
def _get_json(url: str, timeout: float = 8.0) -> dict:
    """GET a JSON document using httpx if present, else the standard library.

    A diagnostic that cannot run because one of its own dependencies is missing
    is worse than useless, so this deliberately falls back to urllib.
    """
    try:
        import httpx
        return httpx.get(url, timeout=timeout).json()
    except ImportError:
        pass
    import json as _json
    import urllib.request
    with urllib.request.urlopen(url, timeout=timeout) as r:   # noqa: S310
        return _json.loads(r.read().decode("utf-8", "replace"))


def check_running_code(server_url: str | None) -> dict:
    head("1. which code is actually running")
    from antai.build_info import runtime_info

    disk = runtime_info()
    print(f"  files on disk: fingerprint={disk['code_fingerprint']} "
          f"({disk['tracked_files']} tracked)")

    result = {"server_reachable": False, "restart_required": None}
    if not server_url:
        info("skipped the live-server check (--no-server)")
        return result

    url = f"{server_url.rstrip('/')}/api/debug/models"
    try:
        data = _get_json(url)
    except Exception as e:
        warn(f"could not reach the running server at {server_url}: "
             f"{type(e).__name__}. Everything below tests the code ON DISK.")
        return result

    result["server_reachable"] = True
    rt = data.get("runtime") or {}
    if not rt:
        warn("the running server has no /runtime field — it predates this check, "
             "which itself means it is running OLD code. Restart it.")
        result["restart_required"] = True
        return result

    stale = bool(rt.get("restart_required"))
    result["restart_required"] = stale
    print(f"  live server:   fingerprint={rt.get('code_fingerprint')} "
          f"started={rt.get('started_at')} uptime={rt.get('uptime_s')}s")
    if stale:
        bad("THE RUNNING SERVER IS STALE. It is executing code older than the "
            "files on disk, so no fix below is live yet.")
        for p in (rt.get("changed_since_start") or [])[:10]:
            info(f"changed since it started: {p}")
        info("Restart it (Ctrl+C the run_dev window, start it again) and re-run "
             "this script. Until then the app's behaviour proves nothing.")
    else:
        ok("the running server matches the files on disk")

    prov = ((data.get("providers") or {}).get("voice_deepfake") or {})
    if prov:
        info(f"live voice_deepfake: backend={prov.get('active_backend')} "
             f"velma_in_use={prov.get('velma_in_use')} "
             f"local_ready={prov.get('local_ensemble_ready')} "
             f"can_detect={prov.get('can_detect_cloned_voice')}")
        if prov.get("velma_last_error"):
            info(f"live velma_last_error: {prov['velma_last_error']}")
    return result


# ------------------------------------------------------------ 2. configuration
def check_config() -> dict:
    head("2. credentials and provider configuration")
    from antai.config import get_config

    cfg = get_config()
    prov = cfg.providers
    pipe = cfg.pipeline

    # presence only. The key itself is never printed, logged or length-reported.
    print(f"  VELMA_API_KEY:        "
          f"{(GRN + 'present' + OFF) if prov.velma_api_key else (RED + 'MISSING' + OFF)}")
    print(f"  providers.voice_deepfake: {prov.voice_deepfake}   "
          f"{DIM}(both | velma | local){OFF}")
    print(f"  velma endpoint set:   {bool(prov.velma_endpoint)}")
    print(f"  velma model_id:       {prov.velma_model_id}")
    print(f"  velma eos:            {prov.velma_eos or '(auto-negotiate)'}")
    print(f"  alert threshold:      {pipe.voice_alert_threshold}   "
          f"disagreement cap {pipe.voice_disagreement_cap}   "
          f"solo alert {getattr(pipe, 'voice_solo_alert_threshold', 0.95)}")

    if prov.voice_deepfake == "local":
        warn("providers.voice_deepfake is 'local' — the hosted API will not be "
             "used at all. Set it to 'both' in server/config.yaml to run both.")
    if not prov.velma_api_key and prov.voice_deepfake != "local":
        warn("no VELMA_API_KEY in server/.env — the hosted backend cannot run; "
             "detection will depend entirely on the local SSL ensemble.")

    weights = SERVER_ROOT / "models"
    for name in ("voice_deepfake", "voice_deepfake_cross2"):
        d = weights / name
        n = len(list(d.glob("*"))) if d.is_dir() else 0
        (ok if n else warn)(f"models/{name}: "
                            f"{'%d files' % n if n else 'MISSING / empty'}")
    return {"mode": prov.voice_deepfake, "key": bool(prov.velma_api_key)}


# ------------------------------------------------------------- 3. hosted Velma
def check_hosted(audio, sr, eos_pin: str) -> dict:
    head("3. hosted backend (Velma-2 synthetic-voice detection)")
    from antai.config import get_config
    from antai.inference import providers as P

    prov = get_config().providers
    if not prov.velma_api_key or not prov.velma_endpoint:
        warn("skipped: no key and/or endpoint configured")
        return {"working": False, "eos": None, "reason": "not configured"}
    if audio is None:
        warn("skipped: no audio sample available to send")
        return {"working": False, "eos": None, "reason": "no audio"}

    variants = (eos_pin,) if eos_pin else P._EOS_VARIANTS
    info(f"sending {len(audio) / sr:.1f}s of real audio, one framing at a time")
    for variant in variants:
        # Reset the negotiated framing so each variant is genuinely tested rather
        # than short-circuited by a previous success.
        P._VELMA_EOS = None
        t0 = time.time()
        score = P.velma_detect(
            audio, sr, api_key=prov.velma_api_key, endpoint=prov.velma_endpoint,
            model_id=prov.velma_model_id, auth_style=prov.velma_auth_style,
            response_path=prov.velma_response_path,
            config_json=prov.velma_config_json, eos=variant)
        dt = time.time() - t0
        if score is not None:
            ok(f"eos={variant:<7} accepted — synthetic-voice score "
               f"{BOLD}{score:.3f}{OFF} in {dt:.1f}s")
            info(f"Pin it in server/config.yaml under providers.velma: "
                 f"eos: \"{variant}\"  (saves the negotiation on every call)")
            return {"working": True, "eos": variant, "score": score}
        bad(f"eos={variant:<7} no verdict in {dt:.1f}s")
        info(P.last_velma_error() or "no reason recorded")

    reason = P.last_velma_error() or "unknown"
    bad("no end-of-stream framing produced a verdict — the hosted backend is "
        "contributing NOTHING to detection right now.")
    if "1003" in reason:
        info("close code 1003 means the endpoint refused the DATA we sent. If it "
             "closed after ~0 bytes the audio framing is wrong; if it closed "
             "after the whole segment, the end-of-stream marker is.")
    if "401" in reason or "403" in reason:
        info("that is an auth rejection: the key is not valid for this endpoint. "
             "Check the key and the plan/entitlement in the Modulate console.")
    if "404" in reason:
        info("that path does not exist on the host — check providers.velma."
             "model_id / endpoint against the console's exact model name.")
    return {"working": False, "eos": None, "reason": reason}


# -------------------------------------------------------- 4. local SSL models
def check_local() -> dict:
    head("4. local SSL ensemble (the fallback that must always work)")
    from antai.inference.voice.deepfake_voice import VoiceDeepfakeEngine

    eng = VoiceDeepfakeEngine()
    t0 = time.time()
    try:
        ready = bool(eng.ready())
    except Exception as e:
        bad(f"loading raised {type(e).__name__}: {e}")
        return {"engine": None, "local": False}
    info(f"load took {time.time() - t0:.1f}s")

    if not ready:
        bad(f"engine not ready: {eng.unavailable_reason() or 'unknown'}")
        return {"engine": None, "local": False}

    local = bool(getattr(eng, "_local_ready", False))
    (ok if local else bad)(
        f"local ensemble: {'loaded on ' + str(getattr(eng, '_local_device', '?')) if local else 'NOT loaded'}")
    if local:
        names = [m.get("name") for m in getattr(eng, "_models", [])]
        info(f"models: {', '.join(str(n) for n in names) or '(none)'}")
    else:
        info("Without this there is no second opinion when the hosted API fails "
             "— which is exactly the situation that produced no detection at all.")
    print(f"  reported backend: {getattr(eng, 'backend', '?')}   "
          f"velma_in_use={getattr(eng, '_use_velma', False)}")
    return {"engine": eng, "local": local}


# ------------------------------------------------------- 5. ground-truth score
def truth_of(path: Path) -> str | None:
    n = path.name.lower()
    if any(m in n for m in CLONE_MARKERS):
        return "clone"
    if any(m in n for m in REAL_MARKERS):
        return "real"
    return None


def load_audio(path: Path):
    """Decode any container to mono float32 @16k using the server's own decoder."""
    from antai.inference.voice.audio_io import decode_audio_bytes, to_sample_rate

    audio, sr = decode_audio_bytes(path.read_bytes())
    if audio is None or not sr:
        return None, 0
    audio, sr = to_sample_rate(audio, sr, 16000)
    return audio, int(sr)


def score_windows(eng, audio, sr, window_s: float) -> list[dict]:
    """Score the clip the way a live call is scored: in short segments.

    A call alerts when ANY segment crosses the line, so a whole-file average would
    hide exactly the behaviour that matters.
    """
    n = int(window_s * sr)
    if n <= 0 or len(audio) <= n:
        return [eng.analyze(audio, sr)]
    out = []
    for i in range(0, len(audio) - n + 1, n):
        out.append(eng.analyze(audio[i:i + n], sr))
    return out


def check_samples(eng, sample_dir: Path, window_s: float, alert_at: float) -> dict:
    head("5. your own samples: real voice vs AI-cloned voice")
    if eng is None:
        warn("skipped: no working engine to score with")
        return {}
    if not sample_dir.is_dir():
        warn(f"no sample directory at {sample_dir}")
        return {}

    files = sorted(p for p in sample_dir.iterdir()
                   if p.suffix.lower() in (".wav", ".mp3", ".m4a", ".flac", ".ogg"))
    if not files:
        warn(f"no audio files in {sample_dir}")
        return {}

    tally = {"clone": [], "real": []}
    unscored = {"clone": 0, "real": 0, None: 0}
    for p in files:
        label = truth_of(p)
        audio, sr = load_audio(p)
        if audio is None:
            unscored[label] += 1
            bad(f"{p.name}: could not decode (mp3/m4a need PyAV — "
                f"pip install av)")
            continue
        results = score_windows(eng, audio, sr, window_s)
        probs = [r["spoof_prob"] for r in results if r.get("spoof_prob") is not None]
        if not probs:
            unscored[label] += 1
            bad(f"{p.name}: NO SCORE AT ALL — detection produced nothing")
            continue
        peak = max(probs)
        flagged = peak > alert_at
        agree = results[max(range(len(results)),
                            key=lambda i: results[i].get("spoof_prob") or -1)]
        tag = {"clone": "CLONE", "real": "real ", None: "  ?  "}[label]
        mark = GRN if (label == "clone") == flagged and label else \
            (RED if label else DIM)
        print(f"  {mark}[{tag}]{OFF} {p.name[:38]:38} peak={peak:.3f} "
              f"{'FLAGGED' if flagged else 'not flagged':11} "
              f"{DIM}{len(probs)} windows, {agree.get('agreement')}{OFF}")
        info(f"per-model: {agree.get('per_model')}")
        if label:
            tally[label].append(flagged)

    caught = sum(tally["clone"])
    missed = len(tally["clone"]) - caught
    false_alarms = sum(tally["real"])
    print()
    if tally["clone"]:
        (ok if missed == 0 else bad)(
            f"cloned samples flagged: {caught}/{len(tally['clone'])}"
            f"{'' if missed == 0 else '  <- MISSED %d' % missed}")
    elif unscored["clone"]:
        bad(f"{unscored['clone']} cloned sample(s) could not be scored at all, so "
            f"nothing here proves detection works. Fix the decode/scoring error "
            f"above first — an unreadable file is not a passing test.")
    else:
        warn("no cloned samples found — nothing here proves detection works. "
             "Drop an AI-generated clip named e.g. 'me_ai_voice_1.mp3' in "
             f"{sample_dir.name}/ and re-run.")
    if tally["real"]:
        (ok if false_alarms == 0 else warn)(
            f"real samples wrongly flagged: {false_alarms}/{len(tally['real'])}")
    return {"caught": caught, "missed": missed, "false_alarms": false_alarms,
            "clones": len(tally["clone"]), "reals": len(tally["real"]),
            "unscored_clones": unscored["clone"]}


# ---------------------------------------------------------------------- main
def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--samples", default=str(SERVER_ROOT / "audio_samples"))
    ap.add_argument("--window", type=float, default=0.0,
                    help="seconds per scored segment (default: pipeline."
                         "voice_window_s — the exact window the live server "
                         "scores, so this check predicts real call behaviour)")
    ap.add_argument("--eos", default="",
                    help="pin the end-of-stream framing: binary|text|json|none")
    ap.add_argument("--skip-hosted", action="store_true")
    ap.add_argument("--skip-local", action="store_true")
    ap.add_argument("--server", default="http://127.0.0.1:8000")
    ap.add_argument("--no-server", action="store_true")
    args = ap.parse_args()

    print(f"{BOLD}antAI — AI-voice-clone detection self-check{OFF}")
    print(f"{DIM}server root: {SERVER_ROOT}{OFF}")

    running = check_running_code(None if args.no_server else args.server)
    conf = check_config()

    from antai.config import get_config
    alert_at = get_config().pipeline.voice_alert_threshold
    # Score exactly the window the live pipeline scores. If this check used a
    # different clip length than production, it could report "your clone IS
    # detected" while real calls kept missing it — the models are strongly
    # length-sensitive, which is why voice_window_s exists at all.
    window_s = args.window or get_config().pipeline.voice_window_s
    info(f"scoring in {window_s:g}s windows (pipeline.voice_window_s), "
         f"alert line {alert_at:g}")

    sample_dir = Path(args.samples)
    probe_audio, probe_sr = None, 16000
    if sample_dir.is_dir():
        # prefer a REAL voice clip for the protocol probe: if the endpoint is
        # healthy this should come back low, which also sanity-checks polarity
        cands = sorted(p for p in sample_dir.iterdir()
                       if truth_of(p) == "real" and p.suffix.lower() != ".m4a")
        if cands:
            probe_audio, probe_sr = load_audio(cands[0])
            if probe_audio is not None:
                # 6s is plenty for a protocol check and keeps the probe quick
                probe_audio = probe_audio[:probe_sr * 6]
                info(f"protocol probe will use {cands[0].name}")

    hosted = {"working": False}
    if not args.skip_hosted:
        hosted = check_hosted(probe_audio, probe_sr, args.eos)
    else:
        head("3. hosted backend (Velma-2)")
        info("skipped (--skip-hosted)")

    local = {"engine": None, "local": False}
    if not args.skip_local:
        local = check_local()
    else:
        head("4. local SSL ensemble")
        info("skipped (--skip-local)")

    acc = check_samples(local.get("engine"), sample_dir, window_s, alert_at)

    # ------------------------------------------------------------- verdict
    head("VERDICT")
    can_detect = bool(hosted.get("working") or local.get("local"))
    if args.skip_hosted and args.skip_local:
        warn("both backends were skipped — this run proves nothing about "
             "detection. Drop the --skip flags for a real answer.")
    elif can_detect:
        ok("A cloned voice CAN be scored right now.")
    else:
        bad("A cloned voice CANNOT be detected right now — neither backend is "
            "producing scores.")
    print(f"  hosted Velma:        "
          f"{DIM + 'skipped' + OFF if args.skip_hosted else (GRN + 'working (eos=%s)' % hosted.get('eos') + OFF if hosted.get('working') else RED + 'not producing verdicts' + OFF)}")
    print(f"  local SSL ensemble:  "
          f"{DIM + 'skipped' + OFF if args.skip_local else (GRN + 'loaded' + OFF if local.get('local') else RED + 'not loaded' + OFF)}")
    if acc.get("clones"):
        good = acc["missed"] == 0
        print(f"  on your clones:      "
              f"{(GRN if good else RED)}{acc['caught']}/{acc['clones']} flagged{OFF}"
              f"   real-voice false alarms: {acc['false_alarms']}/{acc['reals']}")

    if running.get("restart_required"):
        print(f"\n  {YEL}Note:{OFF} your LIVE server is still running older code. "
              f"Everything above tested the files on disk — restart the server "
              f"before judging the app.")
    elif running.get("server_reachable") is False and not args.no_server:
        print(f"\n  {DIM}Note: no live server was reachable, so this tested the "
              f"code on disk only.{OFF}")

    if not can_detect:
        print(f"\n  Next step: fix whichever backend is closer. The local ensemble "
              f"needs weights in models/voice_deepfake (+ _cross2) and does not "
              f"depend on any network or key, so it is usually the faster path to "
              f"a working demo.")
    if args.skip_hosted and args.skip_local:
        return 1
    proven = bool(acc.get("clones")) and acc.get("missed", 0) == 0 \
        and not acc.get("unscored_clones")
    return 0 if can_detect and proven else 1


if __name__ == "__main__":
    try:
        sys.exit(main())
    except KeyboardInterrupt:
        print("\ninterrupted")
        sys.exit(130)
