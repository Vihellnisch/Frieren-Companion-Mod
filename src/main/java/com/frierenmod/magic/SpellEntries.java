package com.frierenmod.magic;

import com.frierenmod.magic.SpellCaster.StaffSpell;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A wheel entry is a single int code: a StaffSpell ordinal, or FUSION_BASE + a Fusion ordinal.
 * The same codes are stored in the player's loadout and selection and sent to the client.
 */
public final class SpellEntries {
    public static final int FUSION_BASE = 100;

    public static int code(StaffSpell spell) {
        return spell.ordinal();
    }

    public static int code(Fusion fusion) {
        return FUSION_BASE + fusion.ordinal();
    }

    @Nullable
    public static StaffSpell spell(int code) {
        StaffSpell[] all = StaffSpell.values();
        return code >= 0 && code < all.length ? all[code] : null;
    }

    @Nullable
    public static Fusion fusion(int code) {
        Fusion[] all = Fusion.values();
        int i = code - FUSION_BASE;
        return i >= 0 && i < all.length ? all[i] : null;
    }

    public static boolean isValid(int code) {
        return spell(code) != null || fusion(code) != null;
    }

    public static String displayName(int code) {
        StaffSpell s = spell(code);
        if (s != null) return s.displayName;
        Fusion f = fusion(code);
        return f != null ? f.displayName : "?";
    }

    public static int cooldownTicks(int code) {
        StaffSpell s = spell(code);
        if (s != null) return s.cooldownTicks;
        Fusion f = fusion(code);
        return f != null ? f.cooldownTicks : 0;
    }

    /** The key its cooldown is stored under (spells keep their old keys). */
    public static String cooldownKey(int code) {
        StaffSpell s = spell(code);
        if (s != null) return s.name();
        Fusion f = fusion(code);
        return f != null ? "fusion_" + f.name() : "unknown";
    }

    /** Learned spell, or discovered fusion whose two spells are both still known. */
    public static boolean isAvailable(int code, int learnedMask, int fusionMask) {
        StaffSpell s = spell(code);
        if (s != null) return s == StaffSpell.MANA_VISION || (learnedMask & s.requiredBit) != 0;
        Fusion f = fusion(code);
        return f != null && f.isDiscovered(fusionMask)
                && (learnedMask & f.a.requiredBit) != 0 && (learnedMask & f.b.requiredBit) != 0;
    }

    public static boolean isAvailable(Player player, int code) {
        return isAvailable(code, PlayerMagic.getLearnedSpells(player), PlayerMagic.getFusions(player));
    }

    /** Everything the player could put on the wheel, spells first, then fusions. */
    public static List<Integer> known(int learnedMask, int fusionMask) {
        List<Integer> out = new ArrayList<>();
        for (StaffSpell s : StaffSpell.values()) {
            if (isAvailable(code(s), learnedMask, fusionMask)) out.add(code(s));
        }
        for (Fusion f : Fusion.values()) {
            if (isAvailable(code(f), learnedMask, fusionMask)) out.add(code(f));
        }
        return out;
    }

    /** The wheel as arranged, minus anything no longer known; the first eight known spells if never arranged. */
    public static List<Integer> loadout(int[] stored, int learnedMask, int fusionMask) {
        List<Integer> out = new ArrayList<>();
        if (stored.length == 0) {
            for (int c : known(learnedMask, fusionMask)) {
                if (out.size() < PlayerMagic.WHEEL_SLOTS) out.add(c);
            }
            return out;
        }
        for (int c : stored) {
            if (isAvailable(c, learnedMask, fusionMask) && !out.contains(c) && out.size() < PlayerMagic.WHEEL_SLOTS) out.add(c);
        }
        if (out.isEmpty()) out.add(code(StaffSpell.MANA_VISION));
        return out;
    }

    public static List<Integer> loadout(Player player) {
        return loadout(PlayerMagic.getStoredLoadout(player), PlayerMagic.getLearnedSpells(player), PlayerMagic.getFusions(player));
    }

    private SpellEntries() {}
}
