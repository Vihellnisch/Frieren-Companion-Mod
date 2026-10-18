package com.frierenmod;

import com.frierenmod.entity.FrierenEntity;
import com.frierenmod.entity.MimicEntity;
import com.frierenmod.spell.MidasSpell;
import com.frierenmod.spell.BarrierSpell;
import com.frierenmod.magic.PlayerMagic;
import com.frierenmod.network.FrierenSyncPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerWakeUpEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Game (forge) bus events driving Frieren's ambient behaviours: demon-detection aura,
 * season/time reactions, partner chatter, sleep syncing, deep sleep, morning greetings,
 * bed claiming and the pending-respawn scheduler.
 */
@EventBusSubscriber(modid = FrierenMod.MODID)
public final class CommonEvents {

    private static final Random RANDOM = new Random();

    // Per-player timing state.
    private static final Map<UUID, Long> LAST_REACTION = new HashMap<>();
    private static final Map<UUID, Long> NEXT_PARTNER_CHAT = new HashMap<>();
    private static final Map<UUID, Boolean> WAS_SLEEPING = new HashMap<>();
    // Per-dimension day tracking for deep-sleep rolls.
    private static final Map<net.minecraft.resources.ResourceLocation, Long> LAST_DAY = new HashMap<>();

    private static final String[] PARTNER_LINES = {
            "Frieren: *gently pats your head* ...You did well today.",
            "Frieren: Eighty-two years... and you're still the one I think of.",
            "Frieren: The flowers are blooming. Himmel liked these too.",
            "Frieren: ...Stay close. The road is long.",
            "Frieren: I've forgotten many faces. I won't forget yours.",
            "Frieren: A human life is brief. I'll treasure every day of it.",
            "Frieren: ...Don't wander off. I worry, a little.",
            "Frieren: You're humming Himmel's old song again. It suits you.",
            "Frieren: Even mages need rest. Sit with me a while.",
            "Frieren: I collected a new spell today. Want to see it later?",
            "Frieren: The stars are the same as a thousand years ago. You aren't.",
            "Frieren: ...I'm glad I decided to know you better.",
            "Frieren: When you sleep, you look almost peaceful. Almost.",
            "Frieren: If a demon comes, I'll handle it. You just stay behind me.",
            "Frieren: ...I love you. Don't make me repeat it."
    };

