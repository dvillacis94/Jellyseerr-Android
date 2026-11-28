package com.nesoi.seerr.android.users

import androidx.annotation.StringRes
import com.nesoi.seerr.android.R
import com.nesoi.seerr.android.core.Permission
import com.nesoi.seerr.android.core.User

/**
 * What someone with Manage Users can do to another user, as Seerr's `routes/user` checks it.
 * User 1 is the server owner.
 */
class UserManagementRules(val actorId: Int, private val actorPermissions: Int) {
    constructor(actor: User) : this(actor.id, actor.permissions)

    val canManageUsers: Boolean get() = isAdmin(actorPermissions) || actorPermissions and Permission.MANAGE_USERS != 0
    val isOwner: Boolean get() = actorId == OWNER_ID

    fun isYou(user: User) = user.id == actorId

    /** The owner's permissions can't change, and nobody can change their own. */
    fun canEditPermissions(user: User) = canManageUsers && user.id != OWNER_ID && user.id != actorId

    /** The server ignores limits sent for yourself or for anyone with Manage Users. */
    fun canEditLimits(user: User) = canManageUsers && user.id != actorId && !user.has(Permission.MANAGE_USERS)

    /** The owner can't be deleted, and only the owner can delete admins. */
    fun canDelete(user: User) =
        canManageUsers && user.id != OWNER_ID && user.id != actorId && (isOwner || !isAdmin(user.permissions))

    companion object {
        const val OWNER_ID = 1

        private fun isAdmin(permissions: Int) = permissions and Permission.ADMIN != 0

        @StringRes
        fun role(user: User): Int = when {
            user.id == OWNER_ID -> R.string.users_role_owner
            isAdmin(user.permissions) -> R.string.users_role_admin
            else -> R.string.users_role_user
        }
    }
}

/** What an option needs before it can be on: every one of [mask]'s bits, or any one of them. */
data class Requirement(val mask: Int, val any: Boolean = false)

/**
 * One switch in the permission editor, mirroring `PermissionOption` in Seerr's web app.
 * Strings take the media server's name as their one argument, though few use it.
 */
data class PermissionOption(
    val permission: Int,
    @StringRes val title: Int,
    @StringRes val description: Int,
    val requires: List<Requirement> = emptyList(),
    val children: List<PermissionOption> = emptyList(),
) {
    /** Whether the option is ticked, and whether it can be changed. */
    data class State(val isOn: Boolean, val isEnabled: Boolean)

    /**
     * Follows `PermissionOption` in the web app: Admin and parents tick (and lock) what they
     * include, unmet requirements and disabled 4K untick (and lock) the option.
     */
    fun state(
        value: Int,
        parent: PermissionOption?,
        editingOwner: Boolean,
        actorIsOwner: Boolean,
        movie4kEnabled: Boolean,
        series4kEnabled: Boolean,
    ): State {
        var isOn = has(value, permission)
        var isEnabled = true

        if (editingOwner ||
            (permission != Permission.ADMIN && value and Permission.ADMIN != 0) ||
            (permission in AUTO_APPROVALS && has(value, Permission.MANAGE_REQUESTS)) ||
            (parent != null && has(value, parent.permission))
        ) {
            isOn = true
            isEnabled = false
        }
        if (permission == Permission.ADMIN && !actorIsOwner) isEnabled = false

        val unmet = requires.any { if (it.any) !hasAny(value, it.mask) else !hasAll(value, it.mask) }
        val needs4k = when (permission) {
            Permission.REQUEST_4K, Permission.AUTO_APPROVE_4K -> !movie4kEnabled || !series4kEnabled
            Permission.REQUEST_4K_MOVIE, Permission.AUTO_APPROVE_4K_MOVIE -> !movie4kEnabled
            Permission.REQUEST_4K_TV, Permission.AUTO_APPROVE_4K_TV -> !series4kEnabled
            else -> false
        }
        if (unmet || needs4k) {
            isOn = false
            isEnabled = false
        }
        return State(isOn, isEnabled)
    }

    companion object {
        private val AUTO_APPROVALS = setOf(
            Permission.AUTO_APPROVE, Permission.AUTO_APPROVE_MOVIE, Permission.AUTO_APPROVE_TV,
            Permission.AUTO_APPROVE_4K, Permission.AUTO_APPROVE_4K_MOVIE, Permission.AUTO_APPROVE_4K_TV,
        )

        // Like Seerr, an admin has every permission.
        private fun has(value: Int, bit: Int) = value and Permission.ADMIN != 0 || value and bit != 0
        private fun hasAll(value: Int, mask: Int) = value and Permission.ADMIN != 0 || value and mask == mask
        private fun hasAny(value: Int, mask: Int) = value and Permission.ADMIN != 0 || value and mask != 0

        /** The web app's list, in its order. */
        val all: List<PermissionOption> by lazy {
            val request = Requirement(Permission.REQUEST)
            val requestMovie = Requirement(Permission.REQUEST or Permission.REQUEST_MOVIE, any = true)
            val requestTv = Requirement(Permission.REQUEST or Permission.REQUEST_TV, any = true)
            listOf(
                PermissionOption(Permission.ADMIN, R.string.perm_admin, R.string.perm_admin_desc),
                PermissionOption(Permission.MANAGE_USERS, R.string.perm_manage_users, R.string.perm_manage_users_desc),
                PermissionOption(
                    Permission.MANAGE_REQUESTS, R.string.perm_manage_requests, R.string.perm_manage_requests_desc,
                    children = listOf(
                        PermissionOption(Permission.REQUEST_ADVANCED, R.string.perm_advanced, R.string.perm_advanced_desc),
                        PermissionOption(Permission.REQUEST_VIEW, R.string.perm_view_requests, R.string.perm_view_requests_desc),
                        PermissionOption(Permission.RECENT_VIEW, R.string.perm_recent, R.string.perm_recent_desc),
                        PermissionOption(Permission.WATCHLIST_VIEW, R.string.perm_view_watchlists, R.string.perm_view_watchlists_desc),
                    ),
                ),
                PermissionOption(
                    Permission.REQUEST, R.string.perm_request, R.string.perm_request_desc,
                    children = listOf(
                        PermissionOption(Permission.REQUEST_MOVIE, R.string.perm_request_movies, R.string.perm_request_movies_desc),
                        PermissionOption(Permission.REQUEST_TV, R.string.perm_request_series, R.string.perm_request_series_desc),
                    ),
                ),
                PermissionOption(
                    Permission.AUTO_APPROVE, R.string.perm_auto_approve, R.string.perm_auto_approve_desc,
                    requires = listOf(request),
                    children = listOf(
                        PermissionOption(Permission.AUTO_APPROVE_MOVIE, R.string.perm_auto_approve_movies, R.string.perm_auto_approve_movies_desc, requires = listOf(requestMovie)),
                        PermissionOption(Permission.AUTO_APPROVE_TV, R.string.perm_auto_approve_series, R.string.perm_auto_approve_series_desc, requires = listOf(requestTv)),
                    ),
                ),
                PermissionOption(
                    Permission.AUTO_REQUEST, R.string.perm_auto_request, R.string.perm_auto_request_desc,
                    requires = listOf(request),
                    children = listOf(
                        PermissionOption(Permission.AUTO_REQUEST_MOVIE, R.string.perm_auto_request_movies, R.string.perm_auto_request_movies_desc, requires = listOf(requestMovie)),
                        PermissionOption(Permission.AUTO_REQUEST_TV, R.string.perm_auto_request_series, R.string.perm_auto_request_series_desc, requires = listOf(requestTv)),
                    ),
                ),
                PermissionOption(
                    Permission.REQUEST_4K, R.string.perm_request_4k, R.string.perm_request_4k_desc,
                    children = listOf(
                        PermissionOption(Permission.REQUEST_4K_MOVIE, R.string.perm_request_4k_movies, R.string.perm_request_4k_movies_desc),
                        PermissionOption(Permission.REQUEST_4K_TV, R.string.perm_request_4k_series, R.string.perm_request_4k_series_desc),
                    ),
                ),
                PermissionOption(
                    Permission.AUTO_APPROVE_4K, R.string.perm_auto_approve_4k, R.string.perm_auto_approve_4k_desc,
                    requires = listOf(Requirement(Permission.REQUEST_4K)),
                    children = listOf(
                        PermissionOption(
                            Permission.AUTO_APPROVE_4K_MOVIE, R.string.perm_auto_approve_4k_movies, R.string.perm_auto_approve_4k_movies_desc,
                            requires = listOf(Requirement(Permission.REQUEST_4K or Permission.REQUEST_4K_MOVIE, any = true)),
                        ),
                        PermissionOption(
                            Permission.AUTO_APPROVE_4K_TV, R.string.perm_auto_approve_4k_series, R.string.perm_auto_approve_4k_series_desc,
                            requires = listOf(Requirement(Permission.REQUEST_4K or Permission.REQUEST_4K_TV, any = true)),
                        ),
                    ),
                ),
                PermissionOption(
                    Permission.MANAGE_ISSUES, R.string.perm_manage_issues, R.string.perm_manage_issues_desc,
                    children = listOf(
                        PermissionOption(Permission.CREATE_ISSUES, R.string.perm_report_issues, R.string.perm_report_issues_desc),
                        PermissionOption(Permission.VIEW_ISSUES, R.string.perm_view_issues, R.string.perm_view_issues_desc),
                    ),
                ),
                PermissionOption(
                    Permission.MANAGE_BLOCKLIST, R.string.perm_manage_blocklist, R.string.perm_manage_blocklist_desc,
                    children = listOf(
                        PermissionOption(Permission.VIEW_BLOCKLIST, R.string.perm_view_blocklist, R.string.perm_view_blocklist_desc),
                    ),
                ),
            )
        }
    }
}

/**
 * A user's request limit for movies or series, as edited: the server default, or their own
 * number of requests per number of days. Like the web app, it counts as an override only when
 * both values are set.
 */
data class QuotaOverride(val isOverridden: Boolean, val limit: Int, val days: Int) {
    /** What to save for the limit: nothing means the server default applies. */
    val limitValue: Int? get() = if (isOverridden) limit else null
    val daysValue: Int? get() = if (isOverridden) days else null

    companion object {
        const val DEFAULT_DAYS = 7

        fun of(limit: Int?, days: Int?, defaultLimit: Int?, defaultDays: Int?) = QuotaOverride(
            isOverridden = limit != null && days != null,
            limit = limit ?: defaultLimit ?: 0,
            days = days ?: defaultDays ?: DEFAULT_DAYS,
        )
    }
}
