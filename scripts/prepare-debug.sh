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

echo "=== Android Debug Setup Completed Successfully ==="
echo "VS Code Java debugger can now attach to localhost:5005."
