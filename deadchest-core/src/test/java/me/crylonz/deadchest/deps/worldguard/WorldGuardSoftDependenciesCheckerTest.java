package me.crylonz.deadchest.deps.worldguard;

import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.protection.RegionResultSet;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.regions.GlobalProtectedRegion;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.UUID;

import static com.sk89q.worldguard.protection.flags.StateFlag.State.ALLOW;
import static com.sk89q.worldguard.protection.flags.StateFlag.State.DENY;
import static me.crylonz.deadchest.deps.worldguard.WorldGuardSoftDependenciesChecker.*;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class WorldGuardSoftDependenciesCheckerTest {
    private LocalPlayer player;
    private UUID uuid;

    @BeforeEach
    void setUp() {
        DEADCHEST_OWNER_FLAG = new StateFlag("dc-owner", false);
        DEADCHEST_MEMBER_FLAG = new StateFlag("dc-member", false);
        DEADCHEST_GUEST_FLAG = new StateFlag("dc-guest", false);
        uuid = UUID.randomUUID();
        player = mock(LocalPlayer.class);
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.getName()).thenReturn("Steve");
        when(player.getAssociation(any())).thenCallRealMethod();
    }

    private ProtectedRegion region(String name, int priority) {
        ProtectedRegion region = new ProtectedCuboidRegion(name,
                BlockVector3.at(0, 0, 0), BlockVector3.at(10, 10, 10));
        region.setPriority(priority);
        return region;
    }

    private boolean allows(boolean fallback, ProtectedRegion global, ProtectedRegion... regions) {
        return allowsDeadChest(new RegionResultSet(new HashSet<>(Arrays.asList(regions)), global),
                global, player, fallback);
    }

    @Test
    void usesConfiguredDefaultWhenNoApplicableFlagExists() {
        assertTrue(allows(true, null));
        assertFalse(allows(false, null));
        assertTrue(allows(true, null, region("empty", 0)));
    }

    @Test
    void denyWinsAtEqualPriorityRegardlessOfOrder() {
        ProtectedRegion allow = region("allow", 5);
        ProtectedRegion deny = region("deny", 5);
        allow.setFlag(DEADCHEST_GUEST_FLAG, ALLOW);
        deny.setFlag(DEADCHEST_GUEST_FLAG, DENY);
        assertFalse(allows(true, null, allow, deny));
        assertFalse(allows(true, null, deny, allow));
    }

    @Test
    void higherPriorityWinsEvenWhenPlayerHasDifferentRoles() {
        ProtectedRegion outer = region("outer", 0);
        ProtectedRegion inner = region("inner", 10);
        outer.setFlag(DEADCHEST_GUEST_FLAG, DENY);
        inner.getMembers().addPlayer(uuid);
        inner.setFlag(DEADCHEST_MEMBER_FLAG, ALLOW);
        assertTrue(allows(false, null, outer, inner));
        inner.setFlag(DEADCHEST_MEMBER_FLAG, DENY);
        outer.setFlag(DEADCHEST_GUEST_FLAG, ALLOW);
        assertFalse(allows(true, null, outer, inner));
    }

    @Test
    void unrelatedRoleDenialsDoNotBlockGuests() {
        ProtectedRegion region = region("spawn", 0);
        region.setFlag(DEADCHEST_OWNER_FLAG, DENY);
        region.setFlag(DEADCHEST_MEMBER_FLAG, DENY);
        region.setFlag(DEADCHEST_GUEST_FLAG, ALLOW);
        assertTrue(allows(false, null, region));
    }

    @Test
    void inheritsMembershipAndFlagsFromNonSpatialParent() throws Exception {
        ProtectedRegion parent = new GlobalProtectedRegion("template");
        parent.getMembers().addPlayer(uuid);
        parent.setFlag(DEADCHEST_MEMBER_FLAG, ALLOW);
        ProtectedRegion child = region("plot", 0);
        child.setParent(parent);
        assertTrue(allows(false, null, child));
        child.setFlag(DEADCHEST_MEMBER_FLAG, DENY);
        assertFalse(allows(true, null, child));
    }

    @Test
    void childOverridesParentAtEqualPriority() throws Exception {
        ProtectedRegion parent = region("parent", 0);
        ProtectedRegion child = region("child", 0);
        child.setParent(parent);
        parent.setFlag(DEADCHEST_GUEST_FLAG, DENY);
        child.setFlag(DEADCHEST_GUEST_FLAG, ALLOW);
        assertTrue(allows(false, null, parent, child));
    }

    @Test
    void groupOwnersUseOwnerFlag() {
        ProtectedRegion region = region("owned", 0);
        region.getOwners().addGroup("builders");
        when(player.hasGroup("builders")).thenReturn(true);
        region.setFlag(DEADCHEST_OWNER_FLAG, ALLOW);
        region.setFlag(DEADCHEST_MEMBER_FLAG, DENY);
        region.setFlag(DEADCHEST_GUEST_FLAG, DENY);
        assertTrue(allows(false, null, region));
    }

    @Test
    void globalFlagIsFallbackBelowEvenNegativeRegionPriority() {
        ProtectedRegion global = new GlobalProtectedRegion(ProtectedRegion.GLOBAL_REGION);
        global.setFlag(DEADCHEST_GUEST_FLAG, DENY);
        assertFalse(allows(true, global));
        ProtectedRegion local = region("local", -10);
        local.setFlag(DEADCHEST_GUEST_FLAG, ALLOW);
        assertTrue(allows(false, global, local));
    }
}
