package com.mydrive.experiment.mdns;

import java.net.InetAddress;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.jmdns.JmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceInfo;
import javax.jmdns.ServiceListener;

public final class MyDriveFinder {

    private static final String SERVICE_TYPE = "_mydrive._tcp.local.";
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(15);

    private MyDriveFinder() {
    }

    public static void main(String[] args) throws Exception {
        String requestedAddress = argument(args, 0, null);
        long timeoutSeconds = Long.parseLong(argument(
                args, 1, Long.toString(DEFAULT_TIMEOUT.toSeconds())));
        InetAddress lanAddress = LanAddressSelector.select(requestedAddress);
        CountDownLatch found = new CountDownLatch(1);
        AtomicBoolean printed = new AtomicBoolean();

        ServiceListener listener = new ServiceListener() {
            @Override
            public void serviceAdded(ServiceEvent event) {
                System.out.println("MyDrive announcement appeared: " + event.getName());
                event.getDNS().requestServiceInfo(event.getType(), event.getName(), true);
            }

            @Override
            public void serviceRemoved(ServiceEvent event) {
                System.out.println("MyDrive announcement disappeared: " + event.getName());
            }

            @Override
            public void serviceResolved(ServiceEvent event) {
                ServiceInfo service = event.getInfo();
                if (!"mydrive".equals(service.getPropertyString("service"))) {
                    return;
                }

                InetAddress address = firstAddress(service);
                if (address == null || !printed.compareAndSet(false, true)) {
                    return;
                }

                System.out.println("Found MyDrive!");
                System.out.println(address.getHostAddress() + ":" + service.getPort());
                System.out.println("Name: " + service.getName());
                System.out.println("Host: " + service.getServer());
                System.out.println("serverId: " + service.getPropertyString("serverId"));
                System.out.println("version: " + service.getPropertyString("version"));
                found.countDown();
            }
        };

        System.out.println("Using LAN interface: " + lanAddress.getHostAddress());
        System.out.println("Searching for " + SERVICE_TYPE + " for " + timeoutSeconds + " seconds...");

        try (JmDNS jmdns = JmDNS.create(lanAddress, "mydrive-experiment-finder")) {
            jmdns.addServiceListener(SERVICE_TYPE, listener);
            boolean discovered = found.await(timeoutSeconds, TimeUnit.SECONDS);
            jmdns.removeServiceListener(SERVICE_TYPE, listener);

            if (!discovered) {
                System.err.println("No MyDrive service was found.");
                System.err.println("Check that both devices use the same Wi-Fi, disable VPNs, ");
                System.err.println("and allow Java/mDNS (UDP 5353) through each firewall.");
                System.exit(1);
            }
        }
    }

    private static InetAddress firstAddress(ServiceInfo service) {
        InetAddress[] ipv4Addresses = service.getInet4Addresses();
        if (ipv4Addresses.length > 0) {
            return ipv4Addresses[0];
        }
        InetAddress[] allAddresses = service.getInetAddresses();
        return allAddresses.length > 0 ? allAddresses[0] : null;
    }

    private static String argument(String[] args, int index, String defaultValue) {
        return args.length > index && !args[index].isBlank() ? args[index] : defaultValue;
    }
}
