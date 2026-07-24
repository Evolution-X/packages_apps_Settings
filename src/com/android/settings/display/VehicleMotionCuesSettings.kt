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

import android.content.Context
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import com.android.axion.compose.preferences.PreferencePosition
import com.android.axion.compose.preferences.SliderPreference
import com.android.axion.compose.preferences.SwitchPreference
import com.android.axion.compose.theme.AxionTheme
import com.android.settings.R
import kotlin.math.sin
import kotlin.math.roundToInt

class VehicleMotionCuesSettings : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                AxionTheme {
                    VehicleMotionCuesScreen()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        activity?.title = getString(R.string.vehicle_motion_cues_title)
    }

    @Composable
    fun VehicleMotionCuesScreen() {
        val context = LocalContext.current
        val cr = context.contentResolver

        var cuesEnabled by remember {
            mutableStateOf(Settings.Secure.getInt(cr, "vehicle_motion_cues", 0) == 1)
        }

        var dotSize by remember {
            mutableStateOf(
                Settings.Secure.getFloat(cr, "vehicle_motion_cues_dot_size", 10f)
            )
        }

        var sensitivity by remember {
            mutableStateOf(
                Settings.Secure.getFloat(cr, "vehicle_motion_cues_sensitivity", 15f)
            )
        }

        var dotCount by remember {
            mutableStateOf(
                Settings.Secure.getInt(cr, "vehicle_motion_cues_dot_count", 14)
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
                    MotionCuesIllustration(cuesEnabled, dotSize, dotCount)
                }

                item {
                    SwitchPreference(
                        title = stringResource(R.string.vehicle_motion_cues_title),
                        summary = stringResource(R.string.vehicle_motion_cues_summary),
                        checked = cuesEnabled,
                        onCheckedChange = { newValue ->
                            cuesEnabled = newValue
                            Settings.Secure.putInt(cr, "vehicle_motion_cues", if (cuesEnabled) 1 else 0)
                        },
                        position = PreferencePosition.Single
                    )
                }

                item {
                    SliderPreference(
                        title = stringResource(R.string.vehicle_motion_cues_dot_size_title),
                        summary = stringResource(R.string.vehicle_motion_cues_dot_size_summary),
                        value = dotSize,
                        onValueChange = { 
                            dotSize = it
                        },
                        onValueChangeFinished = {
                            Settings.Secure.putFloat(cr, "vehicle_motion_cues_dot_size", dotSize)
                        },
                        valueRange = 6f..24f,
                        steps = 17,
                        displayValue = "${dotSize.roundToInt()}dp",
                        enabled = cuesEnabled,
                        position = PreferencePosition.Single
                    )
                }

                item {
                    SliderPreference(
                        title = stringResource(R.string.vehicle_motion_cues_sensitivity_title),
                        summary = stringResource(R.string.vehicle_motion_cues_sensitivity_summary),
                        value = sensitivity,
                        onValueChange = { 
                            sensitivity = it
                        },
                        onValueChangeFinished = {
                            Settings.Secure.putFloat(cr, "vehicle_motion_cues_sensitivity", sensitivity)
                        },
                        valueRange = 5f..35f,
                        steps = 29,
                        displayValue = "${sensitivity.roundToInt()}%",
                        enabled = cuesEnabled,
                        position = PreferencePosition.Single
                    )
                }

                item {
                    SliderPreference(
                        title = stringResource(R.string.vehicle_motion_cues_dot_count_title),
                        summary = stringResource(R.string.vehicle_motion_cues_dot_count_summary),
                        value = dotCount.toFloat(),
                        onValueChange = { 
                            dotCount = (it.roundToInt() / 2 * 2).coerceIn(6, 24)
                        },
                        onValueChangeFinished = {
                            Settings.Secure.putInt(cr, "vehicle_motion_cues_dot_count", dotCount)
                        },
                        valueRange = 6f..24f,
                        steps = 8,
                        displayValue = "$dotCount dots",
                        enabled = cuesEnabled,
                        position = PreferencePosition.Single
                    )
                }
            }
        }
    }

    @Composable
    fun MotionCuesIllustration(enabled: Boolean, dotSize: Float, dotCount: Int) {
        val infiniteTransition = rememberInfiniteTransition(label = "cues_anim")
        
        val tAngle by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 2f * Math.PI.toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(4000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "angle_anim"
        )

        val dxOffset = if (enabled) sin(tAngle) * 15f else 0f
        val dyOffset = if (enabled) sin(tAngle * 2f) * 12f else 0f

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
                            .fillMaxSize()
                            .clip(RoundedCornerShape(26.dp))
                    ) {
                        WallpaperImage(modifier = Modifier.fillMaxSize())

                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val w = size.width
                            val h = size.height

                            val radius = (dotSize * 0.6f).dp.toPx()
                            val paintColor = Color.White.copy(alpha = 0.6f)

                            val leftMarginPx = 16f.dp.toPx()
                            val topMarginPx = 48f.dp.toPx()
                            val bottomMarginPx = 48f.dp.toPx()
                            val usableHeight = h - topMarginPx - bottomMarginPx

                            val dotsPerSide = dotCount / 2
                            if (dotsPerSide > 0) {
                                val divisor = (dotsPerSide - 1).coerceAtLeast(1).toFloat()
                                for (i in 0 until dotsPerSide) {
                                    val py = topMarginPx + usableHeight * (i / divisor)
                                    
                                    val driftXLeft = if (enabled) sin(tAngle * 1.5f + i) * 6f.dp.toPx() else 0f
                                    val driftYLeft = if (enabled) sin(tAngle * 1.0f + i * 1.5f) * 6f.dp.toPx() else 0f
                                    drawCircle(
                                        color = paintColor,
                                        radius = radius,
                                        center = Offset(leftMarginPx + dxOffset + driftXLeft, py + dyOffset + driftYLeft)
                                    )

                                    val driftXRight = if (enabled) sin(tAngle * 1.5f + (i + dotsPerSide)) * 6f.dp.toPx() else 0f
                                    val driftYRight = if (enabled) sin(tAngle * 1.0f + (i + dotsPerSide) * 1.5f) * 6f.dp.toPx() else 0f
                                    drawCircle(
                                        color = paintColor,
                                        radius = radius,
                                        center = Offset((w - leftMarginPx) + dxOffset + driftXRight, py + dyOffset + driftYRight)
                                    )
                                }
                            }
                        }
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
    }

    @Composable
    fun WallpaperImage(modifier: Modifier = Modifier) {
        Box(
            modifier = modifier.background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF1E272C),
                        Color(0xFF2C3E50)
                    )
                )
            )
        )
    }
}