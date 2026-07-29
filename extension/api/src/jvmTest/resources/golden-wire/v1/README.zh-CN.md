# Extension API 1 Golden Wire Fixture

这些文件记录 Extension API 1 序列化器输出的、便于审查的 JSON 结构。

## Fixture 索引

- `manifests/complete.json` 覆盖 Manifest 的全部非空部分。
- `envelopes/` 覆盖规范的调用与结果 Envelope，包括 Settings、Capability 授权、
  Broker Scope，以及新增未知字段的兼容性样例。
- `provider-validation-evidence/` 覆盖 `trusted_direct` 和 `host_broker_receipt`。
- `broker/` 覆盖两种 Operation、两种 Operation Result、成功与失败 Envelope、
  `BrokerValue` 的所有分支，以及 `ResponseValueSource` 的所有分支。

Hook Fixture 目录当前明确保留以下 API 1 Schema 版本：

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

`WireGoldenFixtureTest` 会解码每个规范 Fixture，并确认重新编码后得到相同的 JSON 结构。
未知字段样例只用于解码，因为当前编码器不会输出自己不认识的字段。

首个公开 SDK 发布前，每个 Hook 只保留当前规范 Fixture。SDK 公开发布后，若旧 Schema
仍受支持，再新增相邻的 `schema-<version>` 目录。
