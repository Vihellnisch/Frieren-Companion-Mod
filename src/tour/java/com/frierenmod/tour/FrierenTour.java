package com.frierenmod.tour;

import com.frierenmod.FrierenMod;
import com.frierenmod.entity.FrierenEntity;
import com.frierenmod.entity.MimicEntity;
import com.frierenmod.magic.Fusion;
import com.frierenmod.magic.PlayerMagic;
import com.frierenmod.magic.SpellCaster.StaffSpell;
import com.frierenmod.magic.SpellEntries;
import com.frierenmod.network.FrierenSyncPacket;
import com.frierenmod.network.SpellFusePacket;
import com.frierenmod.spell.UselessSpells;
import com.frierenmod.registry.ModEntities;
import com.frierenmod.world.MimicSpawner;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Dev-only scripted walkthrough (./gradlew runTourClient). Each stop sets up a scene; FrierenTourClient
 * takes a screenshot with the GUI hidden near the end of each stop and quits after the last one.
 * Does nothing unless -Dfrierenmod.tour=true.
 */
@EventBusSubscriber(modid = FrierenMod.MODID)
public final class FrierenTour {
    static final boolean ENABLED = Boolean.getBoolean("frierenmod.tour");
    static final boolean SHOWCASE = Boolean.getBoolean("frierenmod.tour.showcase");
    static final int FIRST_STOP = 60;
    static final int STOP_LENGTH = 130;
    static final int SHOT_AT = 115;
    private static final Logger LOGGER = LogUtils.getLogger();

    private record Stop(String name, BiConsumer<ServerPlayer, ServerLevel> action) {
    }

    private static final List<Stop> STOPS = buildStops();
    static final List<String> STOP_NAMES = STOPS.stream().map(Stop::name).toList();
    static volatile int ticks = -1;
    private static final List<Map.Entry<Integer, Runnable>> LATER = new ArrayList<>();

