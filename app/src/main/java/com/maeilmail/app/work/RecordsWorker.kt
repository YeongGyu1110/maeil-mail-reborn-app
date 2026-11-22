package com.maeilmail.app.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.maeilmail.app.account.Account
import com.maeilmail.app.account.Records
import java.util.concurrent.TimeUnit

/**
 * 보내지 못한 학습 기록 변경을 백그라운드에서 보낸다.
 * 안드로이드는 뒤로 간 앱의 인터넷을 막으므로, 앱을 닫기 직전에 바꾼 기록은 앱 안에서 다시 보내지 못한다.
 * 이 작업은 인터넷이 될 때 시스템이 돌려 주어 그런 변경도 보낸다.
 */
class RecordsWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        Account.init(applicationContext)
        Records.init(applicationContext)
        return if (Records.flushNow()) Result.success() else Result.retry()
    }

    companion object {
        private const val NAME = "records_flush"

        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<RecordsWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(NAME, ExistingWorkPolicy.KEEP, request)
        }
    }
}
