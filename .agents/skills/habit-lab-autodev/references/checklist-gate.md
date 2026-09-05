# Frozen checklist gate

The reviewed stdlib-only executable is [`autodev_gate.py`](../scripts/autodev_gate.py). Run it with Python 3.9 or newer from the repository root. It writes orchestration state only to ignored `.autodev/state/<run-id>/` and its atomic `report.md` plus receipt index only to `.autodev/artifacts/<run-id>/`. It never creates a PR. A successful finish only reports `Draft PR: eligible`.

## Safety and lifecycle

`run-id`, task IDs, and criterion IDs accept a restricted ASCII identifier grammar; paths are always repository-relative. The gate rejects traversal, absolute paths, URLs, symlinks, non-regular files, foreign-owned run directories, changed ancestor/directory identities, or unavailable no-follow/exclusive-create/file-locking/dir-FD primitives. It retains verified directory FDs for the command and performs child discovery, locking, reads, exclusive writes, and atomic replacement relative to them. It creates each run exclusively and never cleans or reuses a foreign run. If the artifact child collides after the state child is created, the incomplete state child remains fail-closed for diagnosis; rollback never removes a child by name.

`manifest.json` is immutable and sealed by an exclusive digest anchor. Every command validates its exact closed schema: `schemaVersion`, `runId`, safe `taskId`, `taskType`, full hexadecimal `sourceRevision`, sorted unique `requestedPlatforms`, nonempty `blastRadius`, timezone-qualified `createdAt`, and the exact empty acquired-none `deviceLeases` list. Missing, extra, edited, mistyped, or re-anchored unsafe fields are integrity failures.

Evidence must already exist below one of the run's closed canonical roots:

- `.autodev/artifacts/<run-id>/...` for run/tool-owned output;
- `build/maestro/<run-id>/...` for Maestro-owned output.

Every evidence event records size and SHA-256. `status` and `finish` safely reopen and revalidate all recorded evidence and reread references; post-finish `status` also checks the report, receipt index, and every indexed receipt. Core criteria, late regressions, and attempts are separate immutable, exclusively created JSON event chains with matching append anchors. Freeze and terminal records have independent anchors. `freeze` stores the canonical core snapshot and digest; later commands reject mutation, deletion, sequence gaps, missing anchors, or broken links. This protects accidental or unsynchronized mutation, not a privileged attacker able to rewrite the repository and every integrity record.

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
python3 .agents/skills/habit-lab-autodev/scripts/autodev_gate.py status <run-id>
python3 .agents/skills/habit-lab-autodev/scripts/autodev_gate.py finish <run-id> \
  --outcome success|blocked|failed|partial --source-revision <git-rev> [--reason <reason>]
