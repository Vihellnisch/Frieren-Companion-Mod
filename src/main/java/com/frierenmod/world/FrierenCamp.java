package com.frierenmod.world;

import com.frierenmod.entity.FrierenEntity;
import com.frierenmod.registry.ModEntities;
import com.frierenmod.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Random;

/**
 * A small discoverable Frieren camp (tent, campfire, bed, chest and a sleeping Frieren).
 * Generated once per world near the spawn point via a login hook, tracked with SavedData so
 * it never duplicates. This is a robust, code-based alternative to a full worldgen structure.
 */
public final class FrierenCamp {

    private static final String DATA_NAME = "frierenmod_camp";

    private FrierenCamp() {}

    /** Generate the camp once for the given (overworld) level. */
    public static void generateOnce(ServerLevel level) {
        Data data = level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(Data::new, FrierenCamp::load, null), DATA_NAME);
        if (data.generated) {
            return;
        }
        data.generated = true;
        data.setDirty();

        BlockPos spawn = level.getSharedSpawnPos();
        Random rng = new Random(level.getSeed() ^ 0x5EEDF12E);
        int dx = (rng.nextBoolean() ? 1 : -1) * (120 + rng.nextInt(120));
        int dz = (rng.nextBoolean() ? 1 : -1) * (120 + rng.nextInt(120));
        int x = spawn.getX() + dx;
        int z = spawn.getZ() + dz;
        int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
        BlockPos base = new BlockPos(x, y, z);

        buildCamp(level, base);
    }

    private static void buildCamp(ServerLevel level, BlockPos base) {
        BlockState ground = Blocks.DIRT_PATH.defaultBlockState();
        BlockState wool = Blocks.WHITE_WOOL.defaultBlockState();
        BlockState woolTrim = Blocks.LIGHT_BLUE_WOOL.defaultBlockState();

        // Flatten a 5x5 ground pad.
        for (int ox = -2; ox <= 2; ox++) {
            for (int oz = -2; oz <= 2; oz++) {
                level.setBlock(base.offset(ox, -1, oz), ground, 3);
                level.setBlock(base.offset(ox, 0, oz), Blocks.AIR.defaultBlockState(), 3);
                level.setBlock(base.offset(ox, 1, oz), Blocks.AIR.defaultBlockState(), 3);
            }
        }

        // Simple tent: back wall + sloped wool roof.
        for (int oz = -1; oz <= 1; oz++) {
            level.setBlock(base.offset(-2, 0, oz), wool, 3);
            level.setBlock(base.offset(-2, 1, oz), wool, 3);
        }
        for (int ox = -2; ox <= 1; ox++) {
            level.setBlock(base.offset(ox, 2, 0), woolTrim, 3);
            level.setBlock(base.offset(ox, 1, -1), wool, 3);
            level.setBlock(base.offset(ox, 1, 1), wool, 3);
        }

        // Campfire in front of the tent.
        BlockPos campfire = base.offset(2, 0, 0);
        level.setBlock(campfire, Blocks.CAMPFIRE.defaultBlockState(), 3);

        // Bed inside the tent (facing east).
        BlockPos bedFoot = base.offset(-1, 0, 0);
        BlockPos bedHead = bedFoot.relative(Direction.WEST);
        level.setBlock(bedFoot, Blocks.RED_BED.defaultBlockState()
                .setValue(net.minecraft.world.level.block.BedBlock.FACING, Direction.WEST)
                .setValue(net.minecraft.world.level.block.BedBlock.PART,
                        net.minecraft.world.level.block.state.properties.BedPart.FOOT), 3);
        level.setBlock(bedHead, Blocks.RED_BED.defaultBlockState()
                .setValue(net.minecraft.world.level.block.BedBlock.FACING, Direction.WEST)
                .setValue(net.minecraft.world.level.block.BedBlock.PART,
                        net.minecraft.world.level.block.state.properties.BedPart.HEAD), 3);

        // Supply chest.
        BlockPos chestPos = base.offset(1, 0, 2);
        level.setBlock(chestPos, Blocks.CHEST.defaultBlockState(), 3);
        BlockEntity be = level.getBlockEntity(chestPos);
        if (be instanceof ChestBlockEntity chest) {
            chest.setItem(0, new ItemStack(ModItems.GRIMOIRE_BASIC_MAGIC.get(), 1));
            chest.setItem(1, new ItemStack(ModItems.MANA_HERB.get(), 3));
            ItemStack note = new ItemStack(Items.PAPER);
            note.set(DataComponents.CUSTOM_NAME,
                    Component.literal("Frieren's Note: 'Off hunting grimoires. Rest here a while.'"));
            chest.setItem(2, note);
        }

        // A sleeping Frieren resting in the camp.
        FrierenEntity frieren = ModEntities.FRIEREN.get().create(level);
        if (frieren != null) {
            frieren.moveTo(bedFoot.getX() + 0.5D, bedFoot.getY(), bedFoot.getZ() + 0.5D, 90.0F, 0.0F);
            frieren.setSleeping(true);
            frieren.setStaying(true);
            level.addFreshEntity(frieren);
        }
    }

    // ----- SavedData -----
    public static final class Data extends SavedData {
        public boolean generated;

        public Data() {}

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            tag.putBoolean("generated", generated);
            return tag;
        }
    }

    private static Data load(CompoundTag tag, HolderLookup.Provider registries) {
        Data d = new Data();
        d.generated = tag.getBoolean("generated");
        return d;
    }
}
