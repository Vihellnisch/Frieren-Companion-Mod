package com.frierenmod.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;

public class FrierenBagScreen extends AbstractContainerScreen<FrierenBagMenu> {

    private static final String[] ARMOR_LABELS = {"H", "C", "L", "B"};

    public FrierenBagScreen(FrierenBagMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 176;
        // Layout (all y relative to panel top):
        //   Bag title: y=6
        //   Bag rows: y=18, 36, 54  (end y=70)
        //   Armor divider: y=74
        //   Armor slots row: y=80   (end y=96)
        //   Player inv separator: y=98
        //   Player inv rows: y=102, 120, 138  (end y=154)
        //   Hotbar: y=158           (end y=174)
        //   Padding: 4px → imageHeight=178
        this.imageHeight = 178;
        this.titleLabelX = 8;
        this.titleLabelY = 6;
        this.inventoryLabelX = 8;
        this.inventoryLabelY = 90; // "Inventory" label not shown; overridden in renderLabels
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int ox = this.leftPos;
        int oy = this.topPos;

        // Panel background
        g.fill(ox, oy, ox + this.imageWidth, oy + this.imageHeight, 0xC0101018);
        g.renderOutline(ox, oy, this.imageWidth, this.imageHeight, 0xFF6A5ACD);

        // Bag slots — 3 rows at y=18, 36, 54
        for (int row = 0; row < FrierenBagMenu.BAG_ROWS; row++) {
            for (int col = 0; col < FrierenBagMenu.BAG_COLS; col++) {
                int sx = ox + 8 + col * 18;
                int sy = oy + 18 + row * 18;
                g.fill(sx - 1, sy - 1, sx + 17, sy + 17, 0xFF555555);
                g.fill(sx, sy, sx + 16, sy + 16, 0xFF8B8B8B);
            }
        }

        // Armor section divider
        g.fill(ox + 7, oy + 74, ox + 169, oy + 75, 0xFF6A5ACD);

        // Armor slot backgrounds — 4 slots at y=80
        for (int i = 0; i < FrierenBagMenu.ARMOR_SIZE; i++) {
            int sx = ox + 8 + i * 18;
            int sy = oy + 80;
            g.fill(sx - 1, sy - 1, sx + 17, sy + 17, 0xFF445566);
            g.fill(sx, sy, sx + 16, sy + 16, 0xFF667799);
        }

        // Player inventory separator
        g.fill(ox + 7, oy + 98, ox + 169, oy + 99, 0xFF6A5ACD);

        // Player main inventory slots — 3 rows at y=102, 120, 138
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int sx = ox + 8 + col * 18;
                int sy = oy + 102 + row * 18;
                g.fill(sx - 1, sy - 1, sx + 17, sy + 17, 0xFF555555);
                g.fill(sx, sy, sx + 16, sy + 16, 0xFF8B8B8B);
            }
        }

        // Hotbar slots at y=158
        for (int col = 0; col < 9; col++) {
            int sx = ox + 8 + col * 18;
            int sy = oy + 158;
            g.fill(sx - 1, sy - 1, sx + 17, sy + 17, 0xFF555555);
            g.fill(sx, sy, sx + 16, sy + 16, 0xFF9B8BBB);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g, mouseX, mouseY, partialTick);
        super.render(g, mouseX, mouseY, partialTick);
        this.renderTooltip(g, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(this.font, "Frieren's Bag", this.titleLabelX, this.titleLabelY, 0xFFFFFF, false);
        // "Armor:" label above the armor slot row
        g.drawString(this.font, "Armor:", 8, 76, 0xAAAAAA, false);
        // Slot type hints (H C L B) drawn below each armor slot
        for (int i = 0; i < ARMOR_LABELS.length; i++) {
            g.drawString(this.font, ARMOR_LABELS[i], 12 + i * 18, 97, 0x888888, false);
        }
        g.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, 91, 0xAAAAAA, false);
    }
}
