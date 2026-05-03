# AGENTS.md

This file applies to `testing/`. Use it together with the root guidance.

## Testing Scope

- Testing modules own device benchmarks, Mobly flows, mock servers, and test-only host tooling.
- Keep test harnesses close to the behavior they validate, but do not bypass the production path unless the test explicitly needs a narrow fixture.
- Prefer stable, observable user or protocol behavior over brittle implementation details.

## Device And Benchmark Tests

- For multi-device flows, keep setup and teardown symmetric. Clean debug settings, ports, test data, and generated artifacts.
- Avoid relying on emulator NSD reliability when the test already has a debug-only direct endpoint mechanism.
- Keep benchmark debug overrides isolated behind debug-only app APIs or settings.

## Mock Server And Generated Files

- Keep mock-server behavior deterministic and documented near the test that depends on it.
- Do not commit generated outputs such as Python bytecode, build outputs, or mock-server binaries.

## Validation

- Use the smallest relevant Gradle task first, such as compiling the benchmark module or the app variant under test.
- For Python Mobly changes, run syntax or compile checks with the configured project Python environment when available.