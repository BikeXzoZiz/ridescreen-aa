// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

import android.content.Context

/**
 * Which vehicle profile Google Maps starts turn-by-turn in when we hand it a destination:
 * motorcycle (two-wheeler) or car. The dash shows whatever Maps on the phone starts, so this is
 * the only lever over "car mode" navigation. Motorcycle is the default — this app exists to put
 * Android Auto on a motorbike. Toggled from the 🏍/🚗 chip next to the 🧭 app chip.
 */
object NavMode {
    const val MOTO = "moto"
    const val CAR = "car"
    private const val PREFS = "nav_mode"
    private const val KEY = "mode"

    /** Remembered mode; motorcycle until changed. */
    fun current(ctx: Context): String =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: MOTO

    fun toggle(ctx: Context) {
        val next = if (current(ctx) == MOTO) CAR else MOTO
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, next).apply()
        LogBus.log("[NAV] travel mode → $next")
    }

    fun chipLabel(ctx: Context): String =
        if (current(ctx) == MOTO) "🏍 " + ctx.getString(R.string.nav_mode_moto)
        else "🚗 " + ctx.getString(R.string.nav_mode_car)
}
