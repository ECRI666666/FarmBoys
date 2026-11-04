package de.ecri.farmbots.item;

import de.ecri.farmbots.entity.HarvesterBotEntity;
import de.ecri.farmbots.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

public class HarvesterBotItem extends Item {
    public HarvesterBotItem(Properties properties) {
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

        HarvesterBotEntity bot = ModEntities.HARVESTER_BOT.get().create(serverLevel, EntitySpawnReason.SPAWN_ITEM_USE);
        if (bot != null) {
            bot.setPos(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);
            bot.setYRot(context.getRotation());
            serverLevel.addFreshEntity(bot);

            serverLevel.playSound(null, spawnPos, SoundEvents.SHEEP_SHEAR, SoundSource.BLOCKS, 0.8f, 1.3f);
            serverLevel.playSound(null, spawnPos, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 0.5f, 1.5f);

            if (context.getPlayer() != null && !context.getPlayer().isCreative()) {
                context.getItemInHand().shrink(1);
            }
            return InteractionResult.SUCCESS;
        }

        return InteractionResult.FAIL;
    }
}
