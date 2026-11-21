package com.maeilmail.app.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.maeilmail.app.data.Library
import java.util.concurrent.TimeUnit

/**
 * 백그라운드에서 웹 서비스 저장소와 맞춘다 (질문과 발송 기록). 맞춘 뒤 알림을 다시 건다 (Library.sync).
 * - 6시간마다: 앱을 열지 않아도 앞으로 받을 질문(next)을 받아 두어 알림이 빠지지 않게 한다
 * - 알림이 울린 뒤 한 번
 * 인터넷이 될 때만 돈다. 실패하면 WorkManager가 나중에 다시 시도한다.
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        Library.init(applicationContext)
        return if (Library.sync()) Result.success() else Result.retry()
    }

    companion object {
        private const val PERIODIC = "sync_periodic"
        private const val ONCE = "sync_once"
        private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS)
                .setConstraints(online)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun runOnce(context: Context) {
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(online)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(ONCE, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
