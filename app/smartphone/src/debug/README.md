# Local Emby debug account

The smartphone debug variant can import one Emby account through the normal provider subscription
path. Add all three values to the repository-root `local.properties`:

```properties
m3u.debug.emby.baseUrl=https://your-server.example
m3u.debug.emby.username=your-account
m3u.debug.emby.password=your-password
```

The equivalent environment variables are `M3U_DEBUG_EMBY_BASE_URL`,
`M3U_DEBUG_EMBY_USERNAME`, and `M3U_DEBUG_EMBY_PASSWORD`. Explicit Gradle properties take
precedence, followed by environment variables and then `local.properties`.

The values are compiled only into the local debug APK. Release variants contain neither the
fixture code nor these fields. A fresh debug app imports the account after the bundled playback
samples; later launches reuse the existing provider account.

## Crash-reporting probe

A controlled crash entry point exists only in the debug variant. After installation, run:

```shell
adb shell am start -n com.m3u.smartphone/.stability.DebugCrashTestActivity
```

Use it to verify report persistence, the isolated sender process, and removal of the probe secret
from exception messages.

To verify automatic HTTP delivery, start the repository mock receiver and reverse the device port
to the host:

```shell
./gradlew :testing:mock-server:startMockServer
./gradlew :app:smartphone:assembleDebug \
  -Pm3u.debug.crash.report.endpoint=http://127.0.0.1:8080/crash-reports
adb reverse tcp:8080 tcp:8080
adb install -r app/smartphone/build/outputs/apk/debug/smartphone-debug.apk
adb shell am start -n com.m3u.smartphone/.stability.DebugCrashTestActivity
curl http://127.0.0.1:8080/crash-reports/latest
```

`m3u.debug.crash.report.endpoint` affects debug only. HTTP is accepted only for localhost,
emulator-host, or private-network receivers; release endpoints always require HTTPS.
