#!/usr/bin/env bash
# =====================================================================
#  antAI AWS backup — deploy / update / model-refresh script.
#  Run ON the EC2 instance, from server/deploy/.
#  Idempotent: safe to re-run; it rebuilds, restarts, and health-checks.
#
#  ONE-TIME MANUAL SETUP (not this script's job):
#    - EC2 instance + security group (inbound):
#        8765/tcp                 API + WebSocket
#        3478/tcp + 3478/udp      TURN
#        49152-65535/udp          TURN relay range (must match turnserver.aws.conf)
#    - Elastic IP attached (stable address for the app + ice.turn_url)
#    - Edit config.aws.yaml: ice.turn_url -> turn:<YOUR-EIP>:3478
#    - server/.env present on the box with real keys (gitignored, never commit)
#    - docker + docker compose plugin installed
#  MODEL UPDATE (same workflow on local + AWS — single source of truth):
#    1. Fine-tune / swap the model, push the checkpoint to a HF repo
#    2. Update the repo_id in server/setup/download_models.py
#    3. Run this script with: ./deploy.sh --refresh-models
#    4. Same command on both machines keeps weights from drifting.
# ---------------------------------------------------------------------
set -euo pipefail
cd "$(dirname "$0")"

REFRESH_MODELS=0
[ "${1:-}" = "--refresh-models" ] && REFRESH_MODELS=1

echo ">>> building image (deps cached after first run)"
docker compose build

if [ "$REFRESH_MODELS" = 1 ]; then
  echo ">>> refreshing models from Hugging Face Hub (into ../models, volume-mounted)"
  docker compose run --rm --no-deps antai python setup/download_models.py
fi

echo ">>> starting stack (antai + coturn)"
docker compose up -d

echo ">>> waiting for health check (first boot warms models; can take minutes)"
for i in $(seq 1 40); do
  if curl -sf http://localhost:8765/ > /dev/null 2>&1; then
    echo "OK — antAI server is live on :8765"
    curl -s http://localhost:8765/ && echo
    exit 0
  fi
  sleep 5
done
echo "FAILED — server did not come up in 200s. Check: docker compose logs antai"
exit 1
