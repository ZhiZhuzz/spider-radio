#!/usr/bin/env bash
set -euo pipefail
SERIAL="${1:?Usage: collect-android-smoke.sh SERIAL APK_OR_DASH OUTPUT}"
APK="${2:--}"
OUT="${3:?Provide output directory}"
mkdir -p "$OUT"
ADB=(adb -s "$SERIAL")
if [[ "$APK" != "-" ]]; then "${ADB[@]}" install -r "$APK"; fi
"${ADB[@]}" shell getprop > "$OUT/device-properties.txt"
"${ADB[@]}" shell am start -n radio.spider.app/.MainActivity
collect(){
 "${ADB[@]}" shell dumpsys media_session > "$OUT/$1-media-session.txt"
 "${ADB[@]}" shell dumpsys notification > "$OUT/$1-notification.txt"
 "${ADB[@]}" exec-out screencap -p > "$OUT/$1.png"
}
read -r -p '在应用正常播放本地歌曲，然后锁屏；完成后按回车。' _
collect playing
read -r -p '在锁屏暂停；完成后按回车。' _
collect paused
read -r -p '恢复播放，检查拖动进度并切换下一首；完成后按回车。' _
collect next
echo '已收集日志和截图；请人工判定。'
