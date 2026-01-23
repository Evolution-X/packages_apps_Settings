/*
 * Copyright (C) 2026 AxionOS
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
package com.android.settings.display

import android.app.WallpaperManager
import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.SystemProperties
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.ColorUtils
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Adb
import androidx.compose.material.icons.filled.AirplanemodeActive
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import com.android.axion.compose.preferences.PreferencePosition
import com.android.axion.compose.preferences.SliderPreference
import com.android.axion.compose.preferences.SwitchPreference
import com.android.axion.compose.theme.AxionTheme
import com.android.settings.R
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import kotlin.math.sin
import kotlin.math.roundToInt

class BlurSettings : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                AxionTheme {
                    BlurSettingsScreen()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        activity?.title = getString(com.android.settingslib.R.string.window_blurs)
    }

    @Composable
    fun BlurSettingsScreen() {
        val context = LocalContext.current
        val cr = context.contentResolver

        val blurEnabledByDefault = SystemProperties.getBoolean("ro.custom.blur.enable", false)

        var blursEnabled by remember {
            mutableStateOf(Settings.Global.getInt(cr, Settings.Global.DISABLE_WINDOW_BLURS, if (blurEnabledByDefault) 0 else 1) == 0)
        }

        var blurRadiusPct by remember {
            mutableStateOf(
                Settings.Secure.getFloat(
                    cr,
                    KEY_SYSTEM_BLUR_RADIUS_PCT,
                    MAX_BLUR_RADIUS_PCT,
                ).takeIf { it.isFinite() }
                    ?.coerceIn(MIN_BLUR_RADIUS_PCT, MAX_BLUR_RADIUS_PCT)
                    ?: MAX_BLUR_RADIUS_PCT
            )
        }

        Scaffold(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp)
            ) {
                item {
                    BlurIllustration(blursEnabled, blurRadiusPct)
                }

                item {
                    SwitchPreference(
                        title = stringResource(com.android.settingslib.R.string.window_blurs),
                        checked = blursEnabled,
                        onCheckedChange = { newValue ->
                            blursEnabled = newValue
                            Settings.Global.putInt(cr, Settings.Global.DISABLE_WINDOW_BLURS, if (blursEnabled) 0 else 1)
                        },
                        position = PreferencePosition.Single
                    )
                }

                item {
                    SliderPreference(
                        title = "Blur Strength",
                        summary = "",
                        value = blurRadiusPct,
                        onValueChange = { 
                            blurRadiusPct = it
                        },
                        onValueChangeFinished = {
                            Settings.Secure.putFloat(
                                cr,
                                KEY_SYSTEM_BLUR_RADIUS_PCT,
                                blurRadiusPct,
                            )
                        },
                        valueRange = MIN_BLUR_RADIUS_PCT..MAX_BLUR_RADIUS_PCT,
                        steps = BLUR_RADIUS_PCT_STEPS,
                        displayValue = "${blurRadiusPct.roundToInt()}%",
                        enabled = blursEnabled,
                        position = PreferencePosition.Single
                    )
                }
            }
        }
    }

    @Composable
    fun BlurIllustration(enabled: Boolean, radiusPct: Float) {
        val context = LocalContext.current

        val layerFg = Color(context.getColor(com.android.internal.R.color.shade_panel_fg))
        val layerBg = Color(context.getColor(com.android.internal.R.color.shade_panel_bg))
        val compositeColor = ColorUtils.compositeColors(layerFg.toArgb(), layerBg.toArgb())
        val shadeColor = Color(compositeColor)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(340.dp)
                .padding(vertical = 12.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f), RoundedCornerShape(32.dp)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(160.dp)
                    .height(280.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .border(4.dp, Color.Black.copy(alpha = 0.9f), RoundedCornerShape(32.dp))
                    .background(Color.Black)
                    .padding(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(26.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(26.dp))
                    ) {
                        WallpaperImage(
                            modifier = Modifier
                                .fillMaxSize()
                                .blur(
                                    (MAX_PREVIEW_BLUR_RADIUS_DP * radiusPct /
                                        MAX_BLUR_RADIUS_PCT).dp
                                )
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(if (enabled) shadeColor else MaterialTheme.colorScheme.surfaceContainer)
                        )

                        QSContent(enabled)
                    }
                    
                    StatusBar()
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 8.dp)
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(Color.Black)
                )
            }
        }
    }

    @Composable
    fun WallpaperImage(modifier: Modifier = Modifier) {
        val context = LocalContext.current
        val wallpaperManager = remember { WallpaperManager.getInstance(context) }
        val drawable = remember { 
            try {
                wallpaperManager.drawable
            } catch (e: Exception) {
                null
            }
        }

        if (drawable != null) {
            val painter = rememberDrawablePainter(drawable)
            Image(
                painter = painter,
                contentDescription = null,
                modifier = modifier,
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = modifier.background(
                    Brush.verticalGradient(
                        listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.secondaryContainer)
                    )
                )
            )
        }
    }

    @Composable
    fun rememberDrawablePainter(drawable: Drawable?): Painter {
        return remember(drawable) {
            object : Painter() {
                override val intrinsicSize: Size
                    get() = drawable?.let { Size(it.intrinsicWidth.toFloat(), it.intrinsicHeight.toFloat()) } ?: Size.Unspecified

                override fun DrawScope.onDraw() {
                    drawable?.let {
                        it.setBounds(0, 0, size.width.toInt(), size.height.toInt())
                        it.draw(drawContext.canvas.nativeCanvas)
                    }
                }
            }
        }
    }

    @Composable
    fun StatusBar() {
        val formatter = remember { DateTimeFormatter.ofPattern("HH:mm") }
        var time by remember { mutableStateOf(LocalTime.now().format(formatter)) }

        LaunchedEffect(Unit) {
            while (true) {
                time = LocalTime.now().format(formatter)
                delay(1000 * 30)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                time,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(
                    modifier = Modifier
                        .size(10.dp, 6.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))
                )
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))
                )
            }
        }
    }

    @Composable
    fun QSContent(enabled: Boolean) {
        val icons = listOf(
            Icons.Filled.Wifi,
            Icons.Filled.Bluetooth,
            Icons.Filled.FlashlightOn,
            Icons.Filled.Settings,
            Icons.Filled.AirplanemodeActive,
            Icons.Filled.Nfc,
            Icons.Filled.Adb,
            Icons.Filled.NotificationsActive
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 32.dp, start = 12.dp, end = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            repeat(4) { rowIndex ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    repeat(2) { colIndex ->
                        val iconIndex = (rowIndex * 2 + colIndex) % icons.size
                        Box(
                            modifier = Modifier
                                .height(28.dp)
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    MaterialTheme.colorScheme.surfaceBright
                                )
                                .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f), RoundedCornerShape(16.dp)),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(Color.Transparent),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = icons[iconIndex],
                                        contentDescription = null,
                                        modifier = Modifier.size(10.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .height(2.5.dp)
                                        .weight(1f)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                                )
                            }
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceBright),
                contentAlignment = Alignment.CenterStart
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.primary)
                )
                Icon(
                    imageVector = Icons.Filled.Brightness4,
                    contentDescription = null,
                    modifier = Modifier.padding(start = 8.dp).size(12.dp),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Box(modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceBright),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Settings,
                        null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }

    companion object {
        private const val KEY_SYSTEM_BLUR_RADIUS_PCT = "system_blur_radius_pct"
        private const val MIN_BLUR_RADIUS_PCT = 0f
        private const val MAX_BLUR_RADIUS_PCT = 100f
        private const val BLUR_RADIUS_PCT_STEPS = 99
        private const val MAX_PREVIEW_BLUR_RADIUS_DP = 34f
    }

}
