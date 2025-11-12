# Building and Installing M3U Android

This guide covers how to build and install M3U Android on your devices.

## 📱 Two Apps Available

This project contains two separate applications:

### 1. **Smartphone App** (Netflix Redesign ✨)
- **Location**: `app/smartphone`
- **Features**: New Netflix-inspired UI with glassmorphism
- **Best for**: Phones, tablets, and can be sideloaded on Android TV
- **Controls**: Touch-optimized

### 2. **Android TV App** (Original TV UI)
- **Location**: `app/tv`
- **Features**: TV-optimized interface with D-pad navigation
- **Best for**: Android TV, Fire TV
- **Controls**: Remote control optimized

---

## 🔨 Building the APKs

### Prerequisites
- JDK 17 or higher
- Android SDK
- Git

### Option 1: Using Build Scripts (Easiest)

**For Android TV:**
```bash
./build-tv-apk.sh
```

**For Smartphone (with Netflix redesign):**
```bash
./build-smartphone-apk.sh
```

### Option 2: Using Gradle Directly

**For Android TV:**
```bash
./gradlew :app:tv:assembleDebug
# Output: app/tv/build/outputs/apk/debug/tv-debug.apk
```

**For Smartphone:**
```bash
./gradlew :app:smartphone:assembleDebug
# Output: app/smartphone/build/outputs/apk/debug/smartphone-debug.apk
```

**For Release builds (requires signing):**
```bash
./gradlew :app:tv:assembleRelease
# or
./gradlew :app:smartphone:assembleRelease
```

---

## 📥 Installing on Android TV

### Method 1: ADB (Recommended)

1. **Enable Developer Options on your TV:**
   - Go to Settings → About
   - Click on "Build" 7 times
   - Go back to Settings → Developer Options
   - Enable "USB Debugging" and "Install via USB"

2. **Find your TV's IP address:**
   - Settings → Network → Network Status → IP Address

3. **Connect from your computer:**
```bash
# Connect to TV
adb connect YOUR_TV_IP_ADDRESS

# Verify connection
adb devices

# Install the APK
adb install app/tv/build/outputs/apk/debug/tv-debug.apk

# Or install smartphone version
adb install app/smartphone/build/outputs/apk/debug/smartphone-debug.apk
```

### Method 2: USB Drive

1. Copy the APK to a USB drive
2. Plug the USB drive into your Android TV
3. Install a file manager app (like "X-plore File Manager" or "File Commander")
4. Navigate to the USB drive and tap the APK to install

### Method 3: Wireless Transfer

Use apps like:
- **Send Files to TV** (available on Play Store)
- **X-plore File Manager** (with network sharing)
- **ES File Explorer**

---

## 🎨 Which Version Should I Install on Android TV?

### Install **TV App** if:
- ✅ You want the native TV experience
- ✅ You primarily use a remote control
- ✅ You want better performance on TV
- ❌ But note: It doesn't have the Netflix redesign yet

### Install **Smartphone App** if:
- ✅ You want to see the new Netflix-inspired design
- ✅ You have a mouse/touchpad for your TV
- ✅ You want to test the new UI
- ❌ But note: Not optimized for remote control navigation

### Pro Tip: Install Both!
You can have both apps installed simultaneously - they have different package names:
- TV app: `com.m3u.androidApp`
- Smartphone app: `com.m3u.androidApp.smartphone`

---

## 🔍 Verifying Installation

After installation:
```bash
# List installed packages
adb shell pm list packages | grep m3u

# Check app info
adb shell dumpsys package com.m3u.androidApp
```

---

## 🐛 Troubleshooting

### "Installation failed"
- Make sure "Install unknown apps" is enabled for your file manager
- Try uninstalling the old version first: `adb uninstall com.m3u.androidApp`

### "Device not found"
- Check if ADB is enabled on TV
- Verify IP address is correct
- Make sure TV and computer are on the same network

### "Build failed"
- Run `./gradlew clean`
- Check you have JDK 17+ installed: `java -version`
- Make sure Android SDK is properly configured

### APK won't install via file manager
- Install "X-plore File Manager" from Play Store
- Or enable "Install unknown apps" for your current file manager

---

## 📦 Creating GitHub Release

To create release APKs like the original repository:

1. **Build release APKs:**
```bash
./gradlew :app:tv:assembleRelease
./gradlew :app:smartphone:assembleRelease
```

2. **Sign the APKs** (requires keystore):
```bash
# Generate keystore (first time only)
keytool -genkey -v -keystore my-release-key.jks -keyalg RSA -keysize 2048 -validity 10000 -alias my-key-alias

# Sign APK
jarsigner -verbose -sigalg SHA256withRSA -digestalg SHA-256 -keystore my-release-key.jks app/tv/build/outputs/apk/release/tv-release-unsigned.apk my-key-alias
```

3. **Create GitHub Release:**
   - Go to GitHub → Releases → Create new release
   - Upload the signed APKs
   - Add release notes

---

## 🚀 Quick Start Commands

```bash
# Build both APKs
./gradlew :app:tv:assembleDebug :app:smartphone:assembleDebug

# Install TV app
adb install app/tv/build/outputs/apk/debug/tv-debug.apk

# Install smartphone app (Netflix redesign)
adb install app/smartphone/build/outputs/apk/debug/smartphone-debug.apk

# Uninstall if needed
adb uninstall com.m3u.androidApp        # TV app
adb uninstall com.m3u.androidApp.smartphone  # Smartphone app
```

---

## 📝 Notes

- **Netflix redesign** is currently only in the smartphone module
- To port the redesign to TV, additional work is needed for:
  - D-pad navigation
  - Focus management
  - 10-foot UI guidelines
  - Compose for TV components

---

## 🆘 Need Help?

Check the issues on GitHub or refer to the Android documentation:
- [Android TV Development](https://developer.android.com/tv)
- [ADB Documentation](https://developer.android.com/tools/adb)
