## Why

Some commands spawn **background commands** — e.g. replaying an `InvoiceRun` causes app-a to enqueue further commands that a cronjob executes asynchronously. aregress can't meaningfully compare a step while such work is still outstanding (the footprint isn't settled), and properly orchestrating around async background work is out of scope. The simpler, safe behaviour is to **stop** when background commands are pending and let the operator resume later.

cfct's `comparison.json` now reports this:
```json
{ "backgroundCommands": { "pending": 0 }, ... }
```

## What Changes

- **Pause on pending background commands**: after confirming a step's comparison, if `backgroundCommands.pending` is non-zero, aregress stops the run with a clear message and a **distinct exit status** that signals "paused — resume later", separate from a regression or an automation error.
- **This is a pause, not a failure**: the message tells the operator to wait until the background commands have executed and then re-run aregress, which picks up from where it left off (the pending commands — including the now-completed background ones — are simply replayed on the next run).
- **Check ordering**: the background-commands check happens *before* the divergence (`hasDifferences`) check, since a divergence verdict would be premature while background work is outstanding.
- `ComparisonResult` gains the `backgroundCommands.pending` field.

## Capabilities

### New Capabilities
<!-- none -->

### Modified Capabilities

- `replay-orchestration`: a step with pending background commands halts the run with a distinct "paused, resume later" status rather than continuing or reporting a divergence.

## Impact

- **Code**: `ComparisonResult` adds `backgroundCommands.pending`; the loop checks it after the race-guard confirmation and returns a new distinct exit code on non-zero.
- **Exit codes**: introduce `3` = "paused: background commands pending, re-run later" (alongside `0` success, `1` regression, `2` automation error). README updated.
- **External dependency**: relies on cfct including `backgroundCommands` in its JSON (already shipped).
- **Behaviour**: no change when no background commands are pending; otherwise a clean, resumable stop.
- **Out of scope**: actually waiting for / orchestrating the background commands; any retry around them.
