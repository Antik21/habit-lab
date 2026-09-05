# Navigation memory node schema

Future reviewed navigation nodes belong in this directory as narrowly scoped Markdown records. Each node must contain:

- stable node name and destination/purpose;
- source revision or contract version last verified;
- valid starting state and required deterministic fixture/state;
- semantic automation ID sequence for app-owned interactions;
- expected semantic state ID at completion;
- supported platform(s) and any platform-owned system gesture;
- one or more evidence records from a successful emulator/simulator run;
- known failure modes and an invalidation trigger;
- verification date and reviewer.

Each evidence record contains the platform, source revision, terminal success and passing-gate status, exact emulator/simulator target identifier, relevant target/toolchain configuration, and a repository-relative canonical owner-defined artifact location. Maestro evidence uses `build/maestro/<run-id>/<platform>`. The record must be attributable and secret-free.

For every listed supported platform, the node must contain a separate terminal-success, passing-gate evidence record. Do not list any platform without its own qualifying evidence.

Nodes must not contain localized labels, runtime/user values, coordinates for app controls, credentials, absolute machine paths, timing guesses presented as guarantees, or copied owner policy. The only system-gesture coordinate exception must reference [ADR 0003](../../../../../docs/adr/0003-maestro-cross-platform-ui-automation.md).

A node is guidance, not an executable flow or a claim that the current screen is reachable. Validate all IDs against the production automation contract before use. Creation and maintenance of actual navigation memory nodes belongs to its follow-up phase; this schema does not preimplement them.
