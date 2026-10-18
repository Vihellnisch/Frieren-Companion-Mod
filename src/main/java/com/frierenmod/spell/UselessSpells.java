package com.frierenmod.spell;

import com.frierenmod.FrierenConfig;
import com.frierenmod.entity.GoldenButterflyEntity;
import com.frierenmod.registry.ModEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.HoneycombItem;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.WeatheringCopper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Frieren's "useless" spells: harmless little magics she collects for the joy of it.
 * All of these run on the server and return true if something happened.
 */
public final class UselessSpells {
    private static final double REACH = 6.0D;

    private UselessSpells() {}

    // ----- Flower Field / Grow Grass / Spring Bloom -----
    public static boolean flowerField(Player player, int radius) {
        boolean ok = bloom(player, radius, 0.55F, false);
        if (!ok) fail(player, "There's nowhere for flowers to grow here.");
        return ok;
    }

    public static boolean growGrass(Player player, int radius) {
        boolean ok = bloom(player, radius, 0.0F, true);
        if (!ok) fail(player, "There's no bare dirt nearby.");
        return ok;
    }

    public static boolean springBloom(Player player, int radius) {
        boolean ok = bloom(player, radius, 0.6F, true);
        if (!ok) fail(player, "Nothing here can bloom.");
        return ok;
    }

