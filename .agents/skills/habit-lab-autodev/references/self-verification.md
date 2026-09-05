# Self-verification

Verification judges the frozen checklist; it may not rewrite the standard it evaluates.

## Evidence matrix

For every acceptance assertion, record:

- assertion and affected ownership boundary;
- requested platform(s);
- source revision and explicit virtual target;
- build/deploy prerequisite;
- navigation path and semantic IDs used;
- observed result and artifact locations;
- regression check and independent-review disposition.

An assertion passes only when its required platforms have direct, attributable evidence. Cross-platform common code normally requires evidence on both requested platforms; one platform cannot proxy for another. Build success is not runtime behavior evidence, and runtime evidence does not replace required static or unit checks.

The AutoDev report stores only its orchestration metadata and evidence map under `.autodev/`. It references canonical tool artifacts at their owner-defined locations. For Maestro, preserve and cite `build/maestro/<run-id>/<platform>` in place; do not copy it into AutoDev storage or alter the runner's evidence contract.

## Gate

Select commands and checks from the root [testing policy](../../../docs/07-testing-verification.md) and [toolchain catalog](../../../docs/01-stack-toolchain.md). The gate passes only when:

- every frozen assertion is evidenced and passes;
- required owner builds, tests, static checks, and documentation checks pass;
- the declared regression boundary passes;
- independent review has no unresolved justified finding;
- evidence is readable, attributable, secret-free, and preserved;
- scratch cleanup completed without affecting user or concurrent-run state.

The gate is a review contract, not an executable implementation in this scaffold. Missing automation must remain visible as a blocker or limitation; do not create an ad hoc gate and do not edit the skill, checklist, test expectations, or scoring rules to obtain a pass.

## Reporting

Report the terminal outcome defined in [the autonomous loop](autonomous-loop.md). Include changed source areas, each checklist result, platforms and virtual target identifiers, checks run, evidence locations, review disposition, cleanup status, and remaining risks.

Gate failure forbids both `success` and Draft PR creation. A Draft PR also requires the user's Git/GitHub authorization and repository account rules. Even after success, stabilization and merge remain outside this skill and require a separate explicit command.

Do not write unverified observations into durable memory. Promote a lesson only after it satisfies the [curated lessons contract](../memory/lessons.md); navigation nodes must follow the [navigation schema](../memory/nav/_SCHEMA.md).
