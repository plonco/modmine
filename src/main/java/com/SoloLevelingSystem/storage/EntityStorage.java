package com.SoloLevelingSystem.storage;

import com.SoloLevelingSystem.configs.ConfigManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.SoloLevelingSystem.events.EventHandler;
import net.minecraft.nbt.ListTag;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.*;

import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import com.SoloLevelingSystem.storage.PlayerTargetStorage;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.pathfinder.Path;
import com.SoloLevelingSystem.ai.FollowOwnerAndAttackTargetGoal;
import net.minecraft.world.level.Level;
import net.minecraft.server.MinecraftServer;

@Mod.EventBusSubscriber
public class EntityStorage {
    private static final Logger LOGGER = LoggerFactory.getLogger(EntityStorage.class);
    private static Map<UUID, Map<ResourceLocation, List<CompoundTag>>> playerEntities = new HashMap<>();
    private static Map<UUID, List<Entity>> spawnedEntities = new HashMap<>(); // Track spawned entities
    private static final Set<UUID> summonedEntities = new HashSet<>();

    private static final int MAX_NORMAL_ENEMIES = 10;
    private static final int MAX_MINIBOSSES = 1;
    private static final int MAX_BOSSES = 1;
    private static final String DATA_FILE_EXTENSION = ".soloLeveling";
    private static final Random RANDOM = new Random();
    private static final double SPAWN_RADIUS = 2.0;


    public static void storeEntity(UUID playerUUID, Entity entity, CompoundTag entityData) {
        ResourceLocation entityResourceLocation = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());

        if (entityResourceLocation == null) {
            LOGGER.error("Entity Resource Location is null for entity: {}", entity);
            return;
        }

        Map<ResourceLocation, List<CompoundTag>> playerEntityMap = playerEntities.computeIfAbsent(playerUUID, k -> new HashMap<>());
        List<CompoundTag> entityList = playerEntityMap.computeIfAbsent(entityResourceLocation, k -> new ArrayList<>());

        int maxEntities = 0;
        if (ConfigManager.isNormalEnemy(entityResourceLocation)) {
            maxEntities = MAX_NORMAL_ENEMIES;
        } else if (ConfigManager.isMinibossEnemy(entityResourceLocation)) {
            maxEntities = MAX_MINIBOSSES;
        } else if (ConfigManager.isBossEnemy(entityResourceLocation)) {
            maxEntities = MAX_BOSSES;
        }

        if (entityList.size() >= maxEntities) {
            LOGGER.warn("Player {} has reached the maximum entity storage limit for entity type {}.", playerUUID, entityResourceLocation);
            return;
        }

        entityList.add(entityData);
        LOGGER.debug("Storing entity data for player: {} - {}", playerUUID, entityResourceLocation);

