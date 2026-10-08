#!/usr/bin/env bash
set -euo pipefail

mkdir -p app/build/x003-evidence
test_status=0
gradle --stacktrace connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.clw.aivideotranslator.subtitle.android.SubtitleLayoutInstrumentedTest \
  -Pandroid.testInstrumentationRunnerArguments.buildSha="$GITHUB_SHA" || test_status=$?

if ! adb exec-out run-as com.clw.aivideotranslator cat files/x003-evidence/layout-metrics.json > app/build/x003-evidence/layout-metrics.json; then
  rm -f app/build/x003-evidence/layout-metrics.json
  if [ "$test_status" -eq 0 ]; then test_status=1; fi
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
