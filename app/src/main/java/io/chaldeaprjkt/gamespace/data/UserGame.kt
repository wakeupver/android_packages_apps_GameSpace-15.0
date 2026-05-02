/*
 * Copyright (C) 2021 Chaldeaprjkt
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

import android.app.GameManager


data class UserGame(
    val packageName: String,
    val mode: Int = GameManager.GAME_MODE_STANDARD,
    val fpsCap: Int = FPS_CAP_DEFAULT,
) {
    /**
     * Serialization format: "packageName=mode:fpsCap"
     * Backward-compatible with old "packageName=mode" entries (fpsCap defaults to 0).
     */
    override fun toString(): String = "$packageName=$mode:$fpsCap"

    companion object {
        /** 0 means "no cap / system default". */
        const val FPS_CAP_DEFAULT = 0

        fun fromSettings(data: String): UserGame {
            val eqIdx = data.indexOf('=')
            if (eqIdx < 0) return UserGame(data)
            val pkg = data.substring(0, eqIdx)
            val rest = data.substring(eqIdx + 1)
            val colonIdx = rest.indexOf(':')
            return if (colonIdx < 0) {
                // Old format: "packageName=mode"
                UserGame(pkg, rest.toIntOrNull() ?: GameManager.GAME_MODE_STANDARD)
            } else {
                val mode = rest.substring(0, colonIdx).toIntOrNull() ?: GameManager.GAME_MODE_STANDARD
                val fps  = rest.substring(colonIdx + 1).toIntOrNull() ?: FPS_CAP_DEFAULT
                UserGame(pkg, mode, fps)
            }
        }
    }
}
