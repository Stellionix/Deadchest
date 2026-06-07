package me.crylonz.deadchest.deps.worldguard;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.domains.DefaultDomain;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.flags.registry.FlagConflictException;
import com.sk89q.worldguard.protection.flags.registry.FlagRegistry;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import me.crylonz.deadchest.DeadChestLoader;
import me.crylonz.deadchest.utils.ConfigKey;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static me.crylonz.deadchest.DeadChestLoader.config;
import static me.crylonz.deadchest.utils.Utils.generateLog;

public class WorldGuardSoftDependenciesChecker {

    public static StateFlag DEADCHEST_GUEST_FLAG;
    public static StateFlag DEADCHEST_OWNER_FLAG;
    public static StateFlag DEADCHEST_MEMBER_FLAG;

    public void load() {
        FlagRegistry registry = WorldGuard.getInstance().getFlagRegistry();
        try {
            StateFlag owner_flag = new StateFlag("dc-owner", false);
            registry.register(owner_flag);
            DEADCHEST_OWNER_FLAG = owner_flag;

            StateFlag nobody_flag = new StateFlag("dc-guest", false);
            registry.register(nobody_flag);
            DEADCHEST_GUEST_FLAG = nobody_flag;

            StateFlag member_flag = new StateFlag("dc-member", false);
            registry.register(member_flag);
            DEADCHEST_MEMBER_FLAG = member_flag;

        } catch (FlagConflictException e) {
            DeadChestLoader.log.warning("Conflict in Deadchest flags");
        }
    }

    public boolean worldGuardChecker(Player p) {

        if (!config.getBoolean(ConfigKey.WORLD_GUARD_DETECTION)) {
            return true;
        }
        try {
            final RegionQuery query = WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery();
            final ApplicableRegionSet set = query.getApplicableRegions(BukkitAdapter.adapt(p.getLocation()));
            final UUID uuid = p.getUniqueId();
            boolean defaultAllow = config.getBoolean(ConfigKey.WORLD_GUARD_FLAG_DEFAULT);
            List<ProtectedRegion> regions = new ArrayList<>(set.getRegions());
            regions.sort(Comparator.comparingInt(ProtectedRegion::getPriority).reversed());
            for (ProtectedRegion region : regions) {
                final boolean isOwner = region.getOwners().contains(uuid);
                final boolean isMember = region.getMembers().contains(uuid);
                final boolean isGuest = !isOwner && !isMember;
                if (isOwner) {
                    StateFlag.State owner = region.getFlag(DEADCHEST_OWNER_FLAG);
                    if (owner == StateFlag.State.DENY) {
                        generateLog("Player [" + p.getName() + "] died without [WorldGuard] owner permission: No Deadchest generated");
                        return false;
                    }
                    if (owner == StateFlag.State.ALLOW) return true;
                }
                if (isMember) {
                    StateFlag.State member = region.getFlag(DEADCHEST_MEMBER_FLAG);
                    if (member == StateFlag.State.DENY) {
                        generateLog("Player [" + p.getName() + "] died without [WorldGuard] member permission: No Deadchest generated");
                        return false;
                    }
                    if (member == StateFlag.State.ALLOW) return true;
                }
                if (isGuest) {
                    StateFlag.State guest = region.getFlag(DEADCHEST_GUEST_FLAG);
                    if (guest == StateFlag.State.DENY) {
                        generateLog("Player [" + p.getName() + "] died without [WorldGuard] guest permission: No Deadchest generated");
                        return false;
                    }
                    if (guest == StateFlag.State.ALLOW) return true;
                }
            }
            if(!defaultAllow)
                generateLog("Player [" + p.getName() + "] died without any [WorldGuard] permission: No Deadchest generated");
            return defaultAllow;
        } catch (NoClassDefFoundError e) {
            return true;
        }
    }

    private State checkRegionFlag(ProtectedRegion region, StateFlag flag, DefaultDomain uuids, UUID playerUUID) {
        StateFlag.State state = region.getFlag(flag);
        if (state == null) return State.NONE;
        if (state == StateFlag.State.DENY) return State.DENY;
        return state == StateFlag.State.ALLOW && uuids.contains(playerUUID) ? State.ALLOWED : State.NOT_APPLICABLE;
    }

    private enum State {
        NONE,
        DENY,
        ALLOWED,
        NOT_APPLICABLE
    }
}
