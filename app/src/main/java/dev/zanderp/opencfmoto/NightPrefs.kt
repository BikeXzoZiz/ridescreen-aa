// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Alexandru <https://alexandru.rocks> and the OpenCfMoto contributors.
// Part of OpenCfMoto. Free software under the GNU AGPL v3 or later; see LICENSE and NOTICE.
package dev.zanderp.opencfmoto

import android.content.Context
import android.os.SystemClock
import androidx.annotation.StringRes
import java.util.Calendar
import java.util.Locale
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Map (Android Auto) day/night theme. Android Auto apps like Google Maps switch between their light
 * and dark map styles based on the head unit's NIGHT sensor — which OpenCfMoto reports (see
 * [dev.zanderp.opencfmoto.aa.NightModeEvent]). This just decides what value to report.
 */
enum class MapTheme(val label: String, @StringRes val labelRes: Int) {
    AUTO("Auto (day / night)", R.string.setup_auto_day_night),
    DAY("Day (light)", R.string.pref_map_theme_day),
    NIGHT("Night (dark)", R.string.pref_map_theme_night),
}

object NightPrefs {
    private const val PREFS = "opencfmoto_bike"
    private const val KEY = "map_theme"

    /** The sun this far below the horizon counts as night — civil twilight, when maps go dark. */
    private const val CIVIL_TWILIGHT_DEG = -6.0

    /** A location up to this old still says where the sun is; DashCards uses the same window. */
    private const val LOCATION_MAX_AGE_MS = 6 * 3_600_000L

    /** AUTO is re-evaluated at most this often — isNightNow runs per dashboard frame. */
    private const val RECHECK_MS = 30_000L

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun theme(ctx: Context): MapTheme {
        val name = prefs(ctx).getString(KEY, MapTheme.AUTO.name)
        return runCatching { MapTheme.valueOf(name!!) }.getOrDefault(MapTheme.AUTO)
    }

    fun setTheme(ctx: Context, theme: MapTheme) {
        prefs(ctx).edit().putString(KEY, theme.name).apply()
    }

    /** Cycle Auto → Day → Night → Auto (for a one-tap toggle). Returns the new theme. */
    fun cycle(ctx: Context): MapTheme {
        val next = when (theme(ctx)) {
            MapTheme.AUTO -> MapTheme.DAY
            MapTheme.DAY -> MapTheme.NIGHT
            MapTheme.NIGHT -> MapTheme.AUTO
        }
        setTheme(ctx, next)
        return next
    }

    private class Cached(val theme: MapTheme, val night: Boolean, val atMs: Long)

    private val lock = Any()
    private var cache: Cached? = null
    private var lastLoggedNight: Boolean? = null

    /**
     * The night value to report to Android Auto right now. In [MapTheme.AUTO] the sun decides: night
     * when the sun is more than [CIVIL_TWILIGHT_DEG] below the horizon at the last known location
     * ([RideExtras.lastLocation], up to [LOCATION_MAX_AGE_MS] old) — the same dusk signal a real head
     * unit gets from its own sensor, and one that carries a ride across sunset on its own. Without a
     * fix (permission off, no provider…) the local clock is the fallback, as before. The phone's own
     * dark theme is deliberately not consulted: the dash follows daylight, not the phone's UI mode.
     *
     * Cached for [RECHECK_MS] — the OBD dashboard's per-frame renders call this.
     */
    fun isNightNow(ctx: Context): Boolean {
        val theme = theme(ctx)
        val now = SystemClock.elapsedRealtime()
        synchronized(lock) {
            cache?.takeIf { it.theme == theme && now - it.atMs < RECHECK_MS }?.let { return it.night }
            val night: Boolean
            val why: String
            when (theme) {
                MapTheme.DAY -> { night = false; why = "theme Day" }
                MapTheme.NIGHT -> { night = true; why = "theme Night" }
                MapTheme.AUTO -> {
                    val loc = RideExtras.lastLocation(ctx, LOCATION_MAX_AGE_MS)
                    if (loc == null) {
                        night = clockSaysNight()
                        why = "clock (no location)"
                    } else {
                        val alt = sunAltitudeDeg(loc.latitude, loc.longitude, System.currentTimeMillis())
                        night = alt < CIVIL_TWILIGHT_DEG
                        why = "sun " + String.format(Locale.US, "%.1f", alt) + "° @" +
                            String.format(Locale.US, "%.3f,%.3f", loc.latitude, loc.longitude)
                    }
                }
            }
            cache = Cached(theme, night, now)
            if (night != lastLoggedNight) {
                lastLoggedNight = night
                LogBus.log("[NIGHT] ${if (night) "night" else "day"} — $why")
            }
            return night
        }
    }

    /** Night between 19:00 and 06:59 local time. */
    private fun clockSaysNight(): Boolean {
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return h < 7 || h >= 19
    }

    /**
     * Sun elevation in degrees above the horizon at [lat]/[lon] (degrees) at UTC epoch [utcMs] — a
     * compact NOAA solar position (accurate to well under a degree, plenty for a twilight check).
     * Comparing the elevation directly instead of rise/set times has no midnight or polar edge case.
     */
    private fun sunAltitudeDeg(lat: Double, lon: Double, utcMs: Long): Double {
        val n = utcMs / 86_400_000.0 + 2_440_587.5 - 2_451_545.0                   // days since J2000.0
        fun norm360(d: Double): Double = (d % 360.0 + 360.0) % 360.0
        val meanLon = norm360(280.460 + 0.9856474 * n)
        val meanAnom = Math.toRadians(norm360(357.528 + 0.9856003 * n))
        val eclLon = Math.toRadians(meanLon + 1.915 * sin(meanAnom) + 0.020 * sin(2 * meanAnom))
        val obliq = Math.toRadians(23.439 - 0.0000004 * n)
        val dec = asin(sin(obliq) * sin(eclLon))
        val ra = atan2(cos(obliq) * sin(eclLon), cos(eclLon))
        val gmst = norm360(280.46061837 + 360.98564736629 * n)
        val hourAngle = Math.toRadians(norm360(gmst + lon - Math.toDegrees(ra)))
        val latRad = Math.toRadians(lat)
        return Math.toDegrees(asin(sin(latRad) * sin(dec) + cos(latRad) * cos(dec) * cos(hourAngle)))
    }
}
