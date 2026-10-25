#!/usr/bin/env bash

# One command for the whole Android build: the native components, the staged launcher assets, then
# the APK. It is the "Build All" and "Assemble ... APK" tasks of .vscode/tasks.json in one script.
#
#   ./scripts/build.sh                     debug APK (native parts built only when missing)
#   ./scripts/build.sh release             release APK (signed when keystore.properties or SK_KEYSTORE_* is set)
#   ./scripts/build.sh --natives           rebuild every native component first (after a submodule bump)
#   ./scripts/build.sh --test --install    run the unit tests, build, install on the device and start it
#   ./scripts/build.sh --version 2.4.3     name the build (default: the latest git tag)

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$REPO_ROOT"

usage() {
    awk 'NR > 2 && /^#/ { sub(/^# ?/, ""); print; next } NR > 2 { exit }' "${BASH_SOURCE[0]}"
}

die() {
    echo "ERROR: $*" >&2
    exit 1
}

MODE=debug
FORCE_NATIVES=0
RUN_TESTS=0
INSTALL=0

while [[ $# -gt 0 ]]; do
    case "$1" in
        debug | release) MODE="$1" ;;
        --natives) FORCE_NATIVES=1 ;;
        --test) RUN_TESTS=1 ;;
        --install) INSTALL=1 ;;
        --version)
            [[ $# -ge 2 ]] || die "--version needs a value"
            export SK_VERSION_NAME="$2"
            shift
            ;;
        -h | --help)
            usage
            exit 0
            ;;
        *) die "unknown argument: $1 (try --help)" ;;
    esac
    shift
done

[[ "$INSTALL" == 0 || "$MODE" == debug ]] || die "--install installs the debug build only"

# Gradle 9 and the Java parts of the natives need JDK 25; LWJGL's multi-release layer needs JDK 8.
# SK_JAVA_HOME / SK_JAVA8_HOME override the lookup; jenv, then the macOS java_home, are tried.
find_jdk() {
    local jenv_name="$1" mac_version="$2"
    jenv prefix "$jenv_name" 2> /dev/null && return
    /usr/libexec/java_home -v "$mac_version" 2> /dev/null && return
    return 1
}

JAVA_HOME="${SK_JAVA_HOME:-$(find_jdk 25 25 || true)}"
[[ -n "$JAVA_HOME" && -x "$JAVA_HOME/bin/java" ]] || die "JDK 25 not found: set SK_JAVA_HOME or add it to jenv"
export JAVA_HOME
export CACIO_JAVA_HOME="$JAVA_HOME"
export FRENCHPRESS_JAVA_HOME="$JAVA_HOME"
JAVA8_HOME="${SK_JAVA8_HOME:-$(find_jdk 1.8 1.8 || true)}"
export JAVA8_HOME

# --- native components ---------------------------------------------------------------------

# submodule | main output | script. A component is built when its output is missing or --natives is given.
# frenchpress is a few seconds of Maven, so it is always rebuilt: the submodule changes often.
NATIVES=(
    "gl4es|out/gl4es/jniLibs/arm64-v8a/libgl4es.so|build-gl4es-android.sh"
    "openal-soft|out/openal/jniLibs/arm64-v8a/libopenal.so|build-openal-android.sh"
    "lwjgl3|out/lwjgl3/lwjgl-3.4.1-android-natives-arm64.zip|build-lwjgl3-android.sh"
    "caciocavallo17|out/cacio/cacio-shared.jar|build-cacio-android.sh"
)

for entry in "${NATIVES[@]}"; do
    IFS='|' read -r name output script <<< "$entry"
    if [[ "$FORCE_NATIVES" == 1 || ! -f "$output" ]]; then
        [[ -n "$(ls -A "external/$name" 2> /dev/null)" ]] \
            || die "external/$name is empty: git submodule update --init --recursive"
        if [[ "$name" == lwjgl3 && -z "$JAVA8_HOME" ]]; then
            die "JDK 8 not found (LWJGL needs it): set SK_JAVA8_HOME or add 1.8 to jenv"
        fi
        echo "=== $name ==="
        "./scripts/$script"
    else
        echo "=== $name: up to date ($output) ==="
    fi
done

echo "=== frenchpress ==="
./scripts/build-frenchpress-android.sh

echo "=== staging launcher assets ==="
./scripts/stage-launcher-assets.sh

# --- APK ------------------------------------------------------------------------------------

cd launcher

if [[ "$RUN_TESTS" == 1 ]]; then
    echo "=== unit tests ==="
    ./gradlew :app:testDebugUnitTest
    # Separate invocation: the app and bootstrap test tasks do not validate together.
    ./gradlew :bootstrap:test
fi

if [[ "$MODE" == release ]]; then
    TASK=:app:assembleRelease
else
    TASK=:app:assembleDebug
fi
echo "=== $TASK ==="
./gradlew "$TASK"

APK_DIR="app/build/outputs/apk/$MODE"
echo
echo "Built:"
ls -lh "$APK_DIR"/*.apk
if [[ "$MODE" == release ]] && ls "$APK_DIR"/*unsigned*.apk > /dev/null 2>&1; then
    echo "NOTE: unsigned. Set keystore.properties (see keystore.properties.template) to sign it."
fi

if [[ "$INSTALL" == 1 ]]; then
    command -v adb > /dev/null 2>&1 || die "adb not found; install the platform-tools"
    echo "=== installing ==="
    ./gradlew :app:installDebug
    adb shell am start -n com.skarm.launcher/com.skarm.launcher.LauncherActivity
fi
