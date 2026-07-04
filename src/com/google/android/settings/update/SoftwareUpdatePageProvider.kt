package com.google.android.settings.update

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.NavigateNext
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.settings.R
import com.android.settings.system.getSystemUpdateInfo
import com.android.settingslib.spa.framework.common.SettingsPageProvider
import com.android.settingslib.spa.widget.card.SettingsCard
import com.android.settingslib.spa.widget.card.SettingsCardContent
import com.android.settingslib.spa.widget.illustration.Illustration
import com.android.settingslib.spa.widget.illustration.ResourceType
import com.android.settingslib.spa.widget.scaffold.RegularScaffold
import com.android.settingslib.spa.widget.ui.SettingsBody
import com.android.settingslib.spa.widget.ui.SettingsIcon
import com.android.settingslib.spa.widget.ui.SettingsTitle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

object SoftwareUpdatePageProvider : SettingsPageProvider {

    override val name: String = "SoftwareUpdate"

    @Composable
    override fun Page(arguments: Bundle?) {
        val status by rememberSystemUpdateStatus()
        SoftwareUpdatePage(status)
    }

    @Composable
    fun rememberSystemUpdateStatus(): State<Int> {
        val context = LocalContext.current
        val statusFlow = remember {
            flow {
                emit(getSystemUpdateStatus(context))
            }.flowOn(Dispatchers.Default)
        }
        return statusFlow.collectAsStateWithLifecycle(0)
    }

    private suspend fun getSystemUpdateStatus(context: Context): Int {
        val status = context.getSystemUpdateInfo()?.getInt("status")
        Log.d("SoftwareUpdate", "status $status")
        return if (status == 2 || status == 3 || status == 4 || status == 5) 1 else 0
    }
}

@Composable
fun SoftwareUpdatePage(status: Int) {
    RegularScaffold(title = getUpdateHeader(status)) {
        Column(
            modifier = Modifier,
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.Start,
        ) {
            val isUpdated = status == 0
            Illustration(
                resId = if (isUpdated) {
                    R.drawable.software_update_illustration_updated
                } else {
                    R.drawable.software_update_illustration_pending
                },
                resourceType = ResourceType.IMAGE,
                modifier = Modifier
                    .testTag(if (isUpdated) "illustration_updated" else "illustration_pending")
                    .padding(bottom = 12.dp),
            )
            SettingsCard {
                SettingsCardContent {
                    SystemUpdatePreference(status)
                }
                SettingsCardContent {
                    AppUpdatesPreference()
                }
            }
        }
    }
}

@Composable
internal fun getUpdateHeader(status: Int): String {
    val resId =
        if (status == 0) {
            R.string.software_update_up_to_date_header
        } else {
            R.string.software_update_can_be_updated_header
        }
    return stringResource(resId)
}

@Composable
private fun MainIcon(icon: ImageVector) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier.size(44.dp),
        tint = MaterialTheme.colorScheme.primary,
    )
}

@Composable
fun PreferenceItem(
    isPending: Boolean = false,
    title: String,
    summary: () -> CharSequence,
    icon: ImageVector,
    statusIcon: Int = 2, // inferred sentinel meaning "no status badge"
    onClick: () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp, 54.dp)
                    .padding(top = 13.dp),
            ) {
                MainIcon(icon)
                when (statusIcon) {
                    0 -> StatusIcon(
                        icon = Icons.Outlined.Done,
                        color = colorResource(R.color.security_green),
                        testTag = "system_update_updated",
                    )
                    1 -> StatusIcon(
                        icon = Icons.Outlined.Download,
                        color = colorResource(R.color.security_yellow),
                        testTag = "system_update_pending",
                    )
                    else -> Unit
                }
            }
            Column(
                modifier = Modifier.padding(top = 4.dp),
                verticalArrangement = Arrangement.Top,
                horizontalAlignment = Alignment.Start,
            ) {
                SettingsTitle(title)
                SettingsBody(summary())
            }
        }
        Box(
            modifier = Modifier.size(40.dp),
            contentAlignment = Alignment.Center,
        ) {
            SettingsIcon(Icons.AutoMirrored.Outlined.NavigateNext)
        }
    }
}

@Composable
private fun StatusIcon(icon: ImageVector, color: Color, testTag: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(1.dp),
        contentAlignment = Alignment.BottomEnd,
    ) {
        Surface(
            modifier = Modifier
                .size(18.dp)
                .padding(1.dp),
            shape = CircleShape,
            color = color,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier
                    .padding(1.dp)
                    .testTag(testTag),
                tint = MaterialTheme.colorScheme.surface,
            )
        }
    }
}
