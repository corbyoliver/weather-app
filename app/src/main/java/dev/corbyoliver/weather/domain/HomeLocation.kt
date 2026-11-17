package dev.corbyoliver.weather.domain

import java.time.ZoneId

/** The one fixed place this app forecasts for. See CLAUDE.md, "Decisions". */
object HomeLocation {
    const val NAME = "Columbia City, OR"
    const val LATITUDE = 45.8901
    const val LONGITUDE = -122.8071
    val ZONE: ZoneId = ZoneId.of("America/Los_Angeles")
}
