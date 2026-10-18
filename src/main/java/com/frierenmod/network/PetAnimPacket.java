package com.frierenmod.network;

import com.frierenmod.FrierenMod;
import com.frierenmod.entity.FrierenEntity;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server -> clients: this player is patting Frieren's head for a while (drives the arm animation). */
public record PetAnimPacket(int playerId, int frierenId, int ticks) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<PetAnimPacket> TYPE =
            new CustomPacketPayload.Type<>(FrierenMod.id("pet_anim"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PetAnimPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, PetAnimPacket::playerId,
                    ByteBufCodecs.VAR_INT, PetAnimPacket::frierenId,
                    ByteBufCodecs.VAR_INT, PetAnimPacket::ticks,
                    PetAnimPacket::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void send(ServerPlayer player, FrierenEntity frieren, int ticks) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new PetAnimPacket(player.getId(), frieren.getId(), ticks));
    }

    public static void handle(PetAnimPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> com.frierenmod.client.ClientPetState.start(packet.playerId(), packet.frierenId(), packet.ticks()));
    }
}
