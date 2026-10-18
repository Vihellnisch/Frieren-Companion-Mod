package com.frierenmod;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Common config (config/frierenmod-common.toml). */
public final class FrierenConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.DoubleValue MIMIC_CHANCE;
    public static final ModConfigSpec.BooleanValue CHEST_CURIOSITY;
    public static final ModConfigSpec.ConfigValue<java.util.List<? extends String>> FLOWER_BLACKLIST;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("mimics");
        MIMIC_CHANCE = b
                .comment("Chance that a single loot chest in a newly generated structure is a Mimic instead.")
                .defineInRange("mimicChance", 0.08D, 0.0D, 1.0D);
        CHEST_CURIOSITY = b
                .comment("Frieren now and then dives head-first into nearby chests to look for grimoires.")
                .define("frierenChestCuriosity", true);
        b.pop();
        b.push("spells");
        FLOWER_BLACKLIST = b
                .comment("Flowers the Flower Field spell never grows. Block ids (\"minecraft:wither_rose\") or block tags (\"#somemod:rare_flowers\").",
                        "Add flowers that another mod uses for progression, so the spell can't hand them out early.")
                .defineListAllowEmpty("flowerFieldBlacklist", java.util.List.of("minecraft:wither_rose"),
                        () -> "minecraft:wither_rose", o -> o instanceof String);
        b.pop();
        SPEC = b.build();
    }

    private FrierenConfig() {}
}
