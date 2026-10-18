package com.frierenmod.client.model;

import com.frierenmod.FrierenMod;
import com.frierenmod.entity.FrierenEntity;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;

/**
 * GeckoLib model wiring. DefaultedEntityGeoModel resolves:
 *   model     -> assets/frierenmod/geo/entity/frieren.geo.json
 *   texture   -> assets/frierenmod/textures/entity/frieren.png
 *   animation -> assets/frierenmod/animations/entity/frieren.animation.json
 */
public class FrierenModel extends DefaultedEntityGeoModel<FrierenEntity> {
    public FrierenModel() {
        super(FrierenMod.id("frieren"));
    }
}
