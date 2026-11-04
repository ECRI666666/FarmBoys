package de.ecri.farmboys.registry;

import de.ecri.farmboys.FarmBoysMod;
import de.ecri.farmboys.item.CanBoyItem;
import de.ecri.farmboys.item.ChestBoyItem;
import de.ecri.farmboys.item.HarvesterBoyItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(FarmBoysMod.MOD_ID);

    public static final DeferredItem<Item> CAN_BOT = ITEMS.registerItem(
        "can_boy",
        CanBoyItem::new,
        p -> p.stacksTo(16)
    );

    public static final DeferredItem<Item> HARVESTER_BOT = ITEMS.registerItem(
        "harvester_boy",
        HarvesterBoyItem::new,
        p -> p.stacksTo(16)
    );

    public static final DeferredItem<Item> CHEST_BOT = ITEMS.registerItem(
        "chest_boy",
        ChestBoyItem::new,
        p -> p.stacksTo(16)
    );

    private ModItems() {}
}
