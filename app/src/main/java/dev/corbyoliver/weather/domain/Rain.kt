package dev.corbyoliver.weather.domain

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

enum class PrecipKind { RAIN, SNOW, SLEET, MIXED, NONE }

enum class NowcastSource { PIRATE_WEATHER, OPEN_METEO }

/** Precipitation for the minute [time, time + 1 min). Unknown values stay null. */
data class RainMinute(
    val time: Instant,
    /** Liquid-equivalent rate, inches per hour. */
    val intensityInPerHr: Double?,
    /** 0..1 */
    val probability: Double?,
    val kind: PrecipKind?,
)

/** The next hour, minute by minute, and where it came from. */
data class Nowcast(val source: NowcastSource, val minutes: List<RainMinute>)

/**
 * Every rain-timing threshold in one place. Starting suggestions; change them here.
 */
data class RainTimingConfig(
    /** At or above this rate (in/hr) a minute counts as wet. 0.005 in/hr is the lightest drizzle. */
    val wetInPerHr: Double = 0.005,
    /** Rain only "eases" when it stays dry at least this long. */
    val dryRunMinutes: Int = 10,
    /** An hour with at least this rain chance counts for "returns around" and "likely around". */
    val likelyChancePct: Int = 50,
    /** How far ahead the hourly forecast is searched. */
    val lookAhead: Duration = Duration.ofHours(12),
    /** Strip bar height is rate / this, capped at full height. 0.3 in/hr is heavy rain. */
    val stripFullInPerHr: Double = 0.3,
    /** Pirate Weather data is used only when it still covers at least this many minutes from now. */
    val minUsefulMinutes: Int = 30,
)

private val hourFmt = DateTimeFormatter.ofPattern("h a", Locale.US)
private val dayHourFmt = DateTimeFormatter.ofPattern("EEE h a", Locale.US)

object RainTiming {

    /**
     * Picks the minute data for the next hour: Pirate Weather when it still
     * covers enough of it, else Open-Meteo's 15-minute steps (the spec's
     * fallback), else null.
     */
    fun chooseNowcast(
        pirateMinutes: List<RainMinute>?,
        quarterHours: List<QuarterHour>,
        now: Instant,
        cfg: RainTimingConfig = RainTimingConfig(),
    ): Nowcast? {
        val startOfMinute = now.minusSeconds(now.epochSecond % 60).minusNanos(now.nano.toLong())
        val pirate = pirateMinutes.orEmpty()
            .filter { !it.time.isBefore(startOfMinute) && it.time.isBefore(now.plus(Duration.ofMinutes(60))) }
            .sortedBy { it.time }
        if (pirate.size >= cfg.minUsefulMinutes && pirate.any { it.intensityInPerHr != null }) {
            return Nowcast(NowcastSource.PIRATE_WEATHER, pirate)
        }
        return fromQuarterHours(quarterHours, startOfMinute)
    }

    /**
     * Spreads Open-Meteo 15-minute amounts over single minutes. A step at t
     * covers [t - 15 min, t), so its rate is amount × 4 in/hr. Null if the
     * steps do not cover the next 60 minutes.
     */
    fun fromQuarterHours(quarters: List<QuarterHour>, start: Instant): Nowcast? {
        val minutes = (0 until 60).map { i ->
            val t = start.plus(Duration.ofMinutes(i.toLong()))
            val q = quarters.firstOrNull { !t.isBefore(it.time.minus(Duration.ofMinutes(15))) && t.isBefore(it.time) }
                ?: return null
            RainMinute(t, q.precipPrevQuarterIn?.let { it * 4 }, null, null)
        }
        if (minutes.all { it.intensityInPerHr == null }) return null
        return Nowcast(NowcastSource.OPEN_METEO, minutes)
    }

    /** 12 bars of 5 minutes each, 0..1 of [RainTimingConfig.stripFullInPerHr]; null where unknown. Empty when there is no nowcast. */
    fun strip(nowcast: Nowcast?, cfg: RainTimingConfig = RainTimingConfig()): List<Float?> {
        val minutes = nowcast?.minutes ?: return emptyList()
        return (0 until 12).map { b ->
            val bucket = minutes.drop(b * 5).take(5).mapNotNull { it.intensityInPerHr }
            if (bucket.isEmpty()) null else (bucket.max() / cfg.stripFullInPerHr).coerceIn(0.0, 1.0).toFloat()
        }
    }

