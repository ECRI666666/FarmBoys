package de.ecri.farmbots;

import de.ecri.farmbots.client.FarmBotsClient;
import de.ecri.farmbots.config.FarmBotsConfig;
import de.ecri.farmbots.entity.CanBotEntity;
import de.ecri.farmbots.entity.ChestBotEntity;
import de.ecri.farmbots.entity.HarvesterBotEntity;
import de.ecri.farmbots.registry.ModEntities;
import de.ecri.farmbots.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@Mod(FarmBotsMod.MOD_ID)
public class FarmBotsMod {
    public static final String MOD_ID = "farmbots";

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    public FarmBotsMod(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.LOCAL, FarmBotsConfig.SPEC);

        ModEntities.ENTITIES.register(modBus);
        ModItems.ITEMS.register(modBus);

        modBus.addListener((EntityAttributeCreationEvent e) -> {
            e.put(ModEntities.CAN_BOT.get(), CanBotEntity.createAttributes().build());
            e.put(ModEntities.HARVESTER_BOT.get(), HarvesterBotEntity.createAttributes().build());
            e.put(ModEntities.CHEST_BOT.get(), ChestBotEntity.createAttributes().build());
        });

        if (FMLEnvironment.getDist() == Dist.CLIENT) {
            modBus.addListener((EntityRenderersEvent.RegisterRenderers e) -> {
                FarmBotsClient.registerRenderers(e);
            });
            container.registerExtensionPoint(
                IConfigScreenFactory.class,
                ConfigurationScreen::new
            );
        }

        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> {
            if (!FarmBotsConfig.NATURAL_SPAWNING.get()) return;

            var server = e.getServer();
            if (server.getTickCount() % 160 != 0) return;

            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (player.level().dimension() != Level.OVERWORLD) continue;
                if (!(player.level() instanceof ServerLevel level)) continue;

                // Only spawn near farmland (villages/player farms)
                BlockPos ppos = player.blockPosition();
                boolean nearFarm = false;
                for (int dx = -10; dx <= 10; dx += 5) {
                    for (int dz = -10; dz <= 10; dz += 5) {
                        if (level.getBlockState(ppos.offset(dx, 0, dz)).is(Blocks.FARMLAND) ||
                            level.getBlockState(ppos.offset(dx, -1, dz)).is(Blocks.FARMLAND)) {
                            nearFarm = true;
                            break;
                        }
                    }
                    if (nearFarm) break;
                }
                if (!nearFarm) continue;

                var existing = level.getEntitiesOfClass(
                    CanBotEntity.class,
                    player.getBoundingBox().inflate(35.0)
                );
                if (existing.size() >= 2) continue;

                var random = level.getRandom();
                if (random.nextDouble() > 0.20) continue;

                double angle = random.nextDouble() * Math.PI * 2.0;
                double dist = 10.0 + random.nextDouble() * 14.0;
                int x = (int) (player.getX() + Math.cos(angle) * dist);
                int z = (int) (player.getZ() + Math.sin(angle) * dist);
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                BlockPos targetPos = new BlockPos(x, y, z);

                if (level.getBlockState(targetPos.below()).isSolid() && level.getBlockState(targetPos).isAir()) {
                    var bot = ModEntities.CAN_BOT.get().create(level, EntitySpawnReason.NATURAL);
                    if (bot != null) {
                        bot.setPos(x + 0.5, y, z + 0.5);
                        bot.setYRot(random.nextFloat() * 360.0f);
                        level.addFreshEntity(bot);
                    }
                }
            }
        });
    }
}
