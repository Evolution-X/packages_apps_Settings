package com.android.settings.gestures;

import android.content.ContentResolver;
import android.content.Context;
import android.provider.Settings;
import android.text.TextUtils;

import com.android.settings.gestures.GesturePreferenceController;
import com.android.settings.slices.SliceBackgroundWorker;

public class DoubleTapScreenToSleepPreferenceController
        extends GesturePreferenceController {

    private static final String PREF_KEY_VIDEO = "gesture_double_tap_screen_video";

    public DoubleTapScreenToSleepPreferenceController(Context context, String key) {
        super(context, key);
    }

    @Override
    public int getAvailabilityStatus() {
        return 0; // AVAILABLE
    }

    @Override
    protected String getVideoPrefKey() {
        return PREF_KEY_VIDEO;
    }

    @Override
    public boolean isChecked() {
        ContentResolver resolver = mContext.getContentResolver();
        int value = Settings.Secure.getInt(resolver, "double_tap_to_sleep", 0);
        return value == 1;
    }

    @Override
    public boolean setChecked(boolean enabled) {
        ContentResolver resolver = mContext.getContentResolver();
        return Settings.Secure.putInt(
                resolver,
                "double_tap_to_sleep",
                enabled ? 1 : 0
        );
    }

    @Override
    public boolean isSliceable() {
        return TextUtils.equals(
                getPreferenceKey(),
                "gesture_double_tap_screen_to_sleep"
        );
    }

    @Override
    public boolean isPublicSlice() {
        return true;
    }

    @Override
    public Class<? extends SliceBackgroundWorker> getBackgroundWorkerClass() {
        return null; // no background worker
    }

    @Override
    public android.content.IntentFilter getIntentFilter() {
        return super.getIntentFilter();
    }

    @Override
    public boolean hasAsyncUpdate() {
        return super.hasAsyncUpdate();
    }

    @Override
    public boolean useDynamicSliceSummary() {
        return super.useDynamicSliceSummary();
    }
}

