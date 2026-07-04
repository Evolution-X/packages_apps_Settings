package com.google.android.settings.update

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import com.android.settings.R
import com.android.settings.system.ClientInitiatedActionRepository
import com.android.settings.system.SystemUpdateRepository
import com.android.settingslib.DeviceInfoUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun SystemUpdatePreference(currentStatus: Int) {
    val context = LocalContext.current
    val scope: CoroutineScope = rememberCoroutineScope()
    val title = stringResource(R.string.system_update_entry_title)

    PreferenceItem(
        title = title,
        summary = { updateInfo(context, currentStatus) },
        icon = ImageVector.vectorResource(R.drawable.software_update_system_update),
        statusIcon = currentStatus,
        onClick = { scope.launch(Dispatchers.Default) { onSystemUpdateClick(context) } },
    )
}

private fun onSystemUpdateClick(context: Context) {
    ClientInitiatedActionRepository(context).onSystemUpdate()
    val intent: Intent? = SystemUpdateRepository(context).getSystemUpdateIntent()
    if (intent != null) {
        context.startActivity(intent)
    }
}

private fun updateInfo(context: Context, status: Int): String {
    if (status == 0) {
        val securityPatch = DeviceInfoUtils.getSecurityPatch()
        return if (securityPatch != null) {
            context.getString(R.string.software_update_up_to_specific_date_summary, securityPatch)
        } else {
            ""
        }
    }
    return context.getString(R.string.software_update_pending_update_summary)
}
