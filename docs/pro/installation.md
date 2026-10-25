# Installation and migration

## New installation

Download the Pro distribution from the official
[DeadChest Pro page on BuiltByBit](https://builtbybit.com/resources/deadchest-pro.131511/).

1. Stop the server.
2. Put `dead-chest-pro-<version>.jar` in `plugins/`. Use the complete distribution JAR.
3. Start the server and check that `Deadchest-Pro` enables without errors.
4. Configure core behavior in `plugins/Deadchest-Pro/config.yml` and Pro features
   in `plugins/Deadchest-Pro/pro.yml`.
5. Set [permissions and languages](configuration.md), then run `/dc reload`.

Pro uses the SQLite driver supplied by the server, like the free edition.
No additional SQLite JAR needs to be installed. Use the Java version required by
your server. Pro supports Spigot/Paper from Minecraft 1.13 onward and Folia.

Do not install the free and Pro JARs together. Pro refuses to enable when the
free DeadChest plugin is also loaded. Use a full server restart when changing editions.

## Upgrade from the free edition

1. Stop the server. Back up its worlds, player data, free JAR and complete
   DeadChest data folder together. Keep the same Minecraft version during migration.
2. Remove the free JAR and install the Pro JAR.
3. Copy the complete contents of the free data folder into
   `plugins/Deadchest-Pro/`. Use the actual source folder name on your server.
   Do not overwrite an existing Pro installation.
4. Start a test copy first. Check startup logs, `/dc list`, chest locations,
   item collection and XP before reopening the production server.
5. Configure `pro.yml` and grant Pro permissions.

Copying the whole folder preserves configuration, translations, existing chests,
pending operations and any older migration files. Keep the original backup intact.

Existing active chests are imported into Pro history. Events that happened
before Pro was installed cannot be reconstructed.

## Updating Pro

Stop the server, make a complete backup and replace the Pro JAR with the new
distribution. Keep the data folder. Start the server and check the logs.
Existing custom messages are preserved; missing keys use the bundled translation.

## Returning to the free edition

Complete pending Pro restorations and resolve any records requiring review
before switching editions. The free edition cannot process the Pro restoration queue.

To return to the exact pre-migration state, stop the server and restore the
worlds, player data and free plugin folder from the same backup, then replace
the Pro JAR with the free JAR. Restoring only an old chest database while keeping
current player inventories can duplicate previously collected items.

Do not downgrade worlds or items to an older Minecraft version as part of this process.
