#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p work
TASK_SIGNING_PATH="${CARDPOOL_KEYSTORE:-work/debug.keystore}"
if [ ! -f "$TASK_SIGNING_PATH" ]; then
 keytool -genkeypair -keystore "$TASK_SIGNING_PATH" -storepass android -keypass android -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 -dname 'CN=Android Debug,O=Android,C=US'
fi
