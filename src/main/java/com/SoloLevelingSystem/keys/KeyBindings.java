package com.SoloLevelingSystem.keys;

import com.SoloLevelingSystem.SoloLevelingSystem;
import com.SoloLevelingSystem.network.SpawnEntitiesMessage;
import net.minecraft.client.Minecraft;
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
    private static final long COOLDOWN = 0; //3 * 60 * 1000; // 3 minutes in milliseconds
    private static long lastInvocationTime = 0;

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        if (event.getKey() == GLFW.GLFW_KEY_R && event.getAction() == GLFW.GLFW_PRESS) {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastInvocationTime >= COOLDOWN) {
                // Send the message to the server
                SoloLevelingSystem.CHANNEL.sendToServer(new SpawnEntitiesMessage());

                lastInvocationTime = currentTime;
            } else {
                Minecraft.getInstance().player.sendSystemMessage(Component.literal("Invocaciones no disponibles"));
                LOGGER.debug("Cooldown not finished");
            }
        }
    }
}