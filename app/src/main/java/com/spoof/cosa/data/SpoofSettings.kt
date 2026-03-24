package com.spoof.cosa.data

import android.content.SharedPreferences
import com.spoof.cosa.common.SpoofConfig

class SpoofSettings(private val preferences: SharedPreferences) {

    fun getFakePrjname(): String {
        return preferences.getString(SpoofConfig.prefsKeyFakePrjname, null)
            .normalizeFakePrjname()
            .ifEmpty { SpoofConfig.defaultFakePrjname }
    }

    fun setFakePrjname(value: String?): Boolean {
        val normalized = value.normalizeFakePrjname()
        return preferences.edit().apply {
            if (normalized.isEmpty()) {
                remove(SpoofConfig.prefsKeyFakePrjname)
            } else {
                putString(SpoofConfig.prefsKeyFakePrjname, normalized)
            }
        }.commit()
    }

    companion object {
        fun String?.normalizeFakePrjname(): String = this?.trim().orEmpty()
    }
}
