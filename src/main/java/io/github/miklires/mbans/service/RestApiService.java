package io.github.miklires.mbans.service;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.miklires.mbans.MBans;
import io.github.miklires.mbans.model.Punishment;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Map;
import java.time.Instant;

public final class RestApiService {
    private final MBans plugin;
    private HttpServer server;
    private ExecutorService executor;
    private final Map<String, RequestWindow> requestWindows = new ConcurrentHashMap<>();

    public RestApiService(MBans plugin) { this.plugin = plugin; }

    public void start() {
        if (!plugin.getConfigManager().isRestApiEnabled()) return;
        if (plugin.getConfigManager().getRestApiToken().isBlank()) {
            plugin.getLogger().warning("REST API is enabled but rest-api.token is empty; API was not started");
            return;
        }
        try {
            server = HttpServer.create(new InetSocketAddress(plugin.getConfigManager().getRestApiBind(),
                    plugin.getConfigManager().getRestApiPort()), 0);
            server.createContext("/v1/health", this::health);
            server.createContext("/v1/punishments", this::punishment);
            server.createContext("/v1/history", this::history);
            executor = Executors.newVirtualThreadPerTaskExecutor();
            server.setExecutor(executor);
            server.start();
            plugin.getLogger().info("REST API listening on " + plugin.getConfigManager().getRestApiBind()
                    + ":" + plugin.getConfigManager().getRestApiPort());
        } catch (IOException e) {
            plugin.getLogger().warning("REST API could not start: " + e.getMessage());
        }
    }

    public void stop() {
        if (server != null) server.stop(1);
        if (executor != null) executor.shutdownNow();
    }

    private void health(HttpExchange exchange) throws IOException {
        if (!authorized(exchange)) return;
        send(exchange, 200, "{\"status\":\"ok\",\"version\":" + quote(plugin.getPluginMeta().getVersion()) + "}");
    }

    private void punishment(HttpExchange exchange) throws IOException {
        if (!authorized(exchange)) return;
        if (!exchange.getRequestMethod().equals("GET")) { send(exchange, 405, error("method_not_allowed")); return; }
        String suffix = exchange.getRequestURI().getPath().substring("/v1/punishments".length());
        try {
            long id = Long.parseLong(suffix.replaceFirst("^/", ""));
            Punishment value = plugin.getPunishmentRepository().findById(id).orElse(null);
            send(exchange, value == null ? 404 : 200, value == null ? error("not_found") : json(value));
        } catch (NumberFormatException e) {
            send(exchange, 400, error("invalid_id"));
        } catch (SQLException e) {
            send(exchange, 500, error("storage_error"));
        }
    }

    private void history(HttpExchange exchange) throws IOException {
        if (!authorized(exchange)) return;
        String raw = exchange.getRequestURI().getRawQuery();
        String name = null;
        if (raw != null) for (String pair : raw.split("&")) {
            String[] parts = pair.split("=", 2);
            if (parts.length == 2 && parts[0].equals("player")) name = URLDecoder.decode(parts[1], StandardCharsets.UTF_8);
        }
        if (name == null || name.isBlank()) { send(exchange, 400, error("missing_player")); return; }
        try {
            List<Punishment> entries = plugin.getPunishmentRepository().getHistory(name, 100, 0);
            StringBuilder out = new StringBuilder("{\"player\":").append(quote(name)).append(",\"entries\":[");
            for (int i = 0; i < entries.size(); i++) {
                if (i > 0) out.append(',');
                out.append(json(entries.get(i)));
            }
            send(exchange, 200, out.append("]}").toString());
        } catch (SQLException e) {
            send(exchange, 500, error("storage_error"));
        }
    }

    private boolean authorized(HttpExchange exchange) throws IOException {
        if (!withinRateLimit(exchange)) return false;
        String supplied = exchange.getRequestHeaders().getFirst("Authorization");
        String expected = "Bearer " + plugin.getConfigManager().getRestApiToken();
        boolean match = supplied != null && MessageDigest.isEqual(supplied.getBytes(StandardCharsets.UTF_8),
                expected.getBytes(StandardCharsets.UTF_8));
        if (!match) send(exchange, 401, error("unauthorized"));
        return match;
    }

    private boolean withinRateLimit(HttpExchange exchange) throws IOException {
        String address = exchange.getRemoteAddress().getAddress().getHostAddress();
        long minute = Instant.now().getEpochSecond() / 60;
        RequestWindow window = requestWindows.compute(address, (ignored, current) -> {
            if (current == null || current.minute() != minute) return new RequestWindow(minute, new AtomicInteger(1));
            current.count().incrementAndGet();
            return current;
        });
        if (requestWindows.size() > 1024) requestWindows.entrySet().removeIf(entry -> entry.getValue().minute() < minute - 1);
        if (window.count().get() <= plugin.getConfigManager().getRestApiRequestsPerMinute()) return true;
        exchange.getResponseHeaders().set("Retry-After", "60");
        send(exchange, 429, error("rate_limited"));
        return false;
    }

    private String json(Punishment p) {
        return "{\"id\":" + p.getId() + ",\"type\":" + quote(p.getType().name())
                + ",\"player\":" + quote(p.getTargetName()) + ",\"reason\":" + quote(p.getReason())
                + ",\"issuer\":" + quote(p.getIssuedByName()) + ",\"issuedAt\":" + p.getIssuedAt().getEpochSecond()
                + ",\"expiresAt\":" + (p.getExpiresAt() == null ? "null" : p.getExpiresAt().getEpochSecond())
                + ",\"active\":" + p.isActive() + ",\"appealId\":" + quote(p.getAppealId())
                + ",\"evidence\":" + quote(p.getEvidence()) + "}";
    }

    private String quote(String value) {
        if (value == null) return "null";
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r") + "\"";
    }

    private String error(String value) { return "{\"error\":" + quote(value) + "}"; }

    private void send(HttpExchange exchange, int code, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(code, bytes.length);
        try (var stream = exchange.getResponseBody()) { stream.write(bytes); }
    }

    private record RequestWindow(long minute, AtomicInteger count) {}
}
