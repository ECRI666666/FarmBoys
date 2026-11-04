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
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * HarvesterBot: The energetic crop cutter!
 * Patrols farmlands, cleanly cuts fully grown wheat, carrots, potatoes, and beetroot,
 * instantly replants the fresh seedlings, and sometimes recoils backward in comical surprise!
 */
public class HarvesterBotEntity extends PathfinderMob {
    public static final EntityDataAccessor<Boolean> CUTTING = SynchedEntityData.defineId(HarvesterBotEntity.class, EntityDataSerializers.BOOLEAN);
    public static final EntityDataAccessor<Boolean> RECOIL = SynchedEntityData.defineId(HarvesterBotEntity.class, EntityDataSerializers.BOOLEAN);

    private int recoilTimer = 0;
    private int cuttingTicks = 0;

    public HarvesterBotEntity(EntityType<? extends PathfinderMob> type, Level level) {
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
        builder.define(CUTTING, false);
        builder.define(RECOIL, false);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new HarvestCropsGoal(this));
        this.goalSelector.addGoal(2, new HarvesterWanderGoal(this));
    }

    public boolean isCutting() {
        return this.entityData.get(CUTTING);
    }

    public boolean isRecoiling() {
        return this.entityData.get(RECOIL);
    }

    @Override
    public void tick() {
        super.tick();

        if (recoilTimer > 0) {
            recoilTimer--;
            if (recoilTimer == 0) {
                this.entityData.set(RECOIL, false);
            }
        }

        if (cuttingTicks > 0) {
            cuttingTicks--;
            if (cuttingTicks == 0) {
                this.entityData.set(CUTTING, false);
            }
        }
    }

    public void triggerRecoil() {
        this.recoilTimer = 20;
        this.entityData.set(RECOIL, true);
        this.getNavigation().stop();

        // Push backward comically
        float yawRad = (float) Math.toRadians(this.getYRot());
        this.setDeltaMovement(Math.sin(yawRad) * 0.25, 0.2, -Math.cos(yawRad) * 0.25);

        this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
            SoundEvents.DISPENSER_FAIL, SoundSource.NEUTRAL, 0.8f, 1.4f);

        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, this.getX(), this.getY() + 0.2, this.getZ(), 6, 0.15, 0.1, 0.15, 0.02);
        }
    }

    public void harvestCrop(BlockPos cropPos) {
        if (this.level().isClientSide()) return;
        BlockState state = this.level().getBlockState(cropPos);

        boolean isCrop = state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state);
        boolean isWart = state.is(Blocks.NETHER_WART) && state.getValue(NetherWartBlock.AGE) >= 3;

        if (!isCrop && !isWart) return;

        this.cuttingTicks = 15;
        this.entityData.set(CUTTING, true);

        // Sound & cutting particles
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
            SoundEvents.SHEEP_SHEAR, SoundSource.NEUTRAL, 0.85f, 1.4f);
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
            SoundEvents.CROP_BREAK, SoundSource.BLOCKS, 0.8f, 1.2f);

        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER, cropPos.getX() + 0.5, cropPos.getY() + 0.3, cropPos.getZ() + 0.5, 8, 0.2, 0.1, 0.2, 0.05);

            // Destroy and drop items on ground for ChestBot
            this.level().destroyBlock(cropPos, true);

            // Replant seedling immediately
            if (isCrop && state.getBlock() instanceof CropBlock crop) {
                this.level().setBlock(cropPos, crop.getStateForAge(0), 3);
            } else if (isWart) {
                this.level().setBlock(cropPos, Blocks.NETHER_WART.defaultBlockState(), 3);
            }
        }

        // 8% chance to comically recoil backward from mechanical momentum
        if (this.random.nextFloat() < 0.08f) {
            triggerRecoil();
        }
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand == InteractionHand.MAIN_HAND && !this.level().isClientSide()) {
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.SHEEP_SHEAR, SoundSource.PLAYERS, 0.8f, 1.5f);
            return InteractionResult.SUCCESS;
        }
        return super.mobInteract(player, hand);
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (!this.level().isClientSide()) {
            ItemEntity dropItem = new ItemEntity(this.level(), this.getX(), this.getY() + 0.1, this.getZ(), new ItemStack(ModItems.HARVESTER_BOT.get()));
            this.level().addFreshEntity(dropItem);
        }
    }

    /** AI Goal: searches for fully grown crops to harvest */
    static class HarvestCropsGoal extends Goal {
        private final HarvesterBotEntity bot;
        private BlockPos targetCrop;
        private int searchDelay = 0;
        private final Map<BlockPos, Integer> cooldowns = new HashMap<>();

        public HarvestCropsGoal(HarvesterBotEntity bot) {
            this.bot = bot;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (this.bot.isRecoiling()) return false;
            if (searchDelay > 0) {
                searchDelay--;
                return false;
            }
            searchDelay = 10;

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
                        boolean isReady = (state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state))
                                || (state.is(Blocks.NETHER_WART) && state.getValue(NetherWartBlock.AGE) >= 3);

                        if (isReady) {
                            candidates.add(check);
                        }
                    }
                }
            }

            if (candidates.isEmpty()) return false;
            candidates.sort((a, b) -> Double.compare(origin.distSqr(a), origin.distSqr(b)));

            for (int i = 0; i < Math.min(3, candidates.size()); i++) {
                BlockPos candidate = candidates.get(i);
                Path path = this.bot.getNavigation().createPath(candidate, 1);
                if (path != null && path.canReach()) {
                    this.targetCrop = candidate;
                    return true;
                } else {
                    this.cooldowns.put(candidate, now + 300);
                }
            }
            return false;
        }

        @Override
        public boolean canContinueToUse() {
            if (this.bot.isRecoiling() || this.targetCrop == null) return false;
            BlockState state = this.bot.level().getBlockState(this.targetCrop);
            return (state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state))
                    || (state.is(Blocks.NETHER_WART) && state.getValue(NetherWartBlock.AGE) >= 3);
        }

        @Override
        public void tick() {
            if (this.targetCrop == null) return;
            this.bot.getLookControl().setLookAt(this.targetCrop.getX() + 0.5, this.targetCrop.getY() + 0.3, this.targetCrop.getZ() + 0.5, 30.0f, 30.0f);
            this.bot.getNavigation().moveTo(this.targetCrop.getX() + 0.5, this.targetCrop.getY(), this.targetCrop.getZ() + 0.5, 1.15);

            double distSq = this.bot.distanceToSqr(Vec3.atCenterOf(this.targetCrop));
            if (distSq < 2.5) {
                this.bot.harvestCrop(this.targetCrop);
                this.cooldowns.put(this.targetCrop, this.bot.tickCount + 300);
                this.targetCrop = null;
                this.bot.getNavigation().stop();
            }
        }
    }

    /** AI Goal: Driving around garden beds */
    static class HarvesterWanderGoal extends Goal {
        private final HarvesterBotEntity bot;
        private double headingRad = 0;
        private int driveTicks = 0;

        public HarvesterWanderGoal(HarvesterBotEntity bot) {
            this.bot = bot;
            this.setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return !this.bot.isRecoiling();
        }

        @Override
        public void start() {
            this.headingRad = Math.toRadians(this.bot.getYRot());
            this.driveTicks = 30 + this.bot.getRandom().nextInt(50);
        }

        @Override
        public void tick() {
            if (this.bot.isRecoiling()) return;

            driveTicks--;
            if (this.bot.horizontalCollision || driveTicks <= 0) {
                double turn = (0.4 + this.bot.getRandom().nextDouble() * 0.6) * Math.PI;
                if (this.bot.getRandom().nextBoolean()) turn = -turn;
                this.headingRad += turn;
                this.driveTicks = 40 + this.bot.getRandom().nextInt(60);
                this.bot.setYRot((float) Math.toDegrees(this.headingRad));
            }

            double tx = this.bot.getX() + Math.sin(-this.headingRad) * 3.0;
            double tz = this.bot.getZ() + Math.cos(-this.headingRad) * 3.0;
            this.bot.getNavigation().moveTo(tx, this.bot.getY(), tz, 1.0);
        }
    }
}
