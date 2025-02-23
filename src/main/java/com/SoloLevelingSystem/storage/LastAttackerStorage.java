package com.SoloLevelingSystem.storage;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class LastAttackerStorage {
    private static final Map<UUID, UUID> lastAttackerMap = new HashMap<>();

    public static void setLastAttacker(LivingEntity entity, Player player) {
        lastAttackerMap.put(entity.getUUID(), player.getUUID());
    }

    public static UUID getLastAttacker(LivingEntity entity) {
        return lastAttackerMap.get(entity.getUUID());
    }

    public static void clearLastAttacker(LivingEntity entity) {
        lastAttackerMap.remove(entity.getUUID());
    }
}
