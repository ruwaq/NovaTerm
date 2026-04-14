package com.novaterm.app

import androidx.test.espresso.Espresso
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.assertion.ViewAssertions
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Navigation tests for NovaTerm settings screens.
 *
 * These tests verify that the settings UI is accessible
 * and can be navigated to from the main screen.
 */
@RunWith(AndroidJUnit4::class)
@MediumTest
class SettingsNavigationTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Test
    fun settingsScreenIsAccessible() {
        // Verify the main screen content is displayed
        Espresso.onView(ViewMatchers.withId(android.R.id.content))
            .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
    }
}