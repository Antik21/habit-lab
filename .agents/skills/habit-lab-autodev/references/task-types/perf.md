# Performance playbook

## Establish the baseline

Define one user-relevant metric, measurement boundary, workload/state, warm-up policy, sample count, device/runtime configuration, build mode, and acceptance threshold before optimization. Capture the baseline on each requested emulator/simulator without changing those conditions.

If the environment cannot produce a stable, attributable baseline, report `blocked` or `failed`; do not optimize from anecdotes, a single incomparable run, or unrelated profiler output.

## Change and compare

Locate the owning bottleneck with measurements, then make the smallest causal production change. Keep functional acceptance and regression assertions frozen alongside the performance target.

Compare before and after using the same source-independent workload, build mode, virtual target class/runtime, state, metric definition, instrumentation, and aggregation. Record raw evidence plus the summarized delta. Explain any unavoidable variance or environment change; if it invalidates comparison, do not claim improvement.

Success requires the agreed metric/threshold, functional evidence on every requested platform, the owner gate, and independent review. Do not trade correctness, persistence, accessibility, or cross-platform behavior for an unproven metric gain.
