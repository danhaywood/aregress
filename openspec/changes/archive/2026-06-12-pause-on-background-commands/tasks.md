## 1. Model

- [x] 1.1 Add a nested `BackgroundCommands { int pending }` to `ComparisonResult` (Gson-bound, null-tolerant — treat absent/null as zero pending); add a small accessor (e.g. `pendingBackgroundCommands()`) returning 0 when absent.

## 2. Orchestration

- [x] 2.1 In `ReplayCommand`, after the race-guard-confirmed comparison and **before** the `hasDifferences` check: if `pendingBackgroundCommands() > 0`, print `[step N] <member> — paused: <n> background command(s) pending; re-run aregress once they have completed` and return exit code `3`.

## 3. Docs

- [x] 3.1 README: add exit code `3` ("paused: background commands pending, re-run later") to the exit-code list, and note that re-running resumes from the still-pending commands.

## 4. Build & verify

- [x] 4.1 `mvn package`; jar builds, `--help` unchanged.
- [x] 4.2 Verified live on a clean in-sync DB: a full `--file` run stepped through 17 commands with no spurious "paused" lines (the background check is a correct no-op at `backgroundCommands.pending: 0`), behaviour unchanged, stopping at the usual `updateLocale` app-b replay-fail (exit 1).
- [~] 4.3 Background case: owner-dependent - needs a recording with an InvoiceRun-style command that actually spawns background work. Then: run stops at that step with the paused message + exit 3, and re-running after the background commands complete continues.
