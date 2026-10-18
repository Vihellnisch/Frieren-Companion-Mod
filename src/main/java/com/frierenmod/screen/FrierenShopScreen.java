package com.frierenmod.screen;

import com.frierenmod.network.FrierenInteractPacket;
import com.frierenmod.registry.ModItems;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * Clean dedicated shop screen. Pure client-side — no server menu needed.
 * Sends FrierenInteractPacket when the player clicks Buy.
 */
public class FrierenShopScreen extends Screen {

    // Window size — tall enough for 4 rows + title + back button.
    private static final int W = 230;
    // title(24) + separator(2) + 4×ROW_H(4×40=160) + back(24) + padding(10) = 220
    private static final int H = 220;
    private static final int ROW_H = 40;
    private static final int FIRST_ROW_Y = 26; // below title+separator

    private final int entityId;
    private int ox, oy;

    private record Trade(ItemStack cost1, int qty1,
                         ItemStack cost2, int qty2,
                         ItemStack result, int qtyR,
                         int action) {}

    private final List<Trade> trades = new ArrayList<>();

    public FrierenShopScreen(int entityId) {
        super(Component.literal("Frieren's Shop"));
        this.entityId = entityId;
    }

    @Override
    protected void init() {
        this.ox = (this.width - W) / 2;
        this.oy = (this.height - H) / 2;

        trades.clear();
        trades.add(new Trade(new ItemStack(Items.EMERALD), 3,
                             ItemStack.EMPTY, 0,
                             new ItemStack(ModItems.MANA_HERB.get()), 2,
                             FrierenInteractPacket.TRADE_0));
        trades.add(new Trade(new ItemStack(Items.BOOK), 4,
                             new ItemStack(Items.LAPIS_LAZULI), 6,
                             new ItemStack(ModItems.GRIMOIRE_BASIC_MAGIC.get()), 1,
                             FrierenInteractPacket.TRADE_1));
        trades.add(new Trade(new ItemStack(Items.EMERALD), 12,
                             ItemStack.EMPTY, 0,
                             new ItemStack(ModItems.GRIMOIRE_SPIRIT_LORE.get()), 1,
                             FrierenInteractPacket.TRADE_2));
        trades.add(new Trade(new ItemStack(ModItems.MANA_HERB.get()), 4,
                             ItemStack.EMPTY, 0,
                             new ItemStack(Items.EMERALD), 1,
                             FrierenInteractPacket.TRADE_3));

        // Buy buttons — right-aligned in each row
        for (int i = 0; i < trades.size(); i++) {
            final int idx = i;
            int btnY = oy + FIRST_ROW_Y + i * ROW_H + (ROW_H - 18) / 2;
            addRenderableWidget(Button.builder(Component.literal("Buy"),
                            btn -> onBuy(idx))
                    .bounds(ox + W - 50, btnY, 44, 18)
                    .build());
        }

        // Back button below all rows
        int backY = oy + FIRST_ROW_Y + trades.size() * ROW_H + 6;
        addRenderableWidget(Button.builder(Component.literal("< Back"),
                        btn -> onClose())
                .bounds(ox + 6, backY, 60, 18)
                .build());
    }

    private void onBuy(int tradeIndex) {
        PacketDistributor.sendToServer(
                new FrierenInteractPacket(entityId, trades.get(tradeIndex).action()));
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        // World blur first
        super.renderBackground(g, mx, my, pt);
        // Panel drawn here so it sits above world blur but below items and buttons
        g.fill(ox, oy, ox + W, oy + H, 0xD0101018);
        g.renderOutline(ox, oy, W, H, 0xFF6A5ACD);
        g.drawString(font, "Frieren's Shop", ox + 8, oy + 7, 0xFFFFFF, false);
        g.fill(ox + 6, oy + 20, ox + W - 6, oy + 21, 0xFF6A5ACD);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        // 1. World blur + panel + title (via renderBackground override above)
        this.renderBackground(g, mx, my, pt);

        // 2. Buttons rendered by super (sharp, above blur)
        super.render(g, mx, my, pt);

        // 3. Trade rows drawn LAST so items appear above the blur layer
        for (int i = 0; i < trades.size(); i++) {
            renderRow(g, i, trades.get(i));
        }
    }

    private void renderRow(GuiGraphics g, int index, Trade t) {
        int rowY = oy + FIRST_ROW_Y + index * ROW_H;
        int iy = rowY + (ROW_H - 16) / 2; // vertical center for 16px icons

        // Alternating row tint
        if (index % 2 == 0) {
            g.fill(ox + 4, rowY + 1, ox + W - 4, rowY + ROW_H - 1, 0x18FFFFFF);
        }

        int cx = ox + 10; // cursor x

        // Cost 1 — item icon + count label below
        renderItemWithCount(g, t.cost1(), t.qty1(), cx, iy);
        cx += 20;

        // Cost 2 (optional)
        if (!t.cost2().isEmpty()) {
            g.drawString(font, "+", cx, iy + 4, 0xAAAAAA, false);
            cx += 10;
            renderItemWithCount(g, t.cost2(), t.qty2(), cx, iy);
            cx += 20;
        }

        // Arrow
        g.drawString(font, "->", cx, iy + 4, 0x888888, false);
        cx += 18;

        // Result
        renderItemWithCount(g, t.result(), t.qtyR(), cx, iy);
    }

    private void renderItemWithCount(GuiGraphics g, ItemStack proto, int qty, int x, int y) {
        // Slot-like background
        g.fill(x - 1, y - 1, x + 17, y + 17, 0xFF444444);
        g.fill(x, y, x + 16, y + 16, 0xFF777777);

        // Render item with the correct stack count so vanilla draws the number
        ItemStack display = proto.copyWithCount(qty);
        g.renderItem(display, x, y);
        g.renderItemDecorations(font, display, x, y);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
