package com.frierenmod.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * A ring given to Frieren to propose. Giving this to her (at ALLY stage) unlocks the PARTNER stage.
 */
public class PromiseRingItem extends Item {
    public PromiseRingItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.frierenmod.promise_ring").withStyle(ChatFormatting.LIGHT_PURPLE));
        super.appendHoverText(stack, context, tooltip, flag);
    }
}
