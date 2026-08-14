package io.github.miklires.mbans.service;

import io.github.miklires.mbans.MBans;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ChatEvidenceService implements Listener {
    private final MBans plugin;
    private final Map<UUID, Deque<Line>> lines = new ConcurrentHashMap<>();

    public ChatEvidenceService(MBans plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (!plugin.getConfigManager().isChatEvidenceEnabled()) return;
        String text = PlainTextComponentSerializer.plainText().serialize(event.message());
        Deque<Line> queue = lines.computeIfAbsent(event.getPlayer().getUniqueId(), ignored -> new ArrayDeque<>());
        synchronized (queue) {
            queue.addLast(new Line(Instant.now(), text));
            int maximum = plugin.getConfigManager().getChatEvidenceBufferSize();
            while (queue.size() > maximum) queue.removeFirst();
        }
    }

    public String snapshot(UUID uuid, int requested) {
        Deque<Line> queue = lines.get(uuid);
        if (queue == null) return null;
        int count = Math.max(1, Math.min(requested, plugin.getConfigManager().getChatEvidenceBufferSize()));
        StringBuilder result = new StringBuilder();
        synchronized (queue) {
            int skip = Math.max(0, queue.size() - count);
            int index = 0;
            for (Line line : queue) {
                if (index++ < skip) continue;
                if (!result.isEmpty()) result.append('\n');
                result.append('[').append(line.time().getEpochSecond()).append("] ").append(line.text());
            }
        }
        return result.isEmpty() ? null : result.toString();
    }

    private record Line(Instant time, String text) {}
}
