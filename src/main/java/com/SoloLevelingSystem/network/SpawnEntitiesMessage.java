package com.SoloLevelingSystem.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public class SpawnEntitiesMessage {

    public SpawnEntitiesMessage() {
        // No data needed for this message
    }

    public SpawnEntitiesMessage(FriendlyByteBuf buffer) {
        // Read data from the buffer (if needed)
    }

    public void encode(FriendlyByteBuf buffer) {
        // Write data to the buffer (if needed)
    }

    public boolean handle(Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> {
            // This is executed on the server thread!
            ServerPlayer player = context.get().getSender();
            if (player != null) {
                com.SoloLevelingSystem.storage.EntityStorage.spawnStoredEntities(player);
            }
        });
        return true;
    }
}
