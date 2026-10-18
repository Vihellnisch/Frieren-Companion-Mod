package com.frierenmod.entity;

import com.frierenmod.registry.ModItems;
import com.frierenmod.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.UUID;

/**
 * A chest that bites. Sits perfectly still as a chest (HIDDEN) until a player gets close, opens it
 * or hits it, then hops after them (AWAKE). A nearby Frieren can't resist it: she dives in head-first
 * (FEED_OPEN), gets swallowed (FEED_CHEW) until someone pushes her deeper and it gags her back out
 * (FEED_GAG), and then it fights like any other Mimic.
 */
public class MimicEntity extends Monster implements GeoEntity {
    public static final int HIDDEN = 0, AWAKE = 1, FEED_OPEN = 2, FEED_CHEW = 3, FEED_GAG = 4;

    private static final EntityDataAccessor<Integer> DATA_STATE =
            SynchedEntityData.defineId(MimicEntity.class, EntityDataSerializers.INT);

    private static final double REVEAL_DISTANCE = 2.5D;
    private static final int SEARCH_TICKS = 70;
    private static final int CHEW_TICKS = 600;
    private static final int GAG_SPIT_TICK = 22, GAG_TICKS = 32;
    private static final int REHIDE_TICKS = 400;
    private static final float GRIMOIRE_CHANCE = 0.01F;

    private static final String[] MUFFLED = {
            "Mmmph! Mmmmph!!",
            "It's dark... I'm scared...!",
            "*flails* Get me out!",
            "Mmph... it's chewing...!"
    };

    private static final RawAnimation HIDDEN_ANIM = RawAnimation.begin().thenLoop("hidden");
    private static final RawAnimation CHEST_OPEN_ANIM = RawAnimation.begin().thenLoop("chest_open");
    private static final RawAnimation IDLE_ANIM = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation HOP_ANIM = RawAnimation.begin().thenLoop("hop");
    private static final RawAnimation CHEW_ANIM = RawAnimation.begin().thenLoop("chew");
    private static final RawAnimation GAG_ANIM = RawAnimation.begin().thenPlay("gag");
    private static final RawAnimation REVEAL_ANIM = RawAnimation.begin().thenPlay("reveal");
    private static final RawAnimation BITE_ANIM = RawAnimation.begin().thenPlay("bite");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    /** The structure loot table this Mimic replaced, dropped on death instead of the default loot. */
    @Nullable
    private String chestLootTable;
    private long lootSeed;

    private int stateTicks;
    private int idleTicks;
    @Nullable
    private UUID frierenId;
    @Nullable
    private UUID claimedBy;
    private int claimTicks;
    @Nullable
    private UUID pusherId;
    private boolean selfFreed;

