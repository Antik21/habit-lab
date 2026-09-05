# Autonomous loop

Execute one bounded change through this sequence. Preserve repository policy and the user's authorization at every stage.

## 1. Preflight

- Confirm the request explicitly includes implementation and emulator/simulator verification.
- Inspect repository state without modifying staged or unrelated work. Stop if safe isolation is impossible.
- Confirm required local tools and the requested virtual platforms are available. Never substitute a physical device.
- Read local configuration according to [setup and environment](setup-and-env.md), without exposing secrets.

## 2. Intake and frozen checklist

Collect the task source, acceptance criteria, dependencies, requested platforms, affected ownership boundaries, and required evidence. Resolve material ambiguity before editing. Convert these facts into a finite checklist with observable assertions and a regression boundary, then freeze it.

After implementation begins, do not silently add, remove, reinterpret, or mark checklist items complete. New task facts require an explicit recorded revision and re-review. The frozen core—this skill, its acceptance evaluator, and any gate definition—cannot self-patch during a run.

## 3. Implement

Use the selected task-type and platform references. Make the smallest production and test changes that satisfy the frozen checklist. Preserve shared/common ownership and follow the root route for each touched boundary. Do not add speculative infrastructure for later AutoDev phases.

## 4. Build, deploy, navigate, verify

Use commands selected from the owner catalog rather than copied into this skill. Build the affected targets, deploy only to the named emulator/simulator, and navigate with stable automation IDs. Capture each checklist assertion and failure with attributable platform evidence. Follow [UI automation](ui-automation.md) when selectors or navigation fail.

## 5. Regression and independent review

Run the narrow checks while iterating and the required owner gate afterward. Exercise the declared regression boundary on every requested platform. Obtain an independent review of the diff, checklist, evidence, architecture, safety, and compatibility; resolve justified findings and repeat affected checks.

## 6. Cleanup and gate

Stop app/process resources started by the run when safe, release virtual-device use, and remove temporary scratch data. Preserve only the intended ignored evidence and source changes. Evaluate the frozen checklist and required checks without changing their criteria.

## 7. Draft PR or report

Only a passing gate with complete evidence permits `success` and, when authorized, a Draft PR. This skill never stabilizes or merges that PR; those actions need a separate explicit command.

Choose one terminal outcome:

- `success`: all frozen assertions and required checks pass with preserved evidence.
- `blocked`: an external prerequisite or required platform is unavailable; identify the exact unblock action.
- `failed`: the attempt completed but a required assertion or check failed; preserve diagnostics.
- `partial`: useful scoped work exists, but the full requested contour was neither proved nor externally blocked. Enumerate completed and missing assertions.

Never relabel a gate failure, missing platform, incomplete evidence, or unreviewed change as success.
