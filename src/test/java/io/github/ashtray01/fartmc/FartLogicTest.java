package io.github.ashtray01.fartmc;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class FartLogicTest {
    @Test void automaticEventsCannotBypassManualCooldownAndHaveTheirOwnBudget() {
        FartLogic logic = new FartLogic();
        UUID player = UUID.randomUUID();
        assertTrue(logic.acquire(player, 0, true, 4000, 35000));
        assertFalse(logic.acquire(player, 3999, false, 4000, 35000));
        assertTrue(logic.acquire(player, 4000, false, 4000, 35000));
        assertFalse(logic.acquire(player, 34999, true, 4000, 35000));
        assertTrue(logic.acquire(player, 35000, true, 4000, 35000));
    }
    @Test void playersAreIndependentAndQuitClearsState() {
        FartLogic logic = new FartLogic();
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        assertTrue(logic.acquire(a, 100, true, 4000, 35000));
        assertTrue(logic.acquire(b, 100, true, 4000, 35000));
        logic.forget(a);
        assertTrue(logic.acquire(a, 101, true, 4000, 35000));
        assertFalse(logic.acquire(b, 101, true, 4000, 35000));
    }
    @Test void randomStylesNeverRepeatConsecutivelyAndCoverAllProfiles() {
        FartLogic logic = new FartLogic();
        UUID player = UUID.randomUUID();
        Random random = new Random(42);
        Set<FartLogic.Style> seen = new HashSet<>();
        FartLogic.Style previous = null;
        for (int i = 0; i < 1000; i++) {
            var style = logic.choose(player, random);
            assertNotEquals(previous, style);
            seen.add(style); previous = style;
        }
        assertEquals(7, seen.size());
    }
    @Test void styleParsingIsCaseInsensitiveAndRejectsUnknownInput() {
        assertEquals(FartLogic.Style.WHISPER, FartLogic.Style.parse("WhIsPeR"));
        assertNull(FartLogic.Style.parse("unknown"));
    }
}
