# M3U Mock Server

Local HTTP fixtures for M3U, HLS, Xtream, Emby-compatible, and external reference-provider flows.

## Run

```bash
./gradlew :testing:mock-server:run
```

The server listens on `0.0.0.0:8080` by default. Override it with:

```bash
./gradlew :testing:mock-server:run --args="--host 0.0.0.0 --port 8090"
```

Android emulator URLs use `10.0.2.2`:

- M3U playlist: `http://10.0.2.2:8080/playlist/live.m3u`
- HLS manifest: `http://10.0.2.2:8080/hls/news/index.m3u8`
- Xtream server: `http://10.0.2.2:8080/player_api.php?username=m3u&password=m3u`
- Emby-compatible server: `http://10.0.2.2:8080`
- Jellyfin server with strict modern authorization: `http://10.0.2.2:8080/jellyfin`
- Reference provider server: `http://10.0.2.2:8080`

For a physical phone or TV, replace `10.0.2.2` with the host machine's LAN IP.

## App Test Integration

The smartphone and TV `connected...AndroidTest` tasks start this server automatically before
instrumentation runs and stop it afterwards.

```bash
./gradlew :app:smartphone:connectedDebugAndroidTest
./gradlew :app:tv:connectedDebugAndroidTest
```

Tests receive the server URL as the `m3uMockServerUrl` instrumentation argument. The default is
`http://10.0.2.2:8080`, which works for the Android emulator. Override it when running against a
physical device:

```bash
./gradlew :app:smartphone:connectedDebugAndroidTest \
  -Pm3uMockServerUrl=http://192.168.1.10:8080
```

## Useful Endpoints

- `/` returns a compact endpoint index.
- `/health` returns `ok`.
- `/playlist/live.m3u` returns live M3U entries.
- `/playlist/mixed.m3u` returns live, VOD, and series-like M3U entries.
- `/hls/{channel}/index.m3u8` returns a small media playlist.
- `/hls/{channel}/segment-{number}.ts` returns deterministic placeholder TS bytes.
- `/player_api.php?username=m3u&password=m3u` returns Xtream account/server info.
- `/player_api.php?username=m3u&password=m3u&action=get_live_categories`
- `/player_api.php?username=m3u&password=m3u&action=get_live_streams`
- `/player_api.php?username=m3u&password=m3u&action=get_vod_categories`
- `/player_api.php?username=m3u&password=m3u&action=get_vod_streams`
- `/player_api.php?username=m3u&password=m3u&action=get_series_categories`
- `/player_api.php?username=m3u&password=m3u&action=get_series`
- `/player_api.php?username=m3u&password=m3u&action=get_series_info&series_id=3001`
- `/System/Info/Public` returns Emby-compatible server identity.
- `/Users/AuthenticateByName` accepts the default credentials.
- `/LiveTv/Channels` returns two live channels with token authentication.
- `/Items/{item}/PlaybackInfo` opens a deterministic playback session.
- `/emby-stream/{item}/index.m3u8` requires the token and playback headers.
- `/Sessions/Playing/Stopped` and `/LiveStreams/Close` accept session cleanup.
- `/jellyfin/*` mirrors the same lifecycle while rejecting deprecated Jellyfin authorization headers.
- `POST /reference-provider/login` accepts `{"username":"m3u","password":"reference-password"}` and returns the reference access token plus projected account fields.
- `GET /reference-provider/channels` requires `X-Emby-Token: mock-reference-access-token` and returns two channels.
- `GET /reference-provider/playback/{item}` requires the token and opens a deterministic session.
- `POST /reference-provider/sessions/close` requires the token and closes that session.
- `GET /reference-provider/sessions/{playSessionId}` requires the token and returns `open` or `closed`.
- `/reference-provider/stream/{item}/index.m3u8` and its segments require the token.

The default Xtream and Emby-compatible credentials are `m3u` / `m3u`.
The reference provider uses `m3u` / `reference-password` and returns `mock-reference-access-token`.
