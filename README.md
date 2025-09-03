# M3UAndroid

<div align="center">

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="https://socialify.git.ci/oxyroid/M3UAndroid/image?font=Raleway&forks=1&issues=1&logo=https%3A%2F%2Fraw.githubusercontent.com%2Foxyroid%2FM3UAndroid%2Fmaster%2Fapp%2Fsmartphone%2Ficon.png&name=1&pattern=Plus&pulls=1&stargazers=1&theme=Dark" />
  <source media="(prefers-color-scheme: light)" srcset="https://socialify.git.ci/oxyroid/M3UAndroid/image?font=Raleway&forks=1&issues=1&logo=https%3A%2F%2Fraw.githubusercontent.com%2Foxyroid%2FM3UAndroid%2Fmaster%2Fapp%2Fsmartphone%2Ficon.png&name=1&pattern=Plus&pulls=1&stargazers=1&theme=Light" />
  <img alt="M3UAndroid" src="https://socialify.git.ci/oxyroid/M3UAndroid/image?font=Raleway&forks=1&issues=1&logo=https%3A%2F%2Fraw.githubusercontent.com%2Foxyroid%2FM3UAndroid%2Fmaster%2Fapp%2Fsmartphone%2Ficon.png&name=1&pattern=Plus&pulls=1&stargazers=1" width="640" height="320" />
</picture>

<p align="center">
  <strong>A modern, feature-rich IPTV streaming player for Android devices</strong>
</p>

