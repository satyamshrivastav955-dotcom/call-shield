#!/usr/bin/env bash
# ===================================================================
#  antAI - USB connectivity setup (macOS / Linux / WSL / Git-Bash)
#
#  Makes every USB-connected Android phone reach the antAI server on
#  THIS computer with NO Wi-Fi / LAN, by tunnelling over the USB cable:
#      phone localhost:18765  --USB-->  this PC localhost:8765  (server)
#      phone localhost:3478   --USB-->  this PC localhost:3478  (TURN, media)
#
#  Run AFTER starting the server (python run_dev.py) and again whenever
#  you replug a phone or restart the adb server.
#
#  Requires: adb on PATH + USB debugging enabled on each phone.
# ===================================================================
set -u

SERVER_PORT=8765
PHONE_PORT=18765
TURN_PORT=3478

if ! command -v adb >/dev/null 2>&1; then
  echo "[ERROR] adb not found on PATH. Install Android platform-tools."
  echo "        https://developer.android.com/tools/releases/platform-tools"
  exit 1
fi

adb start-server >/dev/null 2>&1

count=0
# lines look like:  "SERIAL\tdevice"  (skip header + blanks + offline/unauthorized)
while IFS=$'\t' read -r serial state _; do
  [ -z "${serial:-}" ] && continue
  case "$serial" in "List of devices attached") continue ;; esac
  if [ "${state:-}" = "device" ]; then
    count=$((count+1))
    echo "[device $count] $serial"
    adb -s "$serial" reverse tcp:$PHONE_PORT tcp:$SERVER_PORT >/dev/null \
      && echo "    > app  : phone localhost:$PHONE_PORT -> PC localhost:$SERVER_PORT   OK"
    adb -s "$serial" reverse tcp:$TURN_PORT tcp:$TURN_PORT >/dev/null \
      && echo "    > media: phone localhost:$TURN_PORT  -> PC localhost:$TURN_PORT    OK"
  else
    echo "[skip] $serial is \"${state:-?}\" (unlock the phone / tap Allow USB debugging)"
  fi
done < <(adb devices)

echo
if [ "$count" -eq 0 ]; then
  echo "[ERROR] No authorized devices found."
  echo "        - Plug in with a data-capable USB cable."
  echo "        - Enable Settings > Developer options > USB debugging."
  echo "        - Tap \"Allow\" on the phone, then re-run this."
  exit 1
fi

echo "Done. Forwarded ports for $count device(s)."
echo "Active reverse tunnels (last device queried):"
adb reverse --list
echo
echo "Keep the phones plugged in. If you replug or reboot adb, run this again."
