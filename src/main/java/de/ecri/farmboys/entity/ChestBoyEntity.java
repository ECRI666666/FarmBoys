package de.ecri.farmboys.entity;

import de.ecri.farmboys.registry.ModItems;
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
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
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
 * ChestBoy: The walking wooden chest, sower, and courier!
 * - Vacuums up all crops and seeds dropped on the ground.
 * - Sows fresh seeds onto empty tilled farmland.
 * - Carries harvests to the nearest chest/barrel and deposits them (while keeping a seed reserve).
 * - Sometimes trips comically on a pebble!
 */
public class ChestBoyEntity extends PathfinderMob {
    public static final EntityDataAccessor<Integer> ITEM_COUNT = SynchedEntityData.defineId(ChestBoyEntity.class, EntityDataSerializers.INT);
    public static final EntityDataAccessor<Boolean> LID_OPEN = SynchedEntityData.defineId(ChestBoyEntity.class, EntityDataSerializers.BOOLEAN);
    public static final EntityDataAccessor<Boolean> CLUMSY_TRIP = SynchedEntityData.defineId(ChestBoyEntity.class, EntityDataSerializers.BOOLEAN);

    private final List<ItemStack> inventory = new ArrayList<>();
    private int tripTimer = 0;
    private int lidOpenTicks = 0;
    private int emptyCooldown = 0;

    public ChestBoyEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
            .add(Attributes.MAX_HEALTH, 15.0)
            .add(Attributes.MOVEMENT_SPEED, 0.23)
            .add(Attributes.STEP_HEIGHT, 1.0)
            .add(Attributes.FOLLOW_RANGE, 32.0);
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
        this.goalSelector.addGoal(1, new PickUpCropsGoal(this));    // 1st Priority: Vacuum ground drops
        this.goalSelector.addGoal(2, new PlantSeedsGoal(this));     // 2nd Priority: Plant bare farmland
        this.goalSelector.addGoal(3, new DepositToChestGoal(this)); // 3rd Priority: Store harvest in nearest chest
        this.goalSelector.addGoal(4, new ChestBoyWanderGoal(this));
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

        // Continuous vacuuming while moving around
        if (!this.level().isClientSide() && this.emptyCooldown <= 0) {
            List<ItemEntity> nearby = this.level().getEntitiesOfClass(
                ItemEntity.class,
                this.getBoundingBox().inflate(2.2),
                e -> e.isAlive() && !e.getItem().isEmpty() && isFarmingItem(e.getItem())
            );
            for (ItemEntity item : nearby) {
                this.addItem(item.getItem());
                this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.ITEM_PICKUP, SoundSource.NEUTRAL, 0.5f, 1.4f);
                if (item.getItem().isEmpty()) {
                    item.discard();
                }
            }

            // Comical tripping chance when walking with items
            if (tripTimer <= 0 && !this.inventory.isEmpty()) {
                if (this.getDeltaMovement().horizontalDistanceSqr() > 0.002 && this.random.nextFloat() < 0.0025f) {
                    triggerTrip();
                }
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
        if (stack.isEmpty()) return false;
        Item item = stack.getItem();
        if (item instanceof BlockItem bi && bi.getBlock() instanceof CropBlock) return true;
        return stack.is(Items.WHEAT) || stack.is(Items.WHEAT_SEEDS) ||
               stack.is(Items.CARROT) || stack.is(Items.POTATO) || stack.is(Items.POISONOUS_POTATO) ||
               stack.is(Items.BEETROOT) || stack.is(Items.BEETROOT_SEEDS) ||
               stack.is(Items.MELON_SEEDS) || stack.is(Items.PUMPKIN_SEEDS) ||
               stack.is(Items.PUMPKIN) || stack.is(Items.MELON_SLICE) || stack.is(Items.MELON) ||
               stack.is(Items.BONE_MEAL) || stack.is(Items.NETHER_WART) ||
               stack.is(Items.TORCHFLOWER_SEEDS) || stack.is(Items.PITCHER_POD) ||
               stack.is(Items.SWEET_BERRIES) || stack.is(Items.COCOA_BEANS) ||
               stack.is(Items.SUGAR_CANE) || stack.is(Items.BAMBOO) || stack.is(Items.APPLE);
    }

