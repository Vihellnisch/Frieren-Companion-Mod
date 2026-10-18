package com.frierenmod.spell;

/**
 * Base class holding shared metadata for every spell Frieren can cast.
 */
public abstract class SpellBase {
    protected final String name;
    protected final int cooldownTicks;

    protected SpellBase(String name, int cooldownTicks) {
        this.name = name;
        this.cooldownTicks = cooldownTicks;
    }

    public String getName() {
        return name;
    }

    public int getCooldownTicks() {
        return cooldownTicks;
    }
}
