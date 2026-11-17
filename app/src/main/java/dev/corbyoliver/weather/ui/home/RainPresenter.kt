package dev.corbyoliver.weather.ui.home

import dev.corbyoliver.weather.data.WeatherSnapshot
import dev.corbyoliver.weather.domain.HomeLocation
import dev.corbyoliver.weather.domain.Nowcast
import dev.corbyoliver.weather.domain.RainTiming
import dev.corbyoliver.weather.domain.RainTimingConfig
import java.time.Instant
import java.time.ZoneId

data class RainUi(val line: String?, val strip: List<Float?>, val nowcast: Nowcast?)

/** Rain sentence and 60-minute strip: Pirate Weather when it is fresh, else Open-Meteo. */
fun presentRain(
    snap: WeatherSnapshot,
    now: Instant,
    zone: ZoneId = HomeLocation.ZONE,
    cfg: RainTimingConfig = RainTimingConfig(),
): RainUi {
    val forecast = snap.forecast
    val nowcast = RainTiming.chooseNowcast(snap.rain.minutes, forecast?.quarterHours.orEmpty(), now, cfg)
    val line = RainTiming.sentence(nowcast, forecast?.hours.orEmpty(), now, zone, cfg)
    return RainUi(line, RainTiming.strip(nowcast, cfg), nowcast)
}
