package com.habitly.app.reminder

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class NotificationHelperTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val helper = NotificationHelper(context)

    @Before
    fun setUp() {
        helper.ensureChannel()
    }

    @Test
    fun `the streak nudge only alerts once`() {
        helper.notifyStreakAtRisk(7L, "Read", 5)
        val posted = shadowOf(manager).getNotification("streak_at_risk", 7)
        assertTrue(posted.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0)
    }

    @Test
    fun `cancelFor clears the habit's reminder and nudge but not other habits'`() {
        helper.notifyHabitDue(7L, "Read")
        helper.notifyStreakAtRisk(7L, "Read", 5)
        helper.notifyHabitDue(8L, "Walk")
        assertEquals(3, shadowOf(manager).size())

        helper.cancelFor(7L)

        val left = shadowOf(manager).allNotifications
        assertEquals(1, left.size)
        assertEquals("Time for: Walk", left.single().extras.getString(Notification.EXTRA_TITLE))
    }
}
