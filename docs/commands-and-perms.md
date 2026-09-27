## DeadChest - Commands & Permissions

Make sure you have [installed](installation.md) the plugin before reading this section.

This page documents the current `/dc` commands and permission nodes.

### Commands

| Command                 | Permission               | Description                                                                  |
|-------------------------|--------------------------|------------------------------------------------------------------------------|
| `/dc reload`            | `deadchest.admin`        | Reload configuration and localization.                                       |
| `/dc repair`            | `deadchest.admin`        | Remove nearby broken legacy/DeadChest holograms around the executing player. |
| `/dc repair force`      | `deadchest.admin`        | Force-remove nearby armor stand holograms around the executing player.       |
| `/dc remove`            | `deadchest.remove.own`   | Remove all of your own DeadChests.                                           |
| `/dc remove <player>`   | `deadchest.remove.other` | Remove all DeadChests belonging to a specific player.                        |
| `/dc removeinfinite`    | `deadchest.admin`        | Remove all infinite DeadChests.                                              |
| `/dc removeall`         | `deadchest.admin`        | Remove all DeadChests globally.                                              |
| `/dc list`              | `deadchest.list.own`*    | List your own DeadChests.                                                    |
| `/dc list <player>`     | `deadchest.list.other`   | List DeadChests for a specific player.                                       |
| `/dc list all`          | `deadchest.list.other`   | List all DeadChests on the server.                                           |
| `/dc giveback <player>` | `deadchest.giveback`     | Give back the latest active DeadChest inventory to a player.                 |
| `/dc giveback <player> latest` | `deadchest.giveback` | Give back the latest active DeadChest for a player.                          |
| `/dc giveback <player> oldest` | `deadchest.giveback` | Give back the oldest active DeadChest for a player.                          |
| `/dc giveback <player> all` | `deadchest.giveback` | Give back all active DeadChests for a player.                                |
| `/dc giveback <player> id <n>` | `deadchest.giveback` | Give back one specific active DeadChest by list id.                          |
| `/dc giveback <player> <selector> inventory` | `deadchest.giveback` | Restore through the player's inventory, then drop overflow.                  |
| `/dc giveback <player> <selector> ground` | `deadchest.giveback` | Drop restored items at the player's location.                                |
| `/dc giveback list <player>` | `deadchest.giveback` | List active DeadChests for a player with selectable ids.                     |
| `/dc giveback preview <player>` | `deadchest.giveback` | Preview the default giveback action for a player without applying it.        |
| `/dc giveback preview <player> <selector>` | `deadchest.giveback` | Preview a selected giveback target without applying it.                      |
| `/dc giveback preview <player> <selector> <strategy>` | `deadchest.giveback` | Preview a giveback with explicit strategy without applying it.               |
| `/dc giveback preview list <player>` | `deadchest.giveback` | Preview the selectable giveback list for a player.                           |
| `/dc config`            | `deadchest.config`       | Show `/dc config` usage. `deadchest.admin` also bypasses this permission.    |
| `/dc config get <path>` | `deadchest.config`       | Read a config value using its canonical YAML path.                           |
| `/dc config set <path> <value>` | `deadchest.config` | Update a config value using its canonical YAML path.                         |
| `/dc config reset <path>` | `deadchest.config`     | Reset a config value to its default.                                         |
| `/dc config edit <path>` | `deadchest.config`      | Open an interactive editor for supported paths (`filters.ignored-items` only, player-only). |
| `/dc ignore`            | `deadchest.admin`        | Deprecated alias for `/dc config edit filters.ignored-items`.                |

\* `deadchest.list.own` is only required when `permissions.require-list-own: true` in `config.yml`.

`/dc giveback` selectors are deterministic:

- default `/dc giveback <player>` behaves like `latest`
- `oldest` selects the oldest active chest by creation date
- `latest` selects the newest active chest by creation date
- `all` restores every active chest for the player
- `id <n>` uses the ids shown by `/dc giveback list <player>`
- optional strategy `inventory` restores to inventory then drops overflow
- optional strategy `ground` drops items at the target player's location

Current behavior notes:

- if the target is offline, the selected giveback is queued and delivered automatically on next login
- queued deliveries are persisted in the SQLite `pending_givebacks` table
- `inventory` gives XP directly and restores items to inventory before dropping overflow
- `ground` drops items at the target player's location and gives XP directly
- `preview` never modifies chests, storage, inventory, or the offline queue
- `id` values are runtime list ids, not persistent database ids

### Permission Nodes

| Permission                | Description                                                                          |
|---------------------------|--------------------------------------------------------------------------------------|
| `deadchest.admin`         | Access admin commands and bypass `deadchest.config`.                             |
| `deadchest.config`        | Access `/dc config get|set|reset|edit`.                                        |
| `deadchest.generate`      | Generate a DeadChest on death (if required by config).                               |
| `deadchest.get`           | Claim/retrieve a DeadChest (if required by config).                                  |
| `deadchest.chestPass`     | Bypass owner-only chest access.                                                      |
| `deadchest.infinityChest` | Create infinite DeadChests.                                                          |
| `deadchest.remove.own`    | Remove own DeadChests with `/dc remove`.                                             |
| `deadchest.remove.other`  | Remove another player's DeadChests with `/dc remove <player>`.                       |
| `deadchest.list.own`      | List own DeadChests with `/dc list` (if required by config).                         |
| `deadchest.list.other`    | List another player's/all DeadChests with `/dc list <player>` or `/dc list all`.     |
| `deadchest.giveback`      | Give back another player's items with `/dc giveback <player>`.                       |

### Config-Dependent Permission Requirements

The following config flags control whether some actions require permissions:

- `permissions.require-generate`: require `deadchest.generate` for chest generation.
- `permissions.require-claim`: require `deadchest.get` for claiming chests.
- `permissions.require-list-own`: require `deadchest.list.own` for `/dc list`.

`/dc config` uses canonical YAML paths, so command input stays aligned with `config.yml`:

- Example: `/dc config get localization.language`
- Example: `/dc config set chest.block-type barrel`
- Example: `/dc config reset visuals.effect-animation.style`
- Example: `/dc config edit filters.ignored-items`

`/dc ignore` is still available for compatibility, but it is deprecated.

Console behavior:

- `/dc config`, `/dc config get`, `/dc config set`, and `/dc config reset` can be executed from the server console.
- `/dc config edit` and `/dc ignore` are player-only because they open a GUI.

`/dc config edit filters.ignored-items` and `/dc ignore` edit `filters.ignored-items` from `config.yml` directly:

- `filters.ignored-items` ignores by `Material` name.
- `/dc config edit filters.ignored-items` is the only currently supported interactive config editor and updates the same YAML list through a GUI.
- `/dc ignore` points to that same editor for backward compatibility.
- Custom items are stored there as serialized `ItemStack` entries, including item meta.
- Ignored items are skipped by DeadChest storage and keep their default death behavior.

