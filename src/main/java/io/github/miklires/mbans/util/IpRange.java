package io.github.miklires.mbans.util;

import java.net.InetAddress;

public final class IpRange {
    private IpRange() {}

    public static boolean contains(String range, String address) {
        try {
            String[] parts = range.trim().split("/", 2);
            byte[] network = InetAddress.getByName(parts[0]).getAddress();
            byte[] candidate = InetAddress.getByName(address).getAddress();
            if (network.length != candidate.length) return false;
            int bits = parts.length == 2 ? Integer.parseInt(parts[1]) : network.length * 8;
            if (bits < 0 || bits > network.length * 8) return false;
            for (int i = 0; i < network.length; i++) {
                int remaining = bits - i * 8;
                int mask = remaining >= 8 ? 255 : remaining <= 0 ? 0 : (255 << (8 - remaining)) & 255;
                if ((network[i] & mask) != (candidate[i] & mask)) return false;
            }
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }
}
