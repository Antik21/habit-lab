# ADR 0005: Launch-gated route restoration

- Status: Accepted
- Owner: Habit Lab maintainers
- Date: 2026-09-07
- Supersedes: ADR 0002
- Superseded by: none

## Context

Route snapshot v2 restored `Gallery` before durable onboarding state and the active onboarding
protocol were reread. That could expose a stale product route, race two independent observer
flows, or admit an external experiment link before completed onboarding was proven. The onboarding
flow requires a common Launch Gate, while route restoration remains owned by the common Navigation
3 shell.

## Decision

This ADR supersedes ADR 0002's complete restoration policy while retaining its common single-stack
ownership, route-only persistence, typed route arguments, platform snapshot storage boundary, and
caller-scoped dialog-result rules. The app has one common Nav3 stack whose only cold entry is `LaunchGate`. A dedicated Room query
observes one coherent launch snapshot: state, activity-claiming onboarding-protocol cardinality,
the sole valid active protocol, and its latest configuration. Missing, malformed, wrong-status/slot,
or non-0/1 protocol data is invalid,
not a fresh state. A pure domain resolver neither writes state nor creates protocols. It returns
Welcome, a canonical typed checkpoint path, Today only for the exact completed draft/configuration
match, or Setup recovery/blocking as appropriate.

Snapshot v3 stores only validated complete post-gate paths. LaunchGate is never serialized; v2 and
malformed data are cleared to an in-memory LaunchGate fallback. A valid stored stack is admitted
only after a Today decision. External links queue while the gate resolves and are honored only at
Today; invalid links reset the current canonical root.

## Alternatives

- Restoring Gallery first was rejected because it violates durable onboarding eligibility.
- Combining state and active-protocol flows was rejected because their emissions can be from
  different transactions.
- Native startup branching was rejected because navigation ownership must stay common.

## Consequences

Android and iOS share checkpoint resume, restoration, deep-link, and root-back behavior. Existing
experiment navigation remains beneath Today. Follow-up onboarding screen owners replace neutral
checkpoint contours without changing the stack or route grammar.

## Migration/rollback

v2 payloads are intentionally incompatible and cleared. No Room schema or migration changes are
needed. An older binary rejects v3 as an unknown version under its own existing safe-root policy;
it never interprets a v3 payload as v2.

## Verification

Verify fresh, checkpoint, completed, invalid, stale-route, and deferred-link decisions; compile
Android and iOS production targets; and preserve existing Today-descendant navigation behavior.

## Related docs

- [Presentation and navigation](../../.agents/docs/03-presentation-navigation.md)
- [Offline-first policy](../../.agents/docs/04-data-offline-first.md)
- [Onboarding User Flow](../product/onboarding-user-flow.md)
- [ADR 0002](0002-navigation3-ios-restoration-runtime.md)
- [ADR 0004](0004-onboarding-v2-offline-storage.md)
