package io.github.ashtray01.fartmc;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.util.BoundingBox;
import java.lang.reflect.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.*;

/** Optional WorldGuard public API bridge: no server internals or mandatory dependency. */
final class FoodProtection {
    private final FartPlugin plugin;
    private Object query, worldGuard;
    private Method adapt, wrap, testBuild, testState;
    private Object buildFlags, fireFlags, pvpFlags, mobFlags;
    private boolean failed, warned;
    private record FirePosition(UUID world, int x, int y, int z) {
        static FirePosition of(Block block) { return new FirePosition(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ()); }
    }
    private record OwnFire(UUID owner, long until) {}
    private final Map<FirePosition, OwnFire> ownFires = new HashMap<>();

    void tick() { ownFires.values().removeIf(f -> f.until < System.currentTimeMillis()); }
    boolean ownFire(Player player, Block block) {
        if (block == null || block.getType() != Material.FIRE) return false;
        OwnFire fire = ownFires.get(FirePosition.of(block));
        return fire != null && fire.owner.equals(player.getUniqueId()) && fire.until >= System.currentTimeMillis();
    }

    FoodProtection(FartPlugin plugin) { this.plugin = plugin; }
    void reload() {
        query = null; failed = false; warned = false;
        var wg = Bukkit.getPluginManager().getPlugin("WorldGuard");
        if (wg == null) return;
        if (!wg.isEnabled()) { failed = true; return; }
        try {
            ClassLoader loader = wg.getClass().getClassLoader();
            Class<?> core = Class.forName("com.sk89q.worldguard.WorldGuard", true, loader);
            Object instance = core.getMethod("getInstance").invoke(null);
            Object platform = core.getMethod("getPlatform").invoke(instance);
            Object container = platform.getClass().getMethod("getRegionContainer").invoke(platform);
            query = container.getClass().getMethod("createQuery").invoke(container);
            worldGuard = wg;
            wrap = wg.getClass().getMethod("wrapPlayer", Player.class);
            adapt = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter", true, loader).getMethod("adapt", Location.class);
            Class<?> location = Class.forName("com.sk89q.worldedit.util.Location", true, loader);
            Class<?> player = Class.forName("com.sk89q.worldguard.LocalPlayer", true, loader);
            Class<?> state = Class.forName("com.sk89q.worldguard.protection.flags.StateFlag", true, loader);
            Class<?> flags = Class.forName("com.sk89q.worldguard.protection.flags.Flags", true, loader);
            Class<?> array = Array.newInstance(state, 0).getClass();
            testBuild = query.getClass().getMethod("testBuild", location, player, array);
            testState = query.getClass().getMethod("testState", location, player, array);
            buildFlags = flags(state, flags); // Strict BUILD check in addition to related flags.
            fireFlags = flags(state, flags, "BLOCK_PLACE", "LIGHTER", "FIRE_SPREAD");
            pvpFlags = flags(state, flags, "PVP");
            mobFlags = flags(state, flags, "DAMAGE_ANIMALS");
        } catch (ReflectiveOperationException | LinkageError ex) { fail(ex); }
    }
    private Object flags(Class<?> state, Class<?> flags, String... names) throws ReflectiveOperationException {
        Object array = Array.newInstance(state, names.length);
        for (int i = 0; i < names.length; i++) Array.set(array, i, flags.getField(names[i]).get(null));
        return array;
    }
    private void fail(Throwable ex) {
        failed = true;
        if (!warned) { plugin.getLogger().warning("WorldGuard check unavailable; food ignition denied: " + ex); warned = true; }
    }
    boolean allowed(Player player, Location location, boolean entity, boolean pvp) {
        if (!plugin.getConfig().getBoolean("effects.respect-protected-regions", true)) return true;
        if (failed) return false;
        if (query == null) return Bukkit.getPluginManager().getPlugin("WorldGuard") == null;
        if (!Bukkit.getPluginManager().isPluginEnabled("WorldGuard")) return false;
        try {
            Object point = adapt.invoke(null, location), localPlayer = wrap.invoke(worldGuard, player);
            if (!(boolean)testBuild.invoke(query, point, localPlayer, buildFlags)) return false;
            return (boolean)(entity ? testState : testBuild).invoke(query, point, localPlayer,
                    entity ? (pvp ? pvpFlags : mobFlags) : fireFlags);
        } catch (ReflectiveOperationException | LinkageError ex) { fail(ex); return false; }
    }
    void ignite(Player player, FoodSettings settings, Location origin) {
        if (!settings.ignition() || player.isInWater() || !FoodMotion.loaded(origin)) return;
        var hit = FoodMotion.ground(origin, settings.jetLength());
        double bottom = hit == null ? origin.getY() - settings.jetLength() : hit.getHitPosition().getY();
        if (FoodMotion.safeGround(hit) && ThreadLocalRandom.current().nextDouble() < settings.ignitionChance()) {
            Block support = hit.getHitBlock();
            Block target = support.getRelative(0, 1, 0);
            int spawnRadius = Bukkit.getSpawnRadius();
            Location spawn = player.getWorld().getSpawnLocation();
            boolean spawnProtected = player.getWorld().equals(Bukkit.getWorlds().getFirst()) && !player.isOp()
                    && Math.abs(target.getX() - spawn.getBlockX()) <= spawnRadius
                    && Math.abs(target.getZ() - spawn.getBlockZ()) <= spawnRadius && spawnRadius > 0;
            if (!spawnProtected && target.getType().isAir()
                    && support.getBlockData().isFaceSturdy(org.bukkit.block.BlockFace.UP, org.bukkit.block.BlockSupport.FULL)
                    && allowed(player, target.getLocation(), false, false)) {
                BlockIgniteEvent event = new BlockIgniteEvent(target, BlockIgniteEvent.IgniteCause.FLINT_AND_STEEL, player);
                Bukkit.getPluginManager().callEvent(event);
                if (!event.isCancelled() && target.getType().isAir() && ownFires.size() < 1024) {
                    target.setType(Material.FIRE);
                    ownFires.put(FirePosition.of(target), new OwnFire(player.getUniqueId(), System.currentTimeMillis() + 30_000));
                }
            }
        }
        if (!settings.igniteEntities()) return;
        BoundingBox jet = new BoundingBox(origin.getX() - .4, bottom, origin.getZ() - .4,
                origin.getX() + .4, origin.getY(), origin.getZ() + .4);
        for (Entity target : player.getWorld().getNearbyEntities(jet)) {
            if (!(target instanceof LivingEntity living) || target.equals(player) || target instanceof ArmorStand
                    || target.isDead() || target.isInvulnerable()) continue;
            boolean pvp = target instanceof Player;
            if (pvp && (!settings.ignitePlayers() || !player.getWorld().getPVP()
                    || !plugin.foodParticipating((Player)target))) continue;
            if (!allowed(player, target.getLocation(), true, pvp)
                    || (pvp && !allowed(player, player.getLocation(), true, true))) continue;
            EntityCombustByEntityEvent event = new EntityCombustByEntityEvent(player, target, 2f);
            Bukkit.getPluginManager().callEvent(event);
            if (!event.isCancelled()) living.setFireTicks(Math.max(living.getFireTicks(),
                    (int)(20 * FoodSettings.bounded(event.getDuration(), 2, 0, 10))));
        }
    }
}
