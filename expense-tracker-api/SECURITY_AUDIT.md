# Repository secret audit

Audit date: 2026-11-08. Snapshot: `deb4724` plus the working tree at audit time.
Git root: the parent `quanlychitieuv2` repository, not only the Maven subdirectory.

## Scope and method

- Inspected 124 tracked files and 2 relevant untracked files (126 text files).
  The working tree included the environment example, packaging documentation,
  Docker changes and new tests. Ignored local/generated files and binary files
  were excluded; no private local credentials were copied into this report.
- Read `git log --all -p --no-ext-diff` into memory across all local refs:
  12 commits, 9,158 output lines, 7,881 added/removed patch lines and 205 hunks.
  Both added and removed content was checked, so deleting a secret in a later
  commit would not hide it from this scan. Patch contents were not printed.
- Applied static patterns for private keys, GitHub/AWS/Google/Slack/npm tokens,
  JWT literals, Basic credentials and credentials embedded in URLs. Also checked
  quoted secret/password assignments and credential-bearing properties/YAML/env
  assignments, then reviewed candidate locations in context. Only sanitized
  file locations, classifications and counts were emitted.
- Checked ignore rules with `git check-ignore` for `.env`, `.env.production`
  and a private-key filename, and reviewed the Docker build context exclusions.
  No additional scanner or application dependency was installed.

## Results

No production credential or high-signal token/private-key match was found in the
scanned working files or reachable Git history.

| Candidate group | Result |
| --- | --- |
| Recognizable key/token/credential formats | 0 matches |
| Quoted assignments, including history occurrences | 19 candidates: 15 test fixtures, 2 labels/references, 2 validation-message occurrences |
| Credential configuration assignments, including history occurrences | 18 candidates: 12 environment references, 4 example placeholders, 2 test fixtures |
| Unclassified credential candidates after review | 0 |

The intentional test key is located in `src/test/java/com/example/expensetracker/auth/JwtTestFixtures.java`
and `src/test/resources/application-test.properties`. Synthetic passwords appear
in test fixtures and HTTP test payloads. These are test-only values, not production
credentials. The remaining quoted candidates were the Bearer label and the JWT
configuration validation message in `JwtProperties.java`.

Production datasource/JWT settings and Compose passwords use environment
references. `.env.example` contains explicit replacement placeholders; its JWT
placeholder is deliberately unsuitable as a signing key. The existing uncommitted
`docker-compose.yml` change was inspected but not modified by this audit.

`.gitignore` and `.dockerignore` exclude `.env` and `.env.*` while allowing
`.env.example`. Local credential files and private-key extensions are excluded
from Git and the Docker build context. No actual `.env` or private-key container
file was found among the tracked paths.

## Limits and follow-up

This was a static heuristic scan and contextual review, not proof that every
possible secret format is absent. It covers reachable local refs, not unreachable
objects, remote-only refs, GitHub logs/artifacts, ignored local files, or external
secret stores. Gitleaks/TruffleHog were not available and were not installed.
The initial snapshot was refreshed below; review later changes as part of
normal code review and refresh this audit when adding credential configuration.

If a real credential is discovered, revoke/rotate it first, then assess history
cleanup and affected deployments. Consider GitHub secret scanning/push protection
and a dedicated secret-scanning CI check as a separate improvement.

## Final phase-5 follow-up

Repeated the high-signal scan at HEAD `46d497a` plus the complete non-ignored
working tree before the packaging commit (2026-11-08): **128 text files**,
**15 reachable commits**, **214 patch hunks** via `git log --all -p --no-ext-diff`.
This includes the new CI workflow, JaCoCo configuration, tests, environment
example and packaging documentation. No high-signal secret matches were found.
The contextual candidate classifications in the initial audit remain applicable;
new packaging values are replacement placeholders and the CI uses no credentials.
No raw secret value or patch content was emitted by either scan.
