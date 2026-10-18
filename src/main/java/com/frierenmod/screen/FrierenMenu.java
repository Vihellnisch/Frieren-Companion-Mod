package com.frierenmod.screen;

import com.frierenmod.entity.FrierenEntity;
import com.frierenmod.registry.ModMenuTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/**
 * A slot-less menu that carries the Frieren entity id so the client screen can display
 * stage-appropriate action buttons.
 */
public class FrierenMenu extends AbstractContainerMenu {
    private final int entityId;

    // Server-side constructor.
    public FrierenMenu(int containerId, Inventory inv, FrierenEntity frieren) {
        super(ModMenuTypes.FRIEREN_MENU.get(), containerId);
        this.entityId = frieren.getId();
    }

    // Client-side constructor (reads network data).
    public FrierenMenu(int containerId, Inventory inv, RegistryFriendlyByteBuf buf) {
        super(ModMenuTypes.FRIEREN_MENU.get(), containerId);
        this.entityId = buf.readInt();
    }

    public int getEntityId() {
        return entityId;
    }

    public FrierenEntity getFrieren(Player player) {
        Entity entity = player.level().getEntity(entityId);
        return entity instanceof FrierenEntity frieren ? frieren : null;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        Entity entity = player.level().getEntity(entityId);
        return entity instanceof FrierenEntity && entity.isAlive() && entity.distanceToSqr(player) < 64.0D;
    }
}
