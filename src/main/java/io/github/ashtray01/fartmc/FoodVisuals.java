package io.github.ashtray01.fartmc;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.util.*;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

final class FoodVisuals {
    private record Stain(UUID owner, BlockDisplay entity, long expires) {}
    private final FartPlugin plugin;
    private final List<Stain> stains = new ArrayList<>();
    private final Map<UUID, Integer> budget = new HashMap<>();
    FoodVisuals(FartPlugin plugin) { this.plugin = plugin; }
    void tick(long tick) {
        budget.clear();
        stains.removeIf(s -> {
            if (!s.entity.isValid() || tick >= s.expires) { s.entity.remove(); return true; }
            return false;
        });
    }
    Location behind(Player p) { return p.getLocation().add(FoodPhysics.forward(p.getYaw()).multiply(-.45)).add(0, .5, 0); }
    private <T> void particle(Player p, Particle particle, Location point, int count, double spread, T data) {
        if (!plugin.getConfig().getBoolean("effects.particles-enabled", true)
                || !plugin.getConfig().getBoolean("particles.enabled", true) || !FoodMotion.loaded(point)) return;
        int limit = (int)FoodSettings.number(plugin.getConfig(), "effects.particles-per-player-limit", 50, 0, 100);
        int used = budget.getOrDefault(p.getUniqueId(), 0);
        count = Math.min(count, limit - used);
        if (count <= 0) return;
        budget.put(p.getUniqueId(), used + count);
        for (Player viewer : p.getWorld().getPlayers()) if (plugin.foodViewer(p, viewer))
            viewer.spawnParticle(particle, point, count, spread, spread, spread, .015, data);
    }
    void gas(Player p) {
        Color[] colors = {Color.fromRGB(150, 205, 70), Color.fromRGB(90, 125, 40), Color.fromRGB(190, 200, 70)};
        particle(p, Particle.DUST_COLOR_TRANSITION, behind(p), 18, .2,
                new Particle.DustTransition(colors[ThreadLocalRandom.current().nextInt(3)], Color.fromRGB(195, 215, 135), 1.3f));
        if (ThreadLocalRandom.current().nextInt(3) == 0)
            particle(p, Particle.COMPOSTER, behind(p), 4, .3, null);
    }
    void water(Player p, double length, double fade) {
        Location origin = behind(p);
        org.bukkit.util.Vector back = FoodPhysics.forward(p.getYaw()).multiply(-1);
        for (int i = 0; i < 7 * fade; i++) {
            Location point = origin.clone().add(back.clone().multiply(length * i / 6)).add(0, -.04 * i, 0);
            particle(p, i % 2 == 0 ? Particle.SPLASH : Particle.FALLING_WATER, point, 3, .08 + i * .025, null);
            particle(p, Particle.BUBBLE_POP, point, 1, .12, null);
            particle(p, Particle.DUST, point, 1, .1, new Particle.DustOptions(Color.fromRGB(115, 200, 255), .8f));
        }
    }
    void fire(Player p, double length, double fade) {
        Location origin = behind(p);
        var hit = FoodMotion.ground(origin, length);
        if (hit != null) length = Math.min(length, origin.getY() - hit.getHitPosition().getY());
        particle(p, Particle.FLAME, origin, (int)(8 * fade), .1, null);
        for (int i = 1; i <= 8 * fade; i++) {
            Location point = origin.clone().add(0, -length * i / 8, 0);
            particle(p, Particle.FLAME, point, 3, .08 + i * .025, null);
            particle(p, i % 3 == 0 ? Particle.SOUL_FIRE_FLAME : Particle.SMOKE, point, 1, .15, null);
            if (i > 6) particle(p, Particle.LARGE_SMOKE, point, 2, .25, null);
        }
    }
    void stain(Player player, long tick, int duration) {
        particle(player, Particle.DUST, behind(player), 24, .35, new Particle.DustOptions(Color.fromRGB(100, 60, 25), 1.3f));
        int cap = (int)FoodSettings.number(plugin.getConfig(), "effects.max-stains", 120, 0, 300);
        for (int i = 0; i < 3 && stains.size() < cap; i++) {
            Location from = player.getLocation().add((i - 1) * .35, .5, (i % 2) * .3);
            var hit = FoodMotion.ground(from, 3);
            if (!FoodMotion.safeGround(hit)) continue;
            Location point = hit.getHitPosition().toLocation(player.getWorld()).add(-.2, .015, -.2);
            BlockDisplay display = player.getWorld().spawn(point, BlockDisplay.class, d -> {
                d.setPersistent(false); d.setInvulnerable(true);
                d.setBlock(Material.BROWN_TERRACOTTA.createBlockData());
                d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(.45f, .012f, .38f), new Quaternionf()));
                d.setViewRange(.5f);
            });
            stains.add(new Stain(player.getUniqueId(), display, tick + duration));
        }
    }
    void remove(UUID owner) { removeIf(s -> s.owner.equals(owner)); }
    void removeWorld(World world) { removeIf(s -> s.entity.getWorld().equals(world)); }
    void removeChunk(Chunk chunk) {
        removeIf(s -> s.entity.getWorld().equals(chunk.getWorld()) && s.entity.getLocation().getBlockX() >> 4 == chunk.getX()
                && s.entity.getLocation().getBlockZ() >> 4 == chunk.getZ());
    }
    private void removeIf(java.util.function.Predicate<Stain> predicate) {
        stains.removeIf(s -> { if (!predicate.test(s)) return false; s.entity.remove(); return true; });
    }
    void close() { removeIf(s -> true); budget.clear(); }
}
