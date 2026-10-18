package com.frierenmod.network;

import com.frierenmod.FrierenMod;
import com.frierenmod.entity.Dialogue;
import com.frierenmod.entity.FrierenEntity;
import com.frierenmod.entity.HimmelStatue;
import com.frierenmod.magic.PlayerMagic;
import com.frierenmod.registry.ModItems;
import com.frierenmod.spell.BoneMealSpell;
import com.frierenmod.spell.HasteSpell;
import com.frierenmod.spell.HealingSpell;
import com.frierenmod.spell.WaterCreationSpell;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client -> server packet fired by the interaction screen buttons.
 */
public record FrierenInteractPacket(int entityId, int action) implements CustomPacketPayload {

    // Action ids
    public static final int TALK = 0;
    public static final int TRADE = 1;
    public static final int FOLLOW = 2;
    public static final int STAY = 3;
    public static final int HEAL = 4;
    public static final int BONE_MEAL = 5;
    public static final int WATER = 6;
    public static final int HASTE = 7;
    public static final int TEACH = 8;
    public static final int LOVE = 9;
    public static final int LORE = 10;
    public static final int QUEST_ASK = 11;
    public static final int QUEST_COMPLETE = 12;
    public static final int HIMMEL = 13;
    public static final int BAG = 14;
    public static final int OPEN_SHOP = 15; // handled client-side; never reaches server
    public static final int RETRIEVE_XP = 16;
    public static final int TEACH_USELESS = 17;
    public static final int TRADE_0 = 20;
    public static final int TRADE_1 = 21;
    public static final int TRADE_2 = 22;
    public static final int TRADE_3 = 23;

    public static final CustomPacketPayload.Type<FrierenInteractPacket> TYPE =
            new CustomPacketPayload.Type<>(FrierenMod.id("frieren_interact"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FrierenInteractPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, FrierenInteractPacket::entityId,
                    ByteBufCodecs.VAR_INT, FrierenInteractPacket::action,
                    FrierenInteractPacket::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(FrierenInteractPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            if (!(player instanceof ServerPlayer serverPlayer)) {
                return;
            }
            Entity entity = serverPlayer.level().getEntity(packet.entityId());
            if (!(entity instanceof FrierenEntity frieren)) {
                return;
            }
            if (frieren.distanceToSqr(serverPlayer) > 100.0D) {
                return;
            }
            execute(packet.action(), frieren, serverPlayer);
        });
    }

