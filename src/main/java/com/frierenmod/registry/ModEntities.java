package com.frierenmod.registry;

import com.frierenmod.FrierenMod;
import com.frierenmod.entity.FrierenEntity;
import com.frierenmod.entity.GoldenButterflyEntity;
import com.frierenmod.entity.MimicEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(net.minecraft.core.registries.Registries.ENTITY_TYPE, FrierenMod.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<FrierenEntity>> FRIEREN =
            ENTITIES.register("frieren", registerFrieren());

    public static final DeferredHolder<EntityType<?>, EntityType<MimicEntity>> MIMIC =
            ENTITIES.register("mimic", () -> EntityType.Builder.of(MimicEntity::new, MobCategory.MONSTER)
                    .sized(0.875F, 0.875F)
                    .clientTrackingRange(8)
                    .build("mimic"));

    public static final DeferredHolder<EntityType<?>, EntityType<GoldenButterflyEntity>> GOLDEN_BUTTERFLY =
            ENTITIES.register("golden_butterfly", () -> EntityType.Builder.<GoldenButterflyEntity>of(GoldenButterflyEntity::new, MobCategory.MISC)
                    .sized(0.3F, 0.3F)
                    .clientTrackingRange(8)
                    .updateInterval(2)
                    .build("golden_butterfly"));

    private static Supplier<EntityType<FrierenEntity>> registerFrieren() {
        return () -> EntityType.Builder.of(FrierenEntity::new, MobCategory.CREATURE)
                .sized(0.6F, 1.85F)
                .clientTrackingRange(10)
                .build("frieren");
    }

    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(FRIEREN.get(), FrierenEntity.createAttributes().build());
        event.put(MIMIC.get(), MimicEntity.createAttributes().build());
    }

    private ModEntities() {}
}
