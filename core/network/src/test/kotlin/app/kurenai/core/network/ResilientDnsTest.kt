package app.kurenai.core.network

import okhttp3.Dns
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.net.UnknownHostException

class ResilientDnsTest {
    private class FakeDns(
        private val addresses: List<InetAddress>,
        private val fail: Boolean = false,
    ) : Dns {
        var calls = 0
            private set

        override fun lookup(hostname: String): List<InetAddress> {
            calls++
            if (fail) throw UnknownHostException(hostname)
            return addresses
        }
    }

    private val address = InetAddress.getByAddress(byteArrayOf(8, 8, 8, 8))

    @Test
    fun `falls back to encrypted resolver when system DNS fails`() {
        val system = FakeDns(emptyList(), fail = true)
        val encrypted = FakeDns(listOf(address))
        val dns = ResilientDns(preferEncrypted = false, system = system, encrypted = listOf(encrypted))

        assertEquals(listOf(address), dns.lookup("example.com"))
        assertEquals(1, system.calls)
        assertEquals(1, encrypted.calls)
    }

    @Test
    fun `falls back to system DNS when encrypted resolver is blocked`() {
        val system = FakeDns(listOf(address))
        val encrypted = FakeDns(emptyList(), fail = true)
        val dns = ResilientDns(preferEncrypted = true, system = system, encrypted = listOf(encrypted))

        assertEquals(listOf(address), dns.lookup("example.com"))
    }

    @Test
    fun `positive answers are cached`() {
        val system = FakeDns(listOf(address))
        val dns = ResilientDns(preferEncrypted = false, system = system)

        dns.lookup("cached.example")
        dns.lookup("cached.example")

        assertEquals(1, system.calls)
    }

    @Test
    fun `all resolvers failing raises UnknownHostException`() {
        val dns = ResilientDns(
            preferEncrypted = false,
            system = FakeDns(emptyList(), fail = true),
            encrypted = listOf(FakeDns(emptyList(), fail = true)),
        )
        val failed = try {
            dns.lookup("missing.example")
            false
        } catch (_: UnknownHostException) {
            true
        }
        assertTrue(failed)
    }
}
