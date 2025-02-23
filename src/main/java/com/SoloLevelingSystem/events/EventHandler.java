package com.SoloLevelingSystem.events;

import com.SoloLevelingSystem.SoloLevelingSystem;
import com.SoloLevelingSystem.configs.ConfigManager;
import com.SoloLevelingSystem.attribute.ModAttributes;
import com.SoloLevelingSystem.network.ModMessages;
import com.SoloLevelingSystem.network.packets.SpawnParticlePacket;
import com.SoloLevelingSystem.storage.EntityStorage;
import com.SoloLevelingSystem.storage.PlayerTargetStorage;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.ItemAttributeModifierEvent;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber
public class EventHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(EventHandler.class);
    private static final Map<UUID, CompoundTag> preDeathData = new HashMap<>();

    @SubscribeEvent
    public static void onServerChatEvent(ServerChatEvent event) {
        LOGGER.info("Executing ServerChatEvent for {}", event.getUsername());
    }

    @SubscribeEvent
    public static void onAttachCapabilitiesEvent(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player) {
            LOGGER.info("Executing AttachCapabilitiesEvent for Player");
        }
    }

    @SubscribeEvent
    public static void onRegisterCapabilitiesEvent(RegisterCapabilitiesEvent event) {
        LOGGER.info("Executing RegisterCapabilitiesEvent");
    }

    @SubscribeEvent
    public static void onItemAttributeModifierEvent(ItemAttributeModifierEvent event) {
        ItemStack stack = event.getItemStack();
        UUID attributeUUID = UUID.fromString("91AEc26e-37CB-469F-A1D5-F01F0DF2586E");

        if (stack.getDisplayName().getString().contains("Solo Leveling Sword")) {
            AttributeModifier modifier = new AttributeModifier(attributeUUID, "solo_leveling_damage", 5, AttributeModifier.Operation.ADDITION);
            event.addModifier(ModAttributes.DAMAGE.get(), modifier);
        }
    }

    @SubscribeEvent
    public static void onEntityAttributeCreationEvent(EntityAttributeCreationEvent event) {
        LOGGER.info("Executing EntityAttributeCreationEvent");
    }

    @SubscribeEvent
    public static void onLivingHurtEvent(LivingHurtEvent event) {
        DamageSource source = event.getSource();
        Entity directEntity = source.getDirectEntity();

        if (directEntity instanceof Player player) {
            LivingEntity entity = event.getEntity();
            PlayerTargetStorage.setPlayerTarget(player.getUUID(), entity);
            LOGGER.debug("Set player target: {} - {}", player.getUUID(), entity);
        }
    }

    @SubscribeEvent
    public static void onLivingDeathEvent(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        DamageSource source = event.getSource();
        Entity killer = source.getEntity();
        ResourceLocation entityResourceLocation = entity.getType().getDefaultLootTable();

        if (killer instanceof Player player) {
            UUID playerUUID = player.getUUID();
            ServerLevel serverLevel = player.getLevel() instanceof ServerLevel ? (ServerLevel) player.getLevel() : null;

            if (serverLevel != null && ConfigManager.isNormalEnemy(entityResourceLocation)) {
                CompoundTag entityData = new CompoundTag();
                entity.save(entityData);
                UUID entityUUID = entity.getUUID();
                preDeathData.put(entityUUID, entityData);
                LOGGER.debug("Stored pre-death data for entity: {}", entityUUID);

                // Print the contents of the entityData tag
                LOGGER.debug("Entity data: {}", entityData);

                EntityStorage.storeEntity(playerUUID, entity, entityData);
                LOGGER.debug("Stored entity for player: {} - {}", playerUUID, entityResourceLocation);

                preDeathData.remove(entityUUID);
                LOGGER.debug("Removed pre-death data for entity: {}", entityUUID);
            } else {
                LOGGER.warn("Entity {} is not a valid target for player {}.", entity, player);
            }
        } else {
            LOGGER.debug("Entity died from non-player source: {}", source);
        }

        if (entity instanceof Player player) {
            UUID playerUUID = player.getUUID();
            EntityStorage.clearEntities(playerUUID);
            LOGGER.debug("Cleared entities for player: {}", playerUUID);
        }
    }

    @SubscribeEvent
    public static void onPlayerJoinEvent(PlayerEvent.PlayerLoggedInEvent event) {
        Player player = event.getEntity();
        UUID playerUUID = player.getUUID();

        LOGGER.debug("No stored entities for player: {}", playerUUID);
    }

    @SubscribeEvent
    public static void onPlayerLeaveEvent(PlayerEvent.PlayerLoggedOutEvent event) {
        Player player = event.getEntity();
    }
}