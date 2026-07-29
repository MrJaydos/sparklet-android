package com.sparklet.android.network

import java.time.Instant
import java.time.ZoneId

object TimeZoneOffset {
    // The backend expects JS Date.getTimezoneOffset() semantics: minutes
    // WEST of UTC (e.g. EST = +300) — used as tzOffsetMinutes on
    // /api/interactions and the ?tz= param on /api/profile. java.time's
    // zone-offset seconds are positive EAST, the opposite sign. Don't "fix"
    // this to the natural Java sign — it has to match localDayStart in
    // sparklet/src/lib/xp.ts.
    fun minutesWestOfUtc(): Int {
        val offsetSeconds = ZoneId.systemDefault().rules.getOffset(Instant.now()).totalSeconds
        return -offsetSeconds / 60
    }
}
