package com.frierenmod.client;

import com.frierenmod.FrierenMod;
import com.frierenmod.magic.SpellEntries;
import com.frierenmod.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/**
 * Client HUD: a blue Mana-Vision screen tint and a small staff readout showing the selected
 * spell, mana proficiency, and cooldown.
 */
@EventBusSubscriber(modid = FrierenMod.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class ClientMagicOverlay {

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.options.hideGui) {
            return;
        }
        GuiGraphics gui = event.getGuiGraphics();
        int width = gui.guiWidth();
        int height = gui.guiHeight();

        // Mana Vision blue tint.
        if (ClientMagicState.manaVision) {
            gui.fill(0, 0, width, height, 0x303399FF);
            gui.drawString(mc.font, Component.literal("\u2726 Mana Vision"),
                    6, 6, 0x66CCFF, true);
        }

        // Staff HUD only while holding the staff.
        boolean holdingStaff = player.getMainHandItem().is(ModItems.FRIEREN_STAFF.get())
                || player.getOffhandItem().is(ModItems.FRIEREN_STAFF.get());
        if (!holdingStaff) {
            return;
        }

        int selected = ClientMagicState.loadout.contains(ClientMagicState.selectedSpell) || ClientMagicState.loadout.isEmpty()
                ? ClientMagicState.selectedSpell : ClientMagicState.loadout.get(0);
        String spellText = "Spell: " + SpellEntries.displayName(selected);
        String profText = "Mana: " + ClientMagicState.proficiency;

        int x = 6;
        int y = height - 40;
        gui.drawString(mc.font, Component.literal(spellText), x, y, 0xC8A2FF, true);
        gui.drawString(mc.font, Component.literal(profText), x, y + 10, 0x9AE0FF, true);

        float cd = player.getCooldowns().getCooldownPercent(ModItems.FRIEREN_STAFF.get(), 0.0F);
        if (cd > 0.0F) {
            int barW = 60;
            int filled = (int) (barW * (1.0F - cd));
            gui.fill(x, y + 22, x + barW, y + 26, 0xFF333333);
            gui.fill(x, y + 22, x + filled, y + 26, 0xFF66CCFF);
        }
    }

    private ClientMagicOverlay() {}
}