    private static void execute(int action, FrierenEntity frieren, ServerPlayer player) {
        FrierenEntity.Stage stage = frieren.getStage();
        int stageOrd = stage.ordinal();

        switch (action) {
            case TALK -> {
                Dialogue.say(player, stage, false);
                frieren.triggerTalkAnimation();
            }
            case LORE -> {
                Dialogue.say(player, stage, false);
                frieren.triggerTalkAnimation();
            }
            case TRADE -> frieren.startTrading(player);
            case BAG -> frieren.openBag(player);
            case RETRIEVE_XP -> {
                int xp = frieren.getStoredXp();
                if (xp > 0) {
                    player.giveExperiencePoints(xp);
                    frieren.setStoredXp(0);
                    player.sendSystemMessage(Component.literal(
                            "Frieren: ...Here, take it. I stored " + xp + " experience for you.")
                            .withStyle(ChatFormatting.AQUA));
                } else {
                    player.sendSystemMessage(Component.literal(
                            "Frieren: ...I haven't stored any experience yet.")
                            .withStyle(ChatFormatting.GRAY));
                }
            }
            case TRADE_0 -> frieren.directTrade(player, 0);
            case TRADE_1 -> frieren.directTrade(player, 1);
            case TRADE_2 -> frieren.directTrade(player, 2);
            case TRADE_3 -> frieren.directTrade(player, 3);
            case FOLLOW -> {
                frieren.setStaying(false);
                player.sendSystemMessage(Component.literal("Frieren: I'll follow you.").withStyle(ChatFormatting.AQUA));
            }
            case STAY -> {
                frieren.setStaying(true);
                player.sendSystemMessage(Component.literal("Frieren: I'll wait here.").withStyle(ChatFormatting.AQUA));
            }
            case HEAL -> {
                if (stageOrd >= FrierenEntity.Stage.FRIEND.ordinal() && frieren.canCast("healing")) {
                    HealingSpell.INSTANCE.cast(frieren, player);
                    frieren.setCooldown("healing", HealingSpell.INSTANCE.getCooldownTicks());
                } else {
                    cooldownMessage(player);
                }
            }
            case BONE_MEAL -> {
                if (stageOrd >= FrierenEntity.Stage.FRIEND.ordinal() && frieren.canCast("bone_meal")) {
                    int n = BoneMealSpell.INSTANCE.cast(frieren, player);
                    frieren.setCooldown("bone_meal", BoneMealSpell.INSTANCE.getCooldownTicks());
                    player.sendSystemMessage(Component.literal("Frieren: The plants will grow now. (" + n + ")").withStyle(ChatFormatting.GREEN));
                } else {
                    cooldownMessage(player);
                }
            }
            case WATER -> {
                if (stageOrd >= FrierenEntity.Stage.ALLY.ordinal() && frieren.canCast("water_creation")) {
                    boolean ok = WaterCreationSpell.INSTANCE.cast(frieren);
                    frieren.setCooldown("water_creation", WaterCreationSpell.INSTANCE.getCooldownTicks());
                    player.sendSystemMessage(Component.literal(ok ? "Frieren: Water, as you asked." : "Frieren: There's no room here.").withStyle(ChatFormatting.AQUA));
                } else {
                    cooldownMessage(player);
                }
            }
            case HASTE -> {
                if (stageOrd >= FrierenEntity.Stage.ALLY.ordinal() && frieren.canCast("haste")) {
                    HasteSpell.INSTANCE.cast(frieren, player);
                    frieren.setCooldown("haste", HasteSpell.INSTANCE.getCooldownTicks());
                } else {
                    cooldownMessage(player);
                }
            }
            case TEACH -> teachNextSpell(frieren, player, stageOrd);
            case TEACH_USELESS -> teachUselessSpell(frieren, player, stageOrd);
            case QUEST_ASK -> askGrimoireQuest(frieren, player, stageOrd);
            case QUEST_COMPLETE -> completeGrimoireQuest(frieren, player, stageOrd);
            case HIMMEL -> himmelQuest(frieren, player, stage);
            case LOVE -> {
                if (stage == FrierenEntity.Stage.PARTNER) {
                    Dialogue.say(player, stage, true);
                    frieren.triggerTalkAnimation();
                    if (frieren.level() instanceof ServerLevel sl) {
                        sl.sendParticles(ParticleTypes.HEART, frieren.getX(), frieren.getY() + 2.0D, frieren.getZ(), 15, 0.5D, 0.5D, 0.5D, 0.05D);
                    }
                }
            }
            default -> {}
        }
    }

    /**
     * A single teachable lesson: bitmask flag, minimum stage, and dialogue.
     */
    private record Lesson(int bit, int minStageOrdinal, String dialogue) {}

    private static final java.util.List<Lesson> LESSONS = java.util.List.of(
            new Lesson(FrierenEntity.SPELL_MANA_SENSING, FrierenEntity.Stage.FRIEND.ordinal(),
                    "Frieren: Magic leaves traces... learn to sense them first."),
            new Lesson(FrierenEntity.SPELL_ZOLTRAAK_T1, FrierenEntity.Stage.FRIEND.ordinal(),
                    "Frieren: This is the foundation. Don't misuse it."),
            new Lesson(FrierenEntity.SPELL_ZOLTRAAK_T2, FrierenEntity.Stage.ALLY.ordinal(),
                    "Frieren: Your control has improved. Try this."),
            new Lesson(FrierenEntity.SPELL_EINSAM, FrierenEntity.Stage.ALLY.ordinal(),
                    "Frieren: I learned this from an old grimoire. Use it wisely."),
            new Lesson(FrierenEntity.SPELL_ZOLTRAAK_T3, FrierenEntity.Stage.PARTNER.ordinal(),
                    "Frieren: ...Don't make me regret showing you this."),
            new Lesson(FrierenEntity.SPELL_MIDAS, FrierenEntity.Stage.PARTNER.ordinal(),
                    "Frieren: This gold-transmutation spell is troublesome. Use it only when necessary.")
    );

