package com.heartline.ai

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.heartline.ai.ai.BundledLlmModelProvider
import com.heartline.ai.ai.ModelAssetManager
import com.heartline.ai.data.local.entities.ChatThreadEntity
import com.heartline.ai.data.local.entities.MessageEntity
import com.heartline.ai.data.local.entities.UserProfileEntity
import com.heartline.ai.data.seed.PersonaSeedData
import com.heartline.ai.domain.model.AiChatRequest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BundledLlmQualityInstrumentedTest {
    @Test
    fun bundledModelProducesTextAndFinalRepliesStayGroundedAcrossConversationFailures() = runBlocking {
        assumeTrue("LiteRT-LM bundled inference is arm64-only", Build.SUPPORTED_ABIS.firstOrNull() == "arm64-v8a")

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val provider = BundledLlmModelProvider(context, ModelAssetManager(context))
        provider.preload()
        val scenarios = listOf(
            ModelScenario(
                personaId = "lara",
                message = "good. what did you say you are doing?",
                recent = listOf(
                    userMessage("hows your day", "thread-lara"),
                    aiMessage("I am posting clips from a live set and saving ridiculous memes from the venue chat.", "thread-lara"),
                    aiMessage("How is your day treating you?", "thread-lara")
                ),
                expected = Regex("(?i)(live set|venue|clips|memes|posting)")
            ),
            ModelScenario(
                personaId = "elise",
                message = "you there",
                recent = listOf(
                    aiMessage("How is your evening?", "thread-elise"),
                    userMessage("yeah not bad, just had dinner", "thread-elise"),
                    userMessage("you there", "thread-elise")
                ),
                expected = Regex("(?i)(here|still).*(dinner|meal|food)|(dinner|meal|food).*(here|still)")
            ),
            ModelScenario(
                personaId = "maya",
                message = "are you still awake",
                recent = listOf(
                    userMessage("in a good mood and you", "thread-maya"),
                    userMessage("are you still awake", "thread-maya")
                ),
                expected = Regex("(?i)(awake|still up|yes,? i am up)")
            )
        )

        scenarios.forEach { scenario ->
            val reply = withTimeout(30_000) {
                provider.generateReply(
                    request(
                        personaId = scenario.personaId,
                        message = scenario.message,
                        recent = scenario.recent
                    )
                ).first()
            }
            val text = JSONObject(reply)
                .getJSONArray("messages")
                .let { array -> (0 until array.length()).joinToString(" ") { array.getString(it) } }

            assertTrue("Qwen returned no text for ${scenario.message}", provider.lastReplyDiagnostics.modelOutputReceived)
            assertTrue("Off-topic reply for ${scenario.message}: $text", scenario.expected.containsMatchIn(text))
            assertFalse(
                text.contains(
                    Regex(
                        "(?i)(good question|give me one more detail|json|system prompt|as an ai|what matters most|" +
                            "mobile chat bubbles?|new connection,? new vibe|flirty,? direct|^\\s*\\d+[.)])"
                    )
                )
            )
            assertTrue(text.split(Regex("\\s+")).size <= 45)
        }
    }

    private fun request(
        personaId: String,
        message: String,
        recent: List<MessageEntity>
    ): AiChatRequest {
        val persona = PersonaSeedData.personas().first { it.id == personaId }
        return AiChatRequest(
            persona = persona,
            user = UserProfileEntity(
                displayName = "Fred",
                preferredTone = "Sweet",
                notificationLevel = "Normal",
                quietHoursStart = "22:00",
                quietHoursEnd = "07:00"
            ),
            thread = ChatThreadEntity(
                id = "thread-${persona.id}",
                personaId = persona.id,
                relationshipStage = "Getting to know you",
                affinityScore = 28,
                messageCount = recent.size
            ),
            mood = null,
            memories = emptyList(),
            recentMessages = recent,
            conversationSummary = "Fred asked Lara about her day and wants to know what she meant.",
            message = message
        )
    }

    private fun userMessage(content: String, threadId: String = "thread-lara") = MessageEntity(
        id = "u-${content.hashCode()}",
        threadId = threadId,
        senderType = "USER",
        content = content
    )

    private fun aiMessage(content: String, threadId: String = "thread-lara") = MessageEntity(
        id = "a-${content.hashCode()}",
        threadId = threadId,
        senderType = "AI",
        content = content,
        source = "AI_REPLY"
    )

    private data class ModelScenario(
        val personaId: String,
        val message: String,
        val recent: List<MessageEntity>,
        val expected: Regex
    )
}
