package com.otli.buildlogic

/**
 * Builds the debug-only network security config. Cleartext (the Firebase emulators speak plain
 * HTTP) is permitted for the loopback/emulator hosts plus exactly the configured emulator host,
 * and never through a blanket base-config.
 */
object DebugNetworkSecurityConfig {
    private val DEFAULT_HOSTS = listOf("127.0.0.1", "10.0.2.2", "localhost")

    private val NUMERIC_DOTTED = Regex("[0-9.]+")
    private val IPV4 = Regex("""(25[0-5]|2[0-4][0-9]|1[0-9]{2}|[1-9]?[0-9])(\.(25[0-5]|2[0-4][0-9]|1[0-9]{2}|[1-9]?[0-9])){3}""")
    private val HOSTNAME = Regex("""[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?(\.[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?)*""")

    /** Returns the normalized (lowercase) host or throws with a message naming the property. */
    fun validateHost(raw: String): String {
        val host = raw.lowercase()
        val ok = if (NUMERIC_DOTTED.matches(host)) IPV4.matches(host) else host.length <= 253 && HOSTNAME.matches(host)
        require(ok) {
            "Invalid otli.emulatorHost '$raw': expected an IPv4 address (e.g. 192.168.1.9) or a simple hostname " +
                "(letters, digits, '-' and '.' only; no scheme, port, path or wildcard)."
        }
        return host
    }

    fun xml(emulatorHost: String): String {
        val hosts = (DEFAULT_HOSTS + validateHost(emulatorHost)).distinct()
        val domains = hosts.joinToString("\n") { "        <domain includeSubdomains=\"false\">$it</domain>" }
        return """<?xml version="1.0" encoding="utf-8"?>
<!-- Generated for debug builds only from otli.emulatorHost: cleartext is limited to the emulator hosts. -->
<network-security-config>
    <base-config cleartextTrafficPermitted="false" />
    <domain-config cleartextTrafficPermitted="true">
$domains
    </domain-config>
</network-security-config>
"""
    }
}
