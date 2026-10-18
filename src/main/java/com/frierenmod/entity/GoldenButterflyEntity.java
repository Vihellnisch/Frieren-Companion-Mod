package com.frierenmod.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.UUID;

/**
 * A golden butterfly from Frieren's butterfly spell. It flutters about like a bat for a minute and
 * then fades. Fused with Zoltraak it hunts monsters and bursts on them instead. Never saved.
 */
public class GoldenButterflyEntity extends Entity {
    private static final EntityDataAccessor<Boolean> DATA_EXPLODING =
            SynchedEntityData.defineId(GoldenButterflyEntity.class, EntityDataSerializers.BOOLEAN);

    private static final int LIFETIME = 1200, BOMB_LIFETIME = 400;

    @Nullable
    private UUID ownerId;
    private int life;
    @Nullable
    private Vec3 wanderTarget;
    private int wanderTicks;
    @Nullable
    private LivingEntity prey;
    @Nullable
    private Vec3 home;

    public GoldenButterflyEntity(EntityType<? extends GoldenButterflyEntity> type, Level level) {
        super(type, level);
        this.noPhysics = false;
        setNoGravity(true);
    }

    public void setup(Player owner, boolean exploding) {
        this.ownerId = owner.getUUID();
        this.entityData.set(DATA_EXPLODING, exploding);
    }

    public boolean isExploding() {
        return this.entityData.get(DATA_EXPLODING);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_EXPLODING, false);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            if (random.nextInt(isExploding() ? 4 : 24) == 0) {
                level().addParticle(isExploding() ? ParticleTypes.SMALL_FLAME : ParticleTypes.WAX_ON,
                        getX(), getY(), getZ(), 0.0D, -0.01D, 0.0D);
            }
            return;
        }
        if (++life > (isExploding() ? BOMB_LIFETIME : LIFETIME)) {
            fade();
            return;
        }
        Vec3 v = getDeltaMovement();
        Vec3 steer = isExploding() ? hunt() : null;
        if (steer == null) steer = wander();
        // Fluttery flight: drift toward the goal, with a little bob from the wingbeats.
        v = v.scale(0.88D).add(steer).add(0.0D, Math.sin((tickCount + getId()) * 0.6D) * 0.012D, 0.0D);
        setDeltaMovement(v);
        move(MoverType.SELF, v);
        if (horizontalCollision) {
            wanderTarget = null;
        }
        if (v.horizontalDistanceSqr() > 1.0E-4D) {
            setYRot((float) (Math.atan2(-v.x, v.z) * (180.0D / Math.PI)));
        }
    }

    private Vec3 wander() {
        if (home == null) home = position();
        if (wanderTarget == null || --wanderTicks <= 0 || position().distanceToSqr(wanderTarget) < 0.5D) {
            wanderTicks = 20 + random.nextInt(30);
            // flit around where they were conjured, from knee to a little over head height
            wanderTarget = home.add((random.nextDouble() - 0.5D) * 9.0D, (random.nextDouble() - 0.65D) * 2.4D,
                    (random.nextDouble() - 0.5D) * 9.0D);
        }
        return wanderTarget.subtract(position()).normalize().scale(0.025D);
    }

    @Nullable
    private Vec3 hunt() {
        if (prey == null || !prey.isAlive() || tickCount % 10 == 0) {
            prey = level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(16.0D),
                            e -> e.isAlive() && e instanceof Enemy && !e.getUUID().equals(ownerId))
                    .stream().min(Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
        }
        if (prey == null) return null;
        Vec3 aim = prey.position().add(0.0D, prey.getBbHeight() * 0.6D, 0.0D);
        if (aim.distanceToSqr(position()) < 1.2D) {
            level().explode(this, getX(), getY(), getZ(), 1.6F, Level.ExplosionInteraction.NONE);
            discard();
            return Vec3.ZERO;
        }
        return aim.subtract(position()).normalize().scale(0.06D);
    }

    private void fade() {
        if (level() instanceof net.minecraft.server.level.ServerLevel sl) {
            sl.sendParticles(ParticleTypes.WAX_ON, getX(), getY(), getZ(), 6, 0.15D, 0.15D, 0.15D, 0.02D);
        }
        discard();
    }

    @Override
    public boolean shouldBeSaved() {
        return false; // spell magic, gone when the chunk unloads
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }
}
