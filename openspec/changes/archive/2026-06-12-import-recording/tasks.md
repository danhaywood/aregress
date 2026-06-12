## 1. CLI

- [x] 1.1 Replace the standalone `--timestamp` option with a Picocli exclusive `@ArgGroup(multiplicity = "1")` containing `--timestamp <ts>` and `--file <path>`, so exactly one is required (framework-enforced usage error otherwise).
- [x] 1.2 Update `--timestamp`/`--file` help text and the README options table + examples (file-based priming, no out-of-band import needed).

## 2. Import client

- [x] 2.1 Create `AppImportClient` (JDK `HttpClient`, base URL + Basic-Auth username/password, generous timeout) with `importRecording(Path file) -> String timestamp`: `POST {base}/api/automation/import` with the file as the raw body; require `200`; parse `{ "timestamp": ... }` (reuse Gson); throw a clear `ImportException` on non-`200`/transport error.

## 3. Orchestration

- [x] 3.1 In `Main`: if `--file` is set, construct an `AppImportClient` for app-a and app-b (Causeway creds), import the file into each, and use each app's returned timestamp for that app's replay URL; if `--timestamp` is set, use it for both (unchanged).
- [x] 3.2 Abort before the replay loop on import failure with a clear, app-named message and a distinct non-zero exit (`2`).

## 4. Endpoint documentation

- [x] 4.1 Add `docs/app-import-endpoint.md` — the copy/paste Spring Boot 2.7.x stub (controller + Basic-Auth `SecurityFilterChain`) and the contract (path, auth, request body, JSON `{timestamp}` response, error codes) from design.md, for the owner to wire into app-a/app-b.

## 5. Build & verify

- [x] 5.1 `mvn package` and verify the fat JAR builds; `--help` shows the timestamp/file group; verify neither/both errors with a usage message.
- [x] 5.2 Smoke-tested `--file` end-to-end against the wired endpoints (app-a + app-b) and cfct, from a clean reset baseline: aregress imported the recording into both apps, drove the run from each app's returned timestamp, replayed steps 1–17 `OK` (incl. `OK (no footprint)` for the no-op commands), and stopped at step 18 `updateLocale` with `replay FAILED on app-b` (exit 1) — the same genuine app-b regression the `--timestamp` run surfaced. No out-of-band priming / no `--timestamp` needed.
