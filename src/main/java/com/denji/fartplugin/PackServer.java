package com.denji.fartplugin;

import com.sun.net.httpserver.HttpServer;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import net.kyori.adventure.text.Component;
import java.net.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;

/** Serves exactly one immutable ZIP, never a directory or arbitrary path. */
final class PackServer implements Listener, AutoCloseable {
    private final FartPlugin plugin;
    private final Set<UUID> loaded = new HashSet<>();
    private final UUID packId = UUID.nameUUIDFromBytes("fartmc:audio".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    private HttpServer server;
    private ExecutorService executor;
    private String url;
    private byte[] hash;

    PackServer(FartPlugin plugin) { this.plugin = plugin; }

    void start() {
        if (!plugin.getConfig().getBoolean("resource-pack.enabled", true)) return;
        Path file = plugin.getDataFolder().toPath().resolve("resource-pack.zip");
        if (!Files.isRegularFile(file)) {
            plugin.getLogger().info("No resource-pack.zip: using vanilla sounds.");
            return;
        }
        try {
            byte[] bytes = Files.readAllBytes(file);
            hash = MessageDigest.getInstance("SHA-1").digest(bytes);
            String host = plugin.getConfig().getString("resource-pack.bind", "127.0.0.1");
            int port = plugin.getConfig().getInt("resource-pack.port", 8123);
            url = plugin.getConfig().getString("resource-pack.url", "http://127.0.0.1:8123/fartmc.zip");
            URI uri = URI.create(url);
            if (!Set.of("http","https").contains(uri.getScheme())) throw new IllegalArgumentException("Expected http(s) pack URL");
            server = HttpServer.create(new InetSocketAddress(host, port), 8);
            executor = Executors.newFixedThreadPool(2, task -> {
                Thread thread = new Thread(task, "FartMC-pack"); thread.setDaemon(true); return thread;
            });
            server.setExecutor(executor);
            server.createContext("/fartmc.zip", exchange -> {
                try {
                    if (!exchange.getRequestURI().getPath().equals("/fartmc.zip") || !exchange.getRequestMethod().equals("GET")) {
                        exchange.sendResponseHeaders(404, -1); return;
                    }
                    exchange.getResponseHeaders().set("Content-Type", "application/zip");
                    exchange.sendResponseHeaders(200, bytes.length);
                    exchange.getResponseBody().write(bytes);
                } catch (Exception ex) {
                    plugin.getLogger().log(java.util.logging.Level.WARNING, "Resource pack HTTP request failed", ex);
                } finally { exchange.close(); }
            });
            server.start();
            plugin.getServer().getPluginManager().registerEvents(this, plugin);
            plugin.getLogger().info("Optional sound pack available at " + url);
        } catch (Exception ex) {
            close();
            plugin.getLogger().warning("Sound pack disabled; vanilla fallback: " + ex.getMessage());
        }
    }
    boolean loaded(Player player) { return loaded.contains(player.getUniqueId()); }
    @EventHandler public void join(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && server != null) {
                player.setResourcePack(packId, url, hash, Component.text("Звуки FartMC (необязательно)"), false);
            }
        }, 30);
    }
    @EventHandler public void status(PlayerResourcePackStatusEvent event) {
        if (!packId.equals(event.getID())) return;
        if (event.getStatus() == PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED) loaded.add(event.getPlayer().getUniqueId());
        else loaded.remove(event.getPlayer().getUniqueId());
    }
    @EventHandler public void quit(PlayerQuitEvent event) { loaded.remove(event.getPlayer().getUniqueId()); }
    @Override public void close() {
        if (server != null) { server.stop(0); server = null; }
        if (executor != null) executor.shutdownNow();
        loaded.clear();
        HandlerList.unregisterAll(this);
    }
}
