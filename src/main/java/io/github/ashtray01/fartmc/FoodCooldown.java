package io.github.ashtray01.fartmc;

/** The UI and consumption gate share the same rounded-up, non-negative countdown. */
final class FoodCooldown {
    private FoodCooldown() {}
    static long seconds(long deadline, long now) {
        if (deadline <= now) return 0;
        long milliseconds = deadline - now;
        return milliseconds / 1000 + (milliseconds % 1000 == 0 ? 0 : 1);
    }
    static long waitingSeconds(long deadline, long now, int effectTicks) {
        return Math.max(seconds(deadline, now), (Math.max(0, effectTicks) + 19L) / 20);
    }
}
