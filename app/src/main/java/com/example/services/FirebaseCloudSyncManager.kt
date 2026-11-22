package com.example.services

import android.content.Context
import android.util.Log
import com.example.data.AppDatabase
import com.example.data.entity.FamilyProfileEntity
import com.example.data.entity.InventoryItemEntity
import com.example.data.entity.MapPoiEntity
import com.example.data.model.FamilyRole
import com.example.data.model.ItemCategory
import com.example.data.model.PoiType
import com.example.data.model.StorageLocation
import com.google.android.gms.tasks.Task
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Sincronização Cloud Firestore 100% Offline-First e Ultra-Econômica (Plano Spark Gratuito):
 *
 * - Room (SQLite local) continua sendo o banco principal: tudo funciona sem internet.
 * - ZERO listeners contínuos 24h (`addSnapshotListener`): só lê/grava na nuvem quando o usuário
 *   toca em "Enviar p/ Nuvem" ou "Baixar da Nuvem" com o Modo Online ativo.
 * - Empacota todo o cofre em apenas 3 documentos por usuário (`users/{uid}/vault/inventory`,
 *   `users/{uid}/vault/family`, `users/{uid}/vault/pois`) = apenas 3 escritas para subir tudo
 *   e 3 leituras para restaurar tudo (contra o limite grátis de 20.000 escritas e 50.000 leituras/dia!).
 */
