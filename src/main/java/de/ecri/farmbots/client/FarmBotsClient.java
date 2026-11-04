package de.ecri.farmbots.client;

import de.ecri.farmbots.registry.ModEntities;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

public final class FarmBotsClient {
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.CAN_BOT.get(), CanBotRenderer::new);
        event.registerEntityRenderer(ModEntities.HARVESTER_BOT.get(), HarvesterBotRenderer::new);
        event.registerEntityRenderer(ModEntities.CHEST_BOT.get(), ChestBotRenderer::new);
    }

    private FarmBotsClient() {}
}
