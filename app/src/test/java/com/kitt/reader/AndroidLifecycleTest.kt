package com.kitt.reader

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndroidLifecycleTest {
    @Test fun activityLaunchesAndServiceQuietEndControlsWork() {
        val app = RuntimeEnvironment.getApplication() as KittApp
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.POST_NOTIFICATIONS)
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup()
        assertNotNull(activity.get().window.decorView); assertFalse(app.runtime.journey.running)
        val service = Robolectric.buildService(JourneyService::class.java).create()
        service.get().onStartCommand(Intent(app, JourneyService::class.java).setAction(JourneyService.START)
            .putExtra(JourneyService.SIMULATED, true), 0, 1)
        assertTrue(app.runtime.journey.running)
        service.get().onStartCommand(Intent(app, JourneyService::class.java).setAction(JourneyService.QUIET), 0, 2)
        assertEquals(JourneyState.QUIET, app.runtime.journey.state)
        val notification = app.getSystemService(NotificationManager::class.java).activeNotifications.first().notification
        assertEquals(2, notification.actions.size); assertEquals("结束安静", notification.actions.first().title)
        activity.pause().stop(); assertTrue(app.runtime.journey.running)
        activity.restart().start().resume(); assertEquals(JourneyState.QUIET, app.runtime.journey.state)
        service.get().onStartCommand(Intent(app, JourneyService::class.java).setAction(JourneyService.END), 0, 3)
        assertFalse(app.runtime.journey.running); assertNull(app.runtime.source)
        service.destroy(); activity.pause().stop().destroy()
    }
}
