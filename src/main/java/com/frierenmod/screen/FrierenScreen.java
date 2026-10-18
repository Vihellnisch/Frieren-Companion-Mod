package com.frierenmod.screen;

import com.frierenmod.entity.FrierenEntity;
import com.frierenmod.network.FrierenInteractPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * The chat-bubble style interaction screen. Shows stage-appropriate action buttons in a
 * fixed-size, scrollable panel so extra options never overflow the window.
 */
public class FrierenScreen extends AbstractContainerScreen<FrierenMenu> {

    private static final int BTN_WIDTH = 132;
    private static final int BTN_HEIGHT = 18;
    private static final int ROW_SPACING = 22;
    private static final int MAX_VISIBLE = 5;
    private static final int PANEL_TOP = 24;

    private final List<ButtonDef> defs = new ArrayList<>();
    private final List<Button> buttons = new ArrayList<>();
    private int scrollOffset = 0;

    public FrierenScreen(FrierenMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 160;
        this.imageHeight = 180;
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX = 8;
        this.titleLabelY = 6;

        FrierenEntity frieren = this.minecraft != null && this.minecraft.player != null
                ? this.menu.getFrieren(this.minecraft.player) : null;
        FrierenEntity.Stage stage = frieren != null ? frieren.getStage() : FrierenEntity.Stage.STRANGER;

        this.defs.clear();
        this.defs.addAll(buildButtons(stage));
        this.buttons.clear();

        int startX = this.leftPos + 10;
        for (ButtonDef def : defs) {
            Button b = Button.builder(Component.literal(def.label), btn -> onAction(def.action))
                    .bounds(startX, this.topPos + PANEL_TOP, BTN_WIDTH, BTN_HEIGHT)
                    .build();
            this.buttons.add(b);
            this.addRenderableWidget(b);
        }
        clampScroll();
        updateButtonPositions();
    }

    private List<ButtonDef> buildButtons(FrierenEntity.Stage stage) {
        List<ButtonDef> list = new ArrayList<>();
        int ord = stage.ordinal();

        list.add(new ButtonDef("Talk", FrierenInteractPacket.TALK));
        list.add(new ButtonDef("Shop", FrierenInteractPacket.OPEN_SHOP));
        list.add(new ButtonDef("Frieren's Bag", FrierenInteractPacket.BAG));
        list.add(new ButtonDef("Retrieve stored XP", FrierenInteractPacket.RETRIEVE_XP));
        list.add(new ButtonDef("Give Gift (right-click grimoire)", -1));

        if (ord >= FrierenEntity.Stage.FRIEND.ordinal()) {
            list.add(new ButtonDef("Follow me", FrierenInteractPacket.FOLLOW));
            list.add(new ButtonDef("Stay here", FrierenInteractPacket.STAY));
            list.add(new ButtonDef("Heal me", FrierenInteractPacket.HEAL));
            list.add(new ButtonDef("Bone Meal Spell", FrierenInteractPacket.BONE_MEAL));
            list.add(new ButtonDef("Tell me something", FrierenInteractPacket.LORE));
            list.add(new ButtonDef("Teach me a spell", FrierenInteractPacket.TEACH));
            list.add(new ButtonDef("Teach me a useless spell", FrierenInteractPacket.TEACH_USELESS));
            list.add(new ButtonDef("Ask about grimoires", FrierenInteractPacket.QUEST_ASK));
            list.add(new ButtonDef("I found a grimoire", FrierenInteractPacket.QUEST_COMPLETE));
        }
        if (ord >= FrierenEntity.Stage.ALLY.ordinal()) {
            list.add(new ButtonDef("Haste Buff", FrierenInteractPacket.HASTE));
            list.add(new ButtonDef("Create Water", FrierenInteractPacket.WATER));
        }
        if (ord >= FrierenEntity.Stage.PARTNER.ordinal()) {
            list.add(new ButtonDef("I love you", FrierenInteractPacket.LOVE));
            list.add(new ButtonDef("Himmel...", FrierenInteractPacket.HIMMEL));
        }
        return list;
    }

    private int maxScroll() {
        return Math.max(0, defs.size() - MAX_VISIBLE);
    }

    private void clampScroll() {
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll()));
    }

    private void updateButtonPositions() {
        for (int i = 0; i < buttons.size(); i++) {
            Button b = buttons.get(i);
            int visibleIndex = i - scrollOffset;
            if (visibleIndex >= 0 && visibleIndex < MAX_VISIBLE) {
                b.visible = true;
                b.active = true;
                b.setPosition(this.leftPos + 10, this.topPos + PANEL_TOP + visibleIndex * ROW_SPACING);
            } else {
                b.visible = false;
                b.active = false;
            }
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0 && maxScroll() > 0) {
            scrollOffset -= (int) Math.signum(scrollY);
            clampScroll();
            updateButtonPositions();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void onAction(int action) {
        if (action == -1) {
            if (this.minecraft != null && this.minecraft.player != null) {
                this.minecraft.player.displayClientMessage(
                        Component.literal("Frieren likes grimoires. Right-click her while holding one.").withStyle(ChatFormatting.GRAY), false);
            }
            this.onClose();
            return;
        }
        if (action == FrierenInteractPacket.OPEN_SHOP) {
            this.onClose();
            if (this.minecraft != null) {
                this.minecraft.setScreen(new FrierenShopScreen(this.menu.getEntityId()));
            }
            return;
        }
        PacketDistributor.sendToServer(new FrierenInteractPacket(this.menu.getEntityId(), action));
        if (action == FrierenInteractPacket.BAG) {
            // Do NOT call onClose() here — that would send ServerboundContainerClosePacket for the
            // FrierenMenu, which the server receives AFTER it opens FrierenBagMenu and blindly calls
            // closeContainer(), killing the just-opened bag. Instead, close the screen visually only;
            // the server closes FrierenMenu internally when it calls player.openMenu(FrierenBagMenu).
            if (this.minecraft != null) {
                this.minecraft.setScreen(null);
            }
        } else {
            this.onClose();
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;
        guiGraphics.fill(x, y, x + this.imageWidth, y + this.imageHeight, 0xC0101018);
        guiGraphics.renderOutline(x, y, this.imageWidth, this.imageHeight, 0xFF6A5ACD);

        // Scrollbar (only when there are more options than fit).
        if (maxScroll() > 0) {
            int trackX = this.leftPos + this.imageWidth - 12;
            int trackTop = this.topPos + PANEL_TOP;
            int trackHeight = MAX_VISIBLE * ROW_SPACING - 4;
            guiGraphics.fill(trackX, trackTop, trackX + 4, trackTop + trackHeight, 0xFF2A2A3A);

            int thumbHeight = Math.max(12, trackHeight * MAX_VISIBLE / defs.size());
            int range = trackHeight - thumbHeight;
            int thumbY = trackTop + (maxScroll() == 0 ? 0 : range * scrollOffset / maxScroll());
            guiGraphics.fill(trackX, thumbY, trackX + 4, thumbY + thumbHeight, 0xFF9A8CE0);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, 0xFFFFFF, false);
    }

    private record ButtonDef(String label, int action) {}
}
