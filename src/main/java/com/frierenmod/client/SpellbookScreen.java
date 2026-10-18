package com.frierenmod.client;

import com.frierenmod.magic.Fusion;
import com.frierenmod.magic.PlayerMagic;
import com.frierenmod.magic.SpellCaster.StaffSpell;
import com.frierenmod.magic.SpellEntries;
import com.frierenmod.network.SpellFusePacket;
import com.frierenmod.network.SpellLoadoutPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The spellbook: every known spell and discovered fusion on the left, the staff's wheel (up to eight,
 * in order) in the middle, and a fusion circle on the right where two spells can be combined.
 * Click a spell to add it, drag to reorder, right-click (or drag out) to remove.
 */
public class SpellbookScreen extends Screen {
    private static final int W = 380, H = 220;
    private static final int ROW = 14, LIST_ROWS = 12;
    private static final int GOLD = 0xFFE8C66A, INK = 0xFFE6E0F5, DIM = 0xFF8A82A6;

    private static final Map<StaffSpell, String> DESCRIPTIONS = Map.ofEntries(
            Map.entry(StaffSpell.MANA_VISION, "Toggle. See the world through mana."),
            Map.entry(StaffSpell.MANA_SENSING, "Reveals every creature nearby."),
            Map.entry(StaffSpell.ZOLTRAAK_T1, "The ordinary offensive magic. A straight beam."),
            Map.entry(StaffSpell.ZOLTRAAK_T2, "A Zoltraak that curves toward its target."),
            Map.entry(StaffSpell.ZOLTRAAK_T3, "A Zoltraak that explodes on impact."),
            Map.entry(StaffSpell.EINSAM, "A shockwave that throws back everything around you."),
            Map.entry(StaffSpell.MIDAS, "Turns the ground around you to gold. Troublesome."),
            Map.entry(StaffSpell.FLOWER_FIELD, "Makes flowers bloom all around you. Useless. Lovely."),
            Map.entry(StaffSpell.RUST_REMOVAL, "Scrubs a stage of oxidation off copper."),
            Map.entry(StaffSpell.CREATE_WATER, "A little water, where you're looking."),
            Map.entry(StaffSpell.CREATE_TORCH, "A torch, where you're looking."),
            Map.entry(StaffSpell.GROW_GRASS, "Bare dirt nearby turns to grass."),
            Map.entry(StaffSpell.GOLDEN_BUTTERFLIES, "A dozen golden butterflies. They do nothing."));

    private int left, top;
    private final List<Integer> known = new ArrayList<>();
    private final List<Integer> wheel = new ArrayList<>();
    private int scroll;
    /** The two spells in the fusion circle (codes, or -1). */
    private int socketA = -1, socketB = -1;
    private Button fuseButton;

    // dragging
    private int dragCode = -1, dragFromWheel = -1;
    private boolean dragFromKnown, dragMoved;
    private double pressX, pressY;

    public SpellbookScreen() {
        super(Component.literal("Spellbook"));
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        refresh();
        fuseButton = addRenderableWidget(Button.builder(Component.literal("Fuse"), b -> fuse())
                .bounds(left + 292, top + 128, 70, 18).build());
    }

    private void refresh() {
        known.clear();
        known.addAll(SpellEntries.known(ClientMagicState.learnedSpells, ClientMagicState.fusions));
        wheel.clear();
        wheel.addAll(ClientMagicState.loadout);
        scroll = Math.max(0, Math.min(scroll, known.size() - LIST_ROWS));
    }

    @Override
    public void tick() {
        // The server's answer to a fuse or a wheel change arrives as a sync; pick it up when idle.
        if (dragCode < 0 && (!ClientMagicState.loadout.equals(wheel)
                || known.size() != SpellEntries.known(ClientMagicState.learnedSpells, ClientMagicState.fusions).size())) {
            refresh();
        }
        fuseButton.active = socketA >= 0 && socketB >= 0 && socketA != socketB;
    }

    // ----- layout -----
    private int knownRowAt(double mx, double my) {
        int x0 = left + 10, y0 = top + 30;
        if (mx < x0 || mx > x0 + 130 || my < y0 || my >= y0 + LIST_ROWS * ROW) return -1;
        int i = scroll + (int) ((my - y0) / ROW);
        return i < known.size() ? i : -1;
    }

