package me.crylonz.deadchest.commands;

import me.crylonz.deadchest.utils.ConfigKey;

import static me.crylonz.deadchest.DeadChestLoader.config;

public enum GivebackStrategy {
    INVENTORY("inventory"),
    GROUND("ground");

    private final String token;

    GivebackStrategy(String token) {
        this.token = token;
    }

    public String token() {
        return token;
    }

    public static GivebackStrategy fromToken(String raw) {
        if (raw == null) {
            return null;
        }

        String normalized = raw.trim().toLowerCase();
        for (GivebackStrategy strategy : values()) {
            if (strategy.token.equals(normalized)) {
                return strategy;
            }
        }
        return null;
    }

    public static GivebackStrategy defaultStrategy() {
        String recoveryMode = config.getString(ConfigKey.DROP_MODE);
        if ("ground-drop".equalsIgnoreCase(recoveryMode)) {
            return GROUND;
        }
        return INVENTORY;
    }
}
