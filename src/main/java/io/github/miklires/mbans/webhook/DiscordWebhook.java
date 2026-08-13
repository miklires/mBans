package io.github.miklires.mbans.webhook;

import io.github.miklires.mbans.MBans;
import io.github.miklires.mbans.model.Punishment;
import io.github.miklires.mbans.model.PunishmentType;
import io.github.miklires.mbans.service.DurationParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class DiscordWebhook {

    private final MBans plugin;
    private final HttpClient client;

    public DiscordWebhook(MBans plugin) {
        this.plugin = plugin;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public void sendPunishment(Punishment p) {
        if (!plugin.getConfigManager().isDiscordEnabled()) return;
        String url = plugin.getConfigManager().getWebhookUrl();
        if (url == null || url.isBlank() || url.contains("REPLACE_ME")) return;

        plugin.getScheduler().async(() -> postEmbed(url, buildEmbed(p, false)));
    }

    public void sendRevocation(Punishment p, String revokedBy) {
        if (!plugin.getConfigManager().isDiscordEnabled()) return;
        String url = plugin.getConfigManager().getWebhookUrl();
        if (url == null || url.isBlank() || url.contains("REPLACE_ME")) return;

        plugin.getScheduler().async(() -> postEmbed(url, buildRevocationEmbed(p, revokedBy)));
    }

    private String buildEmbed(Punishment p, boolean revocation) {
        int color = switch (p.getType()) {
            case BAN, IP_BAN -> 0xE74C3C;
            case MUTE -> 0xF1C40F;
            case KICK -> 0x95A5A6;
            case WARN -> 0x3498DB;
        };
        String title = switch (p.getType()) {
            case BAN -> "🔨 Бан";
            case IP_BAN -> "🚫 IP-бан";
            case MUTE -> "🔇 Мут";
            case KICK -> "👢 Кик";
            case WARN -> "⚠️ Варн";
        };

        String duration = p.isPermanent() ? "навсегда"
                : DurationParser.format(Duration.between(p.getIssuedAt(), p.getExpiresAt()));

        StringBuilder fields = new StringBuilder();
        fields.append(fieldJson("Игрок", escape(p.getTargetName() != null ? p.getTargetName() : "—"), true));
        fields.append(",");
        if (p.getType() != PunishmentType.KICK && p.getType() != PunishmentType.WARN) {
            fields.append(fieldJson("Длительность", escape(duration), true)).append(",");
        }
        if (plugin.getConfigManager().isShowIssuer()) {
            fields.append(fieldJson("Выдал", escape(p.getIssuedByName()), true)).append(",");
        }
        if (plugin.getConfigManager().isShowIp() && p.getTargetIp() != null) {
            fields.append(fieldJson("IP", escape(p.getTargetIp()), true)).append(",");
        }
        fields.append(fieldJson("Причина", escape(p.getReason() != null ? p.getReason() : "—"), false));

        return "{"
                + "\"username\":\"" + escape(plugin.getConfigManager().getWebhookBotName()) + "\","
                + (plugin.getConfigManager().getWebhookBotAvatar().isBlank() ? ""
                    : "\"avatar_url\":\"" + escape(plugin.getConfigManager().getWebhookBotAvatar()) + "\",")
                + "\"embeds\":[{"
                + "\"title\":\"" + escape(title) + "\","
                + "\"color\":" + color + ","
                + "\"fields\":[" + fields + "],"
                + "\"timestamp\":\"" + java.time.format.DateTimeFormatter.ISO_INSTANT.format(p.getIssuedAt()) + "\""
                + "}]}";
    }

    private String buildRevocationEmbed(Punishment p, String revokedBy) {
        String action = switch (p.getType()) {
            case BAN -> "✅ Разбан";
            case IP_BAN -> "✅ Снятие IP-бана";
            case MUTE -> "✅ Размут";
            default -> "✅ Снятие наказания";
        };

        String fields = fieldJson("Игрок", escape(p.getTargetName() != null ? p.getTargetName() : "—"), true)
                + "," + fieldJson("Снял", escape(revokedBy), true)
                + "," + fieldJson("Изначальная причина", escape(p.getReason() != null ? p.getReason() : "—"), false);

        return "{"
                + "\"username\":\"" + escape(plugin.getConfigManager().getWebhookBotName()) + "\","
                + "\"embeds\":[{"
                + "\"title\":\"" + escape(action) + "\","
                + "\"color\":3066993,"
                + "\"fields\":[" + fields + "]"
                + "}]}";
    }

    private String fieldJson(String name, String value, boolean inline) {
        return "{\"name\":\"" + name + "\",\"value\":\"" + value + "\",\"inline\":" + inline + "}";
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
    }

    private void postEmbed(String url, String json) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "mBans-plugin")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            int code = resp.statusCode();
            if (code >= 400) {
                plugin.getLogger().warning("Discord webhook returned HTTP " + code);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Discord webhook failed: " + e.getMessage());
        }
    }
}
