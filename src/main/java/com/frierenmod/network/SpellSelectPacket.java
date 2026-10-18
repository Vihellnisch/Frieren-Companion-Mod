package com.frierenmod.network;

import com.frierenmod.FrierenMod;
import com.frierenmod.magic.PlayerMagic;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SpellSelectPacket(int spellIndex) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SpellSelectPacket> TYPE =
            new CustomPacketPayload.Type<>(FrierenMod.id("spell_select"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SpellSelectPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, SpellSelectPacket::spellIndex,
                    SpellSelectPacket::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SpellSelectPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            if (!(player instanceof ServerPlayer serverPlayer)) return;
            int code = packet.spellIndex();
            if (!com.frierenmod.magic.SpellEntries.isAvailable(serverPlayer, code)) return;
            PlayerMagic.setSelectedSpell(serverPlayer, code);
            FrierenSyncPacket.sync(serverPlayer);
        });
    }
}
