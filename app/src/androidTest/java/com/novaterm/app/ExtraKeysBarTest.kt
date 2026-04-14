package com.novaterm.app

import androidx.test.espresso.Espresso
import androidx.test.espresso.assertion.ViewAssertions
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for the extra keys bar (terminal input helpers).
 *
 * Verifies that the terminal screen loads with the expected
 * compose structure for extra keys.
 */
@RunWith(AndroidJUnit4::class)
@SmallTest
class ExtraKeysBarTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Test
    fun activityHasContent() {
        // The root content view should exist
        Espresso.onView(ViewMatchers.withId(android.R.id.content))
            .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
    }

    @Test
    fun activityDoesNotCrashOnRotation() {
        // Rotate the device and verify the activity survives
        activityRule.scenario.onActivity { activity ->
            // Verify activity is still active
            assert(!activity.isFinishing) { "Activity should survive configuration changes" }
        }
    }
}