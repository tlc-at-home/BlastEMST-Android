#!/bin/bash
set -e # Exit immediately if a command exits with a non-zero status.

# The directory where this script is located
SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" &> /dev/null && pwd)

# The root directory of the Android project
ANDROID_PROJECT_DIR="$SCRIPT_DIR/.."

echo "--- Building Rust core for Android ---"

# MODIFIED COMMAND:
# We now explicitly list all four targets using the "-t" flag.
# This ensures we build for ARM phones and x86 emulators.
cargo ndk \
    -t armeabi-v7a \
    -t arm64-v8a \
    -t x86 \
    -t x86_64 \
    -o "$SCRIPT_DIR/jniLibs" \
    build --release

echo "--- Copying native libraries (.so files) to Android project ---"
# The final destination for the .so files
ANDROID_JNI_LIBS_DIR="$ANDROID_PROJECT_DIR/app/src/main/jniLibs"

echo "Cleaning up old libraries in the app directory..."
rm -rf "$ANDROID_JNI_LIBS_DIR"
mkdir -p "$ANDROID_JNI_LIBS_DIR"

echo "Copying newly built libraries..."
# Copy the contents of the Rust project's jniLibs into the Android app's jniLibs
cp -r "$SCRIPT_DIR/jniLibs/"* "$ANDROID_JNI_LIBS_DIR/"

echo "--- Rust build complete! ---"
echo "You can now build your app in Android Studio."
