# iOS simulator playbook

Read the root [iOS adapter policy](../../../../docs/06-platform-ios.md) only when iOS host or adapter code is affected. Use the [Compose rule](../../../../rules/compose.md) for new or substantially changed common screens.

- Require macOS, the needed Xcode/runtime, and an explicit iOS simulator identifier; physical devices are outside this skill.
- Before any iOS Gradle, build, deployment, test, simulator, or UI command, resolve the selected contributor Xcode/toolchain and require it to pass the current pre-command Xcode guard in the repository-owned [`ui-tests/maestro/run.sh`](../../../../../ui-tests/maestro/run.sh). The [toolchain catalog](../../../../docs/01-stack-toolchain.md) remains the source of general compatibility and toolchain context; the contributor Xcode need not numerically equal the exact CI pin. If the runner guard or applicable local compatibility requirements are not satisfied, report `blocked` before running any iOS command.
- Select framework/Xcode, simulator-test, and UI commands from the [toolchain catalog](../../../../docs/01-stack-toolchain.md) according to the [verification policy](../../../../docs/07-testing-verification.md).
- Keep product UI and navigation shared. SwiftUI/UIKit code remains a narrow native bridge.
- Use the shared semantic automation ID contract exposed through the iOS accessibility bridge. Do not use localized/user/runtime text or coordinates for app controls.
- The sole coordinate allowance is the repository-owned native iOS edge-back system gesture governed by [ADR 0003](../../../../../docs/adr/0003-maestro-cross-platform-ui-automation.md).
- Attribute build, install, launch, assertions, logs, screenshots, and reports to the selected simulator/runtime and source revision.

On a non-macOS host or without the required simulator/runtime, report `blocked`; an Android pass cannot substitute for requested iOS evidence. Do not overclaim minimum-runtime compatibility from a different simulator runtime.
