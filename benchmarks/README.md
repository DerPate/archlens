# Architecture Question Benchmark

The benchmark asks deterministic maintenance questions against small, reviewable Java systems.
It evaluates structured MCP facts rather than LLM prose.

Build ArchLens and run all scenarios:

```sh
mvn package
python3 scripts/run-benchmark.py
```

Run one scenario:

```sh
python3 scripts/run-benchmark.py --scenario spring-persistence
```

Generated JSON, Markdown, and server logs are written under `target/benchmark/` and are not
committed. Each `scenario.json` declares a workspace, questions, tool calls, and assertions over
`structuredContent`. Dotted paths in `where` and `requiredFields` address nested result fields.
Scalar assertions use `equals`; list assertions can use `contains`.

`benchmarks/baseline.json` records passing question IDs plus index time and graph node/edge counts
for every scenario. Reports show current values, deltas, per-question timings, and regressions.
Timing deltas are diagnostic because they depend on the host; node/edge deltas are deterministic
for unchanged fixtures.

Scenarios intentionally include unresolved or ambiguous facts. An absent fact is different from a
confirmed negative result, and benchmark assertions should preserve that distinction.

The Java EE scenario also verifies a complete configuration path from an EJB through its
`persistence.xml` unit and JNDI datasource to a sanitized WildFly database endpoint.
