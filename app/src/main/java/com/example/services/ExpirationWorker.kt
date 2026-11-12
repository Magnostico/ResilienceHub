package com.example.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.AppDatabase
import com.example.data.model.ItemAlertStatus
import com.example.domain.RationingAndAlertEngine
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Worker do Android WorkManager para verificação autônoma e periódica (diária)
 * de prazos de validade (FEFO) e ciclos de recarga de baterias (90 dias).
 * 100% Offline, sem exigir rede ou servidor.
 */
class ExpirationWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "ExpirationWorker"
        const val WORK_NAME_PERIODIC = "resilience_daily_expiration_check"
        const val WORK_NAME_IMMEDIATE = "resilience_immediate_expiration_check"
        const val CHANNEL_ID = "resilience_expiration_alerts_channel"
        const val NOTIFICATION_ID = 1001

        /**
         * Agenda a rotina diária no WorkManager (a cada 24 horas).
         * Sem restrição de conexão de rede para operar 100% offline.
         */
        fun scheduleDailyCheck(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresCharging(false)
                .setRequiresDeviceIdle(false)
                .build()

            val dailyRequest = PeriodicWorkRequestBuilder<ExpirationWorker>(
                24, TimeUnit.HOURS,
                1, TimeUnit.HOURS // janela de flexibilidade
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME_PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,
                dailyRequest
            )
            Log.d(TAG, "Agendamento diário de verificação de validades registrado no WorkManager.")
        }

        /**
         * Dispara uma verificação imediata (OneTimeWorkRequest) sob demanda.
         */
        fun runImmediateCheck(context: Context) {
            val oneTime = OneTimeWorkRequestBuilder<ExpirationWorker>().build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME_IMMEDIATE,
                ExistingWorkPolicy.REPLACE,
                oneTime
            )
        }
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Iniciando verificação autônoma diária de estoque e baterias...")

        return try {
            val database = AppDatabase.getInstance(context)
            // Executa também a rotina diária de Auto-Backup com rotação de 3 arquivos (sem entupir o celular)
            runCatching {
                BackupManager(context, database).performRollingBackup(isAutoBackup = true)
            }

            val allItems = database.inventoryDao().getAllItems().first()

            val engine = RationingAndAlertEngine()
            val now = System.currentTimeMillis()

            val alertItems = allItems.map { item ->
                engine.evaluateItemStatus(item, now)
            }.filter { it.status != ItemAlertStatus.OK }

            if (alertItems.isNotEmpty()) {
                val batteryAlerts = alertItems.filter { it.status == ItemAlertStatus.RECARREGAR_BATERIA }
                val criticalAlerts = alertItems.filter { it.status == ItemAlertStatus.CRITICO_30_DIAS }
                val rotateAlerts = alertItems.filter { it.status == ItemAlertStatus.GIRAR_90_DIAS }
                val expiredAlerts = alertItems.filter { it.status == ItemAlertStatus.VENCIDO }

                showNotification(
                    totalAlerts = alertItems.size,
                    batteryCount = batteryAlerts.size,
                    criticalCount = criticalAlerts.size,
                    rotateCount = rotateAlerts.size,
                    expiredCount = expiredAlerts.size,
                    topAlertItems = alertItems.take(4)
                )
            } else {
                Log.d(TAG, "Todos os itens do estoque estão em conformidade normal.")
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Falha na execução do ExpirationWorker", e)
            Result.retry()
        }
    }

    private fun showNotification(
        totalAlerts: Int,
        batteryCount: Int,
        criticalCount: Int,
        rotateCount: Int,
        expiredCount: Int,
        topAlertItems: List<RationingAndAlertEngine.ItemEvaluationResult>
    ) {
        // Checagem de permissão de notificação no Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasPermission) {
                Log.w(TAG, "Sem permissão POST_NOTIFICATIONS concedida para exibir alerta.")
                return
            }
        }

        createNotificationChannelIfNeeded()

        val title = "ResilienceHub: $totalAlerts Itens Requerem Atenção"
        val inboxStyle = NotificationCompat.InboxStyle()
            .setBigContentTitle(title)
            .setSummaryText("Estoque FEFO & Baterias")

        if (batteryCount > 0) {
            inboxStyle.addLine("⚡ $batteryCount equipamento(s) sem recarga há >= 90 dias")
        }
        if (criticalCount > 0) {
            inboxStyle.addLine("⚠ $criticalCount item(ns) com validade crítica (<= 30 dias)")
        }
        if (rotateCount > 0) {
            inboxStyle.addLine("🔄 $rotateCount item(ns) na faixa de giro doméstico (<= 90 dias)")
        }
        if (expiredCount > 0) {
            inboxStyle.addLine("❌ $expiredCount item(ns) vencido(s)")
        }

        topAlertItems.forEach { eval ->
            inboxStyle.addLine("• ${eval.item.name}: ${eval.status.title}")
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText("$totalAlerts item(ns) precisam de giro, recarga ou substituição preventiva.")
            .setStyle(inboxStyle)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
            Log.d(TAG, "Notificação de alerta local agrupada disparada com sucesso.")
        } catch (e: SecurityException) {
            Log.e(TAG, "Permissão negada ao enviar notificação", e)
        }
    }

    private fun createNotificationChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Alertas de Validade e Bateria - ResilienceHub",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificações diárias sobre itens próximos ao vencimento e ciclos de recarga de baterias"
                enableVibration(true)
            }

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}
