#!/bin/bash
set -e

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$PROJECT_DIR"

SDK_ROOT="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/opt/android-sdk}}"
BUILD_TOOLS="$SDK_ROOT/build-tools/36.0.0"
PLATFORM="$SDK_ROOT/platforms/android-36/android.jar"
OUTPUT_APK="$PROJECT_DIR/build/app-debug.apk"

echo "=== Building MotoWidgetShelf ==="
echo "Build tools: $BUILD_TOOLS"
echo "Platform: $PLATFORM"

# Clean build dir
rm -rf build
mkdir -p build/compiled_res build/gen build/classes build/dex

echo "1. Compiling resources with aapt2..."
"$BUILD_TOOLS/aapt2" compile --dir app/src/main/res -o build/compiled_res.zip

echo "2. Linking resources and generating R.java..."
"$BUILD_TOOLS/aapt2" link \
    -o build/app-unaligned.apk \
    -I "$PLATFORM" \
    --manifest app/src/main/AndroidManifest.xml \
    --java build/gen \
    build/compiled_res.zip \
    --auto-add-overlay

echo "3. Compiling Java sources with javac..."
javac -cp "$PLATFORM" -d build/classes -source 17 -target 17 $(find app/src/main/java build/gen -name "*.java")

echo "4. Converting bytecode to DEX with d8..."
"$BUILD_TOOLS/d8" --output build/dex --min-api 30 --lib "$PLATFORM" $(find build/classes -name "*.class")

echo "5. Adding DEX into APK package..."
(cd build/dex && zip -u ../app-unaligned.apk classes.dex)

echo "6. Checking debug keystore..."
if [ ! -f "debug.keystore" ]; then
    keytool -genkeypair -keystore debug.keystore -storepass android -keypass android -alias androiddebugkey -dname "CN=Android Debug,O=Android,C=US" -keyalg RSA -keysize 2048 -validity 10000
fi

echo "7. Signing APK with apksigner..."
"$BUILD_TOOLS/apksigner" sign \
    --ks debug.keystore \
    --ks-pass pass:android \
    --ks-key-alias androiddebugkey \
    --key-pass pass:android \
    --out "$OUTPUT_APK" \
    build/app-unaligned.apk

echo "8. Verifying APK..."
"$BUILD_TOOLS/apksigner" verify "$OUTPUT_APK"
echo "=== BUILD SUCCESSFUL ==="
echo "Output APK: $OUTPUT_APK"
