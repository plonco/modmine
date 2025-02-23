package com.SoloLevelingSystem.ai;

import com.SoloLevelingSystem.storage.PlayerTargetStorage;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.MoverType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumSet;
import java.util.List;

public class FollowOwnerAndAttackTargetGoal extends Goal {
    private static final Logger LOGGER = LoggerFactory.getLogger(FollowOwnerAndAttackTargetGoal.class);
    private final LivingEntity entity;
    private final Player owner;
    private LivingEntity target;
    private final int priority;
    private int attackDelay = 0;
    private final TargetingConditions targetingConditions = TargetingConditions.forCombat().ignoreLineOfSight().ignoreInvisibilityTesting();
    private int canUseCooldown = 0;
    private static final int MAX_COOLDOWN = 20;
    private final double followDistance = 2.0D; // Distance at which the entity will follow the owner
    private final double speedModifier;

    public FollowOwnerAndAttackTargetGoal(LivingEntity entity, Player owner, int priority) {
        this.entity = entity;
        this.owner = owner;
        this.priority = priority;
        this.speedModifier = entity.getAttributeValue(Attributes.MOVEMENT_SPEED) ;
        this.setFlags(EnumSet.of(Goal.Flag.TARGET, Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        //LOGGER.debug("FollowOwnerAndAttackTargetGoal.canUse() called");

        LivingEntity playerTarget = PlayerTargetStorage.getPlayerTarget(owner.getUUID());
        //LOGGER.debug("owner.getPlayerTarget(): {}", playerTarget);
        if (playerTarget != null) {
            LivingEntity potentialTarget = playerTarget;
            if (potentialTarget != owner && isValidTarget(potentialTarget)) {
                // LOGGER.debug("Targeting owner's attacker: {}", potentialTarget);
                this.target = potentialTarget;
                return true;
            }
        }

        // If the owner isn't attacking anything, find a nearby enemy
        AABB searchArea = entity.getBoundingBox().inflate(16.0D, 4.0D, 16.0D);
        List<LivingEntity> nearbyEnemies = entity.level().getNearbyEntities(LivingEntity.class, targetingConditions, entity, searchArea);
        // LOGGER.debug("Number of nearby enemies: {}", nearbyEnemies.size());

        if (!nearbyEnemies.isEmpty()) {
            for (LivingEntity potentialTarget : nearbyEnemies) {
                if (potentialTarget != owner && isValidTarget(potentialTarget)) {
                    // LOGGER.debug("Targeting nearby enemy: {}", potentialTarget);
                    this.target = potentialTarget;
                    return true;
                }
            }
        }

        // If there's nothing to attack, follow the owner
        double distanceToOwner = entity.distanceToSqr(owner);
        // LOGGER.debug("Distance to owner: {}", distanceToOwner);
        boolean shouldFollow = distanceToOwner > (double)(this.followDistance * this.followDistance);
        // LOGGER.debug("Should follow owner: {}", shouldFollow);
        if (shouldFollow) {
            return true;
        }
        return false;
    }

    @Override
    public boolean canContinueToUse() {
        LOGGER.debug("FollowOwnerAndAttackTargetGoal.canContinueToUse() called for: {}", entity);
        boolean canContinue = !entity.isRemoved() && owner.isAlive() && isValidTarget(this.target);
        LOGGER.debug("Can continue: {}, target: {}", canContinue, target);
        return canContinue;
    }

    @Override
    public void start() {
        // set the speed of the entity to the speed of the owner
        PathNavigation navigation = ((Mob)entity).getNavigation();
        navigation.setSpeedModifier(this.speedModifier);
        LOGGER.debug("FollowOwnerAndAttackTargetGoal.start() called for: {}", entity);
        // Log the speed modifier
        LOGGER.debug("Speed modifier: {}", this.speedModifier);
    }

    @Override
    public void tick() {
        //LOGGER.debug("FollowOwnerAndAttackTargetGoal.tick() called for: {}", entity);
        if (attackDelay > 0) {
            attackDelay--;
            return;
        }

        if (this.target != null && this.entity instanceof Mob) {
            try {
                Mob mobEntity = (Mob) entity;
                LOGGER.debug("Current target: {} for entity: {}", this.target, entity);

                // Check if the target is still alive
                if (this.target.isAlive()) {
                    // Check if the entity can pathfind to the target
                    PathNavigation navigation = mobEntity.getNavigation();
                    Path path = navigation.createPath(this.target, 0);
                    if (path != null) {
                        mobEntity.setTarget(this.target);
                        // set the speed of the entity to the speed of the owner
                        navigation.setSpeedModifier(this.speedModifier);
                        mobEntity.doHurtTarget(this.target); // Add this line
                        // Force movement
                        Vec3 moveVec = new Vec3(0.1D, 0.0D, 0.0D); // Move 0.1 blocks in the x direction
                        mobEntity.move(MoverType.SELF, moveVec);
                        LOGGER.debug("Forced movement for {}", mobEntity);
                    } else {
                        LOGGER.debug("Cannot pathfind to target: {} for entity: {}", this.target, entity);
                    }


                } else {
                    LOGGER.debug("Target is not alive: {} for entity: {}", this.target, entity);
                    mobEntity.setTarget(null);
                    this.target = null; // Clear the target
                }


            } catch (Exception e) {
                LOGGER.error("Error setting target for entity {}: {}", entity, e);
            }
            attackDelay = 10; // Reduce attackDelay
        } else {
            // Follow the owner
            double distanceToOwner = entity.distanceToSqr(owner);
            if (distanceToOwner > (double)(this.followDistance * this.followDistance)) {
                PathNavigation navigation = ((Mob)entity).getNavigation();
                navigation.moveTo(owner.getX(), owner.getY(), owner.getZ(), this.speedModifier);
            }
        }
    }

    @Override
    public void stop() {
        if (this.entity instanceof Mob) {
            ((Mob) this.entity).setTarget(null);
            PathNavigation navigation = ((Mob)entity).getNavigation();
            navigation.stop();
            LOGGER.debug("Stopped following/attacking, target is null for: {}", entity);
        }
    }

    private boolean isValidTarget(LivingEntity target) {
        if (target == null) {
            return false;
        }
        if (!(target instanceof Mob)) {
            return false;
        }
        if (target == owner) {
            return false;
        }
        return true;
    }
}