package com.liquorbee.updater;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;

/** Foreground self-update checks; never reads or changes the POS reminder state. */
final class SelfUpdateController {
    static final String PREFERENCES = "liquorbee_updater_self";
    static final String DISMISSED_BUILD = "dismissed_build";
    private static final long CHECK_INTERVAL_MS = 5 * 60 * 1000L;
    private final Activity activity;
    private final BooleanSupplier canPrompt;
    private final ReleaseChecker checker;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SharedPreferences preferences;
    private final TextView status, versions;
    private final Button checkButton, downloadButton;
    private ReleaseCheck last;
    private AlertDialog dialog;
    private boolean resumed, checking, closed, manualPending;
    private long promptedBuild;
    private final Runnable poll = new Runnable() {
        @Override public void run() {
            if (!resumed || closed) return;
            check(false);
            handler.postDelayed(this, CHECK_INTERVAL_MS);
        }
    };

    SelfUpdateController(Activity activity, BooleanSupplier canPrompt) {
        this(activity, canPrompt, new ReleaseChecker(new HttpsTextFetcher()));
    }

    // Injectable checker for device tests; no synthetic metadata is ever published.
    SelfUpdateController(Activity activity, BooleanSupplier canPrompt, ReleaseChecker checker) {
        this.activity = activity;
        this.canPrompt = canPrompt;
        this.checker = checker;
        preferences = activity.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
        status = activity.findViewById(R.id.self_update_status);
        versions = activity.findViewById(R.id.self_update_versions);
        checkButton = activity.findViewById(R.id.self_update_check);
        downloadButton = activity.findViewById(R.id.self_update_download);
        checkButton.setOnClickListener(v -> check(true));
        downloadButton.setOnClickListener(v -> download());
        render();
    }

    void onResume() {
        resumed = true;
        handler.removeCallbacks(poll);
        handler.post(poll);
        render();
    }

    void onPause() {
        resumed = false;
        handler.removeCallbacks(poll);
    }

    void check(boolean manual) {
        if (closed) return;
        manualPending |= manual;
        if (checking) return;
        checking = true;
        render();
        executor.execute(() -> {
            ReleaseCheck result = checker.check(UpdateConfig.UPDATER_VERSION_URL);
            if (Thread.currentThread().isInterrupted()) return;
            handler.post(() -> {
                if (closed || activity.isDestroyed()) return;
                last = result;
                checking = false;
                render();
                maybePrompt();
            });
        });
    }

    void maybePrompt() {
        if (closed || !resumed || checking || last == null || !canPrompt.getAsBoolean()
                || activity.isFinishing() || activity.isDestroyed()
                || (dialog != null && dialog.isShowing())) return;
        InstalledPos installed = InstalledPos.read(activity, activity.getPackageName());
        boolean manual = manualPending;
        manualPending = false;
        if (!installed.installed || last.failed()) {
            if (manual) showMessage(R.string.unable_to_check, R.string.self_update_failed);
            return;
        }
        long latest = last.published.code;
        if (SelfUpdatePolicy.shouldPrompt(manual, latest, installed.code,
                preferences.getLong(DISMISSED_BUILD, 0), promptedBuild)) {
            promptedBuild = latest;
            dialog = new AlertDialog.Builder(activity)
                    .setTitle(R.string.self_update_available)
                    .setMessage(activity.getString(R.string.self_update_prompt, installed.name, latest))
                    .setPositiveButton(R.string.self_update_download, (d, which) -> download())
                    .setNegativeButton(R.string.later, (d, which) ->
                            preferences.edit().putLong(DISMISSED_BUILD, latest).apply())
                    .show();
        } else if (manual && installed.code >= latest) {
            showMessage(R.string.up_to_date, R.string.self_update_current);
        }
    }

    private void showMessage(int title, int message) {
        dialog = new AlertDialog.Builder(activity).setTitle(title).setMessage(message)
                .setPositiveButton(android.R.string.ok, null).show();
    }

    private void render() {
        InstalledPos installed = InstalledPos.read(activity, activity.getPackageName());
        boolean available = installed.installed && last != null && !last.failed()
                && last.published.code > installed.code;
        checkButton.setEnabled(!checking);
        checkButton.setText(checking ? R.string.checking : R.string.self_update_check);
        downloadButton.setVisibility(available ? View.VISIBLE : View.GONE);
        downloadButton.setEnabled(!checking);
        versions.setText(activity.getString(R.string.self_update_installed, installed.name, installed.code));
        if (last != null && !last.failed()) versions.append("\n" + activity.getString(
                R.string.self_update_published, last.published.code));
        int label = checking ? R.string.checking : !installed.installed || (last != null && last.failed())
                ? R.string.unable_to_check : last == null ? R.string.ready_to_check
                : available ? R.string.self_update_available : R.string.up_to_date;
        int color = checking || last == null ? R.color.muted
                : available || last.failed() || !installed.installed ? R.color.warning : R.color.success;
        status.setText(label);
        status.setTextColor(activity.getColor(color));
    }

    private void download() {
        // Opening the browser is not proof of installation; recheck PackageManager on return.
        try {
            activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(UpdateConfig.UPDATER_APK_URL)));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(activity, R.string.no_browser, Toast.LENGTH_LONG).show();
        }
    }

    void close() {
        closed = true;
        resumed = false;
        handler.removeCallbacksAndMessages(null);
        executor.shutdownNow();
        if (dialog != null) dialog.dismiss();
    }
}
