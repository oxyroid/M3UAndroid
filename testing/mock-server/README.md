# M3U Mock Server

Local HTTP fixtures for manual testing M3U, HLS, and Xtream flows.

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

The default Xtream credentials are `m3u` / `m3u`.
