package io.github.ashtray01.fartmc;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.event.world.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.*;
import org.bukkit.scheduler.BukkitTask;
import java.util.*;

final class FoodEffects implements Listener, AutoCloseable {
    static final NamespacedKey COOLDOWN = new NamespacedKey("fartmc", "food_cooldown");
    static final NamespacedKey HEALTH_CREDIT = new NamespacedKey("fartmc", "health_credit");
    static final NamespacedKey ORIGINAL_HEALTH = new NamespacedKey("fartmc", "original_health");
    static final NamespacedKey LANDING = new NamespacedKey("fartmc", "landing");
    private final FartPlugin plugin;
    private final Map<UUID, FoodSession> sessions = new HashMap<>();
    private final Map<UUID, BukkitTask> pending = new HashMap<>();
    private final Map<UUID, Long> landings = new HashMap<>();
    private final Map<UUID, BukkitTask> milkTasks = new HashMap<>();
    private record Status(Player player, BossBar bar, long totalSeconds) {}
    private final Map<UUID, Status> statuses = new HashMap<>();
    final FoodVisuals visuals;
    final FoodProtection protection;
    private final BukkitTask task;
    long tick;

    FoodEffects(FartPlugin plugin) {
        this.plugin = plugin; visuals = new FoodVisuals(plugin); protection = new FoodProtection(plugin);
        protection.reload();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::update, 1, 1);
        Bukkit.getOnlinePlayers().forEach(this::recover);
    }
    boolean active(Player p) { return sessions.containsKey(p.getUniqueId()) || pending.containsKey(p.getUniqueId()); }
    private long deadline(Player p) { return p.getPersistentDataContainer().getOrDefault(COOLDOWN, PersistentDataType.LONG, 0L); }
    private int remainingEffectTicks(Player p) {
        FoodSession session = sessions.get(p.getUniqueId());
        return session == null ? (pending.containsKey(p.getUniqueId()) ? 1 : 0) : Math.max(0, session.settings.duration() - session.age);
    }
    void showStatus(Player p, long now) {
        long seconds = FoodCooldown.waitingSeconds(deadline(p), now, remainingEffectTicks(p));
        if (p.isDead() || !plugin.foodParticipating(p) || !plugin.getConfig().getBoolean("messages.cooldown-bar-enabled", true)) {
            hideStatus(p); return;
        }
        if (seconds == 0 && !active(p)) {
            p.getPersistentDataContainer().remove(COOLDOWN);
            if (hideStatus(p)) {
                p.sendMessage(Component.text("Желудок готов! Можно снова есть.", NamedTextColor.GREEN));
                if (plugin.getConfig().getBoolean("messages.actionbar-enabled", true))
                    p.sendActionBar(Component.text("Желудок готов!", NamedTextColor.GREEN));
            }
            return;
        }
        Status status = statuses.get(p.getUniqueId());
        if (status == null) {
            BossBar bar = BossBar.bossBar(Component.empty(), 1, BossBar.Color.YELLOW, BossBar.Overlay.PROGRESS);
            status = new Status(p, bar, Math.max(1, seconds));
            statuses.put(p.getUniqueId(), status);
            p.showBossBar(bar);
        }
        String suffix = deadline(p) > now
                ? (plugin.getConfig().getBoolean("foods.milk-resets-cooldown", true) ? " • молоко сбросит кулдаун" : "")
                : " • эффект еды ещё действует";
        status.bar.name(Component.text("Желудок ещё не готов: " + seconds + " с" + suffix));
        status.bar.progress((float)Math.clamp((double)seconds / status.totalSeconds, 0, 1));
    }
    private boolean hideStatus(Player p) {
        Status status = statuses.remove(p.getUniqueId());
        if (status == null) return false;
        p.hideBossBar(status.bar); return true;
    }
    private String unavailable(Player p, FoodType type) {
        if (!FoodSettings.read(plugin.getConfig(), type).enabled()) return "Эта еда отключена.";
        if (!p.hasPermission("fartmc.use") || !plugin.foodParticipating(p)) return "Участие выключено или нет права fartmc.use.";
        if (p.isDead() || p.getGameMode() == GameMode.SPECTATOR || p.isFlying() || p.isGliding() || p.isInsideVehicle())
            return "Сначала встаньте на землю и выйдите из транспорта.";
        if (active(p)) return "Желудок ещё не готов: ещё "
                + FoodCooldown.waitingSeconds(deadline(p), System.currentTimeMillis(), remainingEffectTicks(p))
                + " с. Текущий эффект еды ещё действует.";
        long seconds = FoodCooldown.seconds(deadline(p), System.currentTimeMillis());
        if (seconds > 0) return "Желудок ещё не готов: ещё " + seconds + " с."
                + (plugin.getConfig().getBoolean("foods.milk-resets-cooldown", true) ? " Выпейте молоко, чтобы сбросить кулдаун." : "");
        if (type == FoodType.POISONED_CHEBUREK) {
            if (p.isInWater() || !FoodMotion.safeGround(FoodMotion.ground(p.getLocation().add(0, .2, 0), 4))
                    || !FoodMotion.clear(p.getWorld(), p.getBoundingBox().clone().shift(0, .7, 0)))
                return "Для взлёта нужна безопасная поверхность и место над головой.";
        }
        return null;
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void checkConsume(PlayerItemConsumeEvent e) {
        FoodType type = FoodItems.type(e.getItem());
        if (type == null) return;
        String reason = unavailable(e.getPlayer(), type);
        if (reason != null) { e.setCancelled(true); e.getPlayer().sendMessage(reason); showStatus(e.getPlayer(), System.currentTimeMillis()); }
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void consume(PlayerItemConsumeEvent e) {
        if (e.getItem().getType() == Material.MILK_BUCKET && !FoodItems.marked(e.getItem())) {
            milk(e); return;
        }
        FoodType type = FoodItems.type(e.getItem());
        if (type == null || active(e.getPlayer())) return;
        Player player = e.getPlayer();
        UUID world = player.getWorld().getUID();
        // Reserve now, commit next tick after the final cancellation status and vanilla consumption.
        BukkitTask start = Bukkit.getScheduler().runTask(plugin, () -> {
            pending.remove(player.getUniqueId());
            if (e.isCancelled() || !player.isOnline() || !world.equals(player.getWorld().getUID())
                    || unavailable(player, type) != null) return;
            FoodSettings settings = FoodSettings.read(plugin.getConfig(), type);
            FoodSession session = new FoodSession(player, type, settings);
            sessions.put(player.getUniqueId(), session);
            player.getPersistentDataContainer().set(COOLDOWN, PersistentDataType.LONG,
                    System.currentTimeMillis() + settings.cooldownMillis());
            try { session.begin(this); showStatus(player, System.currentTimeMillis()); }
            catch (RuntimeException ex) {
                stop(player, false);
                plugin.getLogger().log(java.util.logging.Level.WARNING, "Food effect could not start", ex);
                player.sendMessage("Эффект безопасно остановлен: запуск недоступен.");
            }
        });
        pending.put(player.getUniqueId(), start);
    }
    private void milk(PlayerItemConsumeEvent e) {
        if (!plugin.getConfig().getBoolean("foods.milk-resets-cooldown", true)) return;
        Player p = e.getPlayer();
        if (milkTasks.containsKey(p.getUniqueId())) return;
        milkTasks.put(p.getUniqueId(), Bukkit.getScheduler().runTask(plugin, () -> {
            milkTasks.remove(p.getUniqueId());
            if (e.isCancelled() || !p.isOnline() || p.isDead()) return;
            boolean cooling = deadline(p) > System.currentTimeMillis();
            p.getPersistentDataContainer().remove(COOLDOWN);
            if (cooling) {
                p.sendMessage(active(p) ? "Молоко сбросило кулдаун. Дождитесь окончания текущего эффекта еды."
                        : "Молоко сбросило кулдаун. Желудок готов! Можно снова есть.");
                if (!active(p)) hideStatus(p); // Avoid a second readiness message.
            }
            showStatus(p, System.currentTimeMillis());
            // Minecraft consumes the milk and returns the bucket; do not edit inventory.
        }));
    }
    private void update() {
        tick++; visuals.tick(tick);
        if (tick % 20 == 0) protection.tick();
        for (FoodSession session : List.copyOf(sessions.values())) {
            try {
                if (!session.update(this) || !plugin.foodParticipating(session.player)) stop(session.player, session.player.isDead());
                else if (tick % 20 == 0 && plugin.getConfig().getBoolean("messages.actionbar-enabled", true))
                    session.player.sendActionBar(Component.text(session.type.title + ": " + (session.settings.duration() - session.age + 19) / 20 + " с"));
            } catch (RuntimeException ex) {
                stop(session.player, session.player.isDead());
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "Food effect safely stopped", ex);
            }
        }
        if (tick % 20 == 0) {
            long now = System.currentTimeMillis();
            Bukkit.getOnlinePlayers().forEach(p -> showStatus(p, now));
        }
        landings.entrySet().removeIf(e -> {
            Player p = Bukkit.getPlayer(e.getKey());
            if (p == null) return true; // PDC marker remains for the next login.
            if (p.isOnGround() || p.isInWater() || p.isDead() || tick >= e.getValue()) {
                p.getPersistentDataContainer().remove(LANDING); return true;
            }
            return false;
        });
    }
    void stop(Player p, boolean dead) {
        BukkitTask start = pending.remove(p.getUniqueId());
        if (start != null) start.cancel();
        FoodSession session = sessions.remove(p.getUniqueId());
        if (session != null) session.stop(this, dead);
    }
    void stopAll() {
        milkTasks.values().forEach(BukkitTask::cancel); milkTasks.clear();
        pending.values().forEach(BukkitTask::cancel); pending.clear();
        for (FoodSession s : List.copyOf(sessions.values())) stop(s.player, s.player.isDead());
        visuals.close();
    }
    void reload() { stopAll(); protection.reload(); }
    void landing(Player p) {
        if (p.isDead()) return;
        p.setFallDistance(0);
        // Finite vanilla handoff also works after disable/logout when no task can protect the player.
        PotionEffect old = p.getPotionEffect(PotionEffectType.SLOW_FALLING);
        if (old == null || (!old.isInfinite() && old.getAmplifier() == 0 && old.getDuration() < 200))
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 200, 0, false, false, true));
        p.getPersistentDataContainer().set(LANDING, PersistentDataType.BYTE, (byte)1);
        landings.put(p.getUniqueId(), tick + 300);
    }
    private void recover(Player p) {
        var data = p.getPersistentDataContainer();
        double credit = data.getOrDefault(HEALTH_CREDIT, PersistentDataType.DOUBLE, 0d);
        double original = data.getOrDefault(ORIGINAL_HEALTH, PersistentDataType.DOUBLE, p.getHealth());
        if (!p.isDead() && Double.isFinite(credit) && credit > 0 && Double.isFinite(original)) {
            var attribute = p.getAttribute(Attribute.MAX_HEALTH);
            double max = attribute == null ? 20 : attribute.getValue();
            p.setHealth(Math.min(max, FoodPhysics.restored(p.getHealth(), credit, original, max)));
        }
        data.remove(HEALTH_CREDIT); data.remove(ORIGINAL_HEALTH);
        if (data.has(LANDING)) landing(p);
        showStatus(p, System.currentTimeMillis());
    }
    void fart(Player p, FartLogic.Style style) {
        if (plugin.getConfig().getBoolean("effects.sounds-enabled", true)) plugin.foodSound(p, style);
    }
    void sound(Player p, String sound, float volume, float pitch) {
        if (plugin.getConfig().getBoolean("effects.sounds-enabled", true)) plugin.foodSound(p, sound, volume, pitch);
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void potionChanged(EntityPotionEffectEvent e) {
        FoodSession session = sessions.get(e.getEntity().getUniqueId());
        if (session != null && !session.applyingPotion) session.potions.remove(e.getModifiedType());
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void damage(EntityDamageEvent e) {
        if (e.getEntity() instanceof ArmorStand stand && stand.getScoreboardTags().contains("fartmc_food_seat")) e.setCancelled(true);
        if (e.getEntity() instanceof Player p && e.getCause() == EntityDamageEvent.DamageCause.FALL && landings.containsKey(p.getUniqueId())) {
            e.setCancelled(true); landings.remove(p.getUniqueId()); p.getPersistentDataContainer().remove(LANDING);
        }
        if (e.getEntity() instanceof Player p && e.getCause() == EntityDamageEvent.DamageCause.FIRE
                && protection.ownFire(p, p.getLocation().getBlock())) e.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void combust(EntityCombustByBlockEvent e) {
        if (e.getEntity() instanceof Player p && protection.ownFire(p,
                e.getCombuster() == null ? p.getLocation().getBlock() : e.getCombuster())) e.setCancelled(true);
    }
    @EventHandler(ignoreCancelled = true)
    public void manipulate(PlayerArmorStandManipulateEvent e) {
        if (e.getRightClicked().getScoreboardTags().contains("fartmc_food_seat")) e.setCancelled(true);
    }
    @EventHandler public void join(PlayerJoinEvent e) { recover(e.getPlayer()); }
    @EventHandler public void quit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        stop(p, false); landings.remove(p.getUniqueId()); hideStatus(p);
        BukkitTask milk = milkTasks.remove(p.getUniqueId()); if (milk != null) milk.cancel();
    }
    @EventHandler public void death(PlayerDeathEvent e) {
        stop(e.getEntity(), true); landings.remove(e.getEntity().getUniqueId()); e.getEntity().getPersistentDataContainer().remove(LANDING);
        hideStatus(e.getEntity());
    }
    @EventHandler public void world(PlayerChangedWorldEvent e) { stop(e.getPlayer(), false); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void teleport(PlayerTeleportEvent e) { stop(e.getPlayer(), false); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void mode(PlayerGameModeChangeEvent e) { stop(e.getPlayer(), false); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void unload(WorldUnloadEvent e) {
        for (FoodSession s : List.copyOf(sessions.values())) if (s.world.equals(e.getWorld().getUID())) stop(s.player, s.player.isDead());
        visuals.removeWorld(e.getWorld());
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void chunk(ChunkUnloadEvent e) { visuals.removeChunk(e.getChunk()); }
    @Override public void close() {
        stopAll(); task.cancel(); landings.clear();
        for (Status status : List.copyOf(statuses.values())) hideStatus(status.player);
        HandlerList.unregisterAll(this);
    }
}
