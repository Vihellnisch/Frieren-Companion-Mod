package com.frierenmod.entity.ai;

import com.frierenmod.entity.FrierenEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

public class FrierenFlowerGoal extends Goal {

    private static final int SCAN_RADIUS = 8;

    private final FrierenEntity frieren;
    @Nullable
    private BlockPos targetFlower;
    private int cooldown;

    public FrierenFlowerGoal(FrierenEntity frieren) {
        this.frieren = frieren;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (cooldown-- > 0) return false;
        if (frieren.isSleeping() || frieren.isStaying()) return false;
        if (frieren.getStage().ordinal() < FrierenEntity.Stage.FRIEND.ordinal()) return false;
        if (frieren.getTarget() != null) return false;
        targetFlower = findNearbyFlower();
        return targetFlower != null;
    }

    @Override
    public boolean canContinueToUse() {
        if (targetFlower == null) return false;
        return frieren.level().getBlockState(targetFlower).is(BlockTags.SMALL_FLOWERS);
    }

    @Override
    public void start() {
        if (targetFlower != null) {
            frieren.getNavigation().moveTo(
                    targetFlower.getX() + 0.5, targetFlower.getY(), targetFlower.getZ() + 0.5, 0.7);
        }
    }

    @Override
    public void tick() {
        if (targetFlower == null) return;
        Vec3 center = Vec3.atCenterOf(targetFlower);
        if (frieren.distanceToSqr(center) < 3.5) {
            Level level = frieren.level();
            BlockState state = level.getBlockState(targetFlower);
            if (state.is(BlockTags.SMALL_FLOWERS)) {
                ItemStack flower = new ItemStack(state.getBlock().asItem());
                if (!flower.isEmpty()) {
                    SimpleContainer bag = frieren.getBag();
                    ItemStack leftover = bag.addItem(flower);
                    if (!leftover.isEmpty()) {
                        frieren.spawnAtLocation(leftover);
                    }
                }
                level.setBlock(targetFlower, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
                level.playSound(null, targetFlower, SoundEvents.GRASS_BREAK, SoundSource.BLOCKS, 0.4f, 1.3f);
            }
            targetFlower = null;
            cooldown = 200 + frieren.getRandom().nextInt(200);
        }
    }

    @Override
    public void stop() {
        targetFlower = null;
        frieren.getNavigation().stop();
        cooldown = 300;
    }

    @Nullable
    private BlockPos findNearbyFlower() {
        BlockPos center = frieren.blockPosition();
        Level level = frieren.level();
        BlockPos nearest = null;
        double nearestDist = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(
                center.offset(-SCAN_RADIUS, -2, -SCAN_RADIUS),
                center.offset(SCAN_RADIUS, 2, SCAN_RADIUS))) {
            if (level.getBlockState(pos).is(BlockTags.SMALL_FLOWERS)) {
                double dist = frieren.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                if (dist < nearestDist) {
                    nearestDist = dist;
                    nearest = pos.immutable();
                }
            }
        }
        return nearest;
    }
}
