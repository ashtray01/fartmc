package io.github.ashtray01.fartmc;

import org.bukkit.NamespacedKey;
import java.util.Locale;

enum FoodType {
    BELYASH("belyash_station", "belyash", "Беляш с вокзала", "Газы, отравление и неожиданные пятна", 30, 45),
    KHACHEBUREK("khacheburek", "khacheburek", "Хачебурек", "Водяная тяга и временная потеря здоровья", 12, 45),
    POISONED_CHEBUREK("poisoned_cheburek", "poisoned-cheburek", "Отравленный чебурек", "Огненный полёт на невидимом сиденье", 15, 60);

    final String id, config, title, description;
    final int duration, cooldown;
    FoodType(String id, String config, String title, String description, int duration, int cooldown) {
        this.id = id; this.config = "foods." + config + "."; this.title = title;
        this.description = description; this.duration = duration; this.cooldown = cooldown;
    }
    NamespacedKey key() { return new NamespacedKey("fartmc", id); }
    static FoodType parse(String value) {
        if (value == null) return null;
        String id = value.toLowerCase(Locale.ROOT);
        if (id.equals("belyash")) return BELYASH;
        for (FoodType type : values()) if (type.id.equals(id)) return type;
        return null;
    }
}
