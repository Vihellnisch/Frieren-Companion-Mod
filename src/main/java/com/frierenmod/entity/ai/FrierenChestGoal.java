package com.frierenmod.entity.ai;

import com.frierenmod.FrierenConfig;
import com.frierenmod.entity.FrierenEntity;
import com.frierenmod.entity.MimicEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;

/**
 * Frieren can't walk past a chest. Now and then she dives head-first into a nearby chest, rummages
 * with her legs in the air and climbs back out. A hidden Mimic near her owner is irresistible: she
 * goes straight for it, and the Mimic takes over from there (see {@link MimicEntity}).
 */
public class FrierenChestGoal extends Goal {
    private static final int CHEST_RADIUS = 8;
    private static final int MIMIC_RADIUS = 12;
    private static final int WALK_TIMEOUT = 200;

    private static final String[] FOUND_LINES = {
            "A treasure chest!",
            "Ooh. A chest.",
            "...There might be a grimoire in there."
    };
    private static final String[] DONE_LINES = {
            "...Nothing but junk. Still, checking is half the fun.",
            "No grimoires. Hmph.",
            "*rummage rummage* ...Hm. Just old socks.",
            "Not a mimic, at least. Not a grimoire either."
    };

    private final FrierenEntity frieren;
    private int checkDelay;
    private int cooldown = 600;

    @Nullable
    private BlockPos chestPos;
    @Nullable
    private MimicEntity mimic;
    private boolean diving;
    private boolean done;
    private int ticks;
    private int diveLength;
    @Nullable
    private Vec3 standPos;

    public FrierenChestGoal(FrierenEntity frieren) {
        this.frieren = frieren;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        // canUse doesn't run every tick, so count calls rather than reading tickCount.
        if (cooldown > 0) cooldown--;
        if (--checkDelay > 0) return false;
        checkDelay = 10;
        if (frieren.isSleeping() || frieren.isStaying() || frieren.isInChest() || frieren.getTarget() != null) return false;
        if (frieren.getStage().ordinal() < FrierenEntity.Stage.FRIEND.ordinal()) return false;
        if (!frieren.onGround()) return false;

        mimic = findMimic();
        if (mimic != null && mimic.claim(frieren)) {
            chestPos = null;
            return true;
        }
        mimic = null;

        if (cooldown > 0 || !FrierenConfig.CHEST_CURIOSITY.get()) return false;
        if (frieren.getRandom().nextInt(6) != 0) return false; // she's curious, not obsessed
        chestPos = findChest();
        if (chestPos == null) {
            cooldown = 100;
            return false;
        }
        return true;
    }

    @Nullable
    private MimicEntity findMimic() {
        Player owner = frieren.getOwner();
        if (owner == null) return null;
        List<MimicEntity> found = frieren.level().getEntitiesOfClass(MimicEntity.class,
                frieren.getBoundingBox().inflate(MIMIC_RADIUS),
                m -> m.isHidden() && !m.isClaimedByOther(frieren) && m.distanceToSqr(owner) < 16.0D * 16.0D);
        MimicEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (MimicEntity m : found) {
            double d = m.distanceToSqr(frieren);
            if (d < bestDist) {
                bestDist = d;
                best = m;
            }
        }
        return best;
    }

