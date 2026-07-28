# Инварианты и обязательные тесты RAGG

Источник: [`docs/PLAN.md`](../../../docs/PLAN.md). Это **минимальный** набор свойств для tester-скилла.

## I1–I10 → тесты

| ID | Свойство | Идея теста |
|----|----------|------------|
| I1 | ¬(embed ∧ llm) резидентно | После `ensureEmbedLoaded` LLM unloaded; после `ensureLlmLoaded` emb unloaded |
| I2 | vectorizing ⇒ ¬runGenerate | `runGenerate` во время applyDraft → reject / Blocked path |
| I3 | retrieval = included ∧ Live | Staging и `included=false` не в top-k |
| I4 | fail/cancel → Staging∅, Live цел | Mid-exception / cancel: счётчики Live прежние, Staging 0 |
| I5 | старт Indexing → cleanup | Симулировать «краш»: status Indexing → cleanup → Ready |
| I6 | JNI serial | Два параллельных `withLease` не пересекаются (Mutex) |
| I7 | uncheck ⇒ Chunk DELETE | После removals-only `SELECT` по doc = 0 |
| I8 | re-include ⇒ re-embed | Новые chunk id/revision; не undelete |
| I9 | один activeCorpusId | Retrieve только по активной Corpus |
| I10 | нет mid-embed resume | snapshot не продолжает с ordinal N; повтор = полный diff |

## Этап 5c (канон из плана)

- removals-only не вызывает `ensureEmbedLoaded`, удаляет все Chunk
- cancel mid-run → `Cancelled`, Live цел
- orphan GC: файл без Document удалён; файл на двух Corpus остаётся

## Этап 2 (оценка)

- fit RAM: mid 6GB → мелкие Fits, крупные Tight/Insufficient
- после якоря T: Weaker estTok > T, Stronger < T
- `cost(1.5B Q4) > cost(0.5B Q4)`

## Границы Mock / native

| Этап | Ожидание в автотестах |
|------|------------------------|
| 0–5 | Mock engines; синтетический tok/s ок |
| 6+ | Контракты native; реальный бенч — device/критерий этапа, не обязательный CI без железа |

## Именование

```text
<unit>_<условие>_<ожидание>
applyDraft_cancelMidRun_stagingEmptyLiveUnchanged
modelSession_parallelWithLease_serialized
perfEstimator_afterEtalonT_weakerEstTokGreaterThanT
```
