package io.github.ashtray01.fartmc;

import org.junit.jupiter.api.Test;
import org.bukkit.util.Vector;
import org.bukkit.configuration.file.YamlConfiguration;
import static org.junit.jupiter.api.Assertions.*;

class FoodPhysicsTest {
    @Test void externalDamageIsNotRefundedAndDeadPlayersStayDead() {
        double credit = FoodPhysics.withheld(18, 20, .5);
        assertEquals(8, credit);
        assertEquals(12, FoodPhysics.restored(4, credit, 18, 20));
        assertEquals(0, FoodPhysics.restored(0, credit, 18, 20));
        assertEquals(0, FoodPhysics.withheld(6, 20, .5));
        assertEquals(12, FoodPhysics.restored(10, credit, 18, 12));
        assertEquals(20, FoodPhysics.restored(20, credit, 18, 20)); // Don't undo external healing.
    }
    @Test void accelerationAndTurningAreBoundedAndPreserveVerticalPhysics() {
        Vector current = new Vector(0, -.3, 0);
        for (int tick = 0; tick < 500; tick++) {
            Vector next = FoodPhysics.accelerate(current, FoodPhysics.forward(tick < 250 ? 0 : 180).multiply(.65), .04, .65);
            assertTrue(next.clone().setY(0).length() <= .6500001);
            assertTrue(next.clone().subtract(current).length() <= .0400001);
            assertEquals(-.3, next.getY());
            current = next;
        }
        assertEquals(-.65, current.getZ(), .000001);
    }
    @Test void configurationRejectsNonFiniteValuesAndBoundsUnsafeSettings() {
        YamlConfiguration c = new YamlConfiguration();
        c.set("foods.khacheburek.max-horizontal-speed", Double.POSITIVE_INFINITY);
        c.set("foods.khacheburek.acceleration", -10);
        c.set("foods.khacheburek.health-percent", Double.NaN);
        c.set("foods.belyash.duration-seconds", -20);
        c.set("foods.belyash.accident-chance", 9);
        c.set("foods.poisoned-cheburek.maximum-hover-height", 1000);
        c.set("foods.poisoned-cheburek.target-hover-height", 30);
        var water = FoodSettings.read(c, FoodType.KHACHEBUREK);
        assertEquals(.65, water.maxSpeed()); assertEquals(.001, water.acceleration()); assertEquals(.5, water.healthPercent());
        var belyash = FoodSettings.read(c, FoodType.BELYASH);
        assertEquals(20, belyash.duration()); assertEquals(1, belyash.accidentChance());
        var fire = FoodSettings.read(c, FoodType.POISONED_CHEBUREK);
        assertEquals(7, fire.maxHover()); assertEquals(7, fire.hover());
        c.set("foods.poisoned-cheburek.maximum-hover-height", 1);
        c.set("foods.poisoned-cheburek.target-hover-height", Double.NaN);
        assertEquals(1, FoodSettings.read(c, FoodType.POISONED_CHEBUREK).hover());
    }
}
