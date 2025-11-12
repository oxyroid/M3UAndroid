#!/bin/bash
# Build script for M3U Android TV APK

echo "🎬 Building M3U Android TV APK..."
echo ""

# Clean previous builds
echo "📦 Cleaning previous builds..."
./gradlew clean

# Build the TV debug APK
echo "🔨 Building TV debug APK..."
./gradlew :app:tv:assembleDebug

# Check if build was successful
if [ -f "app/tv/build/outputs/apk/debug/tv-debug.apk" ]; then
    echo ""
    echo "✅ Build successful!"
    echo ""
    echo "📱 APK location:"
    echo "   app/tv/build/outputs/apk/debug/tv-debug.apk"
    echo ""
    echo "📥 To install on Android TV:"
    echo "   1. Connect via ADB: adb connect YOUR_TV_IP"
    echo "   2. Install: adb install app/tv/build/outputs/apk/debug/tv-debug.apk"
    echo ""
    echo "Or copy to USB drive and install via file manager on TV"
    echo ""
else
    echo ""
    echo "❌ Build failed!"
    echo "Check the error messages above"
    exit 1
fi
