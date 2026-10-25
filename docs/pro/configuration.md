# Configuration and permissions

Pro stores its configuration and message files in `plugins/Deadchest-Pro/`:

| File | Purpose |
| --- | --- |
| `config.yml` | [Core settings](../configuration.md): chest lifetime, access, recovery, XP, holograms and integrations |
| `pro.yml` | Pro language, teleportation and history retention |
| `pro-<language>.yml` | Translated Pro messages, menu labels and feedback |

Run `/dc reload` with `deadchest.admin` to reload core settings, Pro settings
and messages. Reloading cancels pending teleport countdowns.

## Default pro.yml

```yaml
language: en
teleport:
  enabled: false
  warmup-seconds: 5
  cooldown-seconds: 300
  cross-world: false
  denied-worlds: []
  feedback:
    action-bar: true
    titles: true
    particles: true
    sounds: true
history:
  retention-days: 30
```

See [teleportation](teleportation.md) for the effect of each teleport option and
[history retention](history.md#retention) for cleanup rules.

## Languages and custom messages

Both core and Pro include these languages:

| Code | Language |
| --- | --- |
| `en` | English |
| `fr` | French |
| `de` | German |
| `es` | Spanish |
| `it` | Italian |
| `pl` | Polish |
| `pt-br` | Brazilian Portuguese |
| `zh-cn` | Simplified Chinese |

Set `localization.language` in `config.yml` for core messages and `language`
in `pro.yml` for Pro messages. Set both to the same code for a consistent interface.
An unknown Pro language code falls back to English.

Edit the corresponding `pro-<language>.yml`, preserving message placeholders,
then run `/dc reload`. Pro messages support `&` color codes. Existing customizations
are kept; missing keys use the bundled translation for the selected language.

## Commands and permissions

Pro commands that open menus or act on a player are used in game.
Square brackets denote optional arguments; angle brackets denote required arguments.

| Command | Permission | Default |
| --- | --- | --- |
| `/dc menu` | `deadchest.pro.menu` | Everyone |
| `/dc track [latest/number/uuid]` | `deadchest.pro.track` | Everyone |
| `/dc track off` | `deadchest.pro.track` | Everyone |
| `/dc teleport [latest/number/uuid]` | `deadchest.pro.teleport` | Operators |
| `/dc history <player> [page]` | `deadchest.pro.history` | Operators |
| `/dc restore [player]` | `deadchest.pro.restore` and `deadchest.pro.history` for the history menu | Operators |
| `/dc restore <chest-uuid>` | `deadchest.pro.restore` | Operators |
| `/dc reload` | `deadchest.admin` | Operators |

Revoke `deadchest.pro.menu` or `deadchest.pro.track` through your permission
manager if those features should be restricted. Grant teleportation separately;
menu access does not grant permission to teleport. Core chest access rules still apply.

All [core commands and permissions](../commands-and-perms.md) remain available.
