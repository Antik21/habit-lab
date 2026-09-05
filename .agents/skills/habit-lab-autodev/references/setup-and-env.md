# Setup and environment

## Precedence and locality

Read authoritative commands and supported targets from the repository [toolchain catalog](../../../docs/01-stack-toolchain.md). Local runtime configuration, if needed, lives at `.autodev/local_config.json` and follows [`local_config.example.json`](../local_config.example.json). Treat paths in that file as repository-relative. Keep AutoDev-owned orchestration state below `.autodev/state` and its metadata/reports below `.autodev/artifacts`; canonical output produced by an existing tool remains at that tool's owner-defined path.

Before reading or creating runtime data, resolve the repository root and canonicalize the configured roots, including existing parent directories and symlinks. The configured values must be the exact relative paths `.autodev/state` and `.autodev/artifacts`, and their canonical targets must remain within the corresponding repository-root directories. Reject absolute paths, `..` traversal, alternate roots, or any symlink escape. Derive a unique run-owned child beneath each canonical root, verify containment again before every write or cleanup, and cleanup only those canonical run-owned children.

Path prechecks alone are insufficient because another process can replace a path between validation and use. Create run directories, write artifacts, and clean scratch with race-safe filesystem operations appropriate to the action: no-follow resolution, directory-handle-anchored relative operations, and exclusive creation where ownership or uniqueness is claimed. Fail closed if identity, type, containment, or ownership changes during an operation. The reviewed [frozen checklist gate](checklist-gate.md) implements its own state/evidence boundary; missing device adapters and reservation mechanisms must still not be improvised.

Do not commit the actual local configuration. Do not replace nulls in the example with machine identifiers, absolute paths, credentials, environment contents, or device-pool sizing. A configured environment-variable key names a lookup; it is not the secret or device identifier itself.

Device selection is fail-closed for each platform requested by the user. Exactly one source must be non-null: `emulatorId` XOR `emulatorIdEnv` for requested Android, and `simulatorId` XOR `simulatorIdEnv` for requested iOS. An environment-key source must resolve to exactly one non-empty identifier. For a requested platform, reject both-set, neither-set, empty, unresolved, or ambiguous selection; do not apply precedence, discover a fallback target, or silently choose a connected device.

Do not read, use, or validate the configuration section for a platform the user did not request. Its presence or populated values must not block a single-platform run.

## Preflight inventory

For each requested platform, establish:

- the explicit emulator or simulator identifier;
- that its runtime is supported by the relevant project target;
- that required SDK, build tool, and UI runner are available;
- that the selected target is virtual, reachable, and not ambiguously shared;
- where AutoDev orchestration state/report metadata and tool-owned canonical evidence for this run will be written.

If a requested iOS run cannot execute because the host is not macOS, Xcode or the simulator runtime is unavailable, or no explicit simulator can be selected, report `blocked`. Android-only evidence cannot satisfy an Android+iOS request.

## Security and isolation

Never print secrets or dump broad environment/configuration state. Read only named values needed for the run and redact sensitive substrings from logs and reports. Do not put credentials in command arguments, filenames, screenshots, memory, or source-controlled files.

Use unique AutoDev run directories beneath the validated roots. Do not reuse another active run's target, orchestration data, or canonical tool artifacts. Cleanup removes only explicitly ephemeral, run-owned scratch and releases only reservations acquired by the current run; it must not touch app data/state, another run's data, or user-owned emulator/simulator state.

This phase defines configuration and safety only. The checklist gate records an intentionally empty lease list when no reservation adapter exists. Device reservation, adapter execution, and credential providers remain absent and must be reported rather than improvised.
