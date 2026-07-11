package team.starm.starmskyblock.permission;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IslandPermissionLevelTest {

    @Test
    void hierarchyOrdering() {
        assertTrue(IslandPermissionLevel.OWNER.getPermissionLevel() > IslandPermissionLevel.ADMIN.getPermissionLevel());
        assertTrue(IslandPermissionLevel.ADMIN.getPermissionLevel() > IslandPermissionLevel.MOD.getPermissionLevel());
        assertTrue(IslandPermissionLevel.MOD.getPermissionLevel() > IslandPermissionLevel.MEMBER.getPermissionLevel());
        assertTrue(IslandPermissionLevel.MEMBER.getPermissionLevel() > IslandPermissionLevel.COOP.getPermissionLevel());
        assertTrue(IslandPermissionLevel.COOP.getPermissionLevel() > IslandPermissionLevel.VISITOR.getPermissionLevel());
    }

    @Test
    void manageableRolesOwner() {
        Set<IslandPermissionLevel> roles = IslandPermissionLevel.getManageableRoles(IslandPermissionLevel.OWNER);
        assertEquals(6, roles.size());
        assertTrue(roles.containsAll(Arrays.asList(IslandPermissionLevel.values())));
    }

    @Test
    void manageableRolesAdmin() {
        Set<IslandPermissionLevel> roles = IslandPermissionLevel.getManageableRoles(IslandPermissionLevel.ADMIN);
        assertEquals(4, roles.size());
        assertTrue(roles.contains(IslandPermissionLevel.MOD));
        assertTrue(roles.contains(IslandPermissionLevel.MEMBER));
        assertTrue(roles.contains(IslandPermissionLevel.COOP));
        assertTrue(roles.contains(IslandPermissionLevel.VISITOR));
        assertFalse(roles.contains(IslandPermissionLevel.OWNER));
        assertFalse(roles.contains(IslandPermissionLevel.ADMIN));
    }

    @Test
    void manageableRolesMod() {
        Set<IslandPermissionLevel> roles = IslandPermissionLevel.getManageableRoles(IslandPermissionLevel.MOD);
        assertEquals(3, roles.size());
        assertTrue(roles.contains(IslandPermissionLevel.MEMBER));
        assertTrue(roles.contains(IslandPermissionLevel.COOP));
        assertTrue(roles.contains(IslandPermissionLevel.VISITOR));
    }

    @Test
    void manageableRolesLowLevelsAreEmpty() {
        assertTrue(IslandPermissionLevel.getManageableRoles(IslandPermissionLevel.MEMBER).isEmpty());
        assertTrue(IslandPermissionLevel.getManageableRoles(IslandPermissionLevel.COOP).isEmpty());
        assertTrue(IslandPermissionLevel.getManageableRoles(IslandPermissionLevel.VISITOR).isEmpty());
    }

    @Test
    void hasPermissionOwnerAlwaysTrue() {
        assertTrue(IslandPermissionLevel.OWNER.hasPermission(IslandPermission.BUILD, null));
        assertTrue(IslandPermissionLevel.OWNER.hasPermission(IslandPermission.BUILD, 5));
        assertTrue(IslandPermissionLevel.OWNER.hasPermission(IslandPermission.BUILD, 99));
    }

    @Test
    void hasPermissionUnconfiguredReturnsFalse() {
        assertFalse(IslandPermissionLevel.MEMBER.hasPermission(IslandPermission.BUILD, null));
        assertFalse(IslandPermissionLevel.ADMIN.hasPermission(IslandPermission.BUILD, null));
    }

    @Test
    void hasPermissionLevelComparison() {
        // MEMBER(2): min 2 -> true, min 3 -> false
        assertTrue(IslandPermissionLevel.MEMBER.hasPermission(IslandPermission.BUILD, 2));
        assertFalse(IslandPermissionLevel.MEMBER.hasPermission(IslandPermission.BUILD, 3));
        // ADMIN(4): min 5 -> false, min 4 -> true
        assertFalse(IslandPermissionLevel.ADMIN.hasPermission(IslandPermission.BUILD, 5));
        assertTrue(IslandPermissionLevel.ADMIN.hasPermission(IslandPermission.BUILD, 4));
    }
}
