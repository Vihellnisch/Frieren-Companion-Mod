package com.frierenmod.item;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * A common herb used in trades / crafting. Eating it grants brief Regeneration.
 */
public class ManaHerbItem extends Item {
    public static final FoodProperties FOOD = new FoodProperties.Builder()
            .nutrition(2)
            .saturationModifier(0.3F)
            .alwaysEdible()
            .build();

    public ManaHerbItem(Properties properties) {
        super(properties);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide) {
            entity.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 0));
        }
        return super.finishUsingItem(stack, level, entity);
    }
}
