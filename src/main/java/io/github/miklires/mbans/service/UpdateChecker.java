package io.github.miklires.mbans.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import io.github.miklires.mbans.MBans;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public final class UpdateChecker {
    private final MBans plugin;

    public UpdateChecker(MBans plugin) { this.plugin = plugin; }

    public void start() {
        if (!plugin.getConfigManager().isUpdateCheckEnabled()
                || plugin.getConfigManager().getModrinthProjectId().isBlank()) return;
        plugin.getScheduler().async(this::check);
    }

    private void check() {
        try {
            String project = URLEncoder.encode(plugin.getConfigManager().getModrinthProjectId(), StandardCharsets.UTF_8);
            URI uri = URI.create("https://api.modrinth.com/v2/project/" + project
                    + "/version?loaders=%5B%22paper%22%5D&include_changelog=false");
            HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(8))
                    .header("User-Agent", "miklires/mBans/" + plugin.getPluginMeta().getVersion())
                    .header("Accept", "application/json").GET().build();
            HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                plugin.getLogger().fine("Update check returned HTTP " + response.statusCode());
                return;
            }
            JsonArray versions = JsonParser.parseString(response.body()).getAsJsonArray();
            String current = plugin.getPluginMeta().getVersion();
            String latest = current;
            for (JsonElement element : versions) {
                String candidate = element.getAsJsonObject().get("version_number").getAsString();
                if (!current.contains("-") && candidate.contains("-")) continue;
                if (compare(candidate, latest) > 0) latest = candidate;
            }
            if (!latest.equals(current)) plugin.getLogger().info("mBans " + latest + " is available on Modrinth (current: " + current + ")");
        } catch (Exception e) {
            plugin.getLogger().fine("Update check failed: " + e.getMessage());
        }
    }

    static int compare(String left, String right) {
        String cleanLeft = left.replaceFirst("^[vV]", "").split("\\+", 2)[0];
        String cleanRight = right.replaceFirst("^[vV]", "").split("\\+", 2)[0];
        String[] leftParts = cleanLeft.split("-", 2);
        String[] rightParts = cleanRight.split("-", 2);
        String[] a = leftParts[0].split("\\.");
        String[] b = rightParts[0].split("\\.");
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            String x = i < a.length ? a[i] : "0";
            String y = i < b.length ? b[i] : "0";
            try {
                int result = Integer.compare(Integer.parseInt(x), Integer.parseInt(y));
                if (result != 0) return result;
            } catch (NumberFormatException ignored) {
                int result = x.compareToIgnoreCase(y);
                if (result != 0) return result;
            }
        }
        boolean leftPrerelease = leftParts.length > 1;
        boolean rightPrerelease = rightParts.length > 1;
        if (leftPrerelease != rightPrerelease) return leftPrerelease ? -1 : 1;
        if (!leftPrerelease) return 0;
        String[] preA = leftParts[1].split("\\.");
        String[] preB = rightParts[1].split("\\.");
        for (int i = 0; i < Math.max(preA.length, preB.length); i++) {
            if (i >= preA.length) return -1;
            if (i >= preB.length) return 1;
            boolean numericA = preA[i].matches("\\d+");
            boolean numericB = preB[i].matches("\\d+");
            int result;
            if (numericA && numericB) result = Integer.compare(Integer.parseInt(preA[i]), Integer.parseInt(preB[i]));
            else if (numericA != numericB) result = numericA ? -1 : 1;
            else result = preA[i].compareToIgnoreCase(preB[i]);
            if (result != 0) return result;
        }
        return 0;
    }
}
