package de.ecri.farmbots.entity;

import de.ecri.farmbots.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * ChestBot: The walking wooden chest and seeder!
 * Picks up harvested crops from the ground, plants fresh seeds on tilled soil,
 * deposits harvests into nearby real chests, and sometimes trips comically on a pebble!
 */
public class ChestBotEntity extends PathfinderMob {
    public static final EntityDataAccessor<Integer> ITEM_COUNT = SynchedEntityData.defineId(ChestBotEntity.class, EntityDataSerializers.INT);
    public static final EntityDataAccessor<Boolean> LID_OPEN = SynchedEntityData.defineId(ChestBotEntity.class, EntityDataSerializers.BOOLEAN);
    public static final EntityDataAccessor<Boolean> CLUMSY_TRIP = SynchedEntityData.defineId(ChestBotEntity.class, EntityDataSerializers.BOOLEAN);

    private final List<ItemStack> inventory = new ArrayList<>();
    private int tripTimer = 0;
    private int lidOpenTicks = 0;
    private int emptyCooldown = 0;

    public ChestBotEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
            .add(Attributes.MAX_HEALTH, 15.0)
            .add(Attributes.MOVEMENT_SPEED, 0.23)
            .add(Attributes.STEP_HEIGHT, 1.0)
            .add(Attributes.FOLLOW_RANGE, 24.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(ITEM_COUNT, 0);
        builder.define(LID_OPEN, false);
        builder.define(CLUMSY_TRIP, false);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new PickUpCropsGoal(this));
        this.goalSelector.addGoal(2, new PlantSeedsGoal(this));
        this.goalSelector.addGoal(3, new DepositToChestGoal(this));
        this.goalSelector.addGoal(4, new ChestBotWanderGoal(this));
    }

    public int getItemCount() {
        return this.entityData.get(ITEM_COUNT);
    }

    public boolean isLidOpen() {
        return this.entityData.get(LID_OPEN);
    }

    public boolean isClumsyTripping() {
        return this.entityData.get(CLUMSY_TRIP);
    }

    private void updateItemCount() {
        int count = 0;
        for (ItemStack s : this.inventory) {
            count += s.getCount();
        }
        this.entityData.set(ITEM_COUNT, count);
    }

    public List<ItemStack> getInventory() {
        return this.inventory;
    }

    public void addItem(ItemStack stack) {
        if (stack.isEmpty()) return;
        for (ItemStack existing : this.inventory) {
            if (ItemStack.isSameItemSameComponents(existing, stack)) {
                int canAdd = existing.getMaxStackSize() - existing.getCount();
                if (canAdd > 0) {
                    int add = Math.min(canAdd, stack.getCount());
                    existing.grow(add);
                    stack.shrink(add);
                    if (stack.isEmpty()) break;
                }
            }
        }
        if (!stack.isEmpty()) {
            this.inventory.add(stack.copy());
            stack.setCount(0);
        }
        updateItemCount();
    }

    public void triggerTrip() {
        if (this.inventory.isEmpty()) return;
        this.tripTimer = 22;
        this.entityData.set(CLUMSY_TRIP, true);
        this.entityData.set(LID_OPEN, true);
        this.getNavigation().stop();

        this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
            SoundEvents.CHEST_OPEN, SoundSource.NEUTRAL, 0.8f, 1.4f);
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
            SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.NEUTRAL, 0.7f, 1.8f);

        // Drop 1 single item comically
        ItemStack first = this.inventory.getFirst();
        ItemStack dropped = first.split(1);
        if (first.isEmpty()) {
            this.inventory.removeFirst();
        }
        updateItemCount();

        ItemEntity entity = new ItemEntity(this.level(), this.getX(), this.getY() + 0.3, this.getZ(), dropped);
        entity.setDeltaMovement((this.random.nextDouble() - 0.5) * 0.2, 0.25, (this.random.nextDouble() - 0.5) * 0.2);
        this.level().addFreshEntity(entity);
    }

    public void emptyToPlayer(Player player) {
        if (this.level().isClientSide()) return;
        this.emptyCooldown = 120; // 6s cooldown
        this.getNavigation().stop();

        this.entityData.set(LID_OPEN, true);
        this.lidOpenTicks = 20;

        for (ItemStack s : this.inventory) {
            if (s.isEmpty()) continue;
            ItemEntity drop = new ItemEntity(this.level(), this.getX(), this.getY() + 0.3, this.getZ(), s);
            drop.setDefaultPickUpDelay();
            if (player != null) {
                Vec3 dir = player.position().add(0, 0.5, 0).subtract(this.position()).normalize().scale(0.24);
                drop.setDeltaMovement(dir.x, 0.35, dir.z);
            }
            this.level().addFreshEntity(drop);
        }
        this.inventory.clear();
        updateItemCount();

        this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
            SoundEvents.CHEST_OPEN, SoundSource.PLAYERS, 0.8f, 1.2f);
    }

    @Override
    public void tick() {
        super.tick();

        if (emptyCooldown > 0) {
            emptyCooldown--;
        }

        if (tripTimer > 0) {
            tripTimer--;
            if (tripTimer == 0) {
                this.entityData.set(CLUMSY_TRIP, false);
                this.entityData.set(LID_OPEN, false);
            }
        }

        if (lidOpenTicks > 0) {
            lidOpenTicks--;
            if (lidOpenTicks == 0 && tripTimer <= 0) {
                this.entityData.set(LID_OPEN, false);
            }
        }

        // Random chance to comically trip while walking with items
        if (!this.level().isClientSide() && tripTimer <= 0 && !this.inventory.isEmpty()) {
            if (this.getDeltaMovement().horizontalDistanceSqr() > 0.002 && this.random.nextFloat() < 0.003f) {
                triggerTrip();
            }
        }
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand == InteractionHand.MAIN_HAND && !this.level().isClientSide()) {
            emptyToPlayer(player);
            return InteractionResult.SUCCESS;
        }
        return super.mobInteract(player, hand);
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (!this.level().isClientSide()) {
            emptyToPlayer(null);
            ItemEntity dropItem = new ItemEntity(this.level(), this.getX(), this.getY() + 0.1, this.getZ(), new ItemStack(ModItems.CHEST_BOT.get()));
            this.level().addFreshEntity(dropItem);
        }
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.store("inventory", ItemStack.CODEC.listOf(), this.inventory);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.inventory.clear();
        input.read("inventory", ItemStack.CODEC.listOf()).ifPresent(this.inventory::addAll);
        updateItemCount();
    }

    public static boolean isFarmingItem(ItemStack stack) {
        return stack.is(Items.WHEAT) || stack.is(Items.WHEAT_SEEDS) ||
               stack.is(Items.CARROT) || stack.is(Items.POTATO) ||
               stack.is(Items.BEETROOT) || stack.is(Items.BEETROOT_SEEDS) ||
               stack.is(Items.MELON_SEEDS) || stack.is(Items.PUMPKIN_SEEDS) ||
               stack.is(Items.PUMPKIN) || stack.is(Items.MELON_SLICE) ||
               stack.is(Items.BONE_MEAL) || stack.is(Items.NETHER_WART);
    }

    /** AI Goal: Picks up dropped crops/seeds from the ground */
    static class PickUpCropsGoal extends Goal {
        private final ChestBotEntity bot;
        private ItemEntity targetItem;
        private int searchDelay = 0;

        public PickUpCropsGoal(ChestBotEntity bot) {
            this.bot = bot;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (this.bot.isClumsyTripping() || this.bot.emptyCooldown > 0) return false;
            if (searchDelay > 0) {
                searchDelay--;
                return false;
            }
            searchDelay = 10;

            List<ItemEntity> items = this.bot.level().getEntitiesOfClass(
                ItemEntity.class,
                this.bot.getBoundingBox().inflate(12.0),
                e -> e.isAlive() && !e.getItem().isEmpty() && isFarmingItem(e.getItem())
            );

            if (items.isEmpty()) return false;
            items.sort((a, b) -> Double.compare(this.bot.distanceToSqr(a), this.bot.distanceToSqr(b)));

            for (ItemEntity item : items) {
                Path path = this.bot.getNavigation().createPath(item, 1);
                if (path != null && path.canReach()) {
                    this.targetItem = item;
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean canContinueToUse() {
            return !this.bot.isClumsyTripping() && this.targetItem != null && this.targetItem.isAlive() && !this.targetItem.getItem().isEmpty();
        }

        @Override
        public void tick() {
            if (this.targetItem == null) return;
            this.bot.getLookControl().setLookAt(this.targetItem, 30.0f, 30.0f);
            this.bot.getNavigation().moveTo(this.targetItem, 1.2);

            if (this.bot.distanceToSqr(this.targetItem) < 2.0) {
                this.bot.addItem(this.targetItem.getItem());
                this.bot.level().playSound(null, this.bot.getX(), this.bot.getY(), this.bot.getZ(),
                    SoundEvents.ITEM_PICKUP, SoundSource.NEUTRAL, 0.6f, 1.3f);

                if (this.targetItem.getItem().isEmpty()) {
                    this.targetItem.discard();
                }
                this.targetItem = null;
                this.bot.getNavigation().stop();
            }
        }
    }

    /** AI Goal: Plants seeds on empty farmland */
    static class PlantSeedsGoal extends Goal {
        private final ChestBotEntity bot;
        private BlockPos targetFarmland;
        private int searchDelay = 0;

        public PlantSeedsGoal(ChestBotEntity bot) {
            this.bot = bot;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        private ItemStack findSeed() {
            for (ItemStack s : this.bot.inventory) {
                if (s.is(Items.WHEAT_SEEDS) || s.is(Items.CARROT) || s.is(Items.POTATO) || s.is(Items.BEETROOT_SEEDS)) {
                    return s;
                }
            }
            return ItemStack.EMPTY;
        }

        @Override
        public boolean canUse() {
            if (this.bot.isClumsyTripping()) return false;
            if (findSeed().isEmpty()) return false;
            if (searchDelay > 0) {
                searchDelay--;
                return false;
            }
            searchDelay = 12;

            BlockPos origin = this.bot.blockPosition();
            for (int dx = -10; dx <= 10; dx += 2) {
                for (int dz = -10; dz <= 10; dz += 2) {
                    for (int dy = -2; dy <= 1; dy++) {
                        BlockPos check = origin.offset(dx, dy, dz);
                        if (this.bot.level().getBlockState(check).is(Blocks.FARMLAND) && this.bot.level().getBlockState(check.above()).isAir()) {
                            Path path = this.bot.getNavigation().createPath(check.above(), 1);
                            if (path != null && path.canReach()) {
                                this.targetFarmland = check;
                                return true;
                            }
                        }
                    }
                }
            }
            return false;
        }

        @Override
        public boolean canContinueToUse() {
            return !this.bot.isClumsyTripping() && this.targetFarmland != null &&
                   this.bot.level().getBlockState(this.targetFarmland).is(Blocks.FARMLAND) &&
                   this.bot.level().getBlockState(this.targetFarmland.above()).isAir() &&
                   !findSeed().isEmpty();
        }

        @Override
        public void tick() {
            if (this.targetFarmland == null) return;
            this.bot.getLookControl().setLookAt(this.targetFarmland.getX() + 0.5, this.targetFarmland.getY() + 1.0, this.targetFarmland.getZ() + 0.5, 30.0f, 30.0f);
            this.bot.getNavigation().moveTo(this.targetFarmland.getX() + 0.5, this.targetFarmland.getY() + 1.0, this.targetFarmland.getZ() + 0.5, 1.15);

            if (this.bot.distanceToSqr(Vec3.atCenterOf(this.targetFarmland.above())) < 2.5) {
                ItemStack seed = findSeed();
                if (!seed.isEmpty()) {
                    BlockState cropState = Blocks.WHEAT.defaultBlockState();
                    if (seed.is(Items.CARROT)) cropState = Blocks.CARROTS.defaultBlockState();
                    else if (seed.is(Items.POTATO)) cropState = Blocks.POTATOES.defaultBlockState();
                    else if (seed.is(Items.BEETROOT_SEEDS)) cropState = Blocks.BEETROOTS.defaultBlockState();

                    this.bot.level().setBlockAndUpdate(this.targetFarmland.above(), cropState);
                    seed.shrink(1);
                    this.bot.updateItemCount();

                    this.bot.level().playSound(null, this.targetFarmland.getX(), this.targetFarmland.getY() + 1, this.targetFarmland.getZ(),
                        SoundEvents.GRASS_PLACE, SoundSource.BLOCKS, 0.7f, 1.2f);
                }
                this.targetFarmland = null;
                this.bot.getNavigation().stop();
            }
        }
    }

    /** AI Goal: Deposits harvest into nearby chest containers */
    static class DepositToChestGoal extends Goal {
        private final ChestBotEntity bot;
        private BlockPos targetChest;
        private int searchDelay = 0;

        public DepositToChestGoal(ChestBotEntity bot) {
            this.bot = bot;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (this.bot.isClumsyTripping() || this.bot.inventory.isEmpty()) return false;
            if (searchDelay > 0) {
                searchDelay--;
                return false;
            }
            searchDelay = 20;

            BlockPos origin = this.bot.blockPosition();
            for (int dx = -10; dx <= 10; dx += 2) {
                for (int dz = -10; dz <= 10; dz += 2) {
                    for (int dy = -1; dy <= 2; dy++) {
                        BlockPos check = origin.offset(dx, dy, dz);
                        BlockState state = this.bot.level().getBlockState(check);
                        if (state.is(Blocks.CHEST) || state.is(Blocks.BARREL)) {
                            Path path = this.bot.getNavigation().createPath(check, 1);
                            if (path != null && path.canReach()) {
                                this.targetChest = check;
                                return true;
                            }
                        }
                    }
                }
            }
            return false;
        }

        @Override
        public boolean canContinueToUse() {
            return !this.bot.isClumsyTripping() && this.targetChest != null && !this.bot.inventory.isEmpty();
        }

        @Override
        public void tick() {
            if (this.targetChest == null) return;
            this.bot.getLookControl().setLookAt(this.targetChest.getX() + 0.5, this.targetChest.getY() + 0.5, this.targetChest.getZ() + 0.5, 30.0f, 30.0f);
            this.bot.getNavigation().moveTo(this.targetChest.getX() + 0.5, this.targetChest.getY(), this.targetChest.getZ() + 0.5, 1.15);

            if (this.bot.distanceToSqr(Vec3.atCenterOf(this.targetChest)) < 3.0) {
                BlockEntity be = this.bot.level().getBlockEntity(this.targetChest);
                if (be instanceof Container container) {
                    this.bot.entityData.set(LID_OPEN, true);
                    this.bot.lidOpenTicks = 20;

                    for (int i = 0; i < this.bot.inventory.size(); i++) {
                        ItemStack stack = this.bot.inventory.get(i);
                        if (stack.isEmpty()) continue;

                        for (int s = 0; s < container.getContainerSize(); s++) {
                            ItemStack inChest = container.getItem(s);
                            if (inChest.isEmpty()) {
                                container.setItem(s, stack.copy());
                                stack.setCount(0);
                                break;
                            } else if (ItemStack.isSameItemSameComponents(inChest, stack)) {
                                int add = Math.min(container.getMaxStackSize() - inChest.getCount(), stack.getCount());
                                if (add > 0) {
                                    inChest.grow(add);
                                    stack.shrink(add);
                                    if (stack.isEmpty()) break;
                                }
                            }
                        }
                    }
                    this.bot.inventory.removeIf(ItemStack::isEmpty);
                    this.bot.updateItemCount();

                    this.bot.level().playSound(null, this.targetChest.getX(), this.targetChest.getY(), this.targetChest.getZ(),
                        SoundEvents.CHEST_CLOSE, SoundSource.BLOCKS, 0.7f, 1.2f);
                }
                this.targetChest = null;
                this.bot.getNavigation().stop();
            }
        }
    }

    /** AI Goal: Wandering */
    static class ChestBotWanderGoal extends Goal {
        private final ChestBotEntity bot;
        private double headingRad = 0;
        private int walkTicks = 0;

        public ChestBotWanderGoal(ChestBotEntity bot) {
            this.bot = bot;
            this.setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return !this.bot.isClumsyTripping();
        }

        @Override
        public void start() {
            this.headingRad = Math.toRadians(this.bot.getYRot());
            this.walkTicks = 30 + this.bot.getRandom().nextInt(50);
        }

        @Override
        public void tick() {
            if (this.bot.isClumsyTripping()) return;

            walkTicks--;
            if (this.bot.horizontalCollision || walkTicks <= 0) {
                double turn = (0.4 + this.bot.getRandom().nextDouble() * 0.6) * Math.PI;
                if (this.bot.getRandom().nextBoolean()) turn = -turn;
                this.headingRad += turn;
                this.walkTicks = 40 + this.bot.getRandom().nextInt(60);
                this.bot.setYRot((float) Math.toDegrees(this.headingRad));
            }

            double tx = this.bot.getX() + Math.sin(-this.headingRad) * 3.0;
            double tz = this.bot.getZ() + Math.cos(-this.headingRad) * 3.0;
            this.bot.getNavigation().moveTo(tx, this.bot.getY(), tz, 1.0);
        }
    }
}
