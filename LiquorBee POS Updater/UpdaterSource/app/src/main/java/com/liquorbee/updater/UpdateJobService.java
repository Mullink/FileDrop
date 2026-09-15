package com.liquorbee.updater;

import android.app.job.JobParameters;
import android.app.job.JobService;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

public final class UpdateJobService extends JobService {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private Future<?> work;
    private AtomicBoolean stopped;

    @Override public boolean onStartJob(JobParameters params) {
        if (!new UpdateStore(this).monitoringEnabled()) return false;
        final AtomicBoolean cancelled = new AtomicBoolean(false);
        stopped = cancelled;
        work = executor.submit(() -> {
            ReleaseCheck check = new ReleaseChecker(new HttpsTextFetcher()).check();
            if (cancelled.get() || Thread.currentThread().isInterrupted()) return;
            new UpdateStore(this).save(check);
            UpdateNotifications.consider(this, check);
            if (!cancelled.get()) DailyPrompts.consider(this, check);
            if (!cancelled.get()) jobFinished(params, check.failed());
        });
        return true;
    }

    @Override public boolean onStopJob(JobParameters params) {
        if (stopped != null) stopped.set(true);
        if (work != null) work.cancel(true);
        return new UpdateStore(this).monitoringEnabled();
    }

    @Override public void onDestroy() {
        if (stopped != null) stopped.set(true);
        executor.shutdownNow();
        super.onDestroy();
    }
}
