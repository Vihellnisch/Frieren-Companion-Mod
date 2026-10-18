package com.frierenmod.network;

import com.frierenmod.FrierenMod;
import com.frierenmod.magic.Fusion;
import com.frierenmod.magic.PlayerMagic;
import com.frierenmod.magic.SpellCaster.StaffSpell;
import com.frierenmod.magic.SpellEntries;
import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/** Client -> server: try fusing two learned spells in the spellbook's fusion circle. */
public record SpellFusePacket(int first, int second) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SpellFusePacket> TYPE =
            new CustomPacketPayload.Type<>(FrierenMod.id("spell_fuse"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SpellFusePacket> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, SpellFusePacket::first, ByteBufCodecs.VAR_INT, SpellFusePacket::second,
                    SpellFusePacket::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SpellFusePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) fuse(player, packet.first(), packet.second());
        });
    }

    /** Server side. Discovers the fusion of two learned spells, if they have one. */
    public static void fuse(ServerPlayer player, int first, int second) {
        StaffSpell a = SpellEntries.spell(first), b = SpellEntries.spell(second);
        if (a == null || b == null || a == b || !SpellEntries.isAvailable(player, first)
                || !SpellEntries.isAvailable(player, second)) return;
        Fusion fusion = Fusion.of(a, b);
        if (fusion == null) {
            player.displayClientMessage(Component.literal("The mana fizzles. " + a.displayName + " and "
                    + b.displayName + " won't combine.").withStyle(ChatFormatting.GRAY), true);
            player.playNotifySound(SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.5F, 1.4F);
            return;
        }
        boolean isNew = !fusion.isDiscovered(PlayerMagic.getFusions(player));
        PlayerMagic.discoverFusion(player, fusion.ordinal());
        if (isNew) {
            // Straight onto the wheel if there's room.
            List<Integer> wheel = new ArrayList<>(SpellEntries.loadout(player));
            int code = SpellEntries.code(fusion);
            if (wheel.size() < PlayerMagic.WHEEL_SLOTS && !wheel.contains(code)) wheel.add(code);
            PlayerMagic.setLoadout(player, wheel.stream().mapToInt(Integer::intValue).toArray());
            player.sendSystemMessage(Component.literal("New fusion: " + fusion.displayName + "! " + fusion.description)
                    .withStyle(ChatFormatting.GOLD));
        }
        player.playNotifySound(SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1.0F, 1.2F);
        player.playNotifySound(SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 1.0F);
        FrierenSyncPacket.sync(player);
    }
}
