#!/usr/bin/env bash
# Non-interactive CI startup smoke. This does not validate audible playback.
set -euo pipefail
SERIAL="${1:?Provide emulator serial}"; APK="${2:?Provide signed test APK}"
OUT="${3:?Provide output directory}"
mkdir -p "$OUT"
ADB=(adb -s "$SERIAL")
"${ADB[@]}" install -r "$APK"
"${ADB[@]}" logcat -c
"${ADB[@]}" shell am start -W -n radio.spider.app/.MainActivity >"$OUT/start.txt"
sleep 5
"${ADB[@]}" shell pidof radio.spider.app >"$OUT/pid.txt"
"${ADB[@]}" logcat -d >"$OUT/logcat.txt"
"${ADB[@]}" exec-out screencap -p >"$OUT/startup.png"
python3 - "$OUT" <<'PY'
from pathlib import Path
import sys
p=Path(sys.argv[1]);text=(p/'logcat.txt').read_text(errors='replace')
if not (p/'pid.txt').read_text().strip():raise SystemExit('App process missing after startup')
if 'FATAL EXCEPTION' in text and 'Process: radio.spider.app' in text:raise SystemExit('App crashed')
print('PASS emulator startup; playback and OEM surfaces require separate device checks')
PY
