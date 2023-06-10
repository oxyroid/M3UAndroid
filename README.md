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

#### Completed

- Koin has been the dependency injection framework (migrated from Hilt).

#### Migrating

- Replace `context.getString` with i18n kotlin framework.
- Replace `Jetpack Room` by [SQLDelight](https://cashapp.github.io/sqldelight/).
- Replace `Jetpack ViewModel` with KM-MVVM framework.

### Community

M3U is my first elaborate Android project.

You can join the [Telegram Channel](https://t.me/m3u_android) for update information and **alpha &
beta packages**.

### About me

I am a Chinese university student who is about to graduate and eager to find a **job** in Android
Software Development,
If you are interested in offering me a job, please contact [@sortBy](https://t.me/sortBy).
