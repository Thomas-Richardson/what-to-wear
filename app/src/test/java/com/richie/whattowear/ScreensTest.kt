package com.richie.whattowear

import android.app.Application
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.richie.whattowear.model.Feeling
import com.richie.whattowear.model.TimeOfDay
import com.richie.whattowear.model.Wardrobe
import com.richie.whattowear.reminder.ReminderWorker
import com.richie.whattowear.reminder.Reminders
import com.richie.whattowear.ui.ClothesPicker
import com.richie.whattowear.ui.FeelingRow
import com.richie.whattowear.ui.HistoryScreen
import com.richie.whattowear.ui.LogScreen
import com.richie.whattowear.ui.QuickLogSheet
import com.richie.whattowear.ui.WhatToWearTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * The screens, tapped and checked on an imitation Android phone (Robolectric), so they run on the Mac
 * and on GitHub without the emulator. Nothing here goes online: no test saves a log or fetches weather.
 */
@RunWith(AndroidJUnit4::class)
class ScreensTest {
    @get:Rule
    val compose = createComposeRule()
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val reminderJob = "daily-reminder"

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(app)
        Wardrobe.custom = emptyList()
    }

    @After
    fun tearDown() {
        Wardrobe.custom = emptyList()
    }

    private fun waitFor(what: String, check: () -> Boolean) {
        val until = System.currentTimeMillis() + 5_000
        while (!check()) {
            check(System.currentTimeMillis() < until) { "timed out waiting for $what" }
            compose.waitForIdle()
            Thread.sleep(20)
        }
    }

    @Test
    fun theFiveButtonsPickTheRightScore() {
        var picked: Feeling? = null
        compose.setContent { WhatToWearTheme { FeelingRow(null) { picked = it } } }
        for (f in Feeling.entries) compose.onNodeWithText(f.label).assertExists()
        compose.onNodeWithText("Too cold").performClick()
        assertEquals(Feeling.TOO_COLD, picked)
        compose.onNodeWithText("Way too cold").performClick()
        assertEquals(Feeling.WAY_TOO_COLD, picked)
    }

    @Test
    fun clothesTickAndEachGroupOffersAddYourOwn() {
        var toggled: String? = null
        compose.setContent { WhatToWearTheme { ClothesPicker(Wardrobe.items, emptySet(), { toggled = it }, onAdd = {}) } }
        compose.onNodeWithText("Gilet", substring = true).performClick()
        assertEquals("gilet", toggled)
        compose.onAllNodesWithText("Add your own").assertCountEquals(5)
    }

    @Test
    fun theLogTabStartsOnNowAndShowsPartsOfTheDayForOtherChoices() {
        val vm = MainViewModel(app)
        compose.setContent { WhatToWearTheme { LogScreen(vm, onUseMyLocation = {}) } }
        assertEquals(TimeOfDay.NOW, vm.draft.timeOfDay)
        compose.onNodeWithText("Morning").assertDoesNotExist()
        compose.onNodeWithText("Today").performClick()
        compose.onNodeWithText("Morning").assertExists()
        assertEquals(TimeOfDay.ALL_DAY, vm.draft.timeOfDay)
        compose.onNodeWithText("Now").performClick()
        compose.onNodeWithText("Morning").assertDoesNotExist()
        assertEquals(TimeOfDay.NOW, vm.draft.timeOfDay)
    }

    @Test
    fun addingYourOwnClothesTicksThemStraightAway() {
        val vm = MainViewModel(app)
        compose.setContent { WhatToWearTheme { LogScreen(vm, onUseMyLocation = {}) } }
        // The Jacket group's button is below the bottom of the screen, so scroll to it first, as a finger would.
        compose.onAllNodesWithText("Add your own")[2].performScrollTo().performClick()
        compose.onNode(hasSetTextAction() and hasText("What is it?")).performTextInput("Navy pea coat")
        compose.onNodeWithText("Very warm").performClick()
        compose.onNodeWithText("Add").performClick()
        assertTrue(vm.draft.itemIds.toString(), "custom_navy_pea_coat" in vm.draft.itemIds)
        assertEquals(4.0, Wardrobe.byId("custom_navy_pea_coat")!!.warmth, 0.0)
        compose.onNodeWithText("Navy pea coat", substring = true).assertExists()
    }

    @Test
    fun theQuickLogWindowShowsTheClothesAndNeedsAScoreToSave() {
        val vm = MainViewModel(app)
        vm.startQuickLog(null, listOf("shirt", "blazer"))
        compose.setContent { WhatToWearTheme { QuickLogSheet(vm, onClose = {}) } }
        compose.onNodeWithText("Blazer", substring = true).assertExists()
        compose.onNodeWithText("Save").assertIsNotEnabled()
        compose.onNodeWithText("Just right").performClick()
        compose.onNodeWithText("Save").assertIsEnabled()
        compose.onNodeWithText("Layer").assertDoesNotExist()
        compose.onNodeWithText("Other clothes").performClick()
        compose.onNodeWithText("Layer").assertExists()
    }

    @Test
    fun historyOffersToConnectTheNoteAndTheReminderSwitchWorks() {
        val vm = MainViewModel(app)
        Reminders.schedule(app)
        compose.setContent { WhatToWearTheme { HistoryScreen(vm, onConnectNote = {}) } }
        compose.onNodeWithText("Connect my note").assertExists()
        compose.onNodeWithText("Reminder at 5pm").assertExists()
        compose.onNode(isToggleable()).performClick()
        assertFalse(vm.reminderOn)
        waitFor("the reminder to be cancelled") {
            WorkManager.getInstance(app).getWorkInfosForUniqueWork(reminderJob).get().all { it.state.isFinished }
        }
    }

    @Test
    fun theReminderIsSetFor5pm() {
        Reminders.schedule(app)
        val job = WorkManager.getInstance(app).getWorkInfosForUniqueWork(reminderJob).get().single { !it.state.isFinished }
        assertTrue(Reminders.onTime(job.nextScheduleTimeMillis))
    }

    @Test
    fun aReminderThatDriftedTo4pmIsPutBackTo5pm() {
        val drifted = PeriodicWorkRequestBuilder<ReminderWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(Reminders.delayUntil(ZonedDateTime.now(), 16).toMillis(), TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(app).enqueueUniquePeriodicWork(reminderJob, ExistingPeriodicWorkPolicy.KEEP, drifted)
        Reminders.schedule(app)
        val job = WorkManager.getInstance(app).getWorkInfosForUniqueWork(reminderJob).get().single { !it.state.isFinished }
        assertTrue(Reminders.onTime(job.nextScheduleTimeMillis))
    }
}
