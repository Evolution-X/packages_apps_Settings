package com.google.android.settings;

import com.android.settingslib.metadata.FixedArrayMap;
import com.android.settingslib.metadata.PreferenceScreenMetadataFactory;

import com.google.android.settings.fuelgauge.batterysaver.AdaptiveBatteryScreen;
import com.google.android.settings.fuelgauge.batterysaver.BatterySaverGoogleScreen;
import com.google.android.settings.fuelgauge.batterysaver.BatterySaverScheduleScreen;
import com.google.android.settings.update.SoftwareUpdateScreen;

public abstract class SettingsGoogleScreenCollector {

    public static FixedArrayMap<String, PreferenceScreenMetadataFactory> get() {
        return new FixedArrayMap<>(4, SettingsGoogleScreenCollector::init);
    }

    private static void init(
            FixedArrayMap.OrderedInitializer<String, PreferenceScreenMetadataFactory> initializer) {
        initializer.put(
                "adaptive_battery_entry",
                (PreferenceScreenMetadataFactory) AdaptiveBatteryScreen::new);
        initializer.put(
                "battery_saver_schedule",
                (PreferenceScreenMetadataFactory) context -> new BatterySaverScheduleScreen());
        initializer.put(
                "battery_saver_screen",
                (PreferenceScreenMetadataFactory) context -> new BatterySaverGoogleScreen());
        initializer.put(
                "software_update_settings_v2",
                (PreferenceScreenMetadataFactory) context -> new SoftwareUpdateScreen());
    }
}