    public static boolean isPlantableSeed(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return stack.is(Items.WHEAT_SEEDS) || stack.is(Items.CARROT) ||
               stack.is(Items.POTATO) || stack.is(Items.BEETROOT_SEEDS) ||
               stack.is(Items.TORCHFLOWER_SEEDS) || stack.is(Items.PITCHER_POD) ||
               stack.is(Items.PUMPKIN_SEEDS) || stack.is(Items.MELON_SEEDS);
    }

    /** AI Goal: Picks up dropped crops/seeds from the ground within 24 blocks */
    static class PickUpCropsGoal extends Goal {
        private final ChestBoyEntity bot;
        private ItemEntity targetItem;
        private int searchDelay = 0;

        public PickUpCropsGoal(ChestBoyEntity bot) {
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
            searchDelay = 8;

            List<ItemEntity> items = this.bot.level().getEntitiesOfClass(
                ItemEntity.class,
                this.bot.getBoundingBox().inflate(24.0),
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
            this.bot.getNavigation().moveTo(this.targetItem, 1.25);

            if (this.bot.distanceToSqr(this.targetItem) < 2.5) {
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

    /** AI Goal: Plants seeds on empty farmland within 20 blocks */
    static class PlantSeedsGoal extends Goal {
        private final ChestBoyEntity bot;
        private BlockPos targetFarmland;
        private int searchDelay = 0;

        public PlantSeedsGoal(ChestBoyEntity bot) {
            this.bot = bot;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        private ItemStack findSeed() {
            for (ItemStack s : this.bot.inventory) {
                if (isPlantableSeed(s)) {
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
            searchDelay = 10;

            BlockPos origin = this.bot.blockPosition();
            BlockPos closest = null;
            double closestDistSq = Double.MAX_VALUE;

            for (int dx = -20; dx <= 20; dx++) {
                for (int dz = -20; dz <= 20; dz++) {
                    if (dx * dx + dz * dz > 400) continue;
                    for (int dy = -2; dy <= 2; dy++) {
                        BlockPos check = origin.offset(dx, dy, dz);
                        if (this.bot.level().getBlockState(check).is(Blocks.FARMLAND) && this.bot.level().getBlockState(check.above()).isAir()) {
                            double d = origin.distSqr(check);
                            if (d < closestDistSq) {
                                Path path = this.bot.getNavigation().createPath(check.above(), 1);
                                if (path != null && path.canReach()) {
                                    closestDistSq = d;
                                    closest = check;
                                }
                            }
                        }
                    }
                }
            }

            this.targetFarmland = closest;
            return this.targetFarmland != null;
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
                    else if (seed.is(Items.TORCHFLOWER_SEEDS)) cropState = Blocks.TORCHFLOWER_CROP.defaultBlockState();
                    else if (seed.is(Items.PITCHER_POD)) cropState = Blocks.PITCHER_CROP.defaultBlockState();
                    else if (seed.is(Items.PUMPKIN_SEEDS)) cropState = Blocks.PUMPKIN_STEM.defaultBlockState();
                    else if (seed.is(Items.MELON_SEEDS)) cropState = Blocks.MELON_STEM.defaultBlockState();

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

    /** AI Goal: Deposits harvest into the NEAREST chest container (keeping up to 6 seeds for planting) */
    static class DepositToChestGoal extends Goal {
        private final ChestBoyEntity bot;
        private BlockPos targetChest;
        private int searchDelay = 0;

        public DepositToChestGoal(ChestBoyEntity bot) {
            this.bot = bot;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        private boolean hasGoodsToDeposit() {
            for (ItemStack s : this.bot.inventory) {
                if (s.isEmpty()) continue;
                // If it's a non-seed crop (e.g. wheat, carrots, potatoes, pumpkin, etc.)
                if (!isPlantableSeed(s)) return true;
                // If it's seeds, but we have more than 6 seeds reserve
                if (s.getCount() > 6) return true;
            }
            return false;
        }

        @Override
        public boolean canUse() {
            if (this.bot.isClumsyTripping() || !hasGoodsToDeposit()) return false;
            if (searchDelay > 0) {
                searchDelay--;
                return false;
            }
            searchDelay = 15;

            BlockPos origin = this.bot.blockPosition();
            BlockPos closestChest = null;
            double closestDistSq = Double.MAX_VALUE;

            // Search every coordinate in 24-block radius for the NEAREST reachable chest/barrel
            for (int dx = -24; dx <= 24; dx++) {
                for (int dz = -24; dz <= 24; dz++) {
                    if (dx * dx + dz * dz > 576) continue;
                    for (int dy = -3; dy <= 3; dy++) {
                        BlockPos check = origin.offset(dx, dy, dz);
                        BlockState state = this.bot.level().getBlockState(check);
                        if (state.is(Blocks.CHEST) || state.is(Blocks.TRAPPED_CHEST) || state.is(Blocks.BARREL)) {
                            double d = origin.distSqr(check);
                            if (d < closestDistSq) {
                                Path path = this.bot.getNavigation().createPath(check, 1);
                                if (path != null && path.canReach()) {
                                    closestDistSq = d;
                                    closestChest = check;
                                }
                            }
                        }
                    }
                }
            }

            this.targetChest = closestChest;
            return this.targetChest != null;
        }

        @Override
        public boolean canContinueToUse() {
            return !this.bot.isClumsyTripping() && this.targetChest != null && hasGoodsToDeposit();
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
                    this.bot.lidOpenTicks = 25;

                    for (int i = 0; i < this.bot.inventory.size(); i++) {
                        ItemStack stack = this.bot.inventory.get(i);
                        if (stack.isEmpty()) continue;

                        // Keep up to 6 seeds for planting new crops!
                        int depositAmount = stack.getCount();
                        if (isPlantableSeed(stack)) {
                            depositAmount = Math.max(0, stack.getCount() - 6);
                        }
                        if (depositAmount <= 0) continue;

                        ItemStack toDeposit = stack.split(depositAmount);

                        for (int s = 0; s < container.getContainerSize(); s++) {
                            ItemStack inChest = container.getItem(s);
                            if (inChest.isEmpty()) {
                                container.setItem(s, toDeposit.copy());
                                toDeposit.setCount(0);
                                break;
                            } else if (ItemStack.isSameItemSameComponents(inChest, toDeposit)) {
                                int add = Math.min(container.getMaxStackSize() - inChest.getCount(), toDeposit.getCount());
                                if (add > 0) {
                                    inChest.grow(add);
                                    toDeposit.shrink(add);
                                    if (toDeposit.isEmpty()) break;
                                }
                            }
                        }

                        // If chest was full, return remainder to bot
                        if (!toDeposit.isEmpty()) {
                            stack.grow(toDeposit.getCount());
                        }
                    }

                    this.bot.inventory.removeIf(ItemStack::isEmpty);
                    this.bot.updateItemCount();

                    this.bot.level().playSound(null, this.targetChest.getX(), this.targetChest.getY(), this.targetChest.getZ(),
                        SoundEvents.CHEST_CLOSE, SoundSource.BLOCKS, 0.8f, 1.2f);

                    if (this.bot.level() instanceof ServerLevel serverLevel) {
                        serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER, this.bot.getX(), this.bot.getY() + 0.5, this.bot.getZ(), 6, 0.2, 0.2, 0.2, 0.05);
                    }
                }
                this.targetChest = null;
                this.bot.getNavigation().stop();
            }
        }
    }

    /** AI Goal: Wandering */
    static class ChestBoyWanderGoal extends Goal {
        private final ChestBoyEntity bot;
        private double headingRad = 0;
        private int walkTicks = 0;

        public ChestBoyWanderGoal(ChestBoyEntity bot) {
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
