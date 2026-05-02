/*
 * Copyright (C) 2021 Chaldeaprjkt
 *               2022 crDroid Android Project
 * Copyright (C) 2023 risingOS Android Project
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
package io.chaldeaprjkt.gamespace.data

import android.content.Context
import android.media.AudioManager
import android.os.UserHandle
import android.provider.Settings
import android.util.Log
import com.google.gson.Gson
import javax.inject.Inject

class GameSession @Inject constructor(
    private val context: Context,
    private val appSettings: AppSettings,
    private val systemSettings: SystemSettings,
    private val gson: Gson,
) {

    private val db by lazy { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    private val audioManager by lazy { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    private val resolver by lazy { context.contentResolver }

    private var state
        get() = db.getString(KEY_SAVED_SESSION, "")
            .takeIf { !it.isNullOrEmpty() }
            ?.let {
                try {
                    gson.fromJson(it, SessionState::class.java)
                } catch (e: RuntimeException) {
                    null
                }
            }
        set(value) = db.edit()
            .putString(KEY_SAVED_SESSION, value?.let {
                try {
                    gson.toJson(value)
                } catch (e: RuntimeException) {
                    ""
                }
            } ?: "")
            .apply()

    fun register(sessionName: String) {
        if (state?.packageName != sessionName) unregister()

        val fpsCap = systemSettings.userGames
            .firstOrNull { it.packageName == sessionName }?.fpsCap
            ?: UserGame.FPS_CAP_DEFAULT

        val originalPeak = if (fpsCap > UserGame.FPS_CAP_DEFAULT) {
            Settings.System.getFloatForUser(
                resolver,
                Settings.System.PEAK_REFRESH_RATE,
                REFRESH_RATE_UNKNOWN,
                UserHandle.USER_CURRENT,
            )
        } else {
            -1f
        }

        state = SessionState(
            packageName = sessionName,
            autoBrightness = systemSettings.autoBrightness,
            headsup = systemSettings.headsup,
            threeScreenshot = systemSettings.threeScreenshot,
            ringerMode = audioManager.ringerModeInternal,
            adbEnabled = systemSettings.adbEnabled,
            originalPeakRefreshRate = originalPeak,
        )

        if (appSettings.noAutoBrightness) {
            systemSettings.autoBrightness = false
        }
        if (appSettings.danmakuNotification) {
            systemSettings.headsup = false
        }
        if (appSettings.noThreeScreenshot) {
            systemSettings.threeScreenshot = 0
        }
        if (appSettings.noAdbEnabled) {
            systemSettings.adbEnabled = false
        }
        if (appSettings.ringerMode != 3) {
            audioManager.ringerModeInternal = appSettings.ringerMode
        }

        // Apply per-app FPS cap
        if (fpsCap > UserGame.FPS_CAP_DEFAULT) {
            Log.d(TAG, "Applying FPS cap $fpsCap for $sessionName")
            Settings.System.putFloatForUser(
                resolver,
                Settings.System.PEAK_REFRESH_RATE,
                fpsCap.toFloat(),
                UserHandle.USER_CURRENT,
            )
            // Also clamp MIN_REFRESH_RATE so it doesn't exceed our cap
            val minRate = Settings.System.getFloatForUser(
                resolver,
                Settings.System.MIN_REFRESH_RATE,
                0f,
                UserHandle.USER_CURRENT,
            )
            if (minRate > fpsCap) {
                Settings.System.putFloatForUser(
                    resolver,
                    Settings.System.MIN_REFRESH_RATE,
                    fpsCap.toFloat(),
                    UserHandle.USER_CURRENT,
                )
            }
        }
    }

    fun unregister() {
        val orig = state?.copy() ?: return
        if (appSettings.noAutoBrightness) {
            orig.autoBrightness?.let { systemSettings.autoBrightness = it }
        }
        if (appSettings.danmakuNotification) {
            orig.headsup?.let { systemSettings.headsup = it }
        }
        if (appSettings.noThreeScreenshot) {
            systemSettings.threeScreenshot = orig.threeScreenshot
        }
        if (appSettings.noAdbEnabled) {
            orig.adbEnabled?.let { systemSettings.adbEnabled = it }
        }
        if (appSettings.ringerMode != 3) {
            audioManager.ringerModeInternal = orig.ringerMode
        }

        // Restore peak refresh rate if it was changed by an FPS cap
        if (orig.originalPeakRefreshRate >= 0f) {
            Log.d(TAG, "Restoring peak refresh rate to ${orig.originalPeakRefreshRate}")
            if (orig.originalPeakRefreshRate == REFRESH_RATE_UNKNOWN) {
                // No explicit value was set before — remove override (defaults to system max)
                Settings.System.putStringForUser(
                    resolver,
                    Settings.System.PEAK_REFRESH_RATE,
                    null,
                    UserHandle.USER_CURRENT,
                )
            } else {
                Settings.System.putFloatForUser(
                    resolver,
                    Settings.System.PEAK_REFRESH_RATE,
                    orig.originalPeakRefreshRate,
                    UserHandle.USER_CURRENT,
                )
            }
        }

        state = null
    }

    fun finalize() {
        unregister()
    }

    companion object {
        const val TAG = "GameSession"
        const val PREFS_NAME = "persisted_session"
        const val KEY_SAVED_SESSION = "session"
        private const val REFRESH_RATE_UNKNOWN = -1f
    }
}
