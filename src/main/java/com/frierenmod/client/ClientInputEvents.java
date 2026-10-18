package com.frierenmod.client;

import com.frierenmod.FrierenMod;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.platform.InputConstants;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = FrierenMod.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class ClientInputEvents {

    @SubscribeEvent
    public static void onKey(InputEvent.Key event) {
        if (event.getAction() != GLFW.GLFW_PRESS) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;
        InputConstants.Key pressed = InputConstants.getKey(event.getKey(), event.getScanCode());
        if (ModKeyBindings.KEY_SPELL_WHEEL.getKey().equals(pressed)) {
            mc.setScreen(new SpellWheelScreen());
        } else if (ModKeyBindings.KEY_SPELLBOOK.getKey().equals(pressed)) {
            mc.setScreen(new SpellbookScreen());
        }
    }

    private ClientInputEvents() {}
}
