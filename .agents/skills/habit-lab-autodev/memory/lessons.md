# Curated lessons contract

Durable lessons contain only repeatable, verified project facts that materially improve later AutoDev decisions. They are not run logs, task summaries, guesses, personal machine inventory, credentials, or substitutes for owner policy.

A lesson is admitted only after:

1. the source run ended in terminal `success` with its gate passing;
2. the behavior is supported by preserved, attributable, secret-free evidence;
3. source or owner documentation confirms the causal interpretation;
4. independent review finds it reusable and non-conflicting;
5. its platform/runtime scope and invalidation condition are explicit.

Each lesson records a concise decision, scope, evidence reference, source/policy reference, verification date, and invalidation trigger. Remove or revise it when the source contract or owning policy changes. Never promote a workaround that bypasses semantic IDs. Never promote a purported fact that depends on local secrets or device identifiers, transient availability, or an unverified failure theory.

Observations from `blocked`, `failed`, or `partial` runs remain only in their run reports and are not lesson candidates. A successful autonomous run may propose a candidate in its report, but it must not automatically rewrite this file or patch the skill, checklist, evaluator, or gate. Curation is a deliberate reviewed change.

The progressive-memory helper starts from this policy and the navigation index, not a personal run log. Its local classifications and five-run consolidation advice are discovery aids only. A correction needs two distinct terminal-success, independently reviewed runs before explicit tracked JSON storage; a third recurrence adds lint/test/helper advice. Any post-terminal instruction record is one separately validated normal commit and never changes frozen core paths.
