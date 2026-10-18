package com.frierenmod.spell;

import com.frierenmod.entity.FrierenEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Healing - restores 6 HP (3 hearts) to the target player.
 */
public class HealingSpell extends SpellBase {
    public static final HealingSpell INSTANCE = new HealingSpell();

    public HealingSpell() {
        super("healing", 100);
    }

    public void cast(FrierenEntity frieren, Player player) {
        Level level = frieren.level();
        if (level.isClientSide) {
            return;
        }

        frieren.triggerCastAnimation();
        player.heal(6.0F);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6F, 1.6F);

        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.HEART,
                    player.getX(), player.getY() + 1.0D, player.getZ(), 8, 0.4D, 0.6D, 0.4D, 0.02D);
            serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                    player.getX(), player.getY() + 1.0D, player.getZ(), 12, 0.4D, 0.6D, 0.4D, 0.05D);
        }
    }
}
