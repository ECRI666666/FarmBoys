package de.ecri.farmboys.client;

import de.ecri.farmboys.registry.ModEntities;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

public final class FarmBoysClient {
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.CAN_BOT.get(), CanBoyRenderer::new);
        event.registerEntityRenderer(ModEntities.HARVESTER_BOT.get(), HarvesterBoyRenderer::new);
        event.registerEntityRenderer(ModEntities.CHEST_BOT.get(), ChestBoyRenderer::new);
    }

    private FarmBoysClient() {}
}
