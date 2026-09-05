# Autonomous loop

Execute one bounded change through this sequence. Preserve repository policy and the user's authorization at every stage.

## 1. Preflight

- Confirm the request explicitly includes implementation and emulator/simulator verification.
- Inspect repository state without modifying staged or unrelated work. Stop if safe isolation is impossible.
- Confirm required local tools and the requested virtual platforms are available. Never substitute a physical device.
- Read local configuration according to [setup and environment](setup-and-env.md), without exposing secrets.

## 2. Intake and frozen checklist

Collect the task source, acceptance criteria, dependencies, requested platforms, affected ownership boundaries, and required evidence. Resolve material ambiguity before editing. Initialize the [checklist gate](checklist-gate.md), append finite observable assertions and a regression boundary, then freeze it before implementation.

After implementation begins, do not silently add, remove, reinterpret, or mark checklist items complete. Add a newly discovered regression separately with its reason; it invalidates the prior review. The frozen core—this skill, its acceptance evaluator, and the gate definition—cannot self-patch during a run.

## 3. Implement

Use the selected task-type and platform references. Make the smallest production and test changes that satisfy the frozen checklist. Preserve shared/common ownership and follow the root route for each touched boundary. Record evidence attempts through the gate; follow its reread/hypothesis rule after repeated failures. Do not add speculative infrastructure for later AutoDev phases.

## 4. Build, deploy, navigate, verify

Use commands selected from the owner catalog rather than copied into this skill. Build the affected targets, deploy only to the named emulator/simulator, and navigate with stable automation IDs. Capture each checklist assertion and failure with attributable platform evidence. Follow [UI automation](ui-automation.md) when selectors or navigation fail.

## 5. Regression and independent review

Run the narrow checks while iterating and the required owner gate afterward. Exercise the declared regression boundary on every requested platform. Obtain an independent review of the diff, checklist, evidence, architecture, safety, and compatibility; resolve justified findings and repeat affected checks.

Use the progressive memory helper before `finish`: begin with its two initial records, select only relevant catalog entries, run lint, and finalize the memory ledger. On a success path, create the revision-bound memory receipt; blocked, failed, and partial paths retain the ledger without a promotable receipt. This is evidence for the frozen gate, not permission to change it.

## 6. Cleanup and gate

Stop only processes launched by this run. Release only reservations acquired by this run. Delete only explicitly ephemeral scratch owned by this run. Never delete app resources, app data, or app state, and do not change a pre-existing emulator/simulator target beyond stopping, releasing, or removing those run-owned processes, reservations, or scratch paths. Preserve every pre-existing, concurrent-run, and user-owned process, virtual device, reservation, file, state, and canonical artifact. Preserve the current run's intended ignored evidence and source changes. Evaluate the frozen checklist and required checks without changing their criteria.

## 7. Draft PR or report

Only `finish --outcome success` with complete evidence permits `success` and marks a Draft PR eligible. The gate never creates a PR. This skill never stabilizes or merges an authorized Draft PR; those actions need a separate explicit command.

Choose one terminal outcome:

- `success`: all frozen assertions and required checks pass with preserved evidence.
- `blocked`: an external prerequisite or required platform is unavailable, or no task playbook supports the request. Identify the exact unblock action. For an unsupported task type, name the appropriate alternate workflow or a concrete action or condition that would make a supported playbook apply.
- `failed`: the attempt completed but a required assertion or check failed; preserve diagnostics.
- `partial`: useful scoped work exists, but the full requested contour was neither proved nor externally blocked. Enumerate completed and missing assertions.

Never relabel a gate failure, missing platform, incomplete evidence, or unreviewed change as success.

## After a sealed terminal result

Only then may [progressive memory](self-learning.md) observe correction proposals from distinct successful, independently reviewed runs. A second observation confirms a proposal; a third produces advice, not an automatic patch. Consolidation is advisory around every five terminal ledgers. A self-patch is a separate, explicit, one-record normal commit after validation; frozen core paths remain denied.
