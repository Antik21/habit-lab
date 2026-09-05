# Data and offline-first policy

<!-- fact-owner: room-storage -->
<!-- canonical-signature: room-storage-v1 -->

The current offline-first source is a shared Room 3.0.2 database using BundledSQLite. Version 1 is committed at [`shared/schemas/com.denis.habitlab.shared.data.local.HabitLabDatabase/1.json`](../../shared/schemas/com.denis.habitlab.shared.data.local.HabitLabDatabase/1.json). There is no remote source or conflict/sync layer.

`experiments` stores ID, name, status, timestamps, and an `active_slot` constraint allowing one active record and multiple drafts. `daily_check_ins` uses experiment ID plus local date, cascades on experiment deletion, and distinguishes factual occurrence time from submission `recordedAt`. `PERFORMED` requires occurrence fields and a matching local date; `SKIPPED` has no occurrence. Mappers reject invalid persisted combinations.

Presentation calls interactors and observes domain `Flow` contracts. Write repository contracts and focused observers live in domain; Room implementations, data sources, entities, DAO, transaction boundaries, and mappers live in data. A write that spans invariants is one local transaction. Infrastructure exceptions become typed failures while coroutine cancellation is rethrown. UI and routes never receive Room types.

Debug startup asynchronously seeds a deterministic dataset only when empty while a readiness gate keeps observers loading. Release neither seeds nor exposes reset. Debug reset clears dependents and replaces the seed in one transaction. Schema JSON changes must be committed. Version 1 is unencrypted and has no migration policy; changing schema/migration/encryption/storage policy requires an [ADR](../../docs/adr/README.md).

Platform path, backup, protection, and dispatcher details are owned by [Android](05-platform-android.md) and [iOS](06-platform-ios.md). Current and planned source status is owned by [libraries and licenses](08-libraries-licenses.md). Historical implementation evidence is in [DEN-11](../../docs/data/den-11-room-offline-first.md).
