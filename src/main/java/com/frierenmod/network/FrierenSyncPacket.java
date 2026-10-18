package com.frierenmod.network;

import com.frierenmod.FrierenMod;
import com.frierenmod.client.ClientMagicState;
import com.frierenmod.magic.PlayerMagic;
import com.frierenmod.magic.SpellEntries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/**
 * Server -> client sync of the player's magic state so the client HUD, spell wheel and spellbook
 * can display it (persistentData is not synced automatically).
 */
public record FrierenSyncPacket(int learnedSpells, int selectedSpell, int proficiency, boolean manaVision,
                                List<Integer> loadout, int fusions)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<FrierenSyncPacket> TYPE =
            new CustomPacketPayload.Type<>(FrierenMod.id("frieren_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FrierenSyncPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, FrierenSyncPacket::learnedSpells,
                    ByteBufCodecs.VAR_INT, FrierenSyncPacket::selectedSpell,
                    ByteBufCodecs.VAR_INT, FrierenSyncPacket::proficiency,
                    ByteBufCodecs.BOOL, FrierenSyncPacket::manaVision,
                    ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(PlayerMagic.WHEEL_SLOTS)), FrierenSyncPacket::loadout,
                    ByteBufCodecs.VAR_INT, FrierenSyncPacket::fusions,
                    FrierenSyncPacket::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Build a packet from a player's current server-side state and send it to that player. */
    public static void sync(ServerPlayer player) {
        FrierenSyncPacket packet = new FrierenSyncPacket(
                PlayerMagic.getLearnedSpells(player),
                PlayerMagic.getSelectedSpell(player),
                PlayerMagic.getProficiency(player),
                PlayerMagic.isManaVision(player),
                SpellEntries.loadout(player),
                PlayerMagic.getFusions(player));
        PacketDistributor.sendToPlayer(player, packet);
    }

    public static void handle(FrierenSyncPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientMagicState.update(packet.learnedSpells(), packet.selectedSpell(),
                packet.proficiency(), packet.manaVision(), packet.loadout(), packet.fusions()));
    }
}
