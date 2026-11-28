package com.nesoi.seerr.android.auth

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Where the configured server's address is kept. */
interface ServerPreferences {
    suspend fun serverUrl(): HttpUrl?
    suspend fun setServerUrl(url: HttpUrl?)
}

internal val Context.dataStore by preferencesDataStore(name = "settings")

class DataStoreServerPreferences(private val context: Context) : ServerPreferences {
    override suspend fun serverUrl(): HttpUrl? =
        context.dataStore.data.first()[SERVER_URL]?.toHttpUrlOrNull()

    override suspend fun setServerUrl(url: HttpUrl?) {
        context.dataStore.edit { preferences ->
            if (url == null) preferences.remove(SERVER_URL) else preferences[SERVER_URL] = url.toString()
        }
    }

    private companion object {
        val SERVER_URL = stringPreferencesKey("server_url")
    }
}
