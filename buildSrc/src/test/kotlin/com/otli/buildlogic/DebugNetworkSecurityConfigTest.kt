package com.otli.buildlogic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugNetworkSecurityConfigTest {

    private val defaults = listOf("127.0.0.1", "10.0.2.2", "localhost")

    private fun domains(xml: String): List<String> =
        Regex("<domain includeSubdomains=\"false\">([^<]+)</domain>").findAll(xml).map { it.groupValues[1] }.toList()

    @Test
    fun `default host yields exactly the three default domains`() {
        assertEquals(defaults, domains(DebugNetworkSecurityConfig.xml("10.0.2.2")))
    }

    @Test
    fun `LAN IPv4 host is appended after the defaults`() {
        assertEquals(defaults + "192.168.1.9", domains(DebugNetworkSecurityConfig.xml("192.168.1.9")))
    }

    @Test
    fun `hostname is accepted and lowercased`() {
        assertEquals(defaults + "my-pc.local", domains(DebugNetworkSecurityConfig.xml("My-PC.local")))
    }

    @Test
    fun `host equal to a default is not duplicated, case-insensitively`() {
        assertEquals(defaults, domains(DebugNetworkSecurityConfig.xml("LOCALHOST")))
        assertEquals(defaults, domains(DebugNetworkSecurityConfig.xml("127.0.0.1")))
    }

    @Test
    fun `base config never permits cleartext`() {
        val xml = DebugNetworkSecurityConfig.xml("192.168.1.9")
        assertTrue(xml.contains("<base-config cleartextTrafficPermitted=\"false\" />"))
        assertEquals(1, Regex("cleartextTrafficPermitted=\"true\"").findAll(xml).count())
        assertFalse(xml.contains("<base-config cleartextTrafficPermitted=\"true\""))
    }

    @Test
    fun `malformed hosts are rejected with a clear message`() {
        listOf(
            "", " ", "a b", "<x>", "a&b", "a\"b", "a'b", "-bad.com", "bad-.com", "a..b", "a.b.",
            "999.1.1.1", "1.2.3", "1.2.3.4.5", "http://1.2.3.4", "1.2.3.4:8080", "*.example.com", "a/b",
        ).forEach { bad ->
            val e = assertThrows("'$bad'", IllegalArgumentException::class.java) {
                DebugNetworkSecurityConfig.xml(bad)
            }
            assertTrue(e.message!!.contains("otli.emulatorHost"))
        }
    }
}
