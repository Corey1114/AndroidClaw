package com.androidclaw.agent

import android.content.Context
import androidx.work.*
import com.androidclaw.models.CronJob
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * CronScheduler
 * Schedule tasks to run at specific times daily.
 * Uses WorkManager - survives battery optimization, reboots.
 */
class CronScheduler(private val context: Context) {

    private val gson = Gson()

    fun scheduleJob(job: CronJob) {
        val delay = calculateDelayMillis(job.cronExpression)

        val data = workDataOf(
            "job_id" to job.id,
            "prompt" to job.prompt,
            "channel" to job.channel
        )

        val request = OneTimeWorkRequestBuilder<CronWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(data)
            .addTag(job.id)
            .build()

        WorkManager.getInstance(context).enqueue(request)
    }

    fun cancelJob(jobId: String) {
        WorkManager.getInstance(context).cancelAllWorkByTag(jobId)
    }

    fun saveJobs(jobs: List<CronJob>) {
        val prefs = context.getSharedPreferences("androidclaw_cron", Context.MODE_PRIVATE)
        prefs.edit().putString("jobs", gson.toJson(jobs)).apply()
    }

    fun loadJobs(): List<CronJob> {
        val prefs = context.getSharedPreferences("androidclaw_cron", Context.MODE_PRIVATE)
        val json = prefs.getString("jobs", null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<CronJob>>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun calculateDelayMillis(cronExpression: String): Long {
        // Simple HH:mm format
        return try {
            val parts = cronExpression.split(":")
            val hour = parts[0].toInt()
            val minute = parts[1].toInt()

            val now = Calendar.getInstance()
            val target = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
            }

            if (target.before(now)) {
                target.add(Calendar.DAY_OF_MONTH, 1)
            }

            target.timeInMillis - now.timeInMillis
        } catch (e: Exception) {
            TimeUnit.HOURS.toMillis(1) // default 1 hour
        }
    }
}

class CronWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val prompt = inputData.getString("prompt") ?: return Result.failure()
        AgentService.execute(applicationContext, prompt)
        return Result.success()
    }
}
