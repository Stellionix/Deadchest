<h1  align="center">
    <img src="deadchest-logo.png" alt="Deadchest" width="800" /><br>
</h1>

<h2  align="center">
    <img src="https://cf.way2muchnoise.eu/full_322882_downloads.svg" alt="Downloads"/> 
    <a href="https://modrinth.com/plugin/dead-chest"><img src="https://img.shields.io/modrinth/dt/dead-chest?logo=modrinth&label=Modrinth%20downloads" alt="Modrinth Downloads"/></a>
    <img src="https://img.shields.io/github/last-commit/stellionix/Deadchest" alt="commit"/>
    <a href="https://github.com/stellionix/Deadchest/actions/workflows/ci.yml"><img src="https://github.com/stellionix/Deadchest/actions/workflows/ci.yml/badge.svg" alt="CI"/></a>
    <a href="https://stellionix.github.io/Deadchest/"><img src="https://img.shields.io/badge/docs-online-blue" alt="Docs"/></a>
    <img src="https://img.shields.io/github/license/stellionix/Deadchest" alt="License"/>
</h2>

DeadChest keeps a player's inventory in a chest when they die.

It is designed for Minecraft Java Edition servers and primarily targets Bukkit, Spigot, and Paper.

## Features

- Store a player's inventory in a chest on death instead of dropping items on the ground
- Support modern Bukkit-based server software including Paper, Spigot, and Purpur
- Provide admin tools to inspect, remove, repair, and give back DeadChests
- Offer configurable behavior, localization, and optional integrations such as WorldGuard
- Include an API and automated tests for safer maintenance and extensions

## Compatibility

- Minecraft Java Edition
- Bukkit API `1.13+`
- Supported server software: Bukkit, Spigot, Paper, Purpur, Tuinity, and similar forks

## Download

- [Modrinth](https://modrinth.com/plugin/dead-chest)
- [CurseForge](https://www.curseforge.com/minecraft/bukkit-plugins/dead-chest)
- [Bukkit](https://dev.bukkit.org/projects/dead-chest)

## Quick Start

1. Download the latest DeadChest JAR.
2. Place it in your server's `plugins/` directory.
3. Start the server and confirm that DeadChest is enabled in the console.
4. Adjust the generated configuration files if needed.

Expected startup log:

```text
[DeadChest] Enabling DeadChest vX.X.X
```

## Documentation

The official documentation is available at [stellionix.github.io/Deadchest](https://stellionix.github.io/Deadchest/).

Useful pages:

- [Installation](https://stellionix.github.io/Deadchest/latest/installation/)
- [Configuration](https://stellionix.github.io/Deadchest/latest/configuration/)
- [Commands and Permissions](https://stellionix.github.io/Deadchest/latest/commands-and-perms/)
- [Troubleshooting](https://stellionix.github.io/Deadchest/latest/troubleshooting/)

## Statistics

<img align="center" src="https://bstats.org/signatures/bukkit/Deadchest.svg" alt="stats"/> 

More statistics are available on [bStats](https://bstats.org/plugin/bukkit/DeadChest/11385).

## Commands

The main command is `/dc`.

Common examples:

- `/dc reload`
- `/dc list`
- `/dc remove`
- `/dc giveback <player>`
- `/dc giveback list <player>`
- `/dc giveback preview <player>`
- `/dc giveback <player> oldest`
- `/dc giveback <player> all inventory`

The full command list and permission nodes are
documented [here](https://stellionix.github.io/Deadchest/latest/commands-and-perms/).

## Contributing

Development and contribution guidelines are available in [CONTRIBUTING.md](CONTRIBUTING.md).
