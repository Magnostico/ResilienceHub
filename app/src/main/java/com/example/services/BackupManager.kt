package com.example.services

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.example.data.AppDatabase
import com.example.data.OfficialSurvivalKnowledgeBase
import com.example.data.entity.FamilyProfileEntity
import com.example.data.entity.InventoryItemEntity
import com.example.data.entity.ManualChunkFtsEntity
import com.example.data.entity.MapPoiEntity
import com.example.data.model.FamilyRole
import com.example.data.model.ItemCategory
import com.example.data.model.PoiType
import com.example.data.model.StorageLocation
import com.example.ui.FamilyDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Gerenciador de Backup Automático & Restauração 100% Offline com Rotação Inteligente (Zero Entupimento):
 *
 * - Compacta todos os dados do usuário (Estoque, Família, Pontos do Mapa, Notas e PDFs de Receitas/Documentos)
 *   em um único arquivo leve `.zip`.
 * - Salva na pasta pública `Download/ResilienceHub_Backups/` (que SOBREVIVE à desinstalação do app ou limpeza de cache/dados)
 *   e mantém um espelho rápido no armazenamento externo do app.
 * - Política Anti-Entupimento: Mantém no máximo 3 arquivos de backup rotativos (`MAX_ROLLING_BACKUPS = 3`),
 *   apagando automaticamente versões antigas para nunca ocupar espaço desnecessário no celular.
 */
