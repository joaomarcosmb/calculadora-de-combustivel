package org.pdm.calculadoradecombustivel.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

val Context.fuelDataStore by preferencesDataStore(name = "fuel_datastore")

object FuelPreferencesKeys {
    val use75Percent = booleanPreferencesKey("use_75_percent")
    val stations = stringPreferencesKey("stations_json")
}
