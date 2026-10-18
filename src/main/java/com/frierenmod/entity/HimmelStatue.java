package com.frierenmod.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Builds Himmel the Hero's memorial statue a few blocks in front of Frieren.
 * Layout: 2x2 smooth stone base, two stacked stone bricks (body), a gold block (head)
 * and an iron block on top.
 */
public final class HimmelStatue {

    private HimmelStatue() {}

    public static BlockPos build(ServerLevel level, FrierenEntity frieren) {
        Direction facing = frieren.getDirection();
        BlockPos front = frieren.blockPosition().relative(facing, 3);
        int groundY = level.getHeight(Heightmap.Types.WORLD_SURFACE, front.getX(), front.getZ());
        BlockPos base = new BlockPos(front.getX(), groundY, front.getZ());

        BlockState smoothStone = Blocks.SMOOTH_STONE.defaultBlockState();
        BlockState stoneBricks = Blocks.STONE_BRICKS.defaultBlockState();
        BlockState gold = Blocks.GOLD_BLOCK.defaultBlockState();
        BlockState iron = Blocks.IRON_BLOCK.defaultBlockState();

        // 2x2 smooth stone base.
        for (int dx = 0; dx <= 1; dx++) {
            for (int dz = 0; dz <= 1; dz++) {
                level.setBlock(base.offset(dx, 0, dz), smoothStone, 3);
            }
        }
        // Body column on the first base block.
        BlockPos column = base.offset(0, 1, 0);
        level.setBlock(column, stoneBricks, 3);
        level.setBlock(column.above(), stoneBricks, 3);
        level.setBlock(column.above(2), gold, 3);   // head
        level.setBlock(column.above(3), iron, 3);   // crown/top

        return base;
    }
}
