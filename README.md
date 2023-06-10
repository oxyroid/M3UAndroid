# M3UAndroid

### Features

- [x] M3U and M3U8 files.
- [x] HTTPS and RTMP stream.
- [x] Android TV.
- [ ] Android Car.
- [ ] DLNA Protocol.
- [ ] Custom Script.
- [x] i18n.
- [ ] Desktop Platform.

### Android Development

M3U is an app that attempts to use the latest libraries and tools. As a summary:

- Entirely written in Kotlin.
- UI completely written in Jetpack Compose.
- Uses Kotlin Coroutines throughout.
- Uses many of the Architecture Components, including: Room, Lifecycle, Navigation.
- Uses [Koin](https://insert-koin.io) for dependency injection.

### (Migrating) Desktop Platform Development

> If you want to join the migrating work, just
> join the Telegram Channel [@m3u_android_chat](https://t.me/m3u_android_chat).

#### Completed Migrating

- Koin has been the dependency injection framework (migrated from Hilt).

#### Migrating Roadmap

- Migrate i18n kotlin framework from `context.getString` to [MoKo-Resource](https://github.com/icerockdev/moko-resources).
- Migrate ORM framework from `Jetpack Room` to [SQLDelight](https://cashapp.github.io/sqldelight/).
- Migrate Video Player from `ExoPlayer` to [VLCJ](https://github.com/caprica/vlcj), visit [official demo](https://github.com/JetBrains/compose-multiplatform/blob/master/experimental/components/VideoPlayer/library/src/desktopMain/kotlin/org/jetbrains/compose/videoplayer/DesktopVideoPlayer.kt).
- Add desktop Lifecycle ViewModel support just like `Jetpack ViewModel` in Android Platform.

### Community

M3U is my first elaborate Android project and is also my first cross-platform application project.

You can join the [Telegram Channel](https://t.me/m3u_android) for update information and **alpha &
beta packages**.

### About me

I am a Chinese university student who is about to graduate and eager to find a **job** in Android
Software Development,
If you are interested in offering me a job, please contact [@sortBy](https://t.me/sortBy).
