# Smartphone crash reporting

## Current coverage

- Captures uncaught JVM/Kotlin exceptions in the smartphone process.
- On Android 11+, reports ANR and native process exits on the next launch without reading system traces.
- Sends automatically when an endpoint is configured; otherwise keeps the notification-and-email fallback.
- Sends from the isolated `:acra` process without starting WorkManager, providers, or extensions again.
- Limits identical stacks to 3 reports per 7 days, all reports to 25 per 7 days, and retained failures to 5.

Android 10 and below cannot backfill ANR/native exits, and no backfill is possible until the user launches again. This pipeline alone is therefore not a complete crash-free metric.

## Build configuration

Configure the receiver with either input:

```properties
m3u.crash.report.endpoint=https://crash.example.com/reports
```

```shell
export M3U_CRASH_REPORT_ENDPOINT=https://crash.example.com/reports
```

The value must be an absolute HTTPS URL without credentials, query, or fragment. Never compile a receiver token or Basic Auth password into the APK.

An APK built without the endpoint does not upload automatically. It can only open the mail client
after the user approves the crash notification, then let the user send the report to
`crash@oxyroid.com`. This is a manual fallback, not production monitoring.

Production uses the repository's `:stability:receiver`: the app still uploads over HTTPS, then the
receiver groups and sanitizes reports again before sending new-issue, cross-version recurrence,
and spike alerts to `crash@oxyroid.com` over SMTP. SMTP credentials stay on the server and must
never be compiled into the APK. See `stability/receiver/README.md` for deployment inputs.

## Receiver contract

The client sends a gzip-compressed ACRA JSON body using `POST`:

```http
Content-Type: application/json
Content-Encoding: gzip
X-M3U-Report-Schema: 1
```

- `2xx`: accepted.
- `408` or `5xx`: retry later.
- Other `4xx`: discard the report.

Treat requests as unauthenticated. Limit body size and request rate, then filter by `PACKAGE_NAME`, version, and schema.

## Data boundary

Reports contain app version, device model, Android version, memory/display configuration, crash times, thread details, exception types, stack frames, and a small screen context.

They explicitly exclude:

- BuildConfig, logs, SharedPreferences, and file paths;
- installation/device IDs, IP addresses, email, and user comments;
- playlist/provider names, service addresses, accounts, tokens, credential handles, and extension package names;
- exception messages. Only exception types and stack frames are retained.

Delete raw reports after 30 days, restrict access to maintainers, and audit exports and deletions.

## Release gate

1. Inject the endpoint into the release build.
2. Run the debug probe and confirm the receiver does not contain `must-not-be-stored`.
3. Archive `mapping.txt` for the same `versionCode`; R8 traces are otherwise not actionable.
4. Group on `STACK_TRACE_HASH + APP_VERSION_CODE`.
5. Alert immediately on a new crash, on a single-issue spike within 15 minutes, and when receiver health checks fail.
6. Roll out gradually and confirm normal delivery, retry, and rate-limit volumes.

See `app/smartphone/src/debug/README.md` for the debug probe command.

The repository mock receiver validates gzip, schema, package name, payload limits, and forbidden
fields. Use it to test immediate delivery and retry after failure; it is not a production receiver.

The production receiver exposes `/health` for liveness and `/ready` for mail-delivery readiness,
but an external monitor with an independent notification channel must detect failures. Neither the
receiver nor the same SMTP path can reliably monitor itself.

References: [ACRA senders](https://www.acra.ch/docs/Senders), [ACRA advanced usage](https://www.acra.ch/docs/AdvancedUsage).
