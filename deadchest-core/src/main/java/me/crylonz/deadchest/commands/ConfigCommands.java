package me.crylonz.deadchest.commands;

import me.crylonz.deadchest.utils.ConfigKey;

import static me.crylonz.deadchest.DeadChestLoader.config;
import static me.crylonz.deadchest.DeadChestLoader.local;

final class ConfigCommands extends DCSubCommandRegistration {

    ConfigCommands(DCCommandRegistrationService service) {
        super(service);
    }

    void registerConfigOverview() {
        registerCommand("dc config", null, () -> {
            if (!service.ensureConfigPermission()) {
                return;
            }
            sender().sendMessage(local.prefixed("commands.config.usage"));
        });
    }

    void registerConfigGet() {
        registerCommand("dc config get {1}", null, () -> {
            if (!service.ensureConfigPermission()) {
                return;
            }
            ConfigKey key = service.requireConfigKey(args()[2]);
            if (key == null) {
                return;
            }

            sender().sendMessage(local.prefixed(
                    "commands.config.get.success",
                    key.canonicalPath(),
                    DCConfigCommandSupport.formatValue(config.getValue(key))
            ));
        });
    }

    void registerConfigSet() {
        registerCommand("dc config set {1} {2}", null, () -> {
            if (!service.ensureConfigPermission()) {
                return;
            }
            ConfigKey key = service.requireConfigKey(args()[2]);
            if (key == null) {
                return;
            }

            try {
                DCConfigCommandSupport.ParsedConfigValue parsed = DCConfigCommandSupport.parseValue(key, args()[3]);
                config.setValue(key, parsed.value());
                service.reloadPluginConfiguration();
                sender().sendMessage(local.prefixed(
                        "commands.config.set.success",
                        key.canonicalPath(),
                        parsed.displayValue()
                ));
            } catch (IllegalArgumentException ignored) {
                sender().sendMessage(local.prefixed(
                        "commands.config.error.invalid-value",
                        key.canonicalPath(),
                        args()[3],
                        DCConfigCommandSupport.expectedValueDescription(key)
                ));
            }
        });
    }

    void registerConfigReset() {
        registerCommand("dc config reset {1}", null, () -> {
            if (!service.ensureConfigPermission()) {
                return;
            }
            ConfigKey key = service.requireConfigKey(args()[2]);
            if (key == null) {
                return;
            }

            config.resetValue(key);
            service.reloadPluginConfiguration();
            sender().sendMessage(local.prefixed(
                    "commands.config.reset.success",
                    key.canonicalPath(),
                    DCConfigCommandSupport.formatValue(config.getValue(key))
            ));
        });
    }

    void registerConfigEdit() {
        registerCommand("dc config edit {1}", null, () -> {
            if (!service.ensureConfigPermission()) {
                return;
            }
            ConfigKey key = service.requireConfigKey(args()[2]);
            if (key == null) {
                return;
            }

            if (!key.supportsInteractiveEdit()) {
                sender().sendMessage(local.prefixed("commands.config.edit.unsupported", key.canonicalPath()));
                return;
            }

            service.openIgnoredItemsEditor();
        });
    }
}
