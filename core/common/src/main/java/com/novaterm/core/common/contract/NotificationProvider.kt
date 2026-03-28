package com.novaterm.core.common.contract

import com.novaterm.core.common.model.SessionInfo

/**
 * Contract for showing/updating service notifications.
 * Keeps notification logic out of the Service class.
 */
interface NotificationProvider {

    fun buildServiceNotification(
        sessions: List<SessionInfo>,
        wakeLockHeld: Boolean,
    ): Any // Returns platform Notification; Any avoids Android dependency in common

    fun showAlert(title: String, message: String)

    fun dismiss(notificationId: Int)
}
