package com.frierenmod.spell;

import com.frierenmod.entity.FrierenEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Water Creation - places a water source block at Frieren's feet if the space allows it.
 */
public class WaterCreationSpell extends SpellBase {
    public static final WaterCreationSpell INSTANCE = new WaterCreationSpell();

    public WaterCreationSpell() {
        super("water_creation", 100);
    }

    public boolean cast(FrierenEntity frieren) {
        Level level = frieren.level();
        if (level.isClientSide) {
            return false;
        }

        frieren.triggerCastAnimation();
        BlockPos pos = frieren.blockPosition();
        BlockState state = level.getBlockState(pos);

        if (state.canBeReplaced() || state.isAir()) {
            level.setBlock(pos, Blocks.WATER.defaultBlockState(), 3);
            level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.PLAYERS, 1.0F, 1.0F);
            return true;
        }
        return false;
    }
}
