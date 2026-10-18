package com.frierenmod.entity;

import com.frierenmod.entity.ai.FrierenCastSpellGoal;
import com.frierenmod.entity.ai.FrierenChestGoal;
import com.frierenmod.entity.ai.FrierenFollowOwnerGoal;
import com.frierenmod.entity.ai.FrierenSelfHealGoal;
import com.frierenmod.entity.ai.FrierenStayGoal;
import com.frierenmod.registry.ModEntities;
import com.frierenmod.registry.ModItems;
import com.frierenmod.screen.FrierenMenu;
import com.frierenmod.spell.BarrierSpell;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.core.Direction;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EntityType;
import com.frierenmod.screen.FrierenBagMenu;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Frieren - the interactive companion NPC.
 */
public class FrierenEntity extends PathfinderMob implements GeoEntity, Merchant {

    // ----- Relationship stages -----
    public enum Stage {
        STRANGER(0), FRIEND(30), ALLY(80), PARTNER(150);
        public final int threshold;
        Stage(int t) { this.threshold = t; }
    }

    public static final int FRIEND_THRESHOLD = Stage.FRIEND.threshold;
    public static final int ALLY_THRESHOLD = Stage.ALLY.threshold;
    public static final int PARTNER_THRESHOLD = Stage.PARTNER.threshold;

    private static final EntityDataAccessor<Integer> DATA_AFFECTION =
            SynchedEntityData.defineId(FrierenEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_STAYING =
            SynchedEntityData.defineId(FrierenEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_PARTNER =
            SynchedEntityData.defineId(FrierenEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_SLEEPING =
            SynchedEntityData.defineId(FrierenEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_CHEST_POSE =
            SynchedEntityData.defineId(FrierenEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_BOWING =
            SynchedEntityData.defineId(FrierenEntity.class, EntityDataSerializers.BOOLEAN);

    // ----- Head pats -----
    @Nullable
    private UUID petRequester;
    private long petRequestTime;
    private long lastPetDay = -1;

    // ----- Head-first in a chest (or a Mimic) -----
    public static final int CHEST_NONE = 0, CHEST_SEARCH = 1, CHEST_PANIC = 2, CHEST_DEEPER = 3;
    @Nullable
    private Vec3 chestAnchor;
    private float chestYaw;
    @Nullable
    private UUID swallowedBy;
    private boolean pullOutRequested;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private static final RawAnimation IDLE_ANIM = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation WALK_ANIM = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation CAST_ANIM = RawAnimation.begin().thenPlay("cast_spell");
    private static final RawAnimation TALK_ANIM = RawAnimation.begin().thenPlay("talk");
    private static final RawAnimation SLEEP_ANIM = RawAnimation.begin().thenLoop("sleep");
    private static final RawAnimation CHEST_SEARCH_ANIM = RawAnimation.begin().thenLoop("chest_search");
    private static final RawAnimation CHEST_PANIC_ANIM = RawAnimation.begin().thenLoop("chest_panic");
    private static final RawAnimation CHEST_DEEPER_ANIM = RawAnimation.begin().thenLoop("chest_deeper");
    private static final RawAnimation BOW_ANIM = RawAnimation.begin().thenLoop("bow");

    @Nullable
    private UUID ownerUUID;
    private boolean promiseGiven;
    private final Map<String, Integer> spellCooldowns = new HashMap<>();

    // Bitmask of spells Frieren has taught the player.
    public static final int SPELL_MANA_SENSING = 1;
    public static final int SPELL_ZOLTRAAK_T1 = 2;
    public static final int SPELL_ZOLTRAAK_T2 = 4;
    public static final int SPELL_EINSAM = 8;
    public static final int SPELL_ZOLTRAAK_T3 = 16;
    public static final int SPELL_MIDAS = 32;
    private int taughtSpells = 0;

    // ----- Sleep state -----
    private boolean deepSleeping;
    private int deepSleepTicks;
    private boolean movingToBed;
    private int sleepBedX, sleepBedY, sleepBedZ;
    private int sleepPathTicks;

    // ----- Grimoire hunt quest -----
    private boolean questActive;
    private int questTargetX, questTargetY, questTargetZ;
    private int questGrimoireType; // 0-2
    private int questCooldown;

    // ----- Himmel's statue quest -----
    private int himmelQuestStage; // 0-4

    // Respawn bed (dimension + position). respawnDim == null means no bed claimed.
    @Nullable
    private String respawnDim;
    private int respawnBedX;
    private int respawnBedY;
    private int respawnBedZ;

    // Pending respawns keyed by the dead Frieren's UUID.
    public static final Map<UUID, RespawnTask> PENDING_RESPAWNS = new ConcurrentHashMap<>();

    // ----- Player-hit retaliation state -----
    @Nullable
    private UUID warningPlayerUUID;
    private int warningResetTicks = 0;

    // Armor attribute modifier IDs
    private static final ResourceLocation ARMOR_MOD_ID = ResourceLocation.fromNamespaceAndPath("frierenmod", "frieren_worn_armor");
    private static final ResourceLocation TOUGHNESS_MOD_ID = ResourceLocation.fromNamespaceAndPath("frierenmod", "frieren_worn_toughness");

    // Merchant fields
    @Nullable
    private Player tradingPlayer;
    @Nullable
    private MerchantOffers offers;
    private final SimpleContainer bag = new SimpleContainer(27);

    // Armor and XP storage
    private final SimpleContainer armorSlots = new SimpleContainer(4); // HEAD, CHEST, LEGS, FEET
    private int storedXp = 0;

    public FrierenEntity(EntityType<? extends FrierenEntity> type, Level level) {
        super(type, level);
        this.armorSlots.addListener(c -> updateArmorAttributes());
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 40.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(Attributes.ATTACK_DAMAGE, 3.0D)
                .add(Attributes.FOLLOW_RANGE, 24.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.3D)
                .add(Attributes.ARMOR, 0.0D)
                .add(Attributes.ARMOR_TOUGHNESS, 0.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new FrierenSelfHealGoal(this));
        this.goalSelector.addGoal(1, new FrierenStayGoal(this));
        this.goalSelector.addGoal(1, new com.frierenmod.entity.ai.FrierenPetGoal(this));
        this.goalSelector.addGoal(2, new FrierenCastSpellGoal(this));
        this.goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.1D, true));
        this.goalSelector.addGoal(4, new FrierenFollowOwnerGoal(this, 1.2D, 3.0F, 4.0F));
        // Above following: with her owner close by, a chest still wins.
        this.goalSelector.addGoal(3, new FrierenChestGoal(this));
        this.goalSelector.addGoal(5, new com.frierenmod.entity.ai.FrierenFlowerGoal(this));
        this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<Monster>(this, Monster.class, 10, true, false,
                (living) -> living instanceof Enemy && this.getStage().ordinal() >= Stage.ALLY.ordinal()));
    }

    // ----- Synched data -----
    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_AFFECTION, 0);
        builder.define(DATA_STAYING, false);
        builder.define(DATA_PARTNER, false);
        builder.define(DATA_SLEEPING, false);
        builder.define(DATA_CHEST_POSE, CHEST_NONE);
        builder.define(DATA_BOWING, false);
    }

