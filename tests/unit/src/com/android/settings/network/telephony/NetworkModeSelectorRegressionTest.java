/*
 * Copyright (C) 2026 The Evolution X Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 */

package com.android.settings.network.telephony;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.content.res.XmlResourceParser;
import android.telephony.SubscriptionManager;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.settings.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.xmlpull.v1.XmlPullParser;

@RunWith(AndroidJUnit4.class)
public class NetworkModeSelectorRegressionTest {

    @Test
    public void mobileNetworkSettings_keepsDetailedNetworkModePreference() throws Exception {
        final Context context = ApplicationProvider.getApplicationContext();
        boolean found = false;

        try (XmlResourceParser parser =
                context.getResources().getXml(R.xml.mobile_network_settings)) {
            int event;
            while ((event = parser.next()) != XmlPullParser.END_DOCUMENT) {
                if (event != XmlPullParser.START_TAG) {
                    continue;
                }
                if ("com.android.settings.network.telephony.NetworkModePreference"
                        .equals(parser.getName())) {
                    found = true;
                    break;
                }
            }
        }

        assertThat(found).isTrue();
    }

    @Test
    public void invalidSubscription_hidesNetworkModeSelector() {
        final Context context = ApplicationProvider.getApplicationContext();

        assertThat(
                EnabledNetworkModePreferenceControllerHelperKt.getNetworkModePreferenceType(
                        context, SubscriptionManager.INVALID_SUBSCRIPTION_ID))
                .isEqualTo(NetworkModePreferenceType.None);
    }
}
