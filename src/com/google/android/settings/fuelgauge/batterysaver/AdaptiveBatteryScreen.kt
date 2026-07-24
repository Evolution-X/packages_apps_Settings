package com.google.android.settings.fuelgauge.batterysaver

import android.app.settings.SettingsEnums
import android.content.Context
import android.content.Intent
import androidx.fragment.app.Fragment
import com.android.settings.CatalystFragment
import com.android.settings.CatalystSettingsActivity
import com.android.settings.R
import com.android.settings.core.PreferenceScreenMixin
import com.android.settings.utils.makeLaunchIntent
import com.android.settingslib.datastore.HandlerExecutor
import com.android.settingslib.datastore.KeyValueStore
import com.android.settingslib.datastore.KeyedObserver
import com.android.settingslib.metadata.PreferenceAvailabilityProvider
import com.android.settingslib.metadata.PreferenceHierarchy
import com.android.settingslib.metadata.PreferenceLifecycleContext
import com.android.settingslib.metadata.PreferenceLifecycleProvider
import com.android.settingslib.metadata.PreferenceMetadata
import com.android.settingslib.metadata.PreferenceSummaryProvider
import com.android.settingslib.metadata.preferenceHierarchy
import com.android.settingslib.metadata.preferencesapi.preconditions.PreconditionStability
import kotlinx.coroutines.CoroutineScope

class AdaptiveBatteryScreen(context: Context) :
    PreferenceScreenMixin,
    PreferenceAvailabilityProvider,
    PreferenceSummaryProvider,
    PreferenceLifecycleProvider {

    private val adaptiveBatteryStore: KeyValueStore =
        AdaptiveBatteryPreference.getAdaptiveBatteryDataStore(context)

    private var keyedObserver: KeyedObserver<String>? = null

    override val availabilityDescription: String = "The device must support adaptive battery."

    override val key: String = "adaptive_battery_entry"

    override val purpose: Int = R.string.adaptive_battery_entry_purpose

    override val title: Int = R.string.smart_battery_title

    override val keywords: Int = R.string.keywords_battery_adaptive_preferences

    override fun getMetricsCategory(): Int = SettingsEnums.FUELGAUGE_ADAPTIVE_BATTERY

    override val highlightMenuKey: Int = R.string.menu_key_battery

    override val indexable: Boolean = true

    override fun hasCompleteHierarchy(): Boolean = true

    override fun isFlagEnabled(context: Context): Boolean = true

    override fun fragmentClass(): Class<out Fragment>? = CatalystFragment::class.java

    override fun getLaunchIntent(context: Context, metadata: PreferenceMetadata?): Intent =
        makeLaunchIntent(context, AdaptiveBatteryTrampoline::class.java, metadata?.key)

    override fun getSummary(context: Context): CharSequence {
        val isOff = adaptiveBatteryStore.getBoolean("adaptive_battery_management_enabled") == false
        return context.getString(if (isOff) R.string.switch_off_text else R.string.switch_on_text)
    }

    override fun getPreferenceHierarchy(
        context: Context,
        coroutineScope: CoroutineScope,
    ): PreferenceHierarchy =
        preferenceHierarchy(context) {
            +AdaptiveBatteryTopIntroPreference()
            +AdaptiveBatteryIllustrationPreference()
            +AdaptiveBatteryPreference(adaptiveBatteryStore)
        }

    override fun onCreate(context: PreferenceLifecycleContext) {
        if (isEntryPoint(context)) {
            keyedObserver =
                KeyedObserver<String> { _, _ -> context.notifyPreferenceChange(key) }
                    .also { observer ->
                        adaptiveBatteryStore.addObserver(
                            "adaptive_battery_management_enabled",
                            observer,
                            HandlerExecutor.main,
                        )
                    }
        }
    }

    override fun onDestroy(context: PreferenceLifecycleContext) {
        if (isEntryPoint(context)) {
            adaptiveBatteryStore.removeObserver(
                "adaptive_battery_management_enabled",
                checkNotNull(keyedObserver) { "keyedObserver" },
            )
        }
    }

    override fun getAvailabilityStability(): PreconditionStability =
        PreconditionStability.STABLE_UNTIL_APK_UPDATE

    override fun isAvailable(context: Context): Boolean =
        Companion.isAdaptiveBatteryAvailable(context)

    companion object {
        fun isAdaptiveBatteryAvailable(context: Context): Boolean =
            context.resources.getBoolean(com.android.internal.R.bool.config_smart_battery_available)
    }
}

class AdaptiveBatteryTrampoline : CatalystSettingsActivity("adaptive_battery_entry")
