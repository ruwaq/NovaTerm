package com.novaterm.core.common.contract

import com.novaterm.core.common.model.SessionInfo

/**
 * Wraps a platform notification object.
 * Implementations hold the actual android.app.Notification
 * without leaking the Android dependency into the common module.
 */
interface ServiceNotification {
    /** The underlying platform notification. Consumers cast to android.app.Notification. */
    val platformNotification: Any
}

/**
 * Contract for showing/updating service notifications.
 * Keeps notification logic out of the Service class.
 */
interface NotificationProvider {

    /**
     * Build the foreground service notification.
     * Returns a [ServiceNotification] wrapper instead of raw Any.
     */
    fun buildServiceNotification(
        sessions: List<SessionInfo>,
        wakeLockHeld: Boolean,
    ): ServiceNotification

    /** Show a transient alert notification. */
    fun showAlert(title: String, message: String)

    /** Dismiss a previously shown notification. */
    fun dismiss(notificationId: Int)
}