    /** The scene: a mossy dungeon floor up in the sky, chests at its middle facing north (-Z). */
    private static final BlockPos C = new BlockPos(2000, 140, 2000);
    private static final ResourceKey<LootTable> DUNGEON_LOOT =
            ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.withDefaultNamespace("chests/simple_dungeon"));
    private static BlockPos village;

    private static List<Stop> buildStops() {
        List<Stop> s = new ArrayList<>();
        addStops(s);
        addSpellStops(s);
        String only = System.getProperty("frierenmod.tour.only", "");
        if (!only.isEmpty()) s.removeIf(stop -> !stop.name().startsWith(only));
        if (SHOWCASE && !only.isEmpty()) {
            // showcase: only the shots that show something, nothing twice
            s.removeIf(stop -> stop.name().contains("third_person") || stop.name().contains("homing"));
        }
        if (SHOWCASE && only.isEmpty()) {
            // the user-facing tour: just the Mimic feature, nothing repeated
            List<String> keep = List.of("hidden_mimic_vs_chest", "mimic_awake_side", "frieren_chest_search_side",
                    "frieren_goes_for_mimic", "frieren_swallowed", "push_gag", "after_spit", "village_mimic_close");
            s.removeIf(stop -> !keep.contains(stop.name()) && !stop.name().equals("mimic_awake")
                    && !stop.name().equals("frieren_chest_search") && !stop.name().equals("village_mimics"));
        }
        return s;
    }

    private static void addStops(List<Stop> s) {
        s.add(new Stop("hidden_mimic_vs_chest", (p, l) -> {
            reset(l);
            chest(l, C.west(), true);
            hiddenMimic(l, C.east());
            look(p, C, 0, -4, 1.6, 0, 28);
        }));
        s.add(new Stop("mimic_awake", (p, l) -> {
            reset(l);
            MimicEntity m = hiddenMimic(l, C);
            m.hurt(l.damageSources().playerAttack(p), 0.01F);
            look(p, C, 0, -3, 1.4, 0, 30);
        }));
        s.add(new Stop("mimic_awake_side", (p, l) -> look(p, C, 3, -1, 1.2, 70, 25)));
        s.add(new Stop("mimic_reveal_survival", (p, l) -> {
            reset(l);
            hiddenMimic(l, C);
            look(p, C, 0, -1.9, 1.62, 0, 30);
            p.setGameMode(GameType.SURVIVAL);
            p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 400, 4));
            later(100, () -> LOGGER.info("TOUR survival reveal: mimic state={} player health={}",
                    mimics(l).stream().map(MimicEntity::getState).toList(), p.getHealth()));
        }));
        s.add(new Stop("frieren_chest_search", (p, l) -> {
            reset(l);
            chest(l, C, false);
            FrierenEntity f = frieren(l, C.offset(-2, 1, -2), p);
            l.blockEvent(C.above(), Blocks.CHEST, 1, 1);
            f.enterChest(Vec3.atBottomCenterOf(C.above()).add(0, 10.0D / 16.0D, 0), 180.0F, null);
            look(p, C, 0, -3.2, 1.6, 0, 25);
        }));
        s.add(new Stop("frieren_chest_search_side", (p, l) -> look(p, C, 3, 0.5, 1.4, 90, 20)));
        s.add(new Stop("frieren_goes_for_mimic", (p, l) -> {
            reset(l);
            hiddenMimic(l, C);
            frieren(l, C.offset(-4, 1, -3), p);
            look(p, C, 1, -6, 2.5, 0, 22);
            for (int t = 20; t < STOP_LENGTH * 4; t += 20) {
                int at = t;
                later(t, () -> LOGGER.info("TOUR mimic scene +{}: mimic={} frieren pose={}", at,
                        mimics(l).stream().map(MimicEntity::getState).toList(),
                        frierens(l).stream().map(FrierenEntity::getChestPose).toList()));
            }
        }));
        s.add(new Stop("frieren_swallowed", (p, l) -> look(p, C, 0, -3.2, 1.6, 0, 25)));
        s.add(new Stop("frieren_swallowed_side", (p, l) -> look(p, C, 3, 0.3, 1.3, 90, 18)));
        s.add(new Stop("push_gag", (p, l) -> {
            look(p, C, 0, -3.2, 1.6, 0, 25);
            later(SHOT_AT - 20, () -> mimics(l).forEach(m -> m.pushDeeper(p)));
        }));
        s.add(new Stop("after_spit", (p, l) -> look(p, C, 4, -5, 2.2, 39, 22)));
        s.add(new Stop("frieren_chest_goal", (p, l) -> {
            reset(l);
            chest(l, C, false);
            FrierenEntity f = frieren(l, C.offset(-3, 1, -3), p);
            // skip the first-minute cooldown so the (random) dive happens during the tour
            f.goalSelector.getAvailableGoals().stream().map(g -> g.getGoal())
                    .filter(g -> g instanceof com.frierenmod.entity.ai.FrierenChestGoal).forEach(g -> {
                        try {
                            var field = g.getClass().getDeclaredField("cooldown");
                            field.setAccessible(true);
                            field.setInt(g, 0);
                        } catch (ReflectiveOperationException e) {
                            throw new RuntimeException(e);
                        }
                    });
            look(p, C, 2, -4, 2.0, 27, 25);
            for (int t = 20; t < STOP_LENGTH * 2; t += 20) {
                int at = t;
                later(t, () -> frierens(l).forEach(fr -> LOGGER.info("TOUR chest goal +{}: pose={} onGround={} target={} running={} goal={}", at,
                        fr.getChestPose(), fr.onGround(), fr.getTarget(),
                        fr.goalSelector.getAvailableGoals().stream().filter(g -> g.isRunning()).map(g -> g.getGoal().getClass().getSimpleName()).toList(),
                        fieldDump(fr))));
            }
        }));
        s.add(new Stop("frieren_chest_goal_2", (p, l) -> { }));
        s.add(new Stop("loot_from_dungeon_chest", (p, l) -> {
            reset(l);
            chest(l, C, true);
            MimicEntity m = MimicSpawner.convert(l, C.above());
            LOGGER.info("TOUR converted dungeon chest: mimic={} block now={}", m != null, l.getBlockState(C.above()));
            if (m != null) m.kill();
            later(20, () -> LOGGER.info("TOUR mimic loot: {}", l.getEntitiesOfClass(ItemEntity.class,
                    new AABB(C).inflate(6)).stream().map(i -> i.getItem().getCount() + "x " + i.getItem().getHoverName().getString()).toList()));
            look(p, C, 0, -3, 2.2, 0, 45);
        }));
        s.add(new Stop("village_mimics", (p, l) -> {
            reset(l);
            Pair<BlockPos, Holder<Structure>> found = l.getChunkSource().getGenerator().findNearestMapStructure(
                    l, l.registryAccess().registryOrThrow(Registries.STRUCTURE).getTag(StructureTags.VILLAGE).orElseThrow(),
                    new BlockPos(0, 64, 0), 64, false);
            village = found == null ? new BlockPos(0, 64, 0) : found.getFirst();
            LOGGER.info("TOUR nearest village {}", village);
            int y = l.getChunk(village).getHeight(Heightmap.Types.MOTION_BLOCKING, village.getX() & 15, village.getZ() & 15);
            look(p, village, 0, -12, y - village.getY() + 14, 0, 40);
        }));
        s.add(new Stop("village_mimic_close", (p, l) -> {
            List<MimicEntity> ms = l.getEntitiesOfClass(MimicEntity.class, new AABB(village).inflate(160, 128, 160));
            LOGGER.info("TOUR mimics around the village: {} at {}", ms.size(), ms.stream().map(Entity::blockPosition).toList());
            if (!ms.isEmpty()) {
                MimicEntity m = ms.stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(Vec3.atCenterOf(village)))).get();
                Vec3 front = Vec3.directionFromRotation(0, m.getYRot()).scale(3.0D);
                go(p, m.getX() + front.x, m.getY() + 1.0D, m.getZ() + front.z, m.getYRot() + 180.0F, 25);
            }
        }));
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!ENABLED) return;
        var players = event.getServer().getPlayerList().getPlayers();
        if (players.isEmpty()) return;
        ServerPlayer player = players.get(0);
        ServerLevel level = event.getServer().overworld();
        if (++ticks == 0) {
            GameRules rules = level.getGameRules();
            rules.getRule(GameRules.RULE_DAYLIGHT).set(false, event.getServer());
            rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, event.getServer());
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, event.getServer());
            level.setDayTime(6000);
            level.setWeatherParameters(6000, 0, false, false);
        }
        LATER.removeIf(task -> {
            if (task.getKey() > ticks) return false;
            try {
                task.getValue().run();
            } catch (RuntimeException e) {
                LOGGER.error("TOUR delayed task failed", e);
            }
            return true;
        });
        if (ticks < FIRST_STOP || (ticks - FIRST_STOP) % STOP_LENGTH != 0) return;
        int stop = (ticks - FIRST_STOP) / STOP_LENGTH;
        if (stop >= STOPS.size()) return;
        player.setGameMode(GameType.CREATIVE);
        try {
            STOPS.get(stop).action().accept(player, level);
        } catch (RuntimeException e) {
            LOGGER.error("TOUR stop {} failed", STOPS.get(stop).name(), e);
        }
        player.getAbilities().flying = player.isCreative();
        player.onUpdateAbilities();
        LOGGER.info("TOUR stop {}: {} at {}", stop, STOPS.get(stop).name(), player.position());
    }

    private static String fieldDump(FrierenEntity f) {
        StringBuilder b = new StringBuilder();
        f.goalSelector.getAvailableGoals().stream().map(g -> g.getGoal())
                .filter(g -> g instanceof com.frierenmod.entity.ai.FrierenChestGoal).forEach(g -> {
                    for (String n : new String[] {"cooldown", "checkDelay", "chestPos", "diving", "done"}) {
                        try {
                            var field = g.getClass().getDeclaredField(n);
                            field.setAccessible(true);
                            b.append(n).append('=').append(field.get(g)).append(' ');
                        } catch (ReflectiveOperationException e) {
                            b.append(n).append("=? ");
                        }
                    }
                });
        return b.toString();
    }

    // ----- v1.2: useless spells, spellbook, fusions, head pats -----
    private static void addSpellStops(List<Stop> s) {
        s.add(new Stop("v12_spellbook_screen", (p, l) -> {
            reset(l);
            PlayerMagic.learn(p, 0xFFF);
            PlayerMagic.discoverFusion(p, Fusion.HOMING_BLAST.ordinal());
            PlayerMagic.discoverFusion(p, Fusion.GOLDEN_ZOLTRAAK.ordinal());
            PlayerMagic.discoverFusion(p, Fusion.BUTTERFLY_BOMBS.ordinal());
            PlayerMagic.setLoadout(p, new int[] {
                    SpellEntries.code(StaffSpell.ZOLTRAAK_T1), SpellEntries.code(StaffSpell.ZOLTRAAK_T3),
                    SpellEntries.code(StaffSpell.FLOWER_FIELD), SpellEntries.code(Fusion.GOLDEN_ZOLTRAAK),
                    SpellEntries.code(StaffSpell.GOLDEN_BUTTERFLIES), SpellEntries.code(Fusion.HOMING_BLAST)});
            PlayerMagic.setSelectedSpell(p, SpellEntries.code(StaffSpell.ZOLTRAAK_T1));
            FrierenSyncPacket.sync(p);
            // fusing through the real handler: one that works, one that doesn't
            SpellFusePacket.fuse(p, SpellEntries.code(StaffSpell.FLOWER_FIELD), SpellEntries.code(StaffSpell.GROW_GRASS));
            SpellFusePacket.fuse(p, SpellEntries.code(StaffSpell.CREATE_WATER), SpellEntries.code(StaffSpell.MIDAS));
            LOGGER.info("TOUR spellbook: fusions={} wheel={}", Integer.toBinaryString(PlayerMagic.getFusions(p)),
                    SpellEntries.loadout(p).stream().map(SpellEntries::displayName).toList());
            look(p, C, 0, -6, 1.62, 0, 10);
        }));
        s.add(new Stop("v12_flower_field", (p, l) -> {
            reset(l);
            floor(l, Blocks.GRASS_BLOCK);
            go(p, C.getX() + 0.5, C.getY() + 1, C.getZ() + 0.5, 0, 90);
            boolean ok = UselessSpells.flowerField(p, 6);
            later(40, () -> LOGGER.info("TOUR flower field: cast={} flowers={}", ok, countFlowers(l)));
            later(5, () -> look(p, C, 0, -9, 7.0, 0, 38));
        }));
        s.add(new Stop("v12_grow_grass", (p, l) -> {
            reset(l);
            floor(l, Blocks.DIRT);
            go(p, C.getX() + 0.5, C.getY() + 1, C.getZ() + 0.5, 0, 90);
            boolean ok = UselessSpells.growGrass(p, 6);
            later(SHOT_AT - 104, () -> look(p, C, 0, -9, 7.0, 0, 38));
            later(40, () -> LOGGER.info("TOUR grow grass: cast={} grass={}", ok, count(l, Blocks.GRASS_BLOCK)));
        }));
        s.add(new Stop("v12_spring_bloom_fusion", (p, l) -> {
            reset(l);
            floor(l, Blocks.DIRT);
            go(p, C.getX() + 0.5, C.getY() + 1, C.getZ() + 0.5, 0, 90);
            boolean ok = Fusion.SPRING_BLOOM.cast(p);
            later(5, () -> look(p, C, 0, -11, 9.0, 0, 40));
            later(60, () -> LOGGER.info("TOUR spring bloom: cast={} grass={} flowers={}", ok, count(l, Blocks.GRASS_BLOCK), countFlowers(l)));
        }));
        s.add(new Stop("v12_rust_removal", (p, l) -> {
            reset(l);
            // two identical copper walls; only the left one gets the spell
            for (int x = 0; x < 3; x++) for (int y = 1; y <= 3; y++) {
                BlockState st = (x + y) % 3 == 0 ? Blocks.WAXED_OXIDIZED_COPPER.defaultBlockState()
                        : (x + y) % 3 == 1 ? Blocks.OXIDIZED_CUT_COPPER.defaultBlockState() : Blocks.WEATHERED_COPPER.defaultBlockState();
                l.setBlock(C.offset(-4 + x, y, 3), st, 3);
                l.setBlock(C.offset(2 + x, y, 3), st, 3);
            }
            go(p, C.getX() - 2.5 + 0.5, C.getY() + 1, C.getZ() + 0.5, 0, -10);
            boolean ok = UselessSpells.rustRemoval(p);
            LOGGER.info("TOUR rust removal: cast={} left={} right={}", ok, l.getBlockState(C.offset(-3, 2, 3)), l.getBlockState(C.offset(3, 2, 3)));
            later(3, () -> look(p, C, 0, -4, 2.2, 0, 8));
        }));
        s.add(new Stop("v12_torch_and_water", (p, l) -> {
            reset(l);
            go(p, C.getX() + 0.5, C.getY() + 1, C.getZ() + 4.5, 0, 0);   // facing the back wall
            boolean torch = UselessSpells.createTorch(p);
            go(p, C.getX() + 0.5, C.getY() + 1, C.getZ() + 4.5, 180, 60); // floor behind
            boolean water = UselessSpells.createWater(p);
            LOGGER.info("TOUR torch={} water={}", torch, water);
            later(3, () -> look(p, C, -3, 3.5, 2.2, -60, 20));
        }));
        s.add(new Stop("v12_golden_butterflies", (p, l) -> {
            reset(l);
            floor(l, Blocks.GRASS_BLOCK);
            UselessSpells.flowerField(p, 0);
            go(p, C.getX() + 0.5, C.getY() + 1, C.getZ() - 2.5, 0, -10);
            UselessSpells.butterflies(p, 12, false);
            later(SHOT_AT - 40, () -> look(p, C, 0, -4.5, 1.8, 0, 0));
        }));
        s.add(new Stop("v12_butterfly_bombs", (p, l) -> {
            reset(l);
            List<net.minecraft.world.entity.monster.Husk> zs = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                var z = net.minecraft.world.entity.EntityType.HUSK.create(l);
                z.moveTo(C.getX() - 3 + i * 3 + 0.5, C.getY() + 1, C.getZ() + 5.5, 180, 0);
                z.setNoAi(true);
                l.addFreshEntity(z);
                zs.add(z);
            }
            go(p, C.getX() + 0.5, C.getY() + 1, C.getZ() - 2.5, 0, 0);
            Fusion.BUTTERFLY_BOMBS.cast(p);
            later(SHOT_AT - 60, () -> look(p, C, 0, -4, 2.0, 0, 8));
            later(STOP_LENGTH - 5, () -> LOGGER.info("TOUR butterfly bombs: zombie health {}",
                    zs.stream().map(z -> z.isAlive() ? String.format("%.1f", z.getHealth()) : "dead").toList()));
        }));
        s.add(new Stop("v12_golden_zoltraak", (p, l) -> {
            reset(l);
            var z = net.minecraft.world.entity.EntityType.HUSK.create(l);
            z.moveTo(C.getX() + 2.5, C.getY() + 1, C.getZ() + 5.5, 180, 0);
            z.setNoAi(true);
            l.addFreshEntity(z);
            go(p, C.getX() - 1.5, C.getY() + 1, C.getZ() + 0.5, 0, 0);       // at the back wall
            Fusion.GOLDEN_ZOLTRAAK.cast(p);
            go(p, C.getX() + 2.5, C.getY() + 1, C.getZ() + 0.5, 0, 12);      // at the zombie
            Fusion.GOLDEN_ZOLTRAAK.cast(p);
            later(SHOT_AT - 70, () -> {
                LOGGER.info("TOUR golden zoltraak: gold blocks={} zombie={} slowed={}", count(l, Blocks.GOLD_BLOCK),
                        z.isAlive() ? z.getHealth() : "dead", z.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN));
                look(p, C, 0, -3, 2.0, 0, 10);
            });
        }));
        s.add(new Stop("v12_homing_blast", (p, l) -> {
            reset(l);
            var z = net.minecraft.world.entity.EntityType.HUSK.create(l);
            z.moveTo(C.getX() + 5.5, C.getY() + 1, C.getZ() + 4.5, 180, 0);
            z.setNoAi(true);
            l.addFreshEntity(z);
            go(p, C.getX() + 0.5, C.getY() + 1, C.getZ() - 4.5, -20, 0);    // aimed well off to the left
            Fusion.HOMING_BLAST.cast(p);
            later(40, () -> LOGGER.info("TOUR homing blast: zombie {}", z.isAlive() ? z.getHealth() : "dead"));
        }));
        s.add(new Stop("v12_pet_bow_side", (p, l) -> {
            reset(l);
            FrierenEntity f = frieren(l, C.offset(0, 1, 0), p);
            f.setStaying(true);
            f.setBowing(true);
            f.setNoAi(true);
            f.setYRot(0); f.setYBodyRot(0); f.setYHeadRot(0);
            look(p, C, 2.6, 0, 1.0, 90, 5);
        }));
        s.add(new Stop("v12_pet_first_person", (p, l) -> {
            reset(l);
            FrierenEntity f = frieren(l, C.offset(2, 1, 2), p);
            f.setPartner(true);
            p.getInventory().setItem(p.getInventory().selected, net.minecraft.world.item.ItemStack.EMPTY);
            p.setGameMode(GameType.SURVIVAL);
            go(p, C.getX() + 0.5, C.getY() + 1, C.getZ() - 1.5, 0, 25);
            later(SHOT_AT - 60, () -> f.requestPet(p));
            for (int t = SHOT_AT - 50; t <= SHOT_AT; t += 25) {
                int at = t;
                later(t, () -> LOGGER.info("TOUR pet +{}: bowing={} dist={}", at, f.isBowing(), String.format("%.2f", f.distanceTo(p))));
            }
        }));
        s.add(new Stop("v12_pet_third_person_back", (p, l) -> {
            reset(l);
            FrierenEntity f = frieren(l, C.offset(2, 1, 2), p);
            f.setPartner(true);
            p.setGameMode(GameType.SURVIVAL);
            go(p, C.getX() + 0.5, C.getY() + 1, C.getZ() - 1.5, 0, 15);
            later(SHOT_AT - 60, () -> f.requestPet(p));
        }));
    }

    private static void floor(ServerLevel l, net.minecraft.world.level.block.Block block) {
        for (BlockPos pos : BlockPos.betweenClosed(C.offset(-8, 0, -8), C.offset(8, 0, 7))) l.setBlock(pos, block.defaultBlockState(), 2);
    }

    private static int count(ServerLevel l, net.minecraft.world.level.block.Block block) {
        int n = 0;
        for (BlockPos pos : BlockPos.betweenClosed(C.offset(-12, -1, -12), C.offset(12, 4, 12))) if (l.getBlockState(pos).is(block)) n++;
        return n;
    }

    private static int countFlowers(ServerLevel l) {
        int n = 0;
        for (BlockPos pos : BlockPos.betweenClosed(C.offset(-12, -1, -12), C.offset(12, 4, 12))) {
            if (l.getBlockState(pos).is(net.minecraft.tags.BlockTags.FLOWERS)) n++;
        }
        return n;
    }

    // ----- helpers -----
    private static void later(int delay, Runnable task) {
        LATER.add(Map.entry(ticks + delay, task));
    }

    private static void go(ServerPlayer p, double x, double y, double z, float yaw, float pitch) {
        p.connection.teleport(x, y, z, yaw, pitch);
    }

    /** Puts the eyes {@code dy} above the top centre of block {@code at}, shifted by dx/dz. */
    private static void look(ServerPlayer p, BlockPos at, double dx, double dz, double dy, float yaw, float pitch) {
        go(p, at.getX() + 0.5D + dx, at.getY() + 1.0D + dy - 1.62D, at.getZ() + 0.5D + dz, yaw, pitch);
    }

    private static void reset(ServerLevel l) {
        l.getChunk(C);
        AABB box = new AABB(C).inflate(24);
        for (Entity e : l.getEntitiesOfClass(Entity.class, box, e -> !(e instanceof ServerPlayer))) e.discard();
        // unrolled loot chests would spill their loot when the scene is cleared
        for (BlockPos pos : BlockPos.betweenClosed(C.offset(-8, 0, -8), C.offset(8, 8, 8))) {
            if (l.getBlockEntity(pos) instanceof ChestBlockEntity chest) chest.setLootTable(null);
        }
        for (BlockPos pos : BlockPos.betweenClosed(C.offset(-12, 1, -12), C.offset(12, 3, 12))) {
            if (Math.abs(pos.getX() - C.getX()) > 8 || Math.abs(pos.getZ() - C.getZ()) > 8) l.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
        }
        for (BlockPos pos : BlockPos.betweenClosed(C.offset(-8, 0, -8), C.offset(8, 8, 8))) {
            int dx = pos.getX() - C.getX(), dz = pos.getZ() - C.getZ(), dy = pos.getY() - C.getY();
            BlockState state = Blocks.AIR.defaultBlockState();
            if (dy == 0) {
                state = ((dx * 7 + dz * 13) & 3) == 0 ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState();
            } else if (dz == 8 && dy <= 4) {
                state = ((dx + dy) & 3) == 0 ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState();
            }
            l.setBlock(pos, state, 2);
        }
        l.setBlock(C.offset(-3, 1, 7), Blocks.SPAWNER.defaultBlockState(), 2);
        l.setBlock(C.offset(4, 1, 7), Blocks.TORCH.defaultBlockState(), 2);
        l.setBlock(C.offset(-6, 1, 7), Blocks.TORCH.defaultBlockState(), 2);
    }

    /** A chest standing on the floor block {@code floor}, front to the north. */
    private static void chest(ServerLevel l, BlockPos floor, boolean loot) {
        BlockPos pos = floor.above();
        l.setBlock(pos, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, net.minecraft.core.Direction.NORTH), 3);
        if (loot && l.getBlockEntity(pos) instanceof ChestBlockEntity chest) chest.setLootTable(DUNGEON_LOOT, 42L);
    }

    private static MimicEntity hiddenMimic(ServerLevel l, BlockPos floor) {
        MimicEntity m = ModEntities.MIMIC.get().create(l);
        m.moveTo(floor.getX() + 0.5D, floor.getY() + 1.0D, floor.getZ() + 0.5D, 180.0F, 0.0F);
        m.setupHidden(DUNGEON_LOOT, 42L, 180.0F);
        l.addFreshEntity(m);
        return m;
    }

    private static FrierenEntity frieren(ServerLevel l, BlockPos at, ServerPlayer owner) {
        FrierenEntity f = ModEntities.FRIEREN.get().create(l);
        f.moveTo(at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D, 0.0F, 0.0F);
        f.setAffection(40);
        f.setOwnerUUID(owner.getUUID());
        l.addFreshEntity(f);
        return f;
    }

    private static List<MimicEntity> mimics(ServerLevel l) {
        return l.getEntitiesOfClass(MimicEntity.class, new AABB(C).inflate(24));
    }

    private static List<FrierenEntity> frierens(ServerLevel l) {
        return l.getEntitiesOfClass(FrierenEntity.class, new AABB(C).inflate(24));
    }

    private FrierenTour() {}
}
