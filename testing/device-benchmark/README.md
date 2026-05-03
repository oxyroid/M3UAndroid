# Device Benchmark

Mobly benchmark for the phone-assisted TV subscription flow.

The test drives the real UI path:

1. Build and install the smartphone and TV debug APKs.
2. Start the local M3U mock server.
3. Launch both apps.
4. Read the pairing code from the TV UI.
5. Enable Remote Control on the phone.
6. Open the Remote Control FAB, enter the TV code, and connect.
7. Use the phone settings screen to subscribe for TV through `/playlists/subscribe`.
8. Verify the TV library shows the subscribed playlist.

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

Mobly logs are written under:

```text
testing/device-benchmark/build/mobly-results
```
