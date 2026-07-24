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

import androidx.fragment.app.FragmentActivity
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.spy
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.Robolectric
import com.android.settings.R

@RunWith(RobolectricTestRunner::class)
class VehicleMotionCuesSettingsTest {

    private lateinit var fragment: VehicleMotionCuesSettings

    @Before
    fun setUp() {
        fragment = spy(VehicleMotionCuesSettings())
    }

    @Test
    fun onResume_setsActivityTitle() {
        val activity = spy(Robolectric.buildActivity(FragmentActivity::class.java).setup().get())
        `when`(fragment.activity).thenReturn(activity)

        fragment.onResume()

        verify(activity).title = fragment.getString(R.string.vehicle_motion_cues_title)
    }
}
