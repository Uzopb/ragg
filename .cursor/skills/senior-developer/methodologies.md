# Методологии и практики

Читать при проектировании API или рефакторинге, когда в `SKILL.md` не хватает глубины.

Тестирование и code review — **не** зона этого скилла (отдельные скиллы). Ниже — только то, что нужно разработчику для чистых контрактов и простой реализации.

## Принципы (рабочие определения)

| Принцип | В RAGG значит |
|---------|----------------|
| **KISS** | Предпочитай линейный алгоритм и явные фазы хитрой машине состояний «на вырост». |
| **YAGNI** | Не закладывай Parked-BLOB, GPU mid-phone, Wasm, второй runtime — вне скоупа. |
| **DRY** | Дублируй простое дважды; абстрагируй на третий раз или при общем инварианте. |
| **SRP** | Один модуль — одна причина меняться (`CorpusIndexer` ≠ `ChatScreenModel`). |
| **Fail fast** | Бюджеты чанков/RAM проверяй **до** commit Staging. |
| **Explicit > implicit** | Состояния — `sealed`; ошибки — типизированные / понятные сообщения UI. |

## Clean Architecture (практично)

Зависимости inward:

```text
UI / ScreenModel → Application (use-case / facade) → Domain → Infrastructure
```

- Domain: чистые типы и правила (`FitLevel`, diff draft, cosine top-k без Android).
- Application: `ModelManager`, `CorpusIndexer`, `ModelSession` — оркестрация.
- Infrastructure: SQLDelight, Ktor, llama.cpp JNI, `CachePaths` actual.

Не плоди пакеты «ради слоёв». Если use-case = 30 строк в facade — оставь facade.

## Design by Contract

Для критичных операций фиксируй контракт в **русском** KDoc и комментариях «почему» (см. § «Документация кода» в [ragg-conventions.md](ragg-conventions.md)):

- **Pre:** `!vectorizing` для generate; файл etalon на диске для бенча; Corpus `Ready` для apply.
- **Post:** I1–I10; Staging пуст при Failed/Cancelled; Live неизменен при rollback.
- **Invariant:** см. план — делай их соблюдение локальным и очевидным для внешнего ревью/тестов.

## Простота алгоритмов

1. Сначала выпиши фазы текстом (как в плане: UnloadLlm → … → Commit).
2. Код фаз должен **читаться сверху вниз** как этот список.
3. Ветку removals-only держи отдельной early-return — не смешивай с embed-циклом.
4. Сложную математику (`cost`, scoring) — чистые функции без UI/JNI.

## Concurrency

- Один serial path для JNI (Mutex + limitedParallelism(1)).
- UI подписан на `StateFlow`, не на «надежду», что вызов быстрый.
- Cancellation: cooperative; в `finally` — unload + cleanup Staging.
- Process death: источник истины — БД/флаги, не in-memory hope.

## Рефакторинг

Разрешён, только если:

- нужен для текущей задачи, **или**
- пользователь явно просит, **или**
- без него нельзя безопасно соблюсти инвариант.

Техника: маленькие шаги; не совмещай «переименовать всё» с «новая фича».

## Цитируемые ориентиры (идеи, не догма)

- Dijkstra / Gries: программы как доказательство относительно предусловий.
- Parnas: information hiding — модуль скрывает решение, а не данные «просто так».
- Fowler: refactoring catalog; применяй точечно.
- Ousterhout (*A Philosophy of Software Design*): deep modules — простые интерфейсы, сложность внутри **одного** места.

Копировать книжный жаргон в код не нужно: копируй **дисциплину**.
