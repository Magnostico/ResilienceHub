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
import com.example.services.ExpirationWorker
import com.example.services.HardwareToolsManager
import com.example.services.PdfRagManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

data class MainUiState(
    val crisisMode: Boolean = false,
    val items: List<InventoryItemEntity> = emptyList(),
    val evaluatedItems: List<RationingAndAlertEngine.ItemEvaluationResult> = emptyList(),
    val waterReport: RationingAndAlertEngine.WaterAutonomyReport? = null,
    val profiles: List<FamilyProfileEntity> = emptyList(),
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
    val selectedTab: Int = 0, // 0: Estoque, 1: Mapas, 2: Manuais IA, 3: Ferramentas, 4: Família

    // Hardware states
    val isTorchOn: Boolean = false,
    val isSosActive: Boolean = false,
    val isWhistleActive: Boolean = false,

    // RAG states
    val ragResponse: PdfRagManager.RagResponse? = null,
    val isRagLoading: Boolean = false,
    val ragModeAi: Boolean = false,
    val pdfImportStatus: String? = null
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
    }

    private val database = AppDatabase.getInstance(application)
    private val repository = ResilienceRepository(database)
    val engine = RationingAndAlertEngine()

    val hardwareToolsManager = HardwareToolsManager(application)
    val pdfRagManager = PdfRagManager(application, database)

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
    private val _selectedTab = MutableStateFlow(0)

    // RAG Local States
    private val _ragResponse = MutableStateFlow<PdfRagManager.RagResponse?>(null)
    private val _isRagLoading = MutableStateFlow(false)
    private val _ragModeAi = MutableStateFlow(false)
    private val _pdfImportStatus = MutableStateFlow<String?>(null)

    private val _manualChunks = _manualQuery.flatMapLatest { query ->
        repository.searchManual(query)
    }

    init {
        // Agenda a checagem diária em segundo plano do WorkManager
        ExpirationWorker.scheduleDailyCheck(application)

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

                    val speedStr = String.format(Locale.US, "%.1f km/h", _currentSpeedKmh.value)
                    val accStr = if (loc.hasAccuracy()) "±${loc.accuracy.toInt()}m" else ""
                    _gpsProviderInfo.value = "RASTREIO VIVO ATIVO • $speedStr $accStr"
                }
            }
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
            _pdfImportStatus
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
            evaluatedItems = evaluated,
            waterReport = waterReport,
            profiles = profiles,
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
            selectedTab = tab,
            isTorchOn = isTorchOn,
            isSosActive = isSosActive,
            isWhistleActive = isWhistleActive,
            ragResponse = ragResponse,
            isRagLoading = isRagLoading,
            ragModeAi = ragModeAi,
            pdfImportStatus = pdfImportStatus
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MainUiState()
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
                appendPointToTrail(result.location.latitude, result.location.longitude)
                _gpsProviderInfo.value = "FIX OBTIDO (${result.providerUsed}) em ${result.durationMs}ms • GPS DESLIGADO"
            } else {
                _gpsProviderInfo.value = "TIMEOUT GPS (8s) • GPS DESLIGADO PARA SALVAR BATERIA"
            }
        }
    }

    fun triggerImmediateExpirationCheck() {
        ExpirationWorker.runImmediateCheck(getApplication())
    }

    // --- PDF RAG Operations ---

    fun importPdfFromUri(uri: Uri) {
        viewModelScope.launch {
            _pdfImportStatus.value = "Importando e extraindo páginas do PDF..."
            val result = pdfRagManager.ingestPdf(uri)
            if (result.isSuccess) {
                _pdfImportStatus.value = "✓ \"${result.docTitle}\" importado: ${result.totalPages} páginas, ${result.chunksGenerated} blocos FTS4 indexados!"
            } else {
                _pdfImportStatus.value = "❌ Falha ao importar PDF: ${result.errorMessage}"
            }
        }
    }

    fun importModelFromUri(uri: Uri) {
        viewModelScope.launch {
            _pdfImportStatus.value = "Copiando modelo LLM local (.task/.bin) para o armazenamento interno do app (aguarde)..."
            val result = pdfRagManager.importModelFromUri(uri)
            result.fold(
                onSuccess = { name ->
                    _pdfImportStatus.value = "✓ Modelo IA Local \"$name\" configurado e pronto para inferência offline!"
                    _ragModeAi.value = true
                },
                onFailure = { err ->
                    _pdfImportStatus.value = "❌ Falha ao importar modelo IA: ${err.localizedMessage}"
                }
            )
        }
    }

    fun askRagQuestion(question: String) {
        viewModelScope.launch {
            if (question.isBlank()) return@launch
            _isRagLoading.value = true
            val response = pdfRagManager.searchLocalAiRag(question)
            _ragResponse.value = response
            _isRagLoading.value = false
        }
    }

    // --- Inventory, Profiles, POI, Manuals Mutations & Deletion ---

    fun updateItemQuantity(itemId: Long, delta: Double) {
        viewModelScope.launch {
            val item = uiState.value.items.find { it.id == itemId } ?: return@launch
            val newQty = (item.quantity + delta).coerceAtLeast(0.0)
            repository.updateQuantity(itemId, newQty)
        }
    }

    fun registerBatteryMaintenance(itemId: Long) {
        viewModelScope.launch {
            repository.registerMaintenance(itemId, System.currentTimeMillis())
        }
    }

    /**
     * Girar/Renovar Item: atualiza para novo prazo ou Mês/Ano escolhido.
     */
    fun rotateItem(itemId: Long, extendDays: Int = 180) {
        viewModelScope.launch {
            val item = uiState.value.items.find { it.id == itemId } ?: return@launch
            val newExp = System.currentTimeMillis() + (extendDays.toLong() * 24 * 3600 * 1000)
            val updated = item.copy(expirationDate = newExp)
            repository.updateItem(updated)
        }
    }

    fun updateItemExpirationMonthYear(itemId: Long, month: Int, year: Int) {
        viewModelScope.launch {
            val item = uiState.value.items.find { it.id == itemId } ?: return@launch
            val newExp = monthYearToExpirationTimestamp(month, year)
            repository.updateItem(item.copy(expirationDate = newExp))
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
        barcode: String? = null
    ) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val expDate = monthYearToExpirationTimestamp(expirationMonth, expirationYear)
            val lastMaint = if (category == ItemCategory.ENERGY_GEAR) now else null

            repository.insertItem(
                InventoryItemEntity(
                    name = name,
                    barcode = barcode?.ifBlank { null },
                    category = category,
                    location = location,
                    quantity = quantity,
                    unit = unit,
                    expirationDate = expDate,
                    lastMaintenanceDate = lastMaint,
                    alertDaysBefore = 90
                )
            )
        }
    }

    fun addNewItem(
        name: String,
        category: ItemCategory,
        location: StorageLocation,
        quantity: Double,
        unit: String,
        expirationDays: Int?,
        barcode: String? = null
    ) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val expDate = expirationDays?.let { now + (it.toLong() * 24 * 3600 * 1000) }
            val lastMaint = if (category == ItemCategory.ENERGY_GEAR) now else null

            repository.insertItem(
                InventoryItemEntity(
                    name = name,
                    barcode = barcode?.ifBlank { null },
                    category = category,
                    location = location,
                    quantity = quantity,
                    unit = unit,
                    expirationDate = expDate,
                    lastMaintenanceDate = lastMaint,
                    alertDaysBefore = 90
                )
            )
        }
    }

    fun deleteItem(itemId: Long) {
        viewModelScope.launch {
            repository.deleteItemById(itemId)
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
        }
    }

    fun deleteProfile(profileId: Long) {
        viewModelScope.launch {
            repository.deleteProfileById(profileId)
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
        }
    }

    fun deletePoi(poiId: Long) {
        viewModelScope.launch {
            repository.deletePoiById(poiId)
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
                    category = category.trim().ifBlank { "GERAL" },
                    pageNumber = pageNumber.coerceAtLeast(1),
                    contentText = contentText.trim()
                )
            )
            _pdfImportStatus.value = "✓ Manual/Protocolo \"${docTitle.trim()}\" criado e indexado offline!"
        }
    }

    fun deleteManualChunk(rowId: Int) {
        viewModelScope.launch {
            repository.deleteManualChunk(rowId)
        }
    }

    fun clearAllManualChunks() {
        viewModelScope.launch {
            repository.clearAllManualChunks()
            _ragResponse.value = null
            _pdfImportStatus.value = "✓ Todos os manuais foram removidos."
        }
    }

    /**
     * Limpa absolutamente todos os dados cadastrados/fictícios do app (Estoque, Família, Pontos do Mapa e Manuais)
     * para o usuário começar 100% do zero com seus próprios dados.
     */
    fun clearAllFictionalData() {
        viewModelScope.launch {
            repository.clearAllDatabaseData()
            _ragResponse.value = null
            _pdfImportStatus.value = "✓ Todos os dados fictícios foram apagados!"
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
