package com.SoloLevelingSystem.storage;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class EntityStorage {
    private static final Logger LOGGER = LoggerFactory.getLogger(EntityStorage.class);
    private static final Map<UUID, List<LivingEntity>> playerEntities = new HashMap<>();

    public static void storeEntity(UUID playerId, LivingEntity entity) {
        LOGGER.debug("Storing entity for player: {}", playerId);
        playerEntities.computeIfAbsent(playerId, k -> new ArrayList<>()).add(entity);
    }

    public static List<LivingEntity> getStoredEntities(UUID playerId) {
        return playerEntities.getOrDefault(playerId, new ArrayList<>());
    }

    public static boolean hasEntities(UUID playerId) {
        boolean hasEntities = playerEntities.containsKey(playerId) && !playerEntities.get(playerId).isEmpty();
        LOGGER.debug("Player {} has entities: {}", playerId, hasEntities);
        return hasEntities;
    }

    public static void spawnEntities(ServerPlayer player) {
        UUID playerId = player.getUUID();
        LOGGER.debug("Attempting to spawn entities for player: {}", playerId);
        if (hasEntities(playerId)) {
            ServerLevel world = player.serverLevel();
            List<LivingEntity> entitiesToSpawn = getStoredEntities(playerId);
            for (LivingEntity entity : entitiesToSpawn) {
                entity.setPos(player.getX(), player.getY(), player.getZ());
                world.addFreshEntity(entity);
                LOGGER.debug("Spawned entity: {}", entity);
            }
            playerEntities.remove(playerId);
        } else {
            LOGGER.debug("No entities to spawn for player: {}", playerId);
        }
    }
}