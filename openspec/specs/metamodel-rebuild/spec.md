# metamodel-rebuild Specification

## Purpose

Force an app instance to rebuild the metamodel of a given target object before that object is involved in a replay, so that replays which resolve against the metamodel (notably "navigate to one of" actions) are not derailed by a stale metamodel. Rebuild is a precondition for a correct replay — not a pass/fail verdict — so its failures abort the run distinctly from a database divergence.

## Requirements

### Requirement: Rebuild a target object's metamodel via the app automation API
The tool SHALL be able to force an app instance to rebuild the metamodel of a given target object by issuing `POST {app-base}/api/automation/rebuild/{target}` over HTTP Basic Auth, where `{target}` is the command's target bookmark string (URL-path encoded). The tool SHALL send the Causeway `--username` / `--password` as the Basic-Auth credentials (the same ones used for login and import) and SHALL send no request body.

#### Scenario: Rebuild request succeeds
- **WHEN** the tool issues `POST {app-base}/api/automation/rebuild/{target}` with valid Basic-Auth credentials
- **THEN** a `200` response is treated as a successful rebuild and the tool proceeds

#### Scenario: Target bookmark is path-encoded
- **WHEN** the target bookmark contains characters that are not safe in a URL path segment (e.g. `:` in `party.Organisation:123`)
- **THEN** the tool encodes the bookmark so it is transmitted as a single path segment value to `{app-base}/api/automation/rebuild/{target}`

### Requirement: Rebuild failures abort the run distinctly from a divergence
A rebuild is a precondition for a correct replay, not a verdict. If the rebuild request fails — a transport error, or any non-`200` response — the tool SHALL abort the run with a clear error that names the app and target, and SHALL exit non-zero with the same status used for import failures (exit code 2). A rebuild failure SHALL NOT be reported as a database divergence.

#### Scenario: Non-200 response
- **WHEN** the rebuild endpoint returns a non-`200` status (e.g. `401`, `404`, `500`)
- **THEN** the tool prints an error naming the app, target, and HTTP status, and exits with code 2

#### Scenario: Transport error
- **WHEN** the rebuild request cannot be sent or times out
- **THEN** the tool prints an error naming the app and target, and exits with code 2
