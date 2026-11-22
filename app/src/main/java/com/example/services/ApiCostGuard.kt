package com.example.services

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Cofre de Orçamento & Ferramentas Online (Offline-First + Proteção Anti-Gasto de API):
 *
 * - Mantém o app 100% Offline-First por padrão (`onlineToolsEnabled = false`).
 * - Impõe travas diárias rígidas para Street View API (padrão: 10/dia) e Cloud Firestore (padrão: 60 ops/dia),
 *   garantindo permanência confortável dentro das faixas gratuitas (R$ 0,00).
 * - Salva em disco (`streetview_cache/`) cada imagem Street View carregada uma única vez:
 *   na próxima consulta daquele local, abre 100% OFFLINE e sem gastar API.
 * - Fornece atalhos 100% GRATUITOS (R$ 0,00 ilimitado) para Street View 360° e Navegação/Satélite
 *   via Google Maps Intents nativos do Android.
 */
class ApiCostGuard(private val context: Context) {

    companion object {
        private const val TAG = "ApiCostGuard"
        private const val PREFS_NAME = "resilience_api_cost_guard"
        private const val KEY_ONLINE_ENABLED = "online_tools_enabled"
        private const val KEY_DAY = "quota_day_yyyyMMdd"
        private const val KEY_SV_COUNT = "sv_calls_today"
        private const val KEY_SV_LIMIT = "sv_daily_limit"
        private const val KEY_CLOUD_OPS = "cloud_ops_today"
        private const val KEY_CLOUD_LIMIT = "cloud_daily_limit"

        const val DEFAULT_STREET_VIEW_DAILY_LIMIT = 10
        const val DEFAULT_CLOUD_OPS_DAILY_LIMIT = 60
        const val STREET_VIEW_CACHE_DIR = "streetview_cache"
    }

    data class Snapshot(
        val onlineToolsEnabled: Boolean = false,
        val hasMapsApiKey: Boolean = false,
        val streetViewCallsToday: Int = 0,
        val streetViewDailyLimit: Int = DEFAULT_STREET_VIEW_DAILY_LIMIT,
        val cloudOpsToday: Int = 0,
        val cloudOpsDailyLimit: Int = DEFAULT_CLOUD_OPS_DAILY_LIMIT,
        val cachedStreetViewImagesCount: Int = 0
    )

    data class StreetViewResult(
        val bitmap: ImageBitmap? = null,
        val fromOfflineCache: Boolean = false,
        val message: String,
        val blockedByBudget: Boolean = false,
        val missingApiKey: Boolean = false
    )

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val streetViewCacheDir = File(context.filesDir, STREET_VIEW_CACHE_DIR).apply {
        if (!exists()) mkdirs()
    }

    private val _snapshot = MutableStateFlow(readSnapshot())
    val snapshot: StateFlow<Snapshot> = _snapshot.asStateFlow()

