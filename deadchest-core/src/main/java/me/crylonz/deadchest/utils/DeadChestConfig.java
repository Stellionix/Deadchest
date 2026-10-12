package me.crylonz.deadchest.utils;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.stream.Collectors;

public class DeadChestConfig {

    private final Plugin plugin;
    private static final HashMap<String, Object> configData = new HashMap<>();
    private final FileConfiguration configuration = YamlConfiguration.loadConfiguration(new File("config.yml"));

    public DeadChestConfig(Plugin plugin) {
        this.plugin = plugin;
    }

    public static void clearCache() {
        configData.clear();
    }

    public void register(String key, Object defaultValue) {
        configData.put(key, normalizeValue(resolveFromConfig(key), defaultValue, ConfigKey.fromCanonicalPath(key)));
        if (ConfigKey.DROP_MODE.toString().equals(key)
                && "inventory-then-ground".equalsIgnoreCase(plugin.getConfig().getString(key))) {
            plugin.getConfig().set(key, "inventory");
            plugin.saveConfig();
        }

    }

    public void register(ConfigKey key) {
        register(key.toString(), key.defaultValue());
    }

    public Boolean getBoolean(ConfigKey key) {
        return toBoolean(configData.get(key.toString()));
    }

    public double getDouble(ConfigKey key) {
        return toDouble(configData.get(key.toString()));
    }

    public int getInt(ConfigKey key) {
        return toInt(configData.get(key.toString()), key);
    }

    public ArrayList<String> getArray(ConfigKey key) {
        Object value = configData.get(key.toString());
        if (!(value instanceof List)) {
            return new ArrayList<>();
        }

        return ((List<?>) value).stream()
                .map(String::valueOf)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    public String getString(ConfigKey key) {
        Object value = configData.get(key.toString());
        return value == null ? "" : String.valueOf(value);
    }

    public Object getValue(ConfigKey key) {
        return configData.get(key.toString());
    }

    public List<Object> getIgnoredEntries() {
        Object value = configData.get(ConfigKey.IGNORED_ITEMS.toString());
        if (value instanceof List<?>) {
            return new ArrayList<>((List<?>) value);
        }
        return new ArrayList<>();
    }

    public void setIgnoredEntries(Collection<?> entries) {
        List<Object> normalizedEntries = IgnoreItemRules.normalizeEntries(entries);
        plugin.getConfig().set(ConfigKey.IGNORED_ITEMS.toString(), normalizedEntries);
        plugin.saveConfig();
        configData.put(ConfigKey.IGNORED_ITEMS.toString(), normalizedEntries);
    }

    public void setValue(ConfigKey key, Object value) {
        Object normalizedValue = normalizeValue(value, key.defaultValue(), key);
        plugin.getConfig().set(key.toString(), normalizedValue);
        plugin.saveConfig();
        configData.put(key.toString(), normalizedValue);
    }

    public void resetValue(ConfigKey key) {
        setValue(key, key.defaultValue());
    }

    private Object resolveFromConfig(String canonicalPath) {
        Object param = plugin.getConfig().get(canonicalPath);
        if (param != null) {
            return param;
        }

        ConfigKey configKey = ConfigKey.fromCanonicalPath(canonicalPath);
        if (configKey == null) {
            return null;
        }

        for (String alias : configKey.aliases()) {
            param = plugin.getConfig().get(alias);
            if (param != null) {
                return param;
            }
        }
        return null;
    }

    private Object normalizeValue(Object value, Object defaultValue, ConfigKey key) {
        if (value == null) {
            return defaultValue;
        }

        if (key == ConfigKey.IGNORED_ITEMS) {
            return IgnoreItemRules.normalizeEntries(value);
        }

        if (key == ConfigKey.DROP_MODE) {
            return normalizeDropMode(value, defaultValue);
        }
        if (key == ConfigKey.DROP_BLOCK) {
            return normalizeDropBlock(value, defaultValue);
        }
        if (defaultValue instanceof Boolean) {
            return toBoolean(value);
        }
        if (defaultValue instanceof Integer) {
            return toInt(value, key);
        }
        if (defaultValue instanceof Double) {
            return toDouble(value);
        }
        if (defaultValue instanceof Collection) {
            if (value instanceof List) {
                return ((List<?>) value).stream()
                        .map(String::valueOf)
                        .collect(Collectors.toCollection(ArrayList::new));
            }
            return new ArrayList<>();
        }
        if (defaultValue instanceof String) {
            return String.valueOf(value).trim();
        }

        return value;
    }

    private boolean toBoolean(Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof String) {
            return Boolean.parseBoolean(((String) value).trim());
        }
        if (value instanceof Number) {
            return ((Number) value).intValue() != 0;
        }
        return false;
    }

