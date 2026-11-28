package com.nesoi.seerr.android.users

import com.nesoi.seerr.android.R
import com.nesoi.seerr.android.core.Permission
import com.nesoi.seerr.android.core.User
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UserManagementTest {
    private fun user(id: Int, permissions: Int = Permission.REQUEST) = User(id = id, permissions = permissions)
    private val owner = user(1, Permission.ADMIN)
    private val manager = user(5, Permission.MANAGE_USERS)
    private val admin = user(6, Permission.ADMIN)
    private val regular = user(7)

    // Rules

    @Test fun `only people with manage users manage users`() {
        assertTrue(UserManagementRules(manager).canManageUsers)
        assertTrue(UserManagementRules(admin).canManageUsers)
        assertFalse(UserManagementRules(regular).canManageUsers)
        assertFalse(UserManagementRules(regular).canDelete(user(8)))
    }

    @Test fun `nobody edits the owner or themselves`() {
        val rules = UserManagementRules(manager)
        assertFalse(rules.canEditPermissions(owner))
        assertFalse(rules.canEditPermissions(manager))
        assertTrue(rules.canEditPermissions(regular))
    }

    @Test fun `limits are for users without manage users, and not for yourself`() {
        val rules = UserManagementRules(manager)
        assertTrue(rules.canEditLimits(regular))
        assertFalse(rules.canEditLimits(user(9, Permission.MANAGE_USERS)))
        assertFalse(rules.canEditLimits(admin))
        assertFalse(rules.canEditLimits(manager))
    }

    @Test fun `the owner can't be deleted and only the owner deletes admins`() {
        val byManager = UserManagementRules(manager)
        assertTrue(byManager.canDelete(regular))
        assertFalse(byManager.canDelete(admin))
        assertFalse(byManager.canDelete(owner))
        assertFalse(byManager.canDelete(manager))
        val byOwner = UserManagementRules(owner)
        assertTrue(byOwner.canDelete(admin))
        assertFalse(byOwner.canDelete(owner))
    }

    @Test fun `roles`() {
        assertEquals(R.string.users_role_owner, UserManagementRules.role(owner))
        assertEquals(R.string.users_role_admin, UserManagementRules.role(admin))
        assertEquals(R.string.users_role_user, UserManagementRules.role(regular))
    }

    // Permission options

    private fun option(permission: Int): PermissionOption =
        PermissionOption.all.flatMap { listOf(it) + it.children }.first { it.permission == permission }

    private fun parentOf(permission: Int): PermissionOption? = PermissionOption.all.firstOrNull { p -> p.children.any { it.permission == permission } }

    private fun state(permission: Int, value: Int, owner: Boolean = true, movie4k: Boolean = true, series4k: Boolean = true, editingOwner: Boolean = false) =
        option(permission).state(value, parentOf(permission), editingOwner, owner, movie4k, series4k)

    @Test fun `the list follows the web app's order and covers every option once`() {
        val all = PermissionOption.all.flatMap { listOf(it) + it.children }
        assertEquals(all.size, all.map { it.permission }.toSet().size)
        assertEquals(Permission.ADMIN, PermissionOption.all.first().permission)
        assertEquals(Permission.MANAGE_BLOCKLIST, PermissionOption.all.last().permission)
    }

    @Test fun `an option reflects its own bit and can be changed`() {
        assertEquals(PermissionOption.State(true, true), state(Permission.REQUEST, Permission.REQUEST))
        assertEquals(PermissionOption.State(false, true), state(Permission.REQUEST, 0))
    }

    @Test fun `admin ticks and locks everything but itself`() {
        assertEquals(PermissionOption.State(true, false), state(Permission.MANAGE_ISSUES, Permission.ADMIN))
        assertEquals(PermissionOption.State(true, true), state(Permission.ADMIN, Permission.ADMIN))
    }

    @Test fun `only the owner can grant admin`() {
        assertTrue(state(Permission.ADMIN, 0, owner = true).isEnabled)
        assertFalse(state(Permission.ADMIN, 0, owner = false).isEnabled)
    }

    @Test fun `a parent ticks and locks its children`() {
        assertEquals(PermissionOption.State(true, false), state(Permission.REQUEST_MOVIE, Permission.REQUEST))
        assertEquals(PermissionOption.State(false, true), state(Permission.REQUEST_MOVIE, 0))
    }

    @Test fun `managing requests approves automatically`() {
        assertEquals(PermissionOption.State(true, false), state(Permission.AUTO_APPROVE, Permission.MANAGE_REQUESTS or Permission.REQUEST))
    }

    @Test fun `auto-approve needs request`() {
        assertEquals(PermissionOption.State(false, false), state(Permission.AUTO_APPROVE, 0))
        assertTrue(state(Permission.AUTO_APPROVE, Permission.REQUEST).isEnabled)
    }

    @Test fun `auto-approve movies needs request or request movies`() {
        assertFalse(state(Permission.AUTO_APPROVE_MOVIE, 0).isEnabled)
        assertTrue(state(Permission.AUTO_APPROVE_MOVIE, Permission.REQUEST_MOVIE).isEnabled)
    }

    @Test fun `without a 4K server the 4K options are off and locked`() {
        val with4k = Permission.REQUEST_4K
        assertEquals(PermissionOption.State(true, true), state(Permission.REQUEST_4K, with4k))
        assertEquals(PermissionOption.State(false, false), state(Permission.REQUEST_4K, with4k, movie4k = false))
        assertEquals(PermissionOption.State(false, false), state(Permission.REQUEST_4K, with4k, series4k = false))
        // A movie-only 4K setup still offers 4K movies.
        assertTrue(state(Permission.REQUEST_4K_MOVIE, 0, series4k = false).isEnabled)
        assertFalse(state(Permission.REQUEST_4K_TV, 0, series4k = false).isEnabled)
    }

    @Test fun `the owner's options are all on and locked`() {
        assertEquals(PermissionOption.State(true, false), state(Permission.MANAGE_BLOCKLIST, 0, editingOwner = true))
    }

    // Quotas

    @Test fun `a limit is an override only with both values`() {
        assertTrue(QuotaOverride.of(5, 3, 1, 1).isOverridden)
        assertFalse(QuotaOverride.of(5, null, 1, 1).isOverridden)
        assertFalse(QuotaOverride.of(null, 3, 1, 1).isOverridden)
    }

    @Test fun `without an override the editor starts from the server default`() {
        val quota = QuotaOverride.of(null, null, 10, 14)
        assertEquals(10, quota.limit)
        assertEquals(14, quota.days)
        assertNull(quota.limitValue)
        assertNull(quota.daysValue)
        assertEquals(QuotaOverride.DEFAULT_DAYS, QuotaOverride.of(null, null, null, null).days)
    }

    @Test fun `an override saves its numbers`() {
        val quota = QuotaOverride.of(0, 7, null, null)
        assertEquals(0, quota.limitValue)
        assertEquals(7, quota.daysValue)
    }
}
