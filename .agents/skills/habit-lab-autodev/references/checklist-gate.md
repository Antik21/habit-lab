# Frozen checklist gate

The reviewed stdlib-only executable is [`autodev_gate.py`](../scripts/autodev_gate.py). Run it with Python 3.9 or newer from the repository root. It writes orchestration state only to ignored `.autodev/state/<run-id>/` and its atomic `report.md` plus receipt index only to `.autodev/artifacts/<run-id>/`. It never creates a PR. A successful finish only reports `Draft PR: eligible`.

## Safety and lifecycle

`run-id`, task IDs, and criterion IDs accept a restricted ASCII identifier grammar; paths are always repository-relative. The gate rejects traversal, absolute paths, URLs, symlinks, non-regular files, foreign-owned run directories, changed ancestor/directory identities, or unavailable no-follow/exclusive-create/file-locking/dir-FD primitives. It retains verified directory FDs for the command and performs child discovery, locking, reads, exclusive writes, and atomic replacement relative to them. It creates each run exclusively and never cleans or reuses a foreign run. If the artifact child collides after the state child is created, the incomplete state child remains fail-closed for diagnosis; rollback never removes a child by name.

`manifest.json` is immutable and sealed by an exclusive digest anchor. Every command validates its exact closed schema: `schemaVersion`, `runId`, safe `taskId`, `taskType`, full hexadecimal `sourceRevision`, sorted unique `requestedPlatforms`, nonempty `blastRadius`, strict UTC `createdAt`, and the exact empty acquired-none `deviceLeases` list. Missing, extra, edited, mistyped, or re-anchored unsafe fields are integrity failures.

Evidence must already exist below one of the run's closed canonical roots:

- `.autodev/artifacts/<run-id>/...` for run/tool-owned output;
- `build/maestro/<run-id>/...` for Maestro-owned output.

Every evidence event records size and SHA-256. `status` and `finish` safely reopen and revalidate all recorded evidence and reread references; post-finish `status` also checks the report, receipt index, and every indexed receipt. Core criteria, late regressions, attempts, and bug observations are separate immutable, exclusively created JSON event chains with matching append anchors. Freeze and terminal records have independent anchors. `freeze` stores the canonical core snapshot and digest; later commands reject mutation, deletion, sequence gaps, missing anchors, or broken links. This protects accidental or unsynchronized mutation, not a privileged attacker able to rewrite the repository and every integrity record.

Before a new attempt or observation is appended, every existing attempt, observation, and reread artifact is revalidated by hash and structured content. Once `finish` writes the terminal record, the run is immutable: `add`, `freeze`, `pass`, `fail`, `observe`, and another `finish` are rejected before writes; only read-only `status` remains available.

## Commands

All success and error output is one JSON object. Exit codes are stable: `0` success, `2` CLI usage, `3` filesystem/process I/O, `4` validation, `5` integrity, `6` denied gate, and `7` unsupported safety primitives.

```sh
python3 .agents/skills/habit-lab-autodev/scripts/autodev_gate.py init <run-id> \
  --task-id <id> --task-type feature|bug|perf --source-revision <git-rev> \
  [--platform android] [--platform ios] --blast-radius <area>
python3 .agents/skills/habit-lab-autodev/scripts/autodev_gate.py add <run-id> \
  --criterion-id <id> --text <assertion> --kind main|repro|regression \
  [--platform android] [--platform ios] --evidence-type junit|command|metric
python3 .agents/skills/habit-lab-autodev/scripts/autodev_gate.py freeze <run-id>
python3 .agents/skills/habit-lab-autodev/scripts/autodev_gate.py pass|fail <run-id> \
  --criterion <id> --platform android|ios --evidence <repo-relative-file> \
  --source-revision <git-rev> [--phase observation|baseline|fixed|candidate|repeat]
python3 .agents/skills/habit-lab-autodev/scripts/autodev_gate.py observe <run-id> \
  --criterion <repro-id> --platform android|ios --scenario-key <key> \
  --outcome not-reproduced|environment-blocked|diagnostic-error \
  --evidence <repo-relative-json> --source-revision <initial-git-rev>
python3 .agents/skills/habit-lab-autodev/scripts/autodev_gate.py status <run-id>
python3 .agents/skills/habit-lab-autodev/scripts/autodev_gate.py finish <run-id> \
  --outcome success|blocked|failed|partial --source-revision <git-rev> [--reason <reason>]
```

Omitting `--platform` on `init` selects Android and iOS. Before freeze, `add` only appends and exposes no update/delete operation. After freeze it accepts only a new `regression` with a mandatory `--reason`; the late-regression chain invalidates the prior review.

After two failures for one criterion/platform, the next `pass` or `fail` also requires `--hypothesis` and `--reread-reference`. The hypothesis must differ from all earlier hypotheses and the reread artifact must be new, revalidated, and newer than the last failed evidence. A third failure seals that pair as terminal partial, rejects later attempts, and permits only `finish --outcome partial`.

Bug reproduction criteria require `--scenario-key`. Record the immutable failing baseline with `fail --phase baseline` at the manifest's initial source revision, then record separate passing evidence with `pass --phase fixed` at a later descendant checked revision using the same key. Append and every later revalidation reject same-revision, ancestor, incomparable, missing-baseline, or reused-artifact fixed evidence. When the failure does not reproduce or the environment blocks the attempt, use `observe` instead; it records diagnostics in the report but never supplies a failing baseline or a passing matrix cell. Performance attempts require `--scenario-fingerprint`; record a baseline with `pass --phase baseline` at the immutable initial source revision, then a comparable result at the checked later revision with `pass|fail --phase candidate` or `repeat`. Baseline-only events do not satisfy the matrix. Other tasks use `observation`.

`finish` always preserves partial artifacts and writes an atomic report. Non-success outcomes require an exact reason. Success additionally requires `--build-receipt` and `--test-receipt` (repeatable), plus one each of `--memory-receipt`, `--review-receipt`, and `--cleanup-receipt`. Every requested platform must be covered by both build and test receipts. Final passing evidence, receipts, review, current HEAD, and a clean working tree must match the checked revision; each common criterion retains separate Android and iOS cells. Manifest integrity establishes the immutable acquired-none device state; the gate does not invent a reservation adapter.

## Evidence schemas

`--evidence-type` is closed to `junit`, `command`, and `metric`; structured artifacts are capped at 64 MiB, criterion type is frozen, and every status/finish reparses the unchanged bytes and compares derived metadata. Plain text, URLs, bare JSON booleans, arbitrary prose JSON, schema extensions, and missing/mismatched fields are rejected. A screenshot may be retained alongside evidence but is never a passing proof by itself.

`junit` accepts bounded XML whose root is `testsuite` or `testsuites`, with nonnegative integer `tests`, `failures`, `errors`, and `skipped` attributes. Suite properties must bind `autodev.sourceRevision`, `autodev.platform`, `autodev.criterionId`, and `autodev.result` to the attempt, so one platform's XML cannot proxy another. Pass requires at least one executed test and zero failures/errors; fail requires at least one failure/error. Doctypes are rejected.

All gate timestamps use the single strict UTC form `YYYY-MM-DDTHH:MM:SSZ`; offsets, fractional seconds, and basic forms are rejected. `command` is an exact JSON object with `schemaVersion: 1`, `kind: "command-evidence"`, full `sourceRevision`, strict `timestamp`, `platform`, `criterionId`, `result`, integer `exitCode`, and nonempty `command`. All bound fields must match the attempt; pass requires exit 0 and fail requires nonzero.

`observe` accepts only bug `repro` criteria after freeze and only at the immutable initial revision. Its evidence is an exact JSON object with `schemaVersion: 1`, `kind: "bug-observation"`, full `sourceRevision`, strict `timestamp`, `platform`, `criterionId`, nonempty `scenarioKey`, `outcome`, nonempty `command`, integer `exitCode`, and nonempty `diagnostic`. Bound fields must match the command. Outcome is closed to `not-reproduced` with exit code 0 or `environment-blocked|diagnostic-error` with a nonzero exit code. The immutable observation chain is revalidated and included in every terminal report, including `blocked` and `failed`, but never counts as acceptance evidence.

