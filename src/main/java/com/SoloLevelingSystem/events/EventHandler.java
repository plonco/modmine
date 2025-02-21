package com.SoloLevelingSystem.events;

import com.SoloLevelingSystem.SoloLevelingSystem;
import com.SoloLevelingSystem.configs.ConfigManager;
import com.SoloLevelingSystem.storage.EntityStorage;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod.EventBusSubscriber(modid = SoloLevelingSystem.MODID)
public class EventHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(EventHandler.class);

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof LivingEntity)) {
            return;
        }

        LivingEntity entity = (LivingEntity) event.getEntity();
        ResourceLocation entityId = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        DamageSource source = event.getSource();

        LOGGER.debug("Entity died: {}", entityId);

        // Check if the entity is a target enemy
        if (ConfigManager.isNormalEnemy(entityId) || ConfigManager.isMinibossEnemy(entityId) || ConfigManager.isBossEnemy(entityId)) {
            // Check if the killer is a player
            if (source.getEntity() instanceof Player) {
                Player player = (Player) source.getEntity();
                // Create a new instance of the entity to store
                Entity newEntity = entity.getType().create(player.level);
                if (newEntity instanceof LivingEntity) {
                    LivingEntity entityToStore = (LivingEntity) newEntity;
                    // Store the entity in the player's storage
                    EntityStorage.storeEntity(player.getUUID(), entityToStore);
                    LOGGER.debug("Stored entity for player: {} - {}", player.getUUID(), entityId);
                } else {
                    LOGGER.debug("Failed to create a new instance of the entity to store");
                }
            } else {
                LOGGER.debug("Entity not killed by player, source: {}", source.getEntity());
            }
        }
    }
}