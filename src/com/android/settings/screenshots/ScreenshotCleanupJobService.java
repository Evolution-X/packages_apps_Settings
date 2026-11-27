/*
 * Copyright (C) 2026 The Evolution X Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.screenshots;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.os.UserHandle;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Log;

import java.util.concurrent.TimeUnit;

/**
 * Deletes screenshots that are older than the retention period selected in Evolver.
 */
public class ScreenshotCleanupJobService extends JobService {

    public static final String SETTING_KEY = "screenshot_delete_after_days";

    private static final String TAG = "ScreenshotCleanup";
    private static final int JOB_ID = 0x5343524E;
    private static final int MIN_DAYS = 1;
    private static final int MAX_DAYS = 7;

    /**
     * Schedules or cancels the cleanup job for the supplied setting value.
     */
    public static void schedule(Context context, String days) {
        int value = 0;
        if (days != null) {
            try {
                value = Integer.parseInt(days);
            } catch (NumberFormatException ignored) {
                // Treat malformed values as disabled.
            }
        }
        schedule(context, value);
    }

    /**
     * Schedules the cleanup job for 1-7 days, or cancels it for any other value.
     */
    public static void schedule(Context context, int days) {
        final JobScheduler jobScheduler = context.getSystemService(JobScheduler.class);
        if (jobScheduler == null) {
            Log.w(TAG, "JobScheduler unavailable");
            return;
        }

        if (days < MIN_DAYS || days > MAX_DAYS) {
            jobScheduler.cancel(JOB_ID);
            return;
        }

        final JobInfo job = new JobInfo.Builder(JOB_ID,
                new ComponentName(context, ScreenshotCleanupJobService.class))
                .setPeriodic(TimeUnit.DAYS.toMillis(1))
                .setPersisted(true)
                .build();
        if (jobScheduler.schedule(job) != JobScheduler.RESULT_SUCCESS) {
            Log.w(TAG, "Unable to schedule screenshot cleanup");
        }
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        new Thread(() -> {
            try {
                deleteOldScreenshots();
            } finally {
                jobFinished(params, false);
            }
        }, "ScreenshotCleanup").start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return true;
    }

    private void deleteOldScreenshots() {
        final int days = Settings.System.getIntForUser(getContentResolver(), SETTING_KEY, 0,
                UserHandle.myUserId());
        if (days < MIN_DAYS || days > MAX_DAYS) {
            return;
        }

        final long cutoff = (System.currentTimeMillis() / 1000)
                - TimeUnit.DAYS.toSeconds(days);
        final String screenshotsPath = Environment.DIRECTORY_PICTURES
                + "/" + Environment.DIRECTORY_SCREENSHOTS + "/";
        final Uri collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        final String selection = MediaStore.MediaColumns.RELATIVE_PATH + " LIKE ?"
                + " AND " + MediaStore.MediaColumns.DATE_ADDED + " < ?"
                + " AND " + MediaStore.MediaColumns.IS_PENDING + " = 0";
        final String[] selectionArgs = {
                screenshotsPath + "%",
                Long.toString(cutoff)
        };

        try {
            final ContentResolver resolver = getContentResolver();
            final int deleted = resolver.delete(collection, selection, selectionArgs);
            Log.i(TAG, "Deleted " + deleted + " screenshot(s) older than " + days + " day(s)");
        } catch (Exception e) {
            Log.e(TAG, "Unable to delete old screenshots", e);
        }
    }
}
