package com.sleaudioclient.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.sleaudioclient.model.ClientConfig
import com.sleaudioclient.model.PcmFormat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "client_config")

class ConfigStore(private val context: Context) {
    private object Keys {
        val serverIp = stringPreferencesKey("server_ip")
        val serverPort = intPreferencesKey("server_port")
        val bindIp = stringPreferencesKey("bind_ip")
        val bindPort = intPreferencesKey("bind_port")
        val sampleRate = intPreferencesKey("sample_rate")
        val channels = intPreferencesKey("channels")
        val pcmFormat = stringPreferencesKey("pcm_format")
        val prebufferMs = intPreferencesKey("prebuffer_ms")
        val heartbeatMs = intPreferencesKey("heartbeat_ms")
        val volume = floatPreferencesKey("volume")
        val playbackEnabled = booleanPreferencesKey("playback_enabled")
    }

    val configFlow: Flow<ClientConfig> = context.dataStore.data.map { prefs ->
        prefs.toConfig()
    }

    suspend fun save(config: ClientConfig) {
        context.dataStore.edit { prefs ->
            prefs[Keys.serverIp] = config.serverIp
            prefs[Keys.serverPort] = config.serverPort
            prefs[Keys.bindIp] = config.bindIp
            prefs[Keys.bindPort] = config.bindPort
            prefs[Keys.sampleRate] = config.sampleRate
            prefs[Keys.channels] = config.channels
            prefs[Keys.pcmFormat] = config.pcmFormat.name
            prefs[Keys.prebufferMs] = config.prebufferMs
            prefs[Keys.heartbeatMs] = config.heartbeatMs
            prefs[Keys.volume] = config.volume
            prefs[Keys.playbackEnabled] = config.playbackEnabled
        }
    }

    private fun Preferences.toConfig(): ClientConfig {
        val format = this[Keys.pcmFormat]?.let {
            runCatching { PcmFormat.valueOf(it) }.getOrDefault(PcmFormat.INT32)
        } ?: PcmFormat.INT32
        return ClientConfig(
            serverIp = this[Keys.serverIp] ?: "192.168.130.1",
            serverPort = this[Keys.serverPort] ?: 9999,
            bindIp = this[Keys.bindIp] ?: "0.0.0.0",
            bindPort = this[Keys.bindPort] ?: 0,
            sampleRate = this[Keys.sampleRate] ?: 48000,
            channels = this[Keys.channels] ?: 2,
            pcmFormat = format,
            prebufferMs = this[Keys.prebufferMs] ?: 80,
            heartbeatMs = this[Keys.heartbeatMs] ?: 1000,
            volume = this[Keys.volume] ?: 1.0f,
            playbackEnabled = this[Keys.playbackEnabled] ?: true
        )
    }
}
