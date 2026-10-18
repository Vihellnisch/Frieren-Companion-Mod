package com.frierenmod.item;

import com.frierenmod.entity.FrierenEntity;
import com.frierenmod.magic.PlayerMagic;
import com.frierenmod.network.FrierenSyncPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Reusable fusion-spell tomes crafted from grimoire combinations. Each has its own cooldown
 * and requirement, and grants mana proficiency on use.
 */
public class FusionTomeItem extends Item {

    public enum FusionType {
        VERDANT_ZOLTRAAK("verdant_zoltraak", 60),
        AQUA_BARRIER("aqua_barrier", 100),
        MANA_STORM("mana_storm", 1200);

        public final String key;
        public final int cooldownTicks;

        FusionType(String key, int cooldownTicks) {
            this.key = key;
            this.cooldownTicks = cooldownTicks;
        }
    }

    private final FusionType type;

    public FusionTomeItem(FusionType type, Properties properties) {
        super(properties);
        this.type = type;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (level.isClientSide) {
            return InteractionResultHolder.sidedSuccess(stack, true);
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.success(stack);
        }
        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.success(stack);
        }

        // Requirements.
        if (type == FusionType.VERDANT_ZOLTRAAK
                && !PlayerMagic.hasLearned(serverPlayer, PlayerMagic.SPELL_ZOLTRAAK_T2)) {
            deny(player, "You must master Zoltraak II before using this fusion.");
            return InteractionResultHolder.success(stack);
        }
        if (type == FusionType.AQUA_BARRIER && !hasAllyFrierenNearby(serverPlayer)) {
            deny(player, "This fusion needs an Ally-bond Frieren nearby.");
            return InteractionResultHolder.success(stack);
        }

        boolean cast = switch (type) {
            case VERDANT_ZOLTRAAK -> castVerdant(level, serverPlayer);
            case AQUA_BARRIER -> castAquaBarrier(level, serverPlayer);
            case MANA_STORM -> castManaStorm(level, serverPlayer);
        };

        if (cast) {
            player.getCooldowns().addCooldown(this, type.cooldownTicks);
            PlayerMagic.addProficiency(serverPlayer);
            FrierenSyncPacket.sync(serverPlayer);
        }
        return InteractionResultHolder.success(stack);
    }

    private boolean castVerdant(Level level, ServerPlayer player) {
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.EVOKER_CAST_SPELL, SoundSource.PLAYERS, 1.0F, 1.0F);
        BlockPos center = rayTraceBlock(level, player, 30.0D);
        float damage = 10.0F * PlayerMagic.damageMultiplier(player);

        // Bone-meal a 3-block radius around the impact.
        ItemStack bonemeal = new ItemStack(Items.BONE_MEAL, 64);
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-3, -1, -3), center.offset(3, 1, 3))) {
            BoneMealItem.applyBonemeal(bonemeal.copy(), level, pos.immutable(), player);
        }
        // Damage entities in the AoE.
        AABB box = new AABB(center).inflate(3.0D);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, box, e -> e != player && e.isAlive())) {
            target.hurt(player.damageSources().indirectMagic(player, player), damage);
        }
        if (level instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, center.getX() + 0.5, center.getY() + 1.0, center.getZ() + 0.5,
                    40, 3.0D, 1.0D, 3.0D, 0.1D);
            sl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, center.getX() + 0.5, center.getY() + 1.0, center.getZ() + 0.5,
                    30, 2.0D, 1.0D, 2.0D, 0.05D);
        }
        player.displayClientMessage(Component.literal("Verdant Zoltraak: life and ruin as one.")
                .withStyle(ChatFormatting.GREEN), true);
        return true;
    }

    private boolean castAquaBarrier(Level level, ServerPlayer player) {
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.PLAYER_SPLASH, SoundSource.PLAYERS, 1.0F, 1.0F);
        player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 600, 0));
        player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 600, 2)); // Absorption III
        // Ring of water around the player.
        BlockPos base = player.blockPosition();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                int dist = dx * dx + dz * dz;
                if (dist >= 3 && dist <= 5) {
                    BlockPos pos = base.offset(dx, 0, dz);
                    BlockState state = level.getBlockState(pos);
                    if (state.canBeReplaced() || state.isAir()) {
                        level.setBlock(pos, Blocks.WATER.defaultBlockState(), 3);
                    }
                }
            }
        }
        if (level instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.SPLASH, player.getX(), player.getY() + 1.0, player.getZ(),
                    60, 1.5D, 0.5D, 1.5D, 0.1D);
        }
        player.displayClientMessage(Component.literal("Aqua Barrier: the water shields you.")
                .withStyle(ChatFormatting.AQUA), true);
        return true;
    }

    private boolean castManaStorm(Level level, ServerPlayer player) {
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 1.0F, 1.0F);
        float damage = 8.0F * PlayerMagic.damageMultiplier(player);
        BlockPos strike = rayTraceBlock(level, player, 20.0D);

        if (level instanceof ServerLevel sl) {
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(sl);
            if (bolt != null) {
                bolt.moveTo(Vec3.atBottomCenterOf(strike));
                bolt.setVisualOnly(true);
                sl.addFreshEntity(bolt);
            }
            sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY() + 1.0, player.getZ(),
                    80, 5.0D, 2.0D, 5.0D, 0.2D);
        }
        // Einsam-style AoE within 10 blocks.
        AABB box = player.getBoundingBox().inflate(10.0D);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, box, e -> e != player && e.isAlive())) {
            target.hurt(player.damageSources().indirectMagic(player, player), damage);
            Vec3 dir = target.position().subtract(player.position()).normalize();
            target.knockback(1.5D, -dir.x, -dir.z);
        }
        player.displayClientMessage(Component.literal("Mana Storm: the sky answers your call.")
                .withStyle(ChatFormatting.LIGHT_PURPLE), true);
        return true;
    }

    private static boolean hasAllyFrierenNearby(Player player) {
        AABB box = player.getBoundingBox().inflate(16.0D);
        List<FrierenEntity> nearby = player.level().getEntitiesOfClass(FrierenEntity.class, box,
                f -> f.isAlive() && f.isOwnedBy(player)
                        && f.getStage().ordinal() >= FrierenEntity.Stage.ALLY.ordinal());
        return !nearby.isEmpty();
    }

    private static BlockPos rayTraceBlock(Level level, Player player, double reach) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F);
        Vec3 end = eye.add(look.scale(reach));
        HitResult hit = level.clip(new ClipContext(eye, end,
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        if (hit instanceof BlockHitResult bhr && hit.getType() != HitResult.Type.MISS) {
            return bhr.getBlockPos();
        }
        return BlockPos.containing(end);
    }

    private void deny(Player player, String msg) {
        player.displayClientMessage(Component.literal(msg).withStyle(ChatFormatting.GRAY), true);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.frierenmod." + type.key)
                .withStyle(ChatFormatting.GRAY));
        super.appendHoverText(stack, context, tooltip, flag);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }
}
