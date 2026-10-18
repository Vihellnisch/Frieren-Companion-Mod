package com.frierenmod.client;

import com.frierenmod.magic.SpellCaster.StaffSpell;
import com.frierenmod.magic.SpellEntries;
import com.frierenmod.network.SpellSelectPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class SpellWheelScreen extends Screen {

    /** Spell codes on the wheel, in the order arranged in the spellbook. */
    private final List<Integer> spells = new ArrayList<>();
    private int hovered;

    public SpellWheelScreen() {
        super(Component.empty());
        spells.addAll(ClientMagicState.loadout);
        if (spells.isEmpty()) spells.add(SpellEntries.code(StaffSpell.MANA_VISION));
        int idx = spells.indexOf(ClientMagicState.selectedSpell);
        if (idx >= 0) hovered = idx;
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public boolean shouldCloseOnEsc() { return false; }

    @Override
    public void tick() {
        // KeyMapping.isDown() uses a cached field that gets cleared by mc.setScreen(),
        // so query GLFW directly for the real physical key state.
        Minecraft mc = Minecraft.getInstance();
        int keyCode = ModKeyBindings.KEY_SPELL_WHEEL.getKey().getValue();
        if (GLFW.glfwGetKey(mc.getWindow().getWindow(), keyCode) != GLFW.GLFW_PRESS) {
            apply();
            onClose();
        }
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        int n = spells.size();
        if (n == 0) return true;
        if (sy > 0) hovered = ((hovered - 1) % n + n) % n;
        else if (sy < 0) hovered = (hovered + 1) % n;
        return true;
    }

    private void apply() {
        if (spells.isEmpty()) return;
        int chosen = spells.get(hovered);
        ClientMagicState.selectedSpell = chosen;
        PacketDistributor.sendToServer(new SpellSelectPacket(chosen));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float pt) {
        int cx = width / 2, cy = height / 2;
        int n = spells.size();
        if (n == 0) return;

        // Full-screen dim
        g.fill(0, 0, width, height, 0x88000000);

        // Wheel background
        int r = 90;
        g.fill(cx - r - 24, cy - r - 24, cx + r + 24, cy + r + 24, 0xCC0D0D22);

        // Title
        g.drawCenteredString(font, "Spell Wheel", cx, cy - r - 16, 0xFF99AAFF);

        // Spell slots around the circle
        for (int i = 0; i < n; i++) {
            double angle = Math.toRadians(-90.0 + i * 360.0 / n);
            int sx = cx + (int) (Math.cos(angle) * r);
            int sy = cy + (int) (Math.sin(angle) * r);
            int code = spells.get(i);
            boolean sel = (i == hovered);
            String label = SpellEntries.displayName(code);
            int tw = font.width(label);
            int bg = sel ? 0xEEFFD700 : 0x66334466;
            int fg = sel ? 0xFF000000 : SpellEntries.fusion(code) != null ? 0xFFFFD27A : 0xFFBBCCDD;
            g.fill(sx - tw / 2 - 5, sy - 7, sx + tw / 2 + 5, sy + 7, bg);
            g.drawCenteredString(font, label, sx, sy - 3, fg);
        }

        // Needle pointing to selected slot
        double angle = Math.toRadians(-90.0 + hovered * 360.0 / n);
        int ex = cx + (int) (Math.cos(angle) * (r - 18));
        int ey = cy + (int) (Math.sin(angle) * (r - 18));
        drawThickLine(g, cx, cy, ex, ey, 0xCCFFD700);

        // Center: selected spell name
        String name = SpellEntries.displayName(spells.get(hovered));
        int tw = font.width(name);
        g.fill(cx - tw / 2 - 6, cy - 8, cx + tw / 2 + 6, cy + 8, 0xDD000033);
        g.drawCenteredString(font, name, cx, cy - 3, 0xFFFFD700);

        // Hint at bottom
        String keyName = ModKeyBindings.KEY_SPELL_WHEEL.getTranslatedKeyMessage().getString();
        g.drawCenteredString(font, "Scroll to choose  │  Release " + keyName + " to confirm",
                cx, cy + r + 16, 0xFF666688);
        String bookKey = ModKeyBindings.KEY_SPELLBOOK.getTranslatedKeyMessage().getString();
        g.drawCenteredString(font, "Arrange this wheel in the spellbook (" + bookKey + ")", cx, cy + r + 28, 0xFF555577);
    }

    private void drawThickLine(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
        int dx = x1 - x0, dy = y1 - y0;
        int steps = (int) Math.sqrt(dx * dx + dy * dy);
        if (steps < 2) return;
        for (int i = 8; i < steps - 8; i++) {
            int x = x0 + dx * i / steps;
            int y = y0 + dy * i / steps;
            g.fill(x - 1, y - 1, x + 2, y + 2, color);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) { return false; }

    @Override
    public boolean charTyped(char c, int modifiers) { return false; }
}
