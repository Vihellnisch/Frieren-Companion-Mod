package com.frierenmod.screen;

import com.frierenmod.entity.FrierenEntity;
import com.frierenmod.registry.ModMenuTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public class FrierenBagMenu extends AbstractContainerMenu {

    static final int BAG_ROWS = 3;
    static final int BAG_COLS = 9;
    static final int BAG_SIZE = BAG_ROWS * BAG_COLS; // 27
    static final int ARMOR_SIZE = 4;

    private final int entityId;
    private final SimpleContainer bag;
    private final SimpleContainer armorSlots;

    // Client-side constructor (IMenuTypeExtension factory)
    public FrierenBagMenu(int containerId, Inventory playerInv, FriendlyByteBuf buf) {
        super(ModMenuTypes.FRIEREN_BAG_MENU.get(), containerId);
        this.entityId = buf.readInt();
        FrierenEntity frieren = findEntity(playerInv, this.entityId);
        this.bag = frieren != null ? frieren.getBag() : new SimpleContainer(BAG_SIZE);
        this.armorSlots = frieren != null ? frieren.getFrierenArmor() : new SimpleContainer(ARMOR_SIZE);
        addAllSlots(playerInv);
    }

    // Server-side constructor
    public FrierenBagMenu(int containerId, Inventory playerInv, FrierenEntity frieren) {
        super(ModMenuTypes.FRIEREN_BAG_MENU.get(), containerId);
        this.entityId = frieren.getId();
        this.bag = frieren.getBag();
        this.armorSlots = frieren.getFrierenArmor();
        addAllSlots(playerInv);
    }

    private void addAllSlots(Inventory playerInv) {
        // Bag slots 0-26: 3 rows × 9 at y=18, 36, 54
        for (int row = 0; row < BAG_ROWS; row++) {
            for (int col = 0; col < BAG_COLS; col++) {
                this.addSlot(new Slot(this.bag, col + row * BAG_COLS, 8 + col * 18, 18 + row * 18));
            }
        }
        // Player main inventory 27-53: y=102, 120, 138 (shifted down 18px for armor row)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, 102 + row * 18));
            }
        }
        // Hotbar 54-62: y=158
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInv, col, 8 + col * 18, 158));
        }
        // Armor slots 63-66: HEAD, CHEST, LEGS, FEET (at y=80, in the armor row)
        this.addSlot(new ArmorTypeSlot(this.armorSlots, 0,  8, 80, EquipmentSlot.HEAD));
        this.addSlot(new ArmorTypeSlot(this.armorSlots, 1, 26, 80, EquipmentSlot.CHEST));
        this.addSlot(new ArmorTypeSlot(this.armorSlots, 2, 44, 80, EquipmentSlot.LEGS));
        this.addSlot(new ArmorTypeSlot(this.armorSlots, 3, 62, 80, EquipmentSlot.FEET));
    }

    @Override
    public boolean stillValid(Player player) {
        Entity e = player.level().getEntity(entityId);
        return e instanceof FrierenEntity f && f.isAlive();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack item = slot.getItem();
            result = item.copy();
            if (index < BAG_SIZE) {
                // Bag → player inventory
                if (!this.moveItemStackTo(item, BAG_SIZE, BAG_SIZE + 36, true)) {
                    return ItemStack.EMPTY;
                }
            } else if (index < BAG_SIZE + 36) {
                // Player inventory → bag
                if (!this.moveItemStackTo(item, 0, BAG_SIZE, false)) {
                    return ItemStack.EMPTY;
                }
            }
            // Armor slots (63-66): no shift-click, player drags in/out manually
            if (item.isEmpty()) slot.set(ItemStack.EMPTY);
            else slot.setChanged();
        }
        return result;
    }

    @Nullable
    private static FrierenEntity findEntity(Inventory playerInv, int entityId) {
        Entity e = playerInv.player.level().getEntity(entityId);
        return e instanceof FrierenEntity f ? f : null;
    }

    private static final class ArmorTypeSlot extends Slot {
        private final EquipmentSlot equipSlot;

        ArmorTypeSlot(Container container, int index, int x, int y, EquipmentSlot equipSlot) {
            super(container, index, x, y);
            this.equipSlot = equipSlot;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            if (stack.isEmpty()) return false;
            if (!(stack.getItem() instanceof ArmorItem a)) return false;
            return a.getType().getSlot() == equipSlot;
        }
    }
}
