# ADR 0004: Onboarding v2 offline storage

- Status: Accepted
- Owner: Habit Lab maintainers
- Date: 2026-09-07
- Supersedes: none
- Superseded by: none

## Context

The v1 Room database stores only legacy experiments and daily check-ins. First-run onboarding needs
durable typed answers, a fixed offline catalog, health-data status without native-provider types,
and a future-safe correlation between a setup draft and an active protocol configuration. This is a
database and storage-policy change governed by the [offline-first owner](../../.agents/docs/04-data-offline-first.md).

## Decision

Move `HabitLabDatabase` to v2 with a shared explicit non-destructive v1→v2 migration. Keep the v1
tables unchanged. Add a seeded catalog, one onboarding-state aggregate, one-active onboarding
protocol records, and append-only protocol configuration history. The common builder registers the
migration and a creation callback that idempotently seeds the exact same ordered catalog on Android,
iOS, and test databases.

The state aggregate makes a missing context, a confirmed empty context set, and an exclusive
`not-sure-yet` answer distinct. Unknown stored catalog IDs are observable invalid persistence, not
a default. Health persistence retains independent provider, access, visible-record, coverage,
freshness, suitability, and explicit-manual axes; it contains no provider, permission, metric
mapping, threshold, or sufficient-coverage conclusion. Those decisions remain with DEN-37.

An active-slot unique index permits at most one onboarding protocol. A setup-draft reference is
accepted only after durable upstream answers and health state; it is first-write or same-attempt,
strictly increasing revision, with an exact retry idempotent. Initial creation requires the `SETUP`
checkpoint and that exact stored reference, then inserts one active protocol plus immutable v1
configuration and persists `Completed` in the same transaction. Its matching completed retry reads
back as idempotent; a nonmatching active protocol is a conflict. Later substantive configuration
writes require that same current draft and active protocol, append a higher revision for its attempt,
persist `Completed` atomically, and treat the exact latest retry as idempotent. No active protocol
is created by catalog seeding.
Setup UI, launch-gate reconciliation, ranking, and experiment creation stay with their named
follow-up owners.

## Alternatives

- Reusing `experiments` for onboarding was rejected: it would overload legacy state and mix typed
  template IDs with `ExperimentId`.
- Persisting native health results was rejected because native types must stop at platform adapters.
- Destructive migration or release startup seeding was rejected because upgrades must retain legacy
  rows and catalog availability cannot depend on debug behavior.

## Consequences

Storage remains entirely local and available across relaunch. Eligibility is its own confirmation
command: downstream writes reject an unconfirmed persisted state without mutation, while a repeated
confirmation is a no-write idempotent result. One aggregate transaction makes observer emissions
pre-state or full post-state. The catalog is intentionally small and closed; future catalog or
health-plan expansion requires a new migration and decision review.

## Migration/rollback

The v1→v2 migration only creates v2 tables, indexes, initial state, and idempotent catalog rows; it
does not alter legacy rows. A failed migration leaves Room's transaction uncommitted. A binary
rollback to v1 is not supported after v2 opens because v1 cannot read added tables; release rollback
therefore requires a compatible v2 binary or a forward corrective migration, never destructive
fallback.

## Verification

Verify the committed v1/v2 schemas, migration retention of experiments/check-ins, idempotent seed
order and exactly three manual templates, reopen persistence, single-active constraint, append-only
configurations, invalid-ID observation, and atomic Flow behavior. Run `:shared:check` plus target
compilation after schema changes.

## Related docs

- [Offline-first policy](../../.agents/docs/04-data-offline-first.md)
- [Architecture boundaries](../../.agents/docs/02-architecture-boundaries.md)
- [Onboarding scope](../product/onboarding-first-run-scope.md)
- [Onboarding User Flow](../product/onboarding-user-flow.md)
