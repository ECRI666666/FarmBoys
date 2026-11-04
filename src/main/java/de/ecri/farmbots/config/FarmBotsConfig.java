package de.ecri.farmbots.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class FarmBotsConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue NATURAL_SPAWNING;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment("FarmBots Configuration");
        builder.push("spawning");
        NATURAL_SPAWNING = builder
            .comment("Whether FarmBots (CanBot, Harvester, ChestBot) should spawn naturally in the Overworld near villages/farms.",
                     "Default: false (Bots only appear when crafted and placed by a player).")
            .define("naturalSpawning", false);
        builder.pop();
        SPEC = builder.build();
    }

    private FarmBotsConfig() {}
}