```

Omitting `--platform` on `init` selects Android and iOS. Before freeze, `add` only appends and exposes no update/delete operation. After freeze it accepts only a new `regression` with a mandatory `--reason`; the late-regression chain invalidates the prior review.

After two failures for one criterion/platform, the next `pass` or `fail` also requires `--hypothesis` and `--reread-reference`. The hypothesis must differ from all earlier hypotheses and the reread artifact must be new, revalidated, and newer than the last failed evidence. A third failure seals that pair as terminal partial, rejects later attempts, and permits only `finish --outcome partial`.

Bug reproduction criteria require `--scenario-key`. Record the immutable failing baseline with `fail --phase baseline` at the manifest's initial source revision, then record separate passing evidence with `pass --phase fixed` at the checked revision using the same key. Performance attempts require `--scenario-fingerprint`; record a baseline with `pass --phase baseline` at the immutable initial source revision, then a comparable result at the checked later revision with `pass|fail --phase candidate` or `repeat`. Baseline-only events do not satisfy the matrix. Other tasks use `observation`.

`finish` always preserves partial artifacts and writes an atomic report. Non-success outcomes require an exact reason. Success additionally requires `--build-receipt` and `--test-receipt` (repeatable), plus one each of `--memory-receipt`, `--review-receipt`, and `--cleanup-receipt`. Every requested platform must be covered by both build and test receipts. Final passing evidence, receipts, review, current HEAD, and a clean working tree must match the checked revision; each common criterion retains separate Android and iOS cells. No device lease may remain. An empty `deviceLeases` list means none was acquired; the gate does not invent a reservation adapter.

## Evidence schemas

`--evidence-type` is closed to `junit`, `command`, and `metric`; structured artifacts are capped at 64 MiB, criterion type is frozen, and every status/finish reparses the unchanged bytes and compares derived metadata. Plain text, URLs, bare JSON booleans, arbitrary prose JSON, schema extensions, and missing/mismatched fields are rejected. A screenshot may be retained alongside evidence but is never a passing proof by itself.

`junit` accepts bounded XML whose root is `testsuite` or `testsuites`, with nonnegative integer `tests`, `failures`, `errors`, and `skipped` attributes. Suite properties must bind `autodev.sourceRevision`, `autodev.platform`, `autodev.criterionId`, and `autodev.result` to the attempt, so one platform's XML cannot proxy another. Pass requires at least one executed test and zero failures/errors; fail requires at least one failure/error. Doctypes are rejected.

`command` is an exact JSON object with `schemaVersion: 1`, `kind: "command-evidence"`, full `sourceRevision`, timezone-qualified `timestamp`, `platform`, `criterionId`, `result`, integer `exitCode`, and nonempty `command`. All bound fields must match the attempt; pass requires exit 0 and fail requires nonzero.

`metric` is required for performance criteria and forbidden for other task types. Its exact JSON fields are `schemaVersion: 1`, `kind: "metric-evidence"`, full `sourceRevision`, timezone-qualified `timestamp`, `platform`, `criterionId`, `result`, `phase`, `scenarioFingerprint`, nonempty `metricName`, finite numeric `value`, and nonempty `unit`. Bound fields must match the attempt. Performance baseline and candidate/repeat events must use the same fingerprint; the baseline revision must equal the manifest's immutable initial revision.

After two failures, `--reread-reference` is an exact JSON object with `schemaVersion: 1`, `kind: "reread-reference"`, full `sourceRevision`, timezone-qualified `timestamp`, `platform`, `criterionId`, the exact new `hypothesis`, and nonempty `reference`. Its file/hash pair must be new and its mtime newer than the preceding failed evidence.

## Receipt schemas

Receipt files are inputs below an allowed evidence root. The gate hashes them into `receipts.json`; it never treats prose or a bare boolean as proof. Every receipt is a JSON object with `schemaVersion: 1`, the exact full `sourceRevision`, timezone-qualified ISO-8601 `timestamp`, terminal `status`, and the matching `kind`. Success requires `status: "pass"`; a non-success report may index supplied `fail`, `blocked`, or `skipped` receipts without promoting them.

Build and test receipts require `kind: "build"|"test"`, nonempty `command`, integer `exitCode`, and explicit unique `platforms: ["android", "ios"]` as applicable. Pass requires exit 0; fail requires nonzero. Use separate receipts when commands differ.

The composite memory receipt requires `kind: "memory"`, nonempty `read`, `written` (possibly empty), and `lint: {"command": "...", "status": "pass", "exitCode": 0}`. This records both memory use and its lint result.

The independent review receipt requires `kind: "review"`, `independent: true`, `reviewedRevision` equal to the checked revision, `unresolvedJustifiedFindings: 0`, and `lateRegressionDigest` copied from current `status`. Its timestamp must follow the latest late regression.

The cleanup receipt requires `kind: "cleanup"`, `residualScratch: []`, `forbiddenHooks: []`, and `secretScan` containing `status: "pass"`, `findings: 0`, `checkedPaths`, `allowlistedPaths`, and a truthful bounded `coverage` description.

On success the gate also performs its own deterministic, bounded scan of changed source and checked input files up to 64 MiB. It detects a small known set of AutoDev bypass/debug hooks, common token shapes, and private-key headers without printing matched values. This is not universal secret detection; broader security review remains required where the blast radius warrants it.
