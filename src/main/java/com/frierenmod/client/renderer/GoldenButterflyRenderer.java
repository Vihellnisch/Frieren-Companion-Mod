package com.frierenmod.client.renderer;

import com.frierenmod.FrierenMod;
import com.frierenmod.entity.GoldenButterflyEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Two glowing wings that flap around the body's long axis. */
public class GoldenButterflyRenderer extends EntityRenderer<GoldenButterflyEntity> {
    private static final ResourceLocation TEXTURE = FrierenMod.id("textures/entity/golden_butterfly.png");
    private static final float SPAN = 0.32F, LENGTH = 0.38F;

    public GoldenButterflyRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
    }

    @Override
    public void render(GoldenButterflyEntity entity, float yaw, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int packedLight) {
        pose.pushPose();
        pose.translate(0.0D, 0.15D, 0.0D);
        pose.mulPose(Axis.YP.rotationDegrees(-Mth.lerp(partialTick, entity.yRotO, entity.getYRot())));
        pose.mulPose(Axis.XP.rotationDegrees(-15.0F)); // nose up a little, like it's climbing
        float time = entity.tickCount + partialTick + entity.getId() * 7.0F;
        float flap = Mth.sin(time * 1.3F) * 65.0F + 15.0F;
        int color = entity.isExploding()
                ? (((int) time / 3) % 2 == 0 ? 0xFFFFB070 : 0xFFFF7050)
                : 0xFFFFFFFF;
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        wing(pose, vc, flap, false, color);
        wing(pose, vc, flap, true, color);
        pose.popPose();
        super.render(entity, yaw, partialTick, pose, buffers, packedLight);
    }

    private static void wing(PoseStack pose, VertexConsumer vc, float flap, boolean right, int color) {
        pose.pushPose();
        pose.mulPose(Axis.ZP.rotationDegrees(right ? -flap : flap));
        PoseStack.Pose p = pose.last();
        float s = right ? -SPAN : SPAN;
        // The texture is the left wing; the right one is the same picture mirrored.
        float u0 = right ? 1.0F : 0.0F, u1 = right ? 0.0F : 1.0F;
        vertex(vc, p, 0.0F, 0.0F, -LENGTH / 2, u0, 1.0F, color);
        vertex(vc, p, s, 0.0F, -LENGTH / 2, u1, 1.0F, color);
        vertex(vc, p, s, 0.0F, LENGTH / 2, u1, 0.0F, color);
        vertex(vc, p, 0.0F, 0.0F, LENGTH / 2, u0, 0.0F, color);
        pose.popPose();
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose p, float x, float y, float z, float u, float v, int color) {
        vc.addVertex(p, x, y, z).setColor(color).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT).setNormal(p, 0.0F, 1.0F, 0.0F);
    }

    @Override
    public ResourceLocation getTextureLocation(GoldenButterflyEntity entity) {
        return TEXTURE;
    }
}
