# Device Benchmark

Mobly benchmarks for the app's primary feature paths. The default suite is
`mobly/full_feature_benchmark_test.py`; the original phone-assisted TV subscription benchmark remains
available as `mobly/remote_control_subscribe_test.py`.

## Feature paths covered

| Feature | App path | Benchmark coverage |
| --- | --- | --- |
| M3U subscription | Settings → Playlist Management → M3U | Subscribes to `testdata/playlists/live.m3u` through a GitHub raw URL. |
| EPG subscription | Settings → Playlist Management → EPG | Subscribes to `testdata/epg/sample.xml` through a GitHub raw URL. |
| Xtream subscription | Settings → Playlist Management → Xtream | Subscribes against `testing:mock-server` so all `player_api.php?action=...` calls are deterministic. |
| Library / For You | Bottom navigation → For You | Verifies subscribed playlists appear after subscription. |
| Playlist browsing | For You → playlist detail | Opens the subscribed Xtream playlist and waits for mock channels. |
| Playback entry | Playlist detail → channel | Opens the player path for a mock channel. |
| Favorite, Extension, Settings | Bottom navigation destinations | Measures route load for the main non-playback destinations. |
| Remote Control / TV subscribe | Phone Settings → Optional Features → Remote Control; TV Library | Pairs phone and TV, sends `/playlists/subscribe`, and verifies TV library update when a TV device is configured. |

Benchmark timings are logged as `BENCHMARK feature=... duration_ms=...` and written to
`testing/device-benchmark/build/mobly-results/feature-benchmark-metrics.json`.

## Requirements

- Python 3.11+ with Mobly installed from `requirements.txt`.
- One phone device and one Android TV/Leanback device visible in `adb devices -l`.
- Real devices must be able to discover and reach each other on the same network.

The default local emulator config uses a debug-only direct endpoint override because emulator mDNS
does not reliably deliver Android NSD services between separate emulator instances. This only skips
NSD discovery; the benchmark still connects to the TV app's `/say_hello` server, sends the
subscription through `/playlists/subscribe`, and verifies the TV UI.

## Run

The default config targets the local emulator serials used by this workspace:

```bash
./gradlew :testing:device-benchmark:run --no-configuration-cache
```

For real devices, remove `direct_tv_host` and `direct_tv_port` from the Mobly config so the app uses
normal NSD discovery.

To use a different testbed, copy `mobly_config.yml` and pass it with:

```bash
./gradlew :testing:device-benchmark:run \
  -PmoblyConfig=/path/to/mobly_config.yml \
  --no-configuration-cache
```

To run only the original remote-control benchmark:

```bash
./gradlew :testing:device-benchmark:run \
  -PmoblyBenchmarkScript=testing/device-benchmark/mobly/remote_control_subscribe_test.py \
  --no-configuration-cache
```

## GitHub workflow

`.github/workflows/device-benchmarks.yml` is manual-only (`workflow_dispatch`). By default it validates
the GitHub raw testdata links, compiles the benchmark support modules, and checks Python syntax. Set
`run_device_benchmark=true` when a runner has the required emulator/device setup and should execute the
full Mobly suite.

Mobly logs are written under:

```text
testing/device-benchmark/build/mobly-results
```
