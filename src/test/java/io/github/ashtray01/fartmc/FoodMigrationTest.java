package io.github.ashtray01.fartmc;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FoodMigrationTest {
    @Test void oldConfigurationOverridesDefaultsAndPreservesExplicitNewValues() {
        var defaults = new YamlConfiguration();
        defaults.set("foods.cheburek.duration-seconds", 12);
        defaults.set("foods.shaurma.ignition-enabled", true);
        var config = new YamlConfiguration();
        config.setDefaults(defaults);
        config.set("foods.khacheburek.duration-seconds", 28);
        config.set("foods.khacheburek.cooldown-seconds", 99);
        config.set("foods.cheburek.cooldown-seconds", 14);
        config.set("foods.poisoned-cheburek.ignition-enabled", false);
        config.set("foods.belyash.duration-seconds", 25);
        assertTrue(FoodSettings.migrate(config));
        assertEquals(560, FoodSettings.read(config, FoodType.CHEBUREK).duration());
        assertEquals(14000, FoodSettings.read(config, FoodType.CHEBUREK).cooldownMillis());
        assertFalse(FoodSettings.read(config, FoodType.SHAURMA).ignition());
        assertEquals(500, FoodSettings.read(config, FoodType.BELYASH).duration());
        assertFalse(config.contains("foods.khacheburek", true));
        assertFalse(config.contains("foods.poisoned-cheburek", true));
        assertFalse(FoodSettings.migrate(config));
    }
    @Test void canonicalAndLegacyIdentifiersResolveToTheSameFood() {
        for (FoodType type : FoodType.values()) {
            assertSame(type, FoodType.parse(type.id));
            assertSame(type, FoodType.parse(type.legacyId));
            assertSame(type, FoodType.parse(type.id.toUpperCase(java.util.Locale.ROOT)));
        }
        assertNull(FoodType.parse("unknown"));
        assertNull(FoodType.parse(null));
    }
    @Test void shaurmaDefaultsToTwentySecondsAndMigratesOnlyTheOldDefault() {
        var config = new YamlConfiguration();
        assertEquals(400, FoodSettings.read(config, FoodType.SHAURMA).duration());
        config.set("foods.poisoned-cheburek.duration-seconds", 15);
        FoodSettings.migrate(config);
        assertEquals(400, FoodSettings.read(config, FoodType.SHAURMA).duration());
        config.set("foods.shaurma", null);
        config.set("foods.poisoned-cheburek.duration-seconds", 35);
        FoodSettings.migrate(config);
        assertEquals(700, FoodSettings.read(config, FoodType.SHAURMA).duration());
    }
}
