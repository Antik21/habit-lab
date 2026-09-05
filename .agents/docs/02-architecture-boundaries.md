# Architecture boundaries

<!-- fact-owner: package-boundaries -->
<!-- canonical-signature: package-boundaries-v1 -->

`shared` currently uses packages, not feature Gradle modules. The root `com.denis.habitlab.shared` package is reserved for package-only files; common Kotlin code below it must belong to `app`, `core`, `data`, `di`, `domain`, or `presentation`. `./gradlew :shared:checkArchitectureBoundaries` enforces these exact directions:

| package | may depend on |
| --- | --- |
| `core` | nothing in another shared layer |
| `domain` | `core` |
| `data` | `core`, `domain` |
| `presentation` | `core`, `domain` |
| `app` | `presentation` |
| `di` | `app`, `core`, `data`, `domain`, `presentation` |

`core` and `domain` remain pure common Kotlin without UI, DI, database, network, or native SDK APIs. Presentation cannot reference DAO/DataSource types or native/infrastructure APIs. Its explicitly named `*ViewModel.kt` files may use common `androidx.lifecycle.ViewModel` and `viewModelScope` only. Koin references belong in `di`, except the app-owned composition boundary `shared/app/NavigationEntryKoinComposition.kt`. ViewModels receive constructor dependencies and never resolve Koin themselves.

## Ownership

<!-- fact-owner: model-ownership -->
<!-- canonical-signature: model-ownership-v1 -->

- Domain owns business entities, value objects, repository/observer contracts, typed storage results/failures, and interactors.
- Data owns Room entities/DAOs/data sources, repository implementations, infrastructure-exception translation, and explicit data↔domain mappers.
- Presentation owns immutable UI models/state, actions/effects, UI mappers, ViewModels, screens, and automation identifiers.
- App owns composition, the common navigation stack, route serialization/validation, platform capability interfaces, and delivery coordination.
- Native source sets/hosts own OS objects and implement narrow common capabilities. Native types stop at that boundary.

An app route may carry a domain value object as a typed identifier/argument but never a domain entity or UI snapshot. Crossing or reversing these ownership rules requires an [ADR](../../docs/adr/README.md).
