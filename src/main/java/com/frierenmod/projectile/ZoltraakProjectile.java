package com.frierenmod.projectile;

import com.frierenmod.registry.ModProjectiles;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The Zoltraak offensive magic blast fired by Frieren or the Zoltraak Scroll.
 */
public class ZoltraakProjectile extends ThrowableProjectile {
    private float damage = 8.0F;
    private float explosionRadius = 0.0F;
    private boolean breaksBlocks = false;
    private float knockbackStrength = 0.8F;
    private boolean homing = false;
    private Effect effect = Effect.NONE;
    private static final net.minecraft.network.syncher.EntityDataAccessor<Byte> DATA_EFFECT =
            net.minecraft.network.syncher.SynchedEntityData.defineId(ZoltraakProjectile.class,
                    net.minecraft.network.syncher.EntityDataSerializers.BYTE);

    /** Extra effects from spell fusions. */
    public enum Effect { NONE, GOLDEN, SEARING }

    public ZoltraakProjectile(EntityType<? extends ZoltraakProjectile> type, Level level) {
        super(type, level);
    }

    public ZoltraakProjectile(Level level, LivingEntity owner) {
        super(ModProjectiles.ZOLTRAAK.get(), owner, level);
    }

    public void setDamage(float damage) {
        this.damage = damage;
    }

    /** Configure an explosion on impact. radius 0 disables the explosion. */
    public void setExplosion(float radius, boolean breaksBlocks) {
        this.explosionRadius = radius;
        this.breaksBlocks = breaksBlocks;
    }

    public void setKnockbackStrength(float strength) {
        this.knockbackStrength = strength;
    }

    public void setHoming(boolean homing) {
        this.homing = homing;
    }

    public void setEffect(Effect effect) {
        this.effect = effect;
        this.entityData.set(DATA_EFFECT, (byte) effect.ordinal());
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        builder.define(DATA_EFFECT, (byte) 0); // fusion effect, for the client's particle trail
    }

    @Override
    protected double getDefaultGravity() {
        return 0.0D; // Straight magical beam, no gravity.
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            Vec3 pos = this.position();
            this.level().addParticle(ParticleTypes.WITCH, pos.x, pos.y, pos.z, 0, 0, 0);
            this.level().addParticle(ParticleTypes.SOUL_FIRE_FLAME, pos.x, pos.y, pos.z, 0, 0, 0);
            byte fx = this.entityData.get(DATA_EFFECT);
            if (fx == Effect.GOLDEN.ordinal()) this.level().addParticle(ParticleTypes.WAX_ON, pos.x, pos.y, pos.z, 0, 0, 0);
            if (fx == Effect.SEARING.ordinal()) this.level().addParticle(ParticleTypes.FLAME, pos.x, pos.y, pos.z, 0, 0, 0);
        } else {
            if (this.tickCount > 100) {
                this.discard();
                return;
            }
            if (this.homing) {
                steerTowardNearestTarget();
            }
        }
    }

    private void steerTowardNearestTarget() {
        Entity owner = this.getOwner();
        net.minecraft.world.phys.AABB searchBox = this.getBoundingBox().inflate(24.0);
        java.util.List<LivingEntity> targets = this.level().getEntitiesOfClass(LivingEntity.class, searchBox,
                e -> e != owner && e.isAlive() && e instanceof net.minecraft.world.entity.monster.Enemy);
        if (targets.isEmpty()) {
            targets = this.level().getEntitiesOfClass(LivingEntity.class, searchBox,
                    e -> e != owner && e.isAlive() && !(e instanceof net.minecraft.world.entity.player.Player));
        }
        if (targets.isEmpty()) return;

        LivingEntity nearest = targets.stream()
                .min(java.util.Comparator.comparingDouble(e -> this.distanceToSqr(e.getEyePosition())))
                .orElse(null);
        if (nearest == null) return;

        Vec3 toTarget = nearest.getEyePosition().subtract(this.position()).normalize();
        Vec3 current = this.getDeltaMovement();
        double speed = current.length();
        Vec3 steered = current.add(toTarget.scale(0.3)).normalize().scale(speed);
        this.setDeltaMovement(steered);
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        if (this.level().isClientSide) {
            return;
        }
        Entity target = result.getEntity();
        Entity owner = this.getOwner();

        target.hurt(this.damageSources().indirectMagic(this, owner), this.damage);
        if (effect == Effect.SEARING) {
            target.igniteForSeconds(6.0F);
        } else if (effect == Effect.GOLDEN && target instanceof LivingEntity living) {
            if (!living.isAlive()) {
                // Midas touch: what it kills turns into gold.
                int nuggets = 3 + this.random.nextInt(5);
                living.spawnAtLocation(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.GOLD_NUGGET, nuggets));
            } else {
                living.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 100, 3));
            }
            if (this.level() instanceof net.minecraft.server.level.ServerLevel sl) {
                sl.sendParticles(ParticleTypes.WAX_ON, target.getX(), target.getY() + target.getBbHeight() / 2, target.getZ(),
                        30, 0.4D, target.getBbHeight() / 2, 0.4D, 0.1D);
            }
        }

        if (target instanceof LivingEntity living && this.knockbackStrength > 0.0F) {
            Vec3 knock = this.getDeltaMovement().normalize().scale(this.knockbackStrength);
            living.knockback(this.knockbackStrength, -knock.x, -knock.z);
        }
    }

    @Override
    protected void onHitBlock(net.minecraft.world.phys.BlockHitResult result) {
        super.onHitBlock(result);
        if (effect == Effect.GOLDEN && this.level() instanceof net.minecraft.server.level.ServerLevel sl) {
            Entity owner = this.getOwner();
            com.frierenmod.spell.MidasSpell.transmuteTemporary(sl, owner == null ? null : owner.getUUID(),
                    result.getBlockPos(), 1);
        }
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (!this.level().isClientSide) {
            if (this.explosionRadius > 0.0F) {
                Level.ExplosionInteraction interaction = this.breaksBlocks
                        ? Level.ExplosionInteraction.TNT
                        : Level.ExplosionInteraction.NONE;
                this.level().explode(this, this.getX(), this.getY(), this.getZ(),
                        this.explosionRadius, interaction);
            } else {
                this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                        SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 0.6F, 1.5F);
            }
            this.discard();
        }
    }
}
