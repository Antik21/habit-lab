# Architecture decision records

<!-- fact-owner: adr-policy -->
<!-- canonical-signature: adr-policy-v1 -->

ADRs record durable architectural decisions. Use four-digit sequence numbers and a short kebab-case title: `NNNN-title.md`. The author owns an ADR until acceptance; after acceptance, the current maintainer of the affected owner document owns follow-up and superseding work.

## Status and lifecycle

Use one status: `Proposed`, `Accepted`, `Rejected`, `Deprecated`, or `Superseded by ADR NNNN`. Never rewrite an accepted decision to hide history. A replacement ADR links the old record, and the old record changes only its status/superseding link. Numbering is monotonic; gaps are allowed, reuse is not.

An ADR is required when changing:

- Gradle module or shared package dependency boundaries;
- navigation framework/stack ownership or persisted route-format policy;
- database/schema/migration/encryption/storage policy;
- domain/data/presentation/app model ownership;
- analytics provider or privacy boundary;
- common/native ownership boundary.

A new backward-compatible route or ordinary implementation within an accepted policy does not require an ADR. It still requires production changes, verification, and updates to the owning policy document and `AGENTS.md` route when request selection changes.

Copy [the template](template.md), add it to the index below, and link the affected owner documents. Historical task records may support context but do not replace a decision or current owner policy.

## Index

- [ADR 0001: Temporary iOS leading-edge back adapter](0001-navigation3-ios-edge-adapter.md) — Accepted
- [ADR 0002: Common route-only navigation restoration](0002-navigation3-ios-restoration-runtime.md) — Accepted
- [ADR 0003: Maestro cross-platform UI automation](0003-maestro-cross-platform-ui-automation.md) — Accepted
