package io.github.miklires.mbans.command;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import io.github.miklires.mbans.MBans;
import io.github.miklires.mbans.service.DurationParser;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;

public class CommandHelper {

    public static OfflinePlayer resolveOfflinePlayer(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online;
        OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        if (offline.hasPlayedBefore() || offline.isOnline()) return offline;
        return null;
    }

    public static String issuerName(CommandSender sender) {
        return sender.getName();
    }

    public static UUID issuerUuid(CommandSender sender) {
        if (sender instanceof Player p) return p.getUniqueId();
        return null;
    }

    public static ParsedArgs parseDurationAndReason(String[] args, int startIndex) {
        if (args.length <= startIndex) return new ParsedArgs(null, null);
        Optional<Duration> first = DurationParser.parse(args[startIndex]);
        if (first.isPresent()) {
            if (args.length <= startIndex + 1) return new ParsedArgs(first.get(), null);
            String reason = joinFrom(args, startIndex + 1);
            return new ParsedArgs(first.get(), reason);
        }
        return new ParsedArgs(null, joinFrom(args, startIndex));
    }

    public static String joinFrom(String[] args, int from) {
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < args.length; i++) {
            if (i > from) sb.append(' ');
            sb.append(args[i]);
        }
        return sb.toString().trim();
    }

    public static boolean isValidIp(String s) {
        return normalizeIp(s) != null;
    }

    public static String normalizeIp(String s) {
        if (s == null || s.isBlank()) return null;
        String[] parts = s.split("\\.");
        if (parts.length == 4) try {
            StringBuilder normalized = new StringBuilder();
            for (String p : parts) {
                int n = Integer.parseInt(p);
                if (n < 0 || n > 255) return null;
                if (!normalized.isEmpty()) normalized.append('.');
                normalized.append(n);
            }
            return normalized.toString();
        } catch (NumberFormatException e) {
            return null;
        }
        if (!s.contains(":") || !s.matches("[0-9A-Fa-f:]+")) return null;
        try {
            java.net.InetAddress address = java.net.InetAddress.getByName(s);
            return address.getAddress().length == 16 ? address.getHostAddress() : null;
        } catch (java.net.UnknownHostException e) {
            return null;
        }
    }

    public static boolean hasBypass(OfflinePlayer target, String permission, MBans plugin) {
        if (target instanceof Player p) return p.hasPermission(permission);
        return false;
    }

    public record ParsedArgs(Duration duration, String reason) {}

    public static Options parseOptions(String[] args, int from) {
        boolean silent = false;
        String evidence = null;
        int lastMessages = 0;
        List<String> text = new ArrayList<>();
        for (int i = from; i < args.length; i++) {
            String value = args[i];
            if (value.equalsIgnoreCase("-s")) {
                silent = true;
            } else if (value.regionMatches(true, 0, "--evidence=", 0, 11)) {
                evidence = value.substring(11);
            } else if (value.equalsIgnoreCase("-last") && i + 1 < args.length) {
                try { lastMessages = Math.max(1, Integer.parseInt(args[++i])); }
                catch (NumberFormatException ignored) { text.add(value); }
            } else {
                text.add(value);
            }
        }
        return new Options(silent, evidence, lastMessages, String.join(" ", text).trim());
    }

    public record Options(boolean silent, String evidence, int lastMessages, String text) {}
}
