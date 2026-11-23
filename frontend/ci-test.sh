#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FRONTEND_DIR="$SCRIPT_DIR"

# Auto-detect Android SDK if ANDROID_HOME is not already set
if [ -z "${ANDROID_HOME:-}" ]; then
    if [ -n "${ANDROID_SDK_ROOT:-}" ] && [ -d "${ANDROID_SDK_ROOT}" ]; then
        export ANDROID_HOME="$ANDROID_SDK_ROOT"
    elif [ -d "${HOME}/Android/Sdk" ]; then
        export ANDROID_HOME="${HOME}/Android/Sdk"
    elif [ -d "/usr/lib/android-sdk" ]; then
        export ANDROID_HOME="/usr/lib/android-sdk"
    fi
fi

if [ -n "${ANDROID_HOME:-}" ]; then
    export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$ANDROID_HOME}"
fi

echo "==> [1/2] Checking KMP commonMain / commonTest for JVM or Android-only APIs..."
COMMON_MAIN_DIR="$FRONTEND_DIR/composeApp/src/commonMain"
COMMON_TEST_DIR="$FRONTEND_DIR/composeApp/src/commonTest"

if grep -rn -E "import (java\.|android\.)|System\.currentTimeMillis|String\.format" "$COMMON_MAIN_DIR" "$COMMON_TEST_DIR"; then
    echo "ERROR: Forbidden JVM/Android-only APIs found in commonMain or commonTest!"
    exit 1
fi
echo "OK: commonMain and commonTest are pure KMP Kotlin."

echo "==> [2/2] Running Gradle Android/KMP unit tests (:composeApp:testDebugUnitTest)..."
cd "$FRONTEND_DIR"
./gradlew :composeApp:testDebugUnitTest --no-daemon --stacktrace "$@"

echo "==> Frontend CI tests passed!"
