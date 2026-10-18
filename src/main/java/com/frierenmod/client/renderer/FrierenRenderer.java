package com.frierenmod.client.renderer;

import com.frierenmod.client.model.FrierenModel;
import com.frierenmod.entity.FrierenEntity;
import com.frierenmod.registry.ModItems;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.BlockAndItemGeoLayer;

public class FrierenRenderer extends GeoEntityRenderer<FrierenEntity> {
    private ItemStack staffStack;

    public FrierenRenderer(EntityRendererProvider.Context context) {
        super(context, new FrierenModel());
        this.shadowRadius = 0.4F;

        addRenderLayer(new BlockAndItemGeoLayer<>(this) {
            @Override
            protected ItemStack getStackForBone(GeoBone bone, FrierenEntity animatable) {
                if (!"staff".equals(bone.getName())) return null;
                if (staffStack == null) staffStack = new ItemStack(ModItems.FRIEREN_STAFF.get());
                return staffStack;
            }

            @Override
            protected ItemDisplayContext getTransformTypeForStack(GeoBone bone, ItemStack stack, FrierenEntity animatable) {
                return ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
            }

            @Override
            protected void renderStackForBone(PoseStack poseStack, GeoBone bone, ItemStack stack, FrierenEntity animatable,
                                              MultiBufferSource bufferSource, float partialTick, int packedLight, int packedOverlay) {
                // GeckoLib bones are Y-up; vanilla hand transforms expect the arm pointing along -Z.
                poseStack.mulPose(Axis.XP.rotationDegrees(-90F));
                super.renderStackForBone(poseStack, bone, stack, animatable, bufferSource, partialTick, packedLight, packedOverlay);
            }
        });
    }
}
