package com.uzopb.ragg.docs

import com.uzopb.ragg.ai.MockLlmEngine
import com.uzopb.ragg.ai.SessionGate
import com.uzopb.ragg.chat.BlockReason
import com.uzopb.ragg.chat.ChatRepository
import com.uzopb.ragg.chat.ChatState
import com.uzopb.ragg.models.InMemoryInstalledModelStore
import com.uzopb.ragg.models.ModelRole
import com.uzopb.ragg.models.StorageStats
import com.uzopb.ragg.models.StorageStatsProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking

/**
 * Этап 4: mock applyDraft фазы + Blocked(Indexing) на чате.
 */
class MockResourceManagerTest {

    private fun harness(): Triple<SessionGate, ChatRepository, MockResourceManager> {
        val gate = SessionGate()
        val installed = InMemoryInstalledModelStore().also {
            it.upsert("m1", "/m1", 100, "a".repeat(64), ModelRole.Llm, active = true)
        }
        val chat = ChatRepository(
            sessionGate = gate,
            llmEngine = MockLlmEngine(),
            installed = installed,
            nowMs = { 1000L },
        )
        val resources = MockResourceManager(
            sessionGate = gate,
            storageStatsProvider = object : StorageStatsProvider {
                override fun stats(): StorageStats =
                    StorageStats(0, 0, 0, 0)
            },
            chatRepository = chat,
        )
        return Triple(gate, chat, resources)
    }

    @Test
    fun applyDraft_withAdds_emitsFivePhaseFamily() = runBlocking {
        val (_, chat, resources) = harness()
        resources.toggleDraft("d3") // pending add
        val phases = resources.applyDraft().toList()
        assertTrue(phases.any { it is VectorizeProgress.UnloadingLlm })
        assertTrue(phases.any { it is VectorizeProgress.LoadingEmbed })
        assertTrue(phases.any { it is VectorizeProgress.Running })
        assertTrue(phases.any { it is VectorizeProgress.UnloadingEmbed })
        assertTrue(phases.any { it is VectorizeProgress.Committing })
        assertTrue(phases.any { it is VectorizeProgress.Done })
        assertTrue(chat.observeChatState().value !is ChatState.Blocked)
    }

    @Test
    fun applyDraft_removalsOnly_skipsEmbedLoad() = runBlocking {
        val (_, _, resources) = harness()
        resources.toggleDraft("d1") // pending remove
        val phases = resources.applyDraft().toList()
        assertTrue(phases.none { it is VectorizeProgress.LoadingEmbed })
        assertTrue(phases.none { it is VectorizeProgress.Running })
        assertTrue(phases.any { it is VectorizeProgress.UnloadingLlm })
        assertTrue(phases.any { it is VectorizeProgress.Committing })
        assertTrue(phases.any { it is VectorizeProgress.Done })
    }

    @Test
    fun vectorizing_blocksChatComposer() {
        val (gate, chat, _) = harness()
        gate.setVectorizing(true)
        chat.onVectorizingChanged(true)
        val state = chat.observeChatState().value
        assertTrue(state is ChatState.Blocked)
        assertEquals(BlockReason.Indexing, (state as ChatState.Blocked).reason)
    }
}