    private int toInt(Object value, ConfigKey key) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            String normalized = ((String) value).trim().toLowerCase(Locale.ROOT);
            if (key == ConfigKey.DROP_MODE) {
                return decodeDropMode(normalized);
            }
            if (key == ConfigKey.DROP_BLOCK) {
                return decodeDropBlock(normalized);
            }
            try {
                return Integer.parseInt(normalized);
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    private double toDouble(Object value) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        if (value instanceof String) {
            try {
                return Double.parseDouble(((String) value).trim());
            } catch (NumberFormatException ignored) {
                return 0D;
            }
        }
        return 0D;
    }

    private String normalizeDropMode(Object value, Object defaultValue) {
        int mode = toInt(value, ConfigKey.DROP_MODE);
        if (mode == 1) {
            return "inventory";
        }
        if (mode == 2) {
            return "ground-drop";
        }
        return String.valueOf(defaultValue);
    }

    private String normalizeDropBlock(Object value, Object defaultValue) {
        int block = toInt(value, ConfigKey.DROP_BLOCK);
        if (block <= 0) {
            String normalized = String.valueOf(value).trim().toLowerCase(Locale.ROOT);
            switch (normalized) {
                case "player_head":
                case "player-head":
                    return "player-head";
                case "barrel":
                    return "barrel";
                case "shulker_box":
                case "shulker-box":
                    return "shulker-box";
                case "ender_chest":
                case "ender-chest":
                    return "ender-chest";
                case "chest":
                    return "chest";
                default:
                    return String.valueOf(defaultValue);
            }
        }

        switch (block) {
            case 2:
                return "player-head";
            case 3:
                return "barrel";
            case 4:
                return "shulker-box";
            case 5:
                return "ender-chest";
            default:
                return "chest";
        }
    }

    private int decodeDropMode(String normalized) {
        switch (normalized) {
            case "inventory-then-ground":
            case "inventory":
            case "1":
                return 1;
            case "ground-drop":
            case "ground":
            case "2":
                return 2;
            default:
                return 1;
        }
    }

    private int decodeDropBlock(String normalized) {
        switch (normalized) {
            case "chest":
            case "1":
                return 1;
            case "player-head":
            case "player_head":
            case "2":
                return 2;
            case "barrel":
            case "3":
                return 3;
            case "shulker-box":
            case "shulker_box":
            case "4":
                return 4;
            case "ender-chest":
            case "ender_chest":
            case "5":
                return 5;
            default:
                return 1;
        }
    }

    private boolean detectMissingConfigs() {
        plugin.reloadConfig();

        boolean missingCanonical = configData.keySet()
                .stream()
                .anyMatch(key -> !plugin.getConfig().getKeys(true).contains(key));

        boolean hasLegacyKeys = Arrays.stream(ConfigKey.values())
                .flatMap(configKey -> Arrays.stream(configKey.aliases()))
                .anyMatch(alias -> plugin.getConfig().contains(alias));

        return missingCanonical || hasLegacyKeys;
    }

    public void updateConfig() {
        if (detectMissingConfigs()) {
            plugin.getLogger().warning("Missing configuration found");
            plugin.getLogger().warning("Updating config.yml with missing parameters");

            File file = new File(plugin.getDataFolder().getAbsolutePath() + File.separator + "config.yml");
            File backup = new File(plugin.getDataFolder().getAbsolutePath() + File.separator + "config.legacy.yml");
            if (file.exists()) {
                try {
                    Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    plugin.getLogger().warning("Legacy configuration backup created at " + backup.getName());
                } catch (IOException exception) {
                    plugin.getLogger().warning("Could not create config backup: " + exception.getMessage());
                }
            }
            final FileConfiguration legacyConfiguration = YamlConfiguration.loadConfiguration(backup);
            file.delete();
            plugin.saveDefaultConfig();
            // Reload freshly generated V2 template so we keep its structure/comments.
            plugin.reloadConfig();

            configData.entrySet()
                    .stream()
                    .forEach(config -> {
                        Object migratedValue = resolveFromLegacyForMigration(config.getKey(), config.getValue(), legacyConfiguration);
                        plugin.getConfig().set(config.getKey(), migratedValue);
                    });

            for (ConfigKey configKey : ConfigKey.values()) {
                for (String alias : configKey.aliases()) {
                    plugin.getConfig().set(alias, null);
                }
            }
            plugin.getConfig().set("config-version", 2);

            plugin.saveConfig();
        }
    }

    private Object resolveFromLegacyForMigration(String canonicalKey, Object fallbackValue, FileConfiguration legacyConfiguration) {
        ConfigKey configKey = ConfigKey.fromCanonicalPath(canonicalKey);
        if (configKey == null) {
            return fallbackValue;
        }

        Object legacyValue = legacyConfiguration.get(canonicalKey);
        if (legacyValue == null) {
            for (String alias : configKey.aliases()) {
                legacyValue = legacyConfiguration.get(alias);
                if (legacyValue != null) {
                    break;
                }
            }
        }

        if (legacyValue == null) {
            return fallbackValue;
        }
        return normalizeValue(legacyValue, fallbackValue, configKey);
    }

    public FileConfiguration getConfiguration() {
        return configuration;
    }
}
