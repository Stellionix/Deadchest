## DeadChest - Configuration

Make sure you have [installed](installation.md) the plugin before editing the configuration.

For Pro's `pro.yml`, languages and feature permissions, see
[Pro configuration and permissions](pro/configuration.md).

You can edit configuration directly in `config.yml` or in game with `/dc config`.

DeadChest now uses a structured `config.yml` (schema version `2`) since `v5.0.0`.
`/dc config set` and `/dc config reset` apply changes immediately.
If you edit `config.yml` manually, run `/dc reload`.

Console behavior:

- `/dc config`, `/dc config get`, `/dc config set`, and `/dc config reset` can be executed from the server console.
- `/dc config edit` is player-only because it opens a GUI.

### Global

| Key              | Type    | Default | Description                   |
|------------------|---------|---------|-------------------------------|
| `config-version` | integer | `2`     | Configuration schema version. |

### Localization

| Key                     | Type   | Default | Description                                                                                                                            |
|-------------------------|--------|---------|----------------------------------------------------------------------------------------------------------------------------------------|
| `localization.language` | string | `en`    | Language file loaded from `plugins/DeadChest/localization/<language>.json` (ex: `en`, `fr`, `es`, `de`, `pt-br`, `pl`, `it`, `zh-cn`). |

### Updates

| Key                  | Type    | Default | Description                                |
|----------------------|---------|---------|--------------------------------------------|
| `updates.auto-check` | boolean | `true`  | Enable automatic update checks at startup. |

### Chest

| Key                           | Type    | Default                 | Description                                                                 |
|-------------------------------|---------|-------------------------|-----------------------------------------------------------------------------|
| `chest.owner-only-open`       | boolean | `true`                  | During the private phase: only owner can open chest (except `deadchest.chestPass`). |
| `chest.duration-seconds`      | integer | `300`                   | Duration of the private phase in seconds. `0` = infinite private phase.     |
| `chest.indestructible`        | boolean | `true`                  | Protect chest block from destruction/explosions.                            |
| `chest.max-per-player`        | integer | `15`                    | Max active chests per player. `0` = unlimited.                              |
| `chest.recovery-mode`         | string  | `inventory-then-ground` | `inventory-then-ground` or `ground-drop`.                                   |
| `chest.block-type`            | string  | `chest`                 | `chest`, `player-head`, `barrel`, `shulker-box`, `ender-chest`.             |
| `chest.drop-items-on-timeout` | boolean | `false`                 | Legacy one-phase timeout behavior when `chest.loot.enabled=false`: drop items (`true`) or remove contents (`false`). |

### Chest - Public Loot Phase

| Key                                      | Type    | Default | Description |
|------------------------------------------|---------|---------|-------------|
| `chest.loot.enabled`                     | boolean | `false` | Enable a second public loot phase after the private phase ends. |
| `chest.loot.public-duration-seconds`     | integer | `300`   | Duration of the public phase in seconds. `0` = infinite public phase. |
| `chest.loot.drop-items-on-timeout`       | boolean | `false` | At the end of the public phase: drop items (`true`) or remove contents (`false`). |
| `chest.loot.public-access.owner`         | boolean | `true`  | During public phase, allow the owner to recover the chest. |
| `chest.loot.public-access.killer`        | boolean | `true`  | During public phase, allow the PvP killer to loot the chest. |
| `chest.loot.public-access.other-players` | boolean | `true`  | During public phase, allow any other player to loot the chest. |

The timeout model is therefore:

- Phase 1: private phase controlled by `chest.duration-seconds`.
- Phase 2: optional public phase controlled by `chest.loot.*`.
- If `chest.loot.enabled=false`, the chest expires directly after the private phase and uses `chest.drop-items-on-timeout`.
- If `chest.loot.enabled=true`, the chest enters the public phase after the private phase, then expires using `chest.loot.drop-items-on-timeout`.

### Permissions

| Key                            | Type    | Default | Description                                            |
|--------------------------------|---------|---------|--------------------------------------------------------|
| `permissions.require-generate` | boolean | `false` | Require `deadchest.generate` to create chest on death. |
| `permissions.require-claim`    | boolean | `false` | Require `deadchest.get` to claim/retrieve chest.       |
| `permissions.require-list-own` | boolean | `false` | Require `deadchest.list.own` for `/dc list`.           |

### Maintenance

| Key                              | Type    | Default | Description                              |
|----------------------------------|---------|---------|------------------------------------------|
| `maintenance.cleanup-on-startup` | boolean | `false` | Remove all DeadChests on server startup. |

### Generation Rules

| Key                              | Type    | Default | Description                                       |
|----------------------------------|---------|---------|---------------------------------------------------|
| `generation.allow-in-creative`   | boolean | `true`  | Allow chest generation on death in Creative mode. |
| `generation.allow-on-lava`       | boolean | `true`  | Allow generation when death occurs in lava.       |
| `generation.allow-on-water`      | boolean | `true`  | Allow generation when death occurs in water.      |
| `generation.allow-on-rails`      | boolean | `true`  | Allow generation when death occurs on rails.      |
| `generation.allow-in-minecart`   | boolean | `true`  | Allow generation when death occurs in minecart.   |
| `generation.allow-in-end-worlds` | boolean | `true`  | Allow generation in The End worlds.               |

### Messages

| Key                                  | Type    | Default | Description                                |
|--------------------------------------|---------|---------|--------------------------------------------|
| `messages.display-position-on-death` | boolean | `true`  | Send chest coordinates to player on death. |

### XP

| Key                   | Type    | Default | Description                                                              |
|-----------------------|---------|---------|--------------------------------------------------------------------------|
| `xp.store-on-death`   | boolean | `false` | Store XP in chest instead of normal orb drop.                            |
| `xp.store-percentage` | integer | `100`   | XP percent stored when enabled (`0` to `100`, values >100 duplicate XP). |

