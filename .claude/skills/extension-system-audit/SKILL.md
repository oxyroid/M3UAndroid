---
name: extension-system-audit
description: Audit M3UAndroid extension APIs and runtime for host/plugin boundaries, classloader compatibility, manifest metadata assumptions, IPC/protobuf compatibility, and dependency leakage.
allowed-tools: Read, Glob, Grep, Bash, Agent
argument-hint: "[repo path or extension module path]"
---

# Extension System Audit

Audit extension compatibility with evidence from API, runtime, manifest, and dependency boundaries.

## When To Use

Use when a task changes or reviews:

- Extension API modules or host runtime loading
- Plugin discovery, manifest metadata, or registration
- AIDL/protobuf contracts
- Classloader behavior
- Host/plugin dependency boundaries

## Required Context

Read first:

- `AGENTS.md`
- `core/AGENTS.md`
- `docs/ai/playbooks/extension-system.md`
- Relevant app/business/data instructions for touched modules
- Nearby extension API, runtime, manifest, and sample/plugin code

## Audit Checks

Verify with file evidence:

- API/runtime boundary is explicit and stable.
- Extension APIs do not depend on app or host implementation modules.
- Classloader assumptions are documented or guarded.
- Manifest metadata is validated and has failure behavior.
- AIDL/protobuf changes preserve compatibility or document migration.
- Host/plugin dependency leakage is not introduced.
- Public or semi-public API changes include compatibility notes.

## Process

1. Map extension modules, API surfaces, runtime loaders, and metadata inputs.
2. Inspect dependency declarations for forbidden direction or leakage.
3. Trace plugin discovery and failure paths.
4. Check contract/schema changes against compatibility expectations.
5. Cite file paths and line numbers for each finding.

## Validation

Recommend:

```bash
./gradlew :core:extension:compileDebugKotlin :app:extension:compileDebugKotlin
```

Also compile any host app module touched by runtime changes. If unavailable, state compatibility risk.

## Output

Return a concise Markdown report with:

- Scope and compatibility-sensitive APIs
- Pass/fail table for each audit check
- Evidence-backed findings with file paths
- Compatibility notes required in the PR
- Validation run or not run, with reason
