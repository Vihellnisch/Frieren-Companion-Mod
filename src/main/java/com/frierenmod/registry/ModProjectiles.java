package com.frierenmod.registry;

import com.frierenmod.FrierenMod;
import com.frierenmod.projectile.ZoltraakProjectile;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public final class ModProjectiles {
    public static final DeferredRegister<EntityType<?>> PROJECTILES =
            DeferredRegister.create(Registries.ENTITY_TYPE, FrierenMod.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<ZoltraakProjectile>> ZOLTRAAK =
            PROJECTILES.register("zoltraak", registerZoltraak());

    private static Supplier<EntityType<ZoltraakProjectile>> registerZoltraak() {
        return () -> EntityType.Builder.<ZoltraakProjectile>of(ZoltraakProjectile::new, MobCategory.MISC)
                .sized(0.4F, 0.4F)
                .clientTrackingRange(8)
                .updateInterval(2)
                .build("zoltraak");
    }

    private ModProjectiles() {}
}