    // ----- Relationship API -----
    public int getAffection() {
        return this.entityData.get(DATA_AFFECTION);
    }

    public void setAffection(int value) {
        this.entityData.set(DATA_AFFECTION, Math.max(0, value));
    }

    public void addAffection(int amount) {
        setAffection(getAffection() + amount);
    }

    public Stage getStage() {
        if (promiseGiven || this.entityData.get(DATA_PARTNER)) {
            return Stage.PARTNER;
        }
        int a = getAffection();
        if (a >= ALLY_THRESHOLD) return Stage.ALLY;
        if (a >= FRIEND_THRESHOLD) return Stage.FRIEND;
        return Stage.STRANGER;
    }

    public boolean isStaying() {
        return this.entityData.get(DATA_STAYING);
    }

    public void setStaying(boolean staying) {
        this.entityData.set(DATA_STAYING, staying);
    }

    public void setPartner(boolean partner) {
        this.promiseGiven = partner;
        this.entityData.set(DATA_PARTNER, partner);
    }

    @Nullable
    public UUID getOwnerUUID() {
        return ownerUUID;
    }

    public void setOwnerUUID(@Nullable UUID uuid) {
        this.ownerUUID = uuid;
    }

    @Nullable
    public Player getOwner() {
        if (ownerUUID == null) return null;
        return this.level().getPlayerByUUID(ownerUUID);
    }

    public boolean isOwnedBy(Player player) {
        return ownerUUID != null && ownerUUID.equals(player.getUUID());
    }

    // ----- Spell cooldowns -----
    public boolean canCast(String spell) {
        return spellCooldowns.getOrDefault(spell, 0) <= 0;
    }

    public void setCooldown(String spell, int ticks) {
        spellCooldowns.put(spell, ticks);
    }

    // ----- Taught spells -----
    public boolean hasTaught(int bit) {
        return (this.taughtSpells & bit) != 0;
    }

    public void markTaught(int bit) {
        this.taughtSpells |= bit;
    }

    public int getTaughtSpells() {
        return this.taughtSpells;
    }

    // ----- Head pats -----
    public boolean isBowing() {
        return this.entityData.get(DATA_BOWING);
    }

    public void setBowing(boolean bowing) {
        this.entityData.set(DATA_BOWING, bowing);
    }

    /** The player who just asked for a head pat (sneak + right-click), if it's still fresh. */
    @Nullable
    public Player getPetRequester() {
        if (petRequester == null || level().getGameTime() - petRequestTime > 40) return null;
        return level().getPlayerByUUID(petRequester);
    }

    public void clearPetRequest() {
        petRequester = null;
    }

    /** Affection once a day; the pat itself is its own reward. */
    public void onPetted(Player player) {
        long day = level().getDayTime() / 24000L;
        if (day != lastPetDay) {
            lastPetDay = day;
            addAffection(1);
        }
    }

    public InteractionResult requestPet(Player player) {
        if (getStage() == Stage.STRANGER) {
            player.sendSystemMessage(Component.literal("Frieren: ...Don't touch my head.")
                    .withStyle(net.minecraft.ChatFormatting.GRAY));
            return InteractionResult.CONSUME;
        }
        if (isSleeping() || isBowing()) return InteractionResult.CONSUME;
        petRequester = player.getUUID();
        petRequestTime = level().getGameTime();
        return InteractionResult.CONSUME;
    }

    // ----- Chest diving -----
    public int getChestPose() {
        return this.entityData.get(DATA_CHEST_POSE);
    }

    public void setChestPose(int pose) {
        this.entityData.set(DATA_CHEST_POSE, pose);
    }

    public boolean isInChest() {
        return getChestPose() != CHEST_NONE;
    }

    /** The Mimic currently eating her, if any. */
    @Nullable
    public UUID getSwallowedBy() {
        return swallowedBy;
    }

    /** Dives head-first into a chest whose inner floor is at {@code anchor}. */
    public void enterChest(Vec3 anchor, float yaw, @Nullable UUID mimic) {
        this.chestAnchor = anchor;
        this.chestYaw = yaw;
        this.swallowedBy = mimic;
        this.pullOutRequested = false;
        getNavigation().stop();
        setNoGravity(true);
        this.noPhysics = true;
        moveTo(anchor.x, anchor.y, anchor.z, yaw, 0.0F);
        setYBodyRot(yaw);
        setYHeadRot(yaw);
        setChestPose(CHEST_SEARCH);
    }

    public void leaveChest(Vec3 exit, Vec3 velocity) {
        setChestPose(CHEST_NONE);
        this.chestAnchor = null;
        this.swallowedBy = null;
        this.pullOutRequested = false;
        setNoGravity(false);
        this.noPhysics = false;
        this.fallDistance = 0.0F;
        moveTo(exit.x, exit.y, exit.z, getYRot(), 0.0F);
        setDeltaMovement(velocity);
        this.hasImpulse = true;
    }

