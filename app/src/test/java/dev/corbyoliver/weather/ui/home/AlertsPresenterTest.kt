package dev.corbyoliver.weather.ui.home

import dev.corbyoliver.weather.data.AlertsSnapshot
import dev.corbyoliver.weather.data.nws.NWS_OREGON_FIXTURE
import dev.corbyoliver.weather.data.nws.NwsMapper
import dev.corbyoliver.weather.domain.Alert
import dev.corbyoliver.weather.domain.AlertSeverity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class AlertsPresenterTest {
    private val checked = Instant.parse("2026-11-17T22:49:00Z") // 2:49 PM PST
    private val oregon = NwsMapper.map(NWS_OREGON_FIXTURE)
    private fun known(alerts: List<Alert>, error: String? = null) = AlertsSnapshot(alerts, checked, error)

    @Test fun `before any check the note says checking, never no alerts`() {
        val ui = presentAlerts(AlertsSnapshot(), checked)
        assertNull(ui.banner)
        assertEquals(ALERTS_CHECKING, ui.note)
    }

    @Test fun `a failed first check says alerts unavailable`() {
        val ui = presentAlerts(AlertsSnapshot(lastError = "down"), checked)
        assertNull(ui.banner)
        assertEquals(ALERTS_UNAVAILABLE, ui.note)
    }

    @Test fun `a fresh empty list shows nothing at all`() {
        val ui = presentAlerts(known(emptyList()), checked.plus(Duration.ofMinutes(10)))
        assertNull(ui.banner)
        assertNull(ui.note)
    }

    @Test fun `an old check is marked unavailable with its time`() {
        val ui = presentAlerts(known(emptyList()), checked.plus(Duration.ofMinutes(61)))
        assertEquals("Alerts unavailable · last checked 2:49 PM", ui.note)
    }

    @Test fun `a failed later check keeps the known alerts and says so`() {
        val ui = presentAlerts(known(oregon, error = "down"), checked.plus(Duration.ofMinutes(5)))
        assertEquals("Alerts unavailable · last checked 2:49 PM", ui.note)
        assertEquals("Wind Advisory from 7 PM · NWS", ui.banner?.text)
    }

    @Test fun `banner shows the soonest alert first, with a start time when it has not started`() {
        val b = presentAlerts(known(oregon), checked)
        assertEquals("Wind Advisory from 7 PM · NWS", b.banner?.text)
        assertEquals("Wind Advisory", b.banner?.title)
        assertEquals(1, b.banner?.moreCount)
        assertTrue(b.banner!!.body.startsWith("WIND ADVISORY\n7 PM to Wed 7 AM"))
        assertTrue(b.banner!!.body.contains("WINTER WEATHER ADVISORY"))
    }

    @Test fun `a running alert shows its end time, with the day when it is not today`() {
        val evening = Instant.parse("2026-11-18T04:00:00Z") // 8 PM PST Tuesday
        assertEquals("Wind Advisory until Wed 7 AM · NWS", presentAlerts(known(oregon), evening).banner?.text)
    }

    @Test fun `an ended alert disappears without a new check`() {
        val wedMorning = Instant.parse("2026-11-18T16:00:00Z") // 8 AM PST Wednesday
        val b = presentAlerts(AlertsSnapshot(oregon, wedMorning.minus(Duration.ofMinutes(5))), wedMorning).banner
        assertEquals("Winter Weather Advisory until 10 PM · NWS", b?.text)
        assertEquals(0, b?.moreCount)
        val thursday = Instant.parse("2026-11-19T08:00:00Z")
        assertNull(presentAlerts(AlertsSnapshot(oregon, thursday), thursday).banner)
    }

    @Test fun `more serious alerts come first`() {
        fun alert(id: String, sev: AlertSeverity, onset: String) =
            Alert(id, id, sev, Instant.parse(onset), null, null, null, null, null, null)
        val list = listOf(
            alert("Minor soon", AlertSeverity.MINOR, "2026-11-17T20:00:00Z"),
            alert("Severe later", AlertSeverity.SEVERE, "2026-11-18T20:00:00Z"),
        )
        assertEquals("Severe later", activeAlerts(list, checked).first().event)
    }

    @Test fun `times on the half hour keep their minutes`() {
        assertEquals("9:30 PM", whenText(Instant.parse("2026-11-18T05:30:00Z"), checked, dev.corbyoliver.weather.domain.HomeLocation.ZONE))
    }

    @Test fun `wrapped NWS lines are joined but paragraphs stay apart`() {
        assertEquals("* WHAT...Winds 20 to 30 mph.\n\n* WHERE...Ashland and Medford.",
            unwrap("* WHAT...Winds 20 to\n30 mph.\n\n* WHERE...Ashland and\nMedford.\n"))
        val body = presentAlerts(known(oregon), checked).banner!!.body
        assertTrue(body.contains("Tree limbs could be blown down"))
    }
}
