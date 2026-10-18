package com.frierenmod.entity.ai;

import com.frierenmod.entity.FrierenEntity;
import com.frierenmod.spell.ZoltraakSpell;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * Finds a hostile target and blasts it with Zoltraak. FRIEND+ auto-defends within 16 blocks;
 * at ALLY+ Frieren fires a three-shot spread.
 */
public class FrierenCastSpellGoal extends Goal {
    private final FrierenEntity frieren;
    private LivingEntity target;
    private int castDelay;

    public FrierenCastSpellGoal(FrierenEntity frieren) {
        this.frieren = frieren;
        this.setFlags(EnumSet.of(Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (frieren.getStage().ordinal() < FrierenEntity.Stage.FRIEND.ordinal()) {
            return false;
        }
        LivingEntity t = frieren.getTarget();
        if (t == null || !t.isAlive()) {
            return false;
        }
        if (frieren.distanceToSqr(t) > 256.0D) { // 16 blocks
            return false;
        }
        this.target = t;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity t = frieren.getTarget();
        return t != null && t.isAlive() && frieren.distanceToSqr(t) <= 256.0D
                && frieren.getStage().ordinal() >= FrierenEntity.Stage.FRIEND.ordinal();
    }

    @Override
    public void start() {
        this.castDelay = 10;
    }

    @Override
    public void stop() {
        this.target = null;
    }

    @Override
    public void tick() {
        LivingEntity t = frieren.getTarget();
        if (t == null) {
            return;
        }
        frieren.getLookControl().setLookAt(t, 30.0F, 30.0F);

        if (this.castDelay > 0) {
            this.castDelay--;
            return;
        }

        if (frieren.canCast("zoltraak")) {
            boolean spread = frieren.getStage().ordinal() >= FrierenEntity.Stage.ALLY.ordinal();
            ZoltraakSpell.INSTANCE.cast(frieren, t, spread);
            frieren.setCooldown("zoltraak", ZoltraakSpell.INSTANCE.getCooldownTicks());
            this.castDelay = ZoltraakSpell.INSTANCE.getCooldownTicks();
        }
    }
}
