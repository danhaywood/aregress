## ADDED Requirements

### Requirement: Pause when background commands are pending
A command may spawn background commands that execute asynchronously (e.g. an `InvoiceRun`). The tool SHALL NOT judge such a step while that work is outstanding. After a step's comparison has been confirmed (matching the replayed command), and before the divergence check, the tool SHALL inspect `backgroundCommands.pending` in the cfct response. If it is non-zero, the tool SHALL stop the run with a clear "paused — resume later" message and a distinct non-zero exit status (separate from a regression or an automation error), instructing the operator to re-run aregress once the background commands have completed. This is not a failure verdict.

#### Scenario: Background commands pending — pause
- **WHEN** the confirmed comparison reports `backgroundCommands.pending` greater than zero
- **THEN** the tool prints a paused message naming the pending count, exits with the distinct "paused" status, and does not evaluate the divergence for that step

#### Scenario: No background commands — continue
- **WHEN** the confirmed comparison reports `backgroundCommands.pending` of zero (or omits it)
- **THEN** the tool proceeds to the divergence check as normal

#### Scenario: Resume by re-running
- **WHEN** the background commands have since completed and the operator re-runs aregress
- **THEN** the run continues from the still-pending commands (the previously-spawned background commands now among them) without any saved state
