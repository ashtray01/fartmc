package io.github.ashtray01.fartmc;

import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.*;
import org.bukkit.util.Vector;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

final class FoodSession {
    record OwnedPotion(PotionEffect previous, long started) {}
    final Player player;
    final FoodType type;
    final FoodSettings settings;
    final UUID world;
    final Map<PotionEffectType, OwnedPotion> potions = new HashMap<>();
    final int accidentTick;
    int age;
    boolean stopped, applyingPotion;
    ArmorStand seat;
    double originalHealth, credit;

    FoodSession(Player player, FoodType type, FoodSettings settings) {
        this.player = player; this.type = type; this.settings = settings; world = player.getWorld().getUID();
        accidentTick = ThreadLocalRandom.current().nextDouble() < settings.accidentChance()
                ? ThreadLocalRandom.current().nextInt(settings.duration()) : -1;
    }
    void begin(FoodEffects manager) {
        manager.sound(player, "block.bubble_column.bubble_pop", .7f, .55f);
        if (type == FoodType.BELYASH) {
            potion(PotionEffectType.POISON, settings.poison(), manager.tick);
            potion(PotionEffectType.NAUSEA, settings.nausea(), manager.tick);
        } else if (type == FoodType.KHACHEBUREK) {
            originalHealth = player.getHealth();
            credit = FoodPhysics.withheld(originalHealth, maximumHealth(), settings.healthPercent());
            if (credit > 0) {
                player.getPersistentDataContainer().set(FoodEffects.HEALTH_CREDIT, PersistentDataType.DOUBLE, credit);
                player.getPersistentDataContainer().set(FoodEffects.ORIGINAL_HEALTH, PersistentDataType.DOUBLE, originalHealth);
                player.setHealth(originalHealth - credit);
            }
        } else {
            // A small, non-marker stand retains normal server physics. No teleports per tick.
            seat = FoodMotion.seat(player.getLocation(), player);
            player.getPersistentDataContainer().set(FoodEffects.LANDING, PersistentDataType.BYTE, (byte)1);
            manager.visuals.fire(player, 1, 1);
        }
    }
    private void potion(PotionEffectType type, int amplifier, long tick) {
        PotionEffect old = player.getPotionEffect(type);
        if (old != null && (old.getAmplifier() > amplifier || old.isInfinite()
                || old.getDuration() >= settings.duration())) return;
        applyingPotion = true;
        try {
            if (player.addPotionEffect(new PotionEffect(type, settings.duration(), amplifier, false, false, true)))
                potions.put(type, new OwnedPotion(old, tick));
        } finally { applyingPotion = false; }
    }
    boolean update(FoodEffects manager) {
        if (stopped || !player.isOnline() || player.isDead() || !world.equals(player.getWorld().getUID())
                || player.getGameMode() == GameMode.SPECTATOR || player.isFlying() || player.isGliding()) return false;
        if (age >= settings.duration()) return false;
        if (type != FoodType.POISONED_CHEBUREK && player.isInsideVehicle()) return false;
        switch (type) {
            case BELYASH -> belyash(manager);
            case KHACHEBUREK -> water(manager);
            case POISONED_CHEBUREK -> { if (!fire(manager)) return false; }
        }
        age++;
        return true;
    }
    private void belyash(FoodEffects m) {
        if (age % settings.fartInterval() == 0) {
            m.visuals.gas(player);
            m.fart(player, age % 80 == 0 ? FartLogic.Style.THUNDER : FartLogic.Style.CLASSIC);
        }
        if (age % 60 == 0) m.sound(player, "block.bubble_column.bubble_pop", .5f, .6f);
        if (age == accidentTick) {
            m.fart(player, FartLogic.Style.THUNDER);
            player.sendMessage("Кажется, беляш был несвежий...");
            m.visuals.stain(player, m.tick, settings.stainTicks());
        }
    }
    private void water(FoodEffects m) {
        if (age < 20) return;
        double surge = age % 32 < 3 ? .06 : 0;
        Vector target = FoodPhysics.forward(player.getYaw()).multiply(Math.min(settings.maxSpeed(), settings.speed() + surge));
        FoodMotion.velocity(player, player, FoodPhysics.accelerate(player.getVelocity(), target, settings.acceleration(), settings.maxSpeed()));
        if (age % 4 == 0) m.visuals.water(player, settings.jetLength(), Math.min(1, (settings.duration() - age) / 20.0));
        if (age % 16 == 0) {
            m.fart(player, FartLogic.Style.BUBBLES);
            m.sound(player, "entity.player.splash.high_speed", .45f, .65f);
        }
    }
    private boolean fire(FoodEffects m) {
        if (seat == null || !seat.isValid() || !seat.getPassengers().contains(player) || player.isInWater()) return false;
        var ground = FoodMotion.ground(player.getLocation().add(0, .2, 0), 24);
        if (!FoodMotion.safeGround(ground)) return false;
        double floor = ground.getHitPosition().getY();
        double height = player.getY() - floor;
        double target = Math.min(settings.maxHover(), settings.hover() * Math.min(1, (age + 1) / 40.0));
        double remaining = settings.duration() - age;
        // Last two seconds lower the target; the remainder is covered by safe landing.
        if (remaining < 40) target *= remaining / 40;
        double targetY = Math.min(floor + target, player.getWorld().getMaxHeight() - 3);
        double vertical = Math.clamp((targetY - player.getY()) * .22 + .08, -.25, .32);
        if (age >= 40 && remaining >= 40) vertical += Math.sin(age * .4) * .018;
        if (height >= settings.maxHover()) vertical = Math.min(0, vertical);
        Vector wanted = FoodPhysics.forward(player.getYaw()).multiply(settings.speed());
        Vector velocity = FoodPhysics.accelerate(seat.getVelocity(), wanted, .025, settings.speed()).setY(vertical);
        FoodMotion.velocity(player, seat, velocity);
        player.setFallDistance(0); seat.setFallDistance(0);
        if (age % 4 == 0) m.visuals.fire(player, settings.jetLength(), Math.min(1, remaining / 40));
        if (age % 20 == 0) { m.fart(player, FartLogic.Style.RATTLE); m.sound(player, "item.firecharge.use", .4f, .65f); }
        if (remaining > 40 && age % settings.ignitionInterval() == 0)
            m.protection.ignite(player, settings, m.visuals.behind(player));
        return true;
    }
    private double maximumHealth() {
        var attribute = player.getAttribute(Attribute.MAX_HEALTH);
        return attribute == null ? 20 : attribute.getValue();
    }
    void stop(FoodEffects m, boolean dead) {
        if (stopped) return;
        stopped = true;
        if (seat != null) { seat.eject(); seat.remove(); seat = null; }
        if (!dead && !player.isDead()) {
            if (credit > 0) player.setHealth(Math.min(maximumHealth(),
                    FoodPhysics.restored(player.getHealth(), credit, originalHealth, maximumHealth())));
            if (type == FoodType.POISONED_CHEBUREK && settings.safeLanding()) m.landing(player);
            applyingPotion = true;
            try {
                for (var entry : potions.entrySet()) {
                    player.removePotionEffect(entry.getKey());
                    PotionEffect previous = entry.getValue().previous();
                    if (previous != null) {
                        int duration = previous.isInfinite() ? -1 : previous.getDuration() - (int)(m.tick - entry.getValue().started());
                        if (duration > 0 || previous.isInfinite()) player.addPotionEffect(new PotionEffect(previous.getType(), duration,
                                previous.getAmplifier(), previous.isAmbient(), previous.hasParticles(), previous.hasIcon()));
                    }
                }
            } finally { applyingPotion = false; }
        }
        player.getPersistentDataContainer().remove(FoodEffects.HEALTH_CREDIT);
        player.getPersistentDataContainer().remove(FoodEffects.ORIGINAL_HEALTH);
        if (dead || !settings.safeLanding()) player.getPersistentDataContainer().remove(FoodEffects.LANDING);
        credit = 0; potions.clear();
        m.visuals.remove(player.getUniqueId());
        if (!dead) m.sound(player, "entity.player.burp", .4f, 1.2f);
    }
}
