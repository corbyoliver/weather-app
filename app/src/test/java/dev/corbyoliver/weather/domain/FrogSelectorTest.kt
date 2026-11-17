package dev.corbyoliver.weather.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class FrogSelectorTest {
    private val calm = FrogInputs(nextHourRainChancePct = 10, gustMph = 8.0, temperatureF = 55.0, isDay = true, cloudCoverPct = 50)

    @Test fun `rain at the threshold wins`() =
        assertEquals(FrogState.RAIN, selectFrogState(calm.copy(nextHourRainChancePct = 40)))

    @Test fun `rain just under the threshold does not`() =
        assertEquals(FrogState.CLOUDY, selectFrogState(calm.copy(nextHourRainChancePct = 39)))

    @Test fun `rain beats wind cold and clear`() = assertEquals(
        FrogState.RAIN,
        selectFrogState(FrogInputs(90, 40.0, 20.0, false, 0)),
    )

    @Test fun `gust at threshold is windy`() =
        assertEquals(FrogState.WINDY, selectFrogState(calm.copy(gustMph = 25.0)))

    @Test fun `wind beats cold`() =
        assertEquals(FrogState.WINDY, selectFrogState(calm.copy(gustMph = 30.0, temperatureF = 20.0)))

    @Test fun `34F is a cold snap, 35F is not`() {
        assertEquals(FrogState.COLD_SNAP, selectFrogState(calm.copy(temperatureF = 34.0)))
        assertEquals(FrogState.CLOUDY, selectFrogState(calm.copy(temperatureF = 35.0)))
    }

    @Test fun `cold beats clear night`() =
        assertEquals(FrogState.COLD_SNAP, selectFrogState(calm.copy(temperatureF = 30.0, isDay = false, cloudCoverPct = 0)))

    @Test fun `clear night needs cloud under 60`() {
        assertEquals(FrogState.CLEAR_NIGHT, selectFrogState(calm.copy(isDay = false, cloudCoverPct = 59)))
        assertEquals(FrogState.CLOUDY, selectFrogState(calm.copy(isDay = false, cloudCoverPct = 60)))
    }

    @Test fun `clear day needs cloud under 30`() {
        assertEquals(FrogState.CLEAR_DAY, selectFrogState(calm.copy(cloudCoverPct = 29)))
        assertEquals(FrogState.CLOUDY, selectFrogState(calm.copy(cloudCoverPct = 30)))
    }

    @Test fun `unknown inputs never trigger a state`() = assertEquals(
        FrogState.CLOUDY,
        selectFrogState(FrogInputs(null, null, null, true, null)),
    )

    @Test fun `thresholds can be changed in one place`() = assertEquals(
        FrogState.RAIN,
        selectFrogState(calm.copy(nextHourRainChancePct = 20), FrogThresholds(rainChancePct = 20)),
    )
}
