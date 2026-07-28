# Рубрика code review — RAGG

## Чеклист по зонам

### AI / ModelSession

- [ ] Все вызовы через `withLease` / `runEmbed` / `runGenerate`
- [ ] I1: нет одновременной резидентности emb и LLM
- [ ] I2 / I6: нет generate при `vectorizing`; нет параллельного JNI
- [ ] `cooldownAfterUnload` / unload в `finally` на путях ошибки

### Индекс / Corpus

- [ ] Фазы applyDraft читаемы; removals-only — early path без emb
- [ ] Commit атомарный; I4/I5/I10 при cancel/crash
- [ ] I7/I8: DELETE при uncheck; re-include = re-embed
- [ ] Retrieval-предикат: `included` ∧ `stage=Live` ∧ activeCorpus (I3, I9)

### UI / навигация

- [ ] Старт → Home; нет ворот-онбординга моделей
- [ ] `ChatState.Blocked(Indexing)` при vectorizing
- [ ] ScreenModel не протекает в SQLDelight/JNI
- [ ] IA соответствует плану / `docs/demo/` (без лишних экранов)

### KMP / инфраструктура

- [ ] Логика в `commonMain`, где возможно; `actual` тонкие
- [ ] Версии в `libs.versions.toml`; нет лишних зависимостей
- [ ] Нет `.gguf` / секретов в репо и assets
- [ ] Mock vs native соответствует этапу (не «притворились этапом 6»)

### Документация

- [ ] Ключевые новые/изменённые классы и методы имеют **русский** KDoc (роль + контракт / I*)
- [ ] Поясняющие комментарии на русском; без шума «что делает строка»
- [ ] Идентификаторы в латинице; описания — по-русски

### Diff-гигиена

- [ ] Изменения в скоупе задачи
- [ ] Нет мёртвого кода «на будущее» (YAGNI)
- [ ] Имена и sealed-состояния из домена плана

## Approve / Request changes

| Вердикт | Условие |
|---------|---------|
| **Approve** | Нет Must; Should незначительны или осознанно отложены |
| **Approve with nits** | Только Could / мелкие Should с понятным follow-up |
| **Request changes** | Есть Must или Should, без которого нельзя честно закрыть критерий этапа |

## Антипаттерны ревьюера

- Переписывать стиль под личный вкус без нарушения ясности
- Требовать абстракции «на вырост»
- Игнорировать план и предлагать ONNX / mid-embed resume / карточки-дашборд против IA
- Дублировать работу tester: не писать полный suite в ревью — перечислить свойства
