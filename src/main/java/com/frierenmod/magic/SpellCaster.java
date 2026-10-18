package com.frierenmod.magic;

import com.frierenmod.projectile.ZoltraakProjectile;
import com.frierenmod.spell.MidasSpell;
import com.frierenmod.spell.UselessSpells;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Player-driven spell casting used by the Frieren Staff and fusion tomes. Applies mana
 * proficiency scaling at cast time.
 */
public final class SpellCaster {

    /** The ordered list of spells the staff can select. Mana Vision is always available. */
    public enum StaffSpell {
        MANA_VISION("Mana Vision", 0, 0),
        MANA_SENSING("Mana Sensing", PlayerMagic.SPELL_MANA_SENSING, 200),
        ZOLTRAAK_T1("Zoltraak I", PlayerMagic.SPELL_ZOLTRAAK_T1, 20),
        ZOLTRAAK_T2("Zoltraak II", PlayerMagic.SPELL_ZOLTRAAK_T2, 15),
        ZOLTRAAK_T3("Zoltraak III", PlayerMagic.SPELL_ZOLTRAAK_T3, 40),
        EINSAM("Einsam", PlayerMagic.SPELL_EINSAM, 200),
        MIDAS("Midas", PlayerMagic.SPELL_MIDAS, 1200),
        // Useless spells, in Frieren's words. Appended so saved selections keep their meaning.
        FLOWER_FIELD("Flower Field", PlayerMagic.SPELL_FLOWER_FIELD, 100),
        RUST_REMOVAL("Rust Removal", PlayerMagic.SPELL_RUST_REMOVAL, 30),
        CREATE_WATER("Create Water", PlayerMagic.SPELL_CREATE_WATER, 40),
        CREATE_TORCH("Create Torch", PlayerMagic.SPELL_CREATE_TORCH, 10),
        GROW_GRASS("Grow Grass", PlayerMagic.SPELL_GROW_GRASS, 60),
        GOLDEN_BUTTERFLIES("Golden Butterflies", PlayerMagic.SPELL_GOLDEN_BUTTERFLIES, 200);

        public final String displayName;
        public final int requiredBit;
        public final int cooldownTicks;

        StaffSpell(String displayName, int requiredBit, int cooldownTicks) {
            this.displayName = displayName;
            this.requiredBit = requiredBit;
            this.cooldownTicks = cooldownTicks;
        }

        /** Mana Vision is always available; the rest need the matching learned bit. */
        public boolean isAvailable(Player player) {
            return this == MANA_VISION || PlayerMagic.hasLearned(player, requiredBit);
        }
    }

    private SpellCaster() {}

    /**
     * Cast the given staff spell for the player (server side only). Returns true if the spell
     * fired (so the caller can apply the cooldown and increment proficiency).
     */
    public static boolean cast(StaffSpell spell, Player player) {
        Level level = player.level();
        return switch (spell) {
            case MANA_VISION -> false; // handled by the staff toggle, never reaches here
            case MANA_SENSING -> castManaSensing(level, player);
            case ZOLTRAAK_T1 -> fireZoltraak(level, player, 18.0F, 3.0F, 0.0F, 0.0F, false);
            case ZOLTRAAK_T2 -> fireZoltraakHoming(level, player, 10.0F, 2.0F);
            case ZOLTRAAK_T3 -> fireZoltraak(level, player, 0.0F, 2.5F, 0.0F,
                    PlayerMagic.t3ExplosionRadius(player), false);
            case EINSAM -> castEinsam(level, player);
            case MIDAS -> level instanceof ServerLevel serverLevel && MidasSpell.cast(serverLevel, player);
            case FLOWER_FIELD -> UselessSpells.flowerField(player, 6);
            case RUST_REMOVAL -> UselessSpells.rustRemoval(player);
            case CREATE_WATER -> UselessSpells.createWater(player);
            case CREATE_TORCH -> UselessSpells.createTorch(player);
            case GROW_GRASS -> UselessSpells.growGrass(player, 6);
            case GOLDEN_BUTTERFLIES -> UselessSpells.butterflies(player, 12, false);
        };
    }

    static boolean fireZoltraakHoming(Level level, Player player, float baseDamage, float baseVelocity) {
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.EVOKER_CAST_SPELL, SoundSource.PLAYERS, 1.0F, 0.9F);
        ZoltraakProjectile projectile = new ZoltraakProjectile(level, player);
        projectile.setDamage(baseDamage * PlayerMagic.damageMultiplier(player));
        projectile.setHoming(true);
        float velocity = baseVelocity * PlayerMagic.velocityMultiplier(player);
        projectile.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, velocity, 0.4F);
        level.addFreshEntity(projectile);
        return true;
    }

    static boolean fireZoltraak(Level level, Player player, float baseDamage, float baseVelocity,
                                        float knockback, float explosionRadius, boolean breaksBlocks) {
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.EVOKER_CAST_SPELL, SoundSource.PLAYERS, 1.0F, 1.2F);
        ZoltraakProjectile projectile = new ZoltraakProjectile(level, player);
        projectile.setDamage(baseDamage * PlayerMagic.damageMultiplier(player));
        projectile.setKnockbackStrength(knockback);
        if (explosionRadius > 0.0F) {
            projectile.setExplosion(explosionRadius, breaksBlocks);
        }
        float velocity = baseVelocity * PlayerMagic.velocityMultiplier(player);
        projectile.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, velocity, 0.4F);
        level.addFreshEntity(projectile);
        return true;
    }

    private static boolean castManaSensing(Level level, Player player) {
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.4F);
        AABB box = player.getBoundingBox().inflate(20.0D);
        List<LivingEntity> mobs = level.getEntitiesOfClass(LivingEntity.class, box,
                e -> e != player && e.isAlive());
        for (LivingEntity mob : mobs) {
            mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, 200, 0, false, false));
        }
        if (level instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.0D, player.getZ(),
                    30, 1.0D, 1.0D, 1.0D, 0.05D);
        }
        player.displayClientMessage(Component.literal("Mana Sensing: " + mobs.size() + " creatures revealed.")
                .withStyle(ChatFormatting.AQUA), true);
        return true;
    }

    private static boolean castEinsam(Level level, Player player) {
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.EVOKER_CAST_SPELL, SoundSource.PLAYERS, 1.0F, 0.8F);
        float damage = 5.0F * PlayerMagic.damageMultiplier(player);
        AABB box = player.getBoundingBox().inflate(5.0D);
        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, box,
                e -> e != player && e.isAlive());
        for (LivingEntity target : targets) {
            target.hurt(player.damageSources().indirectMagic(player, player), damage);
            Vec3 dir = target.position().subtract(player.position()).normalize();
            target.knockback(1.2D, -dir.x, -dir.z);
        }
        if (level instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.SONIC_BOOM, player.getX(), player.getY() + 1.0D, player.getZ(),
                    1, 0.0D, 0.0D, 0.0D, 0.0D);
            sl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, player.getX(), player.getY() + 0.5D, player.getZ(),
                    40, 2.5D, 0.5D, 2.5D, 0.1D);
        }
        return true;
    }
}
