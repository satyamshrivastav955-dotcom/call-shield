# audio_samples — label schema (on-device plan P0)

`eval_detector.py` collects from `spoof/` + `bonafide/` subdirs; flat files use
filename heuristic (`fake|synth|spoof|clone|tts|ai` → spoof).

## Labels (`labels.csv`)

Columns: `filename,split,label,language,speaker,source`

- `split`: `train|val|test` — keep **speaker-disjoint** (no speaker in 2 splits).
- `label`: `spoof|bonafide`.
- `language`: `en|hi|hinglish|...`.
- `source`: `human|tts-clone|vc-clone|asvspoof5|wavefake|in-the-wild|commonvoice|indictts`.

## Growing the set (open-source, no SIH data on disk)

- Spoof: ASVspoof5, WaveFake, In-the-Wild subset → `spoof/`, mark source.
- Bonafide Indian: CommonVoice `hi`, IndicTTS bonafide, IIT-Madras MSS → `bonafide/`.
- Run: `python scripts/eval_detector.py --dir audio_samples --threshold 40`
- Baseline today: 2 spoof + 4 bonafide (Satyam/Tushar). Record FPR/FNR before any
  AASIST-L / ECAPA / threshold change.
