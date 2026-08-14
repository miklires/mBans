package io.github.miklires.mbans.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IpRangeTest {
    @Test void matchesIpv4Cidr() {
        assertTrue(IpRange.contains("10.0.0.0/8", "10.20.30.40"));
        assertFalse(IpRange.contains("10.0.0.0/8", "11.20.30.40"));
    }

    @Test void matchesIpv6Cidr() {
        assertTrue(IpRange.contains("2001:db8::/32", "2001:db8::1234"));
        assertFalse(IpRange.contains("2001:db8::/32", "2001:db9::1"));
    }
}
