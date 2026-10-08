#!/usr/bin/env bash
set -euo pipefail

EVIDENCE_DIR="app/build/x003-evidence"
TARGET_PACKAGE="com.clw.aivideotranslator"
TEST_RUNNER="com.clw.aivideotranslator.test/androidx.test.runner.AndroidJUnitRunner"
TEST_CLASS="com.clw.aivideotranslator.subtitle.android.SubtitleLayoutInstrumentedTest"

mkdir -p "$EVIDENCE_DIR"
rm -f "$EVIDENCE_DIR"/*

# Build the exact app/test APK pair first. Drive AndroidJUnitRunner directly so
# zero-test Gradle discovery cannot masquerade as a passing native-control run.
gradle --stacktrace assembleDebug assembleDebugAndroidTest

app_apk="$(find app/build/outputs/apk/debug -maxdepth 1 -type f -name '*.apk' -print -quit)"
test_apk="$(find app/build/outputs/apk/androidTest/debug -maxdepth 1 -type f -name '*.apk' -print -quit)"

if [ -z "$app_apk" ] || [ -z "$test_apk" ]; then
  echo "X003: expected debug/test APKs were not produced" >&2
  exit 1
fi

adb install -r -t "$app_apk" >/dev/null
adb install -r -t "$test_apk" >/dev/null

# Stale evidence must never satisfy a new build-bound verdict.
adb shell run-as "$TARGET_PACKAGE" rm -rf files/x003-evidence || true

run_method() {
  local method="$1"
  local log="$EVIDENCE_DIR/instrumentation-${method}.txt"
  local crashlog="$EVIDENCE_DIR/logcat-${method}.txt"
  local output
  local adb_status

  adb logcat -c || true

  set +e
  output="$(adb shell am instrument -w -r \
    -e class "$TEST_CLASS#$method" \
    -e buildSha "$GITHUB_SHA" \
    "$TEST_RUNNER" 2>&1)"
  adb_status=$?
  set -e

  printf '%s\n' "$output" | tee "$log"

  # am instrument may report transport success while AndroidJUnitRunner reports
  # a test failure or process crash. Require an explicit one-test OK verdict.
  if [ "$adb_status" -ne 0 ] || \
     grep -Eq 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed|INSTRUMENTATION_STATUS_CODE: -2' "$log" || \
     ! grep -Eq 'OK \(1 test\)' "$log"; then
    adb logcat -d -v threadtime -t 2500 > "$crashlog" 2>&1 || true
    echo "X003: $method did not produce an unambiguous passing verdict" >&2
    return 1
  fi
}

# Run independently so a process crash identifies the exact falsifier instead
# of collapsing the whole class into an opaque 'Process crashed' result.
methods=(
  packagedFontIsExactPinnedCandidateWithActualStyle
  nativeBoundaryProviderPreservesDiacriticAndProtectedUrl
  emergencyTokenWrapAndMissingGlyphCannotBecomeRenderable
  recordNativeGeometryOutcomesWithoutInventingReadabilityPassRate
)

for method in "${methods[@]}"; do
  run_method "$method" || exit 1
done

# Verify the evidence-producing method actually persisted a non-empty report.
if ! adb shell run-as "$TARGET_PACKAGE" ls -l \
  files/x003-evidence/layout-metrics.json >/dev/null 2>&1; then
  echo "X003: layout evidence file was not produced by passing instrumentation" >&2
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
