#!/usr/bin/env bash

# setup-mac.sh
# Automates the setup of build dependencies and Azul Zulu JDKs (8 and 25) 
# in a user-local, non-administrative (non-sudo) environment on macOS.

set -euo pipefail

echo "=== SKapsule macOS Dependency & Java Setup ==="
echo

# 1. Install System Dependencies via Homebrew
if command -v brew >/dev/null 2>&1; then
    echo "Installing system dependencies via Homebrew..."
    brew install ant maven ninja wget
    brew install ldid || true
else
    echo "WARNING: Homebrew not found. Please install Homebrew and ensure ant, maven, ninja are installed manually." >&2
fi
echo

# 2. Setup Local Java Directory
JVM_DIR="$HOME/Library/Java/JavaVirtualMachines"
mkdir -p "$JVM_DIR"

# 3. Detect Host Architecture
ARCH="$(uname -m)"
if [[ "$ARCH" == "arm64" ]]; then
    echo "Detected architecture: macOS Apple Silicon (arm64)"
    ZULU_ARCH="aarch64"
elif [[ "$ARCH" == "x86_64" ]]; then
    echo "Detected architecture: macOS Intel (x86_64)"
    ZULU_ARCH="x64"
else
    echo "ERROR: Unsupported architecture: $ARCH" >&2
    exit 1
fi
echo

# 4. Handle Azul Zulu JDK 8 Setup
ZULU8_DIR="$JVM_DIR/zulu-8.jdk"
if [[ -d "$ZULU8_DIR" && -f "$ZULU8_DIR/Contents/Home/jre/lib/rt.jar" ]]; then
    echo "Zulu JDK 8 is already installed locally at: $ZULU8_DIR"
else
    echo "Installing Azul Zulu JDK 8..."
    ZULU8_VERSION="zulu8.94.0.17-ca-jdk8.0.492"
    ZULU8_TAR="zulu8.tar.gz"
    ZULU8_URL="https://cdn.azul.com/zulu/bin/${ZULU8_VERSION}-macosx_${ZULU_ARCH}.tar.gz"

    rm -rf "$JVM_DIR/zulu8.tar.gz" "$JVM_DIR/${ZULU8_VERSION}-macosx_${ZULU_ARCH}"
    curl -L "$ZULU8_URL" -o "$JVM_DIR/$ZULU8_TAR"
    tar -xzf "$JVM_DIR/$ZULU8_TAR" -C "$JVM_DIR/"
    rm -rf "$ZULU8_DIR"
    mv "$JVM_DIR/${ZULU8_VERSION}-macosx_${ZULU_ARCH}" "$ZULU8_DIR"
    rm -f "$JVM_DIR/$ZULU8_TAR"
    echo "Installed Zulu JDK 8 successfully."
fi

# 5. Handle Azul Zulu JDK 25 Setup
ZULU25_DIR="$JVM_DIR/zulu-25.jdk"
if [[ -d "$ZULU25_DIR" && -f "$ZULU25_DIR/Contents/Home/bin/java" ]]; then
    echo "Zulu JDK 25 is already installed locally at: $ZULU25_DIR"
else
    echo "Installing Azul Zulu JDK 25..."
    ZULU25_VERSION="zulu25.34.17-ca-jdk25.0.3"
    ZULU25_TAR="zulu25.tar.gz"
    ZULU25_URL="https://cdn.azul.com/zulu/bin/${ZULU25_VERSION}-macosx_${ZULU_ARCH}.tar.gz"

    rm -rf "$JVM_DIR/zulu25.tar.gz" "$JVM_DIR/${ZULU25_VERSION}-macosx_${ZULU_ARCH}"
    curl -L "$ZULU25_URL" -o "$JVM_DIR/$ZULU25_TAR"
    tar -xzf "$JVM_DIR/$ZULU25_TAR" -C "$JVM_DIR/"
    rm -rf "$ZULU25_DIR"
    mv "$JVM_DIR/${ZULU25_VERSION}-macosx_${ZULU_ARCH}" "$ZULU25_DIR"
    rm -f "$JVM_DIR/$ZULU25_TAR"
    echo "Installed Zulu JDK 25 successfully."
fi
echo

# 6. Configure jenv
if command -v jenv >/dev/null 2>&1; then
    echo "Registering installed Java versions with jenv..."
    jenv add "$ZULU8_DIR/Contents/Home"
    jenv add "$ZULU25_DIR/Contents/Home"
    
    # Configure local workspace Java version
    echo "Setting local java version in workspace..."
    jenv local zulu64-25.0.3 || jenv local 25.0.3 || jenv local 25
    jenv rehash
else
    echo "WARNING: jenv is not installed or not in PATH." >&2
    echo "         Please configure your shell startup scripts to load jenv." >&2
fi
echo

echo "=== Setup Completed Successfully ==="
echo "Note: Re-run setup anytime if Java versions need to be restored or registered."
