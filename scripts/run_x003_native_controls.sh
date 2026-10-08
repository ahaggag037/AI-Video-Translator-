#!/usr/bin/env bash
set -euo pipefail

mkdir -p app/build/x003-evidence
rm -f app/build/x003-evidence/layout-metrics.json

test_status=0
gradle --stacktrace connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.clw.aivideotranslator.subtitle.android.SubtitleLayoutInstrumentedTest \
  -Pandroid.testInstrumentationRunnerArguments.buildSha="$GITHUB_SHA" || test_status=$?

# connectedDebugAndroidTest may remove the app/test packages after a successful run.
# Keep that Gradle task as the authoritative full-class test verdict, then reinstall
# the already-built APKs and rerun only the evidence-producing test method so the
# immutable JSON can be copied while the target package is still installed.
if [ "$test_status" -eq 0 ]; then
  app_apk="$(find app/build/outputs/apk/debug -maxdepth 1 -type f -name '*.apk' -print -quit)"
  test_apk="$(find app/build/outputs/apk/androidTest/debug -maxdepth 1 -type f -name '*.apk' -print -quit)"

  if [ -z "$app_apk" ] || [ -z "$test_apk" ]; then
    echo "X003 evidence capture: expected debug/test APKs were not produced" >&2
    test_status=1
  else
    adb install -r -t "$app_apk" >/dev/null || test_status=$?
    if [ "$test_status" -eq 0 ]; then
      adb install -r -t "$test_apk" >/dev/null || test_status=$?
    fi

    if [ "$test_status" -eq 0 ]; then
      # Remove any stale evidence before the dedicated capture run.
      adb shell run-as com.clw.aivideotranslator rm -rf files/x003-evidence || true

      capture_status=0
      adb shell am instrument -w -r \
        -e class 'com.clw.aivideotranslator.subtitle.android.SubtitleLayoutInstrumentedTest#recordNativeGeometryOutcomesWithoutInventingReadabilityPassRate' \
        -e buildSha "$GITHUB_SHA" \
        com.clw.aivideotranslator.test/androidx.test.runner.AndroidJUnitRunner || capture_status=$?

      if [ "$capture_status" -ne 0 ]; then
        test_status="$capture_status"
      elif ! adb exec-out run-as com.clw.aivideotranslator \
        cat files/x003-evidence/layout-metrics.json \
        > app/build/x003-evidence/layout-metrics.json; then
        rm -f app/build/x003-evidence/layout-metrics.json
        test_status=1
      fi
    fi
  fi
fi

if [ "$test_status" -eq 0 ]; then
  python3 - <<'PY'
import json, os
from pathlib import Path
report = json.loads(Path('app/build/x003-evidence/layout-metrics.json').read_text())
assert report['buildSha'] == os.environ['GITHUB_SHA'], 'evidence build mismatch'
assert len(report['results']) == 21, 'incomplete native geometry report'
assert report['humanReadability'] == 'NOT_MEASURED', 'native controls cannot approve human readability'
assert report['previewExportParity'] == 'NOT_MEASURED', 'native controls cannot approve X004'
PY
fi

exit "$test_status"
