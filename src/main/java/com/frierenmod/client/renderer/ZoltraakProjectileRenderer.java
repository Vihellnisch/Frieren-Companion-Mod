package com.frierenmod.client.renderer;

import com.frierenmod.projectile.ZoltraakProjectile;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/**
 * The Zoltraak beam is drawn entirely with particles spawned by the projectile, so the renderer
 * itself draws nothing.
 */
public class ZoltraakProjectileRenderer extends EntityRenderer<ZoltraakProjectile> {
    public ZoltraakProjectileRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(ZoltraakProjectile entity) {
        return ResourceLocation.withDefaultNamespace("textures/particle/generic_0.png");
    }
}
