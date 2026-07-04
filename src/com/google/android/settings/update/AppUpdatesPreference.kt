package com.google.android.settings.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import com.android.settings.R

@Composable
fun AppUpdatesPreference() {
    val context = LocalContext.current
    val summary = stringResource(R.string.play_application_detail_indication_summary)
    val title = stringResource(R.string.play_application_update_entry_title)

    PreferenceItem(
        title = title,
        summary = { summary },
        icon = ImageVector.vectorResource(R.drawable.software_update_app_update),
        onClick = { onAppUpdateClick(context) },
    )
}

private fun onAppUpdateClick(context: Context) {
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/apps")))
}
