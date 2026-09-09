#!/usr/bin/env bash
# =====================================================================
#  ONE-COMMAND AWS BOOTSTRAP — run from your LOCAL machine (needs aws cli
#  configured: `aws configure`), from server/deploy/.
#
#  Creates (idempotent where cheap, named so re-runs are safe):
#    - key pair  antai-sih26        (~/.ssh/antai-sih26.pem)
#    - security group antai-sih26-sg (8765, 3478, 49152-65535, + 22 locked
#      to YOUR current public IP)
#    - EC2 instance  antai-sih26    (t3.large default; see flags)
#    - Elastic IP    antai-sih26-eip (allocated + associated each run)
#  Then SSHes in, clones the repo, installs Docker, and runs deploy.sh.
#
#  Usage:
#    ./aws_bootstrap.sh [--instance t3.large] [--region ap-south-1]
#
#  Re-run after `stop`/`start` in the console: re-associates the EIP and
#  re-runs deploy.sh (idempotent). Instance creation is skipped if a
#  running/stopped instance named antai-sih26 already exists.
# ---------------------------------------------------------------------
set -euo pipefail
cd "$(dirname "$0")"

NAME=antai-sih26
REGION=$(aws configure get region 2>/dev/null || echo ap-south-1)
INSTANCE_TYPE=t3.large
AMI_OWNERS=099720109477       # Canonical (Ubuntu 24.04 LTS)
while [ $# -gt 0 ]; do
  case "$1" in
    --instance) INSTANCE_TYPE="$2"; shift 2 ;;
    --region)   REGION="$2"; shift 2 ;;
    *) echo "unknown flag: $1"; exit 1 ;;
  esac
done
export AWS_DEFAULT_REGION=$REGION

