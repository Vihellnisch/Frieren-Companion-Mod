package com.frierenmod.registry;

import com.frierenmod.FrierenMod;
import com.frierenmod.screen.FrierenBagMenu;
import com.frierenmod.screen.FrierenMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMenuTypes {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, FrierenMod.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<FrierenMenu>> FRIEREN_MENU =
            MENUS.register("frieren_menu", () -> IMenuTypeExtension.create(FrierenMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<FrierenBagMenu>> FRIEREN_BAG_MENU =
            MENUS.register("frieren_bag_menu", () -> IMenuTypeExtension.create(FrierenBagMenu::new));

    private ModMenuTypes() {}
}
