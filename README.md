# M3UAndroid

<a href="https://t.me/m3u_android"><img src="https://img.shields.io/badge/Telegram-2CA5E0?style=flat&logo=telegram&logoColor=white"></a>

M3U is a FREE stream media player on android devices, which made of jetpack compose.
Android 8.0 and above supported.

### Device support

Most Android devices, including smartphones, TVs, and tablets.

### Screenshots

<div align="center">
<img src=".github/images/phone/deviceframes.png"/>
<img src=".github/images/tv/foryou.png" width="30%" />
<img src=".github/images/tv/playlist.png" width="30%" />
<img src=".github/images/tv/player.png" width="30%" />
</div>

### 📢 Translations Wanted 📢

Please submit a pull request if you want to help with translation.

Official:

- [English](i18n/src/main/res/values)
- [Simplified Chinese](i18n/src/main/res/values-zh-rCN)
- (You can also provide better translations for the above languages via pull requests)

From PRs:

- [Spanish](i18n/src/main/res/values-es-rES),
  thanks [@sguinetti](https://github.com/sguinetti/M3UAndroid)

### Features & Roadmap

- [x] Playlist Management.
- [x] Streaming media analysis capabilities.
- [x] Android Tablet adaptation.
- [x] Android TV adaptation.
- [ ] Android Desktop, Car adaptation.
- [ ] EPG support.
- [ ] Xtream support.
- [x] DLNA screencast.
- [ ] AirPlay screencast.
- [ ] Steam segment recording.
- [x] i18n.

### Android Development

M3U is an app that attempts to use the latest libraries and tools. As a summary:

- Entirely written in Kotlin.
- UI completely written in Jetpack Compose.
- Material3 design system.
- Uses Kotlin Coroutines throughout.
- Uses many of the Architecture Components, including: Room, Lifecycle, Navigation.
- Uses Hilt for dependency injection.
- Uses Lint Checks for code scanning.
- Uses KSP & KotlinPoet for Code Generating.
- FFmepg-kit & ExoPlayer.

### Installation

[<img src="https://github.com/realOxy/M3UAndroid/assets/5572928/c407b17c-f64f-4486-ade1-6048eb177e67"
alt="Get it on GitHub"
height="80">](https://github.com/realOxy/M3UAndroid/releases/latest)
[<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png"
alt="Get it on F-Droid"
height="80">](https://f-droid.org/packages/com.m3u.androidApp)
[<img src="https://github.com/realOxy/M3UAndroid/assets/5572928/4ba5a44a-c5e4-4634-a7aa-b8dda0992ba2"
alt="Get it on IzzyOnDroid"
height="80">](https://apt.izzysoft.de/fdroid/index/apk/com.m3u.androidApp)
> Get the SNAPSHOT package [here](https://nightly.link/realOxy/M3UAndroid/workflows/android/master/artifact.zip)

### Community

You can join the [Telegram Channel](https://t.me/m3u_android) for update information and **alpha &
beta packages**.

You can also join the [Telegram Group](https://t.me/m3u_android_chat) for discussing.

### Contributing

View this [file](CONTRIBUTING.md) to learn about how to contribute this repository.

Refer to the [file](RULES.md) to learn about the Code Specification of this repository.

### Star History

<a href="https://star-history.com/#realOxy/M3UAndroid&Date">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="https://api.star-history.com/svg?repos=realOxy/M3UAndroid&type=Date&theme=dark" />
    <source media="(prefers-color-scheme: light)" srcset="https://api.star-history.com/svg?repos=realOxy/M3UAndroid&type=Date" />
    <img alt="Star History Chart" src="https://api.star-history.com/svg?repos=realOxy/M3UAndroid&type=Date" />
  </picture>
</a>

### License

M3UAndroid is distributed under the terms of the Apache License (Version 2.0). See
the [license](LICENSE) for more information.