MY_IP=$(curl -sf https://checkip.amazonaws.com || true)
[ -z "$MY_IP" ] && { echo "cannot determine your public IP (checkip.amazonaws.com)"; exit 1; }
MY_IP=${MY_IP%%$'\n'}

# --- 1. key pair -------------------------------------------------------
PEM=$HOME/.ssh/${NAME}.pem
if ! aws ec2 describe-key-pairs --key-names "$NAME" >/dev/null 2>&1; then
  echo ">>> creating key pair $NAME"
  aws ec2 create-key-pair --key-name "$NAME" \
    --query 'KeyMaterial' --output text > "$PEM"
  chmod 600 "$PEM"
fi
[ -f "$PEM" ] || { echo "missing $PEM (created elsewhere?)"; exit 1; }

# --- 2. security group -------------------------------------------------
SG_ID=$(aws ec2 describe-security-groups --group-names "${NAME}-sg" \
  --query 'SecurityGroups[0].GroupId' --output text 2>/dev/null || true)
if [ "$SG_ID" = "None" ] || [ -z "$SG_ID" ]; then
  echo ">>> creating security group ${NAME}-sg"
  SG_ID=$(aws ec2 create-security-group --group-name "${NAME}-sg" \
    --description "antAI SIH26 backup" --query GroupId --output text)
fi
echo ">>> sg $SG_ID — ensuring rules (22 locked to $MY_IP/32)"
_ssh_rule() {  # idempotent-ish: authorize, ignore DuplicateError
  aws ec2 authorize-security-group-ingress --group-id "$SG_ID" "$@" \
    2>/dev/null || true
}
_ssh_rule --protocol tcp --port 22    --cidr "${MY_IP}/32"
_ssh_rule --protocol tcp --port 8765  --cidr 0.0.0.0/0
_ssh_rule --protocol tcp --port 3478  --cidr 0.0.0.0/0
_ssh_rule --protocol udp --port 3478  --cidr 0.0.0.0/0
_ssh_rule --protocol udp --port 49152-65535 --cidr 0.0.0.0/0

# --- 3. instance ---------------------------------------------------------
INSTANCE_ID=$(aws ec2 describe-instances --filters "Name=tag:Name,Values=$NAME" \
  "Name=instance-state-name,Values=running,stopped" \
  --query 'Reservations[0].Instances[0].InstanceId' --output text)
if [ "$INSTANCE_ID" = "None" ] || [ -z "$INSTANCE_ID" ]; then
  AMI=$(aws ec2 describe-images --owners $AMI_OWNERS \
    --filters "Name=name,Values=ubuntu/images/hvm-ssd-gp3/ubuntu-noble-24.04-amd64-server-*" \
    "Name=state,Values=available" \
    --query 'sort_by(Images,&CreationDate)[-1].ImageId' --output text)
  echo ">>> launching $INSTANCE_TYPE ($AMI) as $NAME"
  INSTANCE_ID=$(aws ec2 run-instances \
    --image-id "$AMI" --instance-type "$INSTANCE_TYPE" \
    --key-name "$NAME" --security-group-ids "$SG_ID" \
    --block-device-mappings '[{"DeviceName":"/dev/sda1","Ebs":{"VolumeSize":40,"VolumeType":"gp3"}}]' \
    --tag-specifications "ResourceType=instance,Tags=[{Key=Name,Value=$NAME}]" \
    --query 'Instances[0].InstanceId' --output text)
  echo ">>> waiting for instance to be running"
  aws ec2 wait instance-running --instance-ids "$INSTANCE_ID"
else
  STATE=$(aws ec2 describe-instances --instance-ids "$INSTANCE_ID" \
    --query 'Reservations[0].Instances[0].State.Name' --output text)
  [ "$STATE" = "stopped" ] && { echo ">>> starting stopped instance"; aws ec2 start-instances --instance-ids "$INSTANCE_ID" >/dev/null; aws ec2 wait instance-running --instance-ids "$INSTANCE_ID"; }
fi
echo ">>> instance $INSTANCE_ID"

# --- 4. elastic IP -------------------------------------------------------
EIP_ALLOC=$(aws ec2 describe-addresses --filters "Name=tag:Name,Values=${NAME}-eip" \
  --query 'Addresses[0].AllocationId' --output text)
if [ "$EIP_ALLOC" = "None" ] || [ -z "$EIP_ALLOC" ]; then
  echo ">>> allocating elastic ip ${NAME}-eip"
  EIP_ALLOC=$(aws ec2 allocate-address --domain vpc \
    --tag-specifications "ResourceType=elastic-ip,Tags=[{Key=Name,Value=${NAME}-eip}]" \
    --query AllocationId --output text)
fi
echo ">>> associating eip (re-association is fine)"
ASSOC=$(aws ec2 describe-addresses --allocation-ids "$EIP_ALLOC" \
  --query 'Addresses[0].AssociationId' --output text)
[ "$ASSOC" != "None" ] && [ -n "$ASSOC" ] && \
  aws ec2 disassociate-address --association-id "$ASSOC" || true
aws ec2 associate-address --instance-id "$INSTANCE_ID" --allocation-id "$EIP_ALLOC" >/dev/null
PUBLIC_IP=$(aws ec2 describe-addresses --allocation-ids "$EIP_ALLOC" \
  --query 'Addresses[0].PublicIp' --output text)
echo ">>> public ip: $PUBLIC_IP"

# --- 5. provision over ssh ----------------------------------------------
ssh_opts=(-o StrictHostKeyChecking=accept-new -o ConnectTimeout=10 -i "$PEM")
SSH="ubuntu@$PUBLIC_IP"

echo ">>> waiting for ssh"
for i in $(seq 1 30); do
  ssh "${ssh_opts[@]}" "$SSH" true 2>/dev/null && break
  sleep 5
done
ssh "${ssh_opts[@]}" "$SSH" true   # last attempt — fails loudly if never up

echo ">>> provisioning (docker + repo) — idempotent"
ssh "${ssh_opts[@]}" "$SSH" 'bash -s' <<'REMOTE'
set -euo pipefail
command -v docker >/dev/null || {
  curl -fsSL https://get.docker.com | sh
  sudo usermod -aG docker ubuntu
}
[ -d ~/SIH26P1 ] || git clone https://github.com/satyamshrivastav955-dotcom/antAI-Guardian.git ~/SIH26P1
REMOTE

# new docker group membership needs a fresh shell
echo ">>> running deploy.sh on the box"
ssh "${ssh_opts[@]}" "$SSH" 'bash -s' <<'REMOTE'
set -euo pipefail
cd ~/SIH26P1/server
[ -f .env ] || { echo "ERROR: ~/SIH26P1/server/.env missing — create it from .env.example with real keys, then re-run bootstrap"; exit 1; }
cd deploy
./deploy.sh
REMOTE

echo
echo "DONE — server: http://$PUBLIC_IP:8765"
echo "app:  set server_host to $PUBLIC_IP"
echo "turn: edit config.aws.yaml ice.turn_url -> turn:$PUBLIC_IP:3478, re-run bootstrap"
