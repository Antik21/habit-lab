# Feature playbook

## Freeze acceptance and blast radius

Translate each requested capability into an observable acceptance assertion before editing. Record user entry points, state transitions, persistence/restoration needs, failure and empty states, navigation/back behavior, accessibility/automation IDs, and the platforms that must prove it.

Map the blast radius through the root router: shared domain/data/presentation/app ownership, Android/iOS adapters, schemas, configuration, and documentation. Follow only affected routes and avoid unrelated cleanup or infrastructure.

## Implement and prove

Implement the smallest coherent vertical slice. New or substantially changed product UI remains common and follows the Compose rule. App interactions use semantic automation IDs on both bridges.

Capture direct evidence for every acceptance assertion on every requested emulator/simulator. Add targeted checks at changed ownership boundaries and run the required owner gate. Exercise adjacent entry, exit, back, retry/error, and persistence behavior selected by the blast-radius analysis.

Cross-platform success requires comparable Android and iOS evidence when both are requested. A build on one platform or visual similarity without semantic assertions is insufficient.