    private fun todayKey(): String = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())

    private fun resetCountersIfNewDay() {
        val today = todayKey()
        val storedDay = prefs.getString(KEY_DAY, null)
        if (storedDay != today) {
            prefs.edit()
                .putString(KEY_DAY, today)
                .putInt(KEY_SV_COUNT, 0)
                .putInt(KEY_CLOUD_OPS, 0)
                .apply()
        }
    }

    fun getMapsApiKey(): String {
        return try {
            BuildConfig.MAPS_API_KEY.trim()
        } catch (_: Throwable) {
            ""
        }
    }

    private fun countCachedStreetViews(): Int {
        return streetViewCacheDir.listFiles { f -> f.isFile && f.extension.equals("jpg", true) && f.length() > 500L }?.size ?: 0
    }

    private fun readSnapshot(): Snapshot {
        resetCountersIfNewDay()
        return Snapshot(
            onlineToolsEnabled = prefs.getBoolean(KEY_ONLINE_ENABLED, false),
            hasMapsApiKey = getMapsApiKey().isNotBlank(),
            streetViewCallsToday = prefs.getInt(KEY_SV_COUNT, 0),
            streetViewDailyLimit = prefs.getInt(KEY_SV_LIMIT, DEFAULT_STREET_VIEW_DAILY_LIMIT),
            cloudOpsToday = prefs.getInt(KEY_CLOUD_OPS, 0),
            cloudOpsDailyLimit = prefs.getInt(KEY_CLOUD_LIMIT, DEFAULT_CLOUD_OPS_DAILY_LIMIT),
            cachedStreetViewImagesCount = countCachedStreetViews()
        )
    }

    fun refreshSnapshot() {
        _snapshot.value = readSnapshot()
    }

    fun setOnlineToolsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ONLINE_ENABLED, enabled).apply()
        refreshSnapshot()
    }

    fun updateDailyLimits(streetViewLimit: Int, cloudOpsLimit: Int) {
        prefs.edit()
            .putInt(KEY_SV_LIMIT, streetViewLimit.coerceIn(1, 30))
            .putInt(KEY_CLOUD_LIMIT, cloudOpsLimit.coerceIn(6, 300))
            .apply()
        refreshSnapshot()
    }

    fun canConsumeCloudOps(opsNeeded: Int): Boolean {
        resetCountersIfNewDay()
        val used = prefs.getInt(KEY_CLOUD_OPS, 0)
        val limit = prefs.getInt(KEY_CLOUD_LIMIT, DEFAULT_CLOUD_OPS_DAILY_LIMIT)
        return (used + opsNeeded) <= limit
    }

    fun recordCloudOps(opsCount: Int) {
        resetCountersIfNewDay()
        val used = prefs.getInt(KEY_CLOUD_OPS, 0)
        prefs.edit().putInt(KEY_CLOUD_OPS, used + opsCount).apply()
        refreshSnapshot()
    }

    private fun tryConsumeStreetViewQuota(): Boolean {
        resetCountersIfNewDay()
        val used = prefs.getInt(KEY_SV_COUNT, 0)
        val limit = prefs.getInt(KEY_SV_LIMIT, DEFAULT_STREET_VIEW_DAILY_LIMIT)
        if (used >= limit) return false
        prefs.edit().putInt(KEY_SV_COUNT, used + 1).apply()
        refreshSnapshot()
        return true
    }

    /**
     * Carrega a foto do Street View para uma coordenada:
     * 1. Primeiro procura no cache offline (`streetview_cache/`) — R$ 0,00 e funciona sem internet!
     * 2. Se não estiver em cache e o Modo Online + MAPS_API_KEY estiverem ativos, valida a trava
     *    diária anti-gasto, baixa 1 única vez (640x360) e salva permanentemente em disco.
     */
    suspend fun loadStreetViewPreview(
        lat: Double,
        lon: Double,
        allowOnlineFetch: Boolean
    ): StreetViewResult = withContext(Dispatchers.IO) {
        val fileKey = String.format(Locale.US, "sv_%.4f_%.4f.jpg", lat, lon)
        val cacheFile = File(streetViewCacheDir, fileKey)

        // 1. Cache offline primeiro (0 chamadas de API, R$ 0,00!)
        if (cacheFile.exists() && cacheFile.length() > 500L) {
            val bmp = BitmapFactory.decodeFile(cacheFile.absolutePath)
            if (bmp != null) {
                return@withContext StreetViewResult(
                    bitmap = bmp.asImageBitmap(),
                    fromOfflineCache = true,
                    message = "✓ Foto Street View carregada do cache offline (R\$ 0,00 • 0 chamadas de API)"
                )
            }
        }

        if (!allowOnlineFetch || !_snapshot.value.onlineToolsEnabled) {
            return@withContext StreetViewResult(
                message = "Modo Offline-First ativo. Ligue as 'Ferramentas Online' ou use o botão gratuito 'Street View 360° (App Maps)' abaixo."
            )
        }

        val apiKey = getMapsApiKey()
        if (apiKey.isBlank()) {
            return@withContext StreetViewResult(
                missingApiKey = true,
                message = "Nenhuma chave MAPS_API_KEY no .env (proteção de custos ativa). Use o botão 'Street View 360° Grátis (App Maps)' abaixo — funciona 100% grátis sem chave!"
            )
        }

        if (!tryConsumeStreetViewQuota()) {
            val lim = _snapshot.value.streetViewDailyLimit
            return@withContext StreetViewResult(
                blockedByBudget = true,
                message = "🛡 Trava Anti-Gasto atingida ($lim/$lim fotos Street View hoje). Use o botão 'Street View 360° Grátis (App Maps)' para continuar sem custo!"
            )
        }

        try {
            val urlStr = String.format(
                Locale.US,
                "https://maps.googleapis.com/maps/api/streetview?size=640x360&location=%.5f,%.5f&fov=90&return_error_code=true&key=%s",
                lat,
                lon,
                Uri.encode(apiKey)
            )
            val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                connectTimeout = 5000
                readTimeout = 5000
            }
            if (conn.responseCode == 200) {
                val bytes = conn.inputStream.use { it.readBytes() }
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                if (bmp != null) {
                    FileOutputStream(cacheFile).use { it.write(bytes) }
                    refreshSnapshot()
                    return@withContext StreetViewResult(
                        bitmap = bmp.asImageBitmap(),
                        fromOfflineCache = false,
                        message = "✓ Street View baixado e salvo no cache offline (${_snapshot.value.streetViewCallsToday}/${_snapshot.value.streetViewDailyLimit} hoje)"
                    )
                }
            } else if (conn.responseCode == 404) {
                return@withContext StreetViewResult(
                    message = "O Google Street View ainda não possui imagem panorâmica exata nesta coordenada."
                )
            }
            StreetViewResult(
                message = "Não foi possível carregar o preview pela API (HTTP ${conn.responseCode}). Use o botão 'Street View 360° Grátis (App Maps)'."
            )
        } catch (e: Exception) {
            Log.w(TAG, "Erro ao buscar Street View Static", e)
            StreetViewResult(
                message = "Sem conexão no momento. Se este ponto já tiver sido visitado antes, aparecerá offline automaticamente."
            )
        }
    }

    /**
     * Abre o Street View 360° interativo direto no Google Maps nativo (100% GRÁTIS — R$ 0,00 de API!).
     */
    fun openFreeGoogleStreetView360(lat: Double, lon: Double): Boolean {
        return try {
            val gmmUri = Uri.parse(String.format(Locale.US, "google.streetview:cbll=%.6f,%.6f", lat, lon))
            val mapIntent = Intent(Intent.ACTION_VIEW, gmmUri).apply {
                setPackage("com.google.android.apps.maps")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (mapIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(mapIntent)
                true
            } else {
                val webUri = Uri.parse(
                    String.format(
                        Locale.US,
                        "https://www.google.com/maps/@?api=1&map_action=pano&viewpoint=%.6f,%.6f",
                        lat,
                        lon
                    )
                )
                context.startActivity(Intent(Intent.ACTION_VIEW, webUri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao abrir Street View 360 gratuito", e)
            false
        }
    }

    /**
     * Abre o Google Maps (Satélite/Trânsito ou Navegação Curva-a-Curva) via Intent nativo (100% GRÁTIS — R$ 0,00!).
     */
    fun openFreeGoogleMaps(lat: Double, lon: Double, label: String, startTurnByTurnNavigation: Boolean): Boolean {
        return try {
            val uri = if (startTurnByTurnNavigation) {
                Uri.parse(String.format(Locale.US, "google.navigation:q=%.6f,%.6f&mode=d", lat, lon))
            } else {
                Uri.parse(
                    String.format(
                        Locale.US,
                        "geo:%.6f,%.6f?q=%.6f,%.6f(%s)",
                        lat,
                        lon,
                        lat,
                        lon,
                        Uri.encode(label)
                    )
                )
            }
            val mapIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage("com.google.android.apps.maps")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (mapIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(mapIntent)
                true
            } else {
                val webUri = Uri.parse(
                    String.format(Locale.US, "https://www.google.com/maps/search/?api=1&query=%.6f,%.6f", lat, lon)
                )
                context.startActivity(Intent(Intent.ACTION_VIEW, webUri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao abrir Google Maps gratuito", e)
            false
        }
    }
}
