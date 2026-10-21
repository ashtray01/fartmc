package io.github.ashtray01.fartmc;

import org.bukkit.NamespacedKey;
import java.util.Locale;

enum FoodType {
    BELYASH("belyash", "belyash_station", "belyash", "Беляш с вокзала", "Газы, отравление и неожиданные пятна", 30, 45),
    CHEBUREK("cheburek", "khacheburek", "khacheburek", "Чебурек загадочный", "Водяная тяга и временная потеря здоровья", 12, 45),
    SHAURMA("shaurma", "poisoned_cheburek", "poisoned-cheburek", "Просроченная шаурма", "Огненный полёт на невидимом сиденье", 20, 60);

    final String id, legacyId, config, legacyConfig, title, description;
    final int duration, cooldown;
    FoodType(String id, String legacyId, String legacyConfig, String title, String description, int duration, int cooldown) {
        this.id = id; this.legacyId = legacyId; this.config = "foods." + id + ".";
        this.legacyConfig = "foods." + legacyConfig + "."; this.title = title;
        this.description = description; this.duration = duration; this.cooldown = cooldown;
    }
    NamespacedKey key() { return new NamespacedKey("fartmc", id); }
    NamespacedKey legacyKey() { return new NamespacedKey("fartmc", legacyId); }
    static FoodType parse(String value) {
        if (value == null) return null;
        String id = value.toLowerCase(Locale.ROOT);
        for (FoodType type : values()) if (type.id.equals(id) || type.legacyId.equals(id)) return type;
        return null;
    }
}
