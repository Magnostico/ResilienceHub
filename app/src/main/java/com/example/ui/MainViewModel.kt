package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.entity.FamilyProfileEntity
import com.example.data.entity.InventoryItemEntity
import com.example.data.entity.ManualChunkFtsEntity
import com.example.data.entity.MapPoiEntity
import com.example.data.model.FamilyRole
import com.example.data.model.ItemCategory
import com.example.data.model.PoiType
import com.example.data.model.StorageLocation
import com.example.data.repository.ResilienceRepository
import com.example.domain.RationingAndAlertEngine
import com.example.services.BackupManager
import com.example.services.ExpirationWorker
import com.example.services.HardwareToolsManager
import com.example.services.PdfRagManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Mensagem individual do Chat de Sobrevivência com IA RAG nos Órgãos Oficiais e Suporte a Tema Livre.
 */
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val isUser: Boolean,
    val timestamp: String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()),
    val executionMode: String? = null,
    val citedSources: List<ManualChunkFtsEntity> = emptyList(),
    val requiresFreeTopicConfirmation: Boolean = false,
    val pendingFreeTopicQuery: String? = null,
    val canExpandDetails: Boolean = false,
    val topicQueryForExpansion: String? = null,
    val isFreeTopic: Boolean = false,
    val isStreaming: Boolean = false,
    val thinkingStatus: String? = null
)

/**
 * Documento ou Receita Médica em PDF da Família armazenado 100% offline.
 */
data class FamilyDocument(
    val id: String,
    val title: String,
    val docType: String, // "RECEITA_MEDICA", "DOCUMENTO_PESSOAL", "LAUDO_EXAME", "VACINACAO"
    val memberName: String,
    val fileName: String,
    val filePath: String,
    val pageCount: Int,
    val extractedText: String,
    val addedAtMillis: Long
)

