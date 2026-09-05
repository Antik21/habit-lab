# Bug playbook

## Reproduce first

Turn the report into a deterministic observation: preconditions, exact path, expected behavior, actual behavior, affected platform(s), and a stable assertion. Reproduce on every platform claimed affected and preserve baseline evidence before editing.

If the bug does not reproduce, inspect task facts, build/source revision, environment parity, persisted state, and selector contract. Retry only when a concrete mismatch justifies it. If the reported behavior still cannot be reproduced, stop speculative implementation and report `blocked` or `failed` with the attempts and evidence. Do not guess a fix from symptoms alone.

## Fix and prove

Identify the owning boundary and smallest causal change. Add a regression check at that boundary when authorized and appropriate. Demonstrate that the original reproduction now passes on the required emulator/simulator set, then exercise adjacent behavior that shares the changed path.

Success requires before/after evidence tied to the same relevant conditions, a passing frozen checklist and owner gate, and independent review. A disappearing symptom under incomparable state or platform conditions is not proof.
