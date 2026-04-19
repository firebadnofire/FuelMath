package org.archuser.fuelmath

import android.content.Context

class FuelRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun loadData(): FuelMathData {
        val json = preferences.getString(KEY_DATA, null) ?: return FuelMathData()
        return FuelJsonCodec.decode(json)
    }

    fun saveData(data: FuelMathData): Boolean =
        preferences.edit()
            .putString(KEY_DATA, FuelJsonCodec.encode(data))
            .commit()

    fun encodeBackup(data: FuelMathData): String = FuelJsonCodec.encode(data)

    fun decodeBackup(json: String): FuelMathData = FuelJsonCodec.decode(json)

    companion object {
        private const val PREFERENCES_NAME = "fuel_math_repository"
        private const val KEY_DATA = "fuel_math_data"
    }
}