class FirebaseCloudSyncManager(
    private val context: Context,
    private val database: AppDatabase,
    private val costGuard: ApiCostGuard
) {

    companion object {
        private const val TAG = "FirebaseCloudSync"
        private const val PREFS_NAME = "resilience_cloud_sync_prefs"
        private const val KEY_LAST_SYNC_TEXT = "last_sync_text"
        private const val SCHEMA_VERSION = 1
        private const val MAX_PAYLOAD_BYTES = 480_000
    }

    data class CloudAuthState(
        val isFirebaseConfigured: Boolean = false,
        val isSignedIn: Boolean = false,
        val userUid: String? = null,
        val userEmail: String? = null,
        val isAnonymous: Boolean = false,
        val lastSyncText: String? = null
    )

    data class CloudSyncResult(
        val isSuccess: Boolean,
        val message: String
    )

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private var persistenceConfigured = false

    fun isFirebaseReady(): Boolean {
        return try {
            FirebaseApp.getApps(context).isNotEmpty() || FirebaseApp.initializeApp(context) != null
        } catch (e: Exception) {
            Log.w(TAG, "Firebase não inicializado (google-services.json ausente ou inválido)", e)
            false
        }
    }

    private fun ensureFirestoreConfigured() {
        if (persistenceConfigured || !isFirebaseReady()) return
        runCatching {
            val db = Firebase.firestore
            db.firestoreSettings = FirebaseFirestoreSettings.Builder().build()
            persistenceConfigured = true
        }
    }

    fun getCurrentAuthState(): CloudAuthState {
        if (!isFirebaseReady()) {
            return CloudAuthState(
                isFirebaseConfigured = false,
                lastSyncText = prefs.getString(KEY_LAST_SYNC_TEXT, null)
            )
        }
        val user = runCatching { Firebase.auth.currentUser }.getOrNull()
        return CloudAuthState(
            isFirebaseConfigured = true,
            isSignedIn = user != null,
            userUid = user?.uid,
            userEmail = user?.email,
            isAnonymous = user?.isAnonymous ?: false,
            lastSyncText = prefs.getString(KEY_LAST_SYNC_TEXT, null)
        )
    }

    suspend fun signInAnonymously(): CloudSyncResult = withContext(Dispatchers.IO) {
        if (!isFirebaseReady()) {
            return@withContext CloudSyncResult(false, "Firebase não configurado neste build.")
        }
        return@withContext try {
            val authRes = Firebase.auth.signInAnonymously().awaitTask()
            val uid = authRes.user?.uid?.take(8) ?: "ok"
            CloudSyncResult(true, "✓ Sessão rápida conectada no Firebase (ID: $uid...).")
        } catch (e: Exception) {
            Log.e(TAG, "Falha no login anônimo Firebase", e)
            CloudSyncResult(false, "❌ Falha ao conectar sessão rápida: ${e.localizedMessage}")
        }
    }

    suspend fun signInWithEmail(email: String, password: String): CloudSyncResult = withContext(Dispatchers.IO) {
        if (!isFirebaseReady()) {
            return@withContext CloudSyncResult(false, "Firebase não configurado neste build.")
        }
        val cleanEmail = email.trim()
        if (cleanEmail.isBlank() || password.length < 6) {
            return@withContext CloudSyncResult(false, "Informe um e-mail válido e senha com pelo menos 6 caracteres.")
        }
        return@withContext try {
            Firebase.auth.signInWithEmailAndPassword(cleanEmail, password).awaitTask()
            CloudSyncResult(true, "✓ Conectado como $cleanEmail!")
        } catch (e: Exception) {
            Log.e(TAG, "Falha no login com e-mail", e)
            CloudSyncResult(false, "❌ Erro ao entrar: ${e.localizedMessage}")
        }
    }

    suspend fun registerWithEmail(email: String, password: String): CloudSyncResult = withContext(Dispatchers.IO) {
        if (!isFirebaseReady()) {
            return@withContext CloudSyncResult(false, "Firebase não configurado neste build.")
        }
        val cleanEmail = email.trim()
        if (cleanEmail.isBlank() || password.length < 6) {
            return@withContext CloudSyncResult(false, "Informe um e-mail válido e senha com pelo menos 6 caracteres.")
        }
        return@withContext try {
            Firebase.auth.createUserWithEmailAndPassword(cleanEmail, password).awaitTask()
            CloudSyncResult(true, "✓ Conta criada e conectada ($cleanEmail)!")
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao criar conta Firebase", e)
            CloudSyncResult(false, "❌ Erro ao criar conta: ${e.localizedMessage}")
        }
    }

    fun signOut() {
        if (!isFirebaseReady()) return
        runCatching { Firebase.auth.signOut() }
    }

    /**
     * Envia Estoque, Família e Pins Estratégicos para o Cloud Firestore em apenas 3 escritas!
     */
    suspend fun uploadVaultToCloud(): CloudSyncResult = withContext(Dispatchers.IO) {
        if (!costGuard.snapshot.value.onlineToolsEnabled) {
            return@withContext CloudSyncResult(false, "Ative o botão 'Modo Online (Nuvem & Maps)' acima para sincronizar.")
        }
        if (!isFirebaseReady()) {
            return@withContext CloudSyncResult(false, "Firebase não inicializado neste aparelho.")
        }
        if (!costGuard.canConsumeCloudOps(3)) {
            return@withContext CloudSyncResult(false, "🛡 Trava Anti-Gasto atingida hoje para sincronizações na nuvem.")
        }

        val user = Firebase.auth.currentUser
            ?: return@withContext CloudSyncResult(false, "Entre com sua conta (ou Sessão Rápida) antes de enviar para o Firebase.")

        ensureFirestoreConfigured()
        val db = Firebase.firestore
        val uid = user.uid
        val now = System.currentTimeMillis()

        return@withContext try {
            val items = database.inventoryDao().getAllItems().first()
            val profiles = database.familyProfileDao().getAllProfiles().first()
            val pois = database.mapPoiDao().getAllPois().first()

            val inventoryJson = serializeItems(items)
            val familyJson = serializeProfiles(profiles)
            val poisJson = serializePois(pois)

            if (inventoryJson.toByteArray(Charsets.UTF_8).size > MAX_PAYLOAD_BYTES ||
                familyJson.toByteArray(Charsets.UTF_8).size > MAX_PAYLOAD_BYTES ||
                poisJson.toByteArray(Charsets.UTF_8).size > MAX_PAYLOAD_BYTES
            ) {
                return@withContext CloudSyncResult(false, "Volume de dados excedeu o limite seguro de 480 KB por documento.")
            }

            val batch = db.batch()
            val vaultCol = db.collection("users").document(uid).collection("vault")

            batch.set(
                vaultCol.document("inventory"),
                mapOf(
                    "uid" to uid,
                    "docType" to "inventory",
                    "payloadJson" to inventoryJson,
                    "itemCount" to items.size,
                    "updatedAtMillis" to now,
                    "schemaVersion" to SCHEMA_VERSION
                )
            )
            batch.set(
                vaultCol.document("family"),
                mapOf(
                    "uid" to uid,
                    "docType" to "family",
                    "payloadJson" to familyJson,
                    "itemCount" to profiles.size,
                    "updatedAtMillis" to now,
                    "schemaVersion" to SCHEMA_VERSION
                )
            )
            batch.set(
                vaultCol.document("pois"),
                mapOf(
                    "uid" to uid,
                    "docType" to "pois",
                    "payloadJson" to poisJson,
                    "itemCount" to pois.size,
                    "updatedAtMillis" to now,
                    "schemaVersion" to SCHEMA_VERSION
                )
            )

            batch.commit().awaitTask()
            costGuard.recordCloudOps(3)

            val stamp = SimpleDateFormat("dd/MM 'às' HH:mm", Locale.getDefault()).format(Date(now))
            val summary = "Enviado em $stamp (${items.size} itens, ${profiles.size} fam., ${pois.size} pins)"
            prefs.edit().putString(KEY_LAST_SYNC_TEXT, summary).apply()

            CloudSyncResult(
                true,
                "☁️ Sincronizado com o Firebase! (${items.size} itens, ${profiles.size} familiares, ${pois.size} pins • apenas 3 escritas usadas)"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao enviar cofre para Cloud Firestore", e)
            CloudSyncResult(false, "❌ Falha ao enviar para o Firebase: ${e.localizedMessage}")
        }
    }

    /**
     * Baixa Estoque, Família e Pins Estratégicos do Cloud Firestore em apenas 3 leituras
     * e grava no Room local (Offline-First).
     */
    suspend fun downloadVaultFromCloud(): CloudSyncResult = withContext(Dispatchers.IO) {
        if (!costGuard.snapshot.value.onlineToolsEnabled) {
            return@withContext CloudSyncResult(false, "Ative o botão 'Modo Online (Nuvem & Maps)' acima para baixar da nuvem.")
        }
        if (!isFirebaseReady()) {
            return@withContext CloudSyncResult(false, "Firebase não inicializado neste aparelho.")
        }
        if (!costGuard.canConsumeCloudOps(3)) {
            return@withContext CloudSyncResult(false, "🛡 Trava Anti-Gasto atingida hoje para leituras na nuvem.")
        }

        val user = Firebase.auth.currentUser
            ?: return@withContext CloudSyncResult(false, "Entre com sua conta Firebase antes de restaurar da nuvem.")

        ensureFirestoreConfigured()
        val db = Firebase.firestore
        val vaultCol = db.collection("users").document(user.uid).collection("vault")

        return@withContext try {
            val invSnap = vaultCol.document("inventory").get().awaitTask()
            val famSnap = vaultCol.document("family").get().awaitTask()
            val poiSnap = vaultCol.document("pois").get().awaitTask()
            costGuard.recordCloudOps(3)

            if (!invSnap.exists() && !famSnap.exists() && !poiSnap.exists()) {
                return@withContext CloudSyncResult(
                    false,
                    "Nenhum dado encontrado na nuvem ainda para esta conta. Toque em 'Enviar p/ Nuvem' primeiro."
                )
            }

            var itemsRestored = 0
            var profilesRestored = 0
            var poisRestored = 0

            invSnap.getString("payloadJson")?.let { json ->
                val list = deserializeItems(json)
                database.inventoryDao().clearAll()
                if (list.isNotEmpty()) database.inventoryDao().insertAll(list)
                itemsRestored = list.size
            }

            famSnap.getString("payloadJson")?.let { json ->
                val list = deserializeProfiles(json)
                database.familyProfileDao().clearAll()
                if (list.isNotEmpty()) database.familyProfileDao().insertAll(list)
                profilesRestored = list.size
            }

            poiSnap.getString("payloadJson")?.let { json ->
                val list = deserializePois(json)
                database.mapPoiDao().clearAll()
                if (list.isNotEmpty()) database.mapPoiDao().insertAll(list)
                poisRestored = list.size
            }

            val stamp = SimpleDateFormat("dd/MM 'às' HH:mm", Locale.getDefault()).format(Date())
            val summary = "Baixado em $stamp ($itemsRestored itens, $profilesRestored fam., $poisRestored pins)"
            prefs.edit().putString(KEY_LAST_SYNC_TEXT, summary).apply()

            CloudSyncResult(
                true,
                "✓ Cofre restaurado do Firebase ($itemsRestored itens, $profilesRestored familiares, $poisRestored pins • apenas 3 leituras usadas)!"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao baixar cofre do Cloud Firestore", e)
            CloudSyncResult(false, "❌ Falha ao baixar do Firebase: ${e.localizedMessage}")
        }
    }

    // =========================================================================
    // SERIALIZAÇÃO COMPACTA JSON (1 DOCUMENTO POR CATEGORIA = CUSTO ZERO)
    // =========================================================================

    private fun serializeItems(items: List<InventoryItemEntity>): String {
        val arr = JSONArray()
        items.forEach { item ->
            arr.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("name", item.name)
                    put("barcode", item.barcode ?: "")
                    put("category", item.category.name)
                    put("location", item.location.name)
                    put("quantity", item.quantity)
                    put("unit", item.unit)
                    if (item.expirationDate != null) put("expirationDate", item.expirationDate)
                    if (item.lastMaintenanceDate != null) put("lastMaintenanceDate", item.lastMaintenanceDate)
                    put("alertDaysBefore", item.alertDaysBefore)
                }
            )
        }
        return arr.toString()
    }

    private fun deserializeItems(jsonStr: String): List<InventoryItemEntity> {
        val arr = JSONArray(jsonStr)
        val out = mutableListOf<InventoryItemEntity>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(
                InventoryItemEntity(
                    id = o.optLong("id", 0L),
                    name = o.optString("name", "Item"),
                    barcode = o.optString("barcode", "").ifBlank { null },
                    category = runCatching { ItemCategory.valueOf(o.optString("category", "WATER")) }.getOrDefault(ItemCategory.WATER),
                    location = runCatching { StorageLocation.valueOf(o.optString("location", "APARTMENT")) }.getOrDefault(StorageLocation.APARTMENT),
                    quantity = o.optDouble("quantity", 1.0),
                    unit = o.optString("unit", "un"),
                    expirationDate = if (o.has("expirationDate") && !o.isNull("expirationDate")) o.getLong("expirationDate") else null,
                    lastMaintenanceDate = if (o.has("lastMaintenanceDate") && !o.isNull("lastMaintenanceDate")) o.getLong("lastMaintenanceDate") else null,
                    alertDaysBefore = o.optInt("alertDaysBefore", 90)
                )
            )
        }
        return out
    }

    private fun serializeProfiles(profiles: List<FamilyProfileEntity>): String {
        val arr = JSONArray()
        profiles.forEach { p ->
            arr.put(
                JSONObject().apply {
                    put("id", p.id)
                    put("name", p.name)
                    put("role", p.role.name)
                    put("weightKg", p.weightKg)
                    put("bloodType", p.bloodType)
                    put("allergies", p.allergies)
                    put("medications", p.medications)
                }
            )
        }
        return arr.toString()
    }

    private fun deserializeProfiles(jsonStr: String): List<FamilyProfileEntity> {
        val arr = JSONArray(jsonStr)
        val out = mutableListOf<FamilyProfileEntity>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(
                FamilyProfileEntity(
                    id = o.optLong("id", 0L),
                    name = o.optString("name", "Familiar"),
                    role = runCatching { FamilyRole.valueOf(o.optString("role", "ADULT")) }.getOrDefault(FamilyRole.ADULT),
                    weightKg = o.optDouble("weightKg", 65.0),
                    bloodType = o.optString("bloodType", "O+"),
                    allergies = o.optString("allergies", "Nenhuma declarada"),
                    medications = o.optString("medications", "Nenhuma")
                )
            )
        }
        return out
    }

    private fun serializePois(pois: List<MapPoiEntity>): String {
        val arr = JSONArray()
        pois.forEach { poi ->
            arr.put(
                JSONObject().apply {
                    put("id", poi.id)
                    put("title", poi.title)
                    put("type", poi.type.name)
                    put("latitude", poi.latitude)
                    put("longitude", poi.longitude)
                    put("address", poi.address)
                    put("phone", poi.phone ?: "")
                }
            )
        }
        return arr.toString()
    }

    private fun deserializePois(jsonStr: String): List<MapPoiEntity> {
        val arr = JSONArray(jsonStr)
        val out = mutableListOf<MapPoiEntity>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(
                MapPoiEntity(
                    id = o.optLong("id", 0L),
                    title = o.optString("title", "Pin Estratégico"),
                    type = runCatching { PoiType.valueOf(o.optString("type", "STRATEGIC_PIN")) }.getOrDefault(PoiType.STRATEGIC_PIN),
                    latitude = o.optDouble("latitude", -22.8792),
                    longitude = o.optDouble("longitude", -43.2718),
                    address = o.optString("address", ""),
                    phone = o.optString("phone", "").ifBlank { null }
                )
            )
        }
        return out
    }

    private suspend fun <T> Task<T>.awaitTask(): T = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { result ->
            if (cont.isActive) cont.resume(result)
        }
        addOnFailureListener { exc ->
            if (cont.isActive) cont.resumeWithException(exc)
        }
        addOnCanceledListener {
            cont.cancel()
        }
    }
}