    /** The player asked her to come out of an ordinary chest early. */
    public boolean consumePullOutRequest() {
        boolean requested = pullOutRequested;
        pullOutRequested = false;
        return requested;
    }

    private void holdInChest() {
        if (chestAnchor == null) {
            setChestPose(CHEST_NONE);
            setNoGravity(false);
            this.noPhysics = false;
            return;
        }
        setPos(chestAnchor.x, chestAnchor.y, chestAnchor.z);
        setDeltaMovement(Vec3.ZERO);
        setYRot(chestYaw);
        setYBodyRot(chestYaw);
        setYHeadRot(chestYaw);
        this.noPhysics = true;
        this.fallDistance = 0.0F;
    }

    /** Pops her out wherever she is, e.g. when she has to go to bed. */
    private void abortChest() {
        if (isInChest()) leaveChest(position().add(0.0D, 0.6D, 0.0D), Vec3.ZERO);
    }

    /** A line of hers in the chat of every player nearby. */
    public void sayNearby(String line, net.minecraft.ChatFormatting color) {
        for (Player p : level().players()) {
            if (p.distanceToSqr(this) < 24.0D * 24.0D) {
                p.sendSystemMessage(Component.literal("Frieren: " + line).withStyle(color));
            }
        }
    }

    // ----- Sleep -----
    public boolean isSleeping() {
        return this.entityData.get(DATA_SLEEPING);
    }

    public void setSleeping(boolean sleeping) {
        this.entityData.set(DATA_SLEEPING, sleeping);
    }

    public boolean isDeepSleeping() {
        return deepSleeping;
    }

    public void setDeepSleeping(boolean value, int ticks) {
        this.deepSleeping = value;
        this.deepSleepTicks = value ? ticks : 0;
        setSleeping(value);
    }

    /** Begin walking to the given bed to sleep. */
    public void startSleepingAt(BlockPos bed) {
        abortChest();
        this.sleepBedX = bed.getX();
        this.sleepBedY = bed.getY();
        this.sleepBedZ = bed.getZ();
        this.movingToBed = true;
        this.sleepPathTicks = 0;
        setStaying(true);
        faceBed(bed);
        this.getNavigation().moveTo(bed.getX() + 0.5D, bed.getY(), bed.getZ() + 0.5D, 1.0D);
    }

    /** Keep the full entity aligned with the bed, rather than letting the idle look goals rotate it. */
    private void faceBed(BlockPos bed) {
        net.minecraft.world.level.block.state.BlockState state = level().getBlockState(bed);
        if (state.getBlock() instanceof net.minecraft.world.level.block.BedBlock) {
            Direction facing = state.getValue(net.minecraft.world.level.block.BedBlock.FACING);
            float yaw = switch (facing) {
                case SOUTH -> 0.0F;
                case WEST -> 90.0F;
                case NORTH -> 180.0F;
                case EAST -> -90.0F;
                default -> getYRot();
            };
            setYRot(yaw);
            setYBodyRot(yaw);
            setYHeadRot(yaw);
        }
    }

    public void wakeUp() {
        this.movingToBed = false;
        setSleeping(false);
        this.deepSleeping = false;
        this.deepSleepTicks = 0;
    }

    public void startDeepSleep(int ticks) {
        abortChest();
        this.deepSleeping = true;
        this.deepSleepTicks = ticks;
        setSleeping(true);
        this.getNavigation().stop();
    }