`metric` is required for performance criteria and forbidden for other task types. Its exact JSON fields are `schemaVersion: 1`, `kind: "metric-evidence"`, full `sourceRevision`, strict `timestamp`, `platform`, `criterionId`, `result`, `phase`, `scenarioFingerprint`, nonempty `metricName`, finite numeric `value`, nonempty `unit`, `instrumentation`, `aggregation`, positive integer `sampleCount`, and `threshold`. The exact threshold object is `{direction: increase|decrease, minimumDelta: <finite nonnegative number>, deltaUnit: absolute|percent}`. Baseline and candidate/repeat must have identical comparison fields and threshold. The baseline uses the immutable initial revision; candidate/repeat uses a later descendant revision. The gate computes directional absolute or percent improvement, rejects percent against a zero baseline, and accepts the candidate result only when it equals the computed threshold result. Computed delta, outcome, threshold, values, and baseline artifact hash are sealed in event metadata and reported.

After two failures, `--reread-reference` is an exact JSON object with `schemaVersion: 1`, `kind: "reread-reference"`, full `sourceRevision`, strict `timestamp`, `platform`, `criterionId`, the exact new `hypothesis`, and nonempty `reference`. Its file/hash pair must be new and its mtime newer than the preceding failed evidence.

## Receipt schemas

Receipt files are inputs below an allowed evidence root. The gate hashes them into `receipts.json`; it never treats prose or a bare boolean as proof. Every receipt is a JSON object with `schemaVersion: 1`, the exact full `sourceRevision`, strict UTC `timestamp`, terminal `status`, and the matching `kind`. Success requires `status: "pass"`; a non-success report may index supplied non-promoting build, test, review, or cleanup receipts without promoting them.

Build and test receipts require `kind: "build"|"test"`, nonempty `command`, integer `exitCode`, and explicit unique `platforms: ["android", "ios"]` as applicable. Pass requires exit 0; fail requires nonzero. Use separate receipts when commands differ.

The composite memory receipt is generated only from a successful ledger by `autodev_memory.py receipt`. Its exact closed fields bind `runId`, checked `sourceRevision`, the run ledger path/SHA-256, and each loaded entry path/SHA-256; its lint command/status/exit code are exactly passing. The helper derives `structureChanged`, an `instructionPatchCount` from zero to one, and any structural evaluation receipt from the same manifest-source-to-checked Git range used by the gate. Blocked, failed, and partial outcomes retain their memory ledger and omit this receipt; if one is supplied, it remains a strict non-promoting passing snapshot. The gate rereads the ledger and evaluation artifact, so a claimed memory use cannot replace current bytes.

The independent review receipt always requires `kind: "review"`, `independent: true`, and `reviewedRevision` equal to the checked revision. For success only, it must also have `status: "pass"`, `unresolvedJustifiedFindings: 0`, `lateRegressionDigest` equal to current `status`, and a timestamp strictly after the latest late regression. Non-success may index a structured fail/blocked/skipped review receipt without promotion.

The cleanup receipt requires `kind: "cleanup"`, `residualScratch: []`, `forbiddenHooks: []`, and `secretScan` containing `status: "pass"`, `findings: 0`, `checkedPaths`, `allowlistedPaths`, and a truthful bounded `coverage` description.

On success the gate also performs its own deterministic, bounded scan of changed source and checked input files up to 64 MiB. Each input is streamed through an anchored file descriptor with a strict byte cap, before/after identity, size, and timestamp checks, and an anchored path-identity recheck; concurrent growth, replacement, or mutation fails closed. The scan detects a small known set of AutoDev bypass/debug hooks, common token shapes, and private-key headers without printing matched values. This is not universal secret detection; broader security review remains required where the blast radius warrants it.
