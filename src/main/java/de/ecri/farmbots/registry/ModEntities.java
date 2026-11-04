package de.ecri.farmbots.registry;

import de.ecri.farmbots.FarmBotsMod;
import de.ecri.farmbots.entity.CanBotEntity;
import de.ecri.farmbots.entity.ChestBotEntity;
import de.ecri.farmbots.entity.HarvesterBotEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, FarmBotsMod.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<CanBotEntity>> CAN_BOT = ENTITIES.register(
        "can_bot",
        () -> EntityType.Builder.<CanBotEntity>of(CanBotEntity::new, MobCategory.CREATURE)
            .sized(0.55f, 0.65f)
            .clientTrackingRange(10)
            .build(ResourceKey.create(Registries.ENTITY_TYPE, FarmBotsMod.id("can_bot")))
    );

    public static final DeferredHolder<EntityType<?>, EntityType<HarvesterBotEntity>> HARVESTER_BOT = ENTITIES.register(
        "harvester_bot",
        () -> EntityType.Builder.<HarvesterBotEntity>of(HarvesterBotEntity::new, MobCategory.CREATURE)
            .sized(0.6f, 0.55f)
            .clientTrackingRange(10)
            .build(ResourceKey.create(Registries.ENTITY_TYPE, FarmBotsMod.id("harvester_bot")))
    );

    public static final DeferredHolder<EntityType<?>, EntityType<ChestBotEntity>> CHEST_BOT = ENTITIES.register(
        "chest_bot",
        () -> EntityType.Builder.<ChestBotEntity>of(ChestBotEntity::new, MobCategory.CREATURE)
            .sized(0.6f, 0.65f)
            .clientTrackingRange(10)
            .build(ResourceKey.create(Registries.ENTITY_TYPE, FarmBotsMod.id("chest_bot")))
    );

    private ModEntities() {}
}
