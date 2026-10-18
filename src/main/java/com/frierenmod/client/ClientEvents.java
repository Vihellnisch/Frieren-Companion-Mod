package com.frierenmod.client;

import com.frierenmod.FrierenMod;
import com.frierenmod.client.renderer.FrierenRenderer;
import com.frierenmod.client.renderer.GoldenButterflyRenderer;
import com.frierenmod.client.renderer.MimicRenderer;
import com.frierenmod.client.renderer.ZoltraakProjectileRenderer;
import com.frierenmod.registry.ModEntities;
import com.frierenmod.registry.ModMenuTypes;
import com.frierenmod.registry.ModProjectiles;
import com.frierenmod.screen.FrierenBagScreen;
import com.frierenmod.screen.FrierenScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = FrierenMod.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class ClientEvents {

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.FRIEREN.get(), FrierenRenderer::new);
        event.registerEntityRenderer(ModEntities.MIMIC.get(), MimicRenderer::new);
        event.registerEntityRenderer(ModEntities.GOLDEN_BUTTERFLY.get(), GoldenButterflyRenderer::new);
        event.registerEntityRenderer(ModProjectiles.ZOLTRAAK.get(), ZoltraakProjectileRenderer::new);
    }

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenuTypes.FRIEREN_MENU.get(), FrierenScreen::new);
        event.register(ModMenuTypes.FRIEREN_BAG_MENU.get(), FrierenBagScreen::new);
    }

    @SubscribeEvent
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(ModKeyBindings.KEY_SPELL_WHEEL);
        event.register(ModKeyBindings.KEY_SPELLBOOK);
    }

    private ClientEvents() {}
}
