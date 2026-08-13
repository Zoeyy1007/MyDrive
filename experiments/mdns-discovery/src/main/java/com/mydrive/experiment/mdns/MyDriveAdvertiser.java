package com.mydrive.experiment.mdns;

import java.net.InetAddress;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;

public final class MyDriveAdvertiser {

    private static final String SERVICE_TYPE = "_mydrive._tcp.local.";
    private static final String DEFAULT_SERVICE_NAME = "MyDrive Experiment";
    private static final int DEFAULT_PORT = 8080;

    private MyDriveAdvertiser() {
    }

    public static void main(String[] args) throws Exception {
        String requestedAddress = argument(args, 0, null);
        int port = Integer.parseInt(argument(args, 1, Integer.toString(DEFAULT_PORT)));
        String serviceName = argument(args, 2, DEFAULT_SERVICE_NAME);
        InetAddress lanAddress = LanAddressSelector.select(requestedAddress);

        Map<String, String> txtProperties = new LinkedHashMap<>();
        txtProperties.put("service", "mydrive");
        txtProperties.put("serverId", "mdns-experiment-server");
        txtProperties.put("version", "1.0");
        txtProperties.put("apiPath", "/api");
        txtProperties.put("tls", "false");

        ServiceInfo service = ServiceInfo.create(
                SERVICE_TYPE,
                serviceName,
                port,
                0,
                0,
                txtProperties);

        System.out.println("Using LAN interface: " + lanAddress.getHostAddress());
        try (JmDNS jmdns = JmDNS.create(lanAddress, "mydrive-experiment-advertiser")) {
            jmdns.registerService(service);

            System.out.println("Advertising " + serviceName);
            System.out.println(lanAddress.getHostAddress() + ":" + port);
            System.out.println("Service type: " + SERVICE_TYPE);
            System.out.println("Press Enter to stop advertising.");
            System.in.read();

            jmdns.unregisterService(service);
        }
    }

    private static String argument(String[] args, int index, String defaultValue) {
        return args.length > index && !args[index].isBlank() ? args[index] : defaultValue;
    }
}
