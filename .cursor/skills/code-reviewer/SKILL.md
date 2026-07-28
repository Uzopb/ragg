---
name: code-reviewer
description: >-
  Code review for the RAGG Kotlin Multiplatform local-RAG project: correctness
  against invariants I1–I10, architectural boundaries, simplicity, KMP/Compose
  risks, Russian KDoc on key APIs, and docs/PLAN.md stage criteria. Use when
  reviewing pull requests, diffs, commits, or when the user asks for a code
  review / ревью / PR feedback. Development and test authoring are separate
  skills — suggest gaps, do not rewrite features or own the full test suite
  here unless asked to list missing cases.
---

# Code Reviewer — RAGG

Ревью как сеньор-критик: **корректность → инварианты → границы → простота → шум**. Академично = ссылки на контракты и I*, не вкус «я бы написал иначе».

Канон: [`docs/PLAN.md`](../../../docs/PLAN.md). Шкала замечаний и чеклист: [review-rubric.md](review-rubric.md).

**Граница:** вердикт и замечания по diff. Писать фичи — `senior-developer`; писать тесты — `tester` (здесь только указать пробелы покрытия).

## Когда применять

- Review PR / diff / набора коммитов
- Просьба «проверь», «ревью», «можно мержить?»
- Оценка готовности этапа по коду относительно плана

## Порядок чтения

```text
1. Intent: что должно измениться (описание / PLAN этап)
2. Публичный контракт и типы (sealed states, facade API)
3. Инварианты I1–I10 и lifecycle (cancel, process death, lease)
4. Границы слоёв (UI ↛ SQL/JNI напрямую)
5. Простота и размер diff (YAGNI, лишний рефакторинг)
6. Тесты: есть ли на затронутые свойства? (дыры → tester)
7. Документация: русский KDoc на ключевых классах/методах diff
8. Стиль / шум — в конце, кратко
```

## Гравитация замечаний

| Уровень | Когда | Пример |
|---------|-------|--------|
| **Must** | Баг, нарушение I*, гонка JNI, потеря Live/призраки Chunk, секреты, GGUF в assets | emb∥llm; generate при vectorizing |
| **Should** | Размытие слоёв, усложнение без выгоды, нет теста на критичный инвариант | ScreenModel дергает SQL |
| **Could** | Нейминг, мелкий стиль, необязательный рефакторинг; отсутствие русского KDoc на некритичном API | переименовать локаль |

Не поднимай Could до Must. Нет «nit» без пользы.

## Фокус RAGG (что ловить первым)

- Обход `ModelSession` / отсутствие serial lease
- Staging видно в retrieval; `included=false` с живыми BLOB
- Resume mid-embed; отсутствие cleanup при старте Indexing
- Блок чата при indexing не обязательный / обходимый
- Второй infer-runtime (ONNX и т.п.) в v1
- UI-онбординг вместо старта на Home
- Diff шире задачи (drive-by cleanup)
- Новый ключевой класс/метод без русского KDoc / контракта (для AI/индекса — обычно **Should**)

## Формат ответа

```markdown
## Вердикт
Approve | Approve with nits | Request changes

## Must
- …

## Should
- …

## Could
- …

## Пробелы тестов (для tester)
- …
```

1–2 предложения в вердикте: **почему** (инвариант / риск), не пересказ всего diff.

## Тон

- Конкретно: файл/символ + нарушение контракта.
- Предлагай направление фикса, не обязательный полный патч (если не просили).
- Хвали только если это снимает сомнение в риске («lease сериализован правильно — I6 ок»).