    @Nullable
    private BlockPos findChest() {
        Level level = frieren.level();
        BlockPos center = frieren.blockPosition();
        BlockPos nearest = null;
        double nearestDist = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-CHEST_RADIUS, -2, -CHEST_RADIUS),
                center.offset(CHEST_RADIUS, 2, CHEST_RADIUS))) {
            BlockState state = level.getBlockState(pos);
            if (!isChest(state) || !level.getBlockState(pos.above()).isAir()) continue;
            double d = frieren.distanceToSqr(Vec3.atCenterOf(pos));
            if (d < nearestDist) {
                nearestDist = d;
                nearest = pos.immutable();
            }
        }
        return nearest;
    }

    private static boolean isChest(BlockState state) {
        return state.getBlock() instanceof ChestBlock || state.getBlock() instanceof EnderChestBlock;
    }

    @Override
    public void start() {
        diving = false;
        done = false;
        ticks = 0;
        if (mimic != null || frieren.getRandom().nextBoolean()) {
            frieren.sayNearby(FOUND_LINES[frieren.getRandom().nextInt(FOUND_LINES.length)], ChatFormatting.AQUA);
        }
        walk();
    }

    private void walk() {
        Vec3 t = targetCenter();
        if (t != null) frieren.getNavigation().moveTo(t.x, t.y, t.z, mimic != null ? 1.25D : 0.9D);
    }

    @Nullable
    private Vec3 targetCenter() {
        if (mimic != null) return mimic.position();
        if (chestPos == null) return null;
        Vec3 c = Vec3.atBottomCenterOf(chestPos);
        BlockState state = frieren.level().getBlockState(chestPos);
        if (state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
            // Double chest: dive into the middle.
            BlockPos other = chestPos.relative(ChestBlock.getConnectedDirection(state));
            c = c.add(Vec3.atBottomCenterOf(other)).scale(0.5D);
        }
        return c;
    }

    @Override
    public boolean canContinueToUse() {
        if (done) return false;
        if (diving) return mimic != null ? frieren.isInChest() : true;
        if (mimic != null) return mimic.isAlive() && mimic.isHidden();
        return chestPos != null && isChest(frieren.level().getBlockState(chestPos));
    }

    @Override
    public boolean isInterruptable() {
        return !diving; // half in a chest, she can't just walk off
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        ticks++;
        if (!diving) {
            tickWalk();
        } else if (mimic == null) {
            tickChestDive();
        } else if (!frieren.isInChest()) {
            done = true; // the Mimic spat her out
        }
    }

    private void tickWalk() {
        Vec3 t = targetCenter();
        if (t == null) {
            done = true;
            return;
        }
        frieren.getLookControl().setLookAt(t.x, t.y + 0.5D, t.z);
        double dx = frieren.getX() - t.x, dz = frieren.getZ() - t.z;
        if (dx * dx + dz * dz < 1.6D * 1.6D && Math.abs(frieren.getY() - t.y) < 1.5D) {
            standPos = frieren.position();
            if (mimic != null) {
                if (!mimic.beginFeeding(frieren)) done = true;
            } else {
                startChestDive(t);
            }
            diving = !done;
            ticks = 0;
            return;
        }
        if (ticks % 20 == 0) walk();
        if (ticks > WALK_TIMEOUT) done = true;
    }

    private void startChestDive(Vec3 center) {
        BlockState state = frieren.level().getBlockState(chestPos);
        float yaw = state.hasProperty(ChestBlock.FACING) ? state.getValue(ChestBlock.FACING).toYRot() : frieren.getYRot();
        setLid(true);
        frieren.enterChest(center.add(0.0D, 10.0D / 16.0D, 0.0D), yaw, null);
        diveLength = 80 + frieren.getRandom().nextInt(60);
    }

    private void tickChestDive() {
        if (chestPos == null || !isChest(frieren.level().getBlockState(chestPos))) {
            climbOut(null); // the chest is gone
            return;
        }
        if (ticks % 15 == 0) {
            frieren.level().playSound(null, chestPos, SoundEvents.BUNDLE_INSERT, SoundSource.NEUTRAL,
                    0.6F, 0.8F + frieren.getRandom().nextFloat() * 0.4F);
        }
        if (frieren.consumePullOutRequest()) {
            climbOut("...Fine, fine. I'm coming out.");
        } else if (ticks >= diveLength) {
            climbOut(frieren.getRandom().nextInt(3) == 0 ? null
                    : DONE_LINES[frieren.getRandom().nextInt(DONE_LINES.length)]);
        }
    }

    private void climbOut(@Nullable String line) {
        setLid(false);
        Vec3 exit = standPos != null ? standPos : frieren.position().add(0.0D, 0.6D, 0.0D);
        frieren.leaveChest(exit, new Vec3(0.0D, 0.25D, 0.0D));
        if (line != null) frieren.sayNearby(line, ChatFormatting.AQUA);
        done = true;
    }

    /** Opens or closes the lid the way a player would, on top of anyone else looking inside. */
    private void setLid(boolean open) {
        if (chestPos == null) return;
        Level level = frieren.level();
        BlockState state = level.getBlockState(chestPos);
        if (!isChest(state)) return;
        int others = state.getBlock() instanceof ChestBlock ? ChestBlockEntity.getOpenCount(level, chestPos) : 0;
        level.blockEvent(chestPos, state.getBlock(), 1, others + (open ? 1 : 0));
        boolean ender = state.getBlock() instanceof EnderChestBlock;
        level.playSound(null, chestPos,
                open ? (ender ? SoundEvents.ENDER_CHEST_OPEN : SoundEvents.CHEST_OPEN)
                        : (ender ? SoundEvents.ENDER_CHEST_CLOSE : SoundEvents.CHEST_CLOSE),
                SoundSource.BLOCKS, 0.5F, 0.9F + frieren.getRandom().nextFloat() * 0.1F);
    }

    @Override
    public void stop() {
        if (diving && mimic == null && frieren.isInChest()) climbOut(null);
        if (mimic != null && !diving) mimic.releaseClaim(frieren);
        frieren.getNavigation().stop();
        // canUse runs about every other tick, so 600-1800 calls is roughly one to three minutes
        if (mimic == null) cooldown = 600 + frieren.getRandom().nextInt(1200);
        mimic = null;
        chestPos = null;
        diving = false;
        standPos = null;
    }
}
