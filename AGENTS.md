# Habit Lab agent router

Keep this file lean. Read only the routed policy needed for the change, plus linked owner documents.

## Hard rules

- Preserve user changes and staged files; do not broaden the requested scope.
- Common product UI belongs in `shared/src/commonMain`; native code is an adapter boundary.
- New or substantially changed Compose screens must follow [the Compose screen rule](.agents/rules/compose.md).
- Routes contain typed identifiers/arguments, never domain entities, results, or `ViewState`.
- Do not introduce a parallel navigation, persistence, networking, DI, or analytics stack.
- Architecture-changing decisions follow [the ADR policy](docs/adr/README.md).

## Bootstrap and verification

Start with [routing and documentation governance](.agents/docs/00-routing.md). Toolchain and exact build commands are in [stack and toolchain](.agents/docs/01-stack-toolchain.md); verification selection is in [testing and verification](.agents/docs/07-testing-verification.md). Run `./gradlew checkDocumentation` after documentation changes.

## Request routes

Choose the matching row and read only its 1–3 linked documents.

| key | request → documents |
| --- | --- |
| screen | New/substantially changed screen → [Compose rule](.agents/rules/compose.md) · [presentation policy](.agents/docs/03-presentation-navigation.md) · [recipe](.agents/docs/09-common-cases.md) |
| route | Route, deep link, back, or restoration → [navigation policy](.agents/docs/03-presentation-navigation.md) · [recipe](.agents/docs/09-common-cases.md) |
| dialog | Dialog or typed result → [navigation policy](.agents/docs/03-presentation-navigation.md) · [recipe](.agents/docs/09-common-cases.md) |
| repository-room | Domain, repository, Room, or offline-first → [boundaries](.agents/docs/02-architecture-boundaries.md) · [data policy](.agents/docs/04-data-offline-first.md) · [recipe](.agents/docs/09-common-cases.md) |
| android-adapter | Android host/native adapter → [boundaries](.agents/docs/02-architecture-boundaries.md) · [Android policy](.agents/docs/05-platform-android.md) · [recipe](.agents/docs/09-common-cases.md) |
| ios-adapter | iOS host/native adapter → [boundaries](.agents/docs/02-architecture-boundaries.md) · [iOS policy](.agents/docs/06-platform-ios.md) · [recipe](.agents/docs/09-common-cases.md) |
| dependency-toolchain | Dependency or toolchain → [toolchain](.agents/docs/01-stack-toolchain.md) · [libraries/licenses](.agents/docs/08-libraries-licenses.md) |
| tests-verification | Tests, CI, or release verification → [testing policy](.agents/docs/07-testing-verification.md) |
