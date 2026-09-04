import sys, numpy as np, json
sys.path.insert(0, "src")
from pathlib import Path
for d in ["models/voice_deepfake", "models/voice_deepfake_cross2"]:
    cfgp = Path(d) / "config.json"
    if cfgp.exists():
        cfg = json.load(open(cfgp))
        id2l = cfg.get("id2label")
        print(d, "-> id2label:", id2l)
        print("   num_labels:", cfg.get("num_labels"))
    else:
        print(d, "-> no config.json; files:", [p.name for p in Path(d).iterdir()][:8])
