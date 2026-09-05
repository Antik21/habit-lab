# Android emulator playbook

Read the root [Android adapter policy](../../../../docs/05-platform-android.md) only when Android host or adapter code is affected. Use the [Compose rule](../../../../rules/compose.md) for new or substantially changed common screens.

- Require an explicit Android emulator identifier and confirm the target is not a physical device.
- Select build, device-test, and UI commands from the [toolchain catalog](../../../../docs/01-stack-toolchain.md) according to the [verification policy](../../../../docs/07-testing-verification.md).
- Keep product UI and navigation in shared common code. Android remains a narrow host/capability bridge.
- Use the shared semantic automation ID contract exposed through the Android bridge. Do not replace missing IDs with labels, runtime values, list positions, or coordinates.
- Attribute install, launch, assertions, logs, screenshots, and reports to the selected emulator and source revision.
- Exercise the affected acceptance path and Android-specific regression boundary, including system behavior only when relevant.

An unavailable, incompatible, ambiguous, or non-virtual target blocks Android evidence. Do not silently choose another connected device. Report the exact missing prerequisite and retain safe diagnostics.
