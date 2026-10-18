package com.frierenmod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

import java.util.HashMap;
import java.util.Map;

/** Which players are patting Frieren right now, for the arm animation (first and third person). */
public final class ClientPetState {
    private record Pat(int frierenId, long start, int ticks) {}

    private static final Map<Integer, Pat> PATS = new HashMap<>();
    private static final float EASE = 6.0F;

    public static void start(int playerId, int frierenId, int ticks) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) PATS.put(playerId, new Pat(frierenId, mc.level.getGameTime(), ticks));
    }

    /** 0 when not patting, easing up to 1 while the hand is on her head. */
    public static float amount(int playerId, float partialTick) {
        Pat pat = PATS.get(playerId);
        Minecraft mc = Minecraft.getInstance();
        if (pat == null || mc.level == null) return 0.0F;
        float t = mc.level.getGameTime() - pat.start + partialTick;
        if (t >= pat.ticks || t < 0) {
            if (t >= pat.ticks) PATS.remove(playerId);
            return 0.0F;
        }
        float in = Mth.clamp(t / EASE, 0.0F, 1.0F), out = Mth.clamp((pat.ticks - t) / EASE, 0.0F, 1.0F);
        float a = Math.min(in, out);
        return a * a * (3.0F - 2.0F * a);
    }

    /** -1..1, the stroking motion. */
    public static float stroke(float time) {
        return Mth.sin(time * 0.55F);
    }

    private ClientPetState() {}
}
