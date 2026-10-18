package com.frierenmod.client.model;

import com.frierenmod.FrierenMod;
import com.frierenmod.entity.MimicEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;

/**
 * mimic.geo.json / mimic.animation.json, drawn with the vanilla chest texture so a hidden Mimic
 * matches real chests (and resource packs). The mouth is a second pass, see MimicRenderer.
 */
public class MimicModel extends DefaultedEntityGeoModel<MimicEntity> {
    public static final ResourceLocation CHEST_TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/entity/chest/normal.png");

    public MimicModel() {
        super(FrierenMod.id("mimic"));
    }

    @Override
    public ResourceLocation getTextureResource(MimicEntity animatable) {
        return CHEST_TEXTURE;
    }
}
