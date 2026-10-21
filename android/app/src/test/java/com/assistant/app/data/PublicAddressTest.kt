package com.assistant.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress

class PublicAddressTest {
    private fun ip(text: String) = InetAddress.getByName(text)

    @Test fun `ordinary internet addresses are public`() {
        assertTrue(isPublicAddress(ip("8.8.8.8")))
        assertTrue(isPublicAddress(ip("93.184.216.34")))
        assertTrue(isPublicAddress(ip("2606:4700:4700::1111")))
    }

    @Test fun `loopback private and link local addresses are refused`() {
        listOf("127.0.0.1", "10.0.0.5", "172.16.3.4", "192.168.1.1", "169.254.169.254", "0.0.0.0", "::1", "fe80::1", "fd00::1")
            .forEach { assertFalse("$it should be refused", isPublicAddress(ip(it))) }
    }

    @Test fun `reserved shared and documentation ranges are refused`() {
        listOf("100.64.0.1", "192.0.0.8", "192.0.2.10", "198.18.0.1", "198.51.100.7", "203.0.113.9", "240.0.0.1", "255.255.255.255", "2001:db8::1")
            .forEach { assertFalse("$it should be refused", isPublicAddress(ip(it))) }
    }

    @Test fun `an ipv4 mapped ipv6 address is judged as its ipv4 address`() {
        assertFalse(isPublicAddress(ip("::ffff:10.1.2.3")))
        assertTrue(isPublicAddress(ip("::ffff:8.8.4.4")))
    }
}
