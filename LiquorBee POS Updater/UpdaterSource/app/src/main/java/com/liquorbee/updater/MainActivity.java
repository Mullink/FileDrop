package com.liquorbee.updater;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Insets;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DateFormat;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final int NOTIFICATION_REQUEST = 7;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private UpdateStore store;
    private boolean checking;
    private boolean scheduleOk = true;
    private TextView status, message, installedText, publishedText, checkedText, notificationText;
    private TextView scheduleText;
    private TextView dailyText;
    private Button dailyPermissionButton;
    private Button checkButton, downloadButton, laterButton, notificationButton;
    private ProgressBar progress;
    private SharedPreferences preferences;
    private final SharedPreferences.OnSharedPreferenceChangeListener preferenceListener = (prefs, key) ->
            runOnUiThread(() -> { if (!isDestroyed() && !checking) render(); });

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        applyInsets();
        store = new UpdateStore(this);
        UpdateNotifications.createChannel(this);
        scheduleOk = UpdateScheduler.reconcile(this);
        status = findViewById(R.id.status);
        message = findViewById(R.id.message);
        installedText = findViewById(R.id.installed_version);
        publishedText = findViewById(R.id.published_version);
        checkedText = findViewById(R.id.last_checked);
        notificationText = findViewById(R.id.notification_description);
        scheduleText = findViewById(R.id.schedule_description);
        checkButton = findViewById(R.id.check_button);
        downloadButton = findViewById(R.id.download_button);
        laterButton = findViewById(R.id.later_button);
        notificationButton = findViewById(R.id.notification_button);
        progress = findViewById(R.id.progress);
        dailyText = findViewById(R.id.daily_description);
        dailyPermissionButton = findViewById(R.id.daily_permission_button);
        dailyPermissionButton.setOnClickListener(v -> enableDailyOpening());
        Switch dailySwitch = findViewById(R.id.daily_switch);
        dailySwitch.setChecked(store.dailyOpeningEnabled());
        dailySwitch.setOnCheckedChangeListener((button, enabled) -> {
            store.setDailyOpening(enabled);
            render();
        });

        Switch backgroundSwitch = findViewById(R.id.background_switch);
        backgroundSwitch.setChecked(store.monitoringEnabled());
        backgroundSwitch.setOnCheckedChangeListener((button, enabled) -> {
            store.setMonitoring(enabled);
            scheduleOk = UpdateScheduler.reconcile(this);
            if (!enabled) UpdateNotifications.cancel(this);
            render();
            if (enabled) checkNow();
        });
        checkButton.setOnClickListener(v -> checkNow());
        downloadButton.setOnClickListener(v -> openUrl(UpdateConfig.APK_URL));
        laterButton.setOnClickListener(v -> {
            ReleaseCheck last = store.lastCheck();
            if (last != null && last.published != null) store.markNotified(last.published.code);
            store.deferDailyOpening(System.currentTimeMillis());
            UpdateNotifications.cancel(this);
            Toast.makeText(this, R.string.later_saved, Toast.LENGTH_LONG).show();
            finish();
        });
        notificationButton.setOnClickListener(v -> enableNotifications());
        findViewById(R.id.source_button).setOnClickListener(v -> openUrl(UpdateConfig.GITHUB_VERSION_URL));
        preferences = getSharedPreferences("liquorbee_updater", MODE_PRIVATE);
        preferences.registerOnSharedPreferenceChangeListener(preferenceListener);
        render();

        if (Build.VERSION.SDK_INT >= 33 && !store.notificationExplained()) {
            store.markNotificationExplained();
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_REQUEST);
        }
    }

    @Override protected void onResume() {
        super.onResume();
        if (store != null) {
            DailyPrompts.confirmOpened(this, getIntent());
            render();
            checkNow();
        }
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        DailyPrompts.confirmOpened(this, intent);
        checkNow();
    }

    private void checkNow() {
        if (checking) return;
        checking = true;
        render();
        executor.execute(() -> {
            ReleaseCheck result = new ReleaseChecker(new HttpsTextFetcher()).check();
            if (Thread.currentThread().isInterrupted()) return;
            store.save(result);
            UpdateNotifications.consider(getApplicationContext(), result);
            runOnUiThread(() -> {
                if (isDestroyed()) return;
                checking = false;
                if (hasWindowFocus()) DailyPrompts.recordReview(this, result);
                render();
            });
        });
    }

    private void render() {
        if (status == null) return;
        InstalledPos installed = InstalledPos.read(this);
        ReleaseCheck last = store.lastCheck();
        PublishedVersion published = last == null ? null : last.published;
        boolean updateAvailable = installed.installed && published != null && published.code > installed.code;
        installedText.setText(installed.installed
                ? getString(R.string.installed_format, installed.name, installed.code)
                : getString(R.string.not_installed));
        publishedText.setText(published == null ? getString(R.string.not_available) : published.label);
        checkedText.setText(last == null ? getString(R.string.not_checked)
                : getString(R.string.last_checked_format, DateFormat.getDateTimeInstance(
                        DateFormat.MEDIUM, DateFormat.SHORT).format(new Date(last.checkedAt))));
        progress.setVisibility(checking ? View.VISIBLE : View.GONE);
        checkButton.setEnabled(!checking);
        checkButton.setText(checking ? R.string.checking : R.string.check_now);
        laterButton.setVisibility(updateAvailable && !checking ? View.VISIBLE : View.GONE);
        downloadButton.setEnabled(!checking);
        downloadButton.setText(updateAvailable ? R.string.download_update : R.string.download_pos);

        if (checking) {
            setStatus(R.string.checking, R.color.muted);
            message.setText(R.string.checking_message);
        } else if (!installed.installed) {
            setStatus(R.string.not_installed, R.color.muted);
            message.setText(last != null && last.failed()
                    ? getString(R.string.not_installed_message) + "\n\n" + last.error
                    : getString(R.string.not_installed_message));
            UpdateNotifications.cancel(this);
        } else if (last == null) {
            setStatus(R.string.ready_to_check, R.color.muted);
            message.setText(R.string.ready_message);
        } else if (last.failed()) {
            setStatus(R.string.unable_to_check, R.color.warning);
            message.setText(last.error);
        } else if (updateAvailable) {
            setStatus(R.string.out_of_date, R.color.warning);
            message.setText(R.string.update_message);
        } else {
            setStatus(R.string.up_to_date, R.color.success);
            message.setText(R.string.current_message);
            UpdateNotifications.cancel(this);
        }
        notificationText.setText(UpdateNotifications.enabled(this)
                ? R.string.notifications_on : R.string.notifications_off);
        notificationButton.setVisibility(UpdateNotifications.enabled(this) ? View.GONE : View.VISIBLE);
        scheduleText.setText(!scheduleOk ? R.string.schedule_failed
                : store.monitoringEnabled() ? R.string.schedule_enabled : R.string.schedule_paused);
        boolean dailyEnabled = store.monitoringEnabled() && store.dailyOpeningEnabled();
        boolean allowed = DailyPrompts.launchAllowed(this);
        dailyText.setText(!dailyEnabled ? R.string.daily_paused
                : allowed ? R.string.daily_enabled : R.string.daily_permission_needed);
        dailyPermissionButton.setVisibility(dailyEnabled && !allowed ? View.VISIBLE : View.GONE);
    }

    private void enableDailyOpening() {
        try {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.daily_settings_unavailable, Toast.LENGTH_LONG).show();
        }
    }

    private void setStatus(int text, int color) {
        status.setText(text);
        status.setTextColor(getColor(color));
    }

    private void enableNotifications() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                && shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_REQUEST);
        } else {
            try {
                startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
            } catch (ActivityNotFoundException e) {
                Toast.makeText(this, R.string.open_notification_settings, Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == NOTIFICATION_REQUEST) {
            render();
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) checkNow();
        }
    }

    private void openUrl(String url) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
        catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.no_browser, Toast.LENGTH_LONG).show();
        }
    }

    @SuppressWarnings("deprecation")
    private void applyInsets() {
        View root = findViewById(R.id.root);
        if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);
        else getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        root.setOnApplyWindowInsetsListener((view, windowInsets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                Insets insets = windowInsets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                view.setPadding(insets.left, insets.top, insets.right, insets.bottom);
            } else {
                view.setPadding(windowInsets.getSystemWindowInsetLeft(), windowInsets.getSystemWindowInsetTop(),
                        windowInsets.getSystemWindowInsetRight(), windowInsets.getSystemWindowInsetBottom());
            }
            return windowInsets;
        });
        root.requestApplyInsets();
    }

    @Override protected void onDestroy() {
        if (preferences != null) preferences.unregisterOnSharedPreferenceChangeListener(preferenceListener);
        executor.shutdownNow();
        super.onDestroy();
    }
}
