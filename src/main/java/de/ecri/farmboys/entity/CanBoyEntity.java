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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * CanBoy: The adorable walking watering can!
 * Waddles across farmlands, hydrates dry soil, gives occasional growth boosts to crops,
 * and occasionally slips on its own puddles in comical fashion!
 */
public class CanBoyEntity extends PathfinderMob {
    public static final EntityDataAccessor<Boolean> WATERING = SynchedEntityData.defineId(CanBoyEntity.class, EntityDataSerializers.BOOLEAN);
    public static final EntityDataAccessor<Boolean> CLUMSY_SLIP = SynchedEntityData.defineId(CanBoyEntity.class, EntityDataSerializers.BOOLEAN);

    private int clumsyTimer = 0;
    private int waterAnimationTicks = 0;

    public CanBoyEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
            .add(Attributes.MAX_HEALTH, 15.0)
            .add(Attributes.MOVEMENT_SPEED, 0.22)
            .add(Attributes.STEP_HEIGHT, 1.0)
            .add(Attributes.FOLLOW_RANGE, 24.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(WATERING, false);
        builder.define(CLUMSY_SLIP, false);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new WaterCropsGoal(this));
        this.goalSelector.addGoal(2, new CanBoyWanderGoal(this));
    }

    public boolean isWatering() {
        return this.entityData.get(WATERING);
    }

    public boolean isClumsySlipping() {
        return this.entityData.get(CLUMSY_SLIP);
    }

    @Override
    public void tick() {
        super.tick();

        if (clumsyTimer > 0) {
            clumsyTimer--;
            this.setYRot(this.getYRot() + 25.0f); // Spin comically!
            if (!this.level().isClientSide() && this.level() instanceof ServerLevel serverLevel) {
                if (clumsyTimer % 3 == 0) {
                    serverLevel.sendParticles(ParticleTypes.SPLASH, this.getX(), this.getY() + 0.1, this.getZ(), 4, 0.2, 0.1, 0.2, 0.1);
                }
            }
            if (clumsyTimer == 0) {
                this.entityData.set(CLUMSY_SLIP, false);
            }
        }

        if (waterAnimationTicks > 0) {
            waterAnimationTicks--;
            if (waterAnimationTicks == 0) {
                this.entityData.set(WATERING, false);
            }
        }
    }

    public void triggerClumsySlip() {
        this.clumsyTimer = 24;
        this.entityData.set(CLUMSY_SLIP, true);
        this.getNavigation().stop();
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
            SoundEvents.GENERIC_SPLASH, SoundSource.NEUTRAL, 0.8f, 1.6f);
    }

    public void waterFarmland(BlockPos farmPos) {
        if (this.level().isClientSide()) return;
        BlockState state = this.level().getBlockState(farmPos);
        if (!state.is(Blocks.FARMLAND)) return;

        this.waterAnimationTicks = 20;
        this.entityData.set(WATERING, true);

        // Hydrate soil
        this.level().setBlock(farmPos, state.setValue(FarmlandBlock.MOISTURE, FarmlandBlock.MAX_MOISTURE), 3);

        this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
            SoundEvents.GENERIC_SPLASH, SoundSource.NEUTRAL, 0.6f, 1.5f);

        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.SPLASH, farmPos.getX() + 0.5, farmPos.getY() + 1.0, farmPos.getZ() + 0.5, 8, 0.2, 0.1, 0.2, 0.05);
            serverLevel.sendParticles(ParticleTypes.DRIPPING_WATER, farmPos.getX() + 0.5, farmPos.getY() + 1.1, farmPos.getZ() + 0.5, 4, 0.2, 0.05, 0.2, 0.01);

            // Occasional gentle crop booster
            BlockPos cropPos = farmPos.above();
            BlockState cropState = this.level().getBlockState(cropPos);
            if (cropState.getBlock() instanceof CropBlock crop && !crop.isMaxAge(cropState)) {
                if (this.random.nextFloat() < 0.35f) {
                    crop.growCrops(serverLevel, cropPos, cropState);
                    serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER, cropPos.getX() + 0.5, cropPos.getY() + 0.3, cropPos.getZ() + 0.5, 5, 0.2, 0.1, 0.2, 0.05);
                }
            }
        }

        // 7% chance to comically slip on its fresh water puddle
        if (this.random.nextFloat() < 0.07f) {
            triggerClumsySlip();
        }
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand == InteractionHand.MAIN_HAND && !this.level().isClientSide()) {
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.WATER_AMBIENT, SoundSource.PLAYERS, 0.8f, 1.3f);
            if (this.level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.SPLASH, this.getX(), this.getY() + 0.5, this.getZ(), 8, 0.2, 0.2, 0.2, 0.05);
            }
            return InteractionResult.SUCCESS;
        }
        return super.mobInteract(player, hand);
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (!this.level().isClientSide()) {
            ItemEntity dropItem = new ItemEntity(this.level(), this.getX(), this.getY() + 0.1, this.getZ(), new ItemStack(ModItems.CAN_BOT.get()));
            this.level().addFreshEntity(dropItem);
        }
    }

    /** AI Goal: searches for unhydrated farmland or growing crops to water */
    static class WaterCropsGoal extends Goal {
        private final CanBoyEntity bot;
        private BlockPos targetFarmland;
        private int searchDelay = 0;
        private final Map<BlockPos, Integer> cooldowns = new HashMap<>();

        public WaterCropsGoal(CanBoyEntity bot) {
            this.bot = bot;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (this.bot.isClumsySlipping()) return false;
            if (searchDelay > 0) {
                searchDelay--;
                return false;
            }
            searchDelay = 12;

            int now = this.bot.tickCount;
            this.cooldowns.entrySet().removeIf(e -> now > e.getValue());

            BlockPos origin = this.bot.blockPosition();
            List<BlockPos> candidates = new ArrayList<>();

            for (int dx = -14; dx <= 14; dx += 2) {
                for (int dz = -14; dz <= 14; dz += 2) {
                    for (int dy = -2; dy <= 1; dy++) {
                        BlockPos check = origin.offset(dx, dy, dz);
                        if (this.cooldowns.containsKey(check)) continue;

                        BlockState state = this.bot.level().getBlockState(check);
                        if (state.is(Blocks.FARMLAND)) {
                            int moisture = state.getValue(FarmlandBlock.MOISTURE);
                            BlockState above = this.bot.level().getBlockState(check.above());
                            boolean hasGrowingCrop = above.getBlock() instanceof CropBlock crop && !crop.isMaxAge(above);

                            if (moisture < FarmlandBlock.MAX_MOISTURE || hasGrowingCrop) {
                                candidates.add(check);
                            }
                        }
                    }
                }
            }

            if (candidates.isEmpty()) return false;
            candidates.sort((a, b) -> Double.compare(origin.distSqr(a), origin.distSqr(b)));

            for (int i = 0; i < Math.min(3, candidates.size()); i++) {
                BlockPos candidate = candidates.get(i);
                Path path = this.bot.getNavigation().createPath(candidate.above(), 1);
                if (path != null && path.canReach()) {
                    this.targetFarmland = candidate;
                    return true;
                } else {
                    this.cooldowns.put(candidate, now + 300);
                }
            }
            return false;
        }

        @Override
        public boolean canContinueToUse() {
            return !this.bot.isClumsySlipping() && this.targetFarmland != null && this.bot.level().getBlockState(this.targetFarmland).is(Blocks.FARMLAND);
        }

        @Override
        public void tick() {
            if (this.targetFarmland == null) return;
            this.bot.getLookControl().setLookAt(this.targetFarmland.getX() + 0.5, this.targetFarmland.getY() + 0.5, this.targetFarmland.getZ() + 0.5, 30.0f, 30.0f);
            this.bot.getNavigation().moveTo(this.targetFarmland.getX() + 0.5, this.targetFarmland.getY() + 1.0, this.targetFarmland.getZ() + 0.5, 1.15);

            double distSq = this.bot.distanceToSqr(Vec3.atCenterOf(this.targetFarmland.above()));
            if (distSq < 2.8) {
                this.bot.waterFarmland(this.targetFarmland);
                this.cooldowns.put(this.targetFarmland, this.bot.tickCount + 400); // 20s cooldown per block
                this.targetFarmland = null;
                this.bot.getNavigation().stop();
            }
        }
    }

    /** AI Goal: Clumsy waddle wandering around garden */
    static class CanBoyWanderGoal extends Goal {
        private final CanBoyEntity bot;
        private double headingRad = 0;
        private int walkTicks = 0;

        public CanBoyWanderGoal(CanBoyEntity bot) {
            this.bot = bot;
            this.setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return !this.bot.isClumsySlipping();
        }

        @Override
        public void start() {
            this.headingRad = Math.toRadians(this.bot.getYRot());
            this.walkTicks = 30 + this.bot.getRandom().nextInt(50);
        }

        @Override
        public void tick() {
            if (this.bot.isClumsySlipping()) return;

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
