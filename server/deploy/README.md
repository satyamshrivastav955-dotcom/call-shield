# antAI AWS Backup Deployment

The local dev rig (Windows + `python run_dev.py`) is the primary demo server.
This folder makes an **EC2 backup** reproducible: same code, same models, one
command to deploy, one command to refresh weights on both machines.

## Files

| File | Purpose |
|---|---|
| `Dockerfile` | API image (CPU wheels; models/.env mounted, not baked in) |
| `docker-compose.yml` | `antai` (API) + `coturn` (TURN relay, host network) |
| `config.aws.yaml` | `../config.yaml` with only the `ice:` block changed — **edit `turn_url`** |
| `turnserver.aws.conf` | `../turnserver.conf` for a public network (wide relay range) |
| `deploy.sh` | build + up + health-check; `--refresh-models` re-pulls weights |

## Instance

- **GPU:** `g4dn.xlarge` (T4 16GB) — runs everything comfortably.
- **Budget CPU:** `c6i.2xlarge` — every engine has a local/CPU fallback, so
  the demo works, just slower per segment. Fine for a *backup*.
- Attach an **Elastic IP** (stable address; the app's `server_host` field and
  `config.aws.yaml ice.turn_url` both point at it).
- **Not Spot** for judged demos — Spot gets reclaimed mid-call.

## Security group (inbound)

| Port | Protocol | What |
|---|---|---|
| 8765 | tcp | REST + WebSocket API |
| 3478 | tcp + udp | TURN |
| 49152–65535 | udp | TURN relay range (matches `turnserver.aws.conf`) |

Put nginx + TLS in front of 8765 if this is ever exposed beyond the demo.

## First deploy

**From your local machine (AWS CLI configured — recommended):**

```bash
cd server/deploy
./aws_bootstrap.sh                    # creates everything + deploys
# stops before deploy if ~/SIH26P1/server/.env is not on the box yet —
# create it (see Secrets checklist), then just re-run the same command.
```

Idempotent: re-running re-associates the Elastic IP and re-runs `deploy.sh`.
Flags: `--instance t3.large` (default; `g4dn.xlarge` for GPU), `--region ap-south-1` (default).

**On the box manually:**

```bash
# on the box, repo checked out at ~/SIH26P1
cd server/deploy
cp ../.env.example ../.env && nano ../.env     # real keys — never commit
nano config.aws.yaml                           # ice.turn_url -> turn:<EIP>:3478
./deploy.sh
```

The Android app needs zero changes — edit the server host in the calls screen
(`server_host` SharedPreferences) to the Elastic IP.

## Model updates (both machines, one source of truth)

Models come from Hugging Face Hub by `repo_id`
(`setup/download_models.py`). To swap/upgrade one:

1. Push the new checkpoint to a (private) HF repo.
2. Update its `repo_id` in `server/setup/download_models.py` — commit that.
3. **AWS:** `./deploy.sh --refresh-models`
   **Local:** `python setup/download_models.py --only <engine>`
4. Same weights everywhere; no manual file copying, no drift.

## Secrets checklist

`server/.env` on the instance (compose `env_file` mounts it; it is gitignored
and never baked into the image): `DEEPGRAM_API_KEY`, `VELMA_API_KEY`,
`GROQ_API_KEY`, `GEMINI_API_KEY`, `OPENROUTER_API_KEY`,
`STORAGE_ENCRYPTION_KEY`, `ANTAI_PHONE_SALT`.

- **Never** leave `storage.encryption_key` at `change-me-please` — `db.py`
  CRITICAL-warns on boot, and on a public box that is a real leak.
- Rotate keys after any public demo (they were on a demo network).

## Cost control

A stopped EC2 instance bills ~nothing (EIP + disk only). Stop it outside
demo/judging windows; start + `./deploy.sh` when needed — health check
confirms it is serving before you present.
