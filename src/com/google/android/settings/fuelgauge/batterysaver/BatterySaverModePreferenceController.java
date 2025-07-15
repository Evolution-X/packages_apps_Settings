package com.google.android.settings.fuelgauge.batterysaver;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;
import android.util.Pair;
import android.view.View;

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
    SelectorWithWidgetPreference mBasicPreference;
    private final ContentObserver mContentObserver;
    boolean mCurrentBatterySaverMode;
    SelectorWithWidgetPreference mExtremePreference;
    private HandlerThread mHandlerThread;
    boolean mIsFlipendoAggressiveMode;
    boolean mIsFlipendoEnabled;

    @Override
    public int getAvailabilityStatus() {
        return AVAILABLE;
    }

    @Override
    public Class getBackgroundWorkerClass() {
        return super.getBackgroundWorkerClass();
    }

    @Override
    public IntentFilter getIntentFilter() {
        return super.getIntentFilter();
    }

    @Override
    public int getSliceHighlightMenuRes() {
        return super.getSliceHighlightMenuRes();
    }

    @Override
    public boolean hasAsyncUpdate() {
        return super.hasAsyncUpdate();
    }

    @Override
    public boolean isPublicSlice() {
        return super.isPublicSlice();
    }

    @Override
    public boolean isSliceable() {
        return super.isSliceable();
    }

    @Override
    public boolean useDynamicSliceSummary() {
        return super.useDynamicSliceSummary();
    }

    public BatterySaverModePreferenceController(Context context, String str) {
        super(context, str);
        this.mContentObserver =
                new ContentObserver(new Handler(Looper.getMainLooper())) {
                    @Override
                    public void onChange(boolean z) {
                        BatterySaverModePreferenceController.this.refreshFlipendoStates();
                        BatterySaverModePreferenceController batterySaverModePreferenceController =
                                BatterySaverModePreferenceController.this;
                        if (batterySaverModePreferenceController.mIsFlipendoAggressiveMode) {
                            return;
                        }
                        batterySaverModePreferenceController.updateSaverModeSelection(
                                !batterySaverModePreferenceController.mIsFlipendoEnabled);
                    }
                };
    }

    @Override
    public void displayPreference(PreferenceScreen preferenceScreen) {
        super.displayPreference(preferenceScreen);
        PreferenceCategory preferenceCategory =
                (PreferenceCategory) preferenceScreen.findPreference(getPreferenceKey());
        if (preferenceCategory != null) {
            refreshFlipendoStates();
            initRadioButton(preferenceCategory);
        }
    }

    @Override
    public void onRadioButtonClicked(SelectorWithWidgetPreference selectorWithWidgetPreference) {
        String key = selectorWithWidgetPreference.getKey();
        if (key.equals("extreme_battery_saver")) {
            updateSaverModeSelection(false);
        } else if (key.equals("basic_battery_saver")) {
            updateSaverModeSelection(true);
        }
        if (this.mIsFlipendoEnabled) {
            this.mCurrentBatterySaverMode = this.mExtremePreference.isChecked();
        }
    }

    @Override
    public void onResume() {
        try {
            boolean z = false;
            this.mContext
                    .getContentResolver()
                    .registerContentObserver(
                            FlipendoUtils.FLIPENDO_ENABLED_OBSERVABLE_URI,
                            false,
                            this.mContentObserver);
            SelectorWithWidgetPreference selectorWithWidgetPreference = this.mBasicPreference;
            if (selectorWithWidgetPreference != null) {
                this.mCurrentBatterySaverMode = selectorWithWidgetPreference.isChecked();
            }
            refreshFlipendoStates();
            if (!this.mIsFlipendoEnabled && !this.mIsFlipendoAggressiveMode) {
                z = true;
            }
            updateSaverModeSelection(z);
        } catch (Exception e) {
            Log.e(TAG, "onResume() failed", e);
        }
    }

    @Override
    public void onPause() {
        this.mContext.getContentResolver().unregisterContentObserver(this.mContentObserver);
        if (this.mCurrentBatterySaverMode == this.mBasicPreference.isChecked()) {
            return;
        }
        if (!this.mIsFlipendoAggressiveMode
                && this.mIsFlipendoEnabled
                && this.mExtremePreference.isChecked()) {
            return;
        }
        HandlerThread handlerThread = new HandlerThread(TAG);
        this.mHandlerThread = handlerThread;
        handlerThread.start();
        new Handler(this.mHandlerThread.getLooper())
                .post(
                        new Runnable() {
                            @Override
                            public final void run() {
                                updateBatterySaverMode(
                                        BatterySaverModePreferenceController.this.mContext,
                                        !BatterySaverModePreferenceController.this.mBasicPreference
                                                        .isChecked()
                                                ? 1
                                                : 0);
                            }
                        });
    }

    private void initRadioButton(PreferenceCategory preferenceCategory) {
        SelectorWithWidgetPreference selectorWithWidgetPreference =
                (SelectorWithWidgetPreference)
                        preferenceCategory.findPreference("basic_battery_saver");
        this.mBasicPreference = selectorWithWidgetPreference;
        if (selectorWithWidgetPreference != null) {
            selectorWithWidgetPreference.setExtraWidgetOnClickListener(null);
            this.mBasicPreference.setOnClickListener(this);
            this.mBasicPreference.setChecked(!this.mIsFlipendoAggressiveMode);
        }
        SelectorWithWidgetPreference selectorWithWidgetPreference2 =
                (SelectorWithWidgetPreference)
                        preferenceCategory.findPreference("extreme_battery_saver");
        this.mExtremePreference = selectorWithWidgetPreference2;
        if (selectorWithWidgetPreference2 != null) {
            selectorWithWidgetPreference2.setExtraWidgetOnClickListener(
                    new View.OnClickListener() {
                        @Override
                        public final void onClick(View view) {
                            BatterySaverModePreferenceController.this.launchFlipendo();
                        }
                    });
            this.mExtremePreference.setOnClickListener(this);
            this.mExtremePreference.setChecked(this.mIsFlipendoAggressiveMode);
        }
    }

    private void updateSaverModeSelection(boolean z) {
        SelectorWithWidgetPreference selectorWithWidgetPreference = this.mBasicPreference;
        if (selectorWithWidgetPreference == null || this.mExtremePreference == null) {
            return;
        }
        selectorWithWidgetPreference.setChecked(z);
        this.mExtremePreference.setChecked(!z);
    }

    private void refreshFlipendoStates() {
        Pair flipendoState = FlipendoUtils.getFlipendoState(this.mContext);
        this.mIsFlipendoAggressiveMode = ((Boolean) flipendoState.first).booleanValue();
        this.mIsFlipendoEnabled = ((Boolean) flipendoState.second).booleanValue();
    }

    private void updateBatterySaverMode(Context context, int i) {
        Bundle bundle = new Bundle();
        bundle.putInt("update_flipendo_mode", i);
        try {
            context.getContentResolver()
                    .call(
                            FlipendoUtils.FLIPENDO_STATE_AUTHORITY,
                            "update_flipendo_mode_method",
                            (String) null,
                            bundle);
        } catch (Exception e) {
            Log.e(TAG, "updateBatterySaverMode() failed", e);
        }
        HandlerThread handlerThread = this.mHandlerThread;
        if (handlerThread != null) {
            handlerThread.quitSafely();
            this.mHandlerThread = null;
        }
    }

    private void launchFlipendo() {
        try {
            this.mContext.startActivity(
                    new Intent("android.settings.batterysaver.flipendo")
                            .setPackage("com.google.android.flipendo"));
        } catch (Exception e) {
            Log.e(TAG, "launchFlipendo() failed", e);
        }
    }
}
