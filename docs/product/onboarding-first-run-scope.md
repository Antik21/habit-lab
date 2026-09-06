# Product contract: первый запуск и первый эксперимент

<!-- fact-owner: onboarding-first-run-scope -->
<!-- canonical-signature: onboarding-first-run-scope-v1 -->

**Статус:** v1, 2026-09-06. Этот контракт фиксирует scope для [DEN-23](https://linear.app/denis-apps/issue/DEN-23/onboarding-0120-zafiksirovat-scope-auditoriyu-i-slovari-pervogo) и является входом в реализацию [DEN-22](https://linear.app/denis-apps/issue/DEN-22/onboarding-habit-lab-i-zapusk-pervogo-personalnoho-eksperimenta). DEN-23 добавляет только документацию: без runtime-поведения, схемы, маршрутов или Kotlin-кода.

Источники: [Linear document 00 — карта исследования](https://linear.app/denis-apps/document/00-karta-issledovaniya-i-marshruty-dlya-agentov-dcf08b50c7a8), [01 — стратегия, UX и MVP](https://linear.app/denis-apps/document/01-produktovaya-strategiya-ux-i-mvp-a0992380a8d4), [02 — health data](https://linear.app/denis-apps/document/02-integracii-health-data-ffe39fdcd683), [03 — метрики и качество данных](https://linear.app/denis-apps/document/03-metriki-normalizaciya-i-kachestvo-dannyh-a56009dff4e5), [04 — эксперименты и аналитика](https://linear.app/denis-apps/document/04-personalnye-eksperimenty-i-analitika-04df95a2fba6) и [06 — приватность и риски](https://linear.app/denis-apps/document/06-privatnost-bezopasnost-i-otkrytye-riski-8a5fc7ed172a).

## Продуктовый scope v1

Продукт для взрослых 18+, исследующих собственный сон и утреннюю энергию. Владельцы часов — cohort первичной проверки, а не критерий допуска: ручной режим даёт тот же продукт без часов и health-permissions. Это wellness-инструмент, не диагностика, лечение, назначение лекарств, клиническое обещание или источник универсальных норм.

Пользователь выбирает один protocol template и получает один active experiment. У эксперимента один primary outcome, а наблюдение разделено на baseline и change phases. Отсутствующие или недостаточные данные — нормальный результат, не ноль, отказ либо доказательство эффекта.

## Канонические словари

Все stable ID — lower-kebab-case. Русские названия нейтральны; это не медицинские утверждения.

### GoalId

| ID | Название | Смысл |
| --- | --- | --- |
| `sleep-better` | Лучше спать | Мотивационный вход. |
| `wake-refreshed` | Бодрее просыпаться | Мотивационный вход. |
| `morning-energy` | Больше энергии утром | Мотивационный вход. |
| `calm-evening` | Спокойнее проводить вечер | Мотивационный вход. |
| `daily-movement` | Больше двигаться каждый день | Мотивационный вход. |

Пять целей нужны только для ranking трёх шаблонов о сне и утренней энергии; они не расширяют тематический scope первого релиза.

### ContextId

| ID | Название | Смысл |
| --- | --- | --- |
| `low-evening-movement` | Мало движения вечером | Контекст для ranking. |
| `screen-before-sleep` | Экран или соцсети перед сном | Контекст для ranking. |
| `irregular-sleep-time` | Нерегулярное время сна | Контекст для ranking. |
| `late-meal` | Поздние приёмы пищи | Контекст для ranking. |
| `hard-to-unwind` | Сложно расслабиться вечером | Контекст для ranking. |
| `variable-schedule` | График часто меняется | Контекст для ranking. |
| `not-sure-yet` | Пока не знаю | Нейтральный контекст. |

Контексты выбираются multi-select, служат только ranking и не являются диагнозом. Для контекста может не быть отдельного шаблона.

### ProtocolTemplateId

| ID | Название | Смысл |
| --- | --- | --- |
| `after-dinner-walk` | Прогулка после ужина | Наблюдаемый вечерний эксперимент. |
| `calm-evening-ritual` | Спокойный вечерний ритуал | Наблюдаемый вечерний эксперимент. |
| `regular-sleep-schedule` | Регулярное время сна | Наблюдаемый режимный эксперимент. |

Каждый шаблон допускает manual flow и не обещает фиксированную универсальную длительность либо эффект.

### MetricId v1

| ID | Название | Смысл |
| --- | --- | --- |
| `sleep-duration` | Длительность сна | Только фактический сон при достоверном методе. |
| `sleep-session-duration` | Длительность сессии сна | Отдельный fallback; не выдаётся за actual sleep. |
| `morning-energy` | Утренняя энергия | Самооценка утром. |
| `subjective-sleep-quality` | Субъективное качество сна | Самооценка сна. |
| `sleep-timing-variability` | Вариативность времени сна | Стабильность времени сна. |
| `sleep-attempt-time` | Время попытки заснуть | Разрешённая протоколом отметка. |

Факт соблюдения, действие, его время и длительность — события adherence, а не `MetricId`; время ввода отдельно от времени действия. RHR и HRV не входят в core v1; будущие RMSSD и SDNN нельзя смешивать с ними.

## Ручной режим, границы и приватность

Manual flow предлагает «продолжить вручную» и записать факт, время и опциональную длительность действия, утреннюю энергию, субъективное качество сна и разрешённое шаблоном время сна. Подключение health выполняется just-in-time отдельным consent для каждой нужной категории; локальная работа — default.

Не входят: каталоги caffeine/alcohol/nicotine/weight-loss; лишение сна, начало употребления алкоголя, экстремальные диеты/обезвоживание, изменение рецептурных препаратов; хронические заболевания, профессиональный спорт и clinical streams; AI-интерпретация, cloud, обязательный аккаунт, прямые watch/Bluetooth/vendor-cloud интеграции. Нужны отдельные согласия, запрет sensitive telemetry и screenshots, нейтральные notifications, бесплатные export/delete. До аудита нельзя заявлять legal compliance.

## Отложенные решения и совместимость

Открыты: точные anchors/ranges оценок, baseline/длительность/stop rules, формальное template-goal-context mapping и состояние «нет рекомендации», поддерживаемые health source/OS/legal launch market, автоматический primary sleep outcome, encryption/backup/retention, onboarding persistence/duplicate submit, localization и age confirmation.

Это продуктовые catalog ID для будущих domain value objects. Их нельзя смешивать с текущими route-safe `ExperimentId`, presentation `MetricKind` или `AutomationId`.
