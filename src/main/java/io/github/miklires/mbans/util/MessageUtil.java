package io.github.miklires.mbans.util;

import io.github.miklires.mbans.MBans;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class MessageUtil {

    private final MBans plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private FileConfiguration messages;
    private String prefix;

    public MessageUtil(MBans plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        String locale = plugin.getConfigManager().getLanguage();
        String resourcePath = "lang/" + locale + ".yml";
        if (plugin.getResource(resourcePath) == null) {
            plugin.getLogger().warning("Unknown locale " + locale + ", using en_US");
            resourcePath = "lang/en_US.yml";
        }

        File file = new File(plugin.getDataFolder(), resourcePath);
        if (!file.exists()) plugin.saveResource(resourcePath, false);
        messages = YamlConfiguration.loadConfiguration(file);
        try (InputStream stream = plugin.getResource(resourcePath)) {
            if (stream != null) {
                messages.setDefaults(YamlConfiguration.loadConfiguration(
                        new InputStreamReader(stream, StandardCharsets.UTF_8)));
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Could not load message defaults: " + e.getMessage());
        }
        prefix = messages.getString("prefix", "");
    }

    public Component get(String path, TagResolver... resolvers) {
        return mm.deserialize(prefix + messages.getString(path, "<red>Missing message: " + path), resolvers);
    }

    public Component getPlain(String path, TagResolver... resolvers) {
        return mm.deserialize(messages.getString(path, "<red>Missing message: " + path), resolvers);
    }

    public Component parse(String raw, TagResolver... resolvers) {
        return mm.deserialize(raw, resolvers);
    }

    public void send(Audience target, String path, TagResolver... resolvers) {
        Component message = get(path, resolvers);
        if (target instanceof Player player) {
            plugin.getScheduler().entity(player, () -> player.sendMessage(message));
        } else {
            plugin.getScheduler().global(() -> target.sendMessage(message));
        }
    }

    public String getRawString(String path) {
        return messages.getString(path, "Missing message: " + path);
    }

    public static TagResolver ph(String key, String value) {
        return Placeholder.unparsed(key, value == null ? "" : value);
    }

    public static TagResolver ph(String key, int value) {
        return Placeholder.unparsed(key, String.valueOf(value));
    }
}
