package com.vicmod.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class VicSpawnUtil {

    /**
     * Finds a safe, clear spawn location in front of or near the player.
     * Searches multiple angles and distances, detects the actual solid ground level,
     * ensures 2 blocks of clear vertical headroom, and clears any soft clutter (grass, leaves, snow).
     *
     * @param level Server level
     * @param player Target player
     * @param preferredDistance Ideal distance in front of player (e.g. 2.5 blocks)
     * @return Safe spawn coordinates
     */
    public static Vec3 findClearSpawnPosition(ServerLevel level, ServerPlayer player, double preferredDistance) {
        Vec3 playerPos = player.position();
        float yaw = player.getYRot();
        double radians = Math.toRadians(yaw);

        // Candidate angles to check: straight ahead first, then slight angles, then sides
        double[] angleOffsets = {0.0, 25.0, -25.0, 45.0, -45.0, 70.0, -70.0, 90.0, -90.0, 180.0};
        double[] distances = {preferredDistance, preferredDistance - 0.5, preferredDistance + 0.8};

        BlockPos bestPos = null;
        Vec3 bestVec = null;

        searchLoop:
        for (double dist : distances) {
            for (double angleDeg : angleOffsets) {
                double rad = Math.toRadians(yaw + angleDeg);
                // In Minecraft: -sin(yaw) is X, cos(yaw) is Z
                double targetX = playerPos.x - Math.sin(rad) * dist;
                double targetZ = playerPos.z + Math.cos(rad) * dist;

                int checkX = (int) Math.floor(targetX);
                int checkZ = (int) Math.floor(targetZ);
                int playerY = player.getBlockY();

                // Search vertically around player Y for solid ground
                for (int dy = 2; dy >= -3; dy--) {
                    BlockPos groundPos = new BlockPos(checkX, playerY + dy, checkZ);
                    BlockPos feetPos = groundPos.above();
                    BlockPos headPos = feetPos.above();

                    BlockState groundState = level.getBlockState(groundPos);
                    BlockState feetState = level.getBlockState(feetPos);
                    BlockState headState = level.getBlockState(headPos);

                    // Ground must be solid/supportive and not hazardous (not air, water, lava, fire)
                    if (isSolidGround(level, groundPos, groundState)) {
                        // Feet and head must be clear or soft replaceable clutter
                        if (isClearOrSoftClutter(feetState) && isClearOrSoftClutter(headState)) {
                            bestPos = feetPos;
                            bestVec = new Vec3(checkX + 0.5, feetPos.getY(), checkZ + 0.5);
                            break searchLoop;
                        }
                    }
                }
            }
        }

        // If no ideal spot was found, default to direct front at player Y
        if (bestPos == null) {
            double defaultX = playerPos.x - Math.sin(radians) * preferredDistance;
            double defaultZ = playerPos.z + Math.cos(radians) * preferredDistance;
            bestPos = new BlockPos((int) Math.floor(defaultX), player.getBlockY(), (int) Math.floor(defaultZ));
            bestVec = new Vec3(defaultX, player.getY(), defaultZ);
        }

        // Clean up the 2x2 spawn area so Vic / box is completely clear of leaves, grass, snow
        clearSpawnArea(level, bestPos);

        return bestVec;
    }

    /**
     * Clears obstructions and soft foliage (tall grass, leaves, snow layers, vines, flowers)
     * at the spawn location to guarantee an open, unobstructed clearing.
     */
    public static void clearSpawnArea(ServerLevel level, BlockPos centerFeet) {
        // Clear 2-block vertical column at center
        clearIfSoft(level, centerFeet);
        clearIfSoft(level, centerFeet.above());

        // Also clear immediate 1-block radius horizontal soft clutter (e.g. tall grass / leaves)
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos adjacent = centerFeet.relative(dir);
            clearIfSoft(level, adjacent);
            clearIfSoft(level, adjacent.above());
        }
    }

    /**
     * Checks if ground is solid and non-hazardous.
     */
    private static boolean isSolidGround(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.isAir()) return false;
        if (state.is(Blocks.LAVA) || state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)) return false;
        if (state.is(Blocks.WATER)) return false;
        return !state.getCollisionShape(level, pos).isEmpty();
    }

    /**
     * Checks if block is air or soft destructible clutter (grass, flowers, snow, leaves, vines).
     */
    private static boolean isClearOrSoftClutter(BlockState state) {
        if (state.isAir()) return true;
        if (state.canBeReplaced()) return true;
        if (state.is(BlockTags.LEAVES)) return true;
        if (state.is(Blocks.SHORT_GRASS) || state.is(Blocks.TALL_GRASS)
                || state.is(Blocks.FERN) || state.is(Blocks.LARGE_FERN)
                || state.is(Blocks.DEAD_BUSH) || state.is(Blocks.VINE)
                || state.is(Blocks.SNOW)) {
            return true;
        }
        return false;
    }

    /**
     * Replaces soft clutter with air.
     */
    private static void clearIfSoft(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!state.isAir() && isClearOrSoftClutter(state)) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        }
    }
}
