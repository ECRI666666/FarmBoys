package de.ecri.farmboys.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class FarmBoysConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue NATURAL_SPAWNING;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment("FarmBoys Configuration");
        builder.push("spawning");
        NATURAL_SPAWNING = builder
            .comment("Whether FarmBoys (CanBoy, Harvester, ChestBoy) should spawn naturally in the Overworld near villages/farms.",
                     "Default: false (Bots only appear when crafted and placed by a player).")
            .define("naturalSpawning", false);
        builder.pop();
        SPEC = builder.build();
    }

    private FarmBoysConfig() {}
}
