# Конвенции RAGG

Выжимка из [`docs/PLAN.md`](../../../docs/PLAN.md). При сомнении открывай полный план.

## Стек

| Слой | Технология |
|------|------------|
| UI | Compose Multiplatform + Voyager |
| DI | Koin |
| Сеть | Ktor |
| БД | SQLDelight |
| AI | llama.cpp / GGUF через `ModelSession` |
| Модули | `sharedLogic`, `sharedUI`, `androidApp`, `desktopApp`, `iosApp` |

## Пакеты `sharedLogic` (целевые)

- `device/` — HardwareProbe, CapabilityScore, SoC/GPU tables
- `models/` — Catalog, PerfEstimator, ModelManager, Downloader
- `cache/` — CachePaths expect/actual
- `db/` — SQLDelight
- `docs/` — DocumentParser, CorpusIndexer, IndexTransaction
- `ai/` — ModelSession, LlmEngine, EmbeddingEngine, Mock / llama
- `di/` — Koin modules
- `chat/` — история, TXT export, `activeCorpusId`

## Инварианты (обязательны)

```text
I1  ¬(embedResident ∧ llmResident)
I2  vectorizing ⇒ ¬runGenerate
I3  retrieval ⇒ included=true ∧ stage=Live
I4  ошибка / cancel applyDraft ⇒ Staging удалён, Live без изменений
I5  старт при Indexing ⇒ cleanup Staging, предложить повторить
I6  все JNI llama.cpp → один serial dispatcher
I7  included=false ⇒ нет Chunk для пары corpus×doc
I8  re-include ⇒ re-embed (нет undelete BLOB)
I9  ровно один Chat.activeCorpusId; retrieval только по нему
I10 snapshotDraftJson — аудит/повтор, не mid-embed resume
```

## UX-инварианты

- Старт всегда **Home (чат)**; онбординг моделей не ворота.
- История / Модели / Ресурсы — из меню; история — drawer слева.
- При индексации: `ChatState.Blocked(Indexing)` **обязателен**.
- Портрет only; визуал — серый/перламутр, бренд RAGG сильный (см. `docs/demo/`).

## AI / индекс — краткие правила

- Query-path: embed query → unload emb → retrieve → LLM (последовательно).
- applyDraft фазы: UnloadLlm → LoadEmbed → Running → UnloadEmbed → Commit.
- Removals-only: без load emb; чанки физически DELETE.
- Etalon и embedding: download-only, `sha256` обязателен; не в APK.
- v1 infer: CPU-first; `preferredBackend` advisory.

## Этапы и Mock

| Этап | AI |
|------|-----|
| 0–5 | Mock engines допустимы / обязательны до натива |
| 6 | Реальный llama.cpp + живой бенч эталона |
| 7 | PDF, FTS, полировка |

Не подключай native «раньше плана» без явной просьбы; не оставляй Mock в этапе 6 как заглушку критерия.

## Документация кода

| Что | Язык | Когда |
|-----|------|--------|
| KDoc классов / интерфейсов / ключевых методов | **русский** | публичный и доменный API, фазы индекса, AI-lease, фасады |
| Inline-комментарии (почему / инвариант) | **русский** | неочевидный контракт, trade-off, ссылка на I* |
| Имена символов, `@param`/`@return` имена | как в коде | латиница; описание — по-русски |
| Тесты (`commonTest`) | имена на английском ок | KDoc не обязателен; assert-сообщения можно по-русски |

Минимум для нового/изменённого ключевого API:

```kotlin
/**
 * Сериализует доступ к llama.cpp: embed и generate не резидентны одновременно (I1, I6).
 */
class ModelSession { … }

/**
 * Применяет draft корпуса: Staging → Commit либо полный rollback (I4, I10).
 * @param draft снимок изменений; mid-embed resume запрещён.
 */
suspend fun applyDraft(draft: CorpusDraft) { … }
```

Не документируй тривиальные геттеры и data-class поля без семантики инварианта.

## Стиль UI-кода

- ScreenModel оркестрирует; composable — отображение + события вверх.
- Состояния экранов — sealed из плана (`CalibrationUiState`, `ChatState`, …).
- Действия вторичные — иконки (как в демо), не раздувать chrome Home.

## Зависимости

- Версии — только `gradle/libs.versions.toml`.
- Новый library: нужна причина (замена ручного кода / закрытие риска), не «пригодится».
