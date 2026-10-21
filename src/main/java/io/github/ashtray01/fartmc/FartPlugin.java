package io.github.ashtray01.fartmc;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import static io.github.ashtray01.fartmc.FartLogic.Style;

public final class FartPlugin extends JavaPlugin implements Listener {
    private static final Particle.DustTransition GREEN_CLOUD = new Particle.DustTransition(
            Color.fromRGB(90, 175, 35), Color.fromRGB(175, 210, 100), 1.6f);
    private final FartLogic logic = new FartLogic();
    private final Random random = new Random();
    private final Map<UUID, Long> sneaks = new HashMap<>();
    private final Map<UUID, Location> positions = new HashMap<>();
    private final Map<UUID, Integer> idle = new HashMap<>();
    private Set<Pair> close = new HashSet<>();
    private NamespacedKey enabledKey, hotkeysKey, autoKey;
    private PackServer pack;
    private FoodItems foodItems;
    private FoodEffects foodEffects;
    private record Pair(UUID a, UUID b) {
        static Pair of(UUID a, UUID b) { return a.compareTo(b) < 0 ? new Pair(a,b) : new Pair(b,a); }
    }

    @Override public void onEnable() {
        saveDefaultConfig();
        enabledKey = new NamespacedKey(this, "enabled");
        hotkeysKey = new NamespacedKey(this, "hotkeys");
        autoKey = new NamespacedKey(this, "automatic");
        getServer().getPluginManager().registerEvents(this, this);
        pack = new PackServer(this);
        pack.start();
        foodItems = new FoodItems(this);
        foodItems.register();
        foodEffects = new FoodEffects(this);
        getServer().getPluginManager().registerEvents(foodItems, this);
        getServer().getPluginManager().registerEvents(foodEffects, this);
        FoodCommand foodCommand = new FoodCommand(this, foodItems, foodEffects);
        Objects.requireNonNull(getCommand("fartmc")).setExecutor(foodCommand);
        Objects.requireNonNull(getCommand("fartmc")).setTabCompleter(foodCommand);
        getServer().getScheduler().runTaskTimer(this, this::ambient, 40, 40);
        getLogger().info("FartPlugin " + getPluginMeta().getVersion() + " ready; 7 sound styles; no client mod required.");
    }
    @Override public void onDisable() {
        if (foodEffects != null) foodEffects.close();
        if (foodItems != null) foodItems.unregister();
        if (pack != null) pack.close();
    }

    void reloadFoodConfiguration() {
        foodEffects.stopAll();
        reloadConfig();
        foodItems.register();
        foodEffects.reload();
    }
    boolean foodParticipating(Player p) { return setting(p, enabledKey); }
    boolean foodViewer(Player source, Player viewer) {
        double radius = bounded("sound-radius", 15, 64);
        return viewer.getWorld().equals(source.getWorld()) && setting(viewer, enabledKey) && viewer.canSee(source)
                && (viewer.equals(source) || viewer.getLocation().distanceSquared(source.getLocation()) <= radius * radius);
    }
    void foodSound(Player source, Style style) {
        long seed = random.nextLong();
        float pitch = .88f + random.nextFloat() * .24f;
        float volume = .5f + random.nextFloat() * .2f;
        for (Player listener : source.getWorld().getPlayers()) if (foodViewer(source, listener)) {
            if (pack.loaded(listener)) listener.playSound(source, "fartmc:" + style.name().toLowerCase(Locale.ROOT),
                    SoundCategory.PLAYERS, volume, pitch, seed);
            else listener.playSound(source, style.sound, SoundCategory.PLAYERS, style.volume * volume,
                    Math.clamp(style.pitch * pitch, .5f, 2f));
        }
    }
    void foodSound(Player source, String sound, float volume, float pitch) {
        for (Player listener : source.getWorld().getPlayers()) if (foodViewer(source, listener))
            listener.playSound(source, sound, SoundCategory.PLAYERS, volume, pitch);
    }

