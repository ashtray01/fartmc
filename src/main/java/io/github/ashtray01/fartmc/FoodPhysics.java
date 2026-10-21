package io.github.ashtray01.fartmc;

import org.bukkit.util.Vector;

final class FoodPhysics {
    private FoodPhysics() {}
    static Vector forward(float yaw) {
        double angle = Math.toRadians(yaw);
        return new Vector(-Math.sin(angle), 0, Math.cos(angle));
    }
    static Vector accelerate(Vector current, Vector target, double acceleration, double maximum) {
        Vector horizontal = current.clone().setY(0);
        Vector change = target.clone().setY(0).subtract(horizontal);
        if (change.lengthSquared() > acceleration * acceleration) change.normalize().multiply(acceleration);
        horizontal.add(change);
        if (horizontal.lengthSquared() > maximum * maximum) horizontal.normalize().multiply(maximum);
        return horizontal.setY(current.getY());
    }
    static double withheld(double health, double maximum, double fraction) {
        return Math.max(0, health - maximum * fraction);
    }
    static double restored(double current, double withheld, double original, double maximum) {
        if (current <= 0) return 0;
        // Never undo healing received during the session, nor restore external damage.
        return Math.max(current, Math.min(Math.min(original, maximum), current + withheld));
    }
}
