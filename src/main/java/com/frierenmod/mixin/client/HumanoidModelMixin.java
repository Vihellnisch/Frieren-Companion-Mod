package com.frierenmod.mixin.client;

import com.frierenmod.client.ClientPetState;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Third person: a player patting Frieren raises their main arm and strokes. Runs before
 * PlayerModel copies the arm onto the sleeve layer.
 */
@Mixin(HumanoidModel.class)
public abstract class HumanoidModelMixin<T extends LivingEntity> {
    @Shadow
    @Final
    public ModelPart rightArm;
    @Shadow
    @Final
    public ModelPart leftArm;

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void frierenmod$headPat(T entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                                    float netHeadYaw, float headPitch, CallbackInfo ci) {
        if (!(entity instanceof Player)) return;
        float a = ClientPetState.amount(entity.getId(), ageInTicks - entity.tickCount);
        if (a <= 0.0F) return;
        boolean right = entity.getMainArm() == HumanoidArm.RIGHT;
        ModelPart arm = right ? rightArm : leftArm;
        float side = right ? 1.0F : -1.0F;
        float stroke = ClientPetState.stroke(ageInTicks);
        arm.xRot = Mth.lerp(a, arm.xRot, -1.75F + 0.08F * Mth.abs(stroke));
        arm.yRot = Mth.lerp(a, arm.yRot, side * (-0.35F + 0.25F * stroke));
        arm.zRot = Mth.lerp(a, arm.zRot, 0.0F);
    }
}