[![Telegram Channel](https://img.shields.io/badge/Telegram-Channel-2CA5E0?style=flat&logo=telegram)](https://t.me/m3u_android)
[![Telegram Discussion](https://img.shields.io/badge/Telegram-Discussion-2CA5E0?style=flat&logo=telegram)](https://t.me/m3u_android_chat)
![GitHub release](https://img.shields.io/github/v/release/oxyroid/M3UAndroid?color=blue&logo=github)
![Android](https://img.shields.io/badge/Android-8.0%2B-brightgreen?logo=android)
![License](https://img.shields.io/badge/License-GPL%20v3-blue.svg)
![Kotlin](https://img.shields.io/badge/100%25-Kotlin-7F52FF?logo=kotlin)

</div>

## 📖 About

**M3UAndroid** is a powerful, modern IPTV streaming application designed for Android devices. Built with cutting-edge Android development practices, it provides a seamless viewing experience across phones, tablets, and Android TV devices. The app leverages Jetpack Compose for a beautiful, adaptive UI and offers comprehensive support for M3U playlists and IPTV streaming.

## ✨ Features

### 🎯 Core Functionality
- **📺 Multi-Device Support** - Adaptive UI optimized for mobile phones, tablets, and Android TV
- **📋 M3U Playlist Support** - Full compatibility with M3U/M3U8 playlist formats
- **🌐 Xtream Codes API** - Native support for Xtream Codes protocol
- **📥 Playlist Management** - Import, organize, and manage your IPTV sources
- **🔍 Smart Stream Analysis** - Intelligent stream detection and optimization

### 🎬 Viewing Experience
- **🖼️ Picture-in-Picture** - Seamless PiP mode for multitasking
- **🎭 DLNA Casting** - Cast content to compatible devices on your network
- **📱 App Widgets** - Quick access to favorites and recent channels
- **🔄 Auto-Resume** - Continue watching from where you left off

### 🛠️ Technical Excellence
- **🚀 Lightweight & Fast** - Optimized performance with minimal resource usage
- **🚫 Ad-Free Experience** - Clean interface without advertisements
- **🇺🇳 Multi-Language** - Comprehensive internationalization support
- **🔒 Privacy-First** - No data collection or tracking

## 📸 Screenshots

<div align="center">

### Mobile Experience
<img src=".github/images/phone/deviceframes.png" width="400" alt="Mobile UI Screenshots">

### Android TV Experience
<table>
  <tr>
    <td><img src=".github/images/tv/playlist.png" width="300" alt="TV Playlist View"></td>
    <td><img src=".github/images/tv/foryou.png" width="300" alt="TV For You Page"></td>
  </tr>
  <tr>
    <td colspan="2"><img src=".github/images/tv/player.png" width="600" alt="TV Player Interface"></td>
  </tr>
</table>

</div>

> **Note**: The TV UI is scheduled for a redesign in future releases to further enhance the viewing experience.

## 📱 System Requirements

- **Android Version**: 8.0 (API level 26) or higher
- **RAM**: 2GB minimum, 4GB recommended
- **Storage**: 100MB for app installation
- **Network**: Stable internet connection for streaming
- **Permissions**: Internet access, network state, wake lock

## ⬇️ Download

<div align="center">

### Official Releases

[![Telegram Channel](https://img.shields.io/badge/📢_Telegram-Channel-2CA5E0?style=for-the-badge&logo=telegram)](https://t.me/m3u_android)
[![GitHub Release](https://img.shields.io/badge/📦_Download-GitHub_Release-181717?style=for-the-badge&logo=github)](https://github.com/oxyroid/M3UAndroid/releases/latest)

### Alternative Sources

[![F-Droid](https://img.shields.io/badge/📱_F--Droid-Official_Repository-1976D2?style=for-the-badge&logo=android)](https://f-droid.org/packages/com.m3u.androidApp)
[![IzzyOnDroid](https://img.shields.io/badge/🔧_IzzyOnDroid-F--Droid_Repository-8A4182?style=for-the-badge)](https://apt.izzysoft.de/fdroid/index/apk/com.m3u.androidApp)

### Development Builds

🚧 **Beta/Nightly Builds**: [Development Artifacts](https://nightly.link/oxyroid/M3UAndroid/workflows/android/master/artifact.zip)

*Note: Development builds may contain experimental features and should be used for testing purposes only.*

</div>

## 🏗️ Architecture & Tech Stack

M3UAndroid is built with modern Android development practices and follows clean architecture principles.

### 🧱 Core Technologies
- **🎨 UI Framework**: [Jetpack Compose](https://developer.android.com/jetpack/compose) - Modern declarative UI toolkit
- **🏛️ Architecture**: MVVM (Model-View-ViewModel) with Clean Architecture
- **⚡ Concurrency**: [Kotlin Coroutines](https://kotlinlang.org/docs/coroutines-overview.html) & [Flow](https://kotlinlang.org/docs/flow.html)
- **🗃️ Database**: [Room](https://developer.android.com/training/data-storage/room) - SQLite abstraction layer
- **💉 Dependency Injection**: [Hilt](https://dagger.dev/hilt/) - Compile-time DI framework

### 📦 Modular Design
- **📱 Multi-Target**: Separate modules for phone, tablet, and Android TV
- **🧩 Feature Modules**: Business logic organized into independent feature modules
- **🛠️ Core Modules**: Shared utilities, data access, and common components
- **🌍 Localization**: Dedicated i18n module for multi-language support

### 🎥 Media & Streaming
- **🎬 Player Engine**: [ExoPlayer](https://exoplayer.dev/) with [FFmpeg](https://ffmpeg.org/) integration
- **📡 Networking**: [OkHttp](https://square.github.io/okhttp/) & [Retrofit](https://square.github.io/retrofit/) for API communication
- **🔄 Serialization**: [Kotlinx Serialization](https://github.com/Kotlin/kotlinx.serialization) for data parsing

### 🔧 Development Tools
- **📊 Performance**: [Baseline Profiles](https://developer.android.com/topic/performance/baselineprofiles) for optimized app startup
- **🎯 Code Quality**: Static analysis with custom lint rules
- **🧪 Testing**: Unit tests with [JUnit](https://junit.org/) and UI tests with [Compose Testing](https://developer.android.com/jetpack/compose/testing)

## 🌍 Internationalization

We actively support multiple languages and welcome community translations!

### Currently Supported Languages

| Language | Status | Maintainer |
|----------|---------|------------|
| 🇬🇧 **English** | Complete | Core Team |
| 🇨🇳 **Simplified Chinese** | Complete | Core Team |
| 🇪🇸 **Spanish** | Community | [@sguinetti](https://github.com/sguinetti) |
| 🇷🇴 **Romanian** | Community | [@iboboc](https://github.com/iboboc) |
| 🇧🇷 **Portuguese (Brazil)** | Community | [@Suburbanno](https://github.com/Suburbanno) |

### 🤝 Help Translate

Want to add your language? Translations are managed in the [`i18n` module](i18n/src/main/res/). Follow these steps:

1. **Fork** the repository
2. **Create** a new values folder: `values-{language}-r{COUNTRY}`
3. **Translate** the strings in [`values/strings.xml`](i18n/src/main/res/values/strings.xml)
4. **Submit** a pull request with your translations

**Translation Guidelines**:
- Maintain context and meaning over literal translation
- Keep UI text concise for mobile interfaces
- Test translations on different screen sizes
- Follow Android's [localization best practices](https://developer.android.com/guide/topics/resources/localization)

## 🛠️ Development Setup

### Prerequisites
- **Android Studio**: Hedgehog | 2023.1.1 or newer
- **JDK**: 17 or higher
- **Android SDK**: API level 34 (compile) / API level 26+ (minimum)
- **Git**: For version control

### Getting Started

1. **Clone the repository**
   ```bash
   git clone https://github.com/oxyroid/M3UAndroid.git
   cd M3UAndroid
   ```

2. **Open in Android Studio**
   - Launch Android Studio
   - Select "Open an Existing Project"
   - Navigate to the cloned directory

3. **Sync and build**
   ```bash
   ./gradlew build
   ```

4. **Run the app**
   - Select your target device (phone/tablet/TV)
   - Click Run or use `./gradlew installDebug`

### 🏃‍♂️ Available Build Variants
- **smartphone**: Mobile phone and tablet optimized build
- **tv**: Android TV and leanback interface build
- **extension**: Plugin system for additional functionality

### 🧪 Testing
```bash
# Run unit tests
./gradlew test

# Run Android instrumentation tests
./gradlew connectedAndroidTest

# Generate test coverage report
./gradlew koverHtmlReport
```

## 🤝 Contributing

We welcome contributions from the community! Here's how you can help:

### 🐛 Bug Reports & Feature Requests
- **Search existing issues** before creating new ones
- **Use issue templates** provided in the repository
- **Provide detailed information** including device, Android version, and reproduction steps

### 💻 Code Contributions
1. **Fork the repository** and create a feature branch
2. **Follow the coding standards** outlined in [`RULES.md`](RULES.md)
3. **Write tests** for new functionality
4. **Submit a pull request** with a clear description

For detailed development rules, please read our [`RULES.md`](RULES.md) file.

### 🎯 Areas We Need Help With
- 🌐 **Translations** - Add support for new languages
- 🎨 **UI/UX** - Improve user interface and experience
- 📱 **Android TV** - Enhance TV-specific features
- 🧪 **Testing** - Increase test coverage
- 📚 **Documentation** - Improve code documentation

## 🔧 FAQ & Troubleshooting

### Common Issues

**Q: DLNA casting is not working**
- Ensure both devices are on the same Wi-Fi network
- Check if your target device supports DLNA/UPnP
- Restart the app and try discovering devices again

### 🆘 Getting Help

- 💬 **Community Support**: Join our [Telegram Discussion Group](https://t.me/m3u_android_chat)
- 📢 **Updates**: Follow the [Telegram Channel](https://t.me/m3u_android) for announcements
- 🐛 **Bug Reports**: Create an [issue on GitHub](https://github.com/oxyroid/M3UAndroid/issues)

## 📈 Project Statistics

<div align="center">

<a href="https://star-history.com/#oxyroid/M3UAndroid&Date">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="https://api.star-history.com/svg?repos=oxyroid/M3UAndroid&type=Date&theme=dark" />
    <source media="(prefers-color-scheme: light)" srcset="https://api.star-history.com/svg?repos=oxyroid/M3UAndroid&type=Date" />
    <img alt="Star History Chart" src="https://api.star-history.com/svg?repos=oxyroid/M3UAndroid&type=Date" />
  </picture>
</a>

</div>

## 🙏 Acknowledgments

- **ExoPlayer Team** - For the powerful media playback engine
- **Jetpack Compose Team** - For the modern UI toolkit
- **Community Contributors** - For translations and bug reports
- **IPTV Community** - For feedback and feature suggestions

## 📄 License

```
Copyright (C) 2024 M3UAndroid Contributors

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
GNU General Public License for more details.
```

**Full License**: [GPL-3.0](LICENSE)

---

<div align="center">

**Made with ❤️ by the M3UAndroid community**

[![Built with Kotlin](https://img.shields.io/badge/Built%20with-Kotlin-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Powered by Compose](https://img.shields.io/badge/Powered%20by-Jetpack%20Compose-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)

</div>
