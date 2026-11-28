package com.nesoi.seerr.android.media

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.nesoi.seerr.android.auth.dataStore
import kotlinx.coroutines.flow.first

/** Small settings kept on the device, as one string per key: filters, appearance. */
interface PreferenceStorage {
    suspend fun read(key: String): String?
    suspend fun write(key: String, value: String)
}

class DataStorePreferences(private val context: Context) : PreferenceStorage {
    override suspend fun read(key: String): String? = context.dataStore.data.first()[stringPreferencesKey(key)]

    override suspend fun write(key: String, value: String) {
        context.dataStore.edit { it[stringPreferencesKey(key)] = value }
    }
}

class MemoryPreferences : PreferenceStorage {
    val values = mutableMapOf<String, String>()
    override suspend fun read(key: String) = values[key]
    override suspend fun write(key: String, value: String) { values[key] = value }
}