    public MimicEntity(EntityType<? extends MimicEntity> type, Level level) {
        super(type, level);
        this.xpReward = 10;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 30.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.26D)
                .add(Attributes.ATTACK_DAMAGE, 6.0D)
                .add(Attributes.ARMOR, 4.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.6D)
                .add(Attributes.FOLLOW_RANGE, 20.0D);
    }

    @Override
    protected void registerGoals() {
        // Every goal only runs while the Mimic is awake; hidden or eating, it's furniture.
        this.goalSelector.addGoal(0, new FloatGoal(this) {
            @Override
            public boolean canUse() {
                return isAwake() && super.canUse();
            }
        });
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.15D, false) {
            @Override
            public boolean canUse() {
                return isAwake() && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return isAwake() && super.canContinueToUse();
            }
        });
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this) {
            @Override
            public boolean canUse() {
                return isAwake() && super.canUse();
            }
        });
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true) {
            @Override
            public boolean canUse() {
                return isAwake() && super.canUse();
            }
        });
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_STATE, HIDDEN);
    }

    public int getState() {
        return this.entityData.get(DATA_STATE);
    }

    private void setState(int state) {
        this.entityData.set(DATA_STATE, state);
        this.stateTicks = 0;
    }

    public boolean isAwake() {
        return getState() == AWAKE;
    }

    public boolean isHidden() {
        return getState() == HIDDEN;
    }

    /** Turns a freshly made Mimic into a disguised chest facing {@code yaw}. */
    public void setupHidden(@Nullable ResourceKey<LootTable> lootTable, long seed, float yaw) {
        this.chestLootTable = lootTable == null ? null : lootTable.location().toString();
        this.lootSeed = seed;
        setRotationLocked(yaw);
        setPersistenceRequired();
        setState(HIDDEN);
    }

    private void setRotationLocked(float yaw) {
        setYRot(yaw);
        this.yRotO = yaw;
        setYBodyRot(yaw);
        this.yBodyRotO = yaw;
        setYHeadRot(yaw);
        this.yHeadRotO = yaw;
        setXRot(0.0F);
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType,
                                        @Nullable SpawnGroupData data) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, spawnType, data);
        // Spawn eggs and commands: sit on the block grid with the front toward the nearest player.
        Player near = level.getNearestPlayer(this, 16.0D);
        float yaw = getYRot();
        if (near != null) {
            yaw = (float) (Mth.atan2(near.getZ() - getZ(), near.getX() - getX()) * Mth.RAD_TO_DEG) - 90.0F;
        }
        yaw = Math.round(yaw / 90.0F) * 90.0F;
        moveTo(Mth.floor(getX()) + 0.5D, getY(), Mth.floor(getZ()) + 0.5D, yaw, 0.0F);
        setupHidden(null, 0L, yaw);
        return result;
    }

    // ----- Frieren -----
    /** Frieren reserves this Mimic while she walks over, so a player arriving first doesn't spring it. */
    public boolean claim(FrierenEntity frieren) {
        if (!isHidden() || (claimedBy != null && !claimedBy.equals(frieren.getUUID()))) return false;
        claimedBy = frieren.getUUID();
        claimTicks = 300;
        return true;
    }

    public void releaseClaim(FrierenEntity frieren) {
        if (frieren.getUUID().equals(claimedBy)) {
            claimedBy = null;
            claimTicks = 0;
        }
    }

    public boolean isClaimedByOther(FrierenEntity frieren) {
        return claimedBy != null && !claimedBy.equals(frieren.getUUID());
    }

    /** Where Frieren's waist goes: the inner floor of the chest. */
    public Vec3 mouthAnchor() {
        return position().add(0.0D, 10.0D / 16.0D, 0.0D);
    }

    /** Frieren reached the "chest" and dives in. */
    public boolean beginFeeding(FrierenEntity frieren) {
        if (!isHidden() || isClaimedByOther(frieren)) return false;
        frierenId = frieren.getUUID();
        claimedBy = null;
        pusherId = null;
        selfFreed = false;
        frieren.enterChest(mouthAnchor(), getYRot(), getUUID());
        setState(FEED_OPEN);
        playSound(SoundEvents.CHEST_OPEN, 0.6F, 0.95F + random.nextFloat() * 0.1F);
        return true;
    }

    @Nullable
    private FrierenEntity swallowedFrieren() {
        if (frierenId == null || !(level() instanceof ServerLevel sl)) return null;
        if (sl.getEntity(frierenId) instanceof FrierenEntity f && f.isAlive() && getUUID().equals(f.getSwallowedBy())) {
            return f;
        }
        return null;
    }

    /** A player shoves Frieren in deeper; the Mimic gags on her and spits her out. */
    public void pushDeeper(Player player) {
        int state = getState();
        if (state == FEED_OPEN) {
            stateTicks = SEARCH_TICKS; // springs it right away
            return;
        }
        if (state != FEED_CHEW) return;
        pusherId = player.getUUID();
        FrierenEntity f = swallowedFrieren();
        if (f != null) f.sayNearby("Mmmph?!", ChatFormatting.LIGHT_PURPLE);
        startGag();
    }

    private void startGag() {
        setState(FEED_GAG);
        FrierenEntity f = swallowedFrieren();
        if (f != null) f.setChestPose(FrierenEntity.CHEST_DEEPER);
        playSound(SoundEvents.GENERIC_EAT, 1.0F, 0.45F);
        playSound(SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, 0.4F, 1.4F);
    }

    private void spit(@Nullable FrierenEntity f) {
        Vec3 forward = Vec3.directionFromRotation(0.0F, getYRot());
        if (f != null) {
            Vec3 exit = position().add(forward.scale(1.1D)).add(0.0D, 0.9D, 0.0D);
            f.leaveChest(exit, forward.scale(0.45D).add(0.0D, 0.35D, 0.0D));
            if (selfFreed) {
                f.sayNearby("...Zoltraak.", ChatFormatting.LIGHT_PURPLE);
            } else {
                f.sayNearby("*cough* ...Thanks. My spell said 99% mimic. I was betting on the 1%.",
                        ChatFormatting.LIGHT_PURPLE);
            }
        }
        playSound(SoundEvents.PLAYER_BURP, 1.0F, 0.6F);
        playSound(SoundEvents.LLAMA_SPIT, 1.0F, 0.7F);
        if (level() instanceof ServerLevel sl) {
            Vec3 mouth = position().add(forward.scale(0.5D)).add(0.0D, 0.8D, 0.0D);
            sl.sendParticles(ParticleTypes.SPIT, mouth.x, mouth.y, mouth.z, 12, 0.2D, 0.2D, 0.2D, 0.15D);
        }
        frierenId = null;
    }

    private void endFeedingAwake() {
        setState(AWAKE);
        idleTicks = 0;
        LivingEntity target = null;
        if (pusherId != null && level() instanceof ServerLevel sl && sl.getEntity(pusherId) instanceof Player p
                && EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(p)) {
            target = p;
        }
        if (target == null) target = level().getNearestPlayer(this, 16.0D);
        if (target instanceof Player p && EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(p)) setTarget(p);
        pusherId = null;
    }

    // ----- Waking up -----
    private void reveal(@Nullable LivingEntity cause) {
        setState(AWAKE);
        idleTicks = 0;
        claimedBy = null;
        triggerAnim("action", "reveal");
        playSound(SoundEvents.CHEST_OPEN, 1.0F, 0.5F);
        playSound(SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, 0.6F, 0.8F);
        if (cause != null && !(cause instanceof FrierenEntity)
                && !(cause instanceof Player p && !EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(p))) {
            setTarget(cause);
        }
    }

    private void hide() {
        setState(HIDDEN);
        setTarget(null);
        getNavigation().stop();
        float yaw = Math.round(getYRot() / 90.0F) * 90.0F;
        if (onGround()) {
            moveTo(Mth.floor(getX()) + 0.5D, getY(), Mth.floor(getZ()) + 0.5D, yaw, 0.0F);
        }
        setRotationLocked(yaw);
        playSound(SoundEvents.CHEST_CLOSE, 0.8F, 0.9F);
    }

    @Override
    public void aiStep() {
        if (!isAwake()) {
            // Furniture doesn't turn, slide or get shoved.
            setRotationLocked(Math.round(getYRot() / 90.0F) * 90.0F);
            Vec3 v = getDeltaMovement();
            setDeltaMovement(0.0D, Math.min(v.y, 0.0D), 0.0D);
            this.xxa = 0.0F;
            this.zza = 0.0F;
        }
        super.aiStep();
        if (level().isClientSide) return;

        switch (getState()) {
            case HIDDEN -> tickHidden();
            case FEED_OPEN -> tickSearch();
            case FEED_CHEW -> tickChew();
            case FEED_GAG -> tickGag();
            default -> tickAwake();
        }
    }

    private void tickHidden() {
        if (claimTicks > 0 && --claimTicks == 0) claimedBy = null;
        if (claimedBy != null) return;
        Player near = level().getNearestPlayer(getX(), getY(), getZ(), REVEAL_DISTANCE, EntitySelector.NO_CREATIVE_OR_SPECTATOR);
        if (near != null && near.isAlive()) reveal(near);
    }

    private void tickSearch() {
        FrierenEntity f = swallowedFrieren();
        if (f == null) {
            reveal(null);
            return;
        }
        stateTicks++;
        if (stateTicks % 12 == 0) {
            playSound(SoundEvents.BUNDLE_INSERT, 0.7F, 0.8F + random.nextFloat() * 0.4F);
        }
        if (stateTicks >= SEARCH_TICKS) {
            setState(FEED_CHEW);
            f.setChestPose(FrierenEntity.CHEST_PANIC);
            playSound(SoundEvents.CHEST_CLOSE, 1.0F, 0.6F);
            playSound(SoundEvents.EVOKER_FANGS_ATTACK, 0.8F, 0.8F);
            screamFromInside();
            f.sayNearby("It's dark! I'm scared!", ChatFormatting.LIGHT_PURPLE);
        }
    }

    /** Her voice, coming out of the Mimic (the clip itself is muffled; being mono, it fades with distance). */
    private void screamFromInside() {
        level().playSound(null, getX(), getY() + 0.5D, getZ(), ModSounds.FRIEREN_KURAI_YO.get(), SoundSource.NEUTRAL,
                1.3F, 0.97F + random.nextFloat() * 0.06F);
    }

    private void tickChew() {
        FrierenEntity f = swallowedFrieren();
        if (f == null) {
            reveal(null);
            return;
        }
        stateTicks++;
        if (stateTicks % 16 == 0) {
            playSound(SoundEvents.GENERIC_EAT, 0.8F, 0.5F + random.nextFloat() * 0.2F);
        }
        if (stateTicks % 160 == 0) {
            String line = MUFFLED[random.nextInt(MUFFLED.length)];
            if (line.startsWith("It's dark")) screamFromInside();
            f.sayNearby(line, ChatFormatting.LIGHT_PURPLE);
        }
        if (stateTicks >= CHEW_TICKS) {
            // Nobody helped. She frees herself, rather less gently.
            selfFreed = true;
            f.sayNearby("...Fine. I'll get myself out.", ChatFormatting.LIGHT_PURPLE);
            startGag();
        }
    }

    private void tickGag() {
        stateTicks++;
        if (stateTicks == GAG_SPIT_TICK) {
            FrierenEntity f = swallowedFrieren();
            spit(f);
            if (selfFreed && f != null) {
                hurt(damageSources().indirectMagic(f, f), 8.0F);
            }
        }
        if (stateTicks >= GAG_TICKS) {
            endFeedingAwake();
        }
    }

    private void tickAwake() {
        if (getTarget() == null || !getTarget().isAlive()) {
            if (++idleTicks > REHIDE_TICKS) hide();
        } else {
            idleTicks = 0;
        }
    }

    // ----- Combat -----
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide) return super.hurt(source, amount);
        int state = getState();
        Entity attacker = source.getEntity();
        if (state == HIDDEN && attacker instanceof LivingEntity living) {
            reveal(living);
        } else if ((state == FEED_OPEN || state == FEED_CHEW) && attacker != null && !(attacker instanceof FrierenEntity)) {
            if (attacker instanceof Player p) pusherId = p.getUUID();
            startGag();
        }
        return super.hurt(source, amount);
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        triggerAnim("action", "bite");
        playSound(SoundEvents.EVOKER_FANGS_ATTACK, 0.7F, 1.2F + random.nextFloat() * 0.2F);
        return super.doHurtTarget(target);
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        int state = getState();
        if (state == AWAKE) return super.mobInteract(player, hand);
        if (!level().isClientSide) {
            if (state == HIDDEN) {
                // Tried to open it.
                reveal(player);
                if (EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(player)) doHurtTarget(player);
            } else {
                pushDeeper(player);
            }
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    // ----- Being a chest -----
    @Override
    public boolean canBeCollidedWith() {
        return !isAwake() && isAlive();
    }

    @Override
    public boolean isPushable() {
        return isAwake() && super.isPushable();
    }

    @Override
    public boolean isPushedByFluid() {
        return isAwake();
    }

    @Override
    public boolean canBeSeenAsEnemy() {
        return isAwake() && super.canBeSeenAsEnemy();
    }

    @Override
    public boolean isPreventingPlayerRest(Player player) {
        return isAwake();
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public void checkDespawn() {
        if (level().getDifficulty() == Difficulty.PEACEFUL) {
            revertToChest();
            return;
        }
        super.checkDespawn();
    }

    /** Peaceful: no Mimics, but the chest (and its loot) stays. */
    private void revertToChest() {
        FrierenEntity f = swallowedFrieren();
        if (f != null) f.leaveChest(position().add(0.0D, 1.0D, 0.0D), Vec3.ZERO);
        BlockPos pos = blockPosition();
        BlockState here = level().getBlockState(pos);
        if (here.canBeReplaced()) {
            Direction facing = Direction.fromYRot(getYRot());
            level().setBlock(pos, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing), 3);
            if (chestLootTable != null && level().getBlockEntity(pos) instanceof ChestBlockEntity chest) {
                chest.setLootTable(ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.parse(chestLootTable)), lootSeed);
            }
        }
        discard();
    }

    // ----- Loot -----
    @Override
    protected void dropFromLootTable(DamageSource source, boolean recentlyHit) {
        if (chestLootTable == null || !(level() instanceof ServerLevel sl)) {
            super.dropFromLootTable(source, recentlyHit);
            return;
        }
        // It ate the chest it replaced, so it coughs up that chest's loot.
        LootTable table = sl.getServer().reloadableRegistries().getLootTable(
                ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.parse(chestLootTable)));
        LootParams params = new LootParams.Builder(sl)
                .withParameter(LootContextParams.ORIGIN, position())
                .withOptionalParameter(LootContextParams.THIS_ENTITY, source.getEntity())
                .create(LootContextParamSets.CHEST);
        table.getRandomItems(params, lootSeed, this::spawnAtLocation);
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        if (random.nextFloat() >= GRIMOIRE_CHANCE) return;
        float roll = random.nextFloat();
        Item grimoire = roll < 0.6F ? ModItems.GRIMOIRE_BASIC_MAGIC.get()
                : roll < 0.9F ? ModItems.GRIMOIRE_SPIRIT_LORE.get() : ModItems.GRIMOIRE_ANCIENT_SPELLS.get();
        spawnAtLocation(new ItemStack(grimoire));
        for (FrierenEntity f : level.getEntitiesOfClass(FrierenEntity.class, getBoundingBox().inflate(16.0D))) {
            f.sayNearby("A grimoire...! See? The 1% was real.", ChatFormatting.LIGHT_PURPLE);
            break;
        }
    }

    // ----- Sounds -----
    @Override
    protected SoundEvent getAmbientSound() {
        return isAwake() ? SoundEvents.CHEST_CLOSE : null;
    }

    @Override
    public int getAmbientSoundInterval() {
        return 160;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.WOOD_BREAK;
    }

    @Override
    protected float getSoundVolume() {
        return 0.6F;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(SoundEvents.WOOD_STEP, 0.3F, 0.7F);
    }

    // ----- Save / load -----
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        // Frieren isn't saved as swallowed, so an interrupted meal goes back to being a chest.
        tag.putInt("MimicState", isAwake() ? AWAKE : HIDDEN);
        if (chestLootTable != null) {
            tag.putString("ChestLoot", chestLootTable);
            tag.putLong("ChestLootSeed", lootSeed);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setState(tag.getInt("MimicState") == AWAKE ? AWAKE : HIDDEN);
        chestLootTable = tag.contains("ChestLoot") ? tag.getString("ChestLoot") : null;
        lootSeed = tag.getLong("ChestLootSeed");
    }

    // ----- GeckoLib -----
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 3, state -> switch (getState()) {
            case HIDDEN -> state.setAndContinue(HIDDEN_ANIM);
            case FEED_OPEN -> state.setAndContinue(CHEST_OPEN_ANIM);
            case FEED_CHEW -> state.setAndContinue(CHEW_ANIM);
            case FEED_GAG -> state.setAndContinue(GAG_ANIM);
            default -> state.setAndContinue(state.isMoving() ? HOP_ANIM : IDLE_ANIM);
        }));
        controllers.add(new AnimationController<>(this, "action", 2, state -> PlayState.STOP)
                .triggerableAnim("reveal", REVEAL_ANIM)
                .triggerableAnim("bite", BITE_ANIM));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
