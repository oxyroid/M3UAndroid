# Extension API 1 golden wire fixtures

These files are the reviewable JSON shapes emitted by the Extension API 1 serializers.

## Fixture index

- `manifests/complete.json` covers every non-empty manifest section.
- `envelopes/` covers invocation and result envelopes. It includes the oldest defaulted
  invocation, a current invocation with settings, grants and broker scope, and an additive
  unknown-field example.
- `provider-validation-evidence/` covers both `trusted_direct` and `host_broker_receipt`.
- `broker/` covers both operations, both operation results, success and failure envelopes,
  every `BrokerValue` branch, and every `ResponseValueSource` branch.

The Hook fixture catalog currently retains these explicit API 1 schema versions:

| Hook | Schema |
|---|---:|
| `subscription.provider.discover` | 4 |
| `subscription.provider.validate` | 2 |
| `subscription.content.refresh` | 4 |
| `playback.source.resolve` | 4 |
| `playback.session.close` | 3 |
| `settings.schema.contribute` | 1 |
| `epg.content.refresh` | 4 |
| `metadata.channel.enrich` | 3 |
| `search.provider.query` | 4 |
| `background.task.run` | 2 |

`WireGoldenFixtureTest` decodes every canonical fixture and verifies that encoding the decoded
value produces the same JSON structure. The unknown-field example is decode-only because a
current encoder does not emit fields it does not know.

Do not replace an existing Hook fixture when introducing a new schema. Add a sibling
`schema-<version>` directory and keep the older fixture for compatibility tests.
The retained Discover schema 3 fixture predates the enforced `userSelectable` flag and is
historical, not accepted by the current host.
