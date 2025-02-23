package com.SoloLevelingSystem.configs;

import net.minecraft.resources.ResourceLocation;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.HashSet;
import java.nio.charset.StandardCharsets;

public class ConfigManager {
    private static final Set<ResourceLocation> normalEnemies = new HashSet<>();
    private static final Set<ResourceLocation> minibossEnemies = new HashSet<>();
    private static final Set<ResourceLocation> bossEnemies = new HashSet<>();

    private static final Properties properties = new Properties();

    public static void loadConfig() {
        InputStream configStream = ConfigManager.class.getClassLoader().getResourceAsStream("solo_leveling_system/config.toml");

        if (configStream == null) {
            System.err.println("Could not find config file: solo_leveling_system/config.toml");
            return;
        }

        try (InputStreamReader reader = new InputStreamReader(configStream, StandardCharsets.UTF_8)) {
            properties.load(reader);

            List<String> normalList = convertToList(properties.getProperty("enemies.normal"));
            if (normalList != null) {
                for (String enemy : normalList) {
                    normalEnemies.add(new ResourceLocation(enemy));
                }
            }

            List<String> minibossList = convertToList(properties.getProperty("enemies.miniboss"));
            if (minibossList != null) {
                for (String enemy : minibossList) {
                    minibossEnemies.add(new ResourceLocation(enemy));
                }
            }

            List<String> bossList = convertToList(properties.getProperty("enemies.boss"));
            if (bossList != null) {
                for (String enemy : bossList) {
                    bossEnemies.add(new ResourceLocation(enemy));
                }
            }

        } catch (IOException e) {
            System.err.println("Error loading config file: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("Error loading config file: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static List<String> convertToList(String property) {
        if (property == null) {
            return null;
        }
        String cleanedProperty = property.trim();
        if (cleanedProperty.startsWith("[") && cleanedProperty.endsWith("]")) {
            cleanedProperty = cleanedProperty.substring(1, cleanedProperty.length() - 1);
        }
        String[] items = cleanedProperty.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
        List<String> list = new java.util.ArrayList<>();
        for (String item : items) {
            list.add(item.trim().replace("\"", ""));
        }
        return list;
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

