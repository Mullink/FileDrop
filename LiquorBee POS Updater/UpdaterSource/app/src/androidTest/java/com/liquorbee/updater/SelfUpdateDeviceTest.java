package com.liquorbee.updater;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import static org.junit.Assert.*;

/** Synthetic updater releases stay in this disposable emulator; no public metadata is changed. */
@RunWith(AndroidJUnit4.class)
public class SelfUpdateDeviceTest {
    private static final Field CONTROLLER = field(MainActivity.class, "selfUpdates");
    private static final Field DIALOG = field(SelfUpdateController.class, "dialog");
    private Context context;
    private long installedCode;

    @Before public void prepare() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.stopService(new Intent(context, ScheduledCheckService.class));
        context.getSharedPreferences("liquorbee_updater", Context.MODE_PRIVATE).edit().clear().commit();
        context.getSharedPreferences(SelfUpdateController.PREFERENCES, Context.MODE_PRIVATE).edit().clear().commit();
        UpdateStore pos = new UpdateStore(context);
        pos.setMonitoring(false);
        pos.markSetupExplained();
        pos.markNotificationExplained();
        UpdateScheduler.reconcile(context);
        InstalledPos installed = InstalledPos.read(context, context.getPackageName());
        assertTrue(installed.installed);
        installedCode = installed.code;
    }

    @After public void clean() {
        context.getSharedPreferences(SelfUpdateController.PREFERENCES, Context.MODE_PRIVATE).edit().clear().commit();
        context.getSharedPreferences("liquorbee_updater", Context.MODE_PRIVATE).edit().clear().commit();
        new UpdateStore(context).setMonitoring(false);
        UpdateScheduler.reconcile(context);
    }

    @Test public void laterPersistsAcrossControllersAndManualCheckCanShowTheUpdateAgain() throws Exception {
        long latest = installedCode + 1;
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            replaceController(scenario, version(latest));
            await(scenario, activity -> visibleDialog(activity) != null);
            awaitPosCheck(scenario);
            Map<String, ?> posBefore = seedPosState(scenario);

            scenario.onActivity(activity -> {
                assertEquals(context.getString(R.string.self_update_available), text(activity, R.id.self_update_status));
                AlertDialog dialog = visibleDialog(activity);
                assertTrue(((TextView) dialog.findViewById(android.R.id.message)).getText().toString()
                        .contains(Long.toString(latest)));
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
            });
            // AlertDialog posts its button callback to the main looper.
            await(scenario, activity -> context.getSharedPreferences(SelfUpdateController.PREFERENCES,
                    Context.MODE_PRIVATE).getLong(SelfUpdateController.DISMISSED_BUILD, 0) == latest
                    && visibleDialog(activity) == null);
            assertEquals(latest, context.getSharedPreferences(SelfUpdateController.PREFERENCES,
                    Context.MODE_PRIVATE).getLong(SelfUpdateController.DISMISSED_BUILD, 0));

            replaceController(scenario, version(latest));
            await(scenario, activity -> context.getString(R.string.self_update_available)
                    .equals(text(activity, R.id.self_update_status)));
            scenario.onActivity(activity -> {
                assertNull("Later survives controller recreation", visibleDialog(activity));
                activity.findViewById(R.id.self_update_check).performClick();
            });
            await(scenario, activity -> visibleDialog(activity) != null);
            assertEquals("Self-update actions must not change POS preferences", posBefore, posState());
        }
    }

    @Test public void downloadOpensOnlyTheUpdaterApkAndDoesNotChangeInstalledOrPosState() throws Exception {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            replaceController(scenario, version(installedCode + 1));
            await(scenario, activity -> visibleDialog(activity) != null);
            awaitPosCheck(scenario);
            Map<String, ?> posBefore = seedPosState(scenario);
            AtomicReference<Intent> opened = new AtomicReference<>();
            Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
            Instrumentation.ActivityMonitor monitor = new Instrumentation.ActivityMonitor() {
                @Override public Instrumentation.ActivityResult onStartActivity(Intent intent) {
                    opened.set(intent);
                    return new Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null);
                }
            };
            instrumentation.addMonitor(monitor);
            try {
                scenario.onActivity(activity -> visibleDialog(activity)
                        .getButton(AlertDialog.BUTTON_POSITIVE).performClick());
                await(scenario, activity -> opened.get() != null);
                assertNotNull(opened.get());
                assertEquals(Intent.ACTION_VIEW, opened.get().getAction());
                assertEquals(UpdateConfig.UPDATER_APK_URL, opened.get().getDataString());
                assertNotEquals(UpdateConfig.APK_URL, opened.get().getDataString());
                assertEquals(installedCode, InstalledPos.read(context, context.getPackageName()).code);
                // Returning from ACTION_VIEW can legitimately refresh the existing POS check result.
                assertEquals("Downloading the updater must preserve POS scheduling and dismissal state",
                        durablePosState(posBefore), durablePosState(posState()));
            } finally {
                instrumentation.removeMonitor(monitor);
            }
        }
    }

    @Test public void failedSelfCheckShowsFailureAndNeverClaimsUpToDate() throws Exception {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            replaceController(scenario, (url, limit) -> { throw new IOException("Synthetic offline check"); });
            await(scenario, activity -> context.getString(R.string.unable_to_check)
                    .equals(text(activity, R.id.self_update_status)));
            awaitPosCheck(scenario);
            Map<String, ?> posBefore = seedPosState(scenario);
            scenario.onActivity(activity -> {
                assertNull("Automatic network failures should not interrupt the operator", visibleDialog(activity));
                assertEquals(View.GONE, activity.findViewById(R.id.self_update_download).getVisibility());
                activity.findViewById(R.id.self_update_check).performClick();
            });
            await(scenario, activity -> visibleDialog(activity) != null);
            scenario.onActivity(activity -> {
                assertEquals(context.getString(R.string.unable_to_check), text(activity, R.id.self_update_status));
                assertNotEquals(context.getString(R.string.up_to_date), text(activity, R.id.self_update_status));
                assertEquals(context.getString(R.string.self_update_failed),
                        ((TextView) visibleDialog(activity).findViewById(android.R.id.message)).getText().toString());
            });
            assertEquals(posBefore, posState());
        }
    }

    private ReleaseChecker.TextFetcher version(long code) {
        return (url, limit) -> {
            assertEquals(UpdateConfig.UPDATER_VERSION_URL, url);
            assertEquals(1024, limit);
            return Long.toString(code);
        };
    }

    private void replaceController(ActivityScenario<MainActivity> scenario, ReleaseChecker.TextFetcher fetcher) {
        scenario.onActivity(activity -> {
            try {
                SelfUpdateController previous = (SelfUpdateController) CONTROLLER.get(activity);
                if (previous != null) previous.close();
                SelfUpdateController injected = new SelfUpdateController(activity, activity::hasWindowFocus,
                        new ReleaseChecker(fetcher));
                CONTROLLER.set(activity, injected);
                injected.onResume();
            } catch (IllegalAccessException e) { throw new AssertionError(e); }
        });
    }

    private Map<String, ?> seedPosState(ActivityScenario<MainActivity> scenario) {
        scenario.onActivity(activity -> {
            UpdateStore pos = new UpdateStore(activity);
            pos.setDailyTime(6, 12);
            pos.deferDailyOpening(System.currentTimeMillis());
            pos.markNotified(20269998L);
            pos.save(new ReleaseCheck(new PublishedVersion(20269999L, "Synthetic POS build"), "", 123456789L));
        });
        return posState();
    }

    private Map<String, ?> posState() {
        return new HashMap<>(context.getSharedPreferences("liquorbee_updater", Context.MODE_PRIVATE).getAll());
    }

    private static Map<String, ?> durablePosState(Map<String, ?> state) {
        Map<String, ?> durable = new HashMap<>(state);
        durable.remove("checked_at");
        durable.remove("published_code");
        durable.remove("published_label");
        durable.remove("check_error");
        return durable;
    }

    private static AlertDialog visibleDialog(MainActivity activity) {
        try {
            AlertDialog dialog = (AlertDialog) DIALOG.get(CONTROLLER.get(activity));
            return dialog != null && dialog.isShowing() ? dialog : null;
        } catch (IllegalAccessException e) { throw new AssertionError(e); }
    }

    private static String text(MainActivity activity, int id) {
        return ((TextView) activity.findViewById(id)).getText().toString();
    }

    private static Field field(Class<?> type, String name) {
        try {
            Field field = type.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }

    private static void awaitPosCheck(ActivityScenario<MainActivity> scenario) throws Exception {
        await(scenario, activity -> activity.findViewById(R.id.check_button).isEnabled());
    }

    private static void await(ActivityScenario<MainActivity> scenario, Predicate<MainActivity> ready) throws Exception {
        AtomicBoolean done = new AtomicBoolean();
        for (int attempt = 0; attempt < 300; attempt++) {
            scenario.onActivity(activity -> done.set(ready.test(activity)));
            if (done.get()) return;
            Thread.sleep(100);
        }
        fail("Timed out waiting for self-update UI state");
    }
}
