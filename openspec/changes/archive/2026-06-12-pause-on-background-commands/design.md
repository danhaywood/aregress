## Context

Per step, after the race guard confirms cfct's comparison is for the just-replayed command, aregress evaluates `hasDifferences`. But a command can spawn **background commands** (e.g. `InvoiceRun` → cronjob-executed work); while those are outstanding the footprint isn't settled, so neither "pass" nor "fail" is meaningful yet. cfct now exposes `backgroundCommands.pending`; aregress should stop and let the operator resume later once that work has run.

## Goals / Non-Goals

**Goals:**
- Detect `backgroundCommands.pending > 0` on the confirmed comparison and stop with a distinct, resumable status.
- Clear message telling the operator to wait then re-run.

**Non-Goals:**
- Waiting for / orchestrating / retrying around the background commands.
- Any change to divergence or replay-failure handling.

## Decisions

### Check after race-guard confirmation, before the divergence check
The pending-background-commands value is read from the **same confirmed `ComparisonResult`** the race guard already obtained (no extra query). It is checked **before** `hasDifferences`, because a divergence verdict is premature while background work is outstanding — pausing takes precedence.

```
result = <race-guard-confirmed comparison for this step>
if result.backgroundCommands != null && result.backgroundCommands.pending > 0:
    print "[step N] <member> — paused: <pending> background command(s) pending; re-run aregress once they have completed"
    return 3
if result.hasDifferences: ... FAIL (1)
... OK
```

### Distinct exit code `3` = paused / resume later
Not `0` (so it's noticed and not mistaken for success), and distinct from `1` (regression) and `2` (automation error) so scripts/CI can treat "paused, resume later" specially. Documented in the README exit-code list.

### Resumption is just re-running
No state is persisted. Once the background commands have executed, they (and any remaining commands) are pending in the `CommandReplayManager`; re-running aregress replays from there. This relies on the existing resume behaviour — re-running picks up the pending commands — so nothing extra is needed.

### Model
`ComparisonResult` gains a nested `BackgroundCommands { int pending }` field (Gson-bound, null-tolerant for older cfct builds — treat absent/null as zero pending).

## Risks / Trade-offs

- **cfct omits `backgroundCommands`** (older build) → treated as zero pending (no pause); safe default, and the contract is already shipped.
- **A step both diverges and has pending background work** → we pause (don't report the divergence), by the ordering above. Correct: the divergence can't be judged until the background work settles; it will be re-evaluated on resume.
- **Operator re-runs too early** (background commands not yet done) → aregress simply pauses again at the same point; idempotent and harmless.

## Migration Plan

1. `ComparisonResult`: add `BackgroundCommands { pending }`.
2. `ReplayCommand`: after the confirmed comparison, check `backgroundCommands.pending`; on non-zero, print the pause message and return `3`, before the `hasDifferences` check.
3. README: add exit code `3` to the exit-code list and note the resume-by-re-running behaviour.
4. Verify: a run with no background commands is unchanged; a step that spawns background commands stops with the pause message and exit `3`, and re-running after they complete continues.

## Open Questions

- Exit code value — `3` proposed; confirm it doesn't clash with any CI convention you rely on.
- Message wording / whether to include the pending count (proposed: yes).
