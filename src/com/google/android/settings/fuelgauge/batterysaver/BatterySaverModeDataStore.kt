package com.google.android.settings.fuelgauge.batterysaver

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Bundle
import android.util.Log
import com.android.settingslib.datastore.AbstractKeyedDataObservable
import com.android.settingslib.datastore.HandlerExecutor
import com.android.settingslib.datastore.KeyValueStore

class BatterySaverModeDataStore(private val context: Context) :
    AbstractKeyedDataObservable<String>(), KeyValueStore {

    private var isFlipendoAggressiveMode: Boolean = false
    private var isFlipendoEnabled: Boolean = false
    private val isFlipendoInstalled: Boolean = FlipendoUtils.isFlipendoInstalled(context)

    private var contentObserver: ContentObserver? = null

    init {
        refreshFlipendoStates(false)
    }

    override fun contains(key: String): Boolean {
        return key == "basic_battery_saver" || key == "extreme_battery_saver"
    }

    override fun <T : Any> getValue(key: String, valueType: Class<T>): T? {
        val result: Any? =
            when {
                key == "basic_battery_saver" -> {
                    !(isFlipendoAggressiveMode || isFlipendoEnabled)
                }
                key == "extreme_battery_saver" -> {
                    isFlipendoAggressiveMode || isFlipendoEnabled
                }
                else -> null
            }
        if (result == null) return null
        return if (valueType.isInstance(result)) {
            valueType.cast(result)
        } else null
    }

    override fun <T : Any> setValue(key: String, valueType: Class<T>, value: T?) {
        if (value is Boolean) {
            val updateFlipendoMode: Int =
                when {
                    key == "basic_battery_saver" && !value -> 1
                    key == "extreme_battery_saver" && value -> 1
                    else -> 0
                }

            Log.i("BatterySaverModeDataStore", "setValue ($key, $value) with $updateFlipendoMode")

            val bundle = Bundle().apply { putInt("update_flipendo_mode", updateFlipendoMode) }

            try {
                context.contentResolver.call(
                    FlipendoUtils.FLIPENDO_STATE_AUTHORITY,
                    "update_flipendo_mode_method",
                    null,
                    bundle,
                )
            } catch (e: Exception) {
                Log.e("BatterySaverModeDataStore", "setValue failed", e)
            }
        }
    }

    override fun onFirstObserverAdded() {
        val mainExecutor = HandlerExecutor.Companion.main
        this.contentObserver =
            object : ContentObserver(mainExecutor) {
                override fun onChange(selfChange: Boolean) {
                    onChange(selfChange, null)
                }

                override fun onChange(selfChange: Boolean, uri: Uri?) {
                    Log.i("BatterySaverModeDataStore", "Flipendo state changed")
                    refreshFlipendoStates(true)
                }
            }
        val contentResolver = this.context.contentResolver
        val uri = FlipendoUtils.FLIPENDO_ENABLED_OBSERVABLE_URI
        contentResolver.registerContentObserver(uri, false, contentObserver!!)
    }

    override fun onLastObserverRemoved() {
        val contentResolver = this.context.contentResolver
        contentResolver.unregisterContentObserver(contentObserver!!)
    }

    fun refreshFlipendoStates(notifyChange: Boolean) {
        if (!isFlipendoInstalled) return

        val flipendoState = FlipendoUtils.getFlipendoState(context)

        isFlipendoAggressiveMode = flipendoState.first as Boolean
        isFlipendoEnabled = flipendoState.second as Boolean

        Log.i(
            "BatterySaverModeDataStore",
            "Flipendo aggressive=$isFlipendoAggressiveMode, enabled=$isFlipendoEnabled",
        )

        if (notifyChange && !isFlipendoAggressiveMode) {
            notifyChange(1)
        }
    }
}
