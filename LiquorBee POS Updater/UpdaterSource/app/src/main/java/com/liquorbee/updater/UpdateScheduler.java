package com.liquorbee.updater;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;

public final class UpdateScheduler {
    static final int JOB_ID = 48101;
    private UpdateScheduler() { }

    public static boolean reconcile(Context context) {
        JobScheduler scheduler = context.getSystemService(JobScheduler.class);
        if (scheduler == null) return false;
        if (!new UpdateStore(context).monitoringEnabled()) {
            scheduler.cancel(JOB_ID);
            return true;
        }
        if (scheduler.getPendingJob(JOB_ID) != null) return true;
        JobInfo job = new JobInfo.Builder(JOB_ID, new ComponentName(context, UpdateJobService.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(UpdateConfig.CHECK_INTERVAL_MS)
                .setPersisted(true)
                .setBackoffCriteria(15L * 60L * 1000L, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
                .build();
        return scheduler.schedule(job) == JobScheduler.RESULT_SUCCESS;
    }
}
