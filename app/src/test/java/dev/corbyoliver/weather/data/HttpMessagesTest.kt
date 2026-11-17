package dev.corbyoliver.weather.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class HttpMessagesTest {
    @Test fun `no network reads as no internet connection`() {
        assertEquals("No internet connection.", unreachableMessage("Open-Meteo", UnknownHostException("api.open-meteo.com")))
        assertEquals("No internet connection.", unreachableMessage("Open-Meteo", IOException("wrapped", ConnectException("refused"))))
    }

    @Test fun `a slow service and other failures name the service`() {
        assertEquals("Open-Meteo did not answer in time.", unreachableMessage("Open-Meteo", SocketTimeoutException("read")))
        assertEquals("National Weather Service could not be reached.", unreachableMessage("National Weather Service", IOException("reset")))
    }
}