    private int wheelSlotAt(double mx, double my) {
        int x0 = left + 152, y0 = top + 30;
        if (mx < x0 || mx > x0 + 120 || my < y0 || my >= y0 + PlayerMagic.WHEEL_SLOTS * 20) return -1;
        return (int) ((my - y0) / 20);
    }

    private int socketAt(double mx, double my) {
        int x0 = left + 292;
        if (mx < x0 || mx > x0 + 70) return -1;
        if (my >= top + 48 && my < top + 66) return 0;
        if (my >= top + 90 && my < top + 108) return 1;
        return -1;
    }

    // ----- input -----
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        int k = knownRowAt(mx, my), w = wheelSlotAt(mx, my), s = socketAt(mx, my);
        if (button == 1) {
            if (w >= 0 && w < wheel.size()) {
                wheel.remove(w);
                sendWheel();
            } else if (s == 0) {
                socketA = -1;
            } else if (s == 1) {
                socketB = -1;
            }
            return true;
        }
        if (button != 0) return false;
        pressX = mx;
        pressY = my;
        dragMoved = false;
        if (k >= 0) {
            dragCode = known.get(k);
            dragFromKnown = true;
            dragFromWheel = -1;
        } else if (w >= 0 && w < wheel.size()) {
            dragCode = wheel.get(w);
            dragFromKnown = false;
            dragFromWheel = w;
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragCode >= 0 && (Math.abs(mx - pressX) > 3 || Math.abs(my - pressY) > 3)) dragMoved = true;
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (button != 0 || dragCode < 0) return super.mouseReleased(mx, my, button);
        int code = dragCode;
        dragCode = -1;
        if (!dragMoved) {
            // A plain click on a known spell: shift puts it in the fusion circle, otherwise onto the wheel.
            if (dragFromKnown) {
                if (hasShiftDown()) {
                    putInSocket(code, socketA < 0 ? 0 : 1);
                } else if (!wheel.contains(code) && wheel.size() < PlayerMagic.WHEEL_SLOTS) {
                    wheel.add(code);
                    sendWheel();
                }
            }
            return true;
        }
        int w = wheelSlotAt(mx, my), s = socketAt(mx, my);
        if (s >= 0) {
            putInSocket(code, s);
        } else if (w >= 0) {
            wheel.remove((Integer) code);
            if (wheel.size() >= PlayerMagic.WHEEL_SLOTS) return true;
            wheel.add(Math.min(w, wheel.size()), code);
            sendWheel();
        } else if (dragFromWheel >= 0) {
            wheel.remove((Integer) code); // dragged off the wheel
            sendWheel();
        }
        return true;
    }

    private void putInSocket(int code, int socket) {
        if (SpellEntries.spell(code) == null || code == SpellEntries.code(StaffSpell.MANA_VISION)) return; // fusions don't fuse again
        if (socket == 0) socketA = code;
        else socketB = code;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        scroll = Math.max(0, Math.min(scroll - (int) Math.signum(sy), Math.max(0, known.size() - LIST_ROWS)));
        return true;
    }

    private void sendWheel() {
        ClientMagicState.loadout = new ArrayList<>(wheel);
        PacketDistributor.sendToServer(new SpellLoadoutPacket(new ArrayList<>(wheel)));
    }

    private void fuse() {
        if (socketA < 0 || socketB < 0) return;
        PacketDistributor.sendToServer(new SpellFusePacket(socketA, socketB));
        socketA = socketB = -1;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ----- drawing -----
    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        super.render(g, mx, my, partialTick); // background + panel, then the Fuse button
        g.drawCenteredString(font, "Spellbook", left + W / 2, top + 6, GOLD);

        // known spells
        g.drawString(font, "Known magic", left + 10, top + 18, DIM, false);
        for (int r = 0; r < LIST_ROWS; r++) {
            int i = scroll + r;
            if (i >= known.size()) break;
            int code = known.get(i);
            int y = top + 30 + r * ROW;
            boolean hover = knownRowAt(mx, my) == i;
            boolean onWheel = wheel.contains(code);
            if (hover) g.fill(left + 8, y - 1, left + 142, y + ROW - 2, 0x40FFFFFF);
            boolean fusion = SpellEntries.fusion(code) != null;
            String label = (fusion ? "✦ " : "") + SpellEntries.displayName(code);
            g.drawString(font, label, left + 12, y + 2, fusion ? GOLD : onWheel ? DIM : INK, false);
            if (onWheel) g.drawString(font, "✓", left + 132, y + 2, 0xFF7FD67F, false);
        }
        if (known.size() > LIST_ROWS) {
            g.drawString(font, (scroll + 1) + "-" + Math.min(known.size(), scroll + LIST_ROWS) + " of " + known.size(),
                    left + 10, top + 30 + LIST_ROWS * ROW + 2, DIM, false);
        }

        // wheel
        g.drawString(font, "Staff wheel (" + wheel.size() + "/" + PlayerMagic.WHEEL_SLOTS + ")", left + 152, top + 18, DIM, false);
        for (int i = 0; i < PlayerMagic.WHEEL_SLOTS; i++) {
            int y = top + 30 + i * 20;
            boolean hover = wheelSlotAt(mx, my) == i;
            g.fill(left + 152, y, left + 272, y + 18, hover ? 0x60584C8A : 0x40342B5C);
            g.drawString(font, String.valueOf(i + 1), left + 156, y + 5, DIM, false);
            if (i < wheel.size() && !(dragCode >= 0 && dragMoved && dragFromWheel == i)) {
                int code = wheel.get(i);
                g.drawString(font, SpellEntries.displayName(code), left + 168, y + 5,
                        SpellEntries.fusion(code) != null ? GOLD : INK, false);
            }
        }

        // fusion circle
        g.drawString(font, "Fusion circle", left + 292, top + 18, DIM, false);
        int found = Integer.bitCount(ClientMagicState.fusions);
        g.drawString(font, found + "/" + Fusion.values().length + " found", left + 292, top + 30, DIM, false);
        drawSocket(g, socketA, top + 48, socketAt(mx, my) == 0);
        g.drawCenteredString(font, "+", left + 327, top + 74, GOLD);
        drawSocket(g, socketB, top + 90, socketAt(mx, my) == 1);
        g.drawString(font, "Shift-click", left + 292, top + 152, DIM, false);
        g.drawString(font, "or drag spells", left + 292, top + 162, DIM, false);
        g.drawString(font, "into the circle.", left + 292, top + 172, DIM, false);

        g.drawCenteredString(font, "Click: add   Drag: reorder   Right-click / drag out: remove",
                left + W / 2, top + H - 14, DIM);

        // the dragged entry follows the mouse
        if (dragCode >= 0 && dragMoved) {
            String label = SpellEntries.displayName(dragCode);
            int tw = font.width(label);
            g.pose().pushPose();
            g.pose().translate(0, 0, 300);
            g.fill(mx + 6, my - 2, mx + 12 + tw, my + 10, 0xE0302850);
            g.drawString(font, label, mx + 9, my, GOLD, false);
            g.pose().popPose();
        } else {
            tooltip(g, mx, my);
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float partialTick) {
        super.renderBackground(g, mx, my, partialTick);
        g.fill(left - 2, top - 2, left + W + 2, top + H + 2, 0xFF6B5A2E);
        g.fill(left, top, left + W, top + H, 0xF01A1530);
    }

    private void drawSocket(GuiGraphics g, int code, int y, boolean hover) {
        int x = left + 292;
        g.fill(x, y, x + 70, y + 18, hover ? 0x80806A30 : 0x50806A30);
        g.renderOutline(x, y, 70, 18, 0xFFB89A4A);
        String label = code >= 0 ? SpellEntries.displayName(code) : "empty";
        g.drawCenteredString(font, font.plainSubstrByWidth(label, 66), x + 35, y + 5, code >= 0 ? INK : DIM);
    }

    private void tooltip(GuiGraphics g, int mx, int my) {
        int code = -1;
        int k = knownRowAt(mx, my), w = wheelSlotAt(mx, my);
        if (k >= 0) code = known.get(k);
        else if (w >= 0 && w < wheel.size()) code = wheel.get(w);
        if (code < 0) return;
        List<FormattedCharSequence> lines = new ArrayList<>();
        lines.add(Component.literal(SpellEntries.displayName(code)).getVisualOrderText());
        Fusion f = SpellEntries.fusion(code);
        String text = f != null ? f.description + " (" + f.a.displayName + " + " + f.b.displayName + ")" : description(code);
        if (text != null) lines.addAll(font.split(Component.literal(text), 180));
        lines.add(Component.literal(String.format("Cooldown %.1fs", SpellEntries.cooldownTicks(code) / 20.0)).getVisualOrderText());
        g.renderTooltip(font, lines, mx, my);
    }

    @Nullable
    private static String description(int code) {
        StaffSpell s = SpellEntries.spell(code);
        return s == null ? null : DESCRIPTIONS.get(s);
    }
}
