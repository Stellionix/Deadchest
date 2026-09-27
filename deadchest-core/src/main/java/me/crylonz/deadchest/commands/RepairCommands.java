package me.crylonz.deadchest.commands;

import me.crylonz.deadchest.Permission;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;

import java.util.Collection;

import static me.crylonz.deadchest.DeadChestLoader.local;

final class RepairCommands extends DCSubCommandRegistration {

    RepairCommands(DCCommandRegistrationService service) {
        super(service);
    }

    void registerRepairForce() {
        registerCommand("dc repair force", Permission.ADMIN.label, () -> repair(true));
    }

    void registerRepair() {
        registerCommand("dc repair", Permission.ADMIN.label, () -> repair(false));
    }

    private void repair(boolean forced) {
        if (player() == null) {
            sender().sendMessage(local.prefixed("commands.error.player-only"));
            return;
        }

        me.crylonz.deadchest.DeadChestLoader.getSchedulerAdapter().executeForEntity(player(), () -> {
            Collection<Entity> entities = player().getWorld().getNearbyEntities(player().getLocation(), 100.0D, 25.0D, 100.0D);
            int holoRemoved = 0;

            for (Entity entity : entities) {
                if (entity.getType() != EntityType.ARMOR_STAND) {
                    continue;
                }

                ArmorStand armorStand = (ArmorStand) entity;
                if (armorStand.hasMetadata("deadchest") || forced) {
                    holoRemoved++;
                    entity.remove();
                }
            }

            player().sendMessage(local.prefixed("commands.operation.holograms-removed", holoRemoved));
        });
    }
}
