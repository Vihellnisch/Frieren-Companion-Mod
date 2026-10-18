package com.frierenmod.registry;

import com.frierenmod.FrierenMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(Registries.SOUND_EVENT, FrierenMod.MODID);

    /** "Kurai yo! Kowai yo!" from inside a Mimic: mono so it fades with distance, low-passed to sound boxed in. */
    public static final DeferredHolder<SoundEvent, SoundEvent> FRIEREN_KURAI_YO =
            SOUNDS.register("frieren.kurai_yo", () -> SoundEvent.createVariableRangeEvent(FrierenMod.id("frieren.kurai_yo")));

    private ModSounds() {}
}
