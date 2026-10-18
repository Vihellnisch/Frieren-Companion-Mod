package com.frierenmod.client;

import com.frierenmod.FrierenMod;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderHandEvent;

/** First person: while you pat Frieren, your empty hand reaches forward and strokes her head. */
@EventBusSubscriber(modid = FrierenMod.MODID, value = Dist.CLIENT)
public final class PetHandRenderer {
    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        Minecraft mc = Minecraft.getInstance();
        AbstractClientPlayer player = mc.player;
        if (player == null || event.getHand() != InteractionHand.MAIN_HAND || !event.getItemStack().isEmpty()) return;
        float a = ClientPetState.amount(player.getId(), event.getPartialTick());
        if (a <= 0.0F) return;
        event.setCanceled(true);

        boolean right = player.getMainArm() == HumanoidArm.RIGHT;
        float f = right ? 1.0F : -1.0F;
        float time = player.tickCount + event.getPartialTick();
        float stroke = ClientPetState.stroke(time) * a;
        PoseStack ps = event.getPoseStack();
        ps.pushPose();
        // In this space the arm model points straight up from its shoulder. Put the shoulder just
        // below the bottom-right corner (rising into view as the pat starts), tip the arm away from
        // the camera and lean it in, so the hand lands on top of her head. Then stroke.
        float pivotY = (player.getSkin().model() == net.minecraft.client.resources.PlayerSkin.Model.SLIM ? 2.5F : 2.0F) / 16.0F;
        ps.translate(f * (0.38F + 5.0F / 16.0F), 0.06F - 0.50F * (1.0F - a) - pivotY, -0.68F);
        ps.mulPose(Axis.ZP.rotationDegrees(f * (17.0F + 5.0F * stroke)));
        ps.mulPose(Axis.XP.rotationDegrees(-25.0F + 3.0F * Mth.abs(stroke)));
        PlayerRenderer renderer = (PlayerRenderer) mc.getEntityRenderDispatcher().getRenderer(player);
        if (right) {
            renderer.renderRightHand(ps, event.getMultiBufferSource(), event.getPackedLight(), player);
        } else {
            renderer.renderLeftHand(ps, event.getMultiBufferSource(), event.getPackedLight(), player);
        }
        ps.popPose();
    }

    private PetHandRenderer() {}
}
