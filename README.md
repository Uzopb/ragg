# RAGG — Local RAG on Kotlin Multiplatform

Кроссплатформенное приложение с **локальным RAG**: документы индексируются через embedding-модель, ответы генерирует LLM. Инференс — **ONNX Runtime**, без облака.

**Таргеты:** Android, Desktop (JVM), iOS (заглушка UI + общая БД), позже Web/Wasm.

| Модель | Пример | Роль |
|--------|--------|------|
| Embedding | `all-MiniLM-L6-v2` | текст → вектор |
| LLM | TinyLlama / Phi-3-mini (ONNX) | ответ по контексту |


