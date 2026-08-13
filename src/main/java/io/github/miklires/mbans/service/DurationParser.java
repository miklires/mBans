package io.github.miklires.mbans.service;

import java.time.Duration;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DurationParser {

    private static final Pattern TOKEN = Pattern.compile("(\\d+)(mo|s|m|h|d|w|y)");

    public static Optional<Duration> parse(String input) {
        if (input == null || input.isBlank()) return Optional.empty();
        String s = input.toLowerCase().replaceAll("\\s+", "");
        Matcher m = TOKEN.matcher(s);

        long totalSeconds = 0;
        int matchedLength = 0;
        boolean matched = false;

        while (m.find()) {
            matched = true;
            long value;
            try {
                value = Long.parseLong(m.group(1));
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
            String unit = m.group(2);
            long multiplier = switch (unit) {
                case "s" -> 1;
                case "m" -> 60;
                case "h" -> 3600;
                case "d" -> 86400;
                case "w" -> 604800;
                case "mo" -> 2592000;
                case "y" -> 31536000;
                default -> 0;
            };
            try {
                totalSeconds = Math.addExact(totalSeconds, Math.multiplyExact(value, multiplier));
            } catch (ArithmeticException e) {
                return Optional.empty();
            }
            matchedLength += m.group(0).length();
        }

        if (!matched || matchedLength != s.length() || totalSeconds <= 0) {
            return Optional.empty();
        }
        return Optional.of(Duration.ofSeconds(totalSeconds));
    }

    public static String format(Duration duration) {
        if (duration == null) return "permanent";
        long seconds = duration.getSeconds();
        if (seconds < 60) return seconds + "s";

        long years = seconds / 31536000; seconds %= 31536000;
        long months = seconds / 2592000; seconds %= 2592000;
        long weeks = seconds / 604800; seconds %= 604800;
        long days = seconds / 86400; seconds %= 86400;
        long hours = seconds / 3600; seconds %= 3600;
        long minutes = seconds / 60;

        StringBuilder sb = new StringBuilder();
        if (years > 0) sb.append(years).append("y ");
        if (months > 0) sb.append(months).append("mo ");
        if (weeks > 0) sb.append(weeks).append("w ");
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0) sb.append(hours).append("h ");
        if (minutes > 0) sb.append(minutes).append("m ");
        return sb.toString().trim();
    }

    public static String formatExpiresAt(java.time.Instant expiresAt) {
        if (expiresAt == null) return "never";
        Duration remaining = Duration.between(java.time.Instant.now(), expiresAt);
        if (remaining.isNegative() || remaining.isZero()) return "expired";
        return format(remaining);
    }
}
