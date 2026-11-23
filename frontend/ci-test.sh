#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FRONTEND_DIR="$SCRIPT_DIR"

MODE="all"
if [ $# -gt 0 ]; then
    case "$1" in
        all|purity|static|lint|test)
            MODE="$1"
            shift
            ;;
    esac
fi

run_purity_check() {
    echo "==> Checking KMP commonMain / commonTest for JVM or Android-only APIs..."
    local common_main_dir="$FRONTEND_DIR/composeApp/src/commonMain"
    local common_test_dir="$FRONTEND_DIR/composeApp/src/commonTest"

    if grep -rn -E "import (java\.|android\.)|System\.currentTimeMillis|String\.format" "$common_main_dir" "$common_test_dir"; then
        echo "ERROR: Forbidden JVM/Android-only APIs found in commonMain or commonTest!"
        exit 1
    fi
    echo "OK: commonMain and commonTest are pure KMP Kotlin."
}

setup_android_sdk() {
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
}

case "$MODE" in
    purity)
        run_purity_check
        ;;
    static)
        setup_android_sdk
        cd "$FRONTEND_DIR"
        echo "==> Running ktlintCheck & detekt (+ Compose Rules)..."
        ./gradlew :composeApp:ktlintCheck :composeApp:detekt --parallel --build-cache --no-daemon --stacktrace "$@"
        ;;
    lint)
        setup_android_sdk
        cd "$FRONTEND_DIR"
        echo "==> Running Android Lint (:composeApp:lintDebug)..."
        ./gradlew :composeApp:lintDebug --parallel --build-cache --no-daemon --stacktrace "$@"
        ;;
    test)
        setup_android_sdk
        cd "$FRONTEND_DIR"
        echo "==> Running Unit Tests (:composeApp:testDebugUnitTest)..."
        ./gradlew :composeApp:testDebugUnitTest --parallel --build-cache --no-daemon --stacktrace "$@"
        ;;
    all)
        run_purity_check
        setup_android_sdk
        cd "$FRONTEND_DIR"
        echo "==> Running ktlintCheck, detekt, lintDebug, and testDebugUnitTest..."
        ./gradlew \
            :composeApp:ktlintCheck \
            :composeApp:detekt \
            :composeApp:lintDebug \
            :composeApp:testDebugUnitTest \
            --parallel \
            --build-cache \
            --no-daemon \
            --stacktrace \
            "$@"
        ;;
esac

echo "==> Frontend CI ($MODE) passed!"