        // Get the level from the entity
        if (entity.level() instanceof ServerLevel) {
            ServerLevel level = (ServerLevel) entity.level();
            savePlayerData(level, playerUUID); // Save immediately after storing
        } else {
            LOGGER.error("Cannot get the ServerLevel to save the data.");
        }
    }

    public static void spawnStoredEntities(Player player) {
        UUID playerUUID = player.getUUID();
        LOGGER.debug("Attempting to spawn entities for player: {}", playerUUID);

        ServerLevel serverLevel = (ServerLevel) player.level();

        if (spawnedEntities.containsKey(playerUUID) && !spawnedEntities.get(playerUUID).isEmpty()) {
            LOGGER.debug("Player {} has existing spawned entities. Removing and respawning.", playerUUID);

            // 1. Remove existing entities and save their NBT data
            List<Entity> existingEntities = spawnedEntities.get(playerUUID);
            List<CompoundTag> savedEntityData = new ArrayList<>();
            for (Entity entity : existingEntities) {
                CompoundTag entityData = new CompoundTag();
                entity.save(entityData);
                savedEntityData.add(entityData);
                entity.remove(Entity.RemovalReason.DISCARDED);
                LOGGER.debug("Removed existing entity and saved NBT data: {}", entity);
            }
            spawnedEntities.remove(playerUUID);

        } else {
            LOGGER.debug("Player {} has no existing spawned entities. Spawning from stored data.", playerUUID);

            if (playerEntities.containsKey(playerUUID) && !playerEntities.get(playerUUID).isEmpty()) {
                Map<ResourceLocation, List<CompoundTag>> playerEntityMap = playerEntities.get(playerUUID);
                List<Entity> currentSpawnedEntities = new ArrayList<>();

                for (Map.Entry<ResourceLocation, List<CompoundTag>> entry : playerEntityMap.entrySet()) {
                    ResourceLocation entityResourceLocation = entry.getKey();
                    List<CompoundTag> entitiesToSpawn = entry.getValue();
                    LOGGER.debug("Spawning entities of type {}: {}", entityResourceLocation, entitiesToSpawn.size());

                    for (CompoundTag entityData : entitiesToSpawn) {
                        try {
                            EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.get(entityResourceLocation);
                            if (entityType == null) {
                                LOGGER.error("Could not find entity type {} in registry", entityResourceLocation);
                                continue;
                            }
                            Entity entity = entityType.create(serverLevel);
                            if (entity == null) {
                                LOGGER.error("Failed to create entity of type {}", entityType);
                                continue;
                            }
                            entity.load(entityData);

                            // Generate a new UUID for the entity
                            entity.setUUID(UUID.randomUUID());

                            double x = player.getX() + (RANDOM.nextDouble() * 2 - 1) * SPAWN_RADIUS;
                            double y = player.getY();
                            double z = player.getZ() + (RANDOM.nextDouble() * 2 - 1) * SPAWN_RADIUS;

                            entity.setPos(x, y, z);
                            // Force position update after spawning
                            entity.xOld = x;
                            entity.yOld = y;
                            entity.zOld = z;

                            serverLevel.addFreshEntity(entity);

                            if (entity instanceof Mob) {
                                Mob mobEntity = (Mob) entity;
                                LOGGER.debug("Setting up AI for spawned entity: {}", mobEntity);

                                // Check and set movement speed
                                if (mobEntity.getAttributeValue(Attributes.MOVEMENT_SPEED) == 0) {
                                    mobEntity.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.25D); // Set a default speed
                                    LOGGER.warn("Movement speed was 0 for {}, setting to 0.25", mobEntity);
                                }

                                // Set persistence required
                                mobEntity.setPersistenceRequired();

                                // Clear any existing targeting goals (THIS IS IMPORTANT)
                                mobEntity.targetSelector.getAvailableGoals().forEach(mobEntity.targetSelector::removeGoal);
                                mobEntity.goalSelector.getAvailableGoals().forEach(mobEntity.goalSelector::removeGoal);

                                // Add the FollowOwnerAndAttackTargetGoal
                                FollowOwnerAndAttackTargetGoal followGoal = new FollowOwnerAndAttackTargetGoal(mobEntity, player, 2);
                                mobEntity.goalSelector.addGoal(1, followGoal);
                                LOGGER.debug("Added FollowOwnerAndAttackTargetGoal to {}", mobEntity);

                                // Add a NearestAttackableTargetGoal as a fallback
                                NearestAttackableTargetGoal<LivingEntity> attackTargetGoal = new NearestAttackableTargetGoal<>(mobEntity, LivingEntity.class, 10, false, false, (livingEntity) -> livingEntity != player);
                                mobEntity.targetSelector.addGoal(2, attackTargetGoal);
                                LOGGER.debug("Added NearestAttackableTargetGoal to {}", mobEntity);

                                // Add a wandering goal, but ONLY if the entity is a PathfinderMob)
                                if (mobEntity instanceof PathfinderMob) {
                                    PathfinderMob pathfinderMob = (PathfinderMob) mobEntity;
                                    RandomStrollGoal strollGoal = new RandomStrollGoal(pathfinderMob, 1.0D);
                                    mobEntity.goalSelector.addGoal(3, strollGoal);
                                    LOGGER.debug("Added RandomStrollGoal to {}", mobEntity);
                                } else {
                                    LOGGER.warn("Entity {} is not a PathfinderMob, cannot add RandomStrollGoal", mobEntity);
                                }

                                LOGGER.debug("Added FollowOwnerAndAttackTargetGoal and NearestAttackableTargetGoal to {}", mobEntity);

                            } else {
                                LOGGER.warn("Entity {} is not a Mob, cannot setup AI", entity);
                            }
                            LOGGER.debug("Spawned entity: {}", entity);
                            currentSpawnedEntities.add(entity);
                            summonedEntities.add(entity.getUUID());

                        } catch (Exception e) {
                            LOGGER.error("Failed to spawn entity", e);
                        }
                    }
                }
                spawnedEntities.put(playerUUID, currentSpawnedEntities);
            } else {
                LOGGER.debug("Player {} has no entities stored.", playerUUID);
            }
        }
    }


    public static boolean hasEntities(UUID playerUUID) {
        return playerEntities.containsKey(playerUUID) && !playerEntities.get(playerUUID).isEmpty();
    }

    public static void clearEntities(UUID playerUUID) {
        playerEntities.remove(playerUUID);
        summonedEntities.removeIf(uuid -> spawnedEntities.values().stream()
                .flatMap(List::stream)
                .noneMatch(entity -> entity.getUUID().equals(uuid)));
        clearSpawnedEntities(playerUUID); // Also clear spawned entities when clearing stored entities
        LOGGER.debug("Cleared entities for player: {}", playerUUID);
    }

    public static List<Entity> getPlayerEntities(UUID playerUUID) {
        List<Entity> allEntities = new ArrayList<>();
        if (spawnedEntities.containsKey(playerUUID)) {
            allEntities.addAll(spawnedEntities.get(playerUUID));
        }
        return allEntities;
    }

    // New method to clear spawned entities
    public static void clearSpawnedEntities(UUID playerUUID) {
        if (spawnedEntities.containsKey(playerUUID)) {
            List<Entity> entitiesToRemove = spawnedEntities.get(playerUUID);
            for (Entity entity : entitiesToRemove) {
                summonedEntities.remove(entity.getUUID());
                entity.remove(Entity.RemovalReason.DISCARDED); // Remove the entity from the world
                LOGGER.debug("Removed entity: {}", entity);
            }
            spawnedEntities.remove(playerUUID);
            LOGGER.debug("Cleared spawned entities for player: {}", playerUUID);
        } else {
            LOGGER.debug("No spawned entities to clear for player: {}", playerUUID);
        }
    }

    public static boolean isSummonedEntity(UUID entityUUID) {
        return summonedEntities.contains(entityUUID);
    }

    private static File getPlayerDataFile(ServerLevel level, UUID playerUUID) {
        MinecraftServer server = level.getServer();
        File worldDir = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.PLAYER_DATA_DIR).toFile();
        return new File(worldDir, playerUUID.toString() + DATA_FILE_EXTENSION);
    }

    private static void loadPlayerData(ServerLevel level, UUID playerUUID) {
        File dataFile = getPlayerDataFile(level, playerUUID);
        if (dataFile.exists()) {
            try (FileInputStream fileInputStream = new FileInputStream(dataFile)) {
                CompoundTag tag = net.minecraft.nbt.NbtIo.readCompressed(fileInputStream);
                LOGGER.debug("Loading player data from file: {} for player: {}", dataFile.getAbsolutePath(), playerUUID);
                LOGGER.debug("NBT data: {}", tag);

                if (tag.contains("playerEntities", 10)) {
                    CompoundTag playerEntitiesTag = tag.getCompound("playerEntities");
                    LOGGER.debug("playerEntitiesTag: {}", playerEntitiesTag);

                    Map<ResourceLocation, List<CompoundTag>> loadedEntities = new HashMap<>();
                    for (String entityTypeKey : playerEntitiesTag.getAllKeys()) {
                        ResourceLocation entityType = new ResourceLocation(entityTypeKey);
                        LOGGER.debug("Loading entity type: {}", entityType);

                        ListTag entityListTag = playerEntitiesTag.getList(entityTypeKey, 10);
                        LOGGER.debug("entityListTag: {}", entityListTag);

                        List<CompoundTag> entityList = new ArrayList<>();
                        for (int i = 0; i < entityListTag.size(); i++) {
                            CompoundTag entityTag = entityListTag.getCompound(i);
                            entityList.add(entityTag);
                            LOGGER.debug("Loaded entity: {}", entityTag);
                        }
                        loadedEntities.put(entityType, entityList);
                        LOGGER.debug("Loaded entities for type {}: {}", entityType, entityList);
                    }
                    playerEntities.put(playerUUID, loadedEntities);
                    LOGGER.debug("Loaded player data from file for player: {}", playerUUID);
                } else {
                    LOGGER.warn("No 'playerEntities' tag found in player data file for player: {}", playerUUID);
                }
            } catch (Exception e) {
                LOGGER.error("Error loading player data for player: {}", playerUUID, e);
            }
        } else {
            LOGGER.debug("No player data file found for player: {}", playerUUID);
        }
    }

    private static void savePlayerData(ServerLevel level, UUID playerUUID) {
        File dataFile = getPlayerDataFile(level, playerUUID);
        CompoundTag tag = new CompoundTag();
        CompoundTag playerEntitiesTag = new CompoundTag();

        if (playerEntities.containsKey(playerUUID)) {
            Map<ResourceLocation, List<CompoundTag>> entityMap = playerEntities.get(playerUUID);
            for (Map.Entry<ResourceLocation, List<CompoundTag>> entry : entityMap.entrySet()) {
                ResourceLocation entityType = entry.getKey();
                List<CompoundTag> entityList = entry.getValue();
                ListTag entityListTag = new ListTag();
                for (CompoundTag entityTag : entityList) {
                    entityListTag.add(entityTag);
                }
                playerEntitiesTag.put(entityType.toString(), entityListTag);
            }
        }
        tag.put("playerEntities", playerEntitiesTag);

        try (FileOutputStream fileOutputStream = new FileOutputStream(dataFile)) {
            net.minecraft.nbt.NbtIo.writeCompressed(tag, fileOutputStream);
            LOGGER.debug("Saved player data to file for player: {}", playerUUID);
        } catch (Exception e) {
            LOGGER.error("Error saving player data for player: {}", playerUUID, e);
        }
    }

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        Player player = event.getEntity();
        if (player instanceof Player) {
            ServerLevel level = (ServerLevel) event.getEntity().level();
            if (level != null) {
                loadPlayerData(level, player.getUUID());
                spawnStoredEntities(player);
            } else {
                LOGGER.error("Cannot get the level to load the data");
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLeave(PlayerEvent.PlayerLoggedOutEvent event) {
        Player player = event.getEntity();
        if (player instanceof Player) {
            ServerLevel level = (ServerLevel) event.getEntity().level();
            if (level != null) {
                savePlayerData(level, player.getUUID());
                clearSpawnedEntities(player.getUUID());
            } else {
                LOGGER.error("Cannot get the level to save the data");
            }

        }
    }

    // Custom AI Goal
    static class FollowOwnerAndAttackTargetGoal extends Goal {
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
            //LOGGER.debug("Distance to owner: {}", distanceToOwner);
            boolean shouldFollow = distanceToOwner > (double)(this.followDistance * this.followDistance);
            //LOGGER.debug("Should follow owner: {}", shouldFollow);
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
            //LOGGER.debug("FollowOwnerAndAttackTargetGoal.tick() called");
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
}