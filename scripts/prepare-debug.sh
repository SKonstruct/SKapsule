#!/usr/bin/env bash

# prepare-debug.sh
# Boots the pixel_9_pro emulator, deploys the debug build, launches the app,
# and configures JDWP port-forwarding for debugger attachment.

set -euo pipefail

# 1. Resolve Android SDK Tools
SDK_ROOT="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
ADB="$SDK_ROOT/platform-tools/adb"
EMULATOR="$SDK_ROOT/emulator/emulator"

if [[ ! -x "$ADB" ]]; then
    ADB="adb"
fi
if [[ ! -x "$EMULATOR" ]]; then
    EMULATOR="emulator"
fi

if [[ "${1:-}" == "--game" ]]; then
    echo "=== Preparing Game Process Debug Session ==="
    # Debuggable builds expose a JDWP socket on every process regardless of
    # wait-for-debugger state, so no arming call is needed here — just find the
    # already-running :game process and forward its port. (Do NOT call
    # `am set-debug-app`: it kills any already-running process of the package
    # to rearm the debug flag for the next launch, which would kill the very
    # :game process this is trying to attach to. Confirmed live: pidof went
    # from a real PID to empty immediately after that call.) Because nothing
    # pauses :game at boot, this attaches to it already running — fine for
    # breakpoints in gameplay code, but you'll miss anything that already ran
    # before you started this task.
    echo "Resolving game process (:game) PID..."
    PID=""
    for i in {1..30}; do
        PID=$("$ADB" shell pidof com.skarm.launcher:game 2>/dev/null || echo "")
        if [[ -n "$PID" ]]; then
            break
        fi
        echo "Waiting for game process to start (make sure you pressed Play in the launcher)..."
        sleep 1
    done

    if [[ -z "$PID" ]]; then
        echo "ERROR: Failed to retrieve game process PID." >&2
        exit 1
    fi

    echo "Game process PID is: $PID"
    echo "Setting up adb JDWP port forwarding (tcp:5006 -> jdwp:$PID)..."
    "$ADB" forward tcp:5006 jdwp:"$PID"
    echo "=== Game Debug Setup Completed Successfully ==="
    exit 0
fi

# Printed verbatim on purpose: this exact string is the "begins" pattern the
# built-in $tsc-watch background problemMatcher (tasks.json) scans for to mark
# this task as started. It has nothing to do with TypeScript.
echo "File change detected. Starting incremental compilation..."

echo "=== Preparing Android Debug Session ==="

# 2. Check and Launch Emulator
if ! "$ADB" devices | grep -q "emulator"; then
    echo "Starting Android Emulator: pixel_9_pro..."
    "$EMULATOR" -avd pixel_9_pro -netdelay none -netspeed full > /dev/null 2>&1 &
    
    echo "Waiting for emulator to connect to adb..."
    until "$ADB" devices | grep -q "emulator"; do
        sleep 1
    done
fi

echo "Waiting for emulator to finish booting..."
until "$ADB" shell getprop sys.boot_completed 2>/dev/null | grep -q "1"; do
    sleep 2
done
echo "Emulator is booted and ready."

# Defensive: clear any wait-for-debugger flag left over from an aborted
# "Debug Game Process" session, so this ordinary launch can't hang.
"$ADB" shell am clear-debug-app

# 3. Build & Install APK
echo "Building and installing debug APK..."
export JAVA_HOME="$(jenv prefix zulu64-25.0.3 2>/dev/null || jenv prefix 25 2>/dev/null || echo "")"
cd "$(dirname "$0")/../launcher"
./gradlew :app:installDebug
cd ..

# 4. Launch App in Debug-Wait Mode
echo "Launching LauncherActivity in debug-wait mode..."
"$ADB" shell am start -D -n com.skarm.launcher/com.skarm.launcher.LauncherActivity

# 5. Resolve PID and Setup JDWP Forwarding
echo "Resolving application PID..."
PID=""
for i in {1..30}; do
    PID=$("$ADB" shell pidof com.skarm.launcher 2>/dev/null || echo "")
    if [[ -n "$PID" ]]; then
        break
    fi
    sleep 0.5
done

if [[ -z "$PID" ]]; then
    echo "ERROR: Failed to retrieve app PID. Ensure the app has started." >&2
    exit 1
fi

echo "App PID is: $PID"
echo "Setting up adb JDWP port forwarding (tcp:5005 -> jdwp:$PID)..."
"$ADB" forward tcp:5005 jdwp:"$PID"

# Kill any existing background logcat streams (to avoid duplicates)
pkill -f "adb logcat" || true

# Clear the logcat buffer to start fresh for this session
"$ADB" logcat -c

echo "=== Android Debug Setup Completed Successfully ==="
echo "VS Code Java debugger can now attach to localhost:5005."
# Printed verbatim on purpose: this exact string is the "ends" pattern the
# $tsc-watch background problemMatcher waits for before letting the debugger
# attach step proceed.
echo "Compilation complete. Watching for file changes..."
sleep 1

# Stream logs for every process of the app (the launcher and :game share one
# UID). logcat has no --package flag on current platform-tools (confirmed
# absent on adb 37.0.0 - "Unknown option '*:I'" was actually --package itself
# being rejected, which then desynced the arg parser); --uid is the
# version-safe equivalent and, unlike --pid="$PID", also covers :game once it
# starts.
APP_UID=$("$ADB" shell pm list packages -U com.skarm.launcher 2>/dev/null | sed -n 's/.*uid://p' | tr -d '\r\n')
if [[ -n "$APP_UID" ]]; then
    exec "$ADB" logcat --uid="$APP_UID" '*:I'
else
    echo "WARNING: could not resolve app UID; falling back to launcher PID only (won't show :game logs)." >&2
    exec "$ADB" logcat --pid="$PID" '*:I'
fi
