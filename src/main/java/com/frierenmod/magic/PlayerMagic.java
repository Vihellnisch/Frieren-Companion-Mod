package com.frierenmod.magic;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Central helper for the player's persistent magic state. Everything is stored inside a
 * {@code frierenmod} sub-compound of the player's PERSISTED_NBT_TAG so it survives death.
 */
public final class PlayerMagic {

    private static final String ROOT = "frierenmod";

    // ----- Learned-spell bitmask flags -----
    public static final int SPELL_MANA_SENSING = 1;
    public static final int SPELL_ZOLTRAAK_T1 = 2;
    public static final int SPELL_ZOLTRAAK_T2 = 4;
    public static final int SPELL_EINSAM = 8;
    public static final int SPELL_ZOLTRAAK_T3 = 16;
    public static final int SPELL_MIDAS = 32;
    // Frieren's "useless" spells
    public static final int SPELL_FLOWER_FIELD = 64;
    public static final int SPELL_RUST_REMOVAL = 128;
    public static final int SPELL_CREATE_WATER = 256;
    public static final int SPELL_CREATE_TORCH = 512;
    public static final int SPELL_GROW_GRASS = 1024;
    public static final int SPELL_GOLDEN_BUTTERFLIES = 2048;

    /** Spells that fit on the staff's wheel at once. */
    public static final int WHEEL_SLOTS = 8;

    private PlayerMagic() {}

    /** Returns the mutable frierenmod compound stored inside PERSISTED_NBT_TAG. */
    private static CompoundTag data(Player player) {
        CompoundTag persisted = player.getPersistentData()
                .getCompound(Player.PERSISTED_NBT_TAG);
        CompoundTag root = persisted.getCompound(ROOT);
        // Re-attach so mutations are stored.
        persisted.put(ROOT, root);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
        return root;
    }

    // ----- Learned spells -----
    public static int getLearnedSpells(Player player) {
        return data(player).getInt("learnedSpells");
    }

    public static boolean hasLearned(Player player, int bit) {
        return (getLearnedSpells(player) & bit) != 0;
    }

    public static void learn(Player player, int bit) {
        CompoundTag d = data(player);
        d.putInt("learnedSpells", d.getInt("learnedSpells") | bit);
    }

    // ----- Wheel loadout (ordered spell codes, see SpellEntries) -----
    /** The player's chosen wheel, or an empty array if they never arranged one. */
    public static int[] getStoredLoadout(Player player) {
        return data(player).getIntArray("loadout");
    }

    public static void setLoadout(Player player, int[] codes) {
        data(player).putIntArray("loadout", codes);
    }

    // ----- Discovered spell fusions (bitmask of Fusion ordinals) -----
    public static int getFusions(Player player) {
        return data(player).getInt("fusions");
    }

    public static void discoverFusion(Player player, int ordinal) {
        CompoundTag d = data(player);
        d.putInt("fusions", d.getInt("fusions") | (1 << ordinal));
    }

    // ----- Selected spell (a spell code, see SpellEntries) -----
    public static int getSelectedSpell(Player player) {
        return data(player).getInt("selectedSpell");
    }

    public static void setSelectedSpell(Player player, int index) {
        data(player).putInt("selectedSpell", index);
    }

    // ----- Mana proficiency -----
    public static int getProficiency(Player player) {
        return data(player).getInt("manaProficiency");
    }

    public static void setProficiency(Player player, int value) {
        data(player).putInt("manaProficiency", Math.max(0, value));
    }

    /** Increment proficiency by one and announce milestones exactly once. */
    public static void addProficiency(Player player) {
        CompoundTag d = data(player);
        int value = d.getInt("manaProficiency") + 1;
        d.putInt("manaProficiency", value);

        int announced = d.getInt("profMilestone");
        int reached = milestoneFor(value);
        if (reached > announced) {
            d.putInt("profMilestone", reached);
            announceMilestone(player, reached);
        }
    }

    private static int milestoneFor(int value) {
        if (value >= 100) return 100;
        if (value >= 50) return 50;
        if (value >= 20) return 20;
        return 0;
    }

    private static void announceMilestone(Player player, int milestone) {
        String msg = switch (milestone) {
            case 20 -> "Frieren: ...Your mana flow is steadier now. You're improving.";
            case 50 -> "Frieren: Your spells hit harder. I can feel your control growing.";
            case 100 -> "Frieren: ...You've become a real mage. Himmel would be proud.";
            default -> null;
        };
        if (msg != null) {
            player.sendSystemMessage(Component.literal(msg).withStyle(ChatFormatting.LIGHT_PURPLE));
        }
    }

    // ----- Proficiency-derived scaling -----
    /** Damage multiplier applied to player-cast spells. */
    public static float damageMultiplier(Player player) {
        int p = getProficiency(player);
        if (p >= 100) return 1.40F;
        if (p >= 50) return 1.25F;
        if (p >= 20) return 1.10F;
        return 1.0F;
    }

    /** Projectile velocity multiplier applied to player-cast beams. */
    public static float velocityMultiplier(Player player) {
        int p = getProficiency(player);
        if (p >= 100) return 1.10F;
        if (p >= 50) return 1.05F;
        return 1.0F;
    }

    /** Zoltraak T3 explosion radius, larger at max proficiency. */
    public static float t3ExplosionRadius(Player player) {
        return getProficiency(player) >= 100 ? 4.0F : 3.0F;
    }

    // ----- Mana vision toggle -----
    public static boolean isManaVision(Player player) {
        return data(player).getBoolean("manaVisionActive");
    }

    public static void setManaVision(Player player, boolean active) {
        data(player).putBoolean("manaVisionActive", active);
    }

    public static boolean toggleManaVision(Player player) {
        boolean next = !isManaVision(player);
        setManaVision(player, next);
        return next;
    }

    // ----- Per-spell cast cooldowns (each spell has its own independent timer) -----
    public static boolean isSpellOnCooldown(ServerPlayer player, String spellKey) {
        long endTick = data(player).getLong("cooldown_" + spellKey);
        return player.serverLevel().getGameTime() < endTick;
    }

    public static long getSpellCooldownTicksRemaining(ServerPlayer player, String spellKey) {
        long endTick = data(player).getLong("cooldown_" + spellKey);
        return Math.max(0L, endTick - player.serverLevel().getGameTime());
    }

    public static void setSpellCooldown(ServerPlayer player, String spellKey, int durationTicks) {
        data(player).putLong("cooldown_" + spellKey, player.serverLevel().getGameTime() + durationTicks);
    }

    // ----- Himmel blessing (permanent Luck) -----
    public static boolean hasHimmelBlessing(Player player) {
        return data(player).getBoolean("himmelBlessing");
    }

    public static void setHimmelBlessing(Player player, boolean value) {
        data(player).putBoolean("himmelBlessing", value);
    }
}
