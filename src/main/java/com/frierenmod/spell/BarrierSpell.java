package com.frierenmod.spell;

import com.frierenmod.entity.FrierenEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Barrier - defensive spell. Grants the player Absorption II for 20 seconds.
 */
public class BarrierSpell extends SpellBase {
    public static final BarrierSpell INSTANCE = new BarrierSpell();
    private static final Map<UUID, Long> ACTIVE_WARDS = new HashMap<>();

    public BarrierSpell() {
        super("barrier", 300);
    }

    public void cast(FrierenEntity frieren, Player player) {
        Level level = frieren.level();
        if (level.isClientSide) {
            return;
        }

        frieren.triggerCastAnimation();
        player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 400, 1));
        // A focused ward, not a dome: it only intercepts projectiles immediately around its target.
        ACTIVE_WARDS.put(player.getUUID(), level.getGameTime() + 100L);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ILLUSIONER_CAST_SPELL, SoundSource.PLAYERS, 0.8F, 1.0F);

        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.ENCHANTED_HIT,
                    player.getX(), player.getY() + 1.0D, player.getZ(), 20, 0.5D, 0.8D, 0.5D, 0.1D);
        }
    }

    /** Intercepts incoming projectiles at the protected player's position for five seconds. */
    public static void tick(ServerLevel level) {
        Iterator<Map.Entry<UUID, Long>> wards = ACTIVE_WARDS.entrySet().iterator();
        while (wards.hasNext()) {
            Map.Entry<UUID, Long> ward = wards.next();
            if (level.getGameTime() >= ward.getValue()) {
                wards.remove();
                continue;
            }
            Player player = level.getPlayerByUUID(ward.getKey());
            if (player == null || !player.isAlive()) continue;
            AABB wardBox = player.getBoundingBox().inflate(1.5D, 1.0D, 1.5D);
            for (Projectile projectile : level.getEntitiesOfClass(Projectile.class, wardBox,
                    projectile -> isHostileProjectile(projectile, player))) {
                level.sendParticles(ParticleTypes.ENCHANT, projectile.getX(), projectile.getY(), projectile.getZ(),
                        12, 0.15D, 0.15D, 0.15D, 0.04D);
                level.playSound(null, projectile.getX(), projectile.getY(), projectile.getZ(),
                        SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.55F, 1.8F);
                projectile.discard();
            }
        }
    }

    private static boolean isHostileProjectile(Projectile projectile, Player player) {
        net.minecraft.world.entity.Entity owner = projectile.getOwner();
        if (owner == player) return false;
        return !(owner instanceof FrierenEntity frieren && frieren.isOwnedBy(player));
    }
}
