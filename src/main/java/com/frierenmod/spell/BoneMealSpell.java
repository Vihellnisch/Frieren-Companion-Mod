package com.frierenmod.spell;

import com.frierenmod.entity.FrierenEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/**
 * Bone Meal Spell - applies a bone-meal growth burst to crops in a 5-block radius.
 */
public class BoneMealSpell extends SpellBase {
    public static final BoneMealSpell INSTANCE = new BoneMealSpell();
    private static final int RADIUS = 5;

    public BoneMealSpell() {
        super("bone_meal", 200);
    }

    public int cast(FrierenEntity frieren, Player player) {
        Level level = frieren.level();
        if (level.isClientSide) {
            return 0;
        }

        frieren.triggerCastAnimation();
        BlockPos center = frieren.blockPosition();
        int applied = 0;

        for (int x = -RADIUS; x <= RADIUS; x++) {
            for (int z = -RADIUS; z <= RADIUS; z++) {
                for (int y = -2; y <= 2; y++) {
                    BlockPos pos = center.offset(x, y, z);
                    ItemStack fakeBoneMeal = new ItemStack(Items.BONE_MEAL, 64);
                    if (BoneMealItem.applyBonemeal(fakeBoneMeal, level, pos, player)) {
                        applied++;
                    }
                }
            }
        }

        level.playSound(null, center, SoundEvents.BONE_MEAL_USE, SoundSource.PLAYERS, 1.0F, 1.0F);
        return applied;
    }
}
