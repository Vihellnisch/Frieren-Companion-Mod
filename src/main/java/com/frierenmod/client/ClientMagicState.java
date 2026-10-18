package com.frierenmod.client;

import java.util.ArrayList;
import java.util.List;

/**
 * Client-only holder for the player's magic state, populated from {@code FrierenSyncPacket}.
 * Used by the mana-vision overlay, staff HUD, spell wheel and spellbook.
 */
public final class ClientMagicState {
    public static int learnedSpells = 0;
    /** A spell code, see SpellEntries. */
    public static int selectedSpell = 0;
    public static int proficiency = 0;
    public static boolean manaVision = false;
    /** The wheel, in order (spell codes). */
    public static List<Integer> loadout = new ArrayList<>();
    public static int fusions = 0;

    private ClientMagicState() {}

    public static void update(int learned, int selected, int prof, boolean vision, List<Integer> wheel, int fusionMask) {
        learnedSpells = learned;
        selectedSpell = selected;
        proficiency = prof;
        manaVision = vision;
        loadout = new ArrayList<>(wheel);
        fusions = fusionMask;
    }
}
