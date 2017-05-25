/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settings.dashboard.suggestions;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.support.annotation.NonNull;
import android.util.Log;

import com.android.internal.logging.nano.MetricsProto;
import com.android.settings.core.instrumentation.MetricsFeatureProvider;
import com.android.settings.overlay.FeatureFactory;
import com.android.settingslib.drawer.Tile;
import com.android.settingslib.suggestions.SuggestionParser;

import java.util.List;

public class SuggestionFeatureProviderImpl implements SuggestionFeatureProvider {

    private static final String TAG = "SuggestionFeature";
    private static final int EXCLUSIVE_SUGGESTION_MAX_COUNT = 3;

    private final SuggestionRanker mSuggestionRanker;
    private final MetricsFeatureProvider mMetricsFeatureProvider;

    @Override
    public boolean isSmartSuggestionEnabled(Context context) {
        return false;
    }

    @Override
    public boolean isPresent(@NonNull ComponentName component) {
        return false;
    }

    @Override
    public boolean isSuggestionCompleted(Context context, @NonNull ComponentName component) {
        return false;
    }


    public SuggestionFeatureProviderImpl(Context context) {
        final Context appContext = context.getApplicationContext();
        mSuggestionRanker = new SuggestionRanker(
                new SuggestionFeaturizer(new EventStore(appContext)));
        mMetricsFeatureProvider = FeatureFactory.getFactory(appContext)
                .getMetricsFeatureProvider();
    }

    @Override
    public void rankSuggestions(final List<Tile> suggestions, List<String> suggestionIds) {
        mSuggestionRanker.rankSuggestions(suggestions, suggestionIds);
    }

    @Override
    public void filterExclusiveSuggestions(List<Tile> suggestions) {
        if (suggestions == null) {
            return;
        }
        for (int i = suggestions.size() - 1; i >= EXCLUSIVE_SUGGESTION_MAX_COUNT; i--) {
            Log.d(TAG, "Removing exclusive suggestion");
            suggestions.remove(i);
        }
    }

    @Override
    public void dismissSuggestion(Context context, SuggestionParser parser, Tile suggestion) {
        if (parser == null || suggestion == null || context == null) {
            return;
        }
        mMetricsFeatureProvider.action(
                context, MetricsProto.MetricsEvent.ACTION_SETTINGS_DISMISS_SUGGESTION,
                getSuggestionIdentifier(context, suggestion));

        final boolean isSmartSuggestionEnabled = isSmartSuggestionEnabled(context);
        if (!parser.dismissSuggestion(suggestion, isSmartSuggestionEnabled)) {
            return;
        }
        context.getPackageManager().setComponentEnabledSetting(
                suggestion.intent.getComponent(),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP);
    }

    @Override
    public String getSuggestionIdentifier(Context context, Tile suggestion) {
        if (suggestion.intent == null || suggestion.intent.getComponent() == null
                || context == null) {
            return "unknown_suggestion";
        }
        String packageName = suggestion.intent.getComponent().getPackageName();
        if (packageName.equals(context.getPackageName())) {
            // Since Settings provides several suggestions, fill in the class instead of the
            // package for these.
            packageName = suggestion.intent.getComponent().getClassName();
        }
        return packageName;
    }

}
