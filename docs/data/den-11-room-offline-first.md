# DEN-11: local Room persistence slice

## Ownership and schema

`shared` owns the complete v1 persistence implementation. The Room 3.0.2 schema is declared in
`shared.data.local`, processed by KSP2, and committed at
[`shared/schemas/com.denis.habitlab.shared.data.local.HabitLabDatabase/1.json`](../../shared/schemas/com.denis.habitlab.shared.data.local.HabitLabDatabase/1.json).
Android and iOS use that same generated database, DAO, entity set, transaction code, mapper, and
repository implementation.

`experiments` stores a general `Experiment` record with its ID, name, status and creation/update
recording timestamps. `active_slot` is `1` for `ACTIVE` and `NULL` for `DRAFT`; its unique index
enforces at most one active experiment transactionally while SQLite permits multiple draft NULLs.

`daily_check_ins` is keyed independently by experiment ID and `check_in_local_date`, and cascades
on experiment deletion. A `PERFORMED` outcome carries an `OccurredAt` factual instant/offset and
must have the same local date as the check-in. A `SKIPPED` outcome has no occurrence at all: both
nullable `occurred_*` columns are NULL. Every record has separate submission `recordedAt` UTC
instant/original offset/local date. Mappers reject invalid persisted status/active-slot pairs and
invalid outcome/nullability combinations; observers translate those failures into typed failure
states. Values cross data/domain only through explicit semantic mappers; presentation does not
access Room entities, DAOs, or data sources.

The common builder configures `BundledSQLiteDriver` and a platform query dispatcher. Android uses
`Dispatchers.IO` as Room's query coroutine context; the bundled Kotlin/Native coroutines artifact
does not publicly expose IO, so iOS uses its supported `Dispatchers.Default` actual. Android passes
an application-context database path and declares `android:allowBackup="false"`. Before Room is
opened, iOS creates a dedicated sandbox Application Support/HabitLab directory, excludes it from
iCloud/iTunes backup, and assigns `NSFileProtectionCompleteUntilFirstUserAuthentication`; the
database and its WAL/SHM sidecars inherit that directory policy. A failed Foundation policy call
fails bootstrap rather than opening a database with an unknown policy. No remote source, Ktor
client, or health API is initialized in this slice.

## Debug seed/reset contract

Only the debug host boundary sets `isDebugBuild` (`BuildConfig.DEBUG` on Android and `#if DEBUG` on
iOS). It receives a `HabitLabRuntime.debugDatabaseControl`; release runtimes return `null` and do
not register the controller or execute seed/reset work.

During debug graph creation, `seedIfEmpty()` completes before an experiment entry observer can
emit. It creates the deterministic active `daily-movement` / `Daily movement` and draft
`sleep-routine` / `Sleep routine` records plus one fixed check-in for each. The performed daily
movement fixture has an occurrence; the skipped sleep fixture deliberately has no occurrence
columns. It never replaces a nonempty database, so closing and reopening the debug app preserves
user writes. `resetAndSeed()` is the explicit QA operation: it clears dependent check-ins first,
clears experiments, and inserts the same fixed dataset in one Room transaction.

For Android exploratory QA, obtain the application instance and invoke its explicit
`debugDatabaseControl?.resetAndSeed()` from a debug-only harness or debugger coroutine. On iOS,
pause a Debug build in LLDB after launch and run
`expr -l Swift -- HabitLabDebugRuntimeHolder.shared.resetAndSeed { print($0) }`. The `#if DEBUG`
holder retains only the one `HabitLabRuntime` already created by `HabitLabApp`; it creates neither
a second runtime nor a second Koin graph and is absent from Release. Neither route adds a
destructive UI control.

## Persistence smoke procedure

1. Install a debug build and open either existing gallery row; its detail renders the Room-backed
   seeded display name. The fixed selector-backed rows appear only while their persisted supported
   IDs exist.
2. From a command caller, create a draft, edit it, and record a performed check-in with a matching
   occurrence date or a skipped check-in without an occurrence. The corresponding
   list/detail/check-in `Flow` updates from Room without a refresh.
3. Terminate and relaunch the app, then navigate to the draft using its valid `draft-*` ID. The
   record and check-in remain. A syntactically valid ID whose record was deleted safely returns the
   route to Gallery after the observer reports `Missing`.
4. Call the debug reset capability and repeat step 1. The two seeded records and their timestamps
   are identical on every reset. Relaunch once more without resetting to verify persistence.
5. Build a release variant and verify it neither seeds nor exposes a reset capability. A clean
   release database shows the gallery's existing empty state rather than fake demo rows; deleted or
   absent deep-linked records safely return to Gallery as missing-record behavior.
6. Simulate a Room read failure and verify the experiment route shows its explicit loading/error
   state; error content has no detail action buttons enabled.

External `habitlab://` deep links remain an exact allowlist for only `daily-movement` and
`sleep-routine`; internal `draft-*` IDs are accepted only by internal navigation and route restore.
Routes contain IDs only, never Room/entity/domain screen state.

## v1 limits

The v1 database is unencrypted. Android backup is disabled and iOS database files are excluded from
backup with the explicit protection policy above; neither is encryption. There is no migration
policy yet. Network sync, remote data sources, Health Connect, and HealthKit integration are
intentionally out of scope.