    /**
     * The one-line rain sentence, for example
     * "Rain eases in ~25 min · returns around 8 PM". Null when nothing is known.
     */
    fun sentence(
        nowcast: Nowcast?,
        hours: List<Hour>,
        now: Instant,
        zone: ZoneId,
        cfg: RainTimingConfig = RainTimingConfig(),
    ): String? {
        val minutes = nowcast?.minutes.orEmpty()
        if (minutes.isEmpty()) {
            if (hours.isEmpty()) return null
            val likely = likelyRainHourStart(hours, now, cfg)
            return if (likely != null) "Rain likely around ${clock(likely, now, zone)}" else noRainAhead(cfg)
        }
        fun wet(i: Int) = minutes.getOrNull(i)?.intensityInPerHr?.let { it >= cfg.wetInPerHr } == true
        val word = word(minutes.filter { (it.intensityInPerHr ?: 0.0) >= cfg.wetInPerHr })
        val end = minutes.last().time.plus(Duration.ofMinutes(1))

        if (wet(0)) {
            val stop = minutes.indices.firstOrNull { i ->
                (i until minOf(i + cfg.dryRunMinutes, minutes.size)).none { wet(it) } &&
                    (i + cfg.dryRunMinutes <= minutes.size || i > 0)
            }
            if (stop == null) {
                val ease = firstDryHourStart(hours, end, now, cfg)
                return "$word for the next hour" + (ease?.let { " · easing around ${clock(it, now, zone)}" } ?: "")
            }
            val back = (stop until minutes.size).firstOrNull { wet(it) }
            val returns = when {
                back != null -> " · returns in ${about(back)}"
                else -> likelyRainHourStart(hours, end, cfg)?.let { " · returns around ${clock(it, now, zone)}" } ?: ""
            }
            return "$word eases in ${about(stop)}$returns"
        }

        val start = minutes.indices.firstOrNull { wet(it) }
        if (start != null) return "$word starting in ${about(start)}"
        val later = likelyRainHourStart(hours, end, cfg)
        return if (later != null) "Dry for the next hour · rain likely around ${clock(later, now, zone)}" else noRainAhead(cfg)
    }

    private fun noRainAhead(cfg: RainTimingConfig) = "No rain expected in the next ${cfg.lookAhead.toHours()} hours"

    /** Start of the first hour from [from] (within the look-ahead) whose rain chance is likely. */
    private fun likelyRainHourStart(hours: List<Hour>, from: Instant, cfg: RainTimingConfig): Instant? =
        hours.asSequence()
            .map { it to it.time.minus(Duration.ofHours(1)) } // "prevHour" values cover [t - 1h, t)
            .filter { (_, start) -> !start.plus(Duration.ofHours(1)).isBefore(from) && start.isBefore(from.plus(cfg.lookAhead)) }
            .firstOrNull { (h, _) -> (h.precipChancePrevHourPct ?: -1) >= cfg.likelyChancePct }
            ?.let { (_, start) -> maxOf(start, from) }

    /** Start of the first hour after [from] whose rain chance drops below likely. */
    private fun firstDryHourStart(hours: List<Hour>, from: Instant, now: Instant, cfg: RainTimingConfig): Instant? =
        hours.asSequence()
            .map { it to it.time.minus(Duration.ofHours(1)) }
            .filter { (_, start) -> !start.isBefore(from) && start.isBefore(now.plus(cfg.lookAhead)) }
            .firstOrNull { (h, _) -> h.precipChancePrevHourPct != null && h.precipChancePrevHourPct < cfg.likelyChancePct }
            ?.second

    private fun word(wetMinutes: List<RainMinute>): String {
        val kinds = wetMinutes.mapNotNull { it.kind }.filter { it != PrecipKind.NONE }
        return when {
            kinds.isEmpty() -> "Rain"
            kinds.all { it == PrecipKind.SNOW } -> "Snow"
            kinds.all { it == PrecipKind.RAIN } -> "Rain"
            else -> "Wintry mix"
        }
    }

    /** "~25 min", rounded to 5 minutes; "a few min" under 3. */
    internal fun about(minutes: Int): String = when {
        minutes < 3 -> "a few min"
        else -> "~${maxOf(5, (minutes / 5.0).roundToInt() * 5)} min"
    }

    /** "8 PM" today, "Wed 2 AM" on another day. Rounded down to the hour. */
    private fun clock(t: Instant, now: Instant, zone: ZoneId): String {
        val local = t.atZone(zone)
        return if (local.toLocalDate() == now.atZone(zone).toLocalDate()) hourFmt.format(local) else dayHourFmt.format(local)
    }
}
