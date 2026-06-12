## 1. Model

- [ ] 1.1 Add a nested `BackgroundCommands { int pending }` to `ComparisonResult` (Gson-bound, null-tolerant — treat absent/null as zero pending); add a small accessor (e.g. `pendingBackgroundCommands()`) returning 0 when absent.

## 2. Orchestration

- [ ] 2.1 In `ReplayCommand`, after the race-guard-confirmed comparison and **before** the `hasDifferences` check: if `pendingBackgroundCommands() > 0`, print `[step N] <member> — paused: <n> background command(s) pending; re-run aregress once they have completed` and return exit code `3`.

## 3. Docs

- [ ] 3.1 README: add exit code `3` ("paused: background commands pending, re-run later") to the exit-code list, and note that re-running resumes from the still-pending commands.

## 4. Build & verify

- [ ] 4.1 `mvn package`; jar builds, `--help` unchanged.
- [ ] 4.2 No-background case unchanged: a normal run behaves exactly as before (background check is a no-op at `pending == 0`).
- [ ] 4.3 Background case (when a recording with an `InvoiceRun`-style command is available): confirm the run stops at that step with the paused message and exit `3`, and that re-running after the background commands complete continues. (Owner-dependent on having such a recording / state.)
