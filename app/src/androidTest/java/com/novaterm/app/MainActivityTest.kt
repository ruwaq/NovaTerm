package com.novaterm.app

import androidx.test.espresso.Espresso
import androidx.test.espresso.assertion.ViewAssertions
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Basic UI tests for NovaTerm's main activity.
 *
 * These tests verify that the app launches successfully and
 * key UI elements are present. They run on a device/emulator.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class MainActivityTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Test
    fun appLaunchesSuccessfully() {
        // Verify the activity is in resumed state
        activityRule.scenario.onActivity { activity ->
            assert(!activity.isFinishing) { "Activity should not be finishing" }
            assert(!activity.isDestroyed) { "Activity should not be destroyed" }
        }
    }

    @Test
    fun mainWindowIsDisplayed() {
        // The main compose view should be present
        Espresso.onView(ViewMatchers.withId(android.R.id.content))
            .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
    }
}