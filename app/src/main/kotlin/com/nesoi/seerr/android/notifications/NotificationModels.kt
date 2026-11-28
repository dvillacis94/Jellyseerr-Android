package com.nesoi.seerr.android.notifications

import com.nesoi.seerr.android.R
import com.nesoi.seerr.android.core.Permission
import com.nesoi.seerr.android.core.User
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * An event Seerr can notify about. Each is a bit in a channel's `notificationTypes`, from
 * `Notification` in the server's `lib/notifications`. Listed in the web app's order.
 */
enum class NotificationKind(val bit: Int, val title: Int) {
    MediaAutoRequested(4096, R.string.notify_auto_requested),
    MediaPending(2, R.string.notify_pending),
    MediaAutoApproved(128, R.string.notify_auto_approved),
    MediaApproved(4, R.string.notify_approved),
    MediaDeclined(64, R.string.notify_declined),
    MediaAvailable(8, R.string.notify_available),
    MediaFailed(16, R.string.notify_failed),
    IssueCreated(256, R.string.notify_issue_created),
    IssueComment(512, R.string.notify_issue_comment),
    IssueResolved(1024, R.string.notify_issue_resolved),
    IssueReopened(2048, R.string.notify_issue_reopened);

    fun isOn(types: Int) = types and bit != 0

    /** The web app's wording for a user's own settings. Issue updates read differently for managers. */
    fun description(isIssueManager: Boolean): Int = when (this) {
        MediaAutoRequested -> R.string.notify_auto_requested_desc
        MediaPending -> R.string.notify_pending_desc
        MediaAutoApproved -> R.string.notify_auto_approved_desc
        MediaApproved -> R.string.notify_approved_desc
        MediaDeclined -> R.string.notify_declined_desc
        MediaAvailable -> R.string.notify_available_desc
        MediaFailed -> R.string.notify_failed_desc
        IssueCreated -> R.string.notify_issue_created_desc
        IssueComment -> if (isIssueManager) R.string.notify_issue_comment_desc_manager else R.string.notify_issue_comment_desc
        IssueResolved -> if (isIssueManager) R.string.notify_issue_resolved_desc_manager else R.string.notify_issue_resolved_desc
        IssueReopened -> if (isIssueManager) R.string.notify_issue_reopened_desc_manager else R.string.notify_issue_reopened_desc
    }

    companion object {
        /** `ALL_NOTIFICATIONS` on the server: every bit, including the test notification (32). */
        const val ALL = 8190

        /**
         * Which events this user can choose, following the web app's `NotificationTypeSelector`.
         * The ones about their own requests and issues come first, as there.
         */
        fun availableTo(
            user: User,
            syncsWatchlist: Boolean,
            movie4kEnabled: Boolean,
            series4kEnabled: Boolean,
            enabledTypes: Int = ALL,
        ): List<NotificationKind> {
            fun any(vararg bits: Int) = user.has(*bits)
            val allAutoApproved = any(Permission.MANAGE_REQUESTS) ||
                !any(Permission.REQUEST, Permission.REQUEST_MOVIE, Permission.REQUEST_TV, Permission.REQUEST_4K, Permission.REQUEST_4K_MOVIE, Permission.REQUEST_4K_TV) ||
                ((!any(Permission.REQUEST, Permission.REQUEST_MOVIE) || any(Permission.AUTO_APPROVE, Permission.AUTO_APPROVE_MOVIE)) &&
                    (!any(Permission.REQUEST, Permission.REQUEST_TV) || any(Permission.AUTO_APPROVE, Permission.AUTO_APPROVE_TV)) &&
                    (!movie4kEnabled || !any(Permission.REQUEST_4K, Permission.REQUEST_4K_MOVIE) || any(Permission.AUTO_APPROVE_4K, Permission.AUTO_APPROVE_4K_MOVIE)) &&
                    (!series4kEnabled || !any(Permission.REQUEST_4K, Permission.REQUEST_4K_TV) || any(Permission.AUTO_APPROVE_4K, Permission.AUTO_APPROVE_4K_TV)))
            val managesIssues = any(Permission.MANAGE_ISSUES)

            fun isShown(kind: NotificationKind) = when (kind) {
                MediaAutoRequested -> syncsWatchlist && any(Permission.AUTO_REQUEST, Permission.AUTO_REQUEST_MOVIE, Permission.AUTO_REQUEST_TV)
                MediaPending, MediaAutoApproved, MediaFailed -> any(Permission.MANAGE_REQUESTS)
                MediaApproved, MediaDeclined -> !allAutoApproved
                MediaAvailable -> true
                IssueCreated -> managesIssues
                IssueComment, IssueResolved, IssueReopened -> any(Permission.MANAGE_ISSUES, Permission.CREATE_ISSUES)
            }

            fun isAboutOwnItems(kind: NotificationKind) = when (kind) {
                MediaAutoRequested, MediaApproved, MediaDeclined, MediaAvailable -> true
                IssueComment, IssueResolved, IssueReopened -> !managesIssues
                MediaPending, MediaAutoApproved, MediaFailed, IssueCreated -> false
            }

            val shown = entries.filter { isShown(it) && it.isOn(enabledTypes) }
            return shown.filter(::isAboutOwnItems) + shown.filterNot(::isAboutOwnItems)
        }
    }
}

/** A way Seerr can notify the user. Web push is left out: it needs a browser subscription. */
enum class NotificationChannel(val key: String, val title: Int) {
    Email("email", R.string.channel_email),
    Discord("discord", R.string.channel_discord),
    Telegram("telegram", R.string.channel_telegram),
    Pushbullet("pushbullet", R.string.channel_pushbullet),
    Pushover("pushover", R.string.channel_pushover);

    companion object {
        fun from(key: String?) = entries.firstOrNull { it.key == key }
    }
}

/** What is wrong with a channel's details, using the web app's checks. */
enum class Problem(val message: Int) {
    DiscordIdFormat(R.string.problem_discord_format),
    DiscordIdMissing(R.string.problem_discord_missing),
    TelegramChatMissing(R.string.problem_telegram_chat_missing),
    TelegramChatFormat(R.string.problem_telegram_chat_format),
    TelegramThreadFormat(R.string.problem_telegram_thread_format),
    PushbulletTokenMissing(R.string.problem_pushbullet_missing),
    PushoverAppTokenMissing(R.string.problem_pushover_app_missing),
    PushoverAppTokenFormat(R.string.problem_pushover_app_format),
    PushoverUserKeyMissing(R.string.problem_pushover_user_missing),
    PushoverUserKeyFormat(R.string.problem_pushover_user_format),
}

/**
 * `GET` and `POST /user/{id}/settings/notifications`.
 *
 * The server overwrites every per-user field with what is posted, so everything that was loaded
 * has to be sent back, and an empty field has to be sent as null to clear it. Only
 * `notificationTypes` is merged, per channel.
 */
