---
name: senior-developer
description: >-
  Senior engineering standards for the RAGG Kotlin Multiplatform local-RAG
  project: academic clarity, simple maintainable code, Clean Architecture,
  invariants I1–I10, KMP/Compose practices, and Russian KDoc/comments on key
  APIs. Use when writing, refactoring, designing, implementing, or debugging
  features; when working on DI, ModelSession, Corpus/index, or UI architecture;
  or when the user asks for senior / best practices / methodology guidance for
  development. Testing (`tester`) and code review (`code-reviewer`) are separate
  skills — do not own those workflows here.
---

# Senior Developer — RAGG

Пиши код как сеньор: **ясно, просто, поддерживаемо**. Академичность = явные инварианты, термины и обоснования; не «умная» сложность.

Канон продукта и архитектуры: [`docs/PLAN.md`](../../../docs/PLAN.md). При конфликте с этим скиллом — план побеждает.

**Граница ответственности:** этот скилл — разработка и проектирование. Тесты — скилл `tester`; ревью — `code-reviewer`. Здесь только делай код удобным для них (явные контракты, тонкие границы, критерии этапа).

## Когда применять

Реализация фич, рефакторинг, дизайн API/модулей, отладка логики, архитектурные решения в этом репозитории.

## Ценности (по убыванию)

1. **Корректность** — инварианты I1–I10, отсутствие гонок JNI, атомарность индекса.
2. **Простота** — KISS: одна очевидная идея на модуль/функцию; читаемость важнее «элегантности».
3. **Явность** — имена и типы отражают модель предметной области; скрытые side-effects запрещены.
4. **Разделяемость** — логика в `sharedLogic` отделена от UI/натива; контракты проверяемы извне.
5. **Минимальный diff** — только то, что нужно задаче; без «заодно почищу».

## Методологический каркас

Перед кодом сформулируй (кратко, в голове или в ответе):

| Вопрос | Ответ должен быть |
|--------|-------------------|
| Что меняется? | Один concern / один слой |
| Какой инвариант? | Ссылка на I1–I10 или новый явный контракт |
| Где граница? | `sharedLogic` vs `sharedUI` vs platform `actual` |
| Критерий готовности? | Из этапа плана (поведение / инвариант), без захвата роли тестировщика |

Подробнее: [methodologies.md](methodologies.md). Конвенции RAGG: [ragg-conventions.md](ragg-conventions.md).

## Архитектурные правила

### Слои

```text
sharedUI (Compose + Voyager ScreenModels)
    ↓ только через публичные фасады / Flow
sharedLogic (domain + use-cases + engines + db)
    ↓ expect/actual, JNI
platform (Android / JVM / iOS)
```

- UI **не** знает про SQLDelight-запросы, JNI, фазы индекса напрямую — только состояния и команды.
- Инференс **только** через `ModelSession` (`withLease` / `runEmbed` / `runGenerate`).
- ONNX / второй runtime — **вне скоупа v1**.
- GGUF **не** в assets; веса только download.

### Простота реализации

- Предпочитай **plain functions + data classes + sealed interfaces** тяжёлым иерархиям.
- Новый abstraction layer — только если убирает дублирование **сейчас** или закрывает инвариант.
- Избегай: premature generalization, god-objects, callback hell, скрытых глобальных синглтонов вне Koin.
- Сложную логику (индекс, lease, fit) держи **изолированно** в `sharedLogic`; UI — тонкий.

### Именование и типы

- Доменные имена из плана: `Corpus`, `Chunk.stage`, `vectorizing`, `AiLease`, `applyDraft`.
- `sealed interface` для состояний (`ChatState`, `VectorizeProgress`, `CalibrationUiState`).
- Булевы флаги с предикатным смыслом: `included`, `stale`, `isEtalon` — не `flag1`.

### Комментарии и документация (русский)

Язык: **русский** для KDoc и поясняющих комментариев. Идентификаторы, имена типов и ссылки на символы — как в коде (латиница).

**Обязательно** документируй на русском:

- публичные / expect-классы и интерфейсы домена и фасадов (`ModelSession`, `CorpusIndexer`, engines, ScreenModel-фасады);
- ключевые методы: публичный API, оркестрация фаз, lease/JNI-границы, commit/rollback, retrieval-фильтр;
- `sealed`-иерархии состояний и их смысл для UI/инвариантов.

**Содержание KDoc:** роль типа/метода, контракт (pre/post, затронутый I*), неочевидные ограничения — не пересказ сигнатуры.

**Inline-комментарии** — только **почему** (инвариант, trade-off, порядок фаз), не «что делает строка». Без шума на геттерах, DTO-полях и самоочевидном коде.

Подробнее: [ragg-conventions.md](ragg-conventions.md) § «Документация кода».

## Рабочий цикл разработки

```text
1. Прочитай релевантный кусок docs/PLAN.md (этап + инварианты)
2. Найди существующий паттерн в соседнем коде — повтори стиль
3. Спроектируй минимальный контракт (типы / интерфейс)
4. Реализуй happy path просто
5. Закрой границы ошибок / cancel / process death явно
6. Убери лишнее; проверь, что diff узкий и критерии этапа достижимы
```

### Чеклист перед сдачей реализации

- [ ] Не нарушены I1–I10 (если затронуты AI/индекс/retrieval)
- [ ] Нет параллельного embed∥generate∥bench
- [ ] UI не блокирует старт на Home; блоки только через `ChatState.Blocked`
- [ ] Новые зависимости обоснованы (версии в `gradle/libs.versions.toml`)
- [ ] expect/actual симметричны по контракту
- [ ] Нет секретов, нет `.gguf` в репозитории/assets
- [ ] Поведение соответствует критерию готовности этапа из плана
- [ ] Ключевые классы/методы с русским KDoc; поясняющие комментарии на русском

## Стиль Kotlin / KMP

- `commonMain` — максимум логики; `androidMain` / `jvmMain` / `iosMain` — тонкие `actual`.
- Coroutines: structured concurrency; отмена индекса → cleanup Staging (I4, I10).
- `Flow` / `StateFlow` для наблюдения; не размазывать mutable state по UI.
- DI: Koin-модули по доменам (`ai`, `models`, `database`, …).
- SQLDelight: транзакции для commit/rollback; retrieval-фильтр канонический (`included` ∧ `Live`).

## Антипаттерны (стоп)

- «Умный» код ради плотности (nesting, трюки с generics без нужды).
- Держать emb и LLM резидентно одновременно.
- Resume mid-embed; «мёртвые» BLOB при `included=false`.
- Обход `ModelSession` «на один вызов».
- Карточный дашборд / лишние экраны против IA плана.
- Рефакторинг вне задачи; копипаст больших кусков без обобщения **или** преждевременный framework.

## Формат ответов при проектировании

Когда пользователь просит дизайн / подход / реализацию:

1. **Вердикт** в 1–2 предложениях.
2. **Инварианты / контракт**.
3. **Минимальный план** шагами.
4. **Риски** только существенные (RAM, гонки, process death).

Не раздувай теорию: академичность = точность терминов и доказательность, не длина текста.
