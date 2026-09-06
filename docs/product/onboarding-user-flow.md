# Onboarding User Flow

<!-- fact-owner: onboarding-user-flow -->
<!-- canonical-signature: onboarding-user-flow-v1 -->

**Статус:** канонический flow для [DEN-24](https://linear.app/denis-apps/issue/DEN-24) с [Linear document](https://linear.app/denis-apps/document/onboarding-user-flow-adaf813af542); он уточняет реализацию [DEN-22](https://linear.app/denis-apps/issue/DEN-22) поверх product contract [DEN-23](https://linear.app/denis-apps/issue/DEN-23). Документ владеет только последовательностью, ветвлениями и семантикой переходов/checkpoint; продуктовый scope и словари принадлежат [контракту первого запуска](onboarding-first-run-scope.md) и не повторяются здесь.

Исследовательские первоисточники: [00 — карта](https://linear.app/denis-apps/document/00-karta-issledovaniya-i-marshruty-dlya-agentov-dcf08b50c7a8), [01 — стратегия, UX и MVP](https://linear.app/denis-apps/document/01-produktovaya-strategiya-ux-i-mvp-a0992380a8d4), [02 — health data](https://linear.app/denis-apps/document/02-integracii-health-data-ffe39fdcd683), [03 — качество данных](https://linear.app/denis-apps/document/03-metriki-normalizaciya-i-kachestvo-dannyh-a56009dff4e5), [04 — эксперименты](https://linear.app/denis-apps/document/04-personalnye-eksperimenty-i-analitika-04df95a2fba6) и [06 — приватность](https://linear.app/denis-apps/document/06-privatnost-bezopasnost-i-otkrytye-riski-8a5fc7ed172a). Архитектурный контекст остаётся у [DEN-10](https://linear.app/denis-apps/issue/DEN-10) и [DEN-11](https://linear.app/denis-apps/issue/DEN-11); этот flow не меняет их владельцев.

## Последовательность и checkpoint

Канонический путь: **Launch Gate** (невидимое решение) → **Welcome** → **Outcome** → **Context** → **Recommended protocols** → **Health explanation** → platform permission **или** manual → **Status/coverage** → **Setup** → **Today**.

Launch Gate — не Screen Spec: это решение реализации [DEN-33](https://linear.app/denis-apps/issue/DEN-33). Нового пользователя оно направляет в Welcome, возобновляемый draft — в его checkpoint, завершённый onboarding — в Today. Today — только destination после Setup в [DEN-28](https://linear.app/denis-apps/issue/DEN-28) и реализация [DEN-41](https://linear.app/denis-apps/issue/DEN-41), не onboarding Screen Spec.

Внутри Welcome до любого product answer обязательны состояние и явное подтверждение 18+. Отказ ведёт в safe exit/info; profile и experiment не создаются. Отдельного eligibility Screen Spec нет. Checkpoint хранит только его устойчивое значение: шаг, typed IDs и draft, но не `UiState`; при relaunch актуальные данные перечитываются и flow заново проверяет достижимость шага.

Manual никогда не перескакивает в Setup: он приходит в Status/coverage как явный manual data plan и лишь затем может продолжить. Permission, наличие провайдера и coverage — независимые факты: missing не равно zero, пустое чтение iOS не равно denied. Назад идёт по цепочке и не отзывает уже выданное системное permission.

## Реестр будущих Screen Spec

Пока attached Linear documents отсутствуют, поэтому следующие issue URL — временные ссылки на spec owner; соответствующие задачи заменят их URL документов и добавят backlink на этот User Flow.

| Screen Spec | Временный owner | Обязательные outgoing transitions |
| --- | --- | --- |
| Welcome | [DEN-26](https://linear.app/denis-apps/issue/DEN-26) | 18+ подтверждено → Outcome; отказ → safe exit/info; Back/relaunch → Launch Gate. |
| Outcome | [DEN-25](https://linear.app/denis-apps/issue/DEN-25) | valid или изменённый Goal → Context для подтверждения; Back → Welcome. |
| Context | [DEN-27](https://linear.app/denis-apps/issue/DEN-27) | valid или изменённый Context → Protocols/recompute; Back → Outcome. |
| Recommended protocols | [DEN-29](https://linear.app/denis-apps/issue/DEN-29) | selected или изменённый template → Health explanation; empty/error → recovery or Back; retry → recompute. |
| Health explanation | [DEN-30](https://linear.app/denis-apps/issue/DEN-30) | platform permission result → Status/coverage; manual → explicit manual plan in Status; Back → Protocols. |
| Status/coverage | [DEN-31](https://linear.app/denis-apps/issue/DEN-31) | fresh coverage для required MetricId/data plan или confirmed manual plan → Setup; refresh/retry stays Status; Back → Health explanation. |
| Setup | [DEN-28](https://linear.app/denis-apps/issue/DEN-28) | valid single save → Today; validation error/duplicate stays controlled; changed draft primary outcome → contextual permission loop. |

## Контракт переходов

| Источник | Условие или действие | Сохраняется / инвалидируется | Следующий экран |
| --- | --- | --- | --- |
| Launch Gate | new | Нет product answers, profile или experiment. | Welcome |
| Launch Gate | resume с достижимым checkpoint | Typed IDs и draft; актуальные данные перечитываются. | Сохранённый шаг |
| Launch Gate | resume/relaunch с недостижимым checkpoint | Совместимые upstream typed IDs и draft; incompatible downstream очищается, затем recompute/refresh. Setup со stale coverage напрямую не восстанавливается. | Ближайший достижимый upstream шаг |
| Launch Gate | completed | Завершённый checkpoint; onboarding не проигрывается. | Today |
| Welcome | 18+ подтверждено | Eligibility confirmation и checkpoint. | Outcome |
| Welcome | eligibility declined | Нет product answers, profile или experiment. | Safe exit/info |
| Outcome | Goal выбран или изменён | Goal и независимый Context selection сохраняются; ranking, template, health/status и Setup draft очищаются. | Context для подтверждения |
| Context | Context подтверждён или изменён | Goal и Context; ranking/template и всё downstream очищается. | Recommended protocols (recompute) |
| Recommended protocols | template выбран или изменён | Выбранный template; старые health/coverage и Setup draft очищаются. | Health explanation |
| Recommended protocols | empty или error; retry | Нет выведенного template; retry не создаёт experiment и повторно получает ranking. | Recommended protocols или Back |
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
| Любой onboarding Screen Spec | Back | Upstream answers/checkpoint; permission не отзывается. | Предыдущий достижимый шаг |
| Любой незавершённый шаг | relaunch | Checkpoint, typed IDs и draft, не `UiState`; данные перечитываются. | Launch Gate |
| Setup | validation failure | Draft и ошибки в текущем шаге; experiment не создаётся. | Setup |
| Setup | valid save | Один active experiment и completed checkpoint. | Today |
| Setup | duplicate submit | Уже созданный результат не дублируется; точный механизм отложен. | Today или Setup с результатом |
| Setup | изменение draft primary outcome меняет требуемые категории | Новый draft primary outcome; устаревший coverage/selection очищается, выданное permission не отзывается. | Health explanation → Status/coverage → Setup |

## Открытые решения

Механизмы ниже намеренно не определяются здесь; применимы только перечисленные инварианты и [раздел отложенных решений scope](onboarding-first-run-scope.md#отложенные-решения-и-совместимость).

- формальный Goal/Context → ranking и template → required/optional `MetricId`;
- схема хранения progress/checkpoint и policy пустой рекомендации;
- смысл `not-sure-yet` относительно пустого выбора;
- completed onboarding без active experiment;
- шкалы, baseline, длительность и stop rules;
- поддерживаемые health source, OS и market;
- legal/privacy retention;
- точная реализация duplicate submit.