    private boolean setting(Player p, NamespacedKey key) {
        return p.getPersistentDataContainer().getOrDefault(key, PersistentDataType.BYTE, (byte)1) != 0;
    }
    private boolean eligible(Player p) {
        return p.isOnline() && !p.isDead() && p.getGameMode() != GameMode.SPECTATOR
                && p.hasPermission("fartplugin.use") && setting(p, enabledKey);
    }
    private boolean automatic(Player p) {
        return eligible(p) && setting(p, autoKey)
                && !getConfig().getBoolean("command-only", false)
                && getConfig().getBoolean("automatic.enabled", true);
    }
    private boolean hotkey(Player p, String key) {
        return eligible(p) && setting(p, hotkeysKey) && !getConfig().getBoolean("command-only", false)
                && getConfig().getBoolean("hotkeys." + key, true);
    }
    private double bounded(String key, double fallback, double max) {
        double value = getConfig().getDouble(key, fallback);
        return Double.isFinite(value) ? Math.max(0, Math.min(max, value)) : fallback;
    }
    private boolean chance(String key, double fallback) { return random.nextDouble() < bounded(key, fallback, 1); }
    private boolean fart(Player p, Style requested, boolean auto) {
        if (foodEffects != null && foodEffects.active(p)) return false;
        if (!(auto ? automatic(p) : eligible(p))) return false;
        long now = System.nanoTime() / 1_000_000;
        if (!logic.acquire(p.getUniqueId(), now, auto,
                (long)(bounded("cooldown-seconds", 1, 3600) * 1000),
                (long)(bounded("automatic-cooldown-seconds", 35, 3600) * 1000))) return false;
        Style style = requested != null ? requested : p.isInWater() ? Style.BUBBLES : logic.choose(p.getUniqueId(), random);
        World world = p.getWorld();
        Location cloud = p.getLocation();
        // Yaw alone keeps the cloud behind the feet even when looking straight up/down.
        double yaw = Math.toRadians(cloud.getYaw());
        cloud.add(Math.sin(yaw) * .5, Math.min(.55, p.getHeight() * .4), -Math.cos(yaw) * .5);
        long soundSeed = random.nextLong(); // All listeners select the same resource-pack variant.
        float pitch = Math.clamp(style.pitch + (random.nextFloat() - .5f) * .15f, .5f, 2f);
        for (int i = 0; i < style.pulses; i++) {
            final int pulse = i;
            getServer().getScheduler().runTaskLater(this, () -> {
                if (!eligible(p) || !p.getWorld().equals(world) || (auto && !automatic(p))) return;
                Location origin = p.getLocation().add(0, .65, 0);
                double radius = bounded("sound-radius", 15, 64);
                if (style == Style.WHISPER) radius = Math.min(radius, 4);
                for (Player listener : origin.getWorld().getPlayers()) {
                    if (!setting(listener, enabledKey) || !listener.canSee(p)) continue;
                    if (!listener.equals(p) && (radius == 0 || listener.getLocation().distanceSquared(origin) > radius * radius)) continue;
                    if (pack.loaded(listener)) {
                        // Entity-bound playback follows the player for the entire recording.
                        if (pulse == 0) listener.playSound(p, "fartmc:" + style.name().toLowerCase(Locale.ROOT),
                                SoundCategory.PLAYERS, style == Style.WHISPER ? .16f : .65f, 1f, soundSeed);
                    } else {
                        listener.playSound(p, style.sound, SoundCategory.PLAYERS, style.volume,
                                Math.clamp(pitch - pulse * .025f, .5f, 2f));
                    }
                    if (pulse == 0 && getConfig().getBoolean("particles.enabled", true)) {
                        listener.spawnParticle(Particle.DUST_COLOR_TRANSITION, cloud,
                                style == Style.WHISPER ? 10 : 24, .18, .12, .18, .015, GREEN_CLOUD);
                        if (style == Style.BUBBLES) {
                            listener.spawnParticle(Particle.BUBBLE_POP, cloud, 6, .18, .12, .18, .015);
                        }
                    }
                }
            }, i * 3L);
        }
        return true;
    }

