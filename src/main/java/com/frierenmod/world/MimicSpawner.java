package com.frierenmod.world;

import com.frierenmod.FrierenConfig;
import com.frierenmod.FrierenMod;
import com.frierenmod.entity.MimicEntity;
import com.frierenmod.registry.ModEntities;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.storage.loot.LootTable;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Turns a few loot chests of freshly generated structures into Mimics. Chunks are queued when they
 * first load and converted on the next level tick, outside of chunk loading.
 */
@EventBusSubscriber(modid = FrierenMod.MODID)
public final class MimicSpawner {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int CHUNKS_PER_TICK = 16;
    private static final Map<ResourceKey<Level>, Queue<ChunkPos>> PENDING = new ConcurrentHashMap<>();

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!event.isNewChunk() || !(event.getLevel() instanceof ServerLevel level)) return;
        if (!(event.getChunk() instanceof LevelChunk chunk)) return;
        PENDING.computeIfAbsent(level.dimension(), k -> new ConcurrentLinkedQueue<>()).add(chunk.getPos());
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        Queue<ChunkPos> queue = PENDING.get(level.dimension());
        if (queue == null || queue.isEmpty()) return;
        double chance = FrierenConfig.MIMIC_CHANCE.get();
        for (int i = 0; i < CHUNKS_PER_TICK; i++) {
            ChunkPos pos = queue.poll();
            if (pos == null) return;
            LevelChunk chunk = level.getChunkSource().getChunkNow(pos.x, pos.z);
            if (chunk != null && chance > 0.0D) convertChunk(level, chunk, chance, level.getRandom());
        }
    }

    /** Rolls every eligible loot chest in the chunk. Returns how many became Mimics. */
    public static int convertChunk(ServerLevel level, LevelChunk chunk, double chance, RandomSource random) {
        int made = 0;
        for (BlockPos pos : new ArrayList<>(chunk.getBlockEntitiesPos())) {
            if (random.nextDouble() < chance && convert(level, pos) != null) made++;
        }
        return made;
    }

    /**
     * Replaces the single, unopened loot chest at {@code pos} with a hidden Mimic carrying its loot table.
     * Returns null if there's no such chest there.
     */
    public static MimicEntity convert(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!state.is(Blocks.CHEST) || state.getValue(ChestBlock.TYPE) != ChestType.SINGLE
                || state.getValue(BlockStateProperties.WATERLOGGED)) {
            return null;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof ChestBlockEntity chest) || be.getType() != BlockEntityType.CHEST) return null;
        ResourceKey<LootTable> loot = chest.getLootTable();
        if (loot == null) return null; // player chests and opened chests stay chests

        MimicEntity mimic = ModEntities.MIMIC.get().create(level);
        if (mimic == null) return null;
        long seed = chest.getLootTableSeed();
        float yaw = state.getValue(ChestBlock.FACING).toYRot();
        // Clear the loot table first: removing a chest spills its contents, which would roll the loot.
        chest.setLootTable(null);
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        mimic.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, yaw, 0.0F);
        mimic.setupHidden(loot, seed, yaw);
        level.addFreshEntity(mimic);
        LOGGER.debug("Mimic hides at {} in {} (loot {})", pos, level.dimension().location(), loot.location());
        return mimic;
    }

    private MimicSpawner() {}
}
