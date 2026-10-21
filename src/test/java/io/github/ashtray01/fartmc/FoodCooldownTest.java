package io.github.ashtray01.fartmc;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FoodCooldownTest {
    @Test void countdownRoundsUpAndEndsExactlyAtDeadline() {
        assertEquals(2, FoodCooldown.seconds(2001, 1000));
        assertEquals(1, FoodCooldown.seconds(2000, 1000));
        assertEquals(1, FoodCooldown.seconds(2000, 1999));
        assertEquals(0, FoodCooldown.seconds(2000, 2000));
        assertEquals(0, FoodCooldown.seconds(2000, 2001));
    }
    @Test void milkCannotMakeAnActiveEffectReadyEarly() {
        assertEquals(45, FoodCooldown.waitingSeconds(45000, 0, 240));
        assertEquals(12, FoodCooldown.waitingSeconds(0, 1000, 240));
        assertEquals(1, FoodCooldown.waitingSeconds(0, 1000, 1));
        assertEquals(0, FoodCooldown.waitingSeconds(0, 1000, 0));
    }
}
