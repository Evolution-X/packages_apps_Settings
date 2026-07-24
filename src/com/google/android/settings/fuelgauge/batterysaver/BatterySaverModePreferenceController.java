package com.google.android.settings.fuelgauge.batterysaver;

import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;
import android.util.Pair;

import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;

import com.android.settings.core.BasePreferenceController;
import com.android.settingslib.core.lifecycle.LifecycleObserver;
import com.android.settingslib.core.lifecycle.events.OnPause;
import com.android.settingslib.core.lifecycle.events.OnResume;
import com.android.settingslib.widget.SelectorWithWidgetPreference;

public class BatterySaverModePreferenceController extends BasePreferenceController
        implements SelectorWithWidgetPreference.OnClickListener,
                LifecycleObserver,
                OnResume,
                OnPause {

    private static final String TAG = "BatterySaverModePreferenceController";
    private static final String KEY_BASIC = "basic_battery_saver";
    private static final String KEY_EXTREME = "extreme_battery_saver";
    private static final String FLIPENDO_ACTION = "android.settings.batterysaver.flipendo";
    private static final String FLIPENDO_PACKAGE = "com.google.android.flipendo";
    private static final String BUNDLE_KEY_MODE = "update_flipendo_mode";
    private static final String FLIPENDO_METHOD = "update_flipendo_mode_method";

    private final boolean mIsFlipendoInstalled;
    private final ContentObserver mContentObserver;

    SelectorWithWidgetPreference mBasicPreference;
    SelectorWithWidgetPreference mExtremePreference;
    boolean mCurrentBatterySaverMode;
    boolean mIsFlipendoAggressiveMode;
    boolean mIsFlipendoEnabled;
    private HandlerThread mHandlerThread;

    public BatterySaverModePreferenceController(Context context, String key) {
        super(context, key);
        mIsFlipendoInstalled = FlipendoUtils.isFlipendoInstalled(context);
        mContentObserver =
                new ContentObserver(new Handler(Looper.getMainLooper())) {
                    @Override
                    public void onChange(boolean selfChange) {
                        refreshFlipendoStates();
                        if (!mIsFlipendoAggressiveMode) {
                            updateSaverModeSelection(!mIsFlipendoEnabled);
                        }
                    }
                };
    }

    @Override
    public int getAvailabilityStatus() {
        return AVAILABLE;
    }

    @Override
    public void displayPreference(PreferenceScreen preferenceScreen) {
        super.displayPreference(preferenceScreen);
        PreferenceCategory category =
                (PreferenceCategory) preferenceScreen.findPreference(getPreferenceKey());
        if (!mIsFlipendoInstalled) {
            category.setVisible(false);
        } else if (category != null) {
            refreshFlipendoStates();
            initRadioButtons(category);
        }
    }

    @Override
    public void onRadioButtonClicked(SelectorWithWidgetPreference preference) {
        String key = preference.getKey();
        if (KEY_EXTREME.equals(key)) {
            updateSaverModeSelection(false);
        } else if (KEY_BASIC.equals(key)) {
            updateSaverModeSelection(true);
        }
        if (mIsFlipendoEnabled) {
            mCurrentBatterySaverMode = mExtremePreference.isChecked();
        }
    }

    @Override
    public void onResume() {
        if (!mIsFlipendoInstalled) return;
        try {
            mContext.getContentResolver()
                    .registerContentObserver(
                            FlipendoUtils.FLIPENDO_ENABLED_OBSERVABLE_URI, false, mContentObserver);
            if (mBasicPreference != null) {
                mCurrentBatterySaverMode = mBasicPreference.isChecked();
            }
            refreshFlipendoStates();
            updateSaverModeSelection(!mIsFlipendoEnabled && !mIsFlipendoAggressiveMode);
        } catch (Exception e) {
            Log.e(TAG, "onResume() failed", e);
        }
    }

    @Override
    public void onPause() {
        if (!mIsFlipendoInstalled) return;
        mContext.getContentResolver().unregisterContentObserver(mContentObserver);
        if (mCurrentBatterySaverMode == mBasicPreference.isChecked()) return;
        if (!mIsFlipendoAggressiveMode && mIsFlipendoEnabled && mExtremePreference.isChecked())
            return;

        mHandlerThread = new HandlerThread(TAG);
        mHandlerThread.start();
        new Handler(mHandlerThread.getLooper())
                .post(() -> updateBatterySaverMode(mContext, mBasicPreference.isChecked() ? 0 : 1));
    }

    private void initRadioButtons(PreferenceCategory category) {
        mBasicPreference = (SelectorWithWidgetPreference) category.findPreference(KEY_BASIC);
        if (mBasicPreference != null) {
            mBasicPreference.setExtraWidgetOnClickListener(null);
            mBasicPreference.setOnClickListener(this);
            mBasicPreference.setChecked(!mIsFlipendoAggressiveMode);
        }

        mExtremePreference =
                (SelectorWithWidgetPreference) category.findPreference("extreme_battery_saver");
        if (mExtremePreference != null) {
            mExtremePreference.setExtraWidgetOnClickListener(view -> launchFlipendo());
            mExtremePreference.setOnClickListener(this);
            mExtremePreference.setChecked(mIsFlipendoAggressiveMode);
        }
    }

    private void updateSaverModeSelection(boolean basicSelected) {
        if (mBasicPreference == null || mExtremePreference == null) return;
        mBasicPreference.setChecked(basicSelected);
        mExtremePreference.setChecked(!basicSelected);
    }

    private void refreshFlipendoStates() {
        Pair<Boolean, Boolean> state = FlipendoUtils.getFlipendoState(mContext);
        mIsFlipendoAggressiveMode = state.first;
        mIsFlipendoEnabled = state.second;
    }

    private void updateBatterySaverMode(Context context, int mode) {
        Bundle bundle = new Bundle();
        bundle.putInt(BUNDLE_KEY_MODE, mode);
        try {
            context.getContentResolver()
                    .call(FlipendoUtils.FLIPENDO_STATE_AUTHORITY, FLIPENDO_METHOD, null, bundle);
        } catch (Exception e) {
            Log.e(TAG, "updateBatterySaverMode() failed", e);
        }
        if (mHandlerThread != null) {
            mHandlerThread.quitSafely();
            mHandlerThread = null;
        }
    }

    private void launchFlipendo() {
        try {
            mContext.startActivity(new Intent(FLIPENDO_ACTION).setPackage(FLIPENDO_PACKAGE));
        } catch (Exception e) {
            Log.e(TAG, "launchFlipendo() failed", e);
        }
    }
}
