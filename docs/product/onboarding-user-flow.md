# Onboarding User Flow

<!-- fact-owner: onboarding-user-flow -->
<!-- canonical-signature: onboarding-user-flow-v1 -->

**Статус:** канонический flow для [DEN-24](https://linear.app/denis-apps/issue/DEN-24) с [Linear document](https://linear.app/denis-apps/document/onboarding-user-flow-adaf813af542); он уточняет реализацию [DEN-22](https://linear.app/denis-apps/issue/DEN-22) поверх product contract [DEN-23](https://linear.app/denis-apps/issue/DEN-23). Документ владеет только последовательностью, ветвлениями и семантикой переходов/checkpoint; продуктовый scope и словари принадлежат [контракту первого запуска](onboarding-first-run-scope.md) и не повторяются здесь.

Исследовательские первоисточники: [00 — карта](https://linear.app/denis-apps/document/00-karta-issledovaniya-i-marshruty-dlya-agentov-dcf08b50c7a8), [01 — стратегия, UX и MVP](https://linear.app/denis-apps/document/01-produktovaya-strategiya-ux-i-mvp-a0992380a8d4), [02 — health data](https://linear.app/denis-apps/document/02-integracii-health-data-ffe39fdcd683), [03 — качество данных](https://linear.app/denis-apps/document/03-metriki-normalizaciya-i-kachestvo-dannyh-a56009dff4e5), [04 — эксперименты](https://linear.app/denis-apps/document/04-personalnye-eksperimenty-i-analitika-04df95a2fba6) и [06 — приватность](https://linear.app/denis-apps/document/06-privatnost-bezopasnost-i-otkrytye-riski-8a5fc7ed172a). Архитектурный контекст остаётся у [DEN-10](https://linear.app/denis-apps/issue/DEN-10) и [DEN-11](https://linear.app/denis-apps/issue/DEN-11); этот flow не меняет их владельцев.

## Последовательность и checkpoint

Канонический путь: **Launch Gate** (невидимое решение) → **Welcome** → **Outcome** → **Context** → **Recommended protocols** → **Health explanation** → platform permission **или** manual → **Status/coverage** → **Setup** → **Today**.

Launch Gate — [DEN-33](https://linear.app/denis-apps/issue/DEN-33) implementation, не Screen Spec: новый → Welcome, resume draft → checkpoint, completed → Today только после подтверждения ровно одного active experiment. Inconsistent completed checkpoint: [DEN-28](https://linear.app/denis-apps/issue/DEN-28) Setup восстанавливает 0 active с валидным draft, блокирует >1, иначе откатывает к ближайшему upstream. Setup recovery не destination; Today после Setup — [DEN-41](https://linear.app/denis-apps/issue/DEN-41).

Welcome требует явного 18+ confirmation до product answers. Fresh confirmation локальна до CTA; CTA сохраняет eligibility/checkpoint. Decline/revoke в confirmed re-entry атомарно очищает eligibility и downstream checkpoint/answers/draft, затем открывает terminal Welcome; Profile/Experiment ещё нет. Checkpoint хранит step, typed IDs и draft, не `UiState`; relaunch идёт через Launch Gate с перечитыванием и проверкой достижимости.

Manual идёт в Status/coverage с явным data plan, не в Setup. Permission, provider и coverage независимы: missing не zero, iOS empty read не denied. Back не отзывает system permission.

## Реестр Screen Spec

Реестр ссылается на attached Linear documents; issue URL временный только для ещё не созданного spec. Каждый document добавляет backlink на этот Flow.

| Screen Spec | Owner / document | Обязательные outgoing transitions |
| --- | --- | --- |
| Welcome | [DEN-26](https://linear.app/denis-apps/document/onboarding-0320-screen-spec-welcome-2698906cbdb6) | confirmed → Outcome; declined → terminal safe exit/info внутри Welcome; root Back → host exit; relaunch → Launch Gate. |
| Outcome | [DEN-25](https://linear.app/denis-apps/document/onboarding-0420-screen-spec-vybor-osnovnogo-rezultata-01a8c5eb01b3) | valid или изменённый Goal → Context для подтверждения; missing/invalid/unresolved → controlled stay; Back → Welcome. |
| Context | [DEN-27](https://linear.app/denis-apps/document/onboarding-0520-screen-spec-kontekst-i-tekushie-privychki-16f4aaa9af09) | confirmed non-empty Context, включая только `not-sure-yet`, либо confirmed empty → Protocols/recompute; missing/invalid/unresolved → controlled stay; Back → Outcome. |
| Recommended protocols | [DEN-29](https://linear.app/denis-apps/issue/DEN-29) | selected или изменённый template → Health explanation; loading/error/empty stays; error → Retry/recompute; empty → только Back → Context. |
| Health explanation | [DEN-30](https://linear.app/denis-apps/issue/DEN-30) | platform permission result → Status/coverage; manual → explicit manual plan in Status; Back → Protocols. |
| Status/coverage | [DEN-31](https://linear.app/denis-apps/issue/DEN-31) | fresh coverage для required MetricId/data plan или confirmed manual plan → Setup; refresh/retry stays Status; Back → Health explanation. |
| Setup | [DEN-28](https://linear.app/denis-apps/issue/DEN-28) | valid single save → Today; validation error/duplicate stays controlled; changed draft primary outcome → contextual permission loop. |

## Контракт переходов

| Источник | Условие или действие | Сохраняется / инвалидируется | Следующий экран |
| --- | --- | --- | --- |
| Launch Gate | new | Нет product answers, profile или experiment. | Welcome |
| Launch Gate | resume с достижимым checkpoint | Typed IDs и draft; актуальные данные перечитываются. | Сохранённый шаг |
| Launch Gate | resume/relaunch с недостижимым checkpoint | Совместимые upstream typed IDs и draft; incompatible downstream очищается, затем recompute/refresh. Setup со stale coverage напрямую не восстанавливается. | Ближайший достижимый upstream шаг |
| Launch Gate | completed marker и актуальное чтение подтверждает ровно один active experiment | Завершённый checkpoint; onboarding не проигрывается. | Today |
| Launch Gate | completed marker, 0 active и Setup draft валиден | Валидный draft сохраняется; downstream, несовместимый с актуальным чтением, очищается. | Существующий Setup в recovery mode |
| Launch Gate | completed marker, 0 active и валидного Setup draft нет | Incompatible downstream очищается, совместимые upstream typed IDs/draft сохраняются, затем recompute/refresh. | Ближайший достижимый upstream шаг |
| Launch Gate | completed marker и >1 active | Создание experiment блокируется до reconciliation; конкретный storage/repair/reconciliation mechanism остаётся TBD. | Существующий Setup в controlled blocking recovery state |
| Welcome | fresh confirmed CTA | Атомарно сохраняются eligibility confirmation и checkpoint. | Outcome |
| Welcome | fresh decline | Product data ещё нет. | Terminal safe exit/info внутри Welcome |
| Welcome | confirmed re-entry CTA без изменения state | Новая запись не нужна. | Outcome |
| Welcome | eligibility declined или explicit revoke в confirmed re-entry | Атомарно очищаются eligibility и весь downstream onboarding checkpoint/answers/draft; Profile/Experiment не созданы. | Terminal safe exit/info внутри Welcome |
| Welcome | revoke persistence failure | Остаётся last persisted confirmed state; retry, без terminal/forward transition. | Welcome |
| Welcome | root Back | Product data не создаются и не меняются. | Host/root exit |
| Outcome | Back | Persisted confirmed eligibility сохраняется. | Welcome в confirmed re-entry state |
| Outcome | Первый Goal или persisted Goal изменён | Атомарно сохранить Goal, eligibility и независимый Context; очистить ranking/result, template, app-owned health/data plan/status/coverage и Setup draft; checkpoint → Context. | Context для подтверждения |
| Outcome | CTA: тот же persisted Goal | Goal/downstream — data no-op; checkpoint Context записать атомарно до navigation. Failure сохраняет Outcome checkpoint; controlled stay. Context подтвердить до recompute. | Context |
| Outcome | missing, invalid или unresolved Goal | Typed UI draft и validation/error сохраняются локально; подтверждённые Goal/Context/checkpoint не подменяются invalid draft, experiment не создаётся. | Outcome (controlled stay) |
| Context | Confirmed non-empty set: specific IDs либо только `not-sure-yet` | Атомарно сохранить Goal + Context; очистить ranking/result, template, app-owned health/data plan/status/coverage, Setup draft; checkpoint → Protocols; nav/recompute после commit. | Recommended protocols (recompute) |
| Context | Явно confirmed empty set через «Ничего из этого» | Та же полная атомарная invalidation с empty `Set<ContextId>`; empty не `ContextId`/`not-sure-yet`. | Recommended protocols (recompute) |
| Context | local unanswered, missing, invalid или unresolved | Typed draft и validation/error локальны; confirmed Goal/Context/checkpoint не подменяются, experiment не создаётся. | Context (controlled stay) |
| Context | persistence failure | Last persisted state + local draft; нет partial invalidation, nav или experiment. | Context (controlled stay) |
| Context | Back / system Back | Сохранить persisted eligibility/Goal/Context/downstream; отбросить только local Context draft. | Outcome |
| Recommended protocols | template выбран или изменён | Выбранный template; старые health/coverage и Setup draft очищаются. | Health explanation |
| Recommended protocols | ranking loading | Подтверждённые Goal и Context; template/experiment не создаются. | Recommended protocols (loading stay) |
| Recommended protocols | ranking error | Подтверждённые Goal и Context; transient error, template/experiment не создаются. | Recommended protocols (error stay) |
| Recommended protocols | Retry | Transient result очищается; ranking пересчитывается только из подтверждённых Goal и Context, template/experiment не создаются. | Recommended protocols (loading stay) |
| Recommended protocols | ranking empty | Empty result без forward; policy empty остаётся TBD, template/experiment не создаются. | Recommended protocols (empty stay) |
| Recommended protocols | Back из loading/error/empty | Подтверждённые Goal и Context сохраняются; transient ranking/result очищается, downstream не создаётся. | Context |
| Health explanation | запрос platform permission | Выбор template и draft; системный запрос не отменяется Back. | Системный permission → Status/coverage |
| Системный permission | full или partial access | Результат доступа, не вывод о coverage. | Status/coverage |
| Системный permission | unknown или no permission | Не подменять missing нулём или denied; остаётся путь manual. | Status/coverage |
| Health source | provider unavailable | Недоступность провайдера без вывода об отказе. | Status/coverage |
| Health read | permission, но NoData (включая iOS empty read) | Факт NoData, не denied; template/draft сохранены. | Status/coverage |
| Status/coverage | fresh coverage достаточен для required MetricId/data plan выбранного template | Проверенный snapshot coverage и draft; mapping остаётся TBD. | Setup |
| Status/coverage | insufficient или stale coverage | Template/draft; setup не открывается по неполным данным. | Status/coverage: refresh, retry, manual или Back |
| Status/coverage | refresh failure; retry | Последний известный status и draft; retry перечитывает данные. | Status/coverage |
| Health explanation или Status/coverage | manual выбран | Template/draft; создаётся и явно показывается manual data plan. | Status/coverage |
| Status/coverage | manual plan подтверждён | Явный manual plan и draft. | Setup |
| Любой onboarding Screen Spec, кроме root Welcome | Back | Upstream answers/checkpoint; permission не отзывается. | Предыдущий достижимый шаг |
| Любой незавершённый шаг, включая Welcome | relaunch | Checkpoint, typed IDs и draft, не `UiState`; данные перечитываются. | Launch Gate |
| Setup | validation failure | Draft и ошибки в текущем шаге; experiment не создаётся. | Setup |
| Setup | valid save, включая 0-active recovery | Перед save актуально перечитывается cardinality; атомарное создание разрешено только при всё ещё 0 active, постусловие — ровно 1 active experiment и completed checkpoint. | Today |
| Setup | recovery save видит nonzero cardinality или conflict | Новый experiment не создаётся; конфликт остаётся управляемым до reconciliation. | Setup (controlled stay) |
| Setup | duplicate submit | Уже созданный результат не дублируется; в recovery cardinality перечитывается перед save, точный механизм отложен. | Today или Setup с результатом |
| Setup | изменение draft primary outcome меняет требуемые категории | Новый draft primary outcome; устаревший coverage/selection очищается, выданное permission не отзывается. | Health explanation → Status/coverage → Setup |

## Открытые решения

Открыты только implementation details; действуют [инварианты scope](onboarding-first-run-scope.md#отложенные-решения-и-совместимость).

- формальный Goal/Context → ranking и template → required/optional `MetricId`;
- схема хранения progress/checkpoint и точный storage/repair mechanism inconsistent completed checkpoint; policy пустой рекомендации;
- шкалы, baseline, длительность и stop rules;
- поддерживаемые health source, OS и market;
- legal/privacy retention;
- точная реализация duplicate submit.
