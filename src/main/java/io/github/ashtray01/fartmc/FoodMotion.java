package io.github.ashtray01.fartmc;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

final class FoodMotion {
    private FoodMotion() {}
    static ArmorStand seat(Location location, Entity passenger) {
        ArmorStand seat = location.getWorld().spawn(location, ArmorStand.class, stand -> {
            stand.setVisible(false); stand.setSmall(true); stand.setMarker(false);
            stand.setGravity(true); stand.setInvulnerable(true); stand.setPersistent(false);
            stand.setCollidable(false); stand.setSilent(true); stand.setBasePlate(false);
            stand.setCanMove(true); stand.setCanTick(true);
            stand.addScoreboardTag("fartmc_food_seat");
            stand.setDisabledSlots(org.bukkit.inventory.EquipmentSlot.values());
        });
        if (!seat.addPassenger(passenger)) { seat.remove(); throw new IllegalStateException("Seat mount was cancelled"); }
        return seat;
    }
    static RayTraceResult ground(Location from, double distance) {
        if (!loaded(from)) return null;
        Vector down = new Vector(0, -1, 0);
        World world = from.getWorld();
        RayTraceResult hit = world.rayTraceBlocks(from, down, distance, FluidCollisionMode.ALWAYS, false);
        if (hit == null || hit.getHitBlock() == null || !flame(hit.getHitBlock())) return hit;
        // Fire has a ray-trace outline but is not the supporting floor. Ignore only
        // flames: globally ignoring passable blocks would also skip fluid hazards.
        int bottom = Math.max(world.getMinHeight(), (int)Math.floor(from.getY() - distance));
        for (int y = hit.getHitBlock().getY() - 1; y >= bottom; y--) {
            Block block = world.getBlockAt(from.getBlockX(), y, from.getBlockZ());
            if (flame(block) || block.getType().isAir()) continue;
            RayTraceResult below = block.rayTrace(from, down, distance, FluidCollisionMode.ALWAYS);
            if (below != null) return below;
        }
        return null;
    }
    private static boolean flame(Block block) { return block.getType() == Material.FIRE || block.getType() == Material.SOUL_FIRE; }
    static boolean safeGround(RayTraceResult hit) {
        if (hit == null || hit.getHitBlock() == null) return false;
        Block block = hit.getHitBlock();
        return !block.isLiquid() && block.getType().isSolid()
                && block.getType() != Material.MAGMA_BLOCK && block.getType() != Material.CAMPFIRE
                && block.getType() != Material.SOUL_CAMPFIRE && block.getType() != Material.CACTUS;
    }
    static boolean loaded(Location location) {
        return location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4);
    }
    static boolean clear(World world, BoundingBox box) {
        if (box.getMinY() < world.getMinHeight() || box.getMaxY() >= world.getMaxHeight()) return false;
        for (double x : new double[]{box.getMinX(), box.getMaxX()}) {
            for (double z : new double[]{box.getMinZ(), box.getMaxZ()}) {
                Location point = new Location(world, x, box.getMinY(), z);
                if (!loaded(point) || !world.getWorldBorder().isInside(point)) return false;
            }
        }
        for (int x = (int)Math.floor(box.getMinX()); x <= Math.floor(box.getMaxX()); x++)
            for (int y = (int)Math.floor(box.getMinY()); y <= Math.floor(box.getMaxY()); y++)
                for (int z = (int)Math.floor(box.getMinZ()); z <= Math.floor(box.getMaxZ()); z++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (!block.isPassable() && block.getBoundingBox().overlaps(box)) return false;
                }
        return true;
    }
    static void velocity(Player player, Entity mover, Vector desired) {
        if (!Double.isFinite(desired.lengthSquared())) return;
        // Sweep the rider's full standing body as well as the seat; be conservative at corners.
        BoundingBox body = player.getBoundingBox().clone().expand(-.02);
        if (mover != player) body.union(mover.getBoundingBox().clone().expand(-.01));
        Vector accepted = new Vector();
        for (Vector axis : new Vector[]{new Vector(0, desired.getY(), 0), new Vector(desired.getX(), 0, 0), new Vector(0, 0, desired.getZ())}) {
            BoundingBox sweep = body.clone().union(body.clone().shift(axis));
            if (clear(player.getWorld(), sweep)) { body.shift(axis); accepted.add(axis); }
        }
        mover.setVelocity(accepted);
    }
}
