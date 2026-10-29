package io.github.shoalapps.pufferfish

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @org.junit.Before fun dismissReleaseNotes() {
        compose.waitForIdle()
        if (compose.onAllNodesWithText("Let's dive in").fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithText("Let's dive in").performClick()
            compose.waitForIdle()
        }
    }

    @Test fun libraryOpens() {
        compose.onAllNodesWithText("Your Library").onFirst().performClick()
        compose.onNodeWithText("Liked Songs").assertExists()
    }
}
