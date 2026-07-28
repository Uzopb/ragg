---
name: tester
description: >-
  QA and test engineering for the RAGG Kotlin Multiplatform local-RAG project:
  unit/property tests for invariants I1–I10, ModelSession leases, Corpus index
  transactions, PerfEstimator, and stage acceptance criteria. Use when writing
  tests, designing test plans, verifying criteria from docs/PLAN.md, checking
  regressions, Mock vs native boundaries, or when the user asks for tester /
  QA / test coverage / verification. Development and code review are separate
  skills — do not implement features or own PR review format here.
---

# Tester — RAGG

Проверяй поведение как спецификацию: **свойства, инварианты, критерии этапа**. Тесты — executable contracts, не «покрытие ради %».

Канон: [`docs/PLAN.md`](../../../docs/PLAN.md). Инварианты и приоритеты: [invariants.md](invariants.md).

**Граница:** этот скилл — план тестов, написание/правка тестов, вердикт «проходит / нет». Реализацию фич — `senior-developer`; формат PR-ревью — `code-reviewer`.

## Когда применять

- Написать или дополнить тесты
- Составить тест-план по этапу / PR
- Проверить критерии готовности из плана
- Разобрать регрессию (воспроизведение → минимальный тест)

## Ценности

1. **Свойства важнее сценариев UI** — сначала I1–I10, lease, commit/rollback.
2. **Один тест — одно утверждение о системе** (или узкий связанный набор).
3. **Детерминизм** — Mock до этапа 6; без флаков от сети/GPU/таймингов без контроля.
4. **Минимальный setup** — Arrange через фасады/фейки, не через весь DI-граф без нужды.
5. **Имена = спецификация** — `removalsOnly_deletesAllChunks_andSkipsEmbed`.

## Пирамида для RAGG

| Уровень | Где | Что |
|---------|-----|-----|
| Unit (`commonTest` / JVM) | `sharedLogic` | `cost()`, fit, draft diff, IndexTransaction, ModelSession Mutex, retrieval-фильтр |
| Integration (узкий) | db + indexer + Mock engines | applyDraft фазы, startup cleanup, orphan GC |
| Manual / device | критерии этапа | бенч tok/s, RAM peak, UI-блок при indexing — по чеклисту этапа, не автоматом «всё» |
| Не цель v1 | E2E Compose на всех таргетах | только если пользователь явно просит |

## Рабочий цикл

```text
1. Что менялось? Какие I* / критерий этапа затронуты?
2. Сформулируй свойства (Given/When/Then или pre/post)
3. Выбери уровень (unit ≫ integration ≫ manual)
4. Напиши failing/уточняющий тест или прогони существующие
5. Зафиксируй пробелы: «не покрыто — риск X» (кратко)
```

### Чеклист тест-плана (короткий)

- [ ] Инварианты из [invariants.md](invariants.md) по зоне изменений
- [ ] Cancel / ошибка mid-run → Staging gone, Live цел (I4)
- [ ] `vectorizing` ⇒ generate отвергается (I2)
- [ ] Removals-only без `ensureEmbedLoaded` + DELETE Chunk (I7)
- [ ] Re-include = re-embed (I8)
- [ ] Граница Mock vs native соответствует этапу плана
- [ ] Нет зависимости от реального GGUF/сети без явной пометки device-теста

## Стиль тестов Kotlin

- kotlinx-coroutines-test / runTest для lease и Flow.
- Фейки: `MockLlmEngine`, `MockEmbeddingEngine`; шпионы вызовов `ensureEmbedLoaded` / `unload*`.
- SQLDelight: in-memory / тестовый драйвер; после теста — чистое состояние.
- Табличные тесты для `cost()` / fit / weaker-stronger.
- Не тестируй: тривиальные геттеры, чистый layout Compose без логики.
- Имена тестов — английские спецификации ок; вспомогательные комментарии и assert-сообщения — **на русском**, если поясняют свойство/инвариант. KDoc на тестах не обязателен (в отличие от production API в `senior-developer`).

## Формат отчёта

1. **Вердикт:** pass / fail / blocked (почему).
2. **Покрытые свойства** — список I* или критериев.
3. **Провалы** — ожидаемое vs факт, минимальный repro.
4. **Пробелы** — что осталось на device/ручное (1–3 пункта).

Без реализации фич «заодно», если пользователь не просил чинить код.
