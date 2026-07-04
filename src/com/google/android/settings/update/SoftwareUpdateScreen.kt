package com.google.android.settings.update

import android.app.settings.SettingsEnums
import android.content.Context
import android.content.Intent
import com.android.settings.R
import com.android.settings.core.PreferenceScreenMixin
import com.android.settings.utils.makeLaunchIntent
import com.android.settingslib.metadata.PersistentPreference
import com.android.settingslib.metadata.PreferenceAvailabilityProvider
import com.android.settingslib.metadata.PreferenceHierarchy
import com.android.settingslib.metadata.PreferenceMetadata
import com.android.settingslib.metadata.PreferenceSummaryProvider
import com.android.settingslib.metadata.preferenceHierarchy
import com.android.settingslib.metadata.preferencesapi.preconditions.PreconditionStability
import com.android.settingslib.widget.SettingsThemeHelper
import kotlinx.coroutines.CoroutineScope

class SoftwareUpdateScreen :
    PreferenceScreenMixin, PreferenceAvailabilityProvider, PreferenceSummaryProvider {

    override val availabilityDescription: String =
        "Requires Android 16 (Baklava) or higher with the 'Material Expressive Design' feature enabled."

    override val key: String = "software_update_settings_v2"

    override val title: Int = R.string.software_update_entry_title

    override val purpose: Int = R.string.software_update_settings_v2_purpose

    override val keywords: Int = R.string.keywords_system_update_settings

    override val icon: Int = com.android.settingslib.R.drawable.ic_system_update

    override val highlightMenuKey: Int = R.string.menu_key_system

    override fun getMetricsCategory(): Int = SettingsEnums.SETTINGS_SOFTWARE_UPDATES

    override fun fragmentClass(): Class<out androidx.fragment.app.Fragment> = SoftwareUpdateFragment::class.java

    override fun isAvailable(context: Context): Boolean =
        SettingsThemeHelper.isExpressiveTheme(context)

    override fun getAvailabilityStability(): PreconditionStability = PreconditionStability.UNSTABLE

    override fun getSummary(context: Context): CharSequence {
        return if (SystemUpdatePreferenceController.Companion.isSystemUpdatable(context)) {
            context.getString(R.string.software_update_can_be_updated_header)
        } else {
            context.getString(R.string.software_update_up_to_date_header)
        }
    }

    override fun getLaunchIntent(context: Context, metadata: PreferenceMetadata?): Intent {
        return makeLaunchIntent(
            context,
            SoftwareUpdateActivity::class.java,
            metadata?.key,
        )
    }

    override fun hasCompleteHierarchy(): Boolean = false

    override fun getPreferenceHierarchy(
        context: Context,
        scope: CoroutineScope,
    ): PreferenceHierarchy {
        return preferenceHierarchy(context) {
            +SoftwareUpdateScreenPreference(this@SoftwareUpdateScreen)
        }
    }

    companion object inner

    class SoftwareUpdateScreenPreference(private val screenMetadata: SoftwareUpdateScreen) :
        PreferenceMetadata,
        PreferenceSummaryProvider,
        PreferenceAvailabilityProvider,
        PersistentPreference<String> {

        override val valueType: Class<String> = String::class.java

        override val key: String = "software_update_settings_v2_preference"

        override val purpose: Int = screenMetadata.purpose

        override fun tags(context: Context): Array<String> = arrayOf("metadata_in_ui")

        override fun isEnabled(context: Context): Boolean = screenMetadata.isEnabled(context)

        override fun getSummary(context: Context): CharSequence = screenMetadata.getSummary(context)

        override val indexable: Boolean = false

        override fun isAvailable(context: Context): Boolean = screenMetadata.isAvailable(context)

        override fun getAvailabilityStability(): PreconditionStability =
            screenMetadata.getAvailabilityStability()

        override val availabilityDescription: String =
            screenMetadata.availabilityDescription

        override val supportsWrite: Boolean = false

        override fun storage(context: Context) = createSummaryStorage(context, key)
    }
}
