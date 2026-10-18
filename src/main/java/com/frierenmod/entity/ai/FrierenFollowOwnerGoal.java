package com.frierenmod.entity.ai;

import com.frierenmod.entity.FrierenEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;

import java.util.EnumSet;

/**
 * Makes Frieren follow her bound owner once they are FRIEND or above (and not staying).
 *
 * Follow logic:
 *   - distance > startDistance: pathfind toward the owner at speedModifier
 *   - distance <= stopDistance: stop pathfinding (close enough)
 *   - distance > TELEPORT_DISTANCE (24 blocks): teleport to a safe spot near the owner
 *   - path is recalculated every 10 ticks for performance
 */
public class FrierenFollowOwnerGoal extends Goal {
    private static final float TELEPORT_DISTANCE = 24.0F;

    private final FrierenEntity frieren;
    private final double speedModifier;
    private final float startDistance;
    private final float stopDistance;
    private Player owner;
    private final PathNavigation navigation;
    private int timeToRecalcPath;

    public FrierenFollowOwnerGoal(FrierenEntity frieren, double speedModifier, float stopDistance, float startDistance) {
        this.frieren = frieren;
        this.speedModifier = speedModifier;
        this.stopDistance = stopDistance;
        this.startDistance = startDistance;
        this.navigation = frieren.getNavigation();
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (frieren.isStaying()) return false;
        if (frieren.getStage().ordinal() < FrierenEntity.Stage.FRIEND.ordinal()) return false;
        Player candidate = frieren.getOwner();
        if (candidate == null || candidate.isSpectator()) return false;
        if (frieren.distanceToSqr(candidate) < (double) (this.startDistance * this.startDistance)) return false;
        this.owner = candidate;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (frieren.isStaying()) return false;
        if (owner == null || owner.isSpectator()) return false;
        // Keep following until we're within the stop distance.
        return frieren.distanceToSqr(owner) > (double) (this.stopDistance * this.stopDistance);
    }

    @Override
    public void start() {
        this.timeToRecalcPath = 0;
    }

    @Override
    public void stop() {
        this.owner = null;
        this.navigation.stop();
    }

    @Override
    public void tick() {
        if (owner == null) return;
        frieren.getLookControl().setLookAt(owner, 10.0F, (float) frieren.getMaxHeadXRot());

        if (--this.timeToRecalcPath > 0) {
            return;
        }
        this.timeToRecalcPath = this.adjustedTickDelay(10);

        double distSqr = frieren.distanceToSqr(owner);
        if (distSqr > (double) (TELEPORT_DISTANCE * TELEPORT_DISTANCE)) {
            // Only teleport as a last resort for extreme distances.
            tryTeleportToOwner();
        } else {
            // Proper pathfinding toward the owner.
            this.navigation.moveTo(owner, this.speedModifier);
        }
    }

    private void tryTeleportToOwner() {
        BlockPos ownerPos = owner.blockPosition();
        // Search a small grid around the owner for a safe landing spot.
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (dx == 0 && dz == 0) continue;
                BlockPos candidate = ownerPos.offset(dx, 0, dz);
                if (isSafeSpot(candidate)) {
                    frieren.moveTo(candidate.getX() + 0.5D, candidate.getY(), candidate.getZ() + 0.5D,
                            frieren.getYRot(), frieren.getXRot());
                    this.navigation.stop();
                    return;
                }
            }
        }
        // Fallback: teleport directly onto the owner's position.
        frieren.moveTo(owner.getX(), owner.getY(), owner.getZ(), frieren.getYRot(), frieren.getXRot());
        this.navigation.stop();
    }

    private boolean isSafeSpot(BlockPos pos) {
        LevelReader level = frieren.level();
        PathType type = WalkNodeEvaluator.getPathTypeStatic(frieren, pos);
        if (type != PathType.WALKABLE) {
            return false;
        }
        // Ensure the block below is solid enough to stand on.
        return level.getBlockState(pos.below()).isCollisionShapeFullBlock(level, pos.below());
    }
}