    // ----- XP gain when Frieren kills a mob -----
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide) return;
        Entity killer = event.getSource().getEntity();
        if (!(killer instanceof FrierenEntity frieren)) return;
        int xp = 0;
        if (victim instanceof net.minecraft.world.entity.Mob mob
                && victim.level() instanceof net.minecraft.server.level.ServerLevel sl) {
            xp = mob.getExperienceReward(sl, frieren);
        }
        if (xp > 0) {
            frieren.gainXp(xp);
        }
    }

    // ----- Morning / wake handling -----
    @SubscribeEvent
    public static void onPlayerWakeUp(PlayerWakeUpEvent event) {
        Player player = event.getEntity();
        Level level = player.level();
        if (level.isClientSide) {
            return;
        }

        AABB box = player.getBoundingBox().inflate(16.0D);
        List<FrierenEntity> nearby = level.getEntitiesOfClass(FrierenEntity.class, box,
                f -> f.isAlive() && f.isOwnedBy(player)
                        && f.getStage().ordinal() >= FrierenEntity.Stage.FRIEND.ordinal());

        // Wake any Frieren sleeping alongside the player.
        for (FrierenEntity f : nearby) {
            if (f.isSleeping() && !f.isDeepSleeping()) {
                f.wakeUp();
                f.setStaying(false);
                player.sendSystemMessage(Component.literal("Frieren: ...You're finally awake.")
                        .withStyle(ChatFormatting.LIGHT_PURPLE));
            }
        }

        boolean hasPartner = nearby.stream().anyMatch(f -> f.getStage() == FrierenEntity.Stage.PARTNER);
        if (hasPartner) {
            player.addEffect(new MobEffectInstance(MobEffects.LUCK, 12000, 0));
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 12000, 0));
        }

        // Feature 9: random morning greeting from any FRIEND+ Frieren.
        if (!nearby.isEmpty()) {
            String[] morning = {
                    "Frieren: ...You snore a little.",
                    "Frieren: I've been awake for an hour. You sleep too long.",
                    "Frieren: ...Good morning."
            };
            player.sendSystemMessage(Component.literal(morning[RANDOM.nextInt(morning.length)])
                    .withStyle(ChatFormatting.AQUA));
        }
    }

    // ----- Bed claiming -----
    @SubscribeEvent
    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        BlockState state = event.getPlacedBlock();
        if (!(state.getBlock() instanceof BedBlock)) {
            return;
        }
        Entity placer = event.getEntity();
        if (!(placer instanceof Player player)) {
            return;
        }
        Level level = player.level();
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        BlockPos placedPos = event.getPos();
        AABB searchBox = new AABB(placedPos).inflate(64.0D);
        List<FrierenEntity> owned = serverLevel.getEntitiesOfClass(FrierenEntity.class, searchBox,
                f -> f.isAlive() && f.isOwnedBy(player));
        if (owned.isEmpty()) {
            owned = serverLevel.getEntitiesOfClass(FrierenEntity.class,
                    new AABB(placedPos).inflate(256.0D), f -> f.isAlive() && f.isOwnedBy(player));
        }
        if (owned.isEmpty()) {
            return;
        }

        ResourceKey<Level> dim = serverLevel.dimension();
        for (FrierenEntity frieren : owned) {
            frieren.setRespawnBed(placedPos, dim);
        }
        player.sendSystemMessage(Component.literal("Frieren: ...I'll remember this place.")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
    }

    // ----- Login: reapply Himmel blessing and sync magic state -----
    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (PlayerMagic.hasHimmelBlessing(player)) {
            player.addEffect(new MobEffectInstance(MobEffects.LUCK, 999999, 0, false, false));
        }
        FrierenSyncPacket.sync(player);
    }

    // ----- Per-tick ambient behaviours -----
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server == null) {
            return;
        }
        FrierenEntity.tickRespawns(server);

        long tick = server.getTickCount();

        // Deep-sleep day roll (once per in-game day, per dimension).
        for (ServerLevel level : server.getAllLevels()) {
            MidasSpell.tick(level);
            BarrierSpell.tick(level);
            long day = level.getDayTime() / 24000L;
            Long prev = LAST_DAY.get(level.dimension().location());
            if (prev == null) {
                LAST_DAY.put(level.dimension().location(), day);
            } else if (day > prev) {
                LAST_DAY.put(level.dimension().location(), day);
                rollDeepSleep(level);
            }
        }

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            // Demon/undead detection aura every 40 ticks.
            if (tick % 40 == 0) {
                detectionAura(player);
            }
            // Season & time reactions every 600 ticks.
            if (tick % 600 == 0) {
                timeReaction(player, tick);
            }
            // Random partner chat.
            partnerChat(player, tick);
            // Sleep edge detection.
            sleepEdge(player);
        }
    }

    private static void detectionAura(ServerPlayer player) {
        Level level = player.level();
        AABB frierenBox = player.getBoundingBox().inflate(30.0D);
        List<FrierenEntity> bound = level.getEntitiesOfClass(FrierenEntity.class, frierenBox,
                f -> f.isAlive() && f.isOwnedBy(player) && !f.isStaying() && !f.isSleeping()
                        && f.getStage().ordinal() >= FrierenEntity.Stage.FRIEND.ordinal());
        if (bound.isEmpty()) {
            return;
        }
        AABB mobBox = player.getBoundingBox().inflate(20.0D);
        List<LivingEntity> mobs = level.getEntitiesOfClass(LivingEntity.class, mobBox,
                e -> e != player && e.isAlive()
                        && !(e instanceof MimicEntity mimic && !mimic.isAwake()) // a disguise her aura doesn't see through
                        && (e instanceof Monster || e instanceof Enemy
                        || e.getType().is(EntityTypeTags.UNDEAD)));
        for (LivingEntity mob : mobs) {
            mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, 60, 0, false, false));
        }
    }

    private static void timeReaction(ServerPlayer player, long tick) {
        Level level = player.level();
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        AABB box = player.getBoundingBox().inflate(30.0D);
        List<FrierenEntity> nearby = level.getEntitiesOfClass(FrierenEntity.class, box,
                f -> f.isAlive() && f.isOwnedBy(player) && !f.isSleeping()
                        && f.getStage().ordinal() >= FrierenEntity.Stage.FRIEND.ordinal());
        if (nearby.isEmpty()) {
            return;
        }
        Long last = LAST_REACTION.get(player.getUUID());
        if (last != null && tick - last < 4800) {
            return;
        }

        long dayTime = serverLevel.getDayTime() % 24000L;
        String msg = null;
        if (serverLevel.dimensionType().natural() && level.getMoonPhase() == 0 && dayTime >= 13000 && dayTime <= 23000) {
            msg = "Frieren: A full moon... Himmel would've called it beautiful.";
        } else if (serverLevel.isThundering()) {
            msg = "Frieren: ...Storms make the mana restless. Stay near.";
        } else if (dayTime >= 23000 || dayTime <= 1000) {
            msg = "Frieren: The sunrise. ...I've seen thousands, yet here I am watching another.";
        } else if (dayTime >= 11500 && dayTime <= 13000) {
            msg = "Frieren: Sunset already. The days pass so quickly for you.";
        } else if (serverLevel.isRaining()) {
            msg = "Frieren: Rain again. ...I don't mind it, with company.";
        } else if (dayTime >= 5500 && dayTime <= 6500) {
            msg = "Frieren: Midday. We should keep moving while it's bright.";
        }

        if (msg != null) {
            LAST_REACTION.put(player.getUUID(), tick);
            player.sendSystemMessage(Component.literal(msg).withStyle(ChatFormatting.AQUA));
        }
    }

    private static void partnerChat(ServerPlayer player, long tick) {
        Long next = NEXT_PARTNER_CHAT.get(player.getUUID());
        if (next == null) {
            NEXT_PARTNER_CHAT.put(player.getUUID(), tick + 2400 + RANDOM.nextInt(2400));
            return;
        }
        if (tick < next) {
            return;
        }
        Level level = player.level();
        AABB box = player.getBoundingBox().inflate(50.0D);
        List<FrierenEntity> partners = level.getEntitiesOfClass(FrierenEntity.class, box,
                f -> f.isAlive() && f.isOwnedBy(player) && !f.isSleeping()
                        && f.getStage() == FrierenEntity.Stage.PARTNER);
        NEXT_PARTNER_CHAT.put(player.getUUID(), tick + 2400 + RANDOM.nextInt(2400));
        if (!partners.isEmpty()) {
            player.sendSystemMessage(Component.literal(PARTNER_LINES[RANDOM.nextInt(PARTNER_LINES.length)])
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        }
    }

    private static void sleepEdge(ServerPlayer player) {
        boolean sleeping = player.isSleeping();
        boolean was = WAS_SLEEPING.getOrDefault(player.getUUID(), false);
        if (sleeping && !was) {
            // Rising edge: send a nearby bound Frieren to her bed.
            Level level = player.level();
            AABB box = player.getBoundingBox().inflate(16.0D);
            List<FrierenEntity> nearby = level.getEntitiesOfClass(FrierenEntity.class, box,
                    f -> f.isAlive() && f.isOwnedBy(player) && !f.isSleeping()
                            && f.getStage().ordinal() >= FrierenEntity.Stage.FRIEND.ordinal());
            for (FrierenEntity f : nearby) {
                BlockPos bed = f.getRespawnBedPos();
                if (bed != null && bed.closerThan(f.blockPosition(), 8.0D)) {
                    f.startSleepingAt(bed);
                    break;
                }
            }
        }
        WAS_SLEEPING.put(player.getUUID(), sleeping);
    }

    private static void rollDeepSleep(ServerLevel level) {
        // For each online player's nearby bound Frieren, 15% chance of a deep sleep.
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            if (player.level() != level) {
                continue;
            }
            AABB box = player.getBoundingBox().inflate(64.0D);
            List<FrierenEntity> owned = level.getEntitiesOfClass(FrierenEntity.class, box,
                    f -> f.isAlive() && f.isOwnedBy(player) && !f.isDeepSleeping()
                            && f.getStage().ordinal() >= FrierenEntity.Stage.FRIEND.ordinal());
            for (FrierenEntity f : owned) {
                if (RANDOM.nextInt(100) < 15) {
                    f.startDeepSleep(24000);
                    player.sendSystemMessage(Component.literal("Frieren is sleeping deeply today...")
                            .withStyle(ChatFormatting.DARK_GRAY));
                }
            }
        }
    }

    private CommonEvents() {}
}
