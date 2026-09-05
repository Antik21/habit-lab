---
name: habit-lab-autodev
description: Autonomously implement a Habit Lab change and verify it on Android emulator and/or iOS simulator. Use only when the user explicitly invokes this skill and asks for both implementation and emulator/simulator testing; exclude plain implementation, read-only diagnosis or review, PR stabilization, and merge-only work.
---

# Habit Lab AutoDev

Use this skill only for the explicit end-to-end request described above. User authorization remains scoped to the requested change; this skill does not grant deployment, stabilization, or merge permission.

## Start here

1. Read the repository [request router](../../../AGENTS.md), [documentation governance](../../docs/00-routing.md), [toolchain catalog](../../docs/01-stack-toolchain.md), and [verification policy](../../docs/07-testing-verification.md).
2. Read [setup and environment](references/setup-and-env.md), [autonomous loop](references/autonomous-loop.md), [UI automation](references/ui-automation.md), and [self-verification](references/self-verification.md).
3. Classify the task and read exactly one playbook: [bug](references/task-types/bug.md), [feature](references/task-types/feature.md), or [performance](references/task-types/perf.md). If none applies, stop before editing with `blocked` and identify the unsupported task type; there is no generic fallback.
4. Select each requested platform and read its playbook: [Android](references/platforms/android.md) and/or [iOS](references/platforms/ios.md).
5. Follow every matching root route for all affected boundaries: screen, route, dialog, repository-room, Android adapter, iOS adapter, dependency-toolchain, or tests-verification. Read only the union of policies linked by those routes.

## Shared invariants

- Run only against Android emulators and iOS simulators with explicit targets; never use physical devices.
- Keep secrets out of commands, logs, screenshots, reports, and chat. AutoDev-owned orchestration state, metadata, reports, local configuration, and credentials belong under ignored `.autodev/` paths. Tool-owned canonical evidence stays at its owner-defined path; in particular, the existing Maestro runner writes to `build/maestro/<run-id>/<platform>` and AutoDev references those artifacts in place.
- For app controls, use stable semantic automation IDs. Never select localized text, runtime or user values, or coordinates. The sole coordinate exception is the platform-owned system gesture admitted by [ADR 0003](../../../docs/adr/0003-maestro-cross-platform-ui-automation.md).
- Treat the repository Maestro runner as the current UI verification baseline, not as a generic AutoDev adapter. Do not manufacture missing adapters, reusable flows, reservation pools, executable gates, or self-patching mechanisms.
- Freeze the task checklist before implementation. Never weaken the checklist, skill, evaluator, or gate to make a run pass. Clean scratch resources when the run ends.

Use [screen navigation](memory/screen-navigation.md) only as an index. Admit reusable knowledge to [lessons](memory/lessons.md) only under its verification contract; future navigation nodes must follow the [navigation schema](memory/nav/_SCHEMA.md).

Report exactly one terminal outcome: `success`, `blocked`, `failed`, or `partial`. `success` requires both preserved evidence and a passing gate. Stabilization and merge require a separate explicit command.
