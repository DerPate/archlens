# GitHub Actions Cache Cleanup

## Problem

The repository currently stores about 1.93 GB across 26 cache entries. Maven
caches account for roughly 704 MB. OWASP Dependency-Check accounts for roughly
1.23 GB because its cache key includes `github.run_id`, so every CI run uploads
a new copy of the database. Pull-request refs also save their own branch-scoped
copies.

## Design

Remove `cache: maven` from the Java setup steps in CI and CodeQL. Keep only the
OWASP Dependency-Check database cache.

Replace the combined cache action with separate restore and save actions. Every
CI run restores the current ISO-week key, falling back to the newest older
Dependency-Check cache. Only successful OWASP runs on `main` may save a missing
weekly key. Pull requests may restore the default branch's cache but never save
their own copies.

The weekly key bounds normal steady-state storage to approximately one or two
110 MB OWASP database snapshots while still periodically persisting incremental
NVD updates.

## Verification

- Parse both workflow files as YAML.
- Assert neither workflow enables Maven caching.
- Assert the OWASP key is weekly and does not contain `github.run_id`.
- Assert cache saving is restricted to successful OWASP runs on `main`.
- Run `git diff --check`.
- After the workflow fix is pushed, delete existing repository cache entries;
  the next successful `main` CI run will create the single weekly OWASP cache.
