package com.frierenmod.spell;

import com.frierenmod.entity.FrierenEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * Transmutes ordinary terrain into gold. Frieren can undo the transmutation after fifteen
 * seconds, restoring the exact block states that existed before the cast.
 */
public final class MidasSpell {
    private static final int RADIUS = 5;
    private static final int RESTORE_DELAY = 15 * 20;
    private static final List<ActiveMidas> ACTIVE = new ArrayList<>();

    private MidasSpell() {}

    public static boolean cast(ServerLevel level, net.minecraft.world.entity.player.Player player) {
        BlockPos center = player.blockPosition();
        List<ChangedBlock> changed = new ArrayList<>();
        int radiusSq = RADIUS * RADIUS;

        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-RADIUS, -RADIUS, -RADIUS),
                center.offset(RADIUS, RADIUS, RADIUS))) {
            if (pos.distSqr(center) > radiusSq) continue;
            BlockState state = level.getBlockState(pos);
            if (!canTransmute(level, pos, state)) continue;
            changed.add(new ChangedBlock(pos.immutable(), state));
            level.setBlock(pos, Blocks.GOLD_BLOCK.defaultBlockState(), 3);
        }

        if (changed.isEmpty()) return false;
        ACTIVE.add(new ActiveMidas(level.dimension(), player.getUUID(), center.immutable(), level.getGameTime() + RESTORE_DELAY, changed, false));
        level.playSound(null, center, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1.0F, 0.65F);
        level.sendParticles(ParticleTypes.WAX_ON, player.getX(), player.getY() + 1.0D, player.getZ(),
                80, RADIUS, RADIUS * 0.5D, RADIUS, 0.1D);
        return true;
    }

    /** Golden Zoltraak: gilds the blocks around a hit for fifteen seconds, then always turns them back. */
    public static void transmuteTemporary(ServerLevel level, @org.jetbrains.annotations.Nullable UUID caster, BlockPos center, int radius) {
        List<ChangedBlock> changed = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -radius, -radius), center.offset(radius, radius, radius))) {
            if (pos.distSqr(center) > radius * radius + 1) continue;
            BlockState state = level.getBlockState(pos);
            if (!canTransmute(level, pos, state)) continue;
            changed.add(new ChangedBlock(pos.immutable(), state));
            level.setBlock(pos, Blocks.GOLD_BLOCK.defaultBlockState(), 3);
        }
        if (changed.isEmpty()) return;
        ACTIVE.add(new ActiveMidas(level.dimension(), caster, center.immutable(), level.getGameTime() + RESTORE_DELAY, changed, true));
        level.playSound(null, center, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 1.2F);
        level.sendParticles(ParticleTypes.WAX_ON, center.getX() + 0.5D, center.getY() + 0.5D, center.getZ() + 0.5D,
                30, radius, radius, radius, 0.1D);
    }

    private static boolean canTransmute(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.isAir() || state.is(Blocks.GOLD_BLOCK) || state.is(Blocks.BEDROCK)
                || state.is(Blocks.END_PORTAL_FRAME) || state.is(Blocks.END_PORTAL)
                || state.is(Blocks.NETHER_PORTAL) || !state.getFluidState().isEmpty()) return false;
        BlockEntity blockEntity = level.getBlockEntity(pos);
        return blockEntity == null;
    }

    /** Called once per server tick from the common event handler. */
    public static void tick(ServerLevel level) {
        Iterator<ActiveMidas> iterator = ACTIVE.iterator();
        while (iterator.hasNext()) {
            ActiveMidas active = iterator.next();
            if (!active.dimension.equals(level.dimension()) || level.getGameTime() < active.restoreAt) continue;
            iterator.remove();
            boolean frierenNearby = active.alwaysRestore || !level.getEntitiesOfClass(FrierenEntity.class,
                    new net.minecraft.world.phys.AABB(active.center).inflate(24.0D),
                    f -> f.isAlive() && f.getOwnerUUID() != null && f.getOwnerUUID().equals(active.caster)).isEmpty();
            if (!frierenNearby) continue;
            for (ChangedBlock original : active.blocks) {
                // Do not overwrite a block the player has changed since the spell was cast.
                if (level.getBlockState(original.pos).is(Blocks.GOLD_BLOCK)) {
                    level.setBlock(original.pos, original.state, 3);
                }
            }
            BlockPos center = active.center;
            level.sendParticles(ParticleTypes.ENCHANT, center.getX() + 0.5D, center.getY() + 0.5D, center.getZ() + 0.5D,
                    60, RADIUS, RADIUS * 0.5D, RADIUS, 0.08D);
        }
    }

    private record ChangedBlock(BlockPos pos, BlockState state) {}
    private record ActiveMidas(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension,
                               UUID caster, BlockPos center, long restoreAt, List<ChangedBlock> blocks,
                               boolean alwaysRestore) {}
}
