package com.mafia.server.net;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 다른 PC가 접속할 주소 안내. 루프백·IPv6는 빼고 IPv4만 보여 준다. */
final class LanAddressesTest {

    @Test
    void listsOnlyNonLoopbackIpv4Addresses() {
        List<String> addresses = LanAddresses.ipv4();
        for (String address : addresses) {
            assertTrue(address.matches("\\d{1,3}(\\.\\d{1,3}){3}"), address);
            assertFalse(address.startsWith("127."), "루프백 제외: " + address);
        }
    }

    @Test
    void linkLocalAndVirtualAdapterAddressesAreNotOffered() {
        assertTrue(LanAddresses.usable("172.16.6.15", "Intel(R) Wi-Fi 6 AX201"));
        assertTrue(LanAddresses.usable("192.168.0.5", "Realtek PCIe GbE Family Controller"));
        assertFalse(LanAddresses.usable("169.254.83.107", "Intel(R) Wi-Fi 6 AX201"), "IP를 못 받은 자동 주소");
        assertFalse(LanAddresses.usable("172.29.96.1", "Hyper-V Virtual Ethernet Adapter"), "가상 스위치");
        assertFalse(LanAddresses.usable("192.168.56.1", "VirtualBox Host-Only Ethernet Adapter"));
        assertFalse(LanAddresses.usable("192.168.80.1", "VMware Virtual Ethernet Adapter for VMnet8"));
    }

    @Test
    void guideNamesEachAddressWithThePortOrFallsBackToLocalhost() {
        assertEquals(List.of("192.168.0.5:5000", "10.0.0.2:5000"),
                LanAddresses.guide(List.of("192.168.0.5", "10.0.0.2"), 5000));
        assertEquals(List.of("localhost:5000 (네트워크 주소를 찾지 못함)"), LanAddresses.guide(List.of(), 5000));
    }
}
