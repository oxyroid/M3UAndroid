# Benchmark test data

Static fixtures used by the device benchmark workflow. They are intentionally small so a
manual workflow run can load them through GitHub raw URLs, for example:

```text
https://raw.githubusercontent.com/oxyroid/M3UAndroid/master/testdata/playlists/live.m3u
https://raw.githubusercontent.com/oxyroid/M3UAndroid/master/testdata/playlists/mixed.m3u
https://raw.githubusercontent.com/oxyroid/M3UAndroid/master/testdata/epg/sample.xml
```

Xtream fixtures are stored as raw JSON snapshots for validation and documentation. The
interactive benchmark uses `testing:mock-server` for Xtream because the app calls
`/player_api.php` with different query-string `action` values during one subscription.
