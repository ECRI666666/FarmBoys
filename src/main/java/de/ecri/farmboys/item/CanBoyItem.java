package de.ecri.farmboys.item;

import de.ecri.farmboys.entity.CanBoyEntity;
import de.ecri.farmboys.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

public class CanBoyItem extends Item {
    public CanBoyItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }

        BlockPos clickedPos = context.getClickedPos();
        Direction clickedFace = context.getClickedFace();
        BlockPos spawnPos = clickedPos.relative(clickedFace);

        CanBoyEntity bot = ModEntities.CAN_BOT.get().create(serverLevel, EntitySpawnReason.SPAWN_ITEM_USE);
        if (bot != null) {
            bot.setPos(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);
            bot.setYRot(context.getRotation());
            serverLevel.addFreshEntity(bot);

            serverLevel.playSound(null, spawnPos, SoundEvents.GENERIC_SPLASH, SoundSource.BLOCKS, 0.7f, 1.4f);
            serverLevel.playSound(null, spawnPos, SoundEvents.LANTERN_PLACE, SoundSource.BLOCKS, 0.8f, 1.2f);

            if (context.getPlayer() != null && !context.getPlayer().isCreative()) {
                context.getItemInHand().shrink(1);
            }
            return InteractionResult.SUCCESS;
        }

        return InteractionResult.FAIL;
    }
}
