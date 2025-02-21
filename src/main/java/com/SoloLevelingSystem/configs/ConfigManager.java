package com.SoloLevelingSystem.configs;

import com.electronwill.nightconfig.core.file.FileConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ConfigManager {
    private static final Set<ResourceLocation> normalEnemies = new HashSet<>();
    private static final Set<ResourceLocation> minibossEnemies = new HashSet<>();
    private static final Set<ResourceLocation> bossEnemies = new HashSet<>();

    public static void loadConfig() {
        Path configPath = FMLPaths.CONFIGDIR.get().resolve("solo_leveling_system/config.toml");

        // Create the config file if it doesn't exist
        if (!Files.exists(configPath)) {
            try {
                Files.createDirectories(configPath.getParent());
                Files.createFile(configPath);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        FileConfig config = FileConfig.of(configPath);
        config.load();

        List<String> normalList = config.get("enemies.normal");
        if (normalList != null) {
            for (String enemy : normalList) {
                normalEnemies.add(new ResourceLocation(enemy));
            }
        }

        List<String> minibossList = config.get("enemies.miniboss");
        if (minibossList != null) {
            for (String enemy : minibossList) {
                minibossEnemies.add(new ResourceLocation(enemy));
            }
        }

        List<String> bossList = config.get("enemies.boss");
        if (bossList != null) {
            for (String enemy : bossList) {
                bossEnemies.add(new ResourceLocation(enemy));
            }
        }

        config.close();
    }

    public static boolean isNormalEnemy(ResourceLocation entity) {
        return normalEnemies.contains(entity);
    }

    public static boolean isMinibossEnemy(ResourceLocation entity) {
        return minibossEnemies.contains(entity);
    }

    public static boolean isBossEnemy(ResourceLocation entity) {
        return bossEnemies.contains(entity);
    }
}