### PvP

| Key                                 | Type    | Default | Description                                                 |
|-------------------------------------|---------|---------|-------------------------------------------------------------|
| `pvp.keep-inventory-on-player-kill` | boolean | `false` | On PvP death: keep inventory and skip DeadChest generation. |

### Integrations

| Key                                     | Type    | Default | Description                                        |
|-----------------------------------------|---------|---------|----------------------------------------------------|
| `integrations.worldguard.enabled`       | boolean | `false` | Enable WorldGuard region checks.                   |
| `integrations.worldguard.default-allow` | boolean | `false` | Default region policy if no DeadChest flag is set. |

### Durability

| Key                                | Type    | Default | Description                                                            |
|------------------------------------|---------|---------|------------------------------------------------------------------------|
| `durability.loss-on-death-percent` | integer | `0`     | Durability loss on death (percentage of max durability). `0` disables. |

### Logging

| Key                                   | Type    | Default | Description                         |
|---------------------------------------|---------|---------|-------------------------------------|
| `logging.deadchest-create-to-console` | boolean | `false` | Log each chest creation in console. |

### Visuals - Effect Animation

| Key                                | Type    | Default | Description                                      |
|------------------------------------|---------|---------|--------------------------------------------------|
| `visuals.effect-animation.enabled` | boolean | `true`  | Enable orbit particles around active DeadChests. |
| `visuals.effect-animation.style`   | string  | `ender` | Effect style: `soul`, `flame`, `ender`.          |
| `visuals.effect-animation.radius`  | number  | `0.8`   | Orbit radius around chest center.                |
| `visuals.effect-animation.speed`   | number  | `1.1`   | Orbit speed multiplier.                          |

DeadChest holograms now use three lines when available:

- top line: access state (`PRIVATE`, `OPEN`, `PUBLIC`, `KILLER`, `OWNER`, `SHARE`)
- middle line: owner name
- bottom line: remaining time

The access-state line reflects both the private/public phase and the configured access rules during the public phase.

### Visuals - Pickup Animation

| Key                                 | Type    | Default    | Description                                     |
|-------------------------------------|---------|------------|-------------------------------------------------|
| `visuals.pickup-animation.enabled`  | boolean | `true`     | Enable animation when a player claims a chest.  |
| `visuals.pickup-animation.particle` | string  | `FIREWORK` | Bukkit particle name used for pickup animation. |
| `visuals.pickup-animation.count`    | integer | `22`       | Number of particles spawned.                    |
| `visuals.pickup-animation.offset-x` | number  | `0.45`     | Particle spread on X axis.                      |
| `visuals.pickup-animation.offset-y` | number  | `0.5`      | Particle spread on Y axis.                      |
| `visuals.pickup-animation.offset-z` | number  | `0.45`     | Particle spread on Z axis.                      |
| `visuals.pickup-animation.speed`    | number  | `0.08`     | Particle extra/speed value.                     |
| `visuals.pickup-animation.y-shift`  | number  | `0.55`     | Vertical center offset from block base.         |

### Visuals - Pickup Sound

| Key                            | Type    | Default                 | Description                   |
|--------------------------------|---------|-------------------------|-------------------------------|
| `visuals.sound.pickup.enabled` | boolean | `true`                  | Enable sound on chest pickup. |
| `visuals.sound.pickup.name`    | string  | `ENTITY_PLAYER_LEVELUP` | Bukkit sound name.            |
| `visuals.sound.pickup.volume`  | number  | `1.2`                   | Sound volume.                 |
| `visuals.sound.pickup.pitch`   | number  | `1.0`                   | Sound pitch.                  |

### Filters

| Key                       | Type         | Default        | Description                                                           |
|---------------------------|--------------|----------------|-----------------------------------------------------------------------|
| `filters.excluded-worlds` | list<string> | Example values | Worlds where DeadChest generation is disabled.                        |
| `filters.excluded-items`  | list<string> | Example values | Items that are not stored in DeadChest.                               |
| `filters.ignored-items`   | list<string> | Example values | Items ignored by DeadChest storage; they keep vanilla death behavior. |

`/dc config` uses canonical YAML paths, so command usage stays aligned with this file:

- `/dc config get localization.language`
- `/dc config set chest.duration-seconds 600`
- `/dc config set filters.excluded-worlds world,world_nether`
- `/dc config reset visuals.sound.pickup.name`
- `/dc config edit filters.ignored-items`

`/dc config edit` currently supports only `filters.ignored-items`.

`filters.ignored-items`, `/dc config edit filters.ignored-items`, and deprecated `/dc ignore` use the same source of truth:

- `filters.ignored-items` is the canonical list in `config.yml`.
- `/dc config edit filters.ignored-items` edits that same list through a GUI.
- `/dc ignore` remains as a deprecated compatibility alias.
- A rule can be either a Bukkit `Material` name or a serialized `ItemStack`.
- Serialized `ItemStack` rules preserve custom meta and are written automatically by the interactive editor.
- Ignored items are not saved in a DeadChest and are left to vanilla death handling or other plugins.

### Permission reference

- `deadchest.admin`: admin commands and bypass for `/dc config`
- `deadchest.config`: use `/dc config get|set|reset|edit`
- `deadchest.generate`: generate chest on death (if required)
- `deadchest.get`: claim/retrieve chest (if required)
- `deadchest.list.own`: list own chests (if required)
- `deadchest.list.other`: list another player's chests
- `deadchest.remove.own`: remove own chests
- `deadchest.remove.other`: remove another player's chests
- `deadchest.giveback`: give back another player's items
- `deadchest.chestPass`: bypass owner-only chest access
- `deadchest.infinityChest`: create infinite chests

