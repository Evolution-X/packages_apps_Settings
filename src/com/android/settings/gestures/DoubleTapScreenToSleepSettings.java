package com.android.settings.gestures;

import com.android.settings.dashboard.DashboardFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.internal.logging.nano.MetricsProto;

public class DoubleTapScreenToSleepSettings extends DashboardFragment {

    // Search index provider used by Settings search system
    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER;

    static {
        // Registers the XML resource that describes this preference screen
        SEARCH_INDEX_DATA_PROVIDER =
                new BaseSearchIndexProvider(
                        com.android.settings.R.xml.double_tap_screen_to_sleep_settings
                );
    }

    public DoubleTapScreenToSleepSettings() {
        super();
    }

    @Override
    protected String getLogTag() {
        return "DoubleTapScreenToSleepSettings";
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.MICA;
    }

    @Override
    protected int getPreferenceScreenResId() {
        // XML that defines the UI for this settings page
        return com.android.settings.R.xml.double_tap_screen_to_sleep_settings;
    }
}
