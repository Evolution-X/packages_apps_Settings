package com.google.android.settings.fuelgauge.batterysaver;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.util.Pair;

public abstract class FlipendoUtils {
    static final Uri FLIPENDO_ENABLED_OBSERVABLE_URI =
            Uri.parse("content://com.google.android.flipendo.api/get_flipendo_state");
    public static final String FLIPENDO_IS_AGGRESSIVE_KEY = "is_flipendo_aggressive";
    public static final String FLIPENDO_STATE_AUTHORITY = "com.google.android.flipendo.api";
    public static final String FLIPENDO_STATE_METHOD = "get_flipendo_state";

    public static Pair getFlipendoState(Context context) {
        Bundle bundle = null;
        try {
            bundle =
                    context.getApplicationContext()
                            .getContentResolver()
                            .call(
                                    FLIPENDO_STATE_AUTHORITY,
                                    FLIPENDO_STATE_METHOD,
                                    (String) null,
                                    (Bundle) null);
        } catch (IllegalArgumentException e) {
            Log.e("FlipendoUtils", "getFlipendoState() failed", e);
        }
        if (bundle == null) {
            bundle = new Bundle();
        }
        return new Pair(
                Boolean.valueOf(bundle.getBoolean(FLIPENDO_IS_AGGRESSIVE_KEY, false)),
                Boolean.valueOf(bundle.getBoolean("flipendo_state", false)));
    }
}
