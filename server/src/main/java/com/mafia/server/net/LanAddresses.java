package com.mafia.server.net;

import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 서버를 0.0.0.0으로 열었을 때 다른 PC가 입력할 주소를 알려 준다. 접속 처리와는 상관없는 안내용이다. */
public final class LanAddresses {
    private LanAddresses() {
    }

    private static final List<String> VIRTUAL_ADAPTERS = List.of("virtual", "vethernet", "hyper-v", "vmware", "vbox");

    /** 켜져 있는 실제 네트워크 카드의 IPv4 주소 (루프백·자동 주소·가상 어댑터 제외). 못 읽으면 빈 목록. */
    public static List<String> ipv4() {
        List<String> found = new ArrayList<>();
        try {
            for (NetworkInterface nic : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!nic.isUp() || nic.isLoopback()) continue;
                String adapter = nic.getDisplayName() == null ? nic.getName() : nic.getDisplayName();
                Collections.list(nic.getInetAddresses()).stream()
                        .filter(address -> address instanceof Inet4Address && !address.isLoopbackAddress())
                        .map(address -> address.getHostAddress())
                        .filter(address -> usable(address, adapter))
                        .forEach(found::add);
            }
        } catch (SocketException e) {
            return List.of();
        }
        return List.copyOf(found);
    }

    /** 다른 PC가 쓸 수 있는 주소인가. 169.254.x.x는 IP를 못 받았을 때의 자동 주소, 가상 어댑터는 이 PC 안에서만 보인다. */
    static boolean usable(String address, String adapter) {
        if (address.startsWith("127.") || address.startsWith("169.254.")) return false;
        String name = adapter.toLowerCase(java.util.Locale.ROOT);
        return VIRTUAL_ADAPTERS.stream().noneMatch(name::contains);
    }

    /** "주소:포트" 목록. 주소를 못 찾으면 같은 PC용 localhost만 안내한다. */
    public static List<String> guide(List<String> addresses, int port) {
        if (addresses.isEmpty()) return List.of("localhost:" + port + " (네트워크 주소를 찾지 못함)");
        return addresses.stream().map(address -> address + ":" + port).toList();
    }
}
