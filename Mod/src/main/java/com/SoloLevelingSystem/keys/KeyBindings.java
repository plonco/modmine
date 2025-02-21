package com.SoloLevelingSystem.keys;

import com.SoloLevelingSystem.SoloLevelingSystem;
import com.SoloLevelingSystem.storage.EntityStorage;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod.EventBusSubscriber(modid = SoloLevelingSystem.MODID, value = Dist.CLIENT)
public class KeyBindings {
    private static final Logger LOGGER = LoggerFactory.getLogger(KeyBindings.class);
    private static final long COOLDOWN = 3 * 60 * 1000; // 3 minutes in milliseconds
    private static long lastInvocationTime = 0;

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        if (event.getKey() == GLFW.GLFW_KEY_R && event.getAction() == GLFW.GLFW_PRESS) {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastInvocationTime >= COOLDOWN) {
                MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
                if (server != null) {
                    ServerPlayer player = server.getPlayerList().getPlayer(Minecraft.getInstance().player.getUUID());
                    if (player != null) {
                        LOGGER.debug("Player {} pressed key R", player.getUUID());
                        EntityStorage.spawnEntities(player);
                    } else {
                        LOGGER.debug("Player not found");
                    }
                }
                lastInvocationTime = currentTime;
            } else {
                Minecraft.getInstance().player.sendSystemMessage(Component.literal("Invocaciones no disponibles"));
                LOGGER.debug("Cooldown not finished");
            }
        }
    }
}