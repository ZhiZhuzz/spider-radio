#!/usr/bin/env bash
set -uo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
OUT="${TEST_REPORT_DIR:-$ROOT/build/test-reports/$(date -u +%Y%m%dT%H%M%SZ)}"
mkdir -p "$OUT/classes"
PASS=0; FAIL=0; SKIP=0
record(){ printf '%-6s %s\n' "$1" "$2" | tee -a "$OUT/summary.txt"; }
run(){
  local name="$1"; shift
  if "$@" >"$OUT/$name.log" 2>&1; then PASS=$((PASS+1)); record PASS "$name";
  else FAIL=$((FAIL+1)); record FAIL "$name (see $name.log)"; fi
}
printf 'Spider Radio 0.8.3 host regressions\n' >"$OUT/summary.txt"
JAVAC="${JAVA_HOME:+$JAVA_HOME/bin/javac}"; JAVA="${JAVA_HOME:+$JAVA_HOME/bin/java}"
JAVAC="${JAVAC:-$(command -v javac || true)}"; JAVA="${JAVA:-$(command -v java || true)}"
NODE="${NODE_BIN:-$(command -v node || true)}"
APP=app/src/main/java/radio/spider/app
if [[ -x "$JAVAC" && -x "$JAVA" ]]; then
  if "$JAVAC" -encoding UTF-8 -source 8 -target 8 -d "$OUT/classes" \
    "$APP/MediaSurfacePolicy.java" "$APP/TrackInfo.java" "$APP/MediaPresentation.java" \
    "$APP/SourceLoader.java" "$APP/StreamProbe.java" "$APP/SleepTimer.java" \
    tests/MediaSurfacePolicyTest.java tests/TrackInfoTest.java tests/MediaPresentationTest.java \
    tests/SourceLoaderTest.java tests/StreamProbeTest.java tests/SleepTimerTest.java >"$OUT/javac.log" 2>&1; then
    for name in MediaSurfacePolicyTest TrackInfoTest MediaPresentationTest SourceLoaderTest StreamProbeTest SleepTimerTest; do
      run "$name" "$JAVA" -cp "$OUT/classes" "radio.spider.app.$name"
    done
  else FAIL=$((FAIL+1)); record FAIL 'Java compilation (see javac.log)'; fi
  if [[ -f "${ORG_JSON_JAR:-}" ]]; then
    if "$JAVAC" -encoding UTF-8 -source 8 -target 8 -cp "$ORG_JSON_JAR" -d "$OUT/classes" \
      "$APP/LibraryCatalog.java" "$APP/PlaybackOrder.java" "$APP/QueueRemoval.java" tests/LibraryCatalogTest.java tests/LibraryLogicTest.java >"$OUT/javac-library.log" 2>&1; then
      for name in LibraryCatalogTest LibraryLogicTest; do run "$name" "$JAVA" -cp "$OUT/classes:$ORG_JSON_JAR" "radio.spider.app.$name"; done
    else FAIL=$((FAIL+1)); record FAIL 'Library Java compilation'; fi
  else SKIP=$((SKIP+2)); record SKIP 'Two library tests need ORG_JSON_JAR'; fi
  export JAVA_BIN="$JAVA"
  run ambient-production python3 tests/patch-native-082.py
  run media-output-production python3 tests/system-media-output.py
else SKIP=$((SKIP+10)); record SKIP 'Java compiler/runtime unavailable'; fi
export JAVA_BIN="$JAVA"
if [[ -n "$NODE" ]]; then
  for name in dom-smoke-081 timer-082 frontend-083; do run "$name" "$NODE" "tests/$name.cjs"; done
  if "$NODE" -e "require('./tests/browser.cjs')" >/dev/null 2>&1; then
    for name in system-playback-07 media-surface-08 library-064 library-063 customization radio-ui; do run "$name" "$NODE" "tests/$name.cjs"; done
  else SKIP=$((SKIP+6)); record SKIP 'Browser tests need Playwright (npm ci)'; fi
else SKIP=$((SKIP+9)); record SKIP 'Node unavailable'; fi
record TOTAL "PASS=$PASS FAIL=$FAIL SKIP=$SKIP"
record LIMIT 'Host checks do not test Android audio decoding, service lifecycle or OEM media surfaces.'
printf 'Report directory: %s\n' "$OUT"
# An incomplete run is not a release pass. Opt in only for development checks.
[[ "$FAIL" -eq 0 && ( "$SKIP" -eq 0 || "${ALLOW_TEST_SKIPS:-0}" == 1 ) ]]