class BackupManager(
    private val context: Context,
    private val database: AppDatabase
) {

    companion object {
        private const val TAG = "BackupManager"
        const val BACKUP_FOLDER_NAME = "ResilienceHub_Backups"
        const val MAX_ROLLING_BACKUPS = 3
        private const val DATA_JSON_ENTRY = "resilience_data.json"
        private const val PDF_DIR_PREFIX = "family_docs/"
    }

    data class BackupSnapshotInfo(
        val fileName: String,
        val filePath: String,
        val timestampMillis: Long,
        val formattedDate: String,
        val sizeKb: Long,
        val itemsCount: Int,
        val profilesCount: Int,
        val poisCount: Int,
        val pdfsCount: Int,
        val isExternalPublic: Boolean
    )

    data class BackupResult(
        val isSuccess: Boolean,
        val message: String,
        val snapshot: BackupSnapshotInfo? = null
    )

    private val internalBackupDir: File = File(
        context.getExternalFilesDir(null) ?: context.filesDir,
        BACKUP_FOLDER_NAME
    ).apply {
        if (!exists()) mkdirs()
    }

    private val publicDownloadsBackupDir: File = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
        BACKUP_FOLDER_NAME
    )

    private val familyDocsDir: File = File(context.filesDir, "family_docs").apply {
        if (!exists()) mkdirs()
    }
    private val familyDocsMetaFile: File = File(familyDocsDir, "family_docs_meta.json")

    /**
     * Executa o backup completo compacto (.zip) e aplica a rotação máxima de 3 arquivos
     * para não entupir o armazenamento do celular.
     */
    suspend fun performRollingBackup(isAutoBackup: Boolean = false): BackupResult = withContext(Dispatchers.IO) {
        try {
            val items = database.inventoryDao().getAllItems().first()
            val profiles = database.familyProfileDao().getAllProfiles().first()
            val pois = database.mapPoiDao().getAllPois().first()
            val allChunks = database.manualChunkDao().getAllChunks().first()
            val customChunks = allChunks.filter { it.rowId > 100 }
            val familyDocs = loadFamilyDocumentsMeta()

            // Se for auto-backup e o banco estiver totalmente zerado, evita sobrescrever um backup bom com dados vazios
            if (isAutoBackup && items.isEmpty() && profiles.isEmpty() && pois.isEmpty() && familyDocs.isEmpty()) {
                return@withContext BackupResult(
                    isSuccess = false,
                    message = "Auto-backup ignorado (nenhum dado cadastrado para salvar)."
                )
            }

            val now = System.currentTimeMillis()
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(now))
            val fileName = "ResilienceHub_Backup_$stamp.zip"

            // 1. Gera o pacote ZIP em memória/arquivo local
            val localZipFile = File(internalBackupDir, fileName)
            FileOutputStream(localZipFile).use { fos ->
                writeBackupZipToStream(
                    outputStream = fos,
                    timestamp = now,
                    items = items,
                    profiles = profiles,
                    pois = pois,
                    customChunks = customChunks,
                    familyDocs = familyDocs
                )
            }

            // 2. Aplica rotação anti-entupimento na pasta local (mantém apenas os 3 mais recentes)
            enforceMaxBackupsInFolder(internalBackupDir, MAX_ROLLING_BACKUPS)

            // 3. Copia também para a pasta pública `Download/ResilienceHub_Backups/` (sobrevive a desinstalar o app!)
            val savedPublicPath = saveToPublicDownloadsFolder(localZipFile, fileName)

            val sizeKb = (localZipFile.length() / 1024L).coerceAtLeast(1L)
            val formattedDate = SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", Locale.getDefault()).format(Date(now))

            val info = BackupSnapshotInfo(
                fileName = fileName,
                filePath = savedPublicPath ?: localZipFile.absolutePath,
                timestampMillis = now,
                formattedDate = formattedDate,
                sizeKb = sizeKb,
                itemsCount = items.size,
                profilesCount = profiles.size,
                poisCount = pois.size,
                pdfsCount = familyDocs.size,
                isExternalPublic = savedPublicPath != null
            )

            val folderMsg = if (savedPublicPath != null) {
                "Download/$BACKUP_FOLDER_NAME/$fileName"
            } else {
                "Armazenamento Externo/$BACKUP_FOLDER_NAME/$fileName"
            }

            BackupResult(
                isSuccess = true,
                message = "✓ Backup seguro salvo em $folderMsg ($sizeKb KB • Máx $MAX_ROLLING_BACKUPS arquivos rotativos).",
                snapshot = info
            )
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao gerar backup rotativo", e)
            BackupResult(
                isSuccess = false,
                message = "❌ Erro ao criar backup: ${e.localizedMessage}"
            )
        }
    }

    /**
     * Exporta o backup `.zip` diretamente para uma URI escolhida pelo usuário (ex: Google Drive, PenDrive USB, Pasta Documentos).
     */
    suspend fun exportBackupToUri(targetUri: Uri): BackupResult = withContext(Dispatchers.IO) {
        try {
            val items = database.inventoryDao().getAllItems().first()
            val profiles = database.familyProfileDao().getAllProfiles().first()
            val pois = database.mapPoiDao().getAllPois().first()
            val customChunks = database.manualChunkDao().getAllChunks().first().filter { it.rowId > 100 }
            val familyDocs = loadFamilyDocumentsMeta()
            val now = System.currentTimeMillis()

            val out = context.contentResolver.openOutputStream(targetUri)
                ?: return@withContext BackupResult(false, "Não foi possível abrir o destino selecionado.")

            out.use { stream ->
                writeBackupZipToStream(
                    outputStream = stream,
                    timestamp = now,
                    items = items,
                    profiles = profiles,
                    pois = pois,
                    customChunks = customChunks,
                    familyDocs = familyDocs
                )
            }

            BackupResult(
                isSuccess = true,
                message = "✓ Backup exportado com sucesso (${items.size} itens, ${profiles.size} familiares, ${familyDocs.size} PDFs)!"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao exportar backup para URI", e)
            BackupResult(false, "❌ Falha ao exportar backup: ${e.localizedMessage}")
        }
    }

    /**
     * Restaura todos os dados a partir de um arquivo de backup local/público (`File`).
     */
    suspend fun restoreFromBackupFile(file: File): BackupResult = withContext(Dispatchers.IO) {
        if (!file.exists()) {
            return@withContext BackupResult(false, "❌ Arquivo de backup não encontrado: ${file.name}")
        }
        return@withContext try {
            FileInputStream(file).use { fis ->
                restoreFromZipInputStream(fis, file.name)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao restaurar backup de arquivo", e)
            BackupResult(false, "❌ Falha ao restaurar backup: ${e.localizedMessage}")
        }
    }

    /**
     * Restaura todos os dados a partir de um arquivo `.zip` selecionado pelo usuário no seletor de arquivos do Android.
     */
    suspend fun restoreFromBackupUri(uri: Uri): BackupResult = withContext(Dispatchers.IO) {
        return@withContext try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return@withContext BackupResult(false, "❌ Não foi possível ler o arquivo selecionado.")
            inputStream.use { stream ->
                restoreFromZipInputStream(stream, "Backup Selecionado")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao restaurar backup via URI", e)
            BackupResult(false, "❌ Arquivo de backup inválido ou corrompido: ${e.localizedMessage}")
        }
    }

    /**
     * Lista até os 3 backups mais recentes disponíveis na pasta pública `Download/ResilienceHub_Backups/`
     * ou na pasta de backup do aplicativo.
     */
    suspend fun listAvailableBackups(): List<BackupSnapshotInfo> = withContext(Dispatchers.IO) {
        val candidateFiles = mutableMapOf<String, Pair<File, Boolean>>()

        // 1. Verifica na pasta pública `Download/ResilienceHub_Backups`
        if (publicDownloadsBackupDir.exists()) {
            publicDownloadsBackupDir.listFiles { f -> f.isFile && f.name.endsWith(".zip", true) }
                ?.forEach { file ->
                    candidateFiles[file.name] = file to true
                }
        }

        // 2. Verifica na pasta de backup do app
        if (internalBackupDir.exists()) {
            internalBackupDir.listFiles { f -> f.isFile && f.name.endsWith(".zip", true) }
                ?.forEach { file ->
                    if (!candidateFiles.containsKey(file.name)) {
                        candidateFiles[file.name] = file to false
                    }
                }
        }

        val snapshots = candidateFiles.values
            .sortedByDescending { it.first.lastModified() }
            .take(MAX_ROLLING_BACKUPS)
            .mapNotNull { (file, isPublic) ->
                readSnapshotMetadata(file, isPublic)
            }

        snapshots
    }

    // =========================================================================
    // MÉTODOS INTERNOS DE COMPACTAÇÃO, LEITURA E ROTAÇÃO ANTI-ENTUPIMENTO
    // =========================================================================

    private fun saveToPublicDownloadsFolder(sourceZip: File, fileName: String): String? {
        // Tentativa 1: Acesso direto a `Download/ResilienceHub_Backups` (funciona em várias versões e mantém arquivos visíveis)
        try {
            if (!publicDownloadsBackupDir.exists()) {
                publicDownloadsBackupDir.mkdirs()
            }
            if (publicDownloadsBackupDir.exists() && publicDownloadsBackupDir.canWrite()) {
                val destFile = File(publicDownloadsBackupDir, fileName)
                sourceZip.copyTo(destFile, overwrite = true)
                enforceMaxBackupsInFolder(publicDownloadsBackupDir, MAX_ROLLING_BACKUPS)
                return destFile.absolutePath
            }
        } catch (e: Exception) {
            Log.d(TAG, "Fallback MediaStore para pasta Downloads", e)
        }

        // Tentativa 2: MediaStore.Downloads no Android 10+ (API 29+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val resolver = context.contentResolver
                // Limpa entradas antigas excedentes criadas pelo próprio app no MediaStore
                cleanupOldMediaStoreBackups()

                val contentValues = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/zip")
                    put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$BACKUP_FOLDER_NAME")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }

                val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val itemUri = resolver.insert(collection, contentValues)
                if (itemUri != null) {
                    resolver.openOutputStream(itemUri)?.use { out ->
                        FileInputStream(sourceZip).use { input ->
                            input.copyTo(out)
                        }
                    }
                    contentValues.clear()
                    contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
                    resolver.update(itemUri, contentValues, null, null)
                    return "Download/$BACKUP_FOLDER_NAME/$fileName"
                }
            } catch (e: Exception) {
                Log.w(TAG, "Não foi possível gravar via MediaStore.Downloads", e)
            }
        }
        return null
    }

    /**
     * Garante que nunca existam mais do que `maxFiles` (3) na pasta, apagando os mais antigos.
     */
    private fun enforceMaxBackupsInFolder(folder: File, maxFiles: Int) {
        try {
            val zipFiles = folder.listFiles { f -> f.isFile && f.name.endsWith(".zip", true) }
                ?.sortedByDescending { it.lastModified() }
                ?: return

            if (zipFiles.size > maxFiles) {
                zipFiles.drop(maxFiles).forEach { oldFile ->
                    runCatching { oldFile.delete() }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao rotacionar backups antigos", e)
        }
    }

    private fun cleanupOldMediaStoreBackups() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        try {
            val resolver = context.contentResolver
            val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val projection = arrayOf(
                MediaStore.Downloads._ID,
                MediaStore.Downloads.DISPLAY_NAME,
                MediaStore.Downloads.DATE_MODIFIED
            )
            val selection = "${MediaStore.Downloads.DISPLAY_NAME} LIKE ?"
            val selectionArgs = arrayOf("ResilienceHub_Backup_%.zip")
            val sortOrder = "${MediaStore.Downloads.DATE_MODIFIED} DESC"

            val ids = mutableListOf<Long>()
            resolver.query(collection, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID)
                while (cursor.moveToNext()) {
                    ids.add(cursor.getLong(idCol))
                }
            }

            // Como vamos inserir +1 agora, mantemos no máximo (MAX_ROLLING_BACKUPS - 1) anteriores
            if (ids.size >= MAX_ROLLING_BACKUPS) {
                ids.drop(MAX_ROLLING_BACKUPS - 1).forEach { oldId ->
                    val uri = Uri.withAppendedPath(collection, oldId.toString())
                    runCatching { resolver.delete(uri, null, null) }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Aviso ao limpar backups antigos no MediaStore", e)
        }
    }

    private fun writeBackupZipToStream(
        outputStream: OutputStream,
        timestamp: Long,
        items: List<InventoryItemEntity>,
        profiles: List<FamilyProfileEntity>,
        pois: List<MapPoiEntity>,
        customChunks: List<ManualChunkFtsEntity>,
        familyDocs: List<FamilyDocument>
    ) {
        val rootJson = JSONObject().apply {
            put("backupVersion", 1)
            put("timestampMillis", timestamp)
            put("itemsCount", items.size)
            put("profilesCount", profiles.size)
            put("poisCount", pois.size)
            put("pdfsCount", familyDocs.size)

            // 1. Inventory Items
            val itemsArr = JSONArray()
            items.forEach { item ->
                itemsArr.put(
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
            put("items", itemsArr)

            // 2. Family Profiles
            val profilesArr = JSONArray()
            profiles.forEach { p ->
                profilesArr.put(
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
            put("profiles", profilesArr)

            // 3. Map POIs
            val poisArr = JSONArray()
            pois.forEach { poi ->
                poisArr.put(
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
            put("pois", poisArr)

            // 4. Custom Manual Notes
            val chunksArr = JSONArray()
            customChunks.forEach { c ->
                chunksArr.put(
                    JSONObject().apply {
                        put("docTitle", c.docTitle)
                        put("category", c.category)
                        put("pageNumber", c.pageNumber)
                        put("contentText", c.contentText)
                    }
                )
            }
            put("customChunks", chunksArr)

            // 5. Family PDF Documents Metadata
            val docsArr = JSONArray()
            familyDocs.forEach { doc ->
                val zipEntryFileName = File(doc.filePath).name
                docsArr.put(
                    JSONObject().apply {
                        put("id", doc.id)
                        put("title", doc.title)
                        put("docType", doc.docType)
                        put("memberName", doc.memberName)
                        put("fileName", doc.fileName)
                        put("storedFileName", zipEntryFileName)
                        put("pageCount", doc.pageCount)
                        put("extractedText", doc.extractedText)
                        put("addedAtMillis", doc.addedAtMillis)
                    }
                )
            }
            put("familyDocs", docsArr)
        }

        ZipOutputStream(outputStream).use { zos ->
            // Grava o JSON principal
            val jsonEntry = ZipEntry(DATA_JSON_ENTRY)
            zos.putNextEntry(jsonEntry)
            val jsonBytes = rootJson.toString(2).toByteArray(Charsets.UTF_8)
            zos.write(jsonBytes)
            zos.closeEntry()

            // Grava cada PDF de Receita / Documento da Família dentro do ZIP
            familyDocs.forEach { doc ->
                val pdfFile = File(doc.filePath)
                if (pdfFile.exists() && pdfFile.isFile) {
                    val pdfEntry = ZipEntry("$PDF_DIR_PREFIX${pdfFile.name}")
                    zos.putNextEntry(pdfEntry)
                    FileInputStream(pdfFile).use { fis ->
                        fis.copyTo(zos)
                    }
                    zos.closeEntry()
                }
            }
            zos.finish()
        }
    }

    private suspend fun restoreFromZipInputStream(
        inputStream: InputStream,
        sourceName: String
    ): BackupResult {
        var jsonContent: String? = null
        val extractedPdfFiles = mutableMapOf<String, File>()

        if (!familyDocsDir.exists()) familyDocsDir.mkdirs()

        ZipInputStream(inputStream).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                val entryName = entry.name
                if (!entry.isDirectory) {
                    if (entryName == DATA_JSON_ENTRY || entryName.endsWith("resilience_data.json")) {
                        val baos = ByteArrayOutputStream()
                        zis.copyTo(baos)
                        jsonContent = baos.toString(Charsets.UTF_8.name())
                    } else if (entryName.startsWith(PDF_DIR_PREFIX) && entryName.endsWith(".pdf", true)) {
                        val safeName = entryName.substringAfterLast('/')
                        val destPdf = File(familyDocsDir, safeName)
                        FileOutputStream(destPdf).use { fos ->
                            zis.copyTo(fos)
                        }
                        extractedPdfFiles[safeName] = destPdf
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        val rawJson = jsonContent
            ?: return BackupResult(false, "❌ O arquivo selecionado não contém dados válidos do ResilienceHub.")

        val root = JSONObject(rawJson)

        // 1. Restaura Estoque
        val itemsArr = root.optJSONArray("items") ?: JSONArray()
        val restoredItems = mutableListOf<InventoryItemEntity>()
        for (i in 0 until itemsArr.length()) {
            val obj = itemsArr.getJSONObject(i)
            val cat = runCatching { ItemCategory.valueOf(obj.optString("category", "WATER")) }.getOrDefault(ItemCategory.WATER)
            val loc = runCatching { StorageLocation.valueOf(obj.optString("location", "APARTMENT")) }.getOrDefault(StorageLocation.APARTMENT)
            val exp = if (obj.has("expirationDate") && !obj.isNull("expirationDate")) obj.getLong("expirationDate") else null
            val maint = if (obj.has("lastMaintenanceDate") && !obj.isNull("lastMaintenanceDate")) obj.getLong("lastMaintenanceDate") else null
            val barcodeStr = obj.optString("barcode", "").ifBlank { null }

            restoredItems.add(
                InventoryItemEntity(
                    id = obj.optLong("id", 0L),
                    name = obj.optString("name", "Item"),
                    barcode = barcodeStr,
                    category = cat,
                    location = loc,
                    quantity = obj.optDouble("quantity", 1.0),
                    unit = obj.optString("unit", "un"),
                    expirationDate = exp,
                    lastMaintenanceDate = maint,
                    alertDaysBefore = obj.optInt("alertDaysBefore", 90)
                )
            )
        }

        // 2. Restaura Perfis Familiares
        val profilesArr = root.optJSONArray("profiles") ?: JSONArray()
        val restoredProfiles = mutableListOf<FamilyProfileEntity>()
        for (i in 0 until profilesArr.length()) {
            val obj = profilesArr.getJSONObject(i)
            val role = runCatching { FamilyRole.valueOf(obj.optString("role", "ADULT")) }.getOrDefault(FamilyRole.ADULT)
            restoredProfiles.add(
                FamilyProfileEntity(
                    id = obj.optLong("id", 0L),
                    name = obj.optString("name", "Familiar"),
                    role = role,
                    weightKg = obj.optDouble("weightKg", 65.0),
                    bloodType = obj.optString("bloodType", "O+"),
                    allergies = obj.optString("allergies", "Nenhuma declarada"),
                    medications = obj.optString("medications", "Nenhuma")
                )
            )
        }

        // 3. Restaura Pontos do Mapa (POIs)
        val poisArr = root.optJSONArray("pois") ?: JSONArray()
        val restoredPois = mutableListOf<MapPoiEntity>()
        for (i in 0 until poisArr.length()) {
            val obj = poisArr.getJSONObject(i)
            val type = runCatching { PoiType.valueOf(obj.optString("type", "FAMILY_MEETING")) }.getOrDefault(PoiType.FAMILY_MEETING)
            restoredPois.add(
                MapPoiEntity(
                    id = obj.optLong("id", 0L),
                    title = obj.optString("title", "Ponto Seguro"),
                    type = type,
                    latitude = obj.optDouble("latitude", -22.9519),
                    longitude = obj.optDouble("longitude", -43.1850),
                    address = obj.optString("address", ""),
                    phone = obj.optString("phone", "").ifBlank { null }
                )
            )
        }

        // 4. Restaura Notas Customizadas + Base Oficial da IA
        val customChunksArr = root.optJSONArray("customChunks") ?: JSONArray()
        val restoredCustomChunks = mutableListOf<ManualChunkFtsEntity>()
        for (i in 0 until customChunksArr.length()) {
            val obj = customChunksArr.getJSONObject(i)
            restoredCustomChunks.add(
                ManualChunkFtsEntity(
                    rowId = 101 + i,
                    docTitle = obj.optString("docTitle", "Nota Familiar"),
                    category = obj.optString("category", "GENERAL"),
                    pageNumber = obj.optInt("pageNumber", 1),
                    contentText = obj.optString("contentText", "")
                )
            )
        }

        // 5. Restaura Metadados dos PDFs de Receitas e Documentos da Família
        val docsArr = root.optJSONArray("familyDocs") ?: JSONArray()
        val metaArrayToSave = JSONArray()
        for (i in 0 until docsArr.length()) {
            val obj = docsArr.getJSONObject(i)
            val storedName = obj.optString("storedFileName", "")
            val restoredFile = extractedPdfFiles[storedName] ?: File(familyDocsDir, storedName)
            if (restoredFile.exists()) {
                metaArrayToSave.put(
                    JSONObject().apply {
                        put("id", obj.optString("id"))
                        put("title", obj.optString("title"))
                        put("docType", obj.optString("docType", "RECEITA_MEDICA"))
                        put("memberName", obj.optString("memberName", "Família"))
                        put("fileName", obj.optString("fileName", "documento.pdf"))
                        put("filePath", restoredFile.absolutePath)
                        put("pageCount", obj.optInt("pageCount", 1))
                        put("extractedText", obj.optString("extractedText", ""))
                        put("addedAtMillis", obj.optLong("addedAtMillis", System.currentTimeMillis()))
                    }
                )
            }
        }
        familyDocsMetaFile.writeText(metaArrayToSave.toString())

        // Grava tudo no banco de dados Room
        database.inventoryDao().clearAll()
        if (restoredItems.isNotEmpty()) database.inventoryDao().insertAll(restoredItems)

        database.familyProfileDao().clearAll()
        if (restoredProfiles.isNotEmpty()) database.familyProfileDao().insertAll(restoredProfiles)

        database.mapPoiDao().clearAll()
        if (restoredPois.isNotEmpty()) database.mapPoiDao().insertAll(restoredPois)

        database.manualChunkDao().clearAll()
        database.manualChunkDao().insertAll(OfficialSurvivalKnowledgeBase.getOfficialChunks())
        if (restoredCustomChunks.isNotEmpty()) {
            database.manualChunkDao().insertAll(restoredCustomChunks)
        }

        return BackupResult(
            isSuccess = true,
            message = "✓ Dados restaurados de \"$sourceName\": ${restoredItems.size} itens, ${restoredProfiles.size} familiares, ${restoredPois.size} pontos e ${metaArrayToSave.length()} PDFs!"
        )
    }

    private fun readSnapshotMetadata(file: File, isExternalPublic: Boolean): BackupSnapshotInfo? {
        return try {
            var itemsCount = 0
            var profilesCount = 0
            var poisCount = 0
            var pdfsCount = 0
            var timestamp = file.lastModified()

            FileInputStream(file).use { fis ->
                ZipInputStream(fis).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        if (entry.name == DATA_JSON_ENTRY) {
                            val baos = ByteArrayOutputStream()
                            zis.copyTo(baos)
                            val root = JSONObject(baos.toString(Charsets.UTF_8.name()))
                            timestamp = root.optLong("timestampMillis", file.lastModified())
                            itemsCount = root.optInt("itemsCount", root.optJSONArray("items")?.length() ?: 0)
                            profilesCount = root.optInt("profilesCount", root.optJSONArray("profiles")?.length() ?: 0)
                            poisCount = root.optInt("poisCount", root.optJSONArray("pois")?.length() ?: 0)
                            pdfsCount = root.optInt("pdfsCount", root.optJSONArray("familyDocs")?.length() ?: 0)
                            break
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            }

            val formattedDate = SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", Locale.getDefault()).format(Date(timestamp))
            val sizeKb = (file.length() / 1024L).coerceAtLeast(1L)

            BackupSnapshotInfo(
                fileName = file.name,
                filePath = file.absolutePath,
                timestampMillis = timestamp,
                formattedDate = formattedDate,
                sizeKb = sizeKb,
                itemsCount = itemsCount,
                profilesCount = profilesCount,
                poisCount = poisCount,
                pdfsCount = pdfsCount,
                isExternalPublic = isExternalPublic
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun loadFamilyDocumentsMeta(): List<FamilyDocument> {
        if (!familyDocsMetaFile.exists()) return emptyList()
        return try {
            val arr = JSONArray(familyDocsMetaFile.readText())
            val list = mutableListOf<FamilyDocument>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val path = obj.optString("filePath")
                if (path.isNotBlank() && File(path).exists()) {
                    list.add(
                        FamilyDocument(
                            id = obj.optString("id"),
                            title = obj.optString("title"),
                            docType = obj.optString("docType", "RECEITA_MEDICA"),
                            memberName = obj.optString("memberName", "Família"),
                            fileName = obj.optString("fileName", "documento.pdf"),
                            filePath = path,
                            pageCount = obj.optInt("pageCount", 1),
                            extractedText = obj.optString("extractedText", ""),
                            addedAtMillis = obj.optLong("addedAtMillis", 0L)
                        )
                    )
                }
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }
}