    /**
     * Spreads outward from the player in rings: bare dirt turns to grass, then flowers pop up on
     * anything they can grow on. {@code flowerChance} 0 means grass only.
     */
    private static boolean bloom(Player player, int radius, float flowerChance, boolean grass) {
        if (!(player.level() instanceof ServerLevel level)) return false;
        RandomSource random = level.getRandom();
        List<BlockState> small = flowers(BlockTags.SMALL_FLOWERS);
        List<BlockState> tall = flowers(BlockTags.TALL_FLOWERS);
        if (flowerChance > 0 && small.isEmpty() && tall.isEmpty()) flowerChance = 0;

        BlockPos center = player.blockPosition();
        int planned = 0;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist > radius + 0.5D) continue;
                BlockPos ground = surface(level, center.offset(dx, 0, dz));
                if (ground == null) continue;
                int delay = 2 + (int) (dist * 2.5D); // the ring spreads outward
                boolean dirt = grass && isBareDirt(level.getBlockState(ground));
                boolean flower = flowerChance > 0 && random.nextFloat() < flowerChance;
                if (!dirt && !flower) continue;
                BlockState pick = null;
                if (flower) {
                    boolean useTall = !tall.isEmpty() && (small.isEmpty() || random.nextFloat() < 0.12F);
                    List<BlockState> from = useTall ? tall : small;
                    pick = from.get(random.nextInt(from.size()));
                }
                BlockState flowerState = pick;
                MagicTasks.schedule(level, delay, () -> {
                    if (dirt && isBareDirt(level.getBlockState(ground))) {
                        level.setBlock(ground, Blocks.GRASS_BLOCK.defaultBlockState(), 3);
                        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, ground.getX() + 0.5D, ground.getY() + 1.05D,
                                ground.getZ() + 0.5D, 2, 0.3D, 0.05D, 0.3D, 0.0D);
                    }
                    if (flowerState != null) placeFlower(level, ground.above(), flowerState);
                });
                planned++;
            }
        }
        if (planned == 0) return false;
        level.playSound(null, center, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.2F, 1.3F);
        level.playSound(null, center, SoundEvents.CHERRY_LEAVES_PLACE, SoundSource.PLAYERS, 1.0F, 1.0F);
        for (int ring = 1; ring <= radius; ring += 2) {
            int r = ring;
            MagicTasks.schedule(level, 2 + (int) (r * 2.5D), () ->
                    level.playSound(null, center, SoundEvents.FLOWERING_AZALEA_PLACE, SoundSource.PLAYERS, 0.6F, 1.0F + r * 0.05F));
        }
        return true;
    }

    private static void placeFlower(ServerLevel level, BlockPos pos, BlockState flower) {
        if (!level.getBlockState(pos).isAir() || !flower.canSurvive(level, pos)) return;
        if (flower.getBlock() instanceof DoublePlantBlock) {
            if (!level.getBlockState(pos.above()).isAir()) return;
            DoublePlantBlock.placeAt(level, flower, pos, 3);
        } else {
            level.setBlock(pos, flower, 3);
        }
        level.sendParticles(ParticleTypes.CHERRY_LEAVES, pos.getX() + 0.5D, pos.getY() + 0.6D, pos.getZ() + 0.5D,
                2, 0.25D, 0.2D, 0.25D, 0.0D);
    }

    /** Every flower in the tag (modded ones too), minus the config blacklist. */
    private static List<BlockState> flowers(TagKey<Block> tag) {
        List<? extends String> blacklist = FrierenConfig.FLOWER_BLACKLIST.get();
        List<BlockState> out = new ArrayList<>();
        for (Holder<Block> holder : BuiltInRegistries.BLOCK.getTagOrEmpty(tag)) {
            if (!isBlacklisted(holder, blacklist)) out.add(holder.value().defaultBlockState());
        }
        return out;
    }

    private static boolean isBlacklisted(Holder<Block> holder, List<? extends String> blacklist) {
        for (String entry : blacklist) {
            if (entry.startsWith("#")) {
                ResourceLocation id = ResourceLocation.tryParse(entry.substring(1));
                if (id != null && holder.is(TagKey.create(Registries.BLOCK, id))) return true;
            } else {
                ResourceLocation id = ResourceLocation.tryParse(entry);
                if (id != null && holder.is(id)) return true;
            }
        }
        return false;
    }

    /** The top solid-ish block of this column near the caster's height, with air above it. */
    @Nullable
    private static BlockPos surface(ServerLevel level, BlockPos column) {
        for (int dy = 3; dy >= -4; dy--) {
            BlockPos pos = column.above(dy);
            if (!level.getBlockState(pos).isAir() && level.getBlockState(pos.above()).isAir()) return pos;
        }
        return null;
    }

    private static boolean isBareDirt(BlockState state) {
        return state.is(Blocks.DIRT) || state.is(Blocks.COARSE_DIRT) || state.is(Blocks.ROOTED_DIRT);
    }

    // ----- Rust Removal -----
    /** Copper has no rust, so this scrubs one stage of oxidation off the copper you look at (and what's joined to it). */
    public static boolean rustRemoval(Player player) {
        if (!(player.level() instanceof ServerLevel level)) return false;
        BlockHitResult hit = lookAt(player);
        if (hit == null || !isCopper(level.getBlockState(hit.getBlockPos()))) {
            fail(player, "Nothing rusty there.");
            return false;
        }
        BlockPos start = hit.getBlockPos();
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        seen.add(start);
        int cleaned = 0;
        while (!queue.isEmpty() && seen.size() <= 96) {
            BlockPos pos = queue.poll();
            BlockState state = level.getBlockState(pos);
            Optional<BlockState> cleaner = lessOxidized(state);
            if (cleaner.isPresent()) {
                level.setBlock(pos, cleaner.get(), 3);
                level.levelEvent(LevelEvent.PARTICLES_SCRAPE, pos, 0);
                cleaned++;
            }
            for (Direction d : Direction.values()) {
                BlockPos next = pos.relative(d);
                if (next.distSqr(start) <= 64 && !seen.contains(next) && isCopper(level.getBlockState(next))) {
                    seen.add(next.immutable());
                    queue.add(next.immutable());
                }
            }
        }
        if (cleaned == 0) {
            fail(player, "It's already spotless.");
            return false;
        }
        level.playSound(null, start, SoundEvents.AXE_SCRAPE, SoundSource.PLAYERS, 1.0F, 1.0F);
        level.playSound(null, start, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8F, 1.6F);
        return true;
    }

    private static boolean isCopper(BlockState state) {
        return state.getBlock() instanceof WeatheringCopper || HoneycombItem.WAX_OFF_BY_BLOCK.get().containsKey(state.getBlock());
    }

    /** One oxidation stage back; waxed copper stays waxed. */
    private static Optional<BlockState> lessOxidized(BlockState state) {
        Block waxedFrom = HoneycombItem.WAX_OFF_BY_BLOCK.get().get(state.getBlock());
        if (waxedFrom == null) return WeatheringCopper.getPrevious(state);
        return WeatheringCopper.getPrevious(waxedFrom.withPropertiesOf(state)).map(prev -> {
            Block waxed = HoneycombItem.WAXABLES.get().get(prev.getBlock());
            return waxed == null ? prev : waxed.withPropertiesOf(prev);
        });
    }

    // ----- Create Water -----
    public static boolean createWater(Player player) {
        if (!(player.level() instanceof ServerLevel level)) return false;
        BlockHitResult hit = lookAt(player);
        if (hit == null) {
            fail(player, "Look at where the water should go.");
            return false;
        }
        BlockPos pos = hit.getBlockPos();
        BlockState state = level.getBlockState(pos);
        if (state.hasProperty(BlockStateProperties.WATERLOGGED) && !state.getValue(BlockStateProperties.WATERLOGGED)
                && !level.dimensionType().ultraWarm()) {
            level.setBlock(pos, state.setValue(BlockStateProperties.WATERLOGGED, true), 3);
            level.scheduleTick(pos, net.minecraft.world.level.material.Fluids.WATER, 5);
        } else {
            pos = pos.relative(hit.getDirection());
            if (!level.getBlockState(pos).canBeReplaced()) {
                fail(player, "There's no room for water there.");
                return false;
            }
            if (level.dimensionType().ultraWarm()) {
                // Like a bucket in the Nether: it just hisses away.
                level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.6F, 1.8F);
                level.sendParticles(ParticleTypes.LARGE_SMOKE, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
                        8, 0.3D, 0.3D, 0.3D, 0.0D);
                return true;
            }
            level.setBlock(pos, Blocks.WATER.defaultBlockState(), 3);
        }
        level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.PLAYERS, 1.0F, 1.0F);
        level.sendParticles(ParticleTypes.SPLASH, pos.getX() + 0.5D, pos.getY() + 0.8D, pos.getZ() + 0.5D,
                20, 0.3D, 0.2D, 0.3D, 0.0D);
        return true;
    }

    // ----- Create Torch -----
    public static boolean createTorch(Player player) {
        if (!(player.level() instanceof ServerLevel level)) return false;
        BlockHitResult hit = lookAt(player);
        if (hit == null || hit.getDirection() == Direction.DOWN) {
            fail(player, "Look at a floor or a wall.");
            return false;
        }
        BlockPos pos = hit.getBlockPos().relative(hit.getDirection());
        BlockState torch = hit.getDirection() == Direction.UP ? Blocks.TORCH.defaultBlockState()
                : Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, hit.getDirection());
        if (!level.getBlockState(pos).canBeReplaced() || !torch.canSurvive(level, pos)) {
            fail(player, "A torch won't hold there.");
            return false;
        }
        level.setBlock(pos, torch, 3);
        level.playSound(null, pos, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.5F, 1.6F);
        level.sendParticles(ParticleTypes.FLAME, pos.getX() + 0.5D, pos.getY() + 0.6D, pos.getZ() + 0.5D,
                6, 0.1D, 0.1D, 0.1D, 0.01D);
        return true;
    }

    // ----- Golden Butterflies -----
    public static boolean butterflies(Player player, int count, boolean exploding) {
        if (!(player.level() instanceof ServerLevel level)) return false;
        RandomSource random = level.getRandom();
        Vec3 origin = player.getEyePosition().add(player.getLookAngle().scale(0.8D));
        for (int i = 0; i < count; i++) {
            GoldenButterflyEntity b = ModEntities.GOLDEN_BUTTERFLY.get().create(level);
            if (b == null) continue;
            b.setup(player, exploding);
            b.moveTo(origin.x + (random.nextDouble() - 0.5D) * 0.6D, origin.y + (random.nextDouble() - 0.5D) * 0.4D,
                    origin.z + (random.nextDouble() - 0.5D) * 0.6D, random.nextFloat() * 360.0F, 0.0F);
            b.setDeltaMovement((random.nextDouble() - 0.5D) * 0.4D, 0.1D + random.nextDouble() * 0.2D,
                    (random.nextDouble() - 0.5D) * 0.4D);
            level.addFreshEntity(b);
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.PLAYERS, 1.0F, exploding ? 0.8F : 1.7F);
        level.sendParticles(ParticleTypes.WAX_ON, origin.x, origin.y, origin.z, 20, 0.4D, 0.4D, 0.4D, 0.05D);
        return true;
    }

    // ----- helpers -----
    @Nullable
    private static BlockHitResult lookAt(Player player) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(REACH));
        BlockHitResult hit = player.level().clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.BLOCK ? hit : null;
    }

    private static void fail(Player player, String message) {
        player.displayClientMessage(Component.literal(message).withStyle(ChatFormatting.GRAY), true);
    }
}
