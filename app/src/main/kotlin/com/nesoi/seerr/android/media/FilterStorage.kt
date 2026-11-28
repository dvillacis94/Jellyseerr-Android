package com.nesoi.seerr.android.media

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.nesoi.seerr.android.auth.dataStore
import kotlinx.coroutines.flow.first

/** Where a tab's saved and last-used filters are kept, as one string per tab. */
interface FilterStorage {
    suspend fun read(key: String): String?
    suspend fun write(key: String, value: String)
}

class DataStoreFilterStorage(private val context: Context) : FilterStorage {
    override suspend fun read(key: String): String? = context.dataStore.data.first()[stringPreferencesKey(key)]

    override suspend fun write(key: String, value: String) {
        context.dataStore.edit { it[stringPreferencesKey(key)] = value }
    }
}

class MemoryFilterStorage : FilterStorage {
    val values = mutableMapOf<String, String>()
    override suspend fun read(key: String) = values[key]
    override suspend fun write(key: String, value: String) { values[key] = value }
}
