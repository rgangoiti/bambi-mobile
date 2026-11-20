package com.shieldstream.tv.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ConfigTransferTest {

    @Test
    fun `direct mode includes the local key`() {
        val config = ConfigTransfer.snapshot(
            serverUrl = null,
            debridService = "ALLDEBRID",
            token = "  abc123  ",
        )

        assertEquals("ALLDEBRID", config.debridService)
        assertEquals("abc123", config.token)
        assertNull(config.serverUrl)
    }

    @Test
    fun `server mode never exports the key`() {
        val config = ConfigTransfer.snapshot(
            serverUrl = "http://192.168.1.215:8201",
            debridService = "REAL_DEBRID",
            token = "abc123",
        )

        assertEquals("http://192.168.1.215:8201", config.serverUrl)
        assertNull(config.token)
    }

    @Test
    fun `json round trip keeps the settings`() {
        val original = ExportedConfig(
            serverUrl = "http://192.168.1.215:8201",
            debridService = "TORBOX",
            token = null,
        )

        val parsed = ConfigTransfer.parse(ConfigTransfer.toJson(original))

        assertEquals(original, parsed)
    }

    @Test
    fun `parsing is tolerant with extra and missing fields`() {
        val parsed = ConfigTransfer.parse("""{"debridService":"premiumize","unknown":42,"version":1}""")

        assertNotNull(parsed)
        assertEquals("premiumize", parsed?.debridService)
        assertNull(parsed?.serverUrl)
        assertNull(parsed?.token)
    }

    @Test
    fun `invalid json does not blow up`() {
        assertNull(ConfigTransfer.parse("no soy json"))
        assertNull(ConfigTransfer.parse("{}}"))
        assertNull(ConfigTransfer.parse("""{"otra":"cosa"}"""))
    }

    @Test
    fun `importing our own export restores the key only in direct mode`() {
        val exported = ConfigTransfer.toJson(
            ConfigTransfer.snapshot(serverUrl = null, debridService = "REAL_DEBRID", token = "tok"),
        )
        val restored = ConfigTransfer.parse(exported)

        assertEquals("tok", restored?.token)
        assertEquals("REAL_DEBRID", restored?.debridService)
    }
}
