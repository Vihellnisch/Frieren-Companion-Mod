package com.frierenmod.entity.ai;

import com.frierenmod.entity.FrierenEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;

/**
 * Frieren heals herself when her health drops low, pausing briefly to cast.
 *
 * Triggers when health &lt; 30% of max, respecting a 15-second cooldown between heals.
 */
public class FrierenSelfHealGoal extends Goal {
    private static final int COOLDOWN_TICKS = 300; // 15 seconds
    private static final int CAST_TICKS = 20;      // 1 second freeze
    private static final float HEAL_AMOUNT = 6.0F;

    private final FrierenEntity frieren;
    private int cooldownUntil = 0;
    private int castTicksLeft = 0;

    public FrierenSelfHealGoal(FrierenEntity frieren) {
        this.frieren = frieren;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (frieren.tickCount < cooldownUntil) return false;
        if (frieren.getHealth() >= frieren.getMaxHealth() * 0.3F) return false;
        return frieren.isAlive();
    }

    @Override
    public boolean canContinueToUse() {
        return castTicksLeft > 0;
    }

    @Override
    public void start() {
        this.castTicksLeft = CAST_TICKS;
        this.cooldownUntil = frieren.tickCount + COOLDOWN_TICKS;
        this.frieren.getNavigation().stop();

        // Heal.
        this.frieren.heal(HEAL_AMOUNT);

        // Sparkle / heart particles.
        if (frieren.level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.HEART, frieren.getX(), frieren.getY() + 1.6D, frieren.getZ(),
                    6, 0.4D, 0.6D, 0.4D, 0.02D);
            sl.sendParticles(ParticleTypes.ENCHANT, frieren.getX(), frieren.getY() + 1.0D, frieren.getZ(),
                    20, 0.5D, 1.0D, 0.5D, 0.1D);
        }

        // Notify the nearby bound owner.
        Player owner = frieren.getOwner();
        if (owner != null && owner.distanceToSqr(frieren) < 400.0D) {
            owner.sendSystemMessage(Component.literal("Frieren: *heals herself* ...I'm fine.")
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        }

        this.frieren.triggerCastAnimation();
    }

    @Override
    public void stop() {
        this.castTicksLeft = 0;
    }

    @Override
    public void tick() {
        if (castTicksLeft > 0) {
            castTicksLeft--;
        }
        // Stay put during the heal.
        this.frieren.getNavigation().stop();
        this.frieren.setDeltaMovement(0, this.frieren.getDeltaMovement().y, 0);
    }
}