    /** Called when a deep sleep ends: wake up and reward the owner with a random gift. */
    private void wakeFromDeepSleep() {
        this.deepSleeping = false;
        this.deepSleepTicks = 0;
        setSleeping(false);
        Player owner = getOwner();
        if (owner == null) {
            return;
        }
        ItemStack gift = rollDeepSleepGift();
        boolean promiseHint = gift.isEmpty();
        if (!promiseHint) {
            if (!owner.getInventory().add(gift)) {
                owner.drop(gift, false);
            }
        }
        owner.sendSystemMessage(Component.literal("Frieren: *yawns*... I slept well. Here, take this.")
                .withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE));
        if (promiseHint) {
            owner.sendSystemMessage(Component.literal(
                    "Frieren: ...A ring of gold, diamond and amethyst would mean a great deal to me.")
                    .withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE));
        }
        if (level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, getX(), getY() + 2.0D, getZ(),
                    10, 0.4D, 0.4D, 0.4D, 0.02D);
        }
    }

    private ItemStack rollDeepSleepGift() {
        int roll = this.getRandom().nextInt(100);
        if (roll < 40) {
            return new ItemStack(ModItems.GRIMOIRE_BASIC_MAGIC.get(), 1);
        } else if (roll < 70) {
            java.util.List<net.minecraft.world.item.Item> tomes = java.util.List.of(
                    ModItems.VERDANT_ZOLTRAAK_TOME.get(),
                    ModItems.AQUA_BARRIER_TOME.get(),
                    ModItems.MANA_STORM_TOME.get());
            return new ItemStack(tomes.get(this.getRandom().nextInt(tomes.size())), 1);
        } else if (roll < 90) {
            return new ItemStack(ModItems.MANA_HERB.get(), 3);
        }
        return ItemStack.EMPTY; // promise ring hint
    }

    @Nullable
    public BlockPos getRespawnBedPos() {
        return respawnDim == null ? null : new BlockPos(respawnBedX, respawnBedY, respawnBedZ);
    }

    // ----- Grimoire hunt quest -----
    public boolean isQuestActive() {
        return questActive;
    }

    public int getQuestCooldown() {
        return questCooldown;
    }

    public int getQuestGrimoireType() {
        return questGrimoireType;
    }

    public BlockPos getQuestTarget() {
        return new BlockPos(questTargetX, questTargetY, questTargetZ);
    }

    public void startGrimoireQuest(BlockPos target, int grimoireType) {
        this.questActive = true;
        this.questTargetX = target.getX();
        this.questTargetY = target.getY();
        this.questTargetZ = target.getZ();
        this.questGrimoireType = grimoireType;
    }

    public void completeGrimoireQuest() {
        this.questActive = false;
        this.questCooldown = 12000;
    }

    // ----- Himmel statue quest -----
    public int getHimmelQuestStage() {
        return himmelQuestStage;
    }

    public void setHimmelQuestStage(int stage) {
        this.himmelQuestStage = stage;
    }

    // ----- Respawn bed -----
    public boolean hasRespawnBed() {
        return this.respawnDim != null;
    }

    public void setRespawnBed(BlockPos pos, ResourceKey<Level> dimension) {
        this.respawnBedX = pos.getX();
        this.respawnBedY = pos.getY();
        this.respawnBedZ = pos.getZ();
        this.respawnDim = dimension.location().toString();
    }

    public void triggerCastAnimation() {
        if (!level().isClientSide) {
            triggerAnim("action", "cast_spell");
        }
    }

    public void triggerTalkAnimation() {
        if (!level().isClientSide) {
            triggerAnim("action", "talk");
        }
    }

    // ----- Tick logic -----
    @Override
    public void aiStep() {
        super.aiStep();

        if (!level().isClientSide) {
            if (isInChest()) {
                holdInChest();
            }

            // Natural health regen: 1 HP every 4 seconds, like a player.
            if (tickCount % 80 == 0 && getHealth() < getMaxHealth()) {
                heal(1.0F);
            }

            // Decrement all cooldowns.
            for (Map.Entry<String, Integer> e : spellCooldowns.entrySet()) {
                if (e.getValue() > 0) {
                    e.setValue(e.getValue() - 1);
                }
            }

            if (questCooldown > 0) {
                questCooldown--;
            }

            if (warningResetTicks > 0) {
                warningResetTicks--;
                if (warningResetTicks == 0) {
                    warningPlayerUUID = null;
                }
            }

            // Sleep handling.
            if (movingToBed) {
                sleepPathTicks++;
                double dx = getX() - (sleepBedX + 0.5D);
                double dz = getZ() - (sleepBedZ + 0.5D);
                if (dx * dx + dz * dz < 2.25D || sleepPathTicks > 200) {
                    movingToBed = false;
                    getNavigation().stop();
                    BlockPos bed = new BlockPos(sleepBedX, sleepBedY, sleepBedZ);
                    faceBed(bed);
                    moveTo(sleepBedX + 0.5D, sleepBedY + 0.0D, sleepBedZ + 0.5D, getYRot(), 0.0F);
                    setSleeping(true);
                    Player owner = getOwner();
                    if (owner != null) {
                        owner.sendSystemMessage(Component.literal("Frieren: ...Good night.")
                                .withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE));
                    }
                } else {
                    getNavigation().moveTo(sleepBedX + 0.5D, sleepBedY, sleepBedZ + 0.5D, 1.0D);
                }
            }

            if (isSleeping()) {
                getNavigation().stop();
                setDeltaMovement(0, getDeltaMovement().y, 0);
                if (deepSleeping) {
                    if (deepSleepTicks > 0) {
                        deepSleepTicks--;
                    } else {
                        wakeFromDeepSleep();
                    }
                }
                return; // Skip other behaviours while asleep.
            }

            // Auto-cast Barrier to protect a low-health owner (ALLY+).
            if (getStage().ordinal() >= Stage.ALLY.ordinal() && canCast("barrier")) {
                Player owner = getOwner();
                if (owner != null && owner.isAlive() && owner.distanceToSqr(this) < 256.0D
                        && owner.getHealth() < 8.0F) {
                    BarrierSpell.INSTANCE.cast(this, owner);
                    setCooldown("barrier", BarrierSpell.INSTANCE.getCooldownTicks());
                }
            }

            // Partner heart particles occasionally.
            if (getStage() == Stage.PARTNER && this.tickCount % 40 == 0 && level() instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.HEART, getX(), getY() + 2.0D, getZ(), 1, 0.3D, 0.2D, 0.3D, 0.0D);
            }
        }
    }

    // ----- Player aggression override -----
    @Override
    public boolean hurt(DamageSource source, float amount) {
        // Head-down in a chest she can't be hit, and a Mimic only slobbers on her.
        if (isInChest() && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return false;
        }
        boolean result = super.hurt(source, amount);
        if (!result || level().isClientSide) return result;
        if (!(source.getEntity() instanceof Player player)) return result;

        Stage stage = getStage();
        if (stage == Stage.PARTNER) {
            // Forgive completely, no retaliation.
            this.setLastHurtByMob(null);
            this.setTarget(null);
        } else if (stage == Stage.FRIEND || stage == Stage.ALLY) {
            boolean alreadyWarned = player.getUUID().equals(warningPlayerUUID) && warningResetTicks > 0;
            if (!alreadyWarned) {
                // First hit from this player: retaliate once and warn.
                this.doHurtTarget(player);
                player.sendSystemMessage(Component.literal("Frieren: ...Do that again and I won't hold back.")
                        .withStyle(net.minecraft.ChatFormatting.RED));
                warningPlayerUUID = player.getUUID();
                warningResetTicks = 300;
                this.setLastHurtByMob(null);
                this.setTarget(null);
            }
            // If already warned: HurtByTargetGoal proceeds normally (stay aggressive).
        }
        // STRANGER: default aggression — do nothing special.
        return result;
    }

    // ----- Interaction -----
    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);

        if (isInChest()) {
            if (!level().isClientSide) {
                if (swallowedBy != null && level() instanceof ServerLevel sl
                        && sl.getEntity(swallowedBy) instanceof MimicEntity mimic) {
                    mimic.pushDeeper(player);
                } else {
                    pullOutRequested = true;
                }
            }
            return InteractionResult.sidedSuccess(level().isClientSide());
        }

        if (!level().isClientSide && player.isShiftKeyDown() && held.isEmpty() && hand == InteractionHand.MAIN_HAND
                && !deepSleeping) {
            return requestPet(player);
        }

        if (!level().isClientSide) {
            if (deepSleeping) {
                player.sendSystemMessage(Component.literal("Frieren is sleeping...")
                        .withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
                return InteractionResult.CONSUME;
            }
            // Gift: grimoires
            if (held.is(ModItems.GRIMOIRE_BASIC_MAGIC.get()) || held.is(ModItems.GRIMOIRE_SPIRIT_LORE.get())
                    || held.is(ModItems.GRIMOIRE_ANCIENT_SPELLS.get())) {
                receiveGift(player, held, 10);
                return InteractionResult.CONSUME;
            }
            // Promise ring -> PARTNER (requires ALLY first)
            if (held.is(ModItems.PROMISE_RING.get())) {
                if (getStage().ordinal() >= Stage.ALLY.ordinal()) {
                    if (!player.getAbilities().instabuild) held.shrink(1);
                    setPartner(true);
                    if (ownerUUID == null) setOwnerUUID(player.getUUID());
                    Dialogue.say(player, Stage.PARTNER, true);
                    if (level() instanceof ServerLevel sl) {
                        sl.sendParticles(ParticleTypes.HEART, getX(), getY() + 2.0D, getZ(), 20, 0.5D, 0.5D, 0.5D, 0.05D);
                    }
                    playSound(SoundEvents.PLAYER_LEVELUP, 1.0F, 1.4F);
                } else {
                    player.sendSystemMessage(Component.literal("Frieren: ...We barely know each other. Not yet.").withStyle(net.minecraft.ChatFormatting.GRAY));
                }
                return InteractionResult.CONSUME;
            }

            // Otherwise open the interaction menu.
            if (player instanceof ServerPlayer serverPlayer) {
                openInteractionMenu(serverPlayer);
            }
            return InteractionResult.CONSUME;
        }

        return InteractionResult.sidedSuccess(level().isClientSide());
    }

    private void receiveGift(Player player, ItemStack held, int amount) {
        Stage before = getStage();
        if (!player.getAbilities().instabuild) held.shrink(1);
        addAffection(amount);
        if (ownerUUID == null && getStage().ordinal() >= Stage.FRIEND.ordinal()) {
            setOwnerUUID(player.getUUID());
        }
        Stage after = getStage();
        if (after != before) {
            player.sendSystemMessage(Component.literal("Frieren: ...I suppose we're closer now.").withStyle(net.minecraft.ChatFormatting.AQUA));
        } else {
            player.sendSystemMessage(Component.literal("Frieren: ...A grimoire. Thank you.").withStyle(net.minecraft.ChatFormatting.GRAY));
        }
        if (level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, getX(), getY() + 2.0D, getZ(), 6, 0.4D, 0.4D, 0.4D, 0.02D);
        }
        triggerTalkAnimation();
    }

    public void openInteractionMenu(ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider(
                (id, inv, p) -> new FrierenMenu(id, inv, this),
                Component.literal("Frieren")), buf -> buf.writeInt(this.getId()));
    }

    public void startTrading(ServerPlayer player) {
        setTradingPlayer(player);
        java.util.OptionalInt opt = player.openMenu(new SimpleMenuProvider(
                (id, inv, p) -> new net.minecraft.world.inventory.MerchantMenu(id, inv, this),
                Component.literal("Frieren")));
        if (opt.isPresent()) {
            MerchantOffers current = getOffers();
            if (!current.isEmpty()) {
                player.sendMerchantOffers(opt.getAsInt(), current, 1, getVillagerXp(), showProgressBar(), canRestock());
            }
        }
    }

    public void playSound(SoundEvent sound, float volume, float pitch) {
        this.level().playSound(null, getX(), getY(), getZ(), sound, getSoundSource(), volume, pitch);
    }

    // ----- Trading (Merchant) -----
    @Override
    public void setTradingPlayer(@Nullable Player player) {
        this.tradingPlayer = player;
    }

    @Nullable
    @Override
    public Player getTradingPlayer() {
        return this.tradingPlayer;
    }

    @Override
    public MerchantOffers getOffers() {
        if (this.offers == null) {
            this.offers = buildOffers();
        }
        return this.offers;
    }

    private MerchantOffers buildOffers() {
        MerchantOffers list = new MerchantOffers();
        list.add(new MerchantOffer(new ItemCost(Items.EMERALD, 3),
                new ItemStack(ModItems.MANA_HERB.get(), 2), 12, 2, 0.05F));
        list.add(new MerchantOffer(new ItemCost(Items.BOOK, 4), java.util.Optional.of(new ItemCost(Items.LAPIS_LAZULI, 6)),
                new ItemStack(ModItems.GRIMOIRE_BASIC_MAGIC.get(), 1), 5, 4, 0.05F));
        list.add(new MerchantOffer(new ItemCost(Items.EMERALD, 12),
                new ItemStack(ModItems.GRIMOIRE_SPIRIT_LORE.get(), 1), 3, 6, 0.05F));
        list.add(new MerchantOffer(new ItemCost(ModItems.MANA_HERB.get(), 4),
                new ItemStack(Items.EMERALD, 1), 8, 2, 0.05F));
        return list;
    }

    @Override
    public void overrideOffers(MerchantOffers newOffers) {
        this.offers = newOffers;
    }

    @Override
    public void notifyTrade(MerchantOffer offer) {
        offer.increaseUses();
        this.ambientSoundTime = -this.getAmbientSoundInterval();
        addAffection(2);
        if (this.tradingPlayer != null && ownerUUID == null && getStage().ordinal() >= Stage.FRIEND.ordinal()) {
            setOwnerUUID(this.tradingPlayer.getUUID());
        }
    }

    @Override
    public void notifyTradeUpdated(ItemStack stack) {
    }

    @Override
    public int getVillagerXp() {
        return 0;
    }

    @Override
    public void overrideXp(int xp) {
    }

    @Override
    public boolean showProgressBar() {
        return false;
    }

    @Override
    public SoundEvent getNotifyTradeSound() {
        return SoundEvents.VILLAGER_YES;
    }

    @Override
    public boolean isClientSide() {
        return this.level().isClientSide;
    }

    // ----- Save / load -----
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("AffectionPoints", getAffection());
        tag.putBoolean("IsStaying", isStaying());
        tag.putBoolean("PromiseGiven", promiseGiven);
        tag.putInt("TaughtSpells", taughtSpells);
        tag.putBoolean("DeepSleeping", deepSleeping);
        tag.putInt("DeepSleepTicks", deepSleepTicks);
        tag.putBoolean("IsSleeping", isSleeping());
        tag.putBoolean("QuestActive", questActive);
        tag.putInt("QuestTargetX", questTargetX);
        tag.putInt("QuestTargetY", questTargetY);
        tag.putInt("QuestTargetZ", questTargetZ);
        tag.putInt("QuestGrimoireType", questGrimoireType);
        tag.putInt("QuestCooldown", questCooldown);
        tag.putInt("HimmelQuestStage", himmelQuestStage);
        tag.putLong("LastPetDay", lastPetDay);
        if (ownerUUID != null) {
            tag.putUUID("OwnerUUID", ownerUUID);
        }
        if (respawnDim != null) {
            tag.putString("RespawnDim", respawnDim);
            tag.putInt("RespawnBedX", respawnBedX);
            tag.putInt("RespawnBedY", respawnBedY);
            tag.putInt("RespawnBedZ", respawnBedZ);
        }
        CompoundTag cds = new CompoundTag();
        for (Map.Entry<String, Integer> e : spellCooldowns.entrySet()) {
            cds.putInt(e.getKey(), e.getValue());
        }
        tag.put("SpellCooldowns", cds);
        if (this.offers != null) {
            RegistryOps<net.minecraft.nbt.Tag> ops = RegistryOps.create(NbtOps.INSTANCE, this.registryAccess());
            MerchantOffers.CODEC.encodeStart(ops, this.offers).result()
                .ifPresent(t -> tag.put("Offers", t));
        }
        net.minecraft.nbt.ListTag bagList = new net.minecraft.nbt.ListTag();
        for (int i = 0; i < this.bag.getContainerSize(); i++) {
            ItemStack s = this.bag.getItem(i);
            if (!s.isEmpty()) {
                CompoundTag slotTag = new CompoundTag();
                slotTag.putByte("Slot", (byte) i);
                slotTag.put("Item", s.save(this.registryAccess()));
                bagList.add(slotTag);
            }
        }
        tag.put("Bag", bagList);

        net.minecraft.nbt.ListTag armorList = new net.minecraft.nbt.ListTag();
        for (int i = 0; i < this.armorSlots.getContainerSize(); i++) {
            ItemStack s = this.armorSlots.getItem(i);
            if (!s.isEmpty()) {
                CompoundTag slotTag = new CompoundTag();
                slotTag.putByte("Slot", (byte) i);
                slotTag.put("Item", s.save(this.registryAccess()));
                armorList.add(slotTag);
            }
        }
        tag.put("ArmorSlots", armorList);
        tag.putInt("StoredXp", storedXp);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setAffection(tag.getInt("AffectionPoints"));
        setStaying(tag.getBoolean("IsStaying"));
        setPartner(tag.getBoolean("PromiseGiven"));
        taughtSpells = tag.getInt("TaughtSpells");
        deepSleeping = tag.getBoolean("DeepSleeping");
        deepSleepTicks = tag.getInt("DeepSleepTicks");
        setSleeping(tag.getBoolean("IsSleeping"));
        setNoGravity(false); // chest dives aren't saved, so she never loads up floating
        questActive = tag.getBoolean("QuestActive");
        questTargetX = tag.getInt("QuestTargetX");
        questTargetY = tag.getInt("QuestTargetY");
        questTargetZ = tag.getInt("QuestTargetZ");
        questGrimoireType = tag.getInt("QuestGrimoireType");
        questCooldown = tag.getInt("QuestCooldown");
        himmelQuestStage = tag.getInt("HimmelQuestStage");
        lastPetDay = tag.contains("LastPetDay") ? tag.getLong("LastPetDay") : -1;
        if (tag.hasUUID("OwnerUUID")) {
            ownerUUID = tag.getUUID("OwnerUUID");
        }
        if (tag.contains("RespawnDim")) {
            respawnDim = tag.getString("RespawnDim");
            respawnBedX = tag.getInt("RespawnBedX");
            respawnBedY = tag.getInt("RespawnBedY");
            respawnBedZ = tag.getInt("RespawnBedZ");
        }
        spellCooldowns.clear();
        CompoundTag cds = tag.getCompound("SpellCooldowns");
        for (String key : cds.getAllKeys()) {
            spellCooldowns.put(key, cds.getInt(key));
        }
        if (tag.contains("Offers")) {
            RegistryOps<net.minecraft.nbt.Tag> ops = RegistryOps.create(NbtOps.INSTANCE, this.registryAccess());
            MerchantOffers.CODEC.parse(ops, tag.get("Offers")).result()
                .ifPresent(o -> this.offers = o);
        }
        if (tag.contains("Bag", net.minecraft.nbt.Tag.TAG_LIST)) {
            this.bag.clearContent();
            net.minecraft.nbt.ListTag bagList = tag.getList("Bag", net.minecraft.nbt.Tag.TAG_COMPOUND);
            for (int i = 0; i < bagList.size(); i++) {
                CompoundTag slotTag = bagList.getCompound(i);
                int slot = slotTag.getByte("Slot") & 255;
                if (slot < this.bag.getContainerSize() && slotTag.contains("Item")) {
                    ItemStack.parse(this.registryAccess(), slotTag.getCompound("Item"))
                        .ifPresent(s -> this.bag.setItem(slot, s));
                }
            }
        }
        if (tag.contains("ArmorSlots", net.minecraft.nbt.Tag.TAG_LIST)) {
            this.armorSlots.clearContent();
            net.minecraft.nbt.ListTag armorList = tag.getList("ArmorSlots", net.minecraft.nbt.Tag.TAG_COMPOUND);
            for (int i = 0; i < armorList.size(); i++) {
                CompoundTag slotTag = armorList.getCompound(i);
                int slot = slotTag.getByte("Slot") & 255;
                if (slot < this.armorSlots.getContainerSize() && slotTag.contains("Item")) {
                    ItemStack.parse(this.registryAccess(), slotTag.getCompound("Item"))
                        .ifPresent(s -> this.armorSlots.setItem(slot, s));
                }
            }
        }
        storedXp = tag.getInt("StoredXp");
        updateArmorAttributes();
    }

    // ----- Misc entity behaviour -----
    @Override
    public boolean removeWhenFarAway(double distance) {
        return false; // Companion should never despawn.
    }

    @Override
    public boolean isPushable() {
        return !isInChest() && super.isPushable();
    }

    // ----- Bag accessors -----
    public SimpleContainer getBag() {
        return this.bag;
    }

    // ----- Armor accessors -----
    public SimpleContainer getFrierenArmor() {
        return this.armorSlots;
    }

    // Wire our armorSlots into vanilla's enchantment and damage pipeline.
    // Without these, Thorns, Protection variants, etc. never fire because the
    // vanilla engine reads getArmorSlots() / getItemBySlot() — not our container.
    @Override
    public Iterable<ItemStack> getArmorSlots() {
        return java.util.List.of(
                armorSlots.getItem(3), // FEET
                armorSlots.getItem(2), // LEGS
                armorSlots.getItem(1), // CHEST
                armorSlots.getItem(0)  // HEAD
        );
    }

    @Override
    public ItemStack getItemBySlot(net.minecraft.world.entity.EquipmentSlot slot) {
        return switch (slot) {
            case HEAD  -> armorSlots.getItem(0);
            case CHEST -> armorSlots.getItem(1);
            case LEGS  -> armorSlots.getItem(2);
            case FEET  -> armorSlots.getItem(3);
            default    -> super.getItemBySlot(slot);
        };
    }

    @Override
    public void setItemSlot(net.minecraft.world.entity.EquipmentSlot slot, ItemStack stack) {
        switch (slot) {
            case HEAD  -> armorSlots.setItem(0, stack);
            case CHEST -> armorSlots.setItem(1, stack);
            case LEGS  -> armorSlots.setItem(2, stack);
            case FEET  -> armorSlots.setItem(3, stack);
            default    -> super.setItemSlot(slot, stack);
        }
    }

    public void updateArmorAttributes() {
        if (this.level() == null || this.level().isClientSide) return;
        double totalArmor = 0;
        double totalToughness = 0;
        for (int i = 0; i < armorSlots.getContainerSize(); i++) {
            ItemStack s = armorSlots.getItem(i);
            if (s.getItem() instanceof net.minecraft.world.item.ArmorItem a) {
                totalArmor += a.getDefense();
                totalToughness += a.getToughness();
            }
        }
        net.minecraft.world.entity.ai.attributes.AttributeInstance armorAttr = getAttribute(Attributes.ARMOR);
        if (armorAttr != null) {
            armorAttr.removeModifier(ARMOR_MOD_ID);
            if (totalArmor > 0) {
                armorAttr.addPermanentModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                        ARMOR_MOD_ID, totalArmor, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE));
            }
        }
        net.minecraft.world.entity.ai.attributes.AttributeInstance toughAttr = getAttribute(Attributes.ARMOR_TOUGHNESS);
        if (toughAttr != null) {
            toughAttr.removeModifier(TOUGHNESS_MOD_ID);
            if (totalToughness > 0) {
                toughAttr.addPermanentModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                        TOUGHNESS_MOD_ID, totalToughness, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE));
            }
        }
    }

    // ----- XP storage -----
    public int getStoredXp() {
        return storedXp;
    }

    public void setStoredXp(int xp) {
        this.storedXp = Math.max(0, xp);
    }

    public void gainXp(int amount) {
        if (amount <= 0) return;
        int remaining = amount;
        for (int i = 0; i < armorSlots.getContainerSize() && remaining > 0; i++) {
            ItemStack stack = armorSlots.getItem(i);
            if (stack.isEmpty() || !stack.isDamageableItem() || stack.getDamageValue() == 0) continue;
            if (!hasMendingEnchant(stack)) continue;
            int damage = stack.getDamageValue();
            int xpToRepairFully = (int) Math.ceil(damage / 2.0);
            int xpUsed = Math.min(remaining, xpToRepairFully);
            stack.setDamageValue(Math.max(0, damage - xpUsed * 2));
            remaining -= xpUsed;
            armorSlots.setChanged();
        }
        storedXp += remaining;
    }

    private boolean hasMendingEnchant(ItemStack stack) {
        for (var entry : stack.getEnchantments().entrySet()) {
            if (entry.getKey().is(net.minecraft.world.item.enchantment.Enchantments.MENDING)) {
                return true;
            }
        }
        return false;
    }

    public void openBag(ServerPlayer player) {
        player.openMenu(
            new SimpleMenuProvider(
                (id, inv, p) -> new FrierenBagMenu(id, inv, this),
                net.minecraft.network.chat.Component.literal("Frieren's Bag")),
            buf -> buf.writeInt(this.getId()));
    }

    // ----- Direct trade (no MerchantMenu, avoids slot-cleanup bugs) -----
    public void directTrade(ServerPlayer player, int tradeIndex) {
        MerchantOffers allOffers = getOffers();
        if (tradeIndex < 0 || tradeIndex >= allOffers.size()) return;
        MerchantOffer offer = allOffers.get(tradeIndex);

        if (offer.isOutOfStock()) {
            player.sendSystemMessage(
                net.minecraft.network.chat.Component.literal("Frieren: I've run out of that for now.")
                    .withStyle(net.minecraft.ChatFormatting.GRAY));
            return;
        }

        ItemCost costA = offer.getItemCostA();
        java.util.Optional<ItemCost> costB = offer.getItemCostB();

        if (!hasEnoughForCost(player, costA) || (costB.isPresent() && !hasEnoughForCost(player, costB.get()))) {
            player.sendSystemMessage(
                net.minecraft.network.chat.Component.literal("Frieren: You don't have what I need.")
                    .withStyle(net.minecraft.ChatFormatting.GRAY));
            return;
        }

        consumeCost(player, costA);
        costB.ifPresent(c -> consumeCost(player, c));

        ItemStack result = offer.getResult().copy();
        if (!player.getInventory().add(result)) {
            player.drop(result, false);
        }

        notifyTrade(offer);
    }

    private boolean hasEnoughForCost(ServerPlayer player, ItemCost cost) {
        int total = 0;
        net.minecraft.world.entity.player.Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty() && cost.test(s)) total += s.getCount();
        }
        return total >= cost.count();
    }

    private void consumeCost(ServerPlayer player, ItemCost cost) {
        int remaining = cost.count();
        net.minecraft.world.entity.player.Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize() && remaining > 0; i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty() && cost.test(s)) {
                int take = Math.min(s.getCount(), remaining);
                s.shrink(take);
                remaining -= take;
            }
        }
    }

    // ----- Death & bed respawn -----
    @Override
    public void die(DamageSource source) {
        if (!level().isClientSide) {
            for (int i = 0; i < this.bag.getContainerSize(); i++) {
                ItemStack stack = this.bag.getItem(i);
                if (!stack.isEmpty()) {
                    this.spawnAtLocation(stack);
                }
            }
            this.bag.clearContent(); // prevent respawn task from re-giving dropped items
            for (int i = 0; i < this.armorSlots.getContainerSize(); i++) {
                ItemStack stack = this.armorSlots.getItem(i);
                if (!stack.isEmpty()) {
                    this.spawnAtLocation(stack);
                }
            }
            this.armorSlots.clearContent();
        }
        if (!level().isClientSide && hasRespawnBed() && level() instanceof ServerLevel) {
            RespawnTask task = new RespawnTask();
            task.ticks = 600; // 30 seconds
            task.data = new CompoundTag();
            this.addAdditionalSaveData(task.data); // copy affection, owner, taughtSpells, bed, etc.
            task.dimensionId = this.respawnDim;
            task.pos = new BlockPos(this.respawnBedX, this.respawnBedY, this.respawnBedZ);
            task.ownerUUID = this.ownerUUID;
            PENDING_RESPAWNS.put(this.getUUID(), task);
        }
        super.die(source);
    }

    /**
     * Ticks down pending respawns and spawns Frieren back at her bed when ready.
     * Called each server tick from {@code CommonEvents}.
     */
    public static void tickRespawns(MinecraftServer server) {
        if (PENDING_RESPAWNS.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<UUID, RespawnTask>> it = PENDING_RESPAWNS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, RespawnTask> entry = it.next();
            RespawnTask task = entry.getValue();
            if (--task.ticks > 0) {
                continue;
            }
            it.remove();
            respawnFrieren(server, task);
        }
    }

    private static void respawnFrieren(MinecraftServer server, RespawnTask task) {
        ResourceKey<Level> dimKey = ResourceKey.create(Registries.DIMENSION,
                ResourceLocation.parse(task.dimensionId));
        ServerLevel target = server.getLevel(dimKey);
        if (target == null) {
            return;
        }

        // Don't spawn a duplicate if a living Frieren with the same owner already exists nearby.
        if (task.ownerUUID != null) {
            AABB search = new AABB(task.pos).inflate(256.0D);
            for (FrierenEntity existing : target.getEntitiesOfClass(FrierenEntity.class, search,
                    f -> f.isAlive() && task.ownerUUID.equals(f.getOwnerUUID()))) {
                if (existing != null) {
                    return;
                }
            }
        }

        FrierenEntity revived = ModEntities.FRIEREN.get().create(target);
        if (revived == null) {
            return;
        }
        revived.readAdditionalSaveData(task.data);
        revived.moveTo(task.pos.getX() + 0.5D, task.pos.getY() + 1.0D, task.pos.getZ() + 0.5D, 0.0F, 0.0F);
        revived.setHealth(revived.getMaxHealth());
        target.addFreshEntity(revived);

        target.sendParticles(ParticleTypes.HEART, revived.getX(), revived.getY() + 1.5D, revived.getZ(),
                20, 0.5D, 0.6D, 0.5D, 0.05D);
        target.sendParticles(ParticleTypes.ENCHANT, revived.getX(), revived.getY() + 1.0D, revived.getZ(),
                30, 0.6D, 1.0D, 0.6D, 0.1D);

        if (task.ownerUUID != null) {
            Player owner = target.getPlayerByUUID(task.ownerUUID);
            if (owner != null) {
                owner.sendSystemMessage(Component.literal("Frieren: ...I came back. Don't look so relieved.")
                        .withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE));
            }
        }
    }

    /** Data carried between a Frieren's death and her bed respawn. */
    public static final class RespawnTask {
        public int ticks;
        public CompoundTag data;
        public String dimensionId;
        public BlockPos pos;
        @Nullable
        public UUID ownerUUID;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return null;
    }

    @Override
    protected SoundEvent getHurtSound(net.minecraft.world.damagesource.DamageSource source) {
        return SoundEvents.PLAYER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.PLAYER_DEATH;
    }

    // ----- GeckoLib -----
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "movement", 5, state -> {
            switch (getChestPose()) {
                case CHEST_SEARCH -> { return state.setAndContinue(CHEST_SEARCH_ANIM); }
                case CHEST_PANIC -> { return state.setAndContinue(CHEST_PANIC_ANIM); }
                case CHEST_DEEPER -> { return state.setAndContinue(CHEST_DEEPER_ANIM); }
                default -> { }
            }
            if (isBowing()) {
                return state.setAndContinue(BOW_ANIM);
            }
            if (isSleeping()) {
                return state.setAndContinue(SLEEP_ANIM);
            }
            if (state.isMoving()) {
                return state.setAndContinue(WALK_ANIM);
            }
            return state.setAndContinue(IDLE_ANIM);
        }));
        controllers.add(new AnimationController<>(this, "action", 5, state -> PlayState.STOP)
                .triggerableAnim("cast_spell", CAST_ANIM)
                .triggerableAnim("talk", TALK_ANIM));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}
