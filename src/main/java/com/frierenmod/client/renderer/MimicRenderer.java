package com.frierenmod.client.renderer;

import com.frierenmod.FrierenMod;
import com.frierenmod.client.model.MimicModel;
import com.frierenmod.entity.MimicEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

public class MimicRenderer extends GeoEntityRenderer<MimicEntity> {
    private static final ResourceLocation MOUTH_TEXTURE = FrierenMod.id("textures/entity/mimic_mouth.png");

    public MimicRenderer(EntityRendererProvider.Context context) {
        super(context, new MimicModel());
        this.shadowRadius = 0.0F; // chests don't cast entity shadows

        // Second pass: teeth, tongue, maw and gums. Their UVs are transparent in the chest texture,
        // and the chest body's UVs are transparent here.
        addRenderLayer(new GeoRenderLayer<>(this) {
            @Override
            public void render(PoseStack poseStack, MimicEntity animatable, BakedGeoModel bakedModel, RenderType renderType,
                               MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick,
                               int packedLight, int packedOverlay) {
                RenderType mouth = RenderType.entityCutoutNoCull(MOUTH_TEXTURE);
                getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, mouth,
                        bufferSource.getBuffer(mouth), partialTick, packedLight, packedOverlay, 0xFFFFFFFF);
            }
        });
    }

    @Override
    protected float getDeathMaxRotation(MimicEntity animatable) {
        return 0.0F; // it just stops moving, like a chest
    }
}
