package io.github.ashtray01.fartmc;

import org.bukkit.configuration.file.FileConfiguration;

/** Validated values are snapshots for a session; reload first stops all sessions. */
record FoodSettings(boolean enabled, int duration, long cooldownMillis, int fartInterval,
                    int poison, int nausea, double accidentChance, int stainTicks,
                    double speed, double maxSpeed, double acceleration, double healthPercent,
                    double jetLength, double hover, double maxHover, boolean ignition,
                    int ignitionInterval, double ignitionChance, boolean igniteEntities,
                    boolean ignitePlayers, boolean safeLanding) {
    static double bounded(double value, double fallback, double min, double max) {
        return Math.clamp(Double.isFinite(value) ? value : fallback, min, max);
    }
    static boolean migrate(FileConfiguration c) {
        boolean changed = false;
        for (FoodType type : FoodType.values()) {
            if (type.config.equals(type.legacyConfig)) continue;
            String oldPath = type.legacyConfig.substring(0, type.legacyConfig.length() - 1);
            if (!c.contains(oldPath, true)) continue;
            var old = c.getConfigurationSection(oldPath);
            if (old == null) continue;
            for (var entry : old.getValues(true).entrySet()) {
                if (entry.getValue() instanceof org.bukkit.configuration.ConfigurationSection) continue;
                String key = type.config + entry.getKey();
                Object value = entry.getValue();
                // Upgrade the previous default, retaining custom durations and explicit new values.
                if (type == FoodType.SHAURMA && entry.getKey().equals("duration-seconds")
                        && value instanceof Number n && n.doubleValue() == 15) value = 20;
                if (!c.contains(key, true)) c.set(key, value);
            }
            c.set(oldPath, null); changed = true;
        }
        return changed;
    }
    static double number(FileConfiguration c, String key, double fallback, double min, double max) {
        return bounded(c.getDouble(key, fallback), fallback, min, max);
    }
    static FoodSettings read(FileConfiguration c, FoodType t) {
        String p = t.config;
        boolean fire = t == FoodType.SHAURMA;
        double maxHover = number(c, p + "maximum-hover-height", 7, 1, 7);
        double maxSpeed = number(c, p + "max-horizontal-speed", .65, 0, .65);
        return new FoodSettings(c.getBoolean(p + "enabled", true),
                (int)(20 * number(c, p + "duration-seconds", t.duration, 1, 300)),
                (long)(1000 * number(c, p + "cooldown-seconds", t.cooldown, 0, 3600)),
                (int)number(c, p + "fart-interval-ticks", 8, 4, 200),
                (int)number(c, p + "poison-amplifier", 0, 0, 4),
                (int)number(c, p + "nausea-amplifier", 0, 0, 4),
                number(c, p + "accident-chance", .35, 0, 1),
                (int)(20 * number(c, p + "stain-duration-seconds", 10, 1, 60)),
                number(c, p + (fire ? "horizontal-speed" : "initial-horizontal-speed"), fire ? .15 : .45, 0, fire ? .25 : maxSpeed),
                maxSpeed, number(c, p + "acceleration", .04, .001, .1),
                number(c, p + "health-percent", .5, .05, 1),
                number(c, p + "jet-length", fire ? 7 : 3, .5, fire ? 7 : 4),
                number(c, p + "target-hover-height", 6, 1, maxHover), maxHover,
                c.getBoolean(p + "ignition-enabled", true),
                (int)number(c, p + "ignition-interval-ticks", 8, 4, 200),
                number(c, p + "ignition-chance", .4, 0, 1),
                c.getBoolean(p + "ignite-entities", true), c.getBoolean(p + "ignite-players", false),
                c.getBoolean(p + "safe-landing", true));
    }
}
