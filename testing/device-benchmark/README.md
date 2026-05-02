# Device Benchmark

Host-side benchmark for the two-device flow:

1. Build and install the smartphone and TV debug APKs to their matching devices.
2. Start the local M3U mock server with fixture playlist/Xtream data.
3. Start the smartphone app and TV app at the same time.
4. Bridge the phone's localhost request to the TV app's subscription server through ADB.
5. Ask the smartphone app to send the test M3U subscription to the TV app.
6. Wait until the TV UI shows the subscribed playlist title.

The benchmark uses ADB from the host because Android Macrobenchmark runs on a single device and
cannot directly coordinate a phone plus a TV at the same time.

## Run

Start one phone/emulator and one TV/emulator, then run:

```bash
./gradlew :testing:device-benchmark:run
```

By default the benchmark auto-detects one non-TV Android device and one TV/Leanback device from
`adb devices -l`. It installs the latest debug APKs from the app modules, reverse-maps the mock
server to the TV at `http://127.0.0.1:8080`, forwards the TV subscription server on host port
`8989`, then exposes it to the phone at `http://127.0.0.1:8998`.

You can still override values when needed:

```bash
./gradlew :testing:device-benchmark:run --args="\
  --phone <phone-serial> \
  --tv <tv-serial> \
  --mock-url http://127.0.0.1:8080 \
  --playlist-title BenchmarkLive"
```

The output report is written to:

```text
testing/device-benchmark/build/reports/phone-tv-subscribe-m3u.json
```