    /** Frieren's "useless" spells: she's happiest teaching these. */
    private static final java.util.List<Lesson> USELESS_LESSONS = java.util.List.of(
            new Lesson(PlayerMagic.SPELL_CREATE_WATER, FrierenEntity.Stage.FRIEND.ordinal(),
                    "Frieren: Water out of thin air. Saves carrying a waterskin."),
            new Lesson(PlayerMagic.SPELL_CREATE_TORCH, FrierenEntity.Stage.FRIEND.ordinal(),
                    "Frieren: A torch, wherever you like. I found it in a ruin, under three hundred years of dust."),
            new Lesson(PlayerMagic.SPELL_GROW_GRASS, FrierenEntity.Stage.FRIEND.ordinal(),
                    "Frieren: It turns bare dirt green. A farmer paid me in bread for it."),
            new Lesson(PlayerMagic.SPELL_RUST_REMOVAL, FrierenEntity.Stage.ALLY.ordinal(),
                    "Frieren: A spell to clean bronze statues. I used it on Himmel's, once. ...Here it works on copper."),
            new Lesson(PlayerMagic.SPELL_FLOWER_FIELD, FrierenEntity.Stage.ALLY.ordinal(),
                    "Frieren: Flamme's favourite spell. It makes a field of flowers, and nothing else. It's my favourite too."),
            new Lesson(PlayerMagic.SPELL_GOLDEN_BUTTERFLIES, FrierenEntity.Stage.PARTNER.ordinal(),
                    "Frieren: ...Golden butterflies. Completely useless. I wanted you to have it.")
    );

    private static void teachUselessSpell(FrierenEntity frieren, ServerPlayer player, int stageOrd) {
        if (stageOrd < FrierenEntity.Stage.FRIEND.ordinal()) {
            player.sendSystemMessage(Component.literal("Frieren: ...We're not close enough for that yet.")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }
        for (Lesson lesson : USELESS_LESSONS) {
            if (stageOrd < lesson.minStageOrdinal() || PlayerMagic.hasLearned(player, lesson.bit())) continue;
            PlayerMagic.learn(player, lesson.bit());
            FrierenSyncPacket.sync(player);
            player.sendSystemMessage(Component.literal(lesson.dialogue()).withStyle(ChatFormatting.GREEN));
            player.sendSystemMessage(Component.literal(
                    "(New spell. Press H for your spellbook to put it on the staff's wheel.)")
                    .withStyle(ChatFormatting.DARK_GRAY));
            frieren.triggerCastAnimation();
            if (frieren.level() instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.CHERRY_LEAVES, frieren.getX(), frieren.getY() + 1.5D, frieren.getZ(),
                        20, 0.6D, 0.5D, 0.6D, 0.02D);
            }
            return;
        }
        player.sendSystemMessage(Component.literal(stageOrd < FrierenEntity.Stage.PARTNER.ordinal()
                ? "Frieren: That's all of them I'll share for now. The best one... later."
                : "Frieren: You know every useless spell I do now. Hm. I'll have to go find more.")
                .withStyle(ChatFormatting.GRAY));
    }

    private static void teachNextSpell(FrierenEntity frieren, ServerPlayer player, int stageOrd) {
        for (Lesson lesson : LESSONS) {
            if (stageOrd < lesson.minStageOrdinal()) {
                continue;
            }
            if (frieren.hasTaught(lesson.bit()) && PlayerMagic.hasLearned(player, lesson.bit())) {
                continue;
            }
            // Teach this lesson: record it on both Frieren and the player's persistent magic.
            frieren.markTaught(lesson.bit());
            PlayerMagic.learn(player, lesson.bit());
            FrierenSyncPacket.sync(player);
            player.sendSystemMessage(Component.literal(lesson.dialogue()).withStyle(ChatFormatting.GREEN));
            player.sendSystemMessage(Component.literal(
                    "Frieren: *shows you the spell* Don't cast it recklessly.").withStyle(ChatFormatting.LIGHT_PURPLE));
            player.sendSystemMessage(Component.literal(
                    "(Use the Frieren Staff: right-click to cast, sneak+right-click to switch spells.)")
                    .withStyle(ChatFormatting.DARK_GRAY));
            frieren.triggerTalkAnimation();
            return;
        }
        player.sendSystemMessage(Component.literal(
                "Frieren: ...I've taught you everything I can for now. Grow closer, and I'll show you more.")
                .withStyle(ChatFormatting.GRAY));
    }

