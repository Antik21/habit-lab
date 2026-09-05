# Libraries, versions, and licenses

<!-- fact-owner: dependency-policy -->
<!-- canonical-signature: dependency-policy-v1 -->

`gradle/libs.versions.toml` is the version catalog and canonical dependency declaration point. Current production families are Compose Multiplatform/Material 3, Kotlin coroutines/serialization/datetime/immutable collections, Koin, Orbit MVI, JetBrains Navigation 3 and lifecycle, Room 3/KSP, and BundledSQLite. Exact toolchain versions are owned by [stack and toolchain](01-stack-toolchain.md).

Before adding or upgrading a dependency:

1. Confirm an existing library or platform API cannot meet the requirement.
2. Check Kotlin Multiplatform target support, minimum OS/SDK requirements, transitive graph, maintenance, and compatibility with the pinned Kotlin/Compose/AGP versions.
3. Record the exact version in the catalog; do not place ad-hoc versions in module scripts.
4. Review license and notices for both direct and bundled/transitive artifacts. Do not add code with an incompatible or unknown distribution license.
5. Run target compilation plus the relevant gate selected by [testing and verification](07-testing-verification.md). Update this owner when the approved stack changes.

Do not introduce parallel DI, navigation, database, serialization, async/state, or UI stacks without an accepted ADR. Replacing a library or changing a provider/privacy boundary also requires one where [ADR policy](../../docs/adr/README.md) says so.

<!-- fact-owner: planned-capabilities -->
<!-- canonical-signature: planned-capabilities-v1 -->

Health Connect, HealthKit, Ktor/network sync, DataStore/persistent theme settings, and analytics are planned/not yet implemented. The current theme repository is observable but process-local. There is no analytics provider or privacy boundary, remote data source, or health-data adapter. A task may not describe these as current capabilities merely because the product direction mentions them. Introduce each only with explicit requirements, boundary ownership, dependency/license review, target verification, and any required ADR.
