package com.novaterm.app.receiver

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.Constraints
import androidx.work.BackoffPolicy
import com.novaterm.app.service.TerminalService
import com.novaterm.core.session.persistence.SessionStore
import java.util.concurrent.TimeUnit

/**
 * WorkManager worker that retries starting TerminalService after boot
 * if the direct [BootReceiver] attempt failed (e.g., Android 12+
 * foreground service start restrictions or OEM battery optimization).
 *
 * Scheduled by [BootReceiver] with exponential backoff. Up to 3 retries
 * over ~10 minutes to handle transient restrictions.
 */
class SessionRestoreWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val sessionStore = SessionStore(applicationContext)
        if (!sessionStore.hasSavedSessions()) {
            Log.i(TAG, "No saved sessions to restore")
            return Result.success()
        }

        return try {
            applicationContext.startForegroundService(
                Intent(applicationContext, TerminalService::class.java)
            )
            Log.i(TAG, "TerminalService started via WorkManager retry")
            Result.success()
        } catch (e: Exception) {
            Log.w(TAG, "WorkManager retry failed (attempt $runAttemptCount)", e)
            if (runAttemptCount >= MAX_RETRIES) {
                Log.e(TAG, "Max retries reached, giving up")
                Result.failure()
            } else {
                Result.retry()
            }
        }
    }

    companion object {
        private const val TAG = "SessionRestoreWorker"
        private const val MAX_RETRIES = 3
        private const val WORK_NAME = "session_restore"

        fun schedule(context: Context) {
            val request = OneTimeWorkRequestBuilder<SessionRestoreWorker>()
                .setConstraints(Constraints.Builder().build())
                .setInitialDelay(30, TimeUnit.SECONDS)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context)
                .enqueue(request)
            Log.i(TAG, "Scheduled session restore retry")
        }
    }
}