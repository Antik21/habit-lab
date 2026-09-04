# Правила организации Compose-экранов

Правило обязательно для новых и существенно изменяемых экранов в `shared/src/commonMain`. Экран
должен оставаться общим для Android и iOS; платформенные различия передаются через capability или
callback, а не проверяются внутри composable.

## Структура пакета экрана

Каждый самостоятельный экран живёт в собственном пакете и разделён по ответственности:

```text
<feature>/<screen>/
├── <Feature><Screen>Screen.kt
├── <Feature><Screen>State.kt
├── <Feature><Screen>ViewModel.kt
├── <Feature><Screen>UiMapper.kt       # когда есть domain -> UI преобразование
└── sections/                          # самостоятельные смысловые UI-блоки
    └── <Feature><Block>Section.kt
```

- Не объединять состояния или ViewModel нескольких экранов в общий `*Screens.kt`/`*ViewModels.kt`.
- Один файл — одна ответственность. Общие типизированные navigation/result-контракты могут лежать
  уровнем выше, если ими действительно пользуются несколько экранов.
- `UiMapper` обязателен, когда состояние строится из domain-моделей, есть ветвление, форматирование
  или fallback-значения. Не создавать пустой mapper для статического экрана без преобразований.

## `<Feature><Screen>Screen.kt`

В одном файле находятся три уровня:

1. Публичный `Screen` — интеграционный слой. Он получает entry-scoped ViewModel, подписывается через
   `collectAsState`/`collectSideEffect`, обрабатывает только локальные `ViewEffect`, передаёт
   `NavigationEffect` владельцу Nav3 и вызывает `Content`.
2. `private Content` — stateless-рендер. Он получает готовый `ViewState` и единый
   `onAction: (Action) -> Unit`, не знает о ViewModel, Koin, Nav3 или платформе.
3. `@Preview private fun Preview()` — вызывает `Content` с полностью ручным `ViewState` внутри
   `HabitLabTheme`. Preview-данные не должны попадать в production state или ViewModel.

Nav3-host создаёт entry-scoped ViewModel и остаётся единственным владельцем back stack, но не
разбирает пользовательские действия и не собирает состояние экрана. Screen сообщает навигацию
наружу типизированным `NavigationEffect`.

UI-локальное эфемерное состояние допускается в `Screen` (например, видимость модального окна после
одноразового эффекта). Пользовательский ввод и данные, которые должны переживать recomposition,
обычно являются частью `ViewState`.

## `sections/`

Если `Content` содержит три и более смысловых блока либо список с двумя и более типами элементов,
каждый блок выносится в отдельный файл `sections/<Feature><Block>Section.kt`.

- Функция секции имеет вид `internal @Composable` и получает только UI-модель/примитивы и callbacks.
- Секция не читает ViewModel и не получает весь `ViewState`, если ей нужна лишь его часть.
- Каждая секция содержит собственный `@Preview private fun Preview()` в `HabitLabTheme`.
- Визуальные состояния блока (`enabled`, `alpha`, цвета) вычисляются внутри секции из её UI-модели.

## `<Feature><Screen>State.kt`

Внутри пакета использовать короткие generic-имена:

```kotlin
@Immutable
data class ViewState(...)

sealed interface Action
sealed interface SideEffect
sealed interface NavigationEffect : SideEffect
sealed interface ViewEffect : SideEffect
```

- Рядом с `ViewState` хранить UI-модели, составляющие единый снимок экрана.
- Каждое пользовательское или lifecycle-событие выражать отдельным `Action`.
- Разделять эффекты по ответственности: `NavigationEffect` обрабатывает Nav3-host, `ViewEffect` —
  сам Screen (диалог, platform capability и другие одноразовые UI-команды).
- Одноразовый эффект не хранить булевым флагом в `ViewState`. Данные, которые постоянно отображаются
  после события, являются состоянием, а не эффектом.
- Помечать immutable UI-состояние и UI-модели `@Immutable`.

## `<Feature><Screen>UiMapper.kt`

Mapper — единственная точка сборки готового `ViewState` из domain-данных. Публичный метод — `map`,
ветвление и форматирование разносить по приватным `build*`/`map*` функциям. ViewModel, Screen и
секции не должны независимо дублировать domain -> UI правила. Compose resources можно разрешать в
`Content`, когда текст полностью статичен и не зависит от domain-данных.

## `<Feature><Screen>ViewModel.kt`

- ViewModel реализует `ContainerHost<ViewState, SideEffect>` и создаётся entry-scoped через Koin на
  границе Nav3-композиции.
- Единственная публичная точка для UI-событий — `dispatchAction(Action)`. Внутри полный `when`,
  делегирующий в приватные `on<Event>()` функции.
- Навигационные аргументы передаются конструктору ViewModel, а не стартовым Action.
- Первичная работа запускается через `container(..., onCreate = { ... })`; async-операции выполняются
  в Orbit `intent`/`subIntent`, а не отдельным `viewModelScope.launch`.
- `reduce` только присваивает уже подготовленное состояние. Бизнес-правила находятся в domain
  interactors, domain -> UI преобразование — в mapper.
- Одноразовые команды отправляются через `postSideEffect`.
- Защищать повторные действия и гонки явными guard-проверками, когда экран имеет loading/saving.
- Комментарии объясняют только неочевидную причину или инвариант, а не пересказывают код.

## Ресурсы, автоматизация и проверка

- Пользовательские строки находятся в Compose resources; не хранить display-текст литералами в UI.
- Интерактивные и проверяемые узлы используют стабильный `AutomationId`, не зависящий от локали,
  пользовательских или runtime-данных.
- Общие composable не содержат Android/iOS conditionals.
- После изменения экрана минимум выполнить Android build/tests, iOS Simulator framework/build и
  проверить ключевой пользовательский сценарий на обеих OS.
