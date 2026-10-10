#!/usr/bin/env bash
set -euo pipefail

# X006 target-device runner. Run from the exact worker checkout with exactly one adb device attached.
# The script records identity and invokes only the X006 qualification surface. Emulator output is
# still useful staging evidence, but it must never be relabeled as target-device qualification.

mapfile -t DEVICES < <(adb devices | awk 'NR > 1 && $2 == "device" { print $1 }')
if [[ ${#DEVICES[@]} -ne 1 ]]; then
  echo "X006_ERROR expected exactly one adb device, found ${#DEVICES[@]}" >&2
  exit 2
fi

SERIAL="${DEVICES[0]}"
HEAD_SHA="$(git rev-parse HEAD)"
MANUFACTURER="$(adb -s "$SERIAL" shell getprop ro.product.manufacturer | tr -d '\r')"
MODEL="$(adb -s "$SERIAL" shell getprop ro.product.model | tr -d '\r')"
API="$(adb -s "$SERIAL" shell getprop ro.build.version.sdk | tr -d '\r')"
BUILD_FINGERPRINT="$(adb -s "$SERIAL" shell getprop ro.build.fingerprint | tr -d '\r')"

TEST_CLASSES="com.clw.aivideotranslator.session.X006SourceCapturePerformanceInstrumentedTest,com.clw.aivideotranslator.semantic.SubtitlePerformanceInstrumentedTest,com.clw.aivideotranslator.subtitle.android.RasterWindowBudgetInstrumentedTest,com.clw.aivideotranslator.subtitle.android.SnapshotSequentialBurnStressInstrumentedTest,com.clw.aivideotranslator.subtitle.android.SnapshotSeekStressInstrumentedTest,com.clw.aivideotranslator.subtitle.android.PreviewRasterLatencyInstrumentedTest"

mkdir -p build/x006-target-device
LOG="build/x006-target-device/instrumentation.log"
IDENTITY="build/x006-target-device/device_identity.txt"

cat > "$IDENTITY" <<EOF
worker_head_sha=$HEAD_SHA
device_serial=$SERIAL
manufacturer=$MANUFACTURER
model=$MODEL
api=$API
build_fingerprint=$BUILD_FINGERPRINT
test_classes=$TEST_CLASSES
EOF

cat "$IDENTITY"

gradle --stacktrace connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class="$TEST_CLASSES" \
  2>&1 | tee "$LOG"

cat <<EOF
X006_TARGET_DEVICE_RESULT result=PASS worker_head_sha=$HEAD_SHA manufacturer=$MANUFACTURER model=$MODEL api=$API
X006_TARGET_DEVICE_EVIDENCE identity=$IDENTITY log=$LOG reports=app/build/reports/androidTests/connected
EOF
