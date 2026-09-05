# Bug playbook

## Reproduce first

Turn the report into a deterministic observation: preconditions, exact path, expected behavior, actual behavior, affected platform(s), and a stable assertion. Give the reproduction criterion a stable scenario key. Attempt reproduction on every requested platform at the manifest's initial source revision. Record immutable failing `baseline` evidence only where the failure actually reproduces; use the checklist gate's `observe` command to preserve a structured `not-reproduced`, `environment-blocked`, or `diagnostic-error` result instead of fabricating a failure.

If a required platform does not reproduce, inspect task facts, build/source revision, environment parity, persisted state, and selector contract. Retry only when a concrete mismatch justifies it, appending each material diagnostic through `observe`. If the reported behavior still cannot be reproduced there, stop speculative implementation and report `blocked` or `failed`; its terminal report retains those observations. Do not guess a fix from symptoms alone.

## Fix and prove

Identify the owning boundary and smallest causal change. Add a regression check at that boundary when authorized and appropriate. Demonstrate that the original reproduction now passes on the required emulator/simulator set, then exercise adjacent behavior that shares the changed path.

Record separate passing `fixed` evidence for the same criterion/platform/scenario key at a checked revision that is different from and descended from the immutable initial revision; never overwrite or reuse its failing artifact. The gate revalidates this provenance on every status and finish. Success requires that comparable pair on every required platform, a passing frozen checklist and owner gate, and independent review. A disappearing symptom under incomparable conditions is not proof.
