## ADDED Requirements

### Requirement: Confirm the comparison matches the replayed command
Before using a cfct comparison to decide pass/fail, the tool SHALL confirm the comparison is for the command just replayed on app-a and app-b. The tool SHALL compare the just-replayed command's interaction id against the `command.interactionId` in the cfct response, case-insensitively.

On mismatch — which indicates cfct was queried before both apps had finished committing the command — the tool SHALL re-query cfct (each query re-refreshes server-side) up to a configured number of attempts, waiting a configured delay between attempts. The attempt count and delay SHALL be configurable (`aregress.compare.max-attempts`, `aregress.compare.retry-delay`) via the standard configuration sources.

If confirmed, the existing divergence check (`hasDifferences`) applies as before. If the comparison is never confirmed within the configured attempts, the tool SHALL abort the step with a clear error and exit non-zero — an unconfirmed comparison SHALL NOT be treated as a pass.

#### Scenario: Confirmed on the first attempt
- **WHEN** the cfct response's `command.interactionId` equals (case-insensitively) the interaction id of the command just replayed
- **THEN** the tool proceeds to evaluate `hasDifferences` for that result

#### Scenario: cfct reports an older command, then catches up
- **WHEN** the first response's `command.interactionId` does not match the just-replayed command (cfct had not yet caught up) and a subsequent re-query within the configured attempts does match
- **THEN** the tool proceeds with the matching result and evaluates `hasDifferences`

#### Scenario: Comparison never confirmed — abort
- **WHEN** no response matches the just-replayed command's interaction id within the configured attempts
- **THEN** the tool prints a clear error naming the expected command and exits non-zero, and does not report the step as passed
