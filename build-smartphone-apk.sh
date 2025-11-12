#!/bin/bash
# Build script for M3U Android Smartphone APK (with Netflix redesign)

echo "📱 Building M3U Android Smartphone APK (Netflix Redesign)..."
echo ""

# Clean previous builds
echo "📦 Cleaning previous builds..."
./gradlew clean

# Build the smartphone debug APK
echo "🔨 Building smartphone debug APK..."
./gradlew :app:smartphone:assembleDebug

# Check if build was successful
if [ -f "app/smartphone/build/outputs/apk/debug/smartphone-debug.apk" ]; then
    echo ""
    echo "✅ Build successful!"
    echo ""
    echo "📱 APK location:"
    echo "   app/smartphone/build/outputs/apk/debug/smartphone-debug.apk"
    echo ""
    echo "📥 To install on Android TV (sideload):"
    echo "   1. Connect via ADB: adb connect YOUR_TV_IP"
    echo "   2. Install: adb install app/smartphone/build/outputs/apk/debug/smartphone-debug.apk"
    echo ""
    echo "⚠️  Note: This is the mobile app with Netflix redesign."
    echo "   It will work on Android TV but isn't optimized for TV remote."
    echo ""
else
    echo ""
    echo "❌ Build failed!"
    echo "Check the error messages above"
    exit 1
fi
