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
        if (s == null || s.isBlank()) return false;
        String[] parts = s.split("\\.");
        if (parts.length != 4) return false;
        try {
            for (String p : parts) {
                int n = Integer.parseInt(p);
                if (n < 0 || n > 255) return false;
            }
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public static boolean hasBypass(OfflinePlayer target, String permission, MBans plugin) {
        if (target instanceof Player p) return p.hasPermission(permission);
        return false;
    }

    public record ParsedArgs(Duration duration, String reason) {}
}
