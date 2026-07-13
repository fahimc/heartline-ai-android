package com.heartline.ai

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class MainActivitySmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun launchesToHeartlinePrimarySurface() {
        composeRule.waitUntil(timeoutMillis = 8_000) {
            composeRule
                .onAllNodesWithText("Heartline AI")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        composeRule.onNodeWithText("Heartline AI").assertIsDisplayed()
        composeRule.waitForIdle()
    }
}
