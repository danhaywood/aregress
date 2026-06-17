## ADDED Requirements

### Requirement: Rebuild the target metamodel before replaying a "navigate to one of" command
"Navigate to one of" actions resolve their choices against the target object's metamodel, so a stale metamodel can cause a spurious replay failure or false divergence. When the rebuild behaviour is enabled (see the `app-configuration` capability), for each step the tool SHALL read the oldest pending command's **logical member identifier** and its **target bookmark**. If the logical member identifier contains the prefix `__causeway_navigate_to_one_of_` or `__isis_navigate_to_one_of_`, the tool SHALL, before replaying that command on each app, force a metamodel rebuild of that command's target bookmark on that app via `POST {app-base}/api/automation/rebuild/{target}` (see the `metamodel-rebuild` capability). The rebuild SHALL be performed on **both** apps: app-a's rebuild before app-a's replay, and app-b's rebuild before app-b's replay. Commands whose identifier does not carry either prefix SHALL be replayed without any rebuild. When the rebuild behaviour is disabled, the tool SHALL replay every command without any rebuild call (the behaviour prior to this change).

#### Scenario: Rebuild disabled — no rebuild for any command
- **WHEN** the rebuild toggle is disabled and the oldest pending command is a "navigate to one of" command
- **THEN** the tool replays the command on both apps without reading its target bookmark or calling the rebuild endpoint

#### Scenario: Navigate-to-one-of command — rebuild both apps first
- **WHEN** the rebuild toggle is enabled and the oldest pending command's logical member identifier contains `__causeway_navigate_to_one_of_` (or `__isis_navigate_to_one_of_`) and its target bookmark is `T`
- **THEN** the tool calls `POST {app-a}/api/automation/rebuild/{T}` before clicking "replay next" on app-a, and `POST {app-b}/api/automation/rebuild/{T}` before clicking "replay next" on app-b

#### Scenario: Ordinary command — no rebuild
- **WHEN** the oldest pending command's logical member identifier carries neither the `__causeway_navigate_to_one_of_` nor the `__isis_navigate_to_one_of_` prefix
- **THEN** the tool replays the command on both apps without calling the rebuild endpoint

#### Scenario: Rebuild fails — abort before replay
- **WHEN** a required rebuild call returns a non-`200` response or fails to send
- **THEN** the tool aborts with a clear error naming the app and target and exits with code 2, without replaying that command

## MODIFIED Requirements

### Requirement: Replay next on both apps per step
Each loop iteration SHALL click "replay next" on app-a, wait for completion, then click "replay next" on app-b and wait for completion. When the oldest command is a "navigate to one of" command (its logical member identifier carries the `__causeway_navigate_to_one_of_` or `__isis_navigate_to_one_of_` prefix), the tool SHALL first rebuild that command's target metamodel on the relevant app — app-a's rebuild immediately before app-a's "replay next", and app-b's rebuild immediately before app-b's "replay next".

#### Scenario: Replay in sequence
- **WHEN** an iteration begins
- **THEN** app-a's "replay next" is clicked and the tool waits for the action to complete before clicking app-b's "replay next"

#### Scenario: Replay in sequence with rebuild
- **WHEN** an iteration begins for a "navigate to one of" command with target bookmark `T`
- **THEN** the tool rebuilds `T` on app-a, clicks app-a's "replay next" and waits for completion, then rebuilds `T` on app-b, clicks app-b's "replay next" and waits for completion
