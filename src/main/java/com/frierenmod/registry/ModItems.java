package com.frierenmod.registry;

import com.frierenmod.FrierenMod;
import com.frierenmod.item.FrierenStaffItem;
import com.frierenmod.item.FusionTomeItem;
import com.frierenmod.item.GrimoireItem;
import com.frierenmod.item.ManaHerbItem;
import com.frierenmod.item.PromiseRingItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(FrierenMod.MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, FrierenMod.MODID);

    // Grimoires (favorite gifts, +10 affection each)
    public static final DeferredItem<Item> GRIMOIRE_BASIC_MAGIC =
            ITEMS.registerItem("grimoire_basic_magic", GrimoireItem::new, new Item.Properties().stacksTo(16));
    public static final DeferredItem<Item> GRIMOIRE_SPIRIT_LORE =
            ITEMS.registerItem("grimoire_spirit_lore", GrimoireItem::new, new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON));
    public static final DeferredItem<Item> GRIMOIRE_ANCIENT_SPELLS =
            ITEMS.registerItem("grimoire_ancient_spells", GrimoireItem::new, new Item.Properties().stacksTo(16).rarity(Rarity.RARE));

    // Promise Ring (triggers PARTNER stage)
    public static final DeferredItem<Item> PROMISE_RING =
            ITEMS.registerItem("promise_ring", PromiseRingItem::new, new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant());

    // Mana Herb (consumable, used in trades)
    public static final DeferredItem<Item> MANA_HERB =
            ITEMS.registerItem("mana_herb", ManaHerbItem::new, new Item.Properties().food(ManaHerbItem.FOOD));

    // ----- Frieren Staff (usable spell-casting focus) -----
    public static final DeferredItem<Item> FRIEREN_STAFF =
            ITEMS.registerItem("frieren_staff", FrierenStaffItem::new,
                    new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant().setNoRepair());

    // ----- Fusion tomes -----
    public static final DeferredItem<Item> VERDANT_ZOLTRAAK_TOME =
            ITEMS.registerItem("verdant_zoltraak_tome",
                    p -> new FusionTomeItem(FusionTomeItem.FusionType.VERDANT_ZOLTRAAK, p),
                    new Item.Properties().stacksTo(1).rarity(Rarity.RARE));
    public static final DeferredItem<Item> AQUA_BARRIER_TOME =
            ITEMS.registerItem("aqua_barrier_tome",
                    p -> new FusionTomeItem(FusionTomeItem.FusionType.AQUA_BARRIER, p),
                    new Item.Properties().stacksTo(1).rarity(Rarity.RARE));
    public static final DeferredItem<Item> MANA_STORM_TOME =
            ITEMS.registerItem("mana_storm_tome",
                    p -> new FusionTomeItem(FusionTomeItem.FusionType.MANA_STORM, p),
                    new Item.Properties().stacksTo(1).rarity(Rarity.EPIC));

    // Spawn egg
    public static final DeferredItem<DeferredSpawnEggItem> FRIEREN_SPAWN_EGG =
            ITEMS.register("frieren_spawn_egg",
                    () -> new DeferredSpawnEggItem(ModEntities.FRIEREN, 0xE8E8E8, 0x3A3550, new Item.Properties()));

    public static final DeferredItem<DeferredSpawnEggItem> MIMIC_SPAWN_EGG =
            ITEMS.register("mimic_spawn_egg",
                    () -> new DeferredSpawnEggItem(ModEntities.MIMIC, 0xA0742E, 0xB8203A, new Item.Properties()));

    // Creative tab
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> FRIEREN_TAB =
            CREATIVE_TABS.register("frieren_tab", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.frierenmod"))
                    .icon(() -> PROMISE_RING.get().getDefaultInstance())
                    .displayItems((params, output) -> {
                        output.accept(GRIMOIRE_BASIC_MAGIC.get());
                        output.accept(GRIMOIRE_SPIRIT_LORE.get());
                        output.accept(GRIMOIRE_ANCIENT_SPELLS.get());
                        output.accept(PROMISE_RING.get());
                        output.accept(MANA_HERB.get());
                        output.accept(FRIEREN_STAFF.get());
                        output.accept(VERDANT_ZOLTRAAK_TOME.get());
                        output.accept(AQUA_BARRIER_TOME.get());
                        output.accept(MANA_STORM_TOME.get());
                        output.accept(FRIEREN_SPAWN_EGG.get());
                        output.accept(MIMIC_SPAWN_EGG.get());
                    })
                    .build());

    public static void addToCreativeTab(BuildCreativeModeTabContentsEvent event) {
        // Items are already added to our own tab; nothing extra required here.
    }

    private ModItems() {}
}
