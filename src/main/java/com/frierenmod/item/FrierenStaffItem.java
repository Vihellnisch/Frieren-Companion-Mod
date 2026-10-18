package com.frierenmod.item;

import com.frierenmod.magic.Fusion;
import com.frierenmod.magic.PlayerMagic;
import com.frierenmod.magic.SpellCaster;
import com.frierenmod.magic.SpellCaster.StaffSpell;
import com.frierenmod.magic.SpellEntries;
import com.frierenmod.network.FrierenSyncPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * The Frieren Staff: right-click casts the selected wheel entry (a spell or a fusion), sneak+right-click
 * steps to the next entry on the wheel. Per-entry cooldowns and mana proficiency are applied via
 * {@link SpellCaster} and {@link PlayerMagic}.
 */
public class FrierenStaffItem extends Item {

    public FrierenStaffItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (level.isClientSide) {
            return InteractionResultHolder.sidedSuccess(stack, true);
        }

        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.success(stack);
        }

        if (player.isShiftKeyDown()) {
            cycleSpell(serverPlayer);
            return InteractionResultHolder.success(stack);
        }

        int code = resolveSelected(serverPlayer);
        StaffSpell spell = SpellEntries.spell(code);

        if (spell == StaffSpell.MANA_VISION) {
            boolean active = PlayerMagic.toggleManaVision(serverPlayer);
            FrierenSyncPacket.sync(serverPlayer);
            player.displayClientMessage(Component.literal("Mana Vision: " + (active ? "ON" : "OFF"))
                    .withStyle(ChatFormatting.AQUA), true);
            return InteractionResultHolder.success(stack);
        }

        String key = SpellEntries.cooldownKey(code);
        if (PlayerMagic.isSpellOnCooldown(serverPlayer, key)) {
            long remaining = PlayerMagic.getSpellCooldownTicksRemaining(serverPlayer, key);
            player.displayClientMessage(
                    Component.literal(String.format("Cooldown: %.1fs remaining", remaining / 20.0))
                            .withStyle(ChatFormatting.YELLOW), true);
            return InteractionResultHolder.success(stack);
        }

        Fusion fusion = SpellEntries.fusion(code);
        boolean cast = spell != null ? SpellCaster.cast(spell, serverPlayer) : fusion != null && fusion.cast(serverPlayer);
        if (cast) {
            PlayerMagic.setSpellCooldown(serverPlayer, key, SpellEntries.cooldownTicks(code));
            PlayerMagic.addProficiency(serverPlayer);
            FrierenSyncPacket.sync(serverPlayer);
            player.displayClientMessage(Component.literal("Casting: " + SpellEntries.displayName(code))
                    .withStyle(fusion != null ? ChatFormatting.GOLD : ChatFormatting.LIGHT_PURPLE), true);
        }
        return InteractionResultHolder.success(stack);
    }

    /** Step to the next entry on the player's wheel and sync it. */
    private void cycleSpell(ServerPlayer player) {
        List<Integer> wheel = SpellEntries.loadout(player);
        int i = wheel.indexOf(PlayerMagic.getSelectedSpell(player));
        int next = wheel.get((i + 1) % wheel.size());
        PlayerMagic.setSelectedSpell(player, next);
        FrierenSyncPacket.sync(player);
        player.displayClientMessage(Component.literal("Selected: " + SpellEntries.displayName(next))
                .withStyle(ChatFormatting.AQUA), true);
    }

    /** The selected entry, or the wheel's first entry if the selection is no longer on it. */
    private int resolveSelected(ServerPlayer player) {
        List<Integer> wheel = SpellEntries.loadout(player);
        int selected = PlayerMagic.getSelectedSpell(player);
        return wheel.contains(selected) ? selected : wheel.get(0);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.frierenmod.frieren_staff")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.frierenmod.frieren_staff_3")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.frierenmod.frieren_staff_2")
                .withStyle(ChatFormatting.DARK_GRAY));
        super.appendHoverText(stack, context, tooltip, flag);
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return false;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }
}
