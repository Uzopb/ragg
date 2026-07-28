package com.uzopb.ragg.ai.llama

/**
 * llama.cpp bridge. Stage 0: stubs only (not linked).
 * Real JNI / CMake wiring — stages 3/6.
 */
/** `true` only when native llama.cpp is linked and loadable. */
expect fun isLlamaNativeLinked(): Boolean
