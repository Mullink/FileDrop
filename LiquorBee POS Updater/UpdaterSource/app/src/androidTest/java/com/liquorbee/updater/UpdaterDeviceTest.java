package com.liquorbee.updater;

import android.app.Notification;
import android.app.Activity;
import android.app.Instrumentation;
import android.app.NotificationManager;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.Context;
import android.content.Intent;
import android.widget.Button;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import android.service.notification.StatusBarNotification;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Run on a disposable emulator with the original POS installed and notifications granted.
 * Synthetic releases stay in this test process; production version.txt is never changed.
 */
@RunWith(AndroidJUnit4.class)
public class UpdaterDeviceTest {
    private Context context;
    private UpdateStore store;
    private NotificationManager notifications;
    private long installed;

    @Before public void prepare() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.getSharedPreferences("liquorbee_updater", Context.MODE_PRIVATE).edit().clear().commit();
        store = new UpdateStore(context);
        store.setMonitoring(false);
        UpdateScheduler.reconcile(context);
        notifications = context.getSystemService(NotificationManager.class);
        notifications.cancelAll();
        UpdateNotifications.createChannel(context);
        InstalledPos pos = InstalledPos.read(context);
        assertTrue("Install the original POS APK before this suite", pos.installed);
        installed = pos.code;
        assertTrue("Grant POST_NOTIFICATIONS before this suite", UpdateNotifications.enabled(context));
        store.setMonitoring(true);
    }

    @After public void clean() {
        notifications.cancelAll();
        context.getSharedPreferences("liquorbee_updater", Context.MODE_PRIVATE).edit().clear().commit();
        UpdateScheduler.reconcile(context);
    }

    private ReleaseCheck release(long code) {
        return new ReleaseCheck(new PublishedVersion(code, "Build " + code), "", System.currentTimeMillis());
    }

    private StatusBarNotification[] active(int expected) throws Exception {
        for (int i = 0; i < 40; i++) {
            StatusBarNotification[] result = notifications.getActiveNotifications();
            if (result.length == expected) return result;
            Thread.sleep(50);
        }
        StatusBarNotification[] result = notifications.getActiveNotifications();
        assertEquals(expected, result.length);
        return result;
    }

    @Test public void readsOriginalPosPackageVersion() {
        InstalledPos pos = InstalledPos.read(context);
        assertEquals(20260910L, pos.code);
        assertEquals("1.2.1", pos.name);
    }

    @Test public void postsOnceAndAlertsForNewerBuildWhileOldNotificationRemains() throws Exception {
        UpdateNotifications.consider(context, release(installed + 1));
        StatusBarNotification first = active(1)[0];
        assertEquals(installed + 1, store.notifiedCode());
        assertNotNull(first.getNotification().contentIntent);
        UpdateNotifications.consider(context, release(installed + 1));
        assertEquals(first.getPostTime(), active(1)[0].getPostTime());
        UpdateNotifications.consider(context, release(installed + 2));
        for (int i = 0; i < 40 && !active(1)[0].getNotification().extras
                .getString(Notification.EXTRA_TEXT).contains(Long.toString(installed + 2)); i++) Thread.sleep(50);
        Notification newer = active(1)[0].getNotification();
        assertTrue(newer.extras.getString(Notification.EXTRA_TEXT).contains(Long.toString(installed + 2)));
        assertEquals(0, newer.flags & Notification.FLAG_ONLY_ALERT_ONCE);
        assertEquals(installed + 2, store.notifiedCode());
    }

    @Test public void laterSurvivesStoreRecreationAndCannotUndoNewerNotification() throws Exception {
        store.markNotified(installed + 2);
        new UpdateStore(context).markNotified(installed + 1);
        assertEquals(installed + 2, new UpdateStore(context).notifiedCode());
        UpdateNotifications.consider(context, release(installed + 2));
        active(0);
        UpdateNotifications.consider(context, release(installed + 3));
        active(1);
    }

    @Test public void failedCheckDoesNotClearAvailableUpdateButCurrentBuildDoes() throws Exception {
        UpdateNotifications.consider(context, release(installed + 1));
        active(1);
        ReleaseCheck failure = new ReleaseCheck(null, "Offline", System.currentTimeMillis());
        store.save(failure);
        UpdateNotifications.consider(context, failure);
        assertTrue(new UpdateStore(context).lastCheck().failed());
        active(1);
        UpdateNotifications.consider(context, release(installed));
        active(0);
    }

    @Test public void pausedMonitoringDoesNotNotifyOrKeepJob() throws Exception {
        store.setMonitoring(false);
        assertTrue(UpdateScheduler.reconcile(context));
        assertNull(context.getSystemService(JobScheduler.class).getPendingJob(UpdateScheduler.JOB_ID));
        UpdateNotifications.consider(context, release(installed + 1));
        active(0);
        assertEquals(0, store.notifiedCode());
    }

    @Test public void schedulesSinglePersistedHourlyNetworkJob() {
        assertTrue(UpdateScheduler.reconcile(context));
        assertTrue(UpdateScheduler.reconcile(context));
        JobScheduler scheduler = context.getSystemService(JobScheduler.class);
        assertEquals(1, scheduler.getAllPendingJobs().size());
        JobInfo job = scheduler.getPendingJob(UpdateScheduler.JOB_ID);
        assertNotNull(job);
        assertTrue(job.isPersisted());
        assertTrue(job.isPeriodic());
        assertEquals(3600000L, job.getIntervalMillis());
        assertNotNull(job.getRequiredNetwork());
    }

    @Test public void fetchesRealHttpsVersionFile() {
        ReleaseCheck check = new ReleaseChecker(new HttpsTextFetcher()).check();
        assertFalse(check.error, check.failed());
        assertTrue(check.published.code > 0);
        store.save(check);
        assertEquals(check.published.code, new UpdateStore(context).lastCheck().published.code);
    }

    @Test public void reviewScreenLaterAndDownloadUseCorrectBuildAndOriginalUrl() throws Exception {
        store.markNotificationExplained();
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            AtomicBoolean ready = new AtomicBoolean(false);
            for (int i = 0; i < 300 && !ready.get(); i++) {
                scenario.onActivity(activity -> ready.set(activity.findViewById(R.id.check_button).isEnabled()));
                Thread.sleep(100);
            }
            assertTrue("Foreground HTTPS check completed", ready.get());
            scenario.onActivity(activity -> {
                store.save(release(installed + 1));
                assertEquals("Out-of-date", ((TextView) activity.findViewById(R.id.status)).getText().toString());
            });
            Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
            AtomicReference<Intent> downloadIntent = new AtomicReference<>();
            Instrumentation.ActivityMonitor monitor = new Instrumentation.ActivityMonitor() {
                @Override public Instrumentation.ActivityResult onStartActivity(Intent intent) {
                    downloadIntent.set(intent);
                    return new Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null);
                }
            };
            instrumentation.addMonitor(monitor);
            try {
                scenario.onActivity(activity -> activity.findViewById(R.id.download_button).performClick());
                assertNotNull(downloadIntent.get());
                assertEquals(Intent.ACTION_VIEW, downloadIntent.get().getAction());
                assertEquals(UpdateConfig.APK_URL, downloadIntent.get().getDataString());
                assertEquals(installed, InstalledPos.read(context).code);
            } finally { instrumentation.removeMonitor(monitor); }
            ready.set(false);
            for (int i = 0; i < 300 && !ready.get(); i++) {
                scenario.onActivity(activity -> ready.set(activity.findViewById(R.id.check_button).isEnabled()));
                Thread.sleep(100);
            }
            assertTrue(ready.get());
            scenario.onActivity(activity -> {
                store.save(release(installed + 1));
                ((Button) activity.findViewById(R.id.later_button)).performClick();
                assertEquals(installed + 1, new UpdateStore(context).notifiedCode());
                assertTrue(new UpdateStore(context).dailyShownAt() > 0);
                assertTrue(activity.isFinishing());
            });
            UpdateNotifications.consider(context, release(installed + 1));
            active(0);
        }
    }

    @Test public void notificationTapOpensReviewActivity() throws Exception {
        store.markNotificationExplained();
        UpdateNotifications.consider(context, release(installed + 1));
        Notification notification = active(1)[0].getNotification();
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Instrumentation.ActivityMonitor monitor = instrumentation.addMonitor(MainActivity.class.getName(), null, false);
        try {
            notification.contentIntent.send();
            Activity activity = instrumentation.waitForMonitorWithTimeout(monitor, 10000);
            assertNotNull("Notification opens the review screen", activity);
            assertTrue(activity instanceof MainActivity);
            instrumentation.runOnMainSync(activity::finish);
        } finally { instrumentation.removeMonitor(monitor); }
    }

    @Test public void dailyAttemptIsNotCountedUntilMatchingActivityConfirms() {
        long now = System.currentTimeMillis();
        String token = store.beginDailyAttempt(now);
        UpdateStore recreated = new UpdateStore(context);
        assertEquals(now, recreated.dailyAttemptAt());
        assertEquals(0, recreated.dailyShownAt());
        assertFalse(recreated.confirmDailyOpened("untrusted-token", now));
        assertEquals(0, recreated.dailyShownAt());
        assertTrue(recreated.confirmDailyOpened(token, now));
        assertEquals(now, new UpdateStore(context).dailyShownAt());
        assertFalse(recreated.confirmDailyOpened(token, now + 1));
    }

    @Test public void dailyAutomaticLaunchOpensAndRecordsOnlyOnce() throws Exception {
        assertTrue("Allow SYSTEM_ALERT_WINDOW before this test", DailyPrompts.launchAllowed(context));
        store.markNotificationExplained();
        store.setDailyOpening(true);
        ReleaseCheck newer = release(installed + 1);
        store.save(newer);
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Instrumentation.ActivityMonitor monitor = instrumentation.addMonitor(MainActivity.class.getName(), null, false);
        try {
            DailyPrompts.consider(context, newer);
            Activity activity = instrumentation.waitForMonitorWithTimeout(monitor, 10000);
            assertNotNull("Automatic review launch succeeded", activity);
            instrumentation.waitForIdleSync();
            assertTrue(store.dailyShownAt() > 0);
            long firstAttempt = store.dailyAttemptAt();
            DailyPrompts.consider(context, release(installed + 2));
            assertEquals("Newer builds obey the same daily cap", firstAttempt, store.dailyAttemptAt());
            instrumentation.runOnMainSync(activity::finish);
        } finally { instrumentation.removeMonitor(monitor); }
    }
}
