package com.frierenmod;

import com.frierenmod.network.FrierenInteractPacket;
import com.frierenmod.network.FrierenSyncPacket;
import com.frierenmod.network.SpellSelectPacket;
import com.frierenmod.registry.ModEntities;
import com.frierenmod.registry.ModItems;
import com.frierenmod.registry.ModMenuTypes;
import com.frierenmod.registry.ModProjectiles;
import com.frierenmod.registry.ModSounds;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@Mod(FrierenMod.MODID)
public class FrierenMod {
    public static final String MODID = "frierenmod";

    public FrierenMod(IEventBus modBus, ModContainer container) {
        ModItems.ITEMS.register(modBus);
        ModItems.CREATIVE_TABS.register(modBus);
        ModEntities.ENTITIES.register(modBus);
        ModMenuTypes.MENUS.register(modBus);
        ModProjectiles.PROJECTILES.register(modBus);
        ModSounds.SOUNDS.register(modBus);

        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.COMMON, FrierenConfig.SPEC);

        modBus.addListener(ModEntities::registerAttributes);
        modBus.addListener(ModItems::addToCreativeTab);
        modBus.addListener(this::registerPayloads);
    }

    private void registerPayloads(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar("2");
        registrar.playToServer(
                FrierenInteractPacket.TYPE,
                FrierenInteractPacket.STREAM_CODEC,
                FrierenInteractPacket::handle
        );
        registrar.playToClient(
                FrierenSyncPacket.TYPE,
                FrierenSyncPacket.STREAM_CODEC,
                FrierenSyncPacket::handle
        );
        registrar.playToServer(
                com.frierenmod.network.SpellLoadoutPacket.TYPE,
                com.frierenmod.network.SpellLoadoutPacket.STREAM_CODEC,
                com.frierenmod.network.SpellLoadoutPacket::handle
        );
        registrar.playToServer(
                com.frierenmod.network.SpellFusePacket.TYPE,
                com.frierenmod.network.SpellFusePacket.STREAM_CODEC,
                com.frierenmod.network.SpellFusePacket::handle
        );
        registrar.playToClient(
                com.frierenmod.network.PetAnimPacket.TYPE,
                com.frierenmod.network.PetAnimPacket.STREAM_CODEC,
                com.frierenmod.network.PetAnimPacket::handle
        );
        registrar.playToServer(
                SpellSelectPacket.TYPE,
                SpellSelectPacket.STREAM_CODEC,
                SpellSelectPacket::handle
        );
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