@Serializable
data class UserNotificationSettings(
    // Server-wide, only in what is loaded.
    val emailEnabled: Boolean? = null,
    val discordEnabled: Boolean? = null,
    /** The events the admin turned on for Discord. Zero unless mentions are enabled. */
    val discordEnabledTypes: Int? = null,
    val telegramEnabled: Boolean? = null,
    val telegramBotUsername: String? = null,
    // The user's own.
    val pgpKey: String? = null,
    val discordIds: List<String>? = null,
    val pushbulletAccessToken: String? = null,
    val pushoverApplicationToken: String? = null,
    val pushoverUserKey: String? = null,
    val pushoverSound: String? = null,
    val telegramChatId: String? = null,
    val telegramMessageThreadId: String? = null,
    val telegramSendSilently: Boolean? = null,
    /** Keyed by channel (`email`, `discord`, `webpush`…). A channel the user never set up can be null. */
    val notificationTypes: Map<String, Int?> = emptyMap(),
) {
    /** Whether the server lets the user set this channel up, as the web app decides. */
    fun offers(channel: NotificationChannel): Boolean = when (channel) {
        NotificationChannel.Email -> emailEnabled == true
        NotificationChannel.Telegram -> telegramEnabled == true && !telegramBotUsername.isNullOrEmpty()
        NotificationChannel.Discord, NotificationChannel.Pushbullet, NotificationChannel.Pushover -> true
    }

    /** The events the admin allows on a channel. Only Discord is limited per user. */
    fun enabledTypes(channel: NotificationChannel): Int =
        if (channel == NotificationChannel.Discord) discordEnabledTypes ?: 0 else NotificationKind.ALL

    /** The user's events for a channel. Email defaults to everything, like on the server. */
    fun types(channel: NotificationChannel): Int {
        val stored = notificationTypes[channel.key] ?: if (channel == NotificationChannel.Email) NotificationKind.ALL else 0
        return stored and enabledTypes(channel)
    }

    fun withTypes(channel: NotificationChannel, types: Int) = copy(notificationTypes = notificationTypes + (channel.key to types))

    fun with(channel: NotificationChannel, kind: NotificationKind, on: Boolean): UserNotificationSettings {
        val current = types(channel)
        return withTypes(channel, if (on) current or kind.bit else current and kind.bit.inv())
    }

    /** Trims the text fields and turns empty ones into null, so the server clears them. */
    fun normalized(): UserNotificationSettings {
        fun clean(value: String?) = value?.trim()?.takeIf { it.isNotEmpty() }
        return copy(
            discordIds = discordIds?.mapNotNull(::clean),
            pushbulletAccessToken = clean(pushbulletAccessToken),
            pushoverApplicationToken = clean(pushoverApplicationToken),
            pushoverUserKey = clean(pushoverUserKey),
            telegramChatId = clean(telegramChatId),
            telegramMessageThreadId = clean(telegramMessageThreadId),
        )
    }

    /** What is wrong with the channel's fields, or null when it can be saved. Expects [normalized] input. */
    fun problem(channel: NotificationChannel): Problem? {
        val needsAccount = types(channel) != 0
        fun blank(value: String?) = value.isNullOrEmpty()
        return when (channel) {
            NotificationChannel.Email -> null
            NotificationChannel.Discord -> {
                val ids = discordIds.orEmpty().filter { it.isNotEmpty() }
                when {
                    ids.any { !DISCORD_ID.matches(it) } -> Problem.DiscordIdFormat
                    needsAccount && ids.isEmpty() -> Problem.DiscordIdMissing
                    else -> null
                }
            }
            NotificationChannel.Telegram -> when {
                needsAccount && blank(telegramChatId) -> Problem.TelegramChatMissing
                !blank(telegramChatId) && !CHAT_ID.matches(telegramChatId!!) -> Problem.TelegramChatFormat
                !blank(telegramMessageThreadId) && !DIGITS.matches(telegramMessageThreadId!!) -> Problem.TelegramThreadFormat
                else -> null
            }
            NotificationChannel.Pushbullet -> if (needsAccount && blank(pushbulletAccessToken)) Problem.PushbulletTokenMissing else null
            NotificationChannel.Pushover -> when {
                needsAccount && blank(pushoverApplicationToken) -> Problem.PushoverAppTokenMissing
                !blank(pushoverApplicationToken) && !PUSHOVER_KEY.matches(pushoverApplicationToken!!) -> Problem.PushoverAppTokenFormat
                needsAccount && blank(pushoverUserKey) -> Problem.PushoverUserKeyMissing
                !blank(pushoverUserKey) && !PUSHOVER_KEY.matches(pushoverUserKey!!) -> Problem.PushoverUserKeyFormat
                else -> null
            }
        }
    }

    /** Every per-user field, with an explicit null for an empty one. */
    fun toRequestBody(): JsonObject = buildJsonObject {
        fun text(name: String, value: String?) = put(name, value?.let { JsonPrimitive(it) } ?: JsonNull)
        text("pgpKey", pgpKey)
        put("discordIds", JsonArray(discordIds.orEmpty().map { JsonPrimitive(it) }))
        text("pushbulletAccessToken", pushbulletAccessToken)
        text("pushoverApplicationToken", pushoverApplicationToken)
        text("pushoverUserKey", pushoverUserKey)
        text("pushoverSound", pushoverSound)
        text("telegramChatId", telegramChatId)
        text("telegramMessageThreadId", telegramMessageThreadId)
        put("telegramSendSilently", telegramSendSilently?.let { JsonPrimitive(it) } ?: JsonNull)
        put("notificationTypes", buildJsonObject { notificationTypes.forEach { (key, value) -> put(key, value?.let { JsonPrimitive(it) } ?: JsonNull) } })
    }

    // Public, because the generated serializer is reached through it.
    companion object {
        private val DISCORD_ID = Regex("^\\d{17,20}$")
        private val CHAT_ID = Regex("^-?\\d+$")
        private val DIGITS = Regex("^\\d+$")
        private val PUSHOVER_KEY = Regex("^[A-Za-z0-9]{30}$")
    }
}
