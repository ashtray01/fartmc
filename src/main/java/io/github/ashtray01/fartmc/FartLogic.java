package io.github.ashtray01.fartmc;

import java.util.*;
import java.util.random.RandomGenerator;

/** Pure policy, independent of the server clock and Bukkit. */
final class FartLogic {
    enum Style {
        CLASSIC("entity.pig.ambient", .65f, .6f, 2),
        WHISPER("block.fire.extinguish", .14f, .55f, 1),
        BUBBLES("block.bubble_column.bubble_pop", .65f, .65f, 5),
        TRUMPET("block.note_block.didgeridoo", .7f, .6f, 3),
        RATTLE("entity.slime.squish_small", .55f, .65f, 6),
        THUNDER("entity.ravager.ambient", .45f, .5f, 2),
        SQUEAK("entity.pig.ambient", .45f, 1.8f, 3);

        final String sound;
        final float volume, pitch;
        final int pulses;
        Style(String sound, float volume, float pitch, int pulses) {
            this.sound = sound; this.volume = volume; this.pitch = pitch; this.pulses = pulses;
        }
        static Style parse(String value) {
            try { return valueOf(value.toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException ex) { return null; }
        }
    }

    private final Map<UUID, Long> manual = new HashMap<>(), automatic = new HashMap<>();
    private final Map<UUID, Style> last = new HashMap<>();

    boolean acquire(UUID id, long now, boolean auto, long generalDelay, long autoDelay) {
        if (now < manual.getOrDefault(id, Long.MIN_VALUE)) return false;
        if (auto && now < automatic.getOrDefault(id, Long.MIN_VALUE)) return false;
        manual.put(id, now + Math.max(0, generalDelay));
        if (auto) automatic.put(id, now + Math.max(generalDelay, autoDelay));
        return true;
    }

    Style choose(UUID id, RandomGenerator random) {
        Style previous = last.get(id);
        List<Style> candidates = new ArrayList<>(List.of(Style.values()));
        candidates.remove(previous);
        Style next = candidates.get(random.nextInt(candidates.size()));
        last.put(id, next);
        return next;
    }

    void forget(UUID id) { manual.remove(id); automatic.remove(id); last.remove(id); }
}