    // ----- Grimoire hunt quest -----
    private static final Item[] QUEST_GRIMOIRES = {
            ModItems.GRIMOIRE_BASIC_MAGIC.get(),
            ModItems.GRIMOIRE_SPIRIT_LORE.get(),
            ModItems.GRIMOIRE_ANCIENT_SPELLS.get()
    };

    private static void askGrimoireQuest(FrierenEntity frieren, ServerPlayer player, int stageOrd) {
        if (stageOrd < FrierenEntity.Stage.FRIEND.ordinal()) {
            player.sendSystemMessage(Component.literal("Frieren: ...We're not close enough for that yet.")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }
        if (frieren.isQuestActive()) {
            sendQuestHint(frieren, player);
            return;
        }
        if (frieren.getQuestCooldown() > 0) {
            player.sendSystemMessage(Component.literal("Frieren: ...Give me time. I'll think of another lead soon.")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }
        // Generate a random target 200-500 blocks away on the surface.
        ServerLevel level = (ServerLevel) frieren.level();
        java.util.Random rng = new java.util.Random();
        int signX = rng.nextBoolean() ? 1 : -1;
        int signZ = rng.nextBoolean() ? 1 : -1;
        int dx = signX * (200 + rng.nextInt(301));
        int dz = signZ * (200 + rng.nextInt(301));
        int tx = (int) player.getX() + dx;
        int tz = (int) player.getZ() + dz;
        int ty = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, tx, tz);
        int type = rng.nextInt(3);
        frieren.startGrimoireQuest(new BlockPos(tx, ty, tz), type);
        frieren.triggerTalkAnimation();
        player.sendSystemMessage(Component.literal(
                "Frieren: I sense a grimoire's mana far away. Let me point you toward it.")
                .withStyle(ChatFormatting.AQUA));
        sendQuestHint(frieren, player);
    }

    private static void sendQuestHint(FrierenEntity frieren, ServerPlayer player) {
        BlockPos target = frieren.getQuestTarget();
        double dx = target.getX() - player.getX();
        double dz = target.getZ() - player.getZ();
        String dir = compass(dx, dz);
        int dist = (int) Math.sqrt(dx * dx + dz * dz);
        player.sendSystemMessage(Component.literal(
                "Frieren: Head " + dir + ", about " + dist + " blocks. The mana grows stronger there.")
                .withStyle(ChatFormatting.AQUA));
    }

    private static String compass(double dx, double dz) {
        // In Minecraft: -Z is North, +Z South, +X East, -X West.
        StringBuilder sb = new StringBuilder();
        if (dz < -20) sb.append("North");
        else if (dz > 20) sb.append("South");
        if (dx > 20) sb.append("East");
        else if (dx < -20) sb.append("West");
        return sb.length() == 0 ? "nearby" : sb.toString();
    }

    private static void completeGrimoireQuest(FrierenEntity frieren, ServerPlayer player, int stageOrd) {
        if (stageOrd < FrierenEntity.Stage.FRIEND.ordinal() || !frieren.isQuestActive()) {
            player.sendSystemMessage(Component.literal("Frieren: ...There's no hunt right now.")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }
        // Look for any grimoire in the inventory.
        for (Item grimoire : QUEST_GRIMOIRES) {
            int slot = findItem(player, grimoire);
            if (slot >= 0) {
                player.getInventory().removeItem(slot, 1);
                frieren.addAffection(15);
                frieren.completeGrimoireQuest();
                frieren.triggerTalkAnimation();
                player.sendSystemMessage(Component.literal(
                        "Frieren: ...You actually found it. Impressive.").withStyle(ChatFormatting.LIGHT_PURPLE));
                if (frieren.level() instanceof ServerLevel sl) {
                    sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, frieren.getX(), frieren.getY() + 2.0D,
                            frieren.getZ(), 12, 0.5D, 0.5D, 0.5D, 0.03D);
                }
                return;
            }
        }
        player.sendSystemMessage(Component.literal(
                "Frieren: ...You don't have a grimoire to show me. Keep looking.").withStyle(ChatFormatting.GRAY));
    }

    private static int findItem(ServerPlayer player, Item item) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).is(item)) {
                return i;
            }
        }
        return -1;
    }

    // ----- Himmel's statue quest -----
    private static void himmelQuest(FrierenEntity frieren, ServerPlayer player, FrierenEntity.Stage stage) {
        if (stage != FrierenEntity.Stage.PARTNER) {
            player.sendSystemMessage(Component.literal("Frieren: ...That's a memory I only share with someone dear.")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }
        int st = frieren.getHimmelQuestStage();
        frieren.triggerTalkAnimation();
        switch (st) {
            case 0 -> {
                player.sendSystemMessage(Component.literal(
                        "Frieren: Himmel the Hero... I'd like to build a statue for him. Will you help me?")
                        .withStyle(ChatFormatting.LIGHT_PURPLE));
                player.sendSystemMessage(Component.literal(
                        "(Bring: 4 Smooth Stone, 2 Stone Bricks, 1 Iron Ingot, 1 Gold Block, then ask again.)")
                        .withStyle(ChatFormatting.DARK_GRAY));
                frieren.setHimmelQuestStage(1);
            }
            case 1 -> {
                if (hasStatueMaterials(player)) {
                    consumeStatueMaterials(player);
                    frieren.setHimmelQuestStage(2);
                    player.sendSystemMessage(Component.literal(
                            "Frieren: ...You gathered everything. Thank you. Ask me once more and I'll build it.")
                            .withStyle(ChatFormatting.LIGHT_PURPLE));
                } else {
                    player.sendSystemMessage(Component.literal(
                            "Frieren: I still need 4 Smooth Stone, 2 Stone Bricks, 1 Iron Ingot and 1 Gold Block.")
                            .withStyle(ChatFormatting.GRAY));
                }
            }
            case 2 -> {
                if (frieren.level() instanceof ServerLevel sl) {
                    BlockPos base = HimmelStatue.build(sl, frieren);
                    sl.sendParticles(ParticleTypes.HEART, base.getX() + 0.5D, base.getY() + 3.0D, base.getZ() + 0.5D,
                            30, 0.6D, 1.0D, 0.6D, 0.05D);
                }
                PlayerMagic.setHimmelBlessing(player, true);
                player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        net.minecraft.world.effect.MobEffects.LUCK, 999999, 0, false, false));
                player.sendSystemMessage(Component.literal(
                        "Frieren: ...There. He'd laugh at how plain it is. Thank you for staying with me.")
                        .withStyle(ChatFormatting.LIGHT_PURPLE));
                player.sendSystemMessage(Component.literal(
                        "(Himmel's Blessing: permanent Luck.)").withStyle(ChatFormatting.DARK_GRAY));
                frieren.setHimmelQuestStage(4);
            }
            default -> player.sendSystemMessage(Component.literal(
                    "Frieren: ...The statue still stands. Good.").withStyle(ChatFormatting.LIGHT_PURPLE));
        }
    }

    private static boolean hasStatueMaterials(ServerPlayer player) {
        return countItem(player, Items.SMOOTH_STONE) >= 4
                && countItem(player, Items.STONE_BRICKS) >= 2
                && countItem(player, Items.IRON_INGOT) >= 1
                && countItem(player, Items.GOLD_BLOCK) >= 1;
    }

    private static void consumeStatueMaterials(ServerPlayer player) {
        removeItems(player, Items.SMOOTH_STONE, 4);
        removeItems(player, Items.STONE_BRICKS, 2);
        removeItems(player, Items.IRON_INGOT, 1);
        removeItems(player, Items.GOLD_BLOCK, 1);
    }

    private static int countItem(ServerPlayer player, Item item) {
        int count = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (s.is(item)) count += s.getCount();
        }
        return count;
    }

    private static void removeItems(ServerPlayer player, Item item, int amount) {
        int remaining = amount;
        for (int i = 0; i < player.getInventory().getContainerSize() && remaining > 0; i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (s.is(item)) {
                int take = Math.min(remaining, s.getCount());
                s.shrink(take);
                remaining -= take;
            }
        }
    }

    private static void cooldownMessage(ServerPlayer player) {
        player.sendSystemMessage(Component.literal("Frieren: ...Not yet. Give me a moment.").withStyle(ChatFormatting.GRAY));
    }
}
