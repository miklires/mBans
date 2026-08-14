package io.github.miklires.mbans.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.miklires.mbans.MBans;
import io.github.miklires.mbans.model.Punishment;
import io.github.miklires.mbans.model.PunishmentType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class DataTransferService {
    private static final DateTimeFormatter VANILLA_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss Z", Locale.ROOT);
    private final MBans plugin;

    public DataTransferService(MBans plugin) { this.plugin = plugin; }

    public TransferResult importVanilla(Path directory, boolean dryRun) throws Exception {
        Path backup = dryRun ? null : plugin.getDatabaseManager().createBackup();
        Path normalized = directory.toAbsolutePath().normalize();
        int read = 0, imported = 0, skipped = 0;
        for (FileType type : FileType.values()) {
            Path file = normalized.resolve(type.file);
            if (!Files.isRegularFile(file)) continue;
            JsonElement root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
            if (!root.isJsonArray()) continue;
            for (JsonElement element : root.getAsJsonArray()) {
                read++;
                Punishment punishment = vanilla(element.getAsJsonObject(), type.type);
                if (punishment == null || duplicate(punishment)) { skipped++; continue; }
                if (!dryRun) store(punishment);
                imported++;
            }
        }
        return new TransferResult(read, imported, skipped, dryRun, backup);
    }

    public TransferResult importJdbc(String profile, String url, String user, String password, boolean dryRun) throws Exception {
        Path backup = dryRun ? null : plugin.getDatabaseManager().createBackup();
        int read = 0, imported = 0, skipped = 0;
        try (Connection connection = DriverManager.getConnection(url, user, password)) {
            for (String table : candidateTables(connection.getMetaData(), profile)) {
                try (Statement statement = connection.createStatement(); ResultSet rs = statement.executeQuery("SELECT * FROM " + table)) {
                    Map<String, Integer> columns = columns(rs.getMetaData());
                    while (rs.next()) {
                        read++;
                        Punishment punishment = legacy(rs, columns, table);
                        if (punishment == null || duplicate(punishment)) { skipped++; continue; }
                        if (!dryRun) store(punishment);
                        imported++;
                    }
                }
            }
        }
        return new TransferResult(read, imported, skipped, dryRun, backup);
    }

    public Path exportHistory(String player, String format) throws Exception {
        List<Punishment> entries = plugin.getPunishmentRepository().getHistory(player, 100_000, 0);
        Path directory = plugin.getDataFolder().toPath().resolve("exports");
        Files.createDirectories(directory);
        String safe = player.replaceAll("[^A-Za-z0-9_-]", "_");
        Path file = directory.resolve(safe + "-history." + format.toLowerCase(Locale.ROOT));
        String output = format.equalsIgnoreCase("csv") ? csv(entries) : json(entries);
        Files.writeString(file, output, StandardCharsets.UTF_8);
        return file;
    }

    private List<String> candidateTables(DatabaseMetaData meta, String profile) throws Exception {
        String hint = profile.toLowerCase(Locale.ROOT).replace("bans", "").replace("ban", "");
        List<String> tables = new ArrayList<>();
        try (ResultSet rs = meta.getTables(null, null, "%", new String[]{"TABLE"})) {
            while (rs.next()) {
                String table = rs.getString("TABLE_NAME");
                String lower = table.toLowerCase(Locale.ROOT);
                if (lower.startsWith("mbans_")) continue;
                boolean punishment = lower.contains("punish") || lower.contains("ban") || lower.contains("mute")
                        || lower.contains("warn") || lower.contains("kick");
                boolean matches = hint.isBlank() || lower.contains(hint) || profile.equalsIgnoreCase("advancedban")
                        && lower.equals("punishments") || profile.equalsIgnoreCase("banmanager") && lower.startsWith("bm_");
                if (punishment && matches) tables.add(quoteIdentifier(meta, table));
            }
        }
        return tables;
    }

    private String quoteIdentifier(DatabaseMetaData meta, String identifier) throws Exception {
        String quote = meta.getIdentifierQuoteString().trim();
        if (quote.isEmpty()) return identifier;
        return quote + identifier.replace(quote, quote + quote) + quote;
    }

    private Map<String, Integer> columns(ResultSetMetaData meta) throws Exception {
        Map<String, Integer> values = new HashMap<>();
        for (int i = 1; i <= meta.getColumnCount(); i++) values.put(meta.getColumnLabel(i).toLowerCase(Locale.ROOT), i);
        return values;
    }

    private Punishment legacy(ResultSet rs, Map<String, Integer> c, String table) throws Exception {
        String rawType = text(rs, c, "type", "punishmenttype", "punishment_type");
        PunishmentType type = detectType(rawType == null ? table : rawType);
        if (type == null) return null;
        String name = text(rs, c, "name", "player", "player_name", "target_name", "victim");
        String ip = text(rs, c, "ip", "address", "target_ip");
        UUID uuid = uuid(text(rs, c, "uuid", "player_uuid", "target_uuid"));
        if (type != PunishmentType.IP_BAN && uuid == null && name == null) return null;
        Punishment p = base(type, uuid, name == null ? (ip == null ? "unknown" : ip) : name, ip,
                text(rs, c, "reason", "message"), text(rs, c, "operator", "actor", "staff", "banned_by_name", "issuer"));
        long issued = number(rs, c, "start", "time", "created", "created_at", "issued_at", "date");
        long expires = number(rs, c, "end", "until", "expires", "expires_at");
        if (issued > 0) p.setIssuedAt(epoch(issued));
        if (expires > 0 && expires != Long.MAX_VALUE) p.setExpiresAt(epoch(expires));
        String active = text(rs, c, "active");
        String removed = text(rs, c, "removed", "revoked");
        p.setActive(active == null ? removed == null || !truthy(removed) : truthy(active));
        return p;
    }

    private Punishment vanilla(JsonObject row, PunishmentType type) {
        String name = string(row, "name");
        String ip = type == PunishmentType.IP_BAN ? name : null;
        Punishment p = base(type, uuid(string(row, "uuid")), name, ip, string(row, "reason"), string(row, "source"));
        try { p.setIssuedAt(ZonedDateTime.parse(string(row, "created"), VANILLA_DATE).toInstant()); }
        catch (Exception ignored) { }
        String expires = string(row, "expires");
        if (expires != null && !expires.equalsIgnoreCase("forever")) {
            try { p.setExpiresAt(ZonedDateTime.parse(expires, VANILLA_DATE).toInstant()); }
            catch (Exception ignored) { }
        }
        return p;
    }

    private Punishment base(PunishmentType type, UUID uuid, String name, String ip, String reason, String issuer) {
        Punishment p = new Punishment();
        p.setType(type); p.setTargetUuid(uuid); p.setTargetName(name); p.setTargetIp(ip);
        p.setReason(reason == null ? "Imported punishment" : reason);
        p.setIssuedByName(issuer == null ? "IMPORT" : issuer); p.setIssuedAt(Instant.now()); p.setActive(true);
        p.setServerName("import"); return p;
    }

    private boolean duplicate(Punishment p) throws Exception {
        return plugin.getPunishmentRepository().existsEquivalent(p.getType(), p.getTargetUuid(), p.getTargetName(),
                p.getIssuedAt(), p.getReason());
    }

    private void store(Punishment p) throws Exception {
        plugin.getPunishmentRepository().insert(p);
        plugin.getNetworkLogRepository().append(p.getId(), "IMPORT", plugin.getConfigManager().getNetworkServerName());
    }

    private PunishmentType detectType(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.contains("ip") && lower.contains("ban")) return PunishmentType.IP_BAN;
        if (lower.contains("ban")) return PunishmentType.BAN;
        if (lower.contains("mute")) return PunishmentType.MUTE;
        if (lower.contains("warn")) return PunishmentType.WARN;
        if (lower.contains("kick")) return PunishmentType.KICK;
        return null;
    }

    private String text(ResultSet rs, Map<String, Integer> columns, String... names) throws Exception {
        for (String name : names) if (columns.containsKey(name)) return rs.getString(columns.get(name));
        return null;
    }
    private long number(ResultSet rs, Map<String, Integer> columns, String... names) throws Exception {
        String value = text(rs, columns, names);
        if (value == null) return 0;
        try { return Long.parseLong(value); } catch (NumberFormatException ignored) { return 0; }
    }
    private Instant epoch(long value) { return Instant.ofEpochSecond(value > 100_000_000_000L ? value / 1000 : value); }
    private boolean truthy(String value) { return value.equalsIgnoreCase("true") || value.equals("1"); }
    private UUID uuid(String raw) {
        if (raw == null) return null;
        try {
            String value = raw.replace("-", "");
            if (value.length() != 32) return null;
            return UUID.fromString(value.substring(0, 8) + "-" + value.substring(8, 12) + "-" + value.substring(12, 16)
                    + "-" + value.substring(16, 20) + "-" + value.substring(20));
        } catch (IllegalArgumentException e) { return null; }
    }
    private String string(JsonObject row, String key) {
        JsonElement value = row.get(key); return value == null || value.isJsonNull() ? null : value.getAsString();
    }

    private String json(List<Punishment> entries) {
        JsonArray array = new JsonArray();
        for (Punishment p : entries) {
            JsonObject row = new JsonObject();
            row.addProperty("id", p.getId()); row.addProperty("type", p.getType().name());
            row.addProperty("player", p.getTargetName()); row.addProperty("reason", p.getReason());
            row.addProperty("issuer", p.getIssuedByName()); row.addProperty("issuedAt", p.getIssuedAt().toString());
            if (p.getExpiresAt() != null) row.addProperty("expiresAt", p.getExpiresAt().toString());
            row.addProperty("active", p.isActive()); row.addProperty("appealId", p.getAppealId());
            row.addProperty("evidence", p.getEvidence()); array.add(row);
        }
        return array.toString();
    }

    private String csv(List<Punishment> entries) {
        StringBuilder out = new StringBuilder("id,type,player,reason,issuer,issued_at,expires_at,active,appeal_id,evidence\n");
        for (Punishment p : entries) out.append(p.getId()).append(',').append(csv(p.getType().name())).append(',')
                .append(csv(p.getTargetName())).append(',').append(csv(p.getReason())).append(',').append(csv(p.getIssuedByName()))
                .append(',').append(csv(p.getIssuedAt().toString())).append(',').append(csv(p.getExpiresAt() == null ? "" : p.getExpiresAt().toString()))
                .append(',').append(p.isActive()).append(',').append(csv(p.getAppealId())).append(',').append(csv(p.getEvidence())).append('\n');
        return out.toString();
    }
    private String csv(String value) { return "\"" + (value == null ? "" : value.replace("\"", "\"\"")) + "\""; }

    private enum FileType { PLAYERS("banned-players.json", PunishmentType.BAN), IPS("banned-ips.json", PunishmentType.IP_BAN);
        final String file; final PunishmentType type; FileType(String file, PunishmentType type) { this.file = file; this.type = type; } }
    public record TransferResult(int read, int imported, int skipped, boolean dryRun, Path backup) {}
}
