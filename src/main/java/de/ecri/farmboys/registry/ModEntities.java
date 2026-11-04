package de.ecri.farmboys.registry;

import de.ecri.farmboys.FarmBoysMod;
import de.ecri.farmboys.entity.CanBoyEntity;
import de.ecri.farmboys.entity.ChestBoyEntity;
import de.ecri.farmboys.entity.HarvesterBoyEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, FarmBoysMod.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<CanBoyEntity>> CAN_BOT = ENTITIES.register(
        "can_boy",
        () -> EntityType.Builder.<CanBoyEntity>of(CanBoyEntity::new, MobCategory.CREATURE)
            .sized(0.55f, 0.65f)
            .clientTrackingRange(10)
            .build(ResourceKey.create(Registries.ENTITY_TYPE, FarmBoysMod.id("can_boy")))
    );

    public static final DeferredHolder<EntityType<?>, EntityType<HarvesterBoyEntity>> HARVESTER_BOT = ENTITIES.register(
        "harvester_boy",
        () -> EntityType.Builder.<HarvesterBoyEntity>of(HarvesterBoyEntity::new, MobCategory.CREATURE)
            .sized(0.6f, 0.55f)
            .clientTrackingRange(10)
            .build(ResourceKey.create(Registries.ENTITY_TYPE, FarmBoysMod.id("harvester_boy")))
    );

    public static final DeferredHolder<EntityType<?>, EntityType<ChestBoyEntity>> CHEST_BOT = ENTITIES.register(
        "chest_boy",
        () -> EntityType.Builder.<ChestBoyEntity>of(ChestBoyEntity::new, MobCategory.CREATURE)
            .sized(0.6f, 0.65f)
            .clientTrackingRange(10)
            .build(ResourceKey.create(Registries.ENTITY_TYPE, FarmBoysMod.id("chest_boy")))
    );

    private ModEntities() {}
}
