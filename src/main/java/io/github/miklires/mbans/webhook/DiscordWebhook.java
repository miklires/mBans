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
        String url = plugin.getConfigManager().getWebhookUrl(p.getType());
        if (url == null || url.isBlank()) return;

        plugin.getScheduler().async(() -> postEmbed(url, buildEmbed(p, false)));
    }

    public void sendRevocation(Punishment p, String revokedBy) {
        if (!plugin.getConfigManager().isDiscordEnabled()) return;
        String url = plugin.getConfigManager().getWebhookUrl(p.getType());
        if (url == null || url.isBlank()) return;

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
            case BAN -> "Ban";
            case IP_BAN -> "IP ban";
            case MUTE -> "Mute";
            case KICK -> "Kick";
            case WARN -> "Warning";
        };

        String duration = p.isPermanent() ? "permanent"
                : DurationParser.format(Duration.between(p.getIssuedAt(), p.getExpiresAt()));

        StringBuilder fields = new StringBuilder();
        fields.append(fieldJson("Player", escape(p.getTargetName() != null ? p.getTargetName() : "Unknown"), true));
        fields.append(",");
        if (p.getType() != PunishmentType.KICK && p.getType() != PunishmentType.WARN) {
            fields.append(fieldJson("Duration", escape(duration), true)).append(",");
        }
        if (plugin.getConfigManager().isShowIssuer()) {
            fields.append(fieldJson("Issued by", escape(p.getIssuedByName()), true)).append(",");
        }
        if (plugin.getConfigManager().isShowIp() && p.getTargetIp() != null) {
            fields.append(fieldJson("IP", escape(p.getTargetIp()), true)).append(",");
        }
        fields.append(fieldJson("Reason", escape(p.getReason() != null ? p.getReason() : "Not specified"), false));
        if (p.getEvidence() != null && !p.getEvidence().isBlank()) {
            fields.append(",").append(fieldJson("Evidence", escape(p.getEvidence()), false));
        }

        String appealUrl = plugin.getConfigManager().getDiscordAppealUrl();
        if (appealUrl != null) appealUrl = appealUrl.replace("<appeal_id>", p.getAppealId() == null ? "" : p.getAppealId());
        String components = appealUrl == null || appealUrl.isBlank() ? ""
                : ",\"components\":[{\"type\":1,\"components\":[{\"type\":2,\"style\":5,\"label\":\"Appeal\",\"url\":\""
                    + escape(appealUrl) + "\"}]}]";

        return "{"
                + "\"username\":\"" + escape(plugin.getConfigManager().getWebhookBotName()) + "\","
                + (plugin.getConfigManager().getWebhookBotAvatar().isBlank() ? ""
                    : "\"avatar_url\":\"" + escape(plugin.getConfigManager().getWebhookBotAvatar()) + "\",")
                + "\"embeds\":[{"
                + "\"title\":\"" + escape(title) + "\","
                + "\"color\":" + color + ","
                + "\"fields\":[" + fields + "],"
                + "\"timestamp\":\"" + java.time.format.DateTimeFormatter.ISO_INSTANT.format(p.getIssuedAt()) + "\""
                + "}]" + components + "}";
    }

    private String buildRevocationEmbed(Punishment p, String revokedBy) {
        String action = switch (p.getType()) {
            case BAN -> "Ban removed";
            case IP_BAN -> "IP ban removed";
            case MUTE -> "Mute removed";
            default -> "Punishment removed";
        };

        String fields = fieldJson("Player", escape(p.getTargetName() != null ? p.getTargetName() : "Unknown"), true)
                + "," + fieldJson("Removed by", escape(revokedBy), true)
                + "," + fieldJson("Original reason", escape(p.getReason() != null ? p.getReason() : "Not specified"), false);

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
