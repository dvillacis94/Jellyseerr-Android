package com.nesoi.seerr.android.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerAddressTest {
    private fun candidates(input: String) = ServerAddress.candidates(input).map { it.toString() }

    @Test fun `a bare host tries https and then http`() {
        assertEquals(listOf("https://requests.example.com/", "http://requests.example.com/"), candidates("requests.example.com"))
    }

    @Test fun `an explicit scheme is the only candidate`() {
        assertEquals(listOf("http://192.168.1.10:5055/"), candidates("http://192.168.1.10:5055/"))
        assertEquals(listOf("https://host/"), candidates("HTTPS://host"))
    }

    @Test fun `a host with a port and no scheme keeps the port`() {
        assertEquals(listOf("https://192.168.1.10:5055/", "http://192.168.1.10:5055/"), candidates("192.168.1.10:5055"))
    }

    @Test fun `a pasted api path is cut off`() {
        assertEquals(listOf("https://host/seerr/"), candidates("https://host/seerr/api/v1"))
        assertEquals(listOf("https://host/"), candidates("https://host/API/V1/"))
    }

    @Test fun `a sub path, query and fragment are handled`() {
        assertEquals(listOf("https://host/seerr/"), candidates("https://host/seerr/?a=1#top"))
    }

    @Test fun `surrounding spaces are ignored`() {
        assertEquals(listOf("https://host/"), candidates("  https://host  "))
    }

    @Test fun `empty input is rejected`() {
        assertEquals(ServerConnectionError.EmptyAddress, error(""))
        assertEquals(ServerConnectionError.EmptyAddress, error("   "))
    }

    @Test fun `other schemes and broken hosts are rejected`() {
        assertEquals(ServerConnectionError.InvalidAddress, error("ftp://host"))
        assertEquals(ServerConnectionError.InvalidAddress, error("my host"))
        assertEquals(ServerConnectionError.InvalidAddress, error("http://"))
    }

    @Test fun `http is reported as unencrypted`() {
        assertTrue(ServerAddress.isUnencrypted(ServerAddress.candidates("http://host").single()))
        assertFalse(ServerAddress.isUnencrypted(ServerAddress.candidates("https://host").single()))
    }

    @Test fun `local addresses are recognised`() {
        listOf("10.0.0.5", "192.168.1.10:5055", "172.16.0.1", "172.31.255.1", "127.0.0.1", "localhost:5055",
            "seerr.local", "nas", "http://seerr.lan", "[fd12::1]", "169.254.1.1").forEach {
            assertTrue(it, ServerAddress.looksLocal(it))
        }
    }

    @Test fun `public and invalid addresses are not local`() {
        listOf("requests.example.com", "https://8.8.8.8", "172.32.0.1", "172.15.0.1", "192.169.1.1", "11.0.0.1",
            "", "ftp://x").forEach {
            assertFalse(it, ServerAddress.looksLocal(it))
        }
    }

    private fun error(input: String): ServerConnectionError =
        assertThrows(ServerConnectionException::class.java) { ServerAddress.candidates(input) }.error
}
