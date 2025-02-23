package com.SoloLevelingSystem.storage;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.world.entity.LivingEntity;

public class PlayerTargetStorage {
    private static final Map<UUID, LivingEntity> playerTargets = new HashMap<>();

    public static void setPlayerTarget(UUID playerUUID, LivingEntity target) {
        playerTargets.put(playerUUID, target);
    }

    public static LivingEntity getPlayerTarget(UUID playerUUID) {
        return playerTargets.get(playerUUID);
    }

    public static void clearPlayerTarget(UUID playerUUID) {
        playerTargets.remove(playerUUID);
    }
}