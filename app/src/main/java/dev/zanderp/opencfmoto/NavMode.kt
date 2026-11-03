// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

import android.content.Context

/**
 * Which vehicle profile Google Maps starts turn-by-turn in when we hand it a destination:
 * motorcycle (two-wheeler), car, or AUTO. The dash shows whatever Maps on the phone starts, so
 * this is the only lever over "car mode" navigation.
 *
 * - [AUTO] (default): don't force a profile — the maps/dir and google.navigation links carry no
 *   travel mode, so Maps uses the phone's own mode (the last one it was in). Navigation started
 *   from the phone itself, whether car or motorcycle, is already projected to the dash as-is.
 * - [MOTO]/[CAR]: the links pin two_wheeler/driving for launches from the ride screen.
 *
 * The Android Auto feed the dash receives only carries turn-by-turn data, not a car/motorcycle
 * flag, so AUTO means "let the phone decide" rather than detecting the mode over the wire.
 * Toggled from the 🤖/🏍/🚗 chip next to the 🧭 app chip.
 */
object NavMode {
    const val AUTO = "auto"
    const val MOTO = "moto"
    const val CAR = "car"
    private const val PREFS = "nav_mode"
    private const val KEY = "mode"

    /** Remembered mode; automatic until changed. */
    fun current(ctx: Context): String =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: AUTO

    /** Cycle Auto → Motorcycle → Car → Auto (like the night-theme chip). */
    fun toggle(ctx: Context) {
        val next = when (current(ctx)) {
            AUTO -> MOTO
            MOTO -> CAR
            else -> AUTO
        }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, next).apply()
        LogBus.log("[NAV] travel mode → $next")
    }

    fun chipLabel(ctx: Context): String = when (current(ctx)) {
        AUTO -> "🤖 " + ctx.getString(R.string.nav_mode_auto)
        MOTO -> "🏍 " + ctx.getString(R.string.nav_mode_moto)
        else -> "🚗 " + ctx.getString(R.string.nav_mode_car)
    }
}
