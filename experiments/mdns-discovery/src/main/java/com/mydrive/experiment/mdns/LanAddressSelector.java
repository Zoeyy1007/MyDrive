package com.mydrive.experiment.mdns;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;

final class LanAddressSelector {

    private LanAddressSelector() {
    }

    static InetAddress select(String requestedAddress) throws SocketException, UnknownHostException {
        if (requestedAddress != null && !requestedAddress.isBlank()) {
            InetAddress requested = InetAddress.getByName(requestedAddress.trim());
            NetworkInterface networkInterface = NetworkInterface.getByInetAddress(requested);
            if (networkInterface == null || !networkInterface.isUp()) {
                throw new IllegalArgumentException(
                        requested.getHostAddress() + " is not an address on an active local interface");
            }
            if (requested.isAnyLocalAddress() || requested.isLoopbackAddress()) {
                throw new IllegalArgumentException("Choose a LAN address, not " + requested.getHostAddress());
            }
            return requested;
        }

        List<InetAddress> candidates = new ArrayList<>();
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        while (interfaces.hasMoreElements()) {
            NetworkInterface networkInterface = interfaces.nextElement();
            if (!networkInterface.isUp()
                    || networkInterface.isLoopback()
                    || networkInterface.isVirtual()
                    || !networkInterface.supportsMulticast()) {
                continue;
            }

            Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
            while (addresses.hasMoreElements()) {
                InetAddress address = addresses.nextElement();
                if (address instanceof Inet4Address
                        && !address.isLoopbackAddress()
                        && !address.isLinkLocalAddress()) {
                    candidates.add(address);
                }
            }
        }

        return candidates.stream()
                .sorted(Comparator.comparing((InetAddress address) -> !address.isSiteLocalAddress()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No multicast-capable IPv4 LAN address was found. "
                                + "Pass your Wi-Fi address as the first argument."));
    }
}
