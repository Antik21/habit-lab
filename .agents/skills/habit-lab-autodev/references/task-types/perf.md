# Performance playbook

## Establish the baseline

For each requested platform, define a user-relevant metric, measurement boundary, workload/state, warm-up policy, sample count, device/runtime configuration, build mode, acceptance threshold, and stable scenario fingerprint before optimization. Capture a separate `baseline` event through the [checklist gate](../checklist-gate.md) at the immutable manifest initial revision on every requested emulator/simulator without changing those conditions.

If the environment cannot produce a stable, attributable baseline, report `blocked` or `failed`; do not optimize from anecdotes, a single incomparable run, or unrelated profiler output.

## Change and compare

Locate the owning bottleneck with measurements, then make the smallest causal production change. Keep functional acceptance and regression assertions frozen alongside the performance target.

On every requested platform, compare its before/after metric using the same scenario fingerprint, source-independent workload, build mode, virtual target class/runtime, state, metric name/unit, instrumentation, aggregation, sample count, and threshold contract. Record separate `candidate` or `repeat` evidence at a later descendant revision. The gate computes the directional absolute or percent delta from the immutable baseline and accepts the recorded result only when it matches the threshold; baseline-only or self-asserted evidence never passes the criterion. Explain unavoidable variance; if it invalidates a comparison, do not claim improvement there.

Success requires the before/after comparison to meet its threshold on every requested platform, functional evidence on every requested platform, the owner gate, and independent review. Do not trade correctness, persistence, accessibility, or cross-platform behavior for an unproven metric gain.
