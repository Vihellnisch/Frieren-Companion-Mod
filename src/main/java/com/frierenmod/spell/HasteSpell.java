package com.frierenmod.spell;

import com.frierenmod.entity.FrierenEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Haste Buff - grants Haste II and Speed I for 3 minutes. 5-minute cooldown.
 */
public class HasteSpell extends SpellBase {
    public static final HasteSpell INSTANCE = new HasteSpell();

    public HasteSpell() {
        super("haste", 6000); // 5-minute cooldown
    }

    public void cast(FrierenEntity frieren, Player player) {
        Level level = frieren.level();
        if (level.isClientSide) {
            return;
        }

        frieren.triggerCastAnimation();
        player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 3600, 1));   // Haste II
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 3600, 0)); // Speed I
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.7F, 1.4F);

        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.GLOW,
                    player.getX(), player.getY() + 1.0D, player.getZ(), 15, 0.4D, 0.6D, 0.4D, 0.05D);
        }
    }
}
