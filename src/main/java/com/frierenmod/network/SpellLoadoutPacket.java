package com.frierenmod.network;

import com.frierenmod.FrierenMod;
import com.frierenmod.magic.PlayerMagic;
import com.frierenmod.magic.SpellEntries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/** Client -> server: the wheel as arranged in the spellbook. */
public record SpellLoadoutPacket(List<Integer> codes) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SpellLoadoutPacket> TYPE =
            new CustomPacketPayload.Type<>(FrierenMod.id("spell_loadout"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SpellLoadoutPacket> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(PlayerMagic.WHEEL_SLOTS)),
                    SpellLoadoutPacket::codes, SpellLoadoutPacket::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SpellLoadoutPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            List<Integer> clean = new ArrayList<>();
            for (int code : packet.codes()) {
                if (SpellEntries.isAvailable(player, code) && !clean.contains(code)) clean.add(code);
            }
            PlayerMagic.setLoadout(player, clean.stream().mapToInt(Integer::intValue).toArray());
            if (!clean.isEmpty() && !clean.contains(PlayerMagic.getSelectedSpell(player))) {
                PlayerMagic.setSelectedSpell(player, clean.get(0));
            }
            FrierenSyncPacket.sync(player);
        });
    }
}
