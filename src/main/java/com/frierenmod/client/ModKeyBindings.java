package com.frierenmod.client;

import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public final class ModKeyBindings {
    public static final KeyMapping KEY_SPELL_WHEEL = new KeyMapping(
            "key.frierenmod.spell_wheel",
            GLFW.GLFW_KEY_G,
            "key.categories.frierenmod"
    );
    public static final KeyMapping KEY_SPELLBOOK = new KeyMapping(
            "key.frierenmod.spellbook",
            GLFW.GLFW_KEY_H,
            "key.categories.frierenmod"
    );

    private ModKeyBindings() {}
}