    @EventHandler(ignoreCancelled = true)
    public void swap(PlayerSwapHandItemsEvent e) {
        if (e.getPlayer().isSneaking() && hotkey(e.getPlayer(), "sneak-swap")) {
            e.setCancelled(true); // Reserve the gesture even during cooldown; never swap/drop items.
            fart(e.getPlayer(), null, false);
        }
    }
    @EventHandler(ignoreCancelled = true)
    public void swing(PlayerAnimationEvent e) {
        // Empty-hand right-click air sends no packet. Arm swing is observable on vanilla.
        Player p = e.getPlayer();
        if (e.getAnimationType() == PlayerAnimationType.ARM_SWING
                && p.getInventory().getItemInMainHand().getType().isAir()
                && p.getInventory().getItemInOffHand().getType().isAir()
                && p.isSneaking() && hotkey(p, "sneak-left-click")
                && p.rayTraceBlocks(6) == null
                && p.getWorld().rayTraceEntities(p.getEyeLocation(), p.getEyeLocation().getDirection(),
                    6, entity -> !entity.equals(p)) == null) {
            fart(p, Style.WHISPER, false);
        }
    }
    @EventHandler(ignoreCancelled = true)
    public void sneak(PlayerToggleSneakEvent e) {
        if (!e.isSneaking() || !hotkey(e.getPlayer(), "double-sneak")) return;
        long now = System.nanoTime() / 1_000_000;
        Long previous = sneaks.put(e.getPlayer().getUniqueId(), now);
        if (previous != null && now - previous <= bounded("hotkeys.double-sneak-milliseconds", 350, 1000)) {
            sneaks.remove(e.getPlayer().getUniqueId());
            fart(e.getPlayer(), Style.RATTLE, false);
        }
    }
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void eat(PlayerItemConsumeEvent e) {
        Player p = e.getPlayer();
        if (FoodItems.marked(e.getItem())) return;
        if (!e.getItem().getType().isEdible() || !automatic(p) || !chance("automatic.food-chance", .35)) return;
        getServer().getScheduler().runTaskLater(this, () -> fart(p, null, true), 60 + random.nextInt(100));
    }
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void land(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && e.getCause() == EntityDamageEvent.DamageCause.FALL
                && e.getFinalDamage() > 0 && automatic(p) && chance("automatic.landing-chance", .12)) {
            getServer().getScheduler().runTask(this, () -> fart(p, Style.THUNDER, true));
        }
    }
    private void ambient() {
        Set<Pair> next = new HashSet<>();
        for (Player p : getServer().getOnlinePlayers()) {
            UUID id = p.getUniqueId();
            if (!automatic(p)) { positions.remove(id); idle.remove(id); continue; }
            Location location = p.getLocation();
            Location old = positions.put(id, location);
            int still = old != null && old.getWorld().equals(location.getWorld())
                    && old.distanceSquared(location) < .04 ? idle.getOrDefault(id, 0) + 1 : 0;
            idle.put(id, still);
            if (still >= 15 && chance("automatic.idle-chance", .015)) fart(p, Style.SQUEAK, true);
            double radius = bounded("automatic.proximity-radius", 2.3, 8);
            if (radius == 0) continue;
            for (Player other : p.getWorld().getNearbyPlayers(location, radius)) {
                if (p.equals(other) || !automatic(other) || !p.canSee(other) || !other.canSee(p)) continue;
                if (other.getLocation().distanceSquared(location) > radius * radius) continue;
                Pair pair = Pair.of(id, other.getUniqueId());
                if (next.add(pair) && !close.contains(pair) && chance("automatic.proximity-chance", .18)) {
                    fart(random.nextBoolean() ? p : other, Style.WHISPER, true);
                }
            }
        }
        close = next;
    }
    @EventHandler public void quit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        logic.forget(id); sneaks.remove(id); positions.remove(id); idle.remove(id);
        close.removeIf(pair -> pair.a.equals(id) || pair.b.equals(id));
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("fartreload")) {
            if (!sender.hasPermission("fartplugin.reload")) { sender.sendMessage("Нет права fartplugin.reload."); return true; }
            reloadFoodConfiguration(); sender.sendMessage("FartPlugin: конфигурация обновлена. Изменения ресурспака — после рестарта."); return true;
        }
        if (!sender.hasPermission("fartplugin.use")) return true;
        String arg = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "";
        if (arg.equals("help")) {
            sender.sendMessage("/fart [classic|whisper|bubbles|trumpet|rattle|thunder|squeak]");
            sender.sendMessage("/fart toggle — участие/звук; hotkeys — комбинации; auto — случайные события.");
            sender.sendMessage("Shift+F — случайный; двойной Shift — треск; Shift+ЛКМ в воздух пустыми руками — шептун.");
            return true;
        }
        if (Set.of("toggle", "hotkeys", "auto").contains(arg)) {
            if (!(sender instanceof Player p)) { sender.sendMessage("Только для игроков."); return true; }
            NamespacedKey key = arg.equals("toggle") ? enabledKey : arg.equals("auto") ? autoKey : hotkeysKey;
            boolean value = !setting(p, key);
            p.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte)(value ? 1 : 0));
            sender.sendMessage("FartPlugin " + arg + ": " + (value ? "включено" : "выключено")); return true;
        }
        Style style = Style.parse(arg);
        Player target = sender instanceof Player p ? p : null;
        if (!arg.isEmpty() && style == null) {
            if (!sender.hasPermission("fartplugin.others")) { sender.sendMessage("Неизвестный стиль. /fart help"); return true; }
            target = getServer().getPlayerExact(args[0]);
            if (args.length > 1) {
                style = Style.parse(args[1]);
                if (style == null) { sender.sendMessage("Неизвестный стиль. /fart help"); return true; }
            }
        }
        if (target == null) { sender.sendMessage("Укажите имя игрока онлайн: /fart <player> [style]"); return true; }
        if (!eligible(target)) { sender.sendMessage("Участие игрока выключено или сейчас недоступно."); return true; }
        if (!fart(target, style, false)) sender.sendMessage("пук еще не накопился...");
        return true;
    }
    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!command.getName().equalsIgnoreCase("fart") || args.length != 1) return List.of();
        return List.of("classic","whisper","bubbles","trumpet","rattle","thunder","squeak","toggle","hotkeys","auto","help")
                .stream().filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
    }
}