data class MainUiState(
    val crisisMode: Boolean = false,
    val items: List<InventoryItemEntity> = emptyList(),
    val allRegisteredItems: List<InventoryItemEntity> = emptyList(),
    val evaluatedItems: List<RationingAndAlertEngine.ItemEvaluationResult> = emptyList(),
    val waterReport: RationingAndAlertEngine.WaterAutonomyReport? = null,
    val profiles: List<FamilyProfileEntity> = emptyList(),
    val familyDocuments: List<FamilyDocument> = emptyList(),
    val pois: List<MapPoiEntity> = emptyList(),
    val poiNavigationList: List<RationingAndAlertEngine.PoiNavigationInfo> = emptyList(),
    val manualChunks: List<ManualChunkFtsEntity> = emptyList(),
    val manualQuery: String = "",
    val selectedLocation: StorageLocation? = null,
    val selectedCategory: ItemCategory? = null,
    val currentLat: Double = -22.9519, // Rio de Janeiro (Botafogo / Copacabana)
    val currentLon: Double = -43.1850,
    val isGpsActive: Boolean = false,
    val isLiveTracking: Boolean = false,
    val currentSpeedKmh: Float = 0f,
    val currentHeadingDegrees: Float = 0f,
    val currentAccuracyMeters: Float = 0f,
    val movementTrail: List<Pair<Double, Double>> = emptyList(),
    val gpsProviderInfo: String = "GPS STANDBY (Economia)",
    val gpsFixCount: Int = 0,
    val selectedTab: Int = 0, // 0: Estoque, 1: Mapas, 2: Chat IA, 3: Ferramentas, 4: Família

    // Hardware states
    val isTorchOn: Boolean = false,
    val isSosActive: Boolean = false,
    val isWhistleActive: Boolean = false,

    // Chat RAG states
    val chatMessages: List<ChatMessage> = emptyList(),
    val ragResponse: PdfRagManager.RagResponse? = null,
    val isRagLoading: Boolean = false,
    val ragModeAi: Boolean = true,
    val pdfImportStatus: String? = null,
    val isModelLoaded: Boolean = false,
    val isModelInitializing: Boolean = false,
    val loadedModelName: String? = null,
    val hasSavedModelOnDisk: Boolean = false,

    // Backup & Restore states
    val availableBackups: List<BackupManager.BackupSnapshotInfo> = emptyList(),
    val backupStatusMessage: String? = null,
    val isBackupBusy: Boolean = false
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        /**
         * Converte Mês (1..12) e Ano (ex: 2027) para o timestamp do último dia daquele mês às 23:59:59.
         */
        fun monthYearToExpirationTimestamp(month: Int?, year: Int?): Long? {
            if (month == null || year == null) return null
            val validMonth = month.coerceIn(1, 12) - 1 // Calendar.MONTH é 0-based (0 = Janeiro)
            val validYear = year.coerceIn(2020, 2100)
            val cal = Calendar.getInstance().apply {
                set(Calendar.YEAR, validYear)
                set(Calendar.MONTH, validMonth)
                val lastDay = getActualMaximum(Calendar.DAY_OF_MONTH)
                set(Calendar.DAY_OF_MONTH, lastDay)
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
                set(Calendar.MILLISECOND, 999)
            }
            return cal.timeInMillis
        }

        private fun createWelcomeMessage(): ChatMessage = ChatMessage(
            id = "welcome_msg",
            text = "Olá! Sou a IA unificada do **ResilienceHub**.\n\n" +
                "Tenho todo o conhecimento do **RAG de Sobrevivência** (30 protocolos da Defesa Civil, OMS, Ministério da Saúde, Cruz Vermelha e FEMA) e do seu estoque/família, e também sou **totalmente livre para conversar sobre qualquer outro assunto** com o conhecimento completo do modelo!",
            isUser = false,
            executionMode = "IA EDGE + RAG UNIFICADO"
        )
    }

    private val database = AppDatabase.getInstance(application)
    private val repository = ResilienceRepository(database)
    val engine = RationingAndAlertEngine()

    val hardwareToolsManager = HardwareToolsManager(application)
    val pdfRagManager = PdfRagManager(application, database)
    val backupManager = BackupManager(application, database)

    private val familyDocsDir = File(application.filesDir, "family_docs").apply {
        if (!exists()) mkdirs()
    }
    private val familyDocsMetaFile = File(familyDocsDir, "family_docs_meta.json")

    private val _crisisMode = MutableStateFlow(false)
    private val _selectedLocation = MutableStateFlow<StorageLocation?>(null)
    private val _selectedCategory = MutableStateFlow<ItemCategory?>(null)
    private val _manualQuery = MutableStateFlow("")
    private val _currentLat = MutableStateFlow(-22.9519)
    private val _currentLon = MutableStateFlow(-43.1850)
    private val _currentSpeedKmh = MutableStateFlow(0f)
    private val _currentHeadingDegrees = MutableStateFlow(0f)
    private val _currentAccuracyMeters = MutableStateFlow(0f)
    private val _movementTrail = MutableStateFlow<List<Pair<Double, Double>>>(
        listOf(-22.9519 to -43.1850)
    )
    private val _gpsProviderInfo = MutableStateFlow("GPS STANDBY (Economia)")
    private val _gpsFixCount = MutableStateFlow(0)
    private val _selectedTab = MutableStateFlow(0)

    // Chat RAG & Family Documents States
    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(listOf(createWelcomeMessage()))
    private val _familyDocuments = MutableStateFlow<List<FamilyDocument>>(emptyList())
    private val _ragResponse = MutableStateFlow<PdfRagManager.RagResponse?>(null)
    private val _isRagLoading = MutableStateFlow(false)
    private val _ragModeAi = MutableStateFlow(true)
    private val _pdfImportStatus = MutableStateFlow<String?>(null)
    private val _isModelLoaded = MutableStateFlow(false)
    private val _isModelInitializing = MutableStateFlow(false)
    private val _loadedModelName = MutableStateFlow<String?>(null)
    private val _hasSavedModelOnDisk = MutableStateFlow(false)
    private var pendingFreeTopicQuestion: String? = null
    private var lastAnsweredTopicQuery: String? = null
    private var lastAnsweredWasFreeTopic: Boolean = false

    // Backup & Restore States
    private val _availableBackups = MutableStateFlow<List<BackupManager.BackupSnapshotInfo>>(emptyList())
    private val _backupStatusMessage = MutableStateFlow<String?>(null)
    private val _isBackupBusy = MutableStateFlow(false)
    private var autoBackupJob: Job? = null

    private val _manualChunks = _manualQuery.flatMapLatest { query ->
        repository.searchManual(query)
    }

    init {
        // Agenda a checagem diária em segundo plano do WorkManager (inclui Auto-Backup diário)
        ExpirationWorker.scheduleDailyCheck(application)

        // Garante que a Base Oficial de Sobrevivência esteja carregada e verifica os backups disponíveis
        viewModelScope.launch {
            repository.ensureOfficialKnowledgeBaseSeeded()
            _familyDocuments.value = loadFamilyDocumentsFromDisk()
            val existingModelFile = pdfRagManager.findLocalModelFile()
            _hasSavedModelOnDisk.value = existingModelFile != null
            if (existingModelFile != null) {
                loadSavedModelFromDisk()
            }
            refreshAvailableBackups()
            if (_availableBackups.value.isEmpty()) {
                // Gera o primeiro snapshot automático sem bloquear a inicialização
                delay(1500)
                backupManager.performRollingBackup(isAutoBackup = true)
                refreshAvailableBackups()
            }
        }

        // Coleta contínua de movimentação GPS em tempo real quando ativado pelo usuário
        viewModelScope.launch {
            hardwareToolsManager.liveLocation.collect { loc ->
                if (loc != null) {
                    val newLat = loc.latitude
                    val newLon = loc.longitude
                    _currentLat.value = newLat
                    _currentLon.value = newLon
                    _currentSpeedKmh.value = if (loc.hasSpeed()) loc.speed * 3.6f else 0f
                    if (loc.hasBearing()) {
                        _currentHeadingDegrees.value = loc.bearing
                    }
                    _currentAccuracyMeters.value = if (loc.hasAccuracy()) loc.accuracy else 0f

                    appendPointToTrail(newLat, newLon)
                    _gpsFixCount.value += 1

                    val speedStr = String.format(Locale.US, "%.1f km/h", _currentSpeedKmh.value)
                    val accStr = if (loc.hasAccuracy()) "±${loc.accuracy.toInt()}m" else ""
                    if (hardwareToolsManager.isLiveTracking.value) {
                        _gpsProviderInfo.value = "RASTREIO VIVO ATIVO • $speedStr $accStr"
                    }
                }
            }
        }
    }

    /**
     * Dispara um Auto-Backup em segundo plano (com debounce de 2.5s) sempre que o usuário
     * cadastra, edita ou altera dados importantes, mantendo no máximo 3 arquivos na pasta pública.
     */
    private fun scheduleAutoBackup() {
        autoBackupJob?.cancel()
        autoBackupJob = viewModelScope.launch {
            delay(2500)
            val res = backupManager.performRollingBackup(isAutoBackup = true)
            if (res.isSuccess) {
                _availableBackups.value = backupManager.listAvailableBackups()
            }
        }
    }

    fun refreshAvailableBackups() {
        viewModelScope.launch {
            _availableBackups.value = backupManager.listAvailableBackups()
        }
    }

    fun triggerManualBackupNow() {
        viewModelScope.launch {
            _isBackupBusy.value = true
            _backupStatusMessage.value = "Compactando e salvando backup na pasta Download/ResilienceHub_Backups..."
            val result = backupManager.performRollingBackup(isAutoBackup = false)
            _backupStatusMessage.value = result.message
            _availableBackups.value = backupManager.listAvailableBackups()
            _isBackupBusy.value = false
        }
    }

    fun exportBackupToUri(targetUri: Uri) {
        viewModelScope.launch {
            _isBackupBusy.value = true
            _backupStatusMessage.value = "Exportando arquivo de backup (.zip)..."
            val result = backupManager.exportBackupToUri(targetUri)
            _backupStatusMessage.value = result.message
            _isBackupBusy.value = false
        }
    }

    fun restoreLatestBackup() {
        val latest = _availableBackups.value.firstOrNull()
        if (latest != null) {
            restoreFromSnapshot(latest)
        } else {
            _backupStatusMessage.value = "⚠ Nenhum backup automático encontrado na pasta. Use 'Importar Arquivo (.zip)' para selecionar um arquivo."
        }
    }

    fun restoreFromSnapshot(snapshot: BackupManager.BackupSnapshotInfo) {
        viewModelScope.launch {
            _isBackupBusy.value = true
            _backupStatusMessage.value = "Restaurando dados de ${snapshot.formattedDate}..."
            val result = backupManager.restoreFromBackupFile(File(snapshot.filePath))
            _familyDocuments.value = loadFamilyDocumentsFromDisk()
            _backupStatusMessage.value = result.message
            _isBackupBusy.value = false
        }
    }

    fun restoreFromBackupUri(uri: Uri) {
        viewModelScope.launch {
            _isBackupBusy.value = true
            _backupStatusMessage.value = "Restaurando backup a partir do arquivo selecionado..."
            val result = backupManager.restoreFromBackupUri(uri)
            _familyDocuments.value = loadFamilyDocumentsFromDisk()
            _backupStatusMessage.value = result.message
            _availableBackups.value = backupManager.listAvailableBackups()
            _isBackupBusy.value = false
        }
    }

    private fun appendPointToTrail(lat: Double, lon: Double) {
        val currentList = _movementTrail.value
        val last = currentList.lastOrNull()
        if (last == null) {
            _movementTrail.value = listOf(lat to lon)
            return
        }
        val distMeters = engine.haversineDistanceMeters(last.first, last.second, lat, lon)
        if (distMeters >= 1.0) {
            val bearing = engine.calculateBearingDegrees(last.first, last.second, lat, lon).toFloat()
            if (_currentHeadingDegrees.value == 0f || distMeters >= 2.0) {
                _currentHeadingDegrees.value = bearing
            }
            _movementTrail.value = (currentList + (lat to lon)).takeLast(250)
        }
    }

    val uiState: StateFlow<MainUiState> = combine(
        listOf(
            repository.allItems,
            repository.allProfiles,
            repository.allPois,
            _manualChunks,
            _crisisMode,
            _selectedLocation,
            _selectedCategory,
            _manualQuery,
            _currentLat,
            _currentLon,
            hardwareToolsManager.isGpsActive,
            hardwareToolsManager.isLiveTracking,
            _currentSpeedKmh,
            _currentHeadingDegrees,
            _currentAccuracyMeters,
            _movementTrail,
            _gpsProviderInfo,
            _selectedTab,
            hardwareToolsManager.isTorchOn,
            hardwareToolsManager.isSosActive,
            hardwareToolsManager.isWhistleActive,
            _ragResponse,
            _isRagLoading,
            _ragModeAi,
            _pdfImportStatus,
            _chatMessages,
            _familyDocuments,
            _availableBackups,
            _backupStatusMessage,
            _isBackupBusy,
            _isModelLoaded,
            _isModelInitializing,
            _loadedModelName,
            _hasSavedModelOnDisk,
            _gpsFixCount
        )
    ) { flowsArray: Array<Any?> ->
        @Suppress("UNCHECKED_CAST")
        val allItems = flowsArray[0] as List<InventoryItemEntity>
        @Suppress("UNCHECKED_CAST")
        val profiles = flowsArray[1] as List<FamilyProfileEntity>
        @Suppress("UNCHECKED_CAST")
        val pois = flowsArray[2] as List<MapPoiEntity>
        @Suppress("UNCHECKED_CAST")
        val manualChunks = flowsArray[3] as List<ManualChunkFtsEntity>
        val crisisMode = flowsArray[4] as Boolean
        val locationFilter = flowsArray[5] as StorageLocation?
        val categoryFilter = flowsArray[6] as ItemCategory?
        val manualQuery = flowsArray[7] as String
        val lat = flowsArray[8] as Double
        val lon = flowsArray[9] as Double
        val isGpsActive = flowsArray[10] as Boolean
        val isLiveTracking = flowsArray[11] as Boolean
        val speedKmh = flowsArray[12] as Float
        val headingDeg = flowsArray[13] as Float
        val accuracyM = flowsArray[14] as Float
        @Suppress("UNCHECKED_CAST")
        val movementTrail = flowsArray[15] as List<Pair<Double, Double>>
        val gpsProviderInfo = flowsArray[16] as String
        val tab = flowsArray[17] as Int
        val isTorchOn = flowsArray[18] as Boolean
        val isSosActive = flowsArray[19] as Boolean
        val isWhistleActive = flowsArray[20] as Boolean
        @Suppress("UNCHECKED_CAST")
        val ragResponse = flowsArray[21] as? PdfRagManager.RagResponse
        val isRagLoading = flowsArray[22] as Boolean
        val ragModeAi = flowsArray[23] as Boolean
        val pdfImportStatus = flowsArray[24] as? String
        @Suppress("UNCHECKED_CAST")
        val chatMessages = flowsArray[25] as List<ChatMessage>
        @Suppress("UNCHECKED_CAST")
        val familyDocuments = flowsArray[26] as List<FamilyDocument>
        @Suppress("UNCHECKED_CAST")
        val availableBackups = flowsArray[27] as List<BackupManager.BackupSnapshotInfo>
        val backupStatusMessage = flowsArray[28] as? String
        val isBackupBusy = flowsArray[29] as Boolean
        val isModelLoaded = flowsArray[30] as Boolean
        val isModelInitializing = flowsArray[31] as Boolean
        val loadedModelName = flowsArray[32] as? String
        val hasSavedModelOnDisk = flowsArray[33] as Boolean
        val gpsFixCount = flowsArray[34] as Int

        // Filtragem do estoque
        val filteredItems = allItems.filter { item ->
            (locationFilter == null || item.location == locationFilter) &&
            (categoryFilter == null || item.category == categoryFilter)
        }

        // Avaliação FEFO e Manutenção de Bateria
        val evaluated = filteredItems.map { item ->
            engine.evaluateItemStatus(item)
        }

        // Relatório de Autonomia de Água
        val waterReport = engine.generateWaterReport(allItems, profiles)

        // Navegação Matemática Offline via Haversine para todos os POIs
        val navList = pois.map { poi ->
            engine.calculatePoiNavigation(lat, lon, poi)
        }.sortedBy { it.distanceMeters }

        MainUiState(
            crisisMode = crisisMode,
            items = filteredItems,
            allRegisteredItems = allItems,
            evaluatedItems = evaluated,
            waterReport = waterReport,
            profiles = profiles,
            familyDocuments = familyDocuments,
            pois = pois,
            poiNavigationList = navList,
            manualChunks = manualChunks,
            manualQuery = manualQuery,
            selectedLocation = locationFilter,
            selectedCategory = categoryFilter,
            currentLat = lat,
            currentLon = lon,
            isGpsActive = isGpsActive,
            isLiveTracking = isLiveTracking,
            currentSpeedKmh = speedKmh,
            currentHeadingDegrees = headingDeg,
            currentAccuracyMeters = accuracyM,
            movementTrail = movementTrail,
            gpsProviderInfo = gpsProviderInfo,
            gpsFixCount = gpsFixCount,
            selectedTab = tab,
            isTorchOn = isTorchOn,
            isSosActive = isSosActive,
            isWhistleActive = isWhistleActive,
            chatMessages = chatMessages,
            ragResponse = ragResponse,
            isRagLoading = isRagLoading,
            ragModeAi = ragModeAi,
            pdfImportStatus = pdfImportStatus,
            isModelLoaded = isModelLoaded,
            isModelInitializing = isModelInitializing,
            loadedModelName = loadedModelName,
            hasSavedModelOnDisk = hasSavedModelOnDisk,
            availableBackups = availableBackups,
            backupStatusMessage = backupStatusMessage,
            isBackupBusy = isBackupBusy
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MainUiState(chatMessages = listOf(createWelcomeMessage()))
    )

    fun toggleCrisisMode() {
        _crisisMode.value = !_crisisMode.value
    }

    fun setSelectedTab(tab: Int) {
        _selectedTab.value = tab
    }

    fun setLocationFilter(location: StorageLocation?) {
        _selectedLocation.value = location
    }

    fun setCategoryFilter(category: ItemCategory?) {
        _selectedCategory.value = category
    }

    fun setManualQuery(query: String) {
        _manualQuery.value = query
    }

    fun setRagModeAi(enabled: Boolean) {
        _ragModeAi.value = enabled
        pendingFreeTopicQuestion = null
    }

    fun getLocalModelStatusText(): String = pdfRagManager.getActiveLocalModelDescription()

    fun getPreferredBackend(): String = pdfRagManager.getPreferredBackend()

    fun switchModelBackend(backendName: String) {
        pdfRagManager.setPreferredBackend(backendName)
        if (pdfRagManager.findLocalModelFile() != null) {
            loadSavedModelFromDisk(forceReload = true)
        } else {
            _pdfImportStatus.value = "⚙️ Acelerador preferido alterado para $backendName. Selecione o arquivo .task para carregar."
        }
    }

    // --- Hardware Tools Controls ---

    fun toggleTorch() {
        hardwareToolsManager.toggleTorch()
    }

    fun toggleSosMorse() {
        if (hardwareToolsManager.isSosActive.value) {
            hardwareToolsManager.stopSosMorse()
        } else {
            hardwareToolsManager.startSosMorse()
        }
    }

    fun toggleWhistle() {
        if (hardwareToolsManager.isWhistleActive.value) {
            hardwareToolsManager.stopWhistle()
        } else {
            hardwareToolsManager.startWhistle(isPulseMode = true)
        }
    }

    /**
     * Alterna o Rastreio Vivo (movimentação em tempo real no mapa com trilha de deslocamento).
     */
    fun toggleLiveGpsTracking() {
        if (hardwareToolsManager.isLiveTracking.value) {
            hardwareToolsManager.stopLiveLocationTracking()
            _currentSpeedKmh.value = 0f
            _gpsProviderInfo.value = "RASTREIO PAUSADO • GPS DESLIGADO (Economia)"
        } else {
            val started = hardwareToolsManager.startLiveLocationTracking()
            if (started) {
                _gpsProviderInfo.value = "RASTREIO VIVO ATIVO • ACOMPANHANDO MOVIMENTO..."
            } else {
                _gpsProviderInfo.value = "SEM PERMISSÃO DE LOCALIZAÇÃO PARA RASTREIO"
            }
        }
    }

    fun clearMovementTrail() {
        _movementTrail.value = listOf(_currentLat.value to _currentLon.value)
    }

    /**
     * GPS Pulso On-Demand: liga o hardware, busca coordenada com timeout de 8s e desliga imediatamente.
     */
    fun triggerSingleShotGpsPulse() {
        viewModelScope.launch {
            _gpsProviderInfo.value = "BUSCANDO FIX SATÉLITE (Timeout 8s)..."
            val result = hardwareToolsManager.requestSingleShotLocation()
            if (result.location != null) {
                _currentLat.value = result.location.latitude
                _currentLon.value = result.location.longitude
                if (result.location.hasAccuracy()) {
                    _currentAccuracyMeters.value = result.location.accuracy
                }
                if (result.location.hasBearing()) {
                    _currentHeadingDegrees.value = result.location.bearing
                }
                appendPointToTrail(result.location.latitude, result.location.longitude)
                _gpsFixCount.value += 1
                _gpsProviderInfo.value = "FIX OBTIDO (${result.providerUsed}) em ${result.durationMs}ms • GPS DESLIGADO"
            } else {
                _gpsProviderInfo.value = "TIMEOUT GPS (8s) • GPS DESLIGADO PARA SALVAR BATERIA"
            }
        }
    }

    fun triggerImmediateExpirationCheck() {
        ExpirationWorker.runImmediateCheck(getApplication())
    }

    // --- Chat IA RAG Operations (Base Oficial dos Órgãos Oficiais) ---

    fun importPdfFromUri(uri: Uri) {
        importFamilyDocumentPdf(
            uri = uri,
            title = pdfRagManager.getFileNameFromUri(uri)?.removeSuffix(".pdf") ?: "Documento da Família",
            docType = "RECEITA_MEDICA",
            memberName = "Família"
        )
    }

    fun importModelFromUri(uri: Uri) {
        viewModelScope.launch {
            _isModelInitializing.value = true
            _pdfImportStatus.value = "📥 Preparando modelo IA local (.task)..."
            val result = pdfRagManager.importModelFromUri(uri) { progressMsg ->
                _pdfImportStatus.value = progressMsg
            }
            result.fold(
                onSuccess = { nameWithBackend ->
                    _isModelLoaded.value = pdfRagManager.isModelLoadedInMemory()
                    _loadedModelName.value = pdfRagManager.getLoadedModelName()
                    _hasSavedModelOnDisk.value = true
                    _pdfImportStatus.value = "✅ Modelo \"$nameWithBackend\" carregado na memória e pronto (RAG + Tema Livre)!"
                    _ragModeAi.value = true
                },
                onFailure = { err ->
                    _isModelLoaded.value = pdfRagManager.isModelLoadedInMemory()
                    _pdfImportStatus.value = "❌ Falha ao carregar modelo IA: ${err.localizedMessage}"
                }
            )
            _isModelInitializing.value = false
        }
    }

    fun loadSavedModelFromDisk(forceReload: Boolean = false) {
        viewModelScope.launch {
            _isModelInitializing.value = true
            _pdfImportStatus.value = "⚙️ Carregando modelo salvo na memória (LiteRT-LM ${pdfRagManager.getPreferredBackend()})..."
            val result = pdfRagManager.loadExistingModelIntoMemory(forceReload = forceReload) { progressMsg ->
                _pdfImportStatus.value = progressMsg
            }
            result.fold(
                onSuccess = { nameWithBackend ->
                    _isModelLoaded.value = pdfRagManager.isModelLoadedInMemory()
                    _loadedModelName.value = pdfRagManager.getLoadedModelName()
                    _hasSavedModelOnDisk.value = true
                    _pdfImportStatus.value = "✅ Modelo \"$nameWithBackend\" carregado na memória e pronto!"
                },
                onFailure = { err ->
                    _isModelLoaded.value = pdfRagManager.isModelLoadedInMemory()
                    _pdfImportStatus.value = "❌ Falha ao carregar modelo: ${err.localizedMessage}"
                }
            )
            _isModelInitializing.value = false
        }
    }

    /**
     * Constrói pares (Pergunta do Usuário -> Resposta da IA) das últimas mensagens para contexto Multi-Turn.
     */
    private fun buildRecentConversationHistory(): List<Pair<String, String>> {
        val msgs = _chatMessages.value.drop(1) // ignora mensagem de boas-vindas
        val pairs = mutableListOf<Pair<String, String>>()
        var lastUserText: String? = null
        for (m in msgs) {
            if (m.isUser) {
                lastUserText = m.text
            } else if (lastUserText != null && !m.requiresFreeTopicConfirmation) {
                pairs.add(lastUserText to m.text)
                lastUserText = null
            }
        }
        return pairs.takeLast(3)
    }

    /**
     * Envia uma pergunta no Chat Unificado:
     * - Combina automaticamente o conhecimento do RAG (quando relevante) com a capacidade livre
     *   do modelo treinado para responder sobre qualquer assunto.
     * - Exibe fase de raciocínio ("Pensando...") e transmite a resposta aos poucos (token a token),
     *   igual ao Google AI Edge Gallery.
     */
    fun askRagQuestion(question: String) {
        val cleanQuestion = question.trim()
        if (cleanQuestion.isBlank()) return

        val recentHistory = buildRecentConversationHistory()
        val userMessage = ChatMessage(
            text = cleanQuestion,
            isUser = true
        )
        val streamingAiId = UUID.randomUUID().toString()
        val placeholderAiMessage = ChatMessage(
            id = streamingAiId,
            text = "",
            isUser = false,
            executionMode = "PROCESSANDO...",
            isStreaming = true,
            thinkingStatus = "🧠 Pensando e analisando contexto..."
        )
        _chatMessages.value = _chatMessages.value + userMessage + placeholderAiMessage

        viewModelScope.launch {
            _isRagLoading.value = true

            val lastTopic = lastAnsweredTopicQuery
            val isShortFollowUpForDetails = lastTopic != null &&
                pdfRagManager.isUserAskingForMoreDetails(cleanQuestion) &&
                cleanQuestion.split(Regex("\\s+")).size <= 6

            val targetQuery = if (isShortFollowUpForDetails && lastTopic != null) lastTopic else cleanQuestion
            val forceDetailed = isShortFollowUpForDetails && lastTopic != null
            if (!forceDetailed) {
                lastAnsweredTopicQuery = cleanQuestion
            }

            val finalResp = pdfRagManager.streamUnifiedAiResponse(
                query = targetQuery,
                recentHistory = recentHistory,
                forceDetailed = forceDetailed,
                onThinkingStatus = { statusText ->
                    _chatMessages.value = _chatMessages.value.map { msg ->
                        if (msg.id == streamingAiId) msg.copy(thinkingStatus = statusText) else msg
                    }
                },
                onPartialAnswer = { partialText ->
                    _chatMessages.value = _chatMessages.value.map { msg ->
                        if (msg.id == streamingAiId) {
                            msg.copy(
                                text = partialText,
                                thinkingStatus = null,
                                executionMode = "GERANDO RESPOSTA..."
                            )
                        } else msg
                    }
                }
            )

            _ragResponse.value = finalResp
            _isModelLoaded.value = pdfRagManager.isModelLoadedInMemory()
            _loadedModelName.value = pdfRagManager.getLoadedModelName()
            _chatMessages.value = _chatMessages.value.map { msg ->
                if (msg.id == streamingAiId) {
                    msg.copy(
                        text = finalResp.answer,
                        isStreaming = false,
                        thinkingStatus = null,
                        executionMode = finalResp.executionMode,
                        citedSources = finalResp.citedSources,
                        canExpandDetails = finalResp.canExpandDetails,
                        topicQueryForExpansion = finalResp.topicQueryForExpansion,
                        isFreeTopic = finalResp.citedSources.isEmpty()
                    )
                } else msg
            }
            _isRagLoading.value = false
        }
    }

    fun confirmFreeTopicQuestion(freeQuery: String) {
        askRagQuestion(freeQuery)
    }

    fun rejectFreeTopicQuestion() {
        pendingFreeTopicQuestion = null
    }

    /**
     * Chamado quando o usuário toca no botão "🔍 Quero mais detalhes" em uma resposta sucinta.
     */
    fun requestMoreDetailsOnTopic(topicQuery: String, isFreeTopic: Boolean) {
        if (topicQuery.isBlank()) return
        val recentHistory = buildRecentConversationHistory()
        val streamingAiId = UUID.randomUUID().toString()

        _chatMessages.value = _chatMessages.value.map {
            if (it.topicQueryForExpansion == topicQuery) it.copy(canExpandDetails = false) else it
        } + ChatMessage(
            text = "Quero mais detalhes sobre isso.",
            isUser = true
        ) + ChatMessage(
            id = streamingAiId,
            text = "",
            isUser = false,
            executionMode = "APROFUNDANDO...",
            isStreaming = true,
            thinkingStatus = "🧠 Elaborando explicação detalhada..."
        )

        viewModelScope.launch {
            _isRagLoading.value = true
            val detailedResp = pdfRagManager.streamUnifiedAiResponse(
                query = topicQuery,
                recentHistory = recentHistory,
                forceDetailed = true,
                onThinkingStatus = { statusText ->
                    _chatMessages.value = _chatMessages.value.map { msg ->
                        if (msg.id == streamingAiId) msg.copy(thinkingStatus = statusText) else msg
                    }
                },
                onPartialAnswer = { partialText ->
                    _chatMessages.value = _chatMessages.value.map { msg ->
                        if (msg.id == streamingAiId) {
                            msg.copy(
                                text = partialText,
                                thinkingStatus = null,
                                executionMode = "GERANDO DETALHES..."
                            )
                        } else msg
                    }
                }
            )

            _ragResponse.value = detailedResp
            _chatMessages.value = _chatMessages.value.map { msg ->
                if (msg.id == streamingAiId) {
                    msg.copy(
                        text = detailedResp.answer,
                        isStreaming = false,
                        thinkingStatus = null,
                        executionMode = detailedResp.executionMode,
                        citedSources = detailedResp.citedSources,
                        canExpandDetails = false,
                        isFreeTopic = detailedResp.citedSources.isEmpty()
                    )
                } else msg
            }
            _isRagLoading.value = false
        }
    }

    fun clearChatHistory() {
        pendingFreeTopicQuestion = null
        lastAnsweredTopicQuery = null
        lastAnsweredWasFreeTopic = false
        pdfRagManager.resetConversation()
        _chatMessages.value = listOf(createWelcomeMessage())
        _ragResponse.value = null
    }

    fun restoreOfficialKnowledgeBase() {
        viewModelScope.launch {
            repository.restoreOfficialKnowledgeBase()
            _pdfImportStatus.value = "✓ Base Oficial de Sobrevivência (30 diretrizes oficiais) restaurada com sucesso!"
        }
    }

    // --- Family Prescriptions & Documents PDF Storage (100% Offline) ---

    fun importFamilyDocumentPdf(
        uri: Uri,
        title: String,
        docType: String,
        memberName: String
    ) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val docId = UUID.randomUUID().toString()
                    val rawFileName = pdfRagManager.getFileNameFromUri(uri) ?: "receita_documento.pdf"
                    val safeFileName = "${docId.take(8)}_${rawFileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")}"
                    val destFile = File(familyDocsDir, if (safeFileName.endsWith(".pdf", true)) safeFileName else "$safeFileName.pdf")

                    val inputStream = getApplication<Application>().contentResolver.openInputStream(uri)
                        ?: return@withContext

                    inputStream.use { input ->
                        destFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }

                    val (pages, previewText) = pdfRagManager.extractFamilyPdfPreview(destFile)
                    val newDoc = FamilyDocument(
                        id = docId,
                        title = title.trim().ifBlank { rawFileName.removeSuffix(".pdf") },
                        docType = docType,
                        memberName = memberName.trim().ifBlank { "Família" },
                        fileName = rawFileName,
                        filePath = destFile.absolutePath,
                        pageCount = pages,
                        extractedText = previewText,
                        addedAtMillis = System.currentTimeMillis()
                    )

                    val updatedList = listOf(newDoc) + _familyDocuments.value
                    saveFamilyDocumentsToDisk(updatedList)
                    _familyDocuments.value = updatedList
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            scheduleAutoBackup()
        }
    }

    fun deleteFamilyDocument(docId: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val target = _familyDocuments.value.find { it.id == docId }
                if (target != null) {
                    runCatching { File(target.filePath).delete() }
                }
                val updated = _familyDocuments.value.filterNot { it.id == docId }
                saveFamilyDocumentsToDisk(updated)
                _familyDocuments.value = updated
            }
            scheduleAutoBackup()
        }
    }

    fun clearAllFamilyDocuments() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                _familyDocuments.value.forEach { doc ->
                    runCatching { File(doc.filePath).delete() }
                }
                saveFamilyDocumentsToDisk(emptyList())
                _familyDocuments.value = emptyList()
            }
        }
    }

    private fun loadFamilyDocumentsFromDisk(): List<FamilyDocument> {
        if (!familyDocsMetaFile.exists()) return emptyList()
        return try {
            val jsonStr = familyDocsMetaFile.readText()
            val arr = JSONArray(jsonStr)
            val result = mutableListOf<FamilyDocument>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val filePath = obj.optString("filePath")
                if (filePath.isNotBlank() && File(filePath).exists()) {
                    result.add(
                        FamilyDocument(
                            id = obj.optString("id"),
                            title = obj.optString("title"),
                            docType = obj.optString("docType", "RECEITA_MEDICA"),
                            memberName = obj.optString("memberName", "Família"),
                            fileName = obj.optString("fileName", "documento.pdf"),
                            filePath = filePath,
                            pageCount = obj.optInt("pageCount", 1),
                            extractedText = obj.optString("extractedText", ""),
                            addedAtMillis = obj.optLong("addedAtMillis", System.currentTimeMillis())
                        )
                    )
                }
            }
            result
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveFamilyDocumentsToDisk(docs: List<FamilyDocument>) {
        try {
            val arr = JSONArray()
            docs.forEach { doc ->
                val obj = JSONObject().apply {
                    put("id", doc.id)
                    put("title", doc.title)
                    put("docType", doc.docType)
                    put("memberName", doc.memberName)
                    put("fileName", doc.fileName)
                    put("filePath", doc.filePath)
                    put("pageCount", doc.pageCount)
                    put("extractedText", doc.extractedText)
                    put("addedAtMillis", doc.addedAtMillis)
                }
                arr.put(obj)
            }
            familyDocsMetaFile.writeText(arr.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // --- Inventory, Profiles, POI, Manuals Mutations & Deletion ---

    fun updateItemQuantity(itemId: Long, delta: Double) {
        viewModelScope.launch {
            val item = uiState.value.allRegisteredItems.find { it.id == itemId } ?: return@launch
            val newQty = (item.quantity + delta).coerceAtLeast(0.0)
            repository.updateQuantity(itemId, newQty)
            scheduleAutoBackup()
        }
    }

    fun registerBatteryMaintenance(itemId: Long) {
        viewModelScope.launch {
            repository.registerMaintenance(itemId, System.currentTimeMillis())
            scheduleAutoBackup()
        }
    }

    /**
     * Girar/Renovar Item: atualiza para novo prazo ou Mês/Ano escolhido.
     */
    fun rotateItem(itemId: Long, extendDays: Int = 180) {
        viewModelScope.launch {
            val item = uiState.value.allRegisteredItems.find { it.id == itemId } ?: return@launch
            val newExp = System.currentTimeMillis() + (extendDays.toLong() * 24 * 3600 * 1000)
            val updated = item.copy(expirationDate = newExp)
            repository.updateItem(updated)
            scheduleAutoBackup()
        }
    }

    fun updateItemExpirationMonthYear(itemId: Long, month: Int, year: Int) {
        viewModelScope.launch {
            val item = uiState.value.allRegisteredItems.find { it.id == itemId } ?: return@launch
            val newExp = monthYearToExpirationTimestamp(month, year)
            repository.updateItem(item.copy(expirationDate = newExp))
            ExpirationWorker.runImmediateCheck(getApplication())
            scheduleAutoBackup()
        }
    }

    /**
     * Atualiza um produto existente (incluindo sua CATEGORIA, local, nome, código de barras,
     * quantidade, unidade e validade Mês/Ano), disparando automaticamente a reavaliação
     * dos protocolos de sobrevivência e alertas da nova categoria.
     */
    fun updateInventoryItem(
        itemId: Long,
        name: String,
        barcode: String?,
        category: ItemCategory,
        location: StorageLocation,
        quantity: Double,
        unit: String,
        expirationMonth: Int?,
        expirationYear: Int?,
        resetBatteryMaintenanceNow: Boolean = false
    ) {
        viewModelScope.launch {
            val existing = uiState.value.allRegisteredItems.find { it.id == itemId } ?: return@launch
            val newExp = monthYearToExpirationTimestamp(expirationMonth, expirationYear)
            val updatedMaint = when {
                resetBatteryMaintenanceNow -> System.currentTimeMillis()
                category == ItemCategory.ENERGY_GEAR && existing.lastMaintenanceDate == null -> System.currentTimeMillis()
                else -> existing.lastMaintenanceDate
            }

            val updatedItem = existing.copy(
                name = name.trim().ifBlank { existing.name },
                barcode = barcode?.trim()?.ifBlank { null },
                category = category,
                location = location,
                quantity = quantity.coerceAtLeast(0.0),
                unit = unit.trim().ifBlank { existing.unit },
                expirationDate = newExp,
                lastMaintenanceDate = updatedMaint
            )

            repository.updateItem(updatedItem)
            ExpirationWorker.runImmediateCheck(getApplication())
            scheduleAutoBackup()
        }
    }

    fun addNewItemWithMonthYear(
        name: String,
        category: ItemCategory,
        location: StorageLocation,
        quantity: Double,
        unit: String,
        expirationMonth: Int?,
        expirationYear: Int?,
        barcode: String? = null,
        forceSeparateLot: Boolean = false
    ) {
        viewModelScope.launch {
            val cleanBarcode = barcode?.trim()?.ifBlank { null }
            val expDate = monthYearToExpirationTimestamp(expirationMonth, expirationYear)

            // Se o código de barras já existir no estoque e o usuário não pediu explicitamente outro lote separado,
            // acrescenta a quantidade (+1 ou +quantidade informada) ao item já cadastrado!
            if (cleanBarcode != null && !forceSeparateLot) {
                val matches = repository.findAllByBarcode(cleanBarcode)
                val targetExisting = matches.find { it.location == location && it.expirationDate == expDate }
                    ?: matches.find { it.location == location }
                    ?: matches.firstOrNull()

                if (targetExisting != null) {
                    val updatedQty = (targetExisting.quantity + quantity).coerceAtLeast(0.0)
                    repository.updateQuantity(targetExisting.id, updatedQty)
                    scheduleAutoBackup()
                    return@launch
                }
            }

            val now = System.currentTimeMillis()
            val lastMaint = if (category == ItemCategory.ENERGY_GEAR) now else null

            repository.insertItem(
                InventoryItemEntity(
                    name = name,
                    barcode = cleanBarcode,
                    category = category,
                    location = location,
                    quantity = quantity,
                    unit = unit,
                    expirationDate = expDate,
                    lastMaintenanceDate = lastMaint,
                    alertDaysBefore = 90
                )
            )
            scheduleAutoBackup()
        }
    }

    fun addNewItem(
        name: String,
        category: ItemCategory,
        location: StorageLocation,
        quantity: Double,
        unit: String,
        expirationDays: Int?,
        barcode: String? = null,
        forceSeparateLot: Boolean = false
    ) {
        viewModelScope.launch {
            val cleanBarcode = barcode?.trim()?.ifBlank { null }
            if (cleanBarcode != null && !forceSeparateLot) {
                val existing = repository.findByBarcode(cleanBarcode)
                if (existing != null) {
                    val updatedQty = (existing.quantity + quantity).coerceAtLeast(0.0)
                    repository.updateQuantity(existing.id, updatedQty)
                    scheduleAutoBackup()
                    return@launch
                }
            }

            val now = System.currentTimeMillis()
            val expDate = expirationDays?.let { now + (it.toLong() * 24 * 3600 * 1000) }
            val lastMaint = if (category == ItemCategory.ENERGY_GEAR) now else null

            repository.insertItem(
                InventoryItemEntity(
                    name = name,
                    barcode = cleanBarcode,
                    category = category,
                    location = location,
                    quantity = quantity,
                    unit = unit,
                    expirationDate = expDate,
                    lastMaintenanceDate = lastMaint,
                    alertDaysBefore = 90
                )
            )
            scheduleAutoBackup()
        }
    }

    fun deleteItem(itemId: Long) {
        viewModelScope.launch {
            repository.deleteItemById(itemId)
            scheduleAutoBackup()
        }
    }

    fun clearAllItems() {
        viewModelScope.launch {
            repository.clearAllItems()
        }
    }

    fun addNewProfile(
        name: String,
        role: FamilyRole,
        weightKg: Double,
        bloodType: String,
        allergies: String,
        medications: String
    ) {
        viewModelScope.launch {
            repository.insertProfile(
                FamilyProfileEntity(
                    name = name,
                    role = role,
                    weightKg = weightKg,
                    bloodType = bloodType,
                    allergies = allergies,
                    medications = medications
                )
            )
            scheduleAutoBackup()
        }
    }

    /**
     * Atualiza os dados de um membro familiar já cadastrado.
     */
    fun updateProfile(
        id: Long,
        name: String,
        role: FamilyRole,
        weightKg: Double,
        bloodType: String,
        allergies: String,
        medications: String
    ) {
        viewModelScope.launch {
            repository.updateProfile(
                FamilyProfileEntity(
                    id = id,
                    name = name.trim(),
                    role = role,
                    weightKg = weightKg,
                    bloodType = bloodType.trim().ifBlank { "O+" },
                    allergies = allergies.trim().ifBlank { "Nenhuma declarada" },
                    medications = medications.trim().ifBlank { "Nenhuma de uso contínuo" }
                )
            )
            scheduleAutoBackup()
        }
    }

    fun deleteProfile(profileId: Long) {
        viewModelScope.launch {
            repository.deleteProfileById(profileId)
            scheduleAutoBackup()
        }
    }

    fun clearAllProfiles() {
        viewModelScope.launch {
            repository.clearAllProfiles()
        }
    }

    fun addNewPoi(
        title: String,
        type: PoiType,
        latitude: Double,
        longitude: Double,
        address: String,
        phone: String?
    ) {
        viewModelScope.launch {
            repository.insertPoi(
                MapPoiEntity(
                    title = title,
                    type = type,
                    latitude = latitude,
                    longitude = longitude,
                    address = address,
                    phone = phone
                )
            )
            scheduleAutoBackup()
        }
    }

    fun deletePoi(poiId: Long) {
        viewModelScope.launch {
            repository.deletePoiById(poiId)
            scheduleAutoBackup()
        }
    }

    fun clearAllPois() {
        viewModelScope.launch {
            repository.clearAllPois()
        }
    }

    fun addNewManualChunk(
        docTitle: String,
        category: String,
        pageNumber: Int,
        contentText: String
    ) {
        viewModelScope.launch {
            repository.insertChunk(
                ManualChunkFtsEntity(
                    docTitle = docTitle.trim(),
                    category = category.trim().ifBlank { "GENERAL" },
                    pageNumber = pageNumber.coerceAtLeast(1),
                    contentText = contentText.trim()
                )
            )
            _pdfImportStatus.value = "✓ Protocolo \"${docTitle.trim()}\" adicionado à Base de Conhecimento da IA!"
            scheduleAutoBackup()
        }
    }

    fun deleteManualChunk(rowId: Int) {
        viewModelScope.launch {
            repository.deleteManualChunk(rowId)
        }
    }

    fun clearAllManualChunks() {
        viewModelScope.launch {
            repository.restoreOfficialKnowledgeBase()
            _pdfImportStatus.value = "✓ Base Oficial de Sobrevivência (30 protocolos) restaurada."
        }
    }

    /**
     * Limpa os dados fictícios (Estoque, Família, Pontos do Mapa),
     * mantendo a Base Oficial de Sobrevivência para que o Chat IA continue pronto para uso.
     */
    fun clearAllFictionalData() {
        viewModelScope.launch {
            repository.clearAllDatabaseData()
            _ragResponse.value = null
            _pdfImportStatus.value = "✓ Dados fictícios apagados! Base Oficial de Sobrevivência da IA mantida."
        }
    }

    fun triggerSingleShotGps(lat: Double = -22.9519, lon: Double = -43.1850) {
        triggerSingleShotGpsPulse()
    }

    override fun onCleared() {
        super.onCleared()
        hardwareToolsManager.releaseAll()
    }
}
