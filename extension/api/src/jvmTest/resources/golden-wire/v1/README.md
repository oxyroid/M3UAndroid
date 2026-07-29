# Extension API 1 golden wire fixtures

These files are the reviewable JSON shapes emitted by the Extension API 1 serializers.

## Fixture index

- `manifests/complete.json` covers every non-empty manifest section.
- `envelopes/` covers the canonical invocation and result envelopes, including settings,
  grants, broker scope, and an additive unknown-field example.
- `provider-validation-evidence/` covers both `trusted_direct` and `host_broker_receipt`.
- `broker/` covers both operations, both operation results, success and failure envelopes,
  every `BrokerValue` branch, and every `ResponseValueSource` branch.

The Hook fixture catalog currently retains these explicit API 1 schema versions:

| Hook | Schema |
|---|---:|
| `subscription.provider.discover` | 1 |
| `subscription.provider.validate` | 1 |
| `subscription.content.refresh` | 1 |
| `subscription.content.browse` | 1 |
| `playback.source.resolve` | 1 |
| `playback.session.update` | 1 |
| `playback.session.close` | 1 |
| `settings.schema.contribute` | 1 |
| `epg.content.refresh` | 1 |
| `metadata.channel.enrich` | 1 |
| `search.provider.query` | 1 |
| `background.task.run` | 1 |

`WireGoldenFixtureTest` decodes every canonical fixture and verifies that encoding the decoded
value produces the same JSON structure. The unknown-field example is decode-only because a
current encoder does not emit fields it does not know.

Until the first public SDK release, each Hook keeps only its current canonical fixture. After the
SDK is public, add a sibling `schema-<version>` directory when retaining an older supported schema.
