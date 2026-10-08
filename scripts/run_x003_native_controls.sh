#!/usr/bin/env bash
set -euo pipefail

EVIDENCE_DIR="app/build/x003-evidence"
TARGET_PACKAGE="com.clw.aivideotranslator"
TEST_RUNNER="com.clw.aivideotranslator.test/androidx.test.runner.AndroidJUnitRunner"
TEST_CLASS="com.clw.aivideotranslator.subtitle.android.SubtitleLayoutInstrumentedTest"

mkdir -p "$EVIDENCE_DIR"
rm -f "$EVIDENCE_DIR/layout-metrics.json" "$EVIDENCE_DIR/instrumentation.txt"

# Build the exact app/test APK pair first. We deliberately drive AndroidJUnitRunner
# directly instead of relying on connectedDebugAndroidTest filtering: the previous
# harness could report a successful Gradle task with zero discovered tests, then
# lose the evidence-producing package before capture.
gradle --stacktrace assembleDebug assembleDebugAndroidTest

app_apk="$(find app/build/outputs/apk/debug -maxdepth 1 -type f -name '*.apk' -print -quit)"
test_apk="$(find app/build/outputs/apk/androidTest/debug -maxdepth 1 -type f -name '*.apk' -print -quit)"

if [ -z "$app_apk" ] || [ -z "$test_apk" ]; then
  echo "X003: expected debug/test APKs were not produced" >&2
  exit 1
fi

adb install -r -t "$app_apk" >/dev/null
adb install -r -t "$test_apk" >/dev/null

# Remove stale evidence so a passing result can only come from this exact run.
adb shell run-as "$TARGET_PACKAGE" rm -rf files/x003-evidence || true

set +e
instrumentation_output="$(adb shell am instrument -w -r \
  -e class "$TEST_CLASS" \
  -e buildSha "$GITHUB_SHA" \
  "$TEST_RUNNER" 2>&1)"
adb_status=$?
set -e

printf '%s\n' "$instrumentation_output" | tee "$EVIDENCE_DIR/instrumentation.txt"

# adb/am instrument can return a transport-success exit code even when the test
# runner reports a test failure. Treat runner output as part of the verdict.
if [ "$adb_status" -ne 0 ] || \
   grep -Eq 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed|INSTRUMENTATION_STATUS_CODE: -2' "$EVIDENCE_DIR/instrumentation.txt" || \
   ! grep -Eq 'OK \([1-9][0-9]* tests?\)' "$EVIDENCE_DIR/instrumentation.txt"; then
  echo "X003: instrumentation did not produce an unambiguous passing verdict" >&2
  exit 1
fi

# Verify the target app actually owns a non-empty evidence file before copying it.
if ! adb shell run-as "$TARGET_PACKAGE" sh -c \
  'test -s files/x003-evidence/layout-metrics.json'; then
  echo "X003: layout evidence file was not produced by the passing instrumentation run" >&2
  exit 1
fi

adb exec-out run-as "$TARGET_PACKAGE" cat files/x003-evidence/layout-metrics.json \
  > "$EVIDENCE_DIR/layout-metrics.json"

python3 - <<'PY'
import json
import os
from pathlib import Path

path = Path('app/build/x003-evidence/layout-metrics.json')
report = json.loads(path.read_text(encoding='utf-8'))
assert report['buildSha'] == os.environ['GITHUB_SHA'], 'evidence build mismatch'
assert len(report['results']) == 21, 'incomplete native geometry report'
assert report['humanReadability'] == 'NOT_MEASURED', 'native controls cannot approve human readability'
assert report['previewExportParity'] == 'NOT_MEASURED', 'native controls cannot approve X004'
PY
