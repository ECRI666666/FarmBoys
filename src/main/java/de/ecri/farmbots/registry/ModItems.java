package de.ecri.farmbots.registry;

import de.ecri.farmbots.FarmBotsMod;
import de.ecri.farmbots.item.CanBotItem;
import de.ecri.farmbots.item.ChestBotItem;
import de.ecri.farmbots.item.HarvesterBotItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(FarmBotsMod.MOD_ID);

    public static final DeferredItem<Item> CAN_BOT = ITEMS.registerItem(
        "can_bot",
        CanBotItem::new,
        p -> p.stacksTo(16)
    );

    public static final DeferredItem<Item> HARVESTER_BOT = ITEMS.registerItem(
        "harvester_bot",
        HarvesterBotItem::new,
        p -> p.stacksTo(16)
    );

    public static final DeferredItem<Item> CHEST_BOT = ITEMS.registerItem(
        "chest_bot",
        ChestBotItem::new,
        p -> p.stacksTo(16)
    );

    private ModItems() {}
}
