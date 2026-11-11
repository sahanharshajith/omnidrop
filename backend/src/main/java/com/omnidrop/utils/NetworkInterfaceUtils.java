package com.omnidrop.utils;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

/**
 * Enumerates active local network interfaces to identify IPv4 addresses and broadcast targets.
 */
public final class NetworkInterfaceUtils {

    private NetworkInterfaceUtils() {}

    public static List<InetAddress> getLocalIPv4Addresses() {
        List<InetAddress> addresses = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces == null) return addresses;

            for (NetworkInterface nif : Collections.list(interfaces)) {
                if (nif.isLoopback() || !nif.isUp()) continue;

                for (InterfaceAddress ifAddr : nif.getInterfaceAddresses()) {
                    InetAddress addr = ifAddr.getAddress();
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        addresses.add(addr);
                    }
                }
            }
        } catch (SocketException ignored) {}

        if (addresses.isEmpty()) {
            try {
                addresses.add(InetAddress.getLocalHost());
            } catch (Exception ignored) {}
        }
        return addresses;
    }

    public static List<InetAddress> getBroadcastAddresses() {
        List<InetAddress> broadcastList = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces == null) return broadcastList;

            for (NetworkInterface nif : Collections.list(interfaces)) {
                if (nif.isLoopback() || !nif.isUp()) continue;

                for (InterfaceAddress ifAddr : nif.getInterfaceAddresses()) {
                    InetAddress broadcast = ifAddr.getBroadcast();
                    if (broadcast != null && broadcast instanceof Inet4Address) {
                        broadcastList.add(broadcast);
                    }
                }
            }
        } catch (SocketException ignored) {}

        if (broadcastList.isEmpty()) {
            try {
                broadcastList.add(InetAddress.getByName("255.255.255.255"));
            } catch (Exception ignored) {}
        }
        return broadcastList;
    }
}
