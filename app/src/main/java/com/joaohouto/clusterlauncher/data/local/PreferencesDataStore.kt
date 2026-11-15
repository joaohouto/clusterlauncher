package com.joaohouto.clusterlauncher.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.playerDataStore: DataStore<Preferences> by preferencesDataStore(name = "cluster_player_prefs")

data class PlaybackStateSnapshot(
    val lastTrackUri: String?,
    val lastFolderPath: String?,
    val lastPositionMs: Long,
    val isShuffleEnabled: Boolean,
    val repeatMode: Int,
    val autoplayOnStart: Boolean = true
)

class PreferencesDataStore(private val context: Context) {

    companion object {
        val KEY_LAST_TRACK_URI = stringPreferencesKey("last_track_uri")
        val KEY_LAST_FOLDER_PATH = stringPreferencesKey("last_folder_path")
        val KEY_LAST_POSITION_MS = longPreferencesKey("last_position_ms")
        val KEY_SHUFFLE_MODE = booleanPreferencesKey("shuffle_mode")
        val KEY_REPEAT_MODE = intPreferencesKey("repeat_mode")
        val KEY_ACCENT_THEME = stringPreferencesKey("accent_theme")
        val KEY_APP_VOLUME_PERCENT = intPreferencesKey("app_volume_percent")
        val KEY_LOUDNESS_BOOST = booleanPreferencesKey("loudness_boost")
        val KEY_KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val KEY_SHOW_LYRICS = booleanPreferencesKey("show_lyrics")
        val KEY_LYRICS_OFFSET_MS = intPreferencesKey("lyrics_offset_ms")
        val KEY_AUDIO_SYNC_OFFSET_MS = intPreferencesKey("audio_sync_offset_ms")
        val KEY_AUTOPLAY_ON_START = booleanPreferencesKey("autoplay_on_start")
        val KEY_FULL_SCREEN_MODE = booleanPreferencesKey("full_screen_mode")
        val KEY_AUTO_CHECK_UPDATES = booleanPreferencesKey("auto_check_updates")

        @Volatile
        private var INSTANCE: PreferencesDataStore? = null

        fun getInstance(context: Context): PreferencesDataStore {
            return INSTANCE ?: synchronized(this) {
                val instance = PreferencesDataStore(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }

    val playbackStateFlow: Flow<PlaybackStateSnapshot> = context.playerDataStore.data.map { prefs ->
        PlaybackStateSnapshot(
            lastTrackUri = prefs[KEY_LAST_TRACK_URI],
            lastFolderPath = prefs[KEY_LAST_FOLDER_PATH],
            lastPositionMs = prefs[KEY_LAST_POSITION_MS] ?: 0L,
            isShuffleEnabled = prefs[KEY_SHUFFLE_MODE] ?: false,
            repeatMode = prefs[KEY_REPEAT_MODE] ?: 0,
            autoplayOnStart = prefs[KEY_AUTOPLAY_ON_START] ?: true
        )
    }

    suspend fun getPlaybackSnapshot(): PlaybackStateSnapshot {
        val prefs = context.playerDataStore.data.first()
        return PlaybackStateSnapshot(
            lastTrackUri = prefs[KEY_LAST_TRACK_URI],
            lastFolderPath = prefs[KEY_LAST_FOLDER_PATH],
            lastPositionMs = prefs[KEY_LAST_POSITION_MS] ?: 0L,
            isShuffleEnabled = prefs[KEY_SHUFFLE_MODE] ?: false,
            repeatMode = prefs[KEY_REPEAT_MODE] ?: 0,
            autoplayOnStart = prefs[KEY_AUTOPLAY_ON_START] ?: true
        )
    }

    suspend fun saveLastPlayback(
        trackUri: String,
        folderPath: String,
        positionMs: Long
    ) {
        context.playerDataStore.edit { prefs ->
            prefs[KEY_LAST_TRACK_URI] = trackUri
            prefs[KEY_LAST_FOLDER_PATH] = folderPath
            prefs[KEY_LAST_POSITION_MS] = positionMs
        }
    }

    suspend fun savePosition(positionMs: Long) {
        context.playerDataStore.edit { prefs ->
            prefs[KEY_LAST_POSITION_MS] = positionMs
        }
    }

    suspend fun saveShuffleMode(enabled: Boolean) {
        context.playerDataStore.edit { prefs ->
            prefs[KEY_SHUFFLE_MODE] = enabled
        }
    }

    val appVolumePercentFlow: Flow<Int> = context.playerDataStore.data.map { prefs ->
        prefs[KEY_APP_VOLUME_PERCENT] ?: 100
    }

    val loudnessBoostFlow: Flow<Boolean> = context.playerDataStore.data.map { prefs ->
        prefs[KEY_LOUDNESS_BOOST] ?: true
    }

    val keepScreenOnFlow: Flow<Boolean> = context.playerDataStore.data.map { prefs ->
        prefs[KEY_KEEP_SCREEN_ON] ?: true
    }

    val showLyricsFlow: Flow<Boolean> = context.playerDataStore.data.map { prefs ->
        prefs[KEY_SHOW_LYRICS] ?: true
    }

    val lyricsOffsetMsFlow: Flow<Int> = context.playerDataStore.data.map { prefs ->
        prefs[KEY_LYRICS_OFFSET_MS] ?: 0
    }

    suspend fun saveLyricsOffsetMs(offsetMs: Int) {
        context.playerDataStore.edit { prefs ->
            prefs[KEY_LYRICS_OFFSET_MS] = offsetMs.coerceIn(-5000, 5000)
        }
    }

    val audioSyncOffsetMsFlow: Flow<Int> = context.playerDataStore.data.map { prefs ->
        prefs[KEY_AUDIO_SYNC_OFFSET_MS] ?: 0
    }

    suspend fun saveAudioSyncOffsetMs(offsetMs: Int) {
        context.playerDataStore.edit { prefs ->
            prefs[KEY_AUDIO_SYNC_OFFSET_MS] = offsetMs.coerceIn(-500, 500)
        }
    }

    val autoplayOnStartFlow: Flow<Boolean> = context.playerDataStore.data.map { prefs ->
        prefs[KEY_AUTOPLAY_ON_START] ?: true
    }

    suspend fun saveRepeatMode(repeatMode: Int) {
        context.playerDataStore.edit { prefs ->
            prefs[KEY_REPEAT_MODE] = repeatMode
        }
    }

    suspend fun saveAppVolumePercent(percent: Int) {
        context.playerDataStore.edit { prefs ->
            prefs[KEY_APP_VOLUME_PERCENT] = percent.coerceIn(10, 100)
        }
    }

    suspend fun saveLoudnessBoost(enabled: Boolean) {
        context.playerDataStore.edit { prefs ->
            prefs[KEY_LOUDNESS_BOOST] = enabled
        }
    }

    suspend fun saveKeepScreenOn(enabled: Boolean) {
        context.playerDataStore.edit { prefs ->
            prefs[KEY_KEEP_SCREEN_ON] = enabled
        }
    }

    suspend fun saveShowLyrics(enabled: Boolean) {
        context.playerDataStore.edit { prefs ->
            prefs[KEY_SHOW_LYRICS] = enabled
        }
    }

    suspend fun saveAutoplayOnStart(enabled: Boolean) {
        context.playerDataStore.edit { prefs ->
            prefs[KEY_AUTOPLAY_ON_START] = enabled
        }
    }

    val autoCheckUpdatesFlow: Flow<Boolean> = context.playerDataStore.data.map { prefs ->
        prefs[KEY_AUTO_CHECK_UPDATES] ?: true
    }

    suspend fun saveAutoCheckUpdates(enabled: Boolean) {
        context.playerDataStore.edit { prefs ->
            prefs[KEY_AUTO_CHECK_UPDATES] = enabled
        }
    }
}
