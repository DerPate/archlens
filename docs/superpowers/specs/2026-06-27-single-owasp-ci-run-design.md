# Single OWASP CI Run

## Problem

The CI workflow runs OWASP Dependency-Check twice. The Maven plugin is bound to
the `verify` lifecycle, so `mvn verify` invokes it before the workflow restores
the OWASP data cache or exposes the NVD API key. A later dedicated OWASP step
runs it again with those settings. The first unauthenticated database update can
therefore take a long time or time out, while the configured second invocation
arrives too late to help.

## Design

Keep OWASP Dependency-Check bound to Maven `verify` for local and release-build
behavior, but skip that execution in the CI build-and-test step by passing the
plugin's skip property. Restore the dependency-check data cache next, then run
one dedicated OWASP step with the cache directory and NVD API-key environment
variable configured explicitly.

The dedicated step remains non-blocking as in the current workflow. This change
only removes the unintended duplicate invocation; it does not alter the existing
vulnerability threshold or suppression rules.

## Verification

- Parse the workflow YAML and assert there is one explicit Dependency-Check
  invocation.
- Confirm the build step passes the Dependency-Check skip property.
- Confirm the cache is restored before the dedicated invocation.
- Confirm the dedicated invocation maps `secrets.NVD_API_KEY` to `NVD_API_KEY`
  and tells the Maven plugin to read that environment variable.
- Run the Maven test suite to ensure the project build remains healthy.
