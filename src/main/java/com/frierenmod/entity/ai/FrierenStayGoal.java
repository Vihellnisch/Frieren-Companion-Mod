package com.frierenmod.entity.ai;

import com.frierenmod.entity.FrierenEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * Keeps Frieren in place when she has been told to stay (and has no combat target).
 */
public class FrierenStayGoal extends Goal {
    private final FrierenEntity frieren;

    public FrierenStayGoal(FrierenEntity frieren) {
        this.frieren = frieren;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        return frieren.isStaying() && frieren.getTarget() == null;
    }

    @Override
    public boolean canContinueToUse() {
        return frieren.isStaying() && frieren.getTarget() == null;
    }

    @Override
    public void start() {
        frieren.getNavigation().stop();
    }

    @Override
    public void tick() {
        frieren.getNavigation().stop();
    }
}
