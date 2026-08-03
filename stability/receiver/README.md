# Crash receiver

This is the production receiver for smartphone crash reports. It accepts the app's gzip JSON,
groups reports by stack fingerprint and version, and sends new-issue, cross-version recurrence,
and 15-minute spike alerts to `crash@oxyroid.com`.

## Run locally

```shell
./gradlew :stability:receiver:installDist
M3U_CRASH_ALERT_MODE=stdout \
  stability/receiver/build/install/receiver/bin/receiver
```

Local mode prints alerts instead of sending mail. State defaults to
`build/crash-receiver/state.json`.

## Production configuration

```shell
M3U_CRASH_HOST=127.0.0.1
M3U_CRASH_PORT=8080
M3U_CRASH_STATE_FILE=/var/lib/m3u-crash-receiver/state.json
M3U_CRASH_ADMIN_TOKEN=<long random token>
M3U_CRASH_ALERT_MODE=smtp
M3U_CRASH_SMTP_HOST=<SMTP host>
M3U_CRASH_SMTP_PORT=587
M3U_CRASH_SMTP_USERNAME=<SMTP username>
M3U_CRASH_SMTP_PASSWORD=<SMTP password>
M3U_CRASH_SMTP_FROM=<authorized sender address>
```

SMTP always requires STARTTLS and verifies the server certificate. Keep the password only in the
server environment file. Terminate HTTPS at a reverse proxy and forward public `/reports` traffic
to this service. Never put the admin token or SMTP password in the APK.

A systemd example is available at `deploy/m3u-crash-receiver.service`. Adjust its install path,
service user, and environment-file path for the target server.

## Endpoints

- `POST /reports`: automatic app ingestion.
- `GET /health`: public liveness check; returns only `ok`.
- `GET /internal/status`: requires `Authorization: Bearer <admin token>` and returns issue count,
  pending alert count, and latest delivery status. Missing configuration or invalid credentials
  return 404.
- `POST /internal/test-alert`: uses the same admin authentication and durably queues a test email
  with a unique probe ID. It returns 503 when the alert queue is full.

## Verify email delivery

After deployment, call the internal endpoint on the server itself. `/internal/*` does not need to
be exposed publicly:

```shell
curl --fail-with-body --request POST \
  --header "Authorization: Bearer <admin token>" \
  http://127.0.0.1:8080/internal/test-alert
```

A successful request returns `202` and a UUID. Verify that `crash@oxyroid.com` receives a message
whose subject is `[M3U crash][test] <first 8 UUID characters>` and whose body contains the exact
response as `probe_id`. Then inspect `pendingAlertCount`, `lastAlertDeliveredAtEpochMillis`, and
`lastAlertFailureType` through `/internal/status`. This does not fabricate a crash or alter issue
aggregation counts.

The receiver atomically persists an accepted report before returning `202`. Mail delivery runs in
the background, with a durable queue and backoff after failures. A `REPORT_ID` counts only once in
30 days. Reports expire after 30 days. The service does not store IP addresses, accounts, provider
URLs, tokens, logs, or exception messages.

The receiver is a single-instance, file-backed service sized for the current low report volume. Do
not let multiple instances write the same state file. The pending queue is capped at 1,000 alerts;
when full it favors new issues and cross-version recurrences, increments `droppedAlertCount`, and
exposes that value through the admin status. Operations should alert whenever it is non-zero.

Production also needs an independent external monitor polling `/health`; a process cannot report
its own outage after it has stopped.
