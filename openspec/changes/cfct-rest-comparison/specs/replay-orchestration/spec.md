## MODIFIED Requirements

### Requirement: Compare in cfct and check for differences
After both replays succeed, the tool SHALL obtain the comparison from cfct's automation REST API and determine whether the databases have diverged. The tool SHALL `GET {cfct}/api/automation/comparison.json` (HTTP Basic Auth); the endpoint refreshes the footprint comparison for the newest successful command server-side before returning it. The tool SHALL parse the returned JSON, whose top-level `hasDifferences` flag is the divergence signal (with `differingTables` and `comparedTables` providing detail).

#### Scenario: No differences — pass
- **WHEN** the comparison request returns `200` and the JSON reports `hasDifferences: false`
- **THEN** the tool logs `[step N] <command> replayed... OK` and continues to the next iteration

#### Scenario: No-op command with no footprint
- **WHEN** the just-replayed command touched no business tables and the comparison returns `200` with `hasDifferences: false` and an empty `comparedTables`
- **THEN** the tool logs `[step N] <command> replayed... OK (no footprint)` and continues to the next iteration

#### Scenario: Differences detected — fail
- **WHEN** the comparison JSON reports `hasDifferences: true`
- **THEN** the tool logs `[step N] <command> replayed... FAIL — database divergence: <differing tables>` and exits with code 1

#### Scenario: Automation API error
- **WHEN** the comparison request returns a non-`200` response
- **THEN** the tool SHALL abort with a clear error distinct from a data divergence and exit non-zero

## REMOVED Requirements

### Requirement: Connect cfct to the databases on startup
**Reason**: cfct's automation REST API owns the database connection server-side, so the tool no longer performs a cfct UI login. Authentication to the API is HTTP Basic Auth on each request instead of a one-time UI connection.
**Migration**: Provide the automation API base URL via `--cfct` and Basic-Auth credentials via `--cfct-username` / `--cfct-password`; ensure cfct is configured and connected to both databases out-of-band.
