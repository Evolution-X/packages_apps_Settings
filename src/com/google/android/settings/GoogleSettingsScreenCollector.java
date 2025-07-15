package com.google.android.settings;

import android.content.Context;

import com.android.settingslib.metadata.FixedArrayMap;
import com.android.settingslib.metadata.PreferenceScreenMetadata;
import com.android.settingslib.metadata.PreferenceScreenMetadataFactory;

import com.google.android.settings.fuelgauge.batterysaver.BatterySaverGoogleScreen;

public abstract class GoogleSettingsScreenCollector {
    public static FixedArrayMap get() {
        return new FixedArrayMap(
                1,
                (obj) ->
                        GoogleSettingsScreenCollector.init((FixedArrayMap.OrderedInitializer) obj));
    }

    private static void init(FixedArrayMap.OrderedInitializer orderedInitializer) {
        orderedInitializer.put(
                "battery_saver_screen",
                new PreferenceScreenMetadataFactory() {
                    @Override
                    public PreferenceScreenMetadata create(Context context) {
                        return new BatterySaverGoogleScreen();
                    }
                });
    }
}
