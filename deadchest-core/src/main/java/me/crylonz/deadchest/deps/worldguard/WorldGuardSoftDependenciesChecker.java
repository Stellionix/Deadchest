package me.crylonz.deadchest.deps.worldguard;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.FlagValueCalculator;
import com.sk89q.worldguard.protection.association.RegionAssociable;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.flags.registry.FlagConflictException;
import com.sk89q.worldguard.protection.flags.registry.FlagRegistry;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import com.sk89q.worldguard.protection.util.NormativeOrders;
import me.crylonz.deadchest.DeadChestLoader;
import me.crylonz.deadchest.utils.ConfigKey;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

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
            final LocalPlayer player = WorldGuardPlugin.inst().wrapPlayer(p);
            final RegionManager manager = WorldGuard.getInstance().getPlatform().getRegionContainer()
                    .get(BukkitAdapter.adapt(p.getWorld()));
            final ProtectedRegion global = manager == null ? null : manager.getRegion(ProtectedRegion.GLOBAL_REGION);
            boolean allowed = allowsDeadChest(set, global, player,
                    config.getBoolean(ConfigKey.WORLD_GUARD_FLAG_DEFAULT));
            if (!allowed) {
                generateLog("Player [" + p.getName() + "] died without [WorldGuard] permission: No Deadchest generated");
            }
            return allowed;
        } catch (NoClassDefFoundError e) {
            return true;
        }
    }

    // Only used for calculation, never registered or saved. An absent value
    // must defer to DeadChest's configured default.
    private static final StateFlag EFFECTIVE_FLAG = new StateFlag("dc-effective", false) {
        @Override
        public State getDefault() {
            return null;
        }
    };

    static boolean allowsDeadChest(ApplicableRegionSet set, ProtectedRegion global,
                                   LocalPlayer player, boolean defaultAllow) {
        if (set.isVirtual()) {
            return set.testState(player, EFFECTIVE_FLAG);
        }
        List<ProtectedRegion> regions = new ArrayList<>(set.getRegions());
        regions.remove(global);
        NormativeOrders.sort(regions);
        FlagValueCalculator calculator = new FlagValueCalculator(regions, global) {
            @Override
            @SuppressWarnings("unchecked")
            public <V> V getEffectiveFlag(ProtectedRegion region, Flag<V> flag, RegionAssociable subject) {
                if (flag != EFFECTIVE_FLAG) {
                    return super.getEffectiveFlag(region, flag, subject);
                }
                // WorldGuard includes inherited ownership/membership and groups.
                // Owners use dc-owner, members dc-member, and everyone else dc-guest.
                StateFlag roleFlag = region.isOwner(player) ? DEADCHEST_OWNER_FLAG
                        : region.isMember(player) ? DEADCHEST_MEMBER_FLAG : DEADCHEST_GUEST_FLAG;
                return (V) super.getEffectiveFlag(region, roleFlag, subject);
            }
        };
        // One decision across all roles lets WorldGuard resolve priorities,
        // inheritance and deny/allow conflicts even when roles differ by region.
        StateFlag.State state = calculator.queryState(player, EFFECTIVE_FLAG);
        return state == null ? defaultAllow : state == StateFlag.State.ALLOW;
    }
}
