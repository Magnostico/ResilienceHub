package com.example.ui.components

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.Typeface
import android.location.Geocoder
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.entity.MapPoiEntity
import com.example.data.model.PoiType
import com.example.domain.RationingAndAlertEngine
import com.example.ui.MainUiState
import com.example.ui.MainViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.*

private const val TILE_CACHE_DIR_NAME = "osm_tiles_v4"
private const val VALID_TILE_USER_AGENT =
    "ResilienceHubApp/2.0 (Android; https://github.com/Magnostico/ResilienceHub)"

private const val DEL_CASTILHO_LAT = -22.8792
private const val DEL_CASTILHO_LON = -43.2718

private data class NamedRoadVector(
    val name: String,
    val labelLat: Double,
    val labelLon: Double,
    val points: List<Pair<Double, Double>>,
    val isExpressway: Boolean = false
)

private enum class OfflineDownloadScope {
    CURRENT_NEIGHBORHOOD_HD,
    CORRIDOR_ROUTES_HD,
    ENTIRE_RIO_CITY_PLUS_PINS
}

private data class DrivingManeuverGuidance(
    val instructionTitle: String,
    val instructionSub: String,
    val suggestedCorridor: String,
    val turnDeltaDegrees: Float,
    val etaMinutes: Int,
    val arrivalTimeFormatted: String,
    val hasArrived: Boolean
)

/**
 * Base Vetorial Nativa Completa do Município do Rio de Janeiro (100% embutida no APK — 0 MB de internet):
 * Cobre Del Castilho, toda a Zona Norte, Centro, Zona Sul e Zona Oeste (Barra, Jacarepaguá, Bangu, Campo Grande).
 */
private val ALL_RIO_NAMED_ROADS: List<NamedRoadVector> = listOf(
    // --- ZONA NORTE & DEL CASTILHO (VIAS EXPRESSAS E PRINCIPAIS AVENIDAS) ---
    NamedRoadVector(
        name = "Linha Amarela",
        labelLat = -22.8768,
        labelLon = -43.2715,
        isExpressway = true,
        points = listOf(
            -22.8520 to -43.2380, // Fundão / Av. Brasil
            -22.8680 to -43.2550, // Bonsucesso / Inhaúma
            -22.8768 to -43.2715, // Del Castilho / Shopping Nova América (Saída 4/5)
            -22.8890 to -43.2895, // Engenho de Dentro / Abolição
            -22.9050 to -43.3100, // Água Santa / Serra Grajaú-Jacarepaguá
            -22.9360 to -43.3420, // Freguesia / Pechincha
            -22.9650 to -43.3620, // Cidade de Deus / Ayrton Senna
            -22.9980 to -43.3650  // Barra da Tijuca
        )
    ),
    NamedRoadVector(
        name = "Av. Dom Hélder Câmara",
        labelLat = -22.8805,
        labelLon = -43.2735,
        isExpressway = false,
        points = listOf(
            -22.8910 to -43.2400, // Benfica / Jacarezinho
            -22.8825 to -43.2620, // Maria da Graça
            -22.8798 to -43.2735, // Del Castilho (Nova América / Largo)
            -22.8855 to -43.2855, // Cachambi / NorteShopping
            -22.8880 to -43.3010, // Abolição / Pilares
            -22.8830 to -43.3180  // Cascadura
        )
    ),
    NamedRoadVector(
        name = "Av. Pastor Martin Luther King Jr.",
        labelLat = -22.8745,
        labelLon = -43.2765,
        isExpressway = false,
        points = listOf(
            -22.8960 to -43.2440, // Triagem
            -22.8840 to -43.2630, // Maria da Graça
            -22.8782 to -43.2718, // Del Castilho / Shopping Nova América
            -22.8710 to -43.2810, // Inhaúma
            -22.8595 to -43.3010, // Engenho da Rainha / Thomaz Coelho
            -22.8550 to -43.3120, // Vicente de Carvalho
            -22.8320 to -43.3280  // Irajá / Colégio
        )
    ),
    NamedRoadVector(
        name = "Estr. Adhemar Bebiano",
        labelLat = -22.8725,
        labelLon = -43.2795,
        points = listOf(
            -22.8795 to -43.2705, // Del Castilho
            -22.8725 to -43.2795, // Inhaúma
            -22.8655 to -43.2930, // Engenho da Rainha
            -22.8630 to -43.3040  // Tomás Coelho
        )
    ),
    NamedRoadVector(
        name = "Av. Brasil",
        labelLat = -22.8520,
        labelLon = -43.2550,
        isExpressway = true,
        points = listOf(
            -22.8920 to -43.2120, // Caju / Into
            -22.8720 to -43.2430, // Manguinhos / Bonsucesso
            -22.8420 to -43.2680, // Ramos / Penha
            -22.8250 to -43.3150, // Irajá / Trevo das Margaridas
            -22.8450 to -43.3750, // Guadalupe / Deodoro
            -22.8680 to -43.4500, // Realengo / Bangu
            -22.8850 to -43.5550  // Campo Grande
        )
    ),
    NamedRoadVector(
        name = "Linha Vermelha",
        labelLat = -22.8580,
        labelLon = -43.2320,
        isExpressway = true,
        points = listOf(
            -22.8980 to -43.2180, // São Cristóvão
            -22.8580 to -43.2320, // Maré / Fundão
            -22.8150 to -43.2480, // Galeão
            -22.7880 to -43.2850  // Dutra / Baixada
        )
    ),
    NamedRoadVector(
        name = "Rua Dias da Cruz / 24 de Maio",
        labelLat = -22.9020,
        labelLon = -43.2780,
        points = listOf(
            -22.9015 to -43.2550, // Riachuelo / Sampaio
            -22.9020 to -43.2780, // Méier
            -22.9065 to -43.2920  // Engenho de Dentro
        )
    ),
    NamedRoadVector(
        name = "Estr. do Galeão",
        labelLat = -22.8180,
        labelLon = -43.2150,
        points = listOf(
            -22.8320 to -43.2350,
            -22.8180 to -43.2150,
            -22.8050 to -43.1950
        )
    ),
    NamedRoadVector(
        name = "Av. Ernani Cardoso / Portela",
        labelLat = -22.8740,
        labelLon = -43.3350,
        points = listOf(
            -22.8830 to -43.3180, // Cascadura
            -22.8740 to -43.3350, // Madureira
            -22.8660 to -43.3520  // Oswaldo Cruz
        )
    ),
    NamedRoadVector(
        name = "Av. Maracanã",
        labelLat = -22.9155,
        labelLon = -43.2290,
        points = listOf(-22.9110 to -43.2190, -22.9155 to -43.2290, -22.9245 to -43.2325)
    ),
    NamedRoadVector(
        name = "Rua Conde de Bonfim",
        labelLat = -22.9260,
        labelLon = -43.2355,
        points = listOf(-22.9184 to -43.2185, -22.9260 to -43.2355, -22.9380 to -43.2465)
    ),

    // --- CENTRO & ZONA SUL ---
    NamedRoadVector(
        name = "Av. Presidente Vargas",
        labelLat = -22.9045,
        labelLon = -43.1910,
        isExpressway = true,
        points = listOf(-22.9015 to -43.1780, -22.9045 to -43.1910, -22.9095 to -43.2090)
    ),
    NamedRoadVector(
        name = "Ponte Rio-Niterói",
        labelLat = -22.8710,
        labelLon = -43.1650,
        isExpressway = true,
        points = listOf(-22.8850 to -43.2100, -22.8710 to -43.1650, -22.8820 to -43.1150)
    ),
    NamedRoadVector(
        name = "Aterro do Flamengo",
        labelLat = -22.9280,
        labelLon = -43.1720,
        isExpressway = true,
        points = listOf(-22.9120 to -43.1700, -22.9280 to -43.1720, -22.9420 to -43.1780)
    ),
    NamedRoadVector(
        name = "Túnel Rebouças",
        labelLat = -22.9460,
        labelLon = -43.2075,
        isExpressway = true,
        points = listOf(-22.9615 to -43.2095, -22.9460 to -43.2075, -22.9210 to -43.2085)
    ),
    NamedRoadVector(
        name = "Av. Atlântica",
        labelLat = -22.9725,
        labelLon = -43.1835,
        points = listOf(-22.9625 to -43.1680, -22.9715 to -43.1828, -22.9845 to -43.1892)
    ),
    NamedRoadVector(
        name = "Av. N. Sra. de Copacabana",
        labelLat = -22.9702,
        labelLon = -43.1862,
        points = listOf(-22.9630 to -43.1735, -22.9702 to -43.1862, -22.9835 to -43.1918)
    ),
    NamedRoadVector(
        name = "Rua Barata Ribeiro",
        labelLat = -22.9685,
        labelLon = -43.1885,
        points = listOf(-22.9635 to -43.1775, -22.9685 to -43.1885, -22.9810 to -43.1935)
    ),
    NamedRoadVector(
        name = "Praia de Botafogo",
        labelLat = -22.9455,
        labelLon = -43.1815,
        points = listOf(-22.9390 to -43.1765, -22.9455 to -43.1815, -22.9520 to -43.1810)
    ),
    NamedRoadVector(
        name = "Rua Voluntários da Pátria",
        labelLat = -22.9525,
        labelLon = -43.1885,
        points = listOf(-22.9480 to -43.1830, -22.9525 to -43.1885, -22.9560 to -43.1975)
    ),
    NamedRoadVector(
        name = "Rua São Clemente",
        labelLat = -22.9505,
        labelLon = -43.1915,
        points = listOf(-22.9470 to -43.1845, -22.9505 to -43.1915, -22.9565 to -43.1992)
    ),
    NamedRoadVector(
        name = "Av. Pasteur",
        labelLat = -22.9535,
        labelLon = -43.1715,
        points = listOf(-22.9520 to -43.1805, -22.9535 to -43.1715, -22.9545 to -43.1655)
    ),
    NamedRoadVector(
        name = "Rua Jardim Botânico",
        labelLat = -22.9635,
        labelLon = -43.2165,
        points = listOf(-22.9567 to -43.1994, -22.9635 to -43.2165, -22.9755 to -43.2280)
    ),
    NamedRoadVector(
        name = "Av. Epitácio Pessoa",
        labelLat = -22.9740,
        labelLon = -43.2020,
        points = listOf(-22.9655 to -43.2045, -22.9740 to -43.2020, -22.9830 to -43.2065)
    ),
    NamedRoadVector(
        name = "Av. Vieira Souto",
        labelLat = -22.9862,
        labelLon = -43.2045,
        points = listOf(-22.9862 to -43.1930, -22.9862 to -43.2045, -22.9875 to -43.2165)
    ),
    NamedRoadVector(
        name = "Rua Visconde de Pirajá",
        labelLat = -22.9840,
        labelLon = -43.2055,
        points = listOf(-22.9842 to -43.1945, -22.9840 to -43.2055, -22.9850 to -43.2155)
    ),
    NamedRoadVector(
        name = "Autoestrada Lagoa-Barra",
        labelLat = -22.9910,
        labelLon = -43.2650,
        isExpressway = true,
        points = listOf(-22.9790 to -43.2310, -22.9910 to -43.2650, -23.0010 to -43.3150)
    ),

    // --- ZONA OESTE (BARRA, JACAREPAGUÁ, BANGU, CAMPO GRANDE) ---
    NamedRoadVector(
        name = "Av. das Américas",
        labelLat = -23.0005,
        labelLon = -43.3650,
        isExpressway = true,
        points = listOf(
            -23.0010 to -43.3150, // Jardim Oceânico
            -23.0005 to -43.3650, // Alvorada / Barra
            -23.0015 to -43.4150, // Riocentro / Recreio
            -23.0120 to -43.4650  // Recreio
        )
    ),
    NamedRoadVector(
        name = "Av. Ayrton Senna",
        labelLat = -22.9850,
        labelLon = -43.3650,
        isExpressway = true,
        points = listOf(
            -23.0090 to -43.3655,
            -22.9850 to -43.3650,
            -22.9620 to -43.3610
        )
    ),
    NamedRoadVector(
        name = "Estr. dos Bandeirantes",
        labelLat = -22.9580,
        labelLon = -43.3880,
        points = listOf(
            -22.9250 to -43.3720, // Taquara
            -22.9580 to -43.3880, // Curicica / Camorim
            -23.0050 to -43.4350  // Recreio
        )
    ),
    NamedRoadVector(
        name = "Av. Santa Cruz",
        labelLat = -22.8780,
        labelLon = -43.4650,
        points = listOf(
            -22.8750 to -43.4280, // Realengo
            -22.8780 to -43.4650, // Bangu
            -22.9020 to -43.5580  // Campo Grande
        )
    )
)

private val ALL_RIO_NEIGHBORHOODS: List<Triple<String, Double, Double>> = listOf(
    // Zona Norte & Del Castilho
    Triple("DEL CASTILHO", -22.8792, -43.2718),
    Triple("MARIA DA GRAÇA", -22.8835, -43.2625),
    Triple("INHAÚMA", -22.8715, -43.2815),
    Triple("CACHAMBI", -22.8865, -43.2795),
    Triple("HIGIENÓPOLIS", -22.8735, -43.2610),
    Triple("MÉIER", -22.9020, -43.2780),
    Triple("ENGENHO DE DENTRO", -22.8965, -43.2925),
    Triple("ABOLIÇÃO", -22.8875, -43.2995),
    Triple("PIEDADE", -22.8935, -43.3065),
    Triple("CASCADURA", -22.8830, -43.3210),
    Triple("MADUREIRA", -22.8720, -43.3380),
    Triple("BONSUCESSO", -22.8640, -43.2530),
    Triple("RAMOS", -22.8510, -43.2590),
    Triple("PENHA", -22.8390, -43.2750),
    Triple("IRAJÁ", -22.8340, -43.3250),
    Triple("VICENTE DE CARVALHO", -22.8545, -43.3130),
    Triple("ILHA DO GOVERNADOR", -22.8150, -43.2090),
    Triple("SÃO CRISTÓVÃO", -22.8990, -43.2220),
    Triple("BENFICA", -22.8905, -43.2395),
    Triple("TIJUCA", -22.9240, -43.2320),
    Triple("MARACANÃ", -22.9125, -43.2300),
    Triple("VILA ISABEL", -22.9165, -43.2465),
    Triple("GRAJAÚ", -22.9230, -43.2630),
    // Centro & Zona Sul
    Triple("CENTRO", -22.9035, -43.1820),
    Triple("FLAMENGO", -22.9320, -43.1760),
    Triple("LARANJEIRAS", -22.9360, -43.1920),
    Triple("BOTAFOGO", -22.9510, -43.1860),
    Triple("HUMAITÁ", -22.9560, -43.1995),
    Triple("URCA", -22.9485, -43.1665),
    Triple("COPACABANA", -22.9700, -43.1860),
    Triple("IPANEMA", -22.9840, -43.2040),
    Triple("LEBLON", -22.9845, -43.2215),
    Triple("LAGOA", -22.9710, -43.2105),
    Triple("JARDIM BOTÂNICO", -22.9650, -43.2230),
    Triple("GÁVEA", -22.9790, -43.2330),
    Triple("SÃO CONRADO", -22.9935, -43.2680),
    // Zona Oeste
    Triple("BARRA DA TIJUCA", -22.9995, -43.3550),
    Triple("RECREIO", -23.0150, -43.4600),
    Triple("JACAREPAGUÁ", -22.9520, -43.3580),
    Triple("FREGUESIA", -22.9380, -43.3410),
    Triple("TAQUARA", -22.9230, -43.3710),
    Triple("REALENGO", -22.8760, -43.4310),
    Triple("BANGU", -22.8750, -43.4660),
    Triple("CAMPO GRANDE", -22.9020, -43.5590)
)

/**
 * Motor Cartográfico Híbrido + Navegador Automotivo "Modo Waze Offline" 100% Kotlin/Compose:
 * 1. Projeção Web Mercator com Escala DPI Nativa (256.dp por tile + FilterQuality.High) para exibir nomes de todas as ruas com nitidez.
 * 2. Salvamento da Cidade do Rio de Janeiro Inteira + Del Castilho + Corredores de Rotas dos Pins em cache persistente (`osm_tiles_v4`).
 * 3. Cravação de Pins Estratégicos direto no mapa (segurando o dedo em qualquer rua ou pelo botão "📌 Cravar Pin").
 * 4. Modo "Waze / Dirigir Offline" com Velocímetro Digital km/h, ETA automotivo real, seta de manobra em tempo real e sugestão de corredor viário.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@Composable
fun OfflineMapScreen(
    state: MainUiState,
    viewModel: MainViewModel,
    onOpenAddPoiDialog: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tileDownloadDispatcher = remember { Dispatchers.IO.limitedParallelism(4) }
    val tileDownloadScope = remember { CoroutineScope(SupervisorJob() + tileDownloadDispatcher) }
    val inFlightTiles = remember { ConcurrentHashMap.newKeySet<String>() }

    DisposableEffect(Unit) {
        onDispose {
            tileDownloadScope.cancel()
        }
    }

    val crisisMode = state.crisisMode
    val navList = state.poiNavigationList

    var selectedPoiId by remember { mutableStateOf<Long?>(null) }
    val focusedPoi = navList.find { it.poiId == selectedPoiId } ?: navList.firstOrNull()
    val focusedPoiEntity = state.pois.find { it.id == focusedPoi?.poiId }

    var mapCenterLat by remember { mutableDoubleStateOf(state.currentLat) }
    var mapCenterLon by remember { mutableDoubleStateOf(state.currentLon) }
    var mapZoomFloat by remember { mutableFloatStateOf(16.0f) }
    val mapZoomInt = mapZoomFloat.toInt().coerceIn(10, 19)

    var isMapFullscreen by remember { mutableStateOf(false) }
    var showBottomPoisInFullscreen by remember { mutableStateOf(true) }
    var autoFollowUser by remember { mutableStateOf(true) }
    var showStreetTiles by remember { mutableStateOf(true) }
    var isDrivingWazeMode by remember { mutableStateOf(false) }
    var currentStreetAddress by remember { mutableStateOf("Localizando rua/avenida...") }

    // Estados para Cravar Pin Estratégico direto pelo Mapa (Toque Longo ou Mira)
    var pendingPinCoords by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var pendingPinResolvedAddress by remember { mutableStateOf("") }

    // Estados para o Gerenciador de Download da Cidade Completa Offline
    var showOfflineDownloadModal by remember { mutableStateOf(false) }
    var downloadProgressCurrent by remember { mutableIntStateOf(0) }
    var downloadProgressTotal by remember { mutableIntStateOf(0) }
    var activeDownloadJob by remember { mutableStateOf<Job?>(null) }

    // Cache em memória, versão de renderização reativa e contador de blocos em disco
    val tileBitmapCache = remember { mutableStateMapOf<String, ImageBitmap>() }
    var tileRenderVersion by remember { mutableIntStateOf(0) }
    var cachedTilesCount by remember {
        mutableIntStateOf(countCachedTilesOnDisk(context))
    }
    var isPreloadingTiles by remember { mutableStateOf(false) }
    var preloadStatusText by remember { mutableStateOf<String?>(null) }
    var showConfirmClearPois by remember { mutableStateOf(false) }

    // Calcula em tempo real a orientação estilo Waze (manobra, corredor viário e ETA de carro)
    val drivingGuidance = remember(
        state.currentLat,
        state.currentLon,
        state.currentSpeedKmh,
        state.currentHeadingDegrees,
        focusedPoi,
        focusedPoiEntity
    ) {
        if (focusedPoi != null && focusedPoiEntity != null) {
            computeDrivingManeuverGuidance(
                userLat = state.currentLat,
                userLon = state.currentLon,
                userSpeedKmh = state.currentSpeedKmh,
                userHeadingDeg = state.currentHeadingDegrees,
                poiNav = focusedPoi,
                poiEntity = focusedPoiEntity
            )
        } else null
    }

    // Limpa na inicialização qualquer tile antigo corrompido
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            purgePoisonedLegacyTiles(context)
        }
        cachedTilesCount = countCachedTilesOnDisk(context)
        tileRenderVersion++
    }

    // Reatualiza a contagem e renderização de blocos de mapa sempre que um Backup é criado/restaurado ou os Pins mudam
    LaunchedEffect(state.availableBackups, state.pois.size, state.backupStatusMessage) {
        val diskCount = withContext(Dispatchers.IO) { countCachedTilesOnDisk(context) }
        if (diskCount != cachedTilesCount) {
            cachedTilesCount = diskCount
            tileRenderVersion++
        }
    }

    // Quando o GPS atualiza a posição e o modo Auto-Seguir (ou Modo Waze) está ativo, move o centro do mapa junto com o veículo
    LaunchedEffect(state.currentLat, state.currentLon, state.gpsFixCount) {
        if (autoFollowUser || isDrivingWazeMode) {
            mapCenterLat = state.currentLat
            mapCenterLon = state.currentLon
        }
    }

    // Atualiza o nome da rua/avenida atual conforme o usuário se move
    LaunchedEffect(
        (state.currentLat * 1000).roundToInt(),
        (state.currentLon * 1000).roundToInt(),
        state.gpsFixCount
    ) {
        val resolved = resolveStreetNameOfflineOrOnline(context, state.currentLat, state.currentLon)
        if (resolved.isNotBlank()) {
            currentStreetAddress = resolved
        }
    }

    // Função unificada para abrir o modal de Pin Estratégico em qualquer coordenada
    val openPinDialogAt: (Double, Double) -> Unit = { targetLat, targetLon ->
        pendingPinCoords = targetLat to targetLon
        pendingPinResolvedAddress = resolveNearestVectorStreetAndNeighborhood(targetLat, targetLon)
        scope.launch {
            val addr = resolveStreetNameOfflineOrOnline(context, targetLat, targetLon)
            if (addr.isNotBlank()) {
                pendingPinResolvedAddress = addr
            }
        }
    }

    // Função para iniciar o download de um pacote offline (Bairro, Corredor de Rota ou Cidade do Rio Inteira)
    val startOfflinePackageDownload: (OfflineDownloadScope) -> Unit = { scopeOption ->
        activeDownloadJob?.cancel()
        activeDownloadJob = scope.launch {
            isPreloadingTiles = true
            downloadProgressCurrent = 0
            downloadProgressTotal = 1
            preloadStatusText = when (scopeOption) {
                OfflineDownloadScope.ENTIRE_RIO_CITY_PLUS_PINS -> "Preparando malha da Cidade do Rio + Del Castilho + Pins..."
                OfflineDownloadScope.CORRIDOR_ROUTES_HD -> "Calculando corredor de ruas até os Pins..."
                OfflineDownloadScope.CURRENT_NEIGHBORHOOD_HD -> "Baixando ruas detalhadas do bairro..."
            }
            val downloaded = downloadOfflineMapPackage(
                context = context,
                packageScope = scopeOption,
                centerLat = mapCenterLat,
                centerLon = mapCenterLon,
                userLat = state.currentLat,
                userLon = state.currentLon,
                zoom = mapZoomInt,
                pois = state.pois,
                cache = tileBitmapCache,
                onProgress = { done, total ->
                    downloadProgressCurrent = done
                    downloadProgressTotal = total.coerceAtLeast(1)
                    val pct = ((done.toFloat() / total.coerceAtLeast(1)) * 100).roundToInt()
                    preloadStatusText = "Baixando mapa: $done/$total blocos ($pct%)"
                    tileRenderVersion++
                }
            )
            cachedTilesCount = withContext(Dispatchers.IO) { countCachedTilesOnDisk(context) }
            tileRenderVersion++
            if (downloaded > 0) {
                viewModel.triggerAutoBackupAfterMapDownload()
            }
            preloadStatusText = if (downloaded > 0) {
                "✓ Cidade/Região salva no celular + Backup ZIP! ($cachedTilesCount blocos)"
            } else {
                "✓ Todos os blocos da região já estão salvos ($cachedTilesCount blocos)"
            }
            isPreloadingTiles = false
        }
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
        modifier = modifier
            .fillMaxSize()
            .testTag("offline_map_screen")
    ) {
        // --- 1. PAINEL BÚSSOLA, MODO WAZE AUTOMOTIVO, RUA ATUAL & CONTROLES GPS ---
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (crisisMode) Color.Black else Color(0xFF111827)
                ),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(
                    1.5.dp,
                    when {
                        isDrivingWazeMode -> Color(0xFFA855F7)
                        state.isLiveTracking -> Color(0xFF10B981)
                        crisisMode -> MaterialTheme.colorScheme.primary
                        else -> Color(0xFF334155)
                    }
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    // Linha de Título + Botão Tela Cheia
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (isDrivingWazeMode) Icons.Default.DirectionsCar else Icons.Default.Explore,
                                    contentDescription = null,
                                    tint = if (isDrivingWazeMode) Color(0xFFD8B4FE) else Color(0xFFFBBF24),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isDrivingWazeMode) "Modo Waze Offline (Dirigindo)" else "Navegação & Mapa Tático Offline",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 15.sp,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "📍 $currentStreetAddress",
                                fontSize = 12.sp,
                                color = Color(0xFFFDE047),
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = state.gpsProviderInfo,
                                fontSize = 11.sp,
                                color = if (state.isLiveTracking) Color(0xFF34D399) else Color(0xFF38BDF8),
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Button(
                            onClick = { isMapFullscreen = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF0284C7),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("btn_open_fullscreen_map")
                        ) {
                            Icon(
                                Icons.Default.Fullscreen,
                                contentDescription = "Abrir Mapa em Tela Cheia",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Tela Cheia", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Botões de Modo Waze (Dirigir), Rastreio Vivo e GPS Pulso (1x)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = {
                                isDrivingWazeMode = !isDrivingWazeMode
                                if (isDrivingWazeMode) {
                                    autoFollowUser = true
                                    if (!state.isLiveTracking) {
                                        viewModel.toggleLiveGpsTracking()
                                    }
                                    mapCenterLat = state.currentLat
                                    mapCenterLon = state.currentLon
                                    if (mapZoomFloat < 16.0f) mapZoomFloat = 16.5f
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isDrivingWazeMode) Color(0xFF9333EA) else Color(0xFF1E293B),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                            modifier = Modifier
                                .weight(1.15f)
                                .testTag("btn_waze_driving_mode")
                        ) {
                            Icon(
                                Icons.Default.DirectionsCar,
                                contentDescription = null,
                                tint = if (isDrivingWazeMode) Color.White else Color(0xFFD8B4FE),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isDrivingWazeMode) "Waze: ATIVO" else "Modo Waze",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Button(
                            onClick = {
                                autoFollowUser = true
                                viewModel.toggleLiveGpsTracking()
                                mapCenterLat = state.currentLat
                                mapCenterLon = state.currentLon
                                if (mapZoomFloat < 16.0f) mapZoomFloat = 16.5f
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (state.isLiveTracking) Color(0xFF059669) else Color(0xFF1E293B),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                            modifier = Modifier
                                .weight(1.1f)
                                .testTag("btn_live_tracking_toggle")
                        ) {
                            Icon(
                                if (state.isLiveTracking) Icons.Default.DirectionsRun else Icons.Default.LocationSearching,
                                contentDescription = null,
                                tint = if (state.isLiveTracking) Color.White else Color(0xFF34D399),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (state.isLiveTracking) "GPS Vivo: ON" else "GPS Vivo",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Button(
                            onClick = {
                                autoFollowUser = true
                                viewModel.triggerSingleShotGpsPulse()
                                mapCenterLat = state.currentLat
                                mapCenterLon = state.currentLon
                                if (mapZoomFloat < 16.0f) mapZoomFloat = 16.5f
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFF59E0B),
                                contentColor = Color(0xFF090D16)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                            modifier = Modifier
                                .weight(0.95f)
                                .testTag("btn_gps_pulse_map")
                        ) {
                            Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Pulso 1x",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Atalhos rápidos de Região & Cravar Pin Estratégico
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        item {
                            Surface(
                                color = Color(0xFF1E293B),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color(0xFFFBBF24)),
                                modifier = Modifier.clickable {
                                    openPinDialogAt(mapCenterLat, mapCenterLon)
                                }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                ) {
                                    Icon(Icons.Default.PushPin, contentDescription = null, tint = Color(0xFFFBBF24), modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("📌 Cravar Pin no Mapa", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFFBBF24))
                                }
                            }
                        }
                        item {
                            Surface(
                                color = Color(0xFF0F2942),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color(0xFF38BDF8)),
                                modifier = Modifier.clickable {
                                    showOfflineDownloadModal = true
                                }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                ) {
                                    Icon(Icons.Default.DownloadForOffline, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("⬇ Salvar Cidade do Rio", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                                }
                            }
                        }
                        item {
                            Surface(
                                color = Color(0xFF1E293B),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color(0xFF34D399)),
                                modifier = Modifier.clickable {
                                    autoFollowUser = false
                                    mapCenterLat = DEL_CASTILHO_LAT
                                    mapCenterLon = DEL_CASTILHO_LON
                                    mapZoomFloat = 16.0f
                                }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                ) {
                                    Icon(Icons.Default.Place, contentDescription = null, tint = Color(0xFF34D399), modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Ver Del Castilho", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF34D399))
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Barra de Telemetria de Movimentação (Velocidade, Rumo, Pontos da Trilha)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF090D16), RoundedCornerShape(8.dp))
                            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        TelemetryItem(
                            label = "VELOCIDADE",
                            value = String.format(Locale.US, "%.1f km/h", state.currentSpeedKmh),
                            valueColor = Color(0xFF38BDF8)
                        )
                        TelemetryItem(
                            label = "RUMO ATUAL",
                            value = "${state.currentHeadingDegrees.toInt()}°",
                            valueColor = Color(0xFFFBBF24)
                        )
                        TelemetryItem(
                            label = "BLOCOS SALVOS",
                            value = "$cachedTilesCount",
                            valueColor = Color(0xFF34D399)
                        )
                        TextButton(
                            onClick = { viewModel.clearMovementTrail() },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text("Limpar Trilha", fontSize = 10.sp, color = Color(0xFFF87171), fontWeight = FontWeight.Bold)
                        }
                    }

                    // --- PAINEL DE DIREÇÃO WAZE OU BÚSSOLA TÁTICA PARA O PIN SELECIONADO ---
                    if (focusedPoi != null && drivingGuidance != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        WazeGuidanceBanner(
                            focusedPoi = focusedPoi,
                            guidance = drivingGuidance,
                            isDrivingWazeMode = isDrivingWazeMode,
                            onToggleWazeMode = {
                                isDrivingWazeMode = !isDrivingWazeMode
                                if (isDrivingWazeMode) {
                                    autoFollowUser = true
                                    if (!state.isLiveTracking) viewModel.toggleLiveGpsTracking()
                                }
                            }
                        )
                    }
                }
            }
        }

        // --- 2. MAPA INTERATIVO COM NOMES DE RUAS HD, TOQUE LONGO P/ PIN & MODO WAZE ---
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF070B14)
                ),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(
                    1.5.dp,
                    when {
                        isDrivingWazeMode -> Color(0xFFA855F7)
                        state.isLiveTracking -> Color(0xFF10B981)
                        else -> Color(0xFF0284C7)
                    }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(395.dp)
            ) {
                MapViewportBox(
                    context = context,
                    state = state,
                    mapCenterLat = mapCenterLat,
                    mapCenterLon = mapCenterLon,
                    mapZoomFloat = mapZoomFloat,
                    autoFollowUser = autoFollowUser,
                    showStreetTiles = showStreetTiles,
                    isDrivingWazeMode = isDrivingWazeMode,
                    drivingGuidance = drivingGuidance,
                    isFullscreen = false,
                    currentStreetAddress = currentStreetAddress,
                    focusedPoiEntity = focusedPoiEntity,
                    tileBitmapCache = tileBitmapCache,
                    tileRenderVersion = tileRenderVersion,
                    tileDownloadScope = tileDownloadScope,
                    inFlightTiles = inFlightTiles,
                    cachedTilesCount = cachedTilesCount,
                    isPreloadingTiles = isPreloadingTiles,
                    preloadStatusText = preloadStatusText,
                    downloadProgressCurrent = downloadProgressCurrent,
                    downloadProgressTotal = downloadProgressTotal,
                    onTileLoaded = { newlySavedToDisk ->
                        tileRenderVersion++
                        if (newlySavedToDisk) {
                            cachedTilesCount++
                            viewModel.triggerAutoBackupAfterMapDownload()
                        }
                    },
                    onTransformMap = { dLat, dLon, zoomFactor ->
                        if (abs(dLat) > 1e-7 || abs(dLon) > 1e-7) {
                            autoFollowUser = false
                            mapCenterLat = (mapCenterLat + dLat).coerceIn(-85.0, 85.0)
                            mapCenterLon = (mapCenterLon + dLon).coerceIn(-180.0, 180.0)
                        }
                        if (zoomFactor != 1.0f) {
                            val deltaZ = (ln(zoomFactor.toDouble()) / ln(2.0)).toFloat()
                            mapZoomFloat = (mapZoomFloat + deltaZ).coerceIn(9.0f, 19.2f)
                        }
                    },
                    onLongPressMap = { pressedLat, pressedLon ->
                        openPinDialogAt(pressedLat, pressedLon)
                    },
                    onDropPinAtCenter = {
                        openPinDialogAt(mapCenterLat, mapCenterLon)
                    },
                    onZoomChange = { newZoom -> mapZoomFloat = newZoom.coerceIn(9.0f, 19.2f) },
                    onCenterUser = {
                        autoFollowUser = true
                        mapCenterLat = state.currentLat
                        mapCenterLon = state.currentLon
                        if (mapZoomFloat < 16.0f) mapZoomFloat = 16.5f
                    },
                    onToggleStreetTiles = { showStreetTiles = !showStreetTiles },
                    onToggleFullscreen = { isMapFullscreen = true },
                    onOpenOfflineDownloadModal = { showOfflineDownloadModal = true }
                )
            }
        }

        // --- 3. CABEÇALHO DA LISTA DE PINS ESTRATÉGICOS & PONTOS DE APOIO ---
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Pins Estratégicos & Destinos (${navList.size}):",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                    Text(
                        text = "Dica: Segure o dedo em qualquer rua do mapa para cravar um Pin lá!",
                        fontSize = 10.sp,
                        color = Color(0xFFFBBF24),
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = { openPinDialogAt(mapCenterLat, mapCenterLon) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Icon(Icons.Default.AddLocationAlt, contentDescription = null, tint = Color(0xFFFBBF24), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Novo Pin", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFBBF24))
                    }
                    if (navList.isNotEmpty()) {
                        TextButton(
                            onClick = { showConfirmClearPois = true },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Limpar Pontos", tint = Color(0xFFF87171), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("Limpar", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF87171))
                        }
                    }
                }
            }
        }

        // --- 4. LISTA DE PINS ESTRATÉGICOS COM BOTÃO "IR (WAZE)" ---
        if (navList.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFF334155)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Text(
                            text = "Nenhum Pin Estratégico ou Ponto de Encontro cadastrado.",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Segure o dedo em qualquer ponto do mapa ou toque abaixo para cravar seu primeiro Pin estratégico.",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = { openPinDialogAt(mapCenterLat, mapCenterLon) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFF59E0B),
                                contentColor = Color(0xFF090D16)
                            )
                        ) {
                            Icon(Icons.Default.PushPin, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Cravar Pin Estratégico", fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
            }
        } else {
            items(navList, key = { it.poiId }) { nav ->
                val isSelected = nav.poiId == (selectedPoiId ?: navList.firstOrNull()?.poiId)
                PoiOfflineCard(
                    nav = nav,
                    isSelected = isSelected,
                    isDrivingToThis = isSelected && isDrivingWazeMode,
                    crisisMode = crisisMode,
                    currentSpeedKmh = state.currentSpeedKmh,
                    onClick = {
                        selectedPoiId = nav.poiId
                        val targetPoi = state.pois.find { it.id == nav.poiId }
                        if (targetPoi != null) {
                            autoFollowUser = false
                            mapCenterLat = targetPoi.latitude
                            mapCenterLon = targetPoi.longitude
                            if (mapZoomFloat < 16.0f) mapZoomFloat = 16.5f
                        }
                    },
                    onStartWazeNavigation = {
                        selectedPoiId = nav.poiId
                        isDrivingWazeMode = true
                        autoFollowUser = true
                        if (!state.isLiveTracking) {
                            viewModel.toggleLiveGpsTracking()
                        }
                        mapCenterLat = state.currentLat
                        mapCenterLon = state.currentLon
                        if (mapZoomFloat < 16.0f) mapZoomFloat = 16.5f
                    },
                    onDelete = { viewModel.deletePoi(nav.poiId) }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(76.dp))
        }
    }

    // =========================================================================
    // MODAL RÁPIDO PARA CRAVAR PIN ESTRATÉGICO (TOQUE LONGO OU BOTÃO DE MIRA)
    // =========================================================================
    pendingPinCoords?.let { (pinLat, pinLon) ->
        QuickStrategicPinDialog(
            initialLat = pinLat,
            initialLon = pinLon,
            resolvedStreetAddress = pendingPinResolvedAddress,
            userLat = state.currentLat,
            userLon = state.currentLon,
            onDismiss = { pendingPinCoords = null },
            onConfirmPin = { title, type, finalLat, finalLon, address, phone, startWazeImmediately ->
                viewModel.addNewPoi(title, type, finalLat, finalLon, address, phone)
                pendingPinCoords = null
                if (startWazeImmediately) {
                    isDrivingWazeMode = true
                    autoFollowUser = true
                    if (!state.isLiveTracking) {
                        viewModel.toggleLiveGpsTracking()
                    }
                    mapCenterLat = state.currentLat
                    mapCenterLon = state.currentLon
                } else {
                    autoFollowUser = false
                    mapCenterLat = finalLat
                    mapCenterLon = finalLon
                }
            }
        )
    }

    // =========================================================================
    // MODAL DE DOWNLOAD DA CIDADE DO RIO DE JANEIRO COMPLETA & CORREDORES WAZE
    // =========================================================================
    if (showOfflineDownloadModal) {
        OfflineCityDownloadDialog(
            cachedTilesCount = cachedTilesCount,
            poisCount = state.pois.size,
            isDownloading = isPreloadingTiles,
            progressCurrent = downloadProgressCurrent,
            progressTotal = downloadProgressTotal,
            statusText = preloadStatusText,
            onDismiss = { showOfflineDownloadModal = false },
            onSelectPackage = { chosenScope ->
                startOfflinePackageDownload(chosenScope)
            },
            onCancelDownload = {
                activeDownloadJob?.cancel()
                isPreloadingTiles = false
                preloadStatusText = "Download pausado ($cachedTilesCount blocos salvos)"
            }
        )
    }

    if (showConfirmClearPois) {
        ConfirmClearDialog(
            title = "Limpar Todos os Pontos do Mapa?",
            message = "Todos os pins estratégicos, pontos de encontro, hospitais e abrigos serão removidos para que você cadastre os seus próprios locais.",
            confirmButtonText = "Apagar Pontos",
            onDismiss = { showConfirmClearPois = false },
            onConfirm = { viewModel.clearAllPois() }
        )
    }

    // =========================================================================
    // MODAL DE MAPA EM TELA CHEIA (100% DA TELA DO CELULAR COM MODO WAZE)
    // =========================================================================
    if (isMapFullscreen) {
        Dialog(
            onDismissRequest = { isMapFullscreen = false },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            Surface(
                color = Color(0xFF050811),
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("fullscreen_map_dialog")
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    MapViewportBox(
                        context = context,
                        state = state,
                        mapCenterLat = mapCenterLat,
                        mapCenterLon = mapCenterLon,
                        mapZoomFloat = mapZoomFloat,
                        autoFollowUser = autoFollowUser,
                        showStreetTiles = showStreetTiles,
                        isDrivingWazeMode = isDrivingWazeMode,
                        drivingGuidance = drivingGuidance,
                        isFullscreen = true,
                        currentStreetAddress = currentStreetAddress,
                        focusedPoiEntity = focusedPoiEntity,
                        tileBitmapCache = tileBitmapCache,
                        tileRenderVersion = tileRenderVersion,
                        tileDownloadScope = tileDownloadScope,
                        inFlightTiles = inFlightTiles,
                        cachedTilesCount = cachedTilesCount,
                        isPreloadingTiles = isPreloadingTiles,
                        preloadStatusText = preloadStatusText,
                        downloadProgressCurrent = downloadProgressCurrent,
                        downloadProgressTotal = downloadProgressTotal,
                        onTileLoaded = { newlySavedToDisk ->
                            tileRenderVersion++
                            if (newlySavedToDisk) {
                                cachedTilesCount++
                                viewModel.triggerAutoBackupAfterMapDownload()
                            }
                        },
                        onTransformMap = { dLat, dLon, zoomFactor ->
                            if (abs(dLat) > 1e-7 || abs(dLon) > 1e-7) {
                                autoFollowUser = false
                                mapCenterLat = (mapCenterLat + dLat).coerceIn(-85.0, 85.0)
                                mapCenterLon = (mapCenterLon + dLon).coerceIn(-180.0, 180.0)
                            }
                            if (zoomFactor != 1.0f) {
                                val deltaZ = (ln(zoomFactor.toDouble()) / ln(2.0)).toFloat()
                                mapZoomFloat = (mapZoomFloat + deltaZ).coerceIn(9.0f, 19.2f)
                            }
                        },
                        onLongPressMap = { pressedLat, pressedLon ->
                            openPinDialogAt(pressedLat, pressedLon)
                        },
                        onDropPinAtCenter = {
                            openPinDialogAt(mapCenterLat, mapCenterLon)
                        },
                        onZoomChange = { newZoom -> mapZoomFloat = newZoom.coerceIn(9.0f, 19.2f) },
                        onCenterUser = {
                            autoFollowUser = true
                            mapCenterLat = state.currentLat
                            mapCenterLon = state.currentLon
                            if (mapZoomFloat < 16.0f) mapZoomFloat = 16.8f
                        },
                        onToggleStreetTiles = { showStreetTiles = !showStreetTiles },
                        onToggleFullscreen = { isMapFullscreen = false },
                        onOpenOfflineDownloadModal = { showOfflineDownloadModal = true }
                    )

                    // HUD Tático / Waze Superior em Tela Cheia
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isDrivingWazeMode) Color(0xFF130924).copy(alpha = 0.94f)
                            else Color(0xFF090D16).copy(alpha = 0.92f)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(
                            1.5.dp,
                            if (isDrivingWazeMode) Color(0xFFA855F7) else Color(0xFF334155)
                        ),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 34.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f).padding(end = 6.dp)
                                ) {
                                    if (focusedPoi != null && drivingGuidance != null) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .size(38.dp)
                                                .background(
                                                    if (drivingGuidance.hasArrived) Color(0xFF059669)
                                                    else if (isDrivingWazeMode) Color(0xFF7E22CE)
                                                    else Color(0xFF1E293B),
                                                    RoundedCornerShape(8.dp)
                                                )
                                        ) {
                                            Icon(
                                                Icons.Default.Navigation,
                                                contentDescription = null,
                                                tint = Color(0xFFFDE047),
                                                modifier = Modifier
                                                    .size(22.dp)
                                                    .rotate(
                                                        if (isDrivingWazeMode) drivingGuidance.turnDeltaDegrees
                                                        else focusedPoi.bearingDegrees.toFloat()
                                                    )
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (isDrivingWazeMode && drivingGuidance != null) {
                                                "${drivingGuidance.instructionTitle} • ${focusedPoi?.distanceFormatted ?: ""}"
                                            } else {
                                                "📍 $currentStreetAddress"
                                            },
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 13.sp,
                                            color = Color(0xFFFDE047),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = if (isDrivingWazeMode && drivingGuidance != null) {
                                                "${drivingGuidance.suggestedCorridor} • Chegada ${drivingGuidance.arrivalTimeFormatted} (~${drivingGuidance.etaMinutes} min)"
                                            } else if (focusedPoi != null) {
                                                "${focusedPoi.poiTitle} • ${focusedPoi.distanceFormatted} • $cachedTilesCount blocos"
                                            } else {
                                                "Zoom ${String.format(Locale.US, "%.1f", mapZoomFloat)}x • $cachedTilesCount blocos salvos"
                                            },
                                            fontSize = 11.sp,
                                            color = Color(0xFF38BDF8),
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                Button(
                                    onClick = { isMapFullscreen = false },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFDC2626),
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.FullscreenExit, contentDescription = "Fechar Tela Cheia", modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Fechar", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Barra Rápida em Tela Cheia: Modo Waze + GPS Vivo + Pin Aqui + Ocultar Lista
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Button(
                                    onClick = {
                                        isDrivingWazeMode = !isDrivingWazeMode
                                        if (isDrivingWazeMode) {
                                            autoFollowUser = true
                                            if (!state.isLiveTracking) viewModel.toggleLiveGpsTracking()
                                            mapCenterLat = state.currentLat
                                            mapCenterLon = state.currentLon
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isDrivingWazeMode) Color(0xFF9333EA) else Color(0xFF1E293B),
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Icon(Icons.Default.DirectionsCar, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (isDrivingWazeMode) "Waze: ON" else "Waze",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }

                                Button(
                                    onClick = {
                                        autoFollowUser = true
                                        viewModel.toggleLiveGpsTracking()
                                        mapCenterLat = state.currentLat
                                        mapCenterLon = state.currentLon
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (state.isLiveTracking) Color(0xFF059669) else Color(0xFF1E293B),
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Icon(
                                        if (state.isLiveTracking) Icons.Default.DirectionsRun else Icons.Default.LocationSearching,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (state.isLiveTracking) "GPS ON" else "Rastrear",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Button(
                                    onClick = { openPinDialogAt(mapCenterLat, mapCenterLon) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFF59E0B),
                                        contentColor = Color(0xFF090D16)
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Icon(Icons.Default.PushPin, contentDescription = null, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("Pin Aqui", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                                }

                                if (navList.isNotEmpty()) {
                                    OutlinedButton(
                                        onClick = { showBottomPoisInFullscreen = !showBottomPoisInFullscreen },
                                        border = BorderStroke(1.dp, Color(0xFFFBBF24)),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Text(
                                            text = if (showBottomPoisInFullscreen) "Ocultar" else "Pins",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFFBBF24)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Carrossel Inferior de POIs em Tela Cheia
                    if (showBottomPoisInFullscreen && navList.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .padding(bottom = 26.dp)
                        ) {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(navList, key = { it.poiId }) { nav ->
                                    val isSel = nav.poiId == (selectedPoiId ?: navList.firstOrNull()?.poiId)
                                    Surface(
                                        color = if (isSel) Color(0xFF1E1035) else Color(0xFF090D16).copy(alpha = 0.92f),
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(
                                            if (isSel) 2.dp else 1.dp,
                                            if (isSel) Color(0xFFFBBF24) else Color(0xFF334155)
                                        ),
                                        modifier = Modifier
                                            .width(235.dp)
                                            .clickable {
                                                selectedPoiId = nav.poiId
                                                val target = state.pois.find { it.id == nav.poiId }
                                                if (target != null && !isDrivingWazeMode) {
                                                    autoFollowUser = false
                                                    mapCenterLat = target.latitude
                                                    mapCenterLon = target.longitude
                                                    if (mapZoomFloat < 16.5f) mapZoomFloat = 17.0f
                                                }
                                            }
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(
                                                    text = nav.poiTitle,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp,
                                                    color = Color.White,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = nav.distanceFormatted,
                                                    fontWeight = FontWeight.Black,
                                                    fontSize = 12.sp,
                                                    color = Color(0xFFFBBF24)
                                                )
                                            }
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                                            ) {
                                                Text(
                                                    text = "${nav.cardinalCode} (${nav.bearingDegrees.toInt()}°) • ${nav.poiType}",
                                                    fontSize = 10.sp,
                                                    color = Color(0xFF38BDF8),
                                                    maxLines = 1,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Surface(
                                                    color = Color(0xFF9333EA),
                                                    shape = RoundedCornerShape(6.dp),
                                                    modifier = Modifier.clickable {
                                                        selectedPoiId = nav.poiId
                                                        isDrivingWazeMode = true
                                                        autoFollowUser = true
                                                        if (!state.isLiveTracking) viewModel.toggleLiveGpsTracking()
                                                        mapCenterLat = state.currentLat
                                                        mapCenterLon = state.currentLon
                                                    }
                                                ) {
                                                    Text(
                                                        text = "🚗 Ir",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        color = Color.White,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WazeGuidanceBanner(
    focusedPoi: RationingAndAlertEngine.PoiNavigationInfo,
    guidance: DrivingManeuverGuidance,
    isDrivingWazeMode: Boolean,
    onToggleWazeMode: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (guidance.hasArrived) Color(0xFF064E3B)
                else if (isDrivingWazeMode) Color(0xFF1E1035)
                else Color(0xFF090D16),
                RoundedCornerShape(10.dp)
            )
            .border(
                1.5.dp,
                if (guidance.hasArrived) Color(0xFF34D399)
                else if (isDrivingWazeMode) Color(0xFFA855F7)
                else Color(0xFF1E293B),
                RoundedCornerShape(10.dp)
            )
            .padding(10.dp)
    ) {
        // Ícone de Manobra / Bússola Dinâmica
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(54.dp)
        ) {
            Canvas(modifier = Modifier.size(54.dp)) {
                drawCircle(
                    color = if (isDrivingWazeMode) Color(0xFFA855F7) else Color(0xFF334155),
                    style = Stroke(width = 2.2.dp.toPx())
                )
                drawLine(
                    color = Color(0xFFEF4444),
                    start = Offset(size.width / 2, 3.dp.toPx()),
                    end = Offset(size.width / 2, 10.dp.toPx()),
                    strokeWidth = 2.5.dp.toPx()
                )
            }
            Icon(
                if (guidance.hasArrived) Icons.Default.Flag else Icons.Default.Navigation,
                contentDescription = null,
                tint = if (guidance.hasArrived) Color(0xFF34D399) else Color(0xFFFBBF24),
                modifier = Modifier
                    .size(28.dp)
                    .rotate(
                        if (isDrivingWazeMode) guidance.turnDeltaDegrees
                        else focusedPoi.bearingDegrees.toFloat()
                    )
            )
        }

        Column(
            modifier = Modifier
                .padding(start = 10.dp, end = 6.dp)
                .weight(1f)
        ) {
            Text(
                text = if (isDrivingWazeMode) guidance.instructionTitle else "DESTINO SELECIONADO:",
                fontSize = if (isDrivingWazeMode) 12.sp else 10.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (isDrivingWazeMode) Color(0xFFFDE047) else Color(0xFF94A3B8)
            )
            Text(
                text = focusedPoi.poiTitle,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = Color.White
            )
            Text(
                text = guidance.suggestedCorridor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFD8B4FE),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val walkMinutes = (focusedPoi.distanceMeters / 75.0).roundToInt().coerceAtLeast(1)
            Text(
                text = "🚗 ~${guidance.etaMinutes} min (Chegada ${guidance.arrivalTimeFormatted}) • 🚶 ~$walkMinutes min",
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF34D399),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = focusedPoi.distanceFormatted,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                color = Color(0xFF38BDF8)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Surface(
                color = if (isDrivingWazeMode) Color(0xFF9333EA) else Color(0xFF1E293B),
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, Color(0xFFA855F7)),
                modifier = Modifier.clickable { onToggleWazeMode() }
            ) {
                Text(
                    text = if (isDrivingWazeMode) "🚗 Waze ON" else "🚗 Ir Waze",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
    }
}

@Composable
private fun TelemetryItem(
    label: String,
    value: String,
    valueColor: Color
) {
    Column(horizontalAlignment = Alignment.Start) {
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF94A3B8)
        )
        Text(
            text = value,
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold,
            color = valueColor
        )
    }
}

@Composable
private fun MapViewportBox(
    context: Context,
    state: MainUiState,
    mapCenterLat: Double,
    mapCenterLon: Double,
    mapZoomFloat: Float,
    autoFollowUser: Boolean,
    showStreetTiles: Boolean,
    isDrivingWazeMode: Boolean,
    drivingGuidance: DrivingManeuverGuidance?,
    isFullscreen: Boolean,
    currentStreetAddress: String,
    focusedPoiEntity: MapPoiEntity?,
    tileBitmapCache: MutableMap<String, ImageBitmap>,
    tileRenderVersion: Int,
    tileDownloadScope: CoroutineScope,
    inFlightTiles: MutableSet<String>,
    cachedTilesCount: Int,
    isPreloadingTiles: Boolean,
    preloadStatusText: String?,
    downloadProgressCurrent: Int,
    downloadProgressTotal: Int,
    onTileLoaded: (newlySavedToDisk: Boolean) -> Unit,
    onTransformMap: (dLat: Double, dLon: Double, zoomFactor: Float) -> Unit,
    onLongPressMap: (lat: Double, lon: Double) -> Unit,
    onDropPinAtCenter: () -> Unit,
    onZoomChange: (Float) -> Unit,
    onCenterUser: () -> Unit,
    onToggleStreetTiles: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onOpenOfflineDownloadModal: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(if (isFullscreen) RoundedCornerShape(0.dp) else RoundedCornerShape(14.dp))
    ) {
        InteractiveOfflineMapCanvas(
            context = context,
            centerLat = mapCenterLat,
            centerLon = mapCenterLon,
            zoomFloat = mapZoomFloat,
            userLat = state.currentLat,
            userLon = state.currentLon,
            userHeadingDegrees = state.currentHeadingDegrees,
            userAccuracyMeters = state.currentAccuracyMeters,
            isLiveTracking = state.isLiveTracking,
            isDrivingWazeMode = isDrivingWazeMode,
            currentStreetAddress = currentStreetAddress,
            movementTrail = state.movementTrail,
            pois = state.pois,
            focusedPoi = focusedPoiEntity,
            crisisMode = state.crisisMode,
            showStreetTiles = showStreetTiles,
            tileBitmapCache = tileBitmapCache,
            tileRenderVersion = tileRenderVersion,
            tileDownloadScope = tileDownloadScope,
            inFlightTiles = inFlightTiles,
            onTileLoaded = onTileLoaded,
            onTransformGesture = onTransformMap,
            onLongPressCoordinates = onLongPressMap
        )

        // Mira Tática Discreta no Centro do Mapa (quando o usuário arrasta para escolher onde cravar um Pin)
        if (!autoFollowUser) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(28.dp)
            ) {
                Canvas(modifier = Modifier.size(28.dp)) {
                    val c = size.width / 2f
                    drawCircle(
                        color = Color(0xFFFBBF24).copy(alpha = 0.85f),
                        radius = 8.dp.toPx(),
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                    drawLine(
                        color = Color(0xFFFBBF24),
                        start = Offset(c - 12.dp.toPx(), c),
                        end = Offset(c + 12.dp.toPx(), c),
                        strokeWidth = 1.5.dp.toPx()
                    )
                    drawLine(
                        color = Color(0xFFFBBF24),
                        start = Offset(c, c - 12.dp.toPx()),
                        end = Offset(c, c + 12.dp.toPx()),
                        strokeWidth = 1.5.dp.toPx()
                    )
                }
            }
        }

        // Barra Superior do Mapa (na visualização normal)
        if (!isFullscreen) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.85f),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(0.5.dp, Color(0xFF334155))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .background(
                                        when {
                                            isDrivingWazeMode -> Color(0xFFA855F7)
                                            state.isLiveTracking -> Color(0xFF10B981)
                                            else -> Color(0xFF38BDF8)
                                        },
                                        CircleShape
                                    )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Zoom ${String.format(Locale.US, "%.1f", mapZoomFloat)}x • $cachedTilesCount blocos",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Surface(
                            color = Color.Black.copy(alpha = 0.85f),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(0.5.dp, Color(0xFF38BDF8)),
                            modifier = Modifier.clickable { onToggleStreetTiles() }
                        ) {
                            Text(
                                text = if (showStreetTiles) "Ruas HD" else "Só Vetor OLED",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF38BDF8),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }

                        Surface(
                            color = Color(0xFF0284C7),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.clickable { onOpenOfflineDownloadModal() }
                        ) {
                            Text(
                                text = if (isPreloadingTiles) {
                                    "⬇ ${downloadProgressCurrent}/${downloadProgressTotal}"
                                } else {
                                    "⬇ Salvar Cidade"
                                },
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                // Barra de progresso visível durante download da cidade
                if (isPreloadingTiles && downloadProgressTotal > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { (downloadProgressCurrent.toFloat() / downloadProgressTotal.coerceAtLeast(1)).coerceIn(0f, 1f) },
                        color = Color(0xFF38BDF8),
                        trackColor = Color(0xFF1E293B),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp))
                    )
                }
            }
        }

        // Velocímetro Digital Estilo Waze (canto inferior esquerdo do mapa quando em Modo Waze ou Rastreio Vivo)
        if (isDrivingWazeMode || state.isLiveTracking) {
            Surface(
                color = Color(0xFF090D16).copy(alpha = 0.92f),
                shape = CircleShape,
                border = BorderStroke(2.5.dp, if (isDrivingWazeMode) Color(0xFFA855F7) else Color(0xFF10B981)),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(
                        start = 10.dp,
                        bottom = if (isFullscreen) 105.dp else 36.dp
                    )
                    .size(64.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "${state.currentSpeedKmh.roundToInt()}",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )
                    Text(
                        text = "km/h",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isDrivingWazeMode) Color(0xFFD8B4FE) else Color(0xFF34D399)
                    )
                }
            }
        }

        // Controles Flutuantes Laterais (+ / - / Cravar Pin / Centralizar / Tela Cheia)
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 10.dp)
        ) {
            SmallFloatingButton(
                icon = Icons.Default.Add,
                contentDescription = "Mais Zoom",
                onClick = { onZoomChange(mapZoomFloat + 0.85f) }
            )
            SmallFloatingButton(
                icon = Icons.Default.Remove,
                contentDescription = "Menos Zoom",
                onClick = { onZoomChange(mapZoomFloat - 0.85f) }
            )
            SmallFloatingButton(
                icon = Icons.Default.PushPin,
                contentDescription = "Cravar Pin Estratégico Aqui",
                tint = Color(0xFFFBBF24),
                onClick = onDropPinAtCenter
            )
            SmallFloatingButton(
                icon = if (autoFollowUser) Icons.Default.GpsFixed else Icons.Default.MyLocation,
                contentDescription = "Centralizar e Dar Zoom na Sua Rua",
                tint = if (autoFollowUser) Color(0xFF34D399) else Color(0xFF38BDF8),
                onClick = onCenterUser
            )
            if (isFullscreen) {
                SmallFloatingButton(
                    icon = Icons.Default.Layers,
                    contentDescription = "Alternar Camada",
                    tint = Color(0xFFFBBF24),
                    onClick = onToggleStreetTiles
                )
                SmallFloatingButton(
                    icon = Icons.Default.Download,
                    contentDescription = "Salvar Cidade Offline",
                    tint = Color(0xFF38BDF8),
                    onClick = onOpenOfflineDownloadModal
                )
            } else {
                SmallFloatingButton(
                    icon = Icons.Default.Fullscreen,
                    contentDescription = "Tela Cheia",
                    tint = Color(0xFFFBBF24),
                    onClick = onToggleFullscreen
                )
            }
        }

        // Rodapé do Mapa: Status de Download / Dica de Toque Longo para Pin e Legenda
        if (!isFullscreen) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.84f))
                    .padding(horizontal = 8.dp, vertical = 5.dp)
            ) {
                Text(
                    text = preloadStatusText ?: "Segure o dedo numa rua p/ cravar Pin • Pinça p/ Zoom",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (preloadStatusText != null) Color(0xFF38BDF8) else Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    LegendDot(color = Color(0xFFA855F7), label = "Waze")
                    LegendDot(color = Color(0xFFF59E0B), label = "Pin")
                    LegendDot(color = Color(0xFFEF4444), label = "Hosp")
                }
            }
        }
    }
}

@Composable
private fun SmallFloatingButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    tint: Color = Color.White,
    onClick: () -> Unit
) {
    Surface(
        color = Color(0xFF090D16).copy(alpha = 0.90f),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, Color(0xFF475569)),
        modifier = Modifier
            .size(38.dp)
            .clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .background(color, CircleShape)
        )
        Spacer(modifier = Modifier.width(3.dp))
        Text(text = label, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFE2E8F0))
    }
}

@Composable
private fun InteractiveOfflineMapCanvas(
    context: Context,
    centerLat: Double,
    centerLon: Double,
    zoomFloat: Float,
    userLat: Double,
    userLon: Double,
    userHeadingDegrees: Float,
    userAccuracyMeters: Float,
    isLiveTracking: Boolean,
    isDrivingWazeMode: Boolean,
    currentStreetAddress: String,
    movementTrail: List<Pair<Double, Double>>,
    pois: List<MapPoiEntity>,
    focusedPoi: MapPoiEntity?,
    crisisMode: Boolean,
    showStreetTiles: Boolean,
    tileBitmapCache: MutableMap<String, ImageBitmap>,
    tileRenderVersion: Int,
    tileDownloadScope: CoroutineScope,
    inFlightTiles: MutableSet<String>,
    onTileLoaded: (newlySavedToDisk: Boolean) -> Unit,
    onTransformGesture: (dLat: Double, dLon: Double, zoomFactor: Float) -> Unit,
    onLongPressCoordinates: (lat: Double, lon: Double) -> Unit
) {
    val currentCenterLat by rememberUpdatedState(centerLat)
    val currentCenterLon by rememberUpdatedState(centerLon)
    val currentZoomFloat by rememberUpdatedState(zoomFloat)
    val currentOnTileLoaded by rememberUpdatedState(onTileLoaded)
    val currentOnLongPress by rememberUpdatedState(onLongPressCoordinates)

    // Zoom inteiro do tile OpenStreetMap (9 a 19) e fator de escala contínuo entre níveis
    val tileZoom = zoomFloat.toInt().coerceIn(9, 19)
    val subTileScale = 2.0.pow((zoomFloat - tileZoom).toDouble())
    val n = 2.0.pow(tileZoom.toDouble())

    val clampedCenterLat = centerLat.coerceIn(-85.0511, 85.0511)
    val centerLatRad = Math.toRadians(clampedCenterLat)
    val currentCenterTileX = ((centerLon + 180.0) / 360.0 * n).toInt()
    val currentCenterTileY = ((1.0 - ln(tan(centerLatRad) + 1.0 / cos(centerLatRad)) / PI) / 2.0 * n).toInt()

    // Dispara carregamento assíncrono em fila controlada quando a grade de tiles muda
    LaunchedEffect(currentCenterTileX, currentCenterTileY, tileZoom, showStreetTiles) {
        if (!showStreetTiles) return@LaunchedEffect
        val maxTile = n.toInt() - 1

        fun requestTileAsync(z: Int, tx: Int, ty: Int) {
            val key = "$z/$tx/$ty"
            if (tileBitmapCache.containsKey(key) || !inFlightTiles.add(key)) return
            tileDownloadScope.launch {
                try {
                    val res = loadOrDownloadTileWithStatus(context, z, tx, ty)
                    if (res != null) {
                        val (bmp, newlySaved) = res
                        withContext(Dispatchers.Main) {
                            tileBitmapCache[key] = bmp
                            currentOnTileLoaded(newlySaved)
                        }
                    }
                } finally {
                    inFlightTiles.remove(key)
                }
            }
        }

        // 1º: Anel central 3x3 (prioridade máxima)
        val priorityOffsets = listOf(
            0 to 0,
            0 to -1, 0 to 1, -1 to 0, 1 to 0,
            -1 to -1, 1 to -1, -1 to 1, 1 to 1
        )
        for ((dx, dy) in priorityOffsets) {
            val tx = (currentCenterTileX + dx).coerceIn(0, maxTile)
            val ty = (currentCenterTileY + dy).coerceIn(0, maxTile)
            requestTileAsync(tileZoom, tx, ty)
        }

        // 2º: Tiles pais (zoom - 1) para fallback instantâneo
        if (tileZoom > 9) {
            val parentZ = tileZoom - 1
            val parentMax = (1 shl parentZ) - 1
            val pTx = (currentCenterTileX shr 1).coerceIn(0, parentMax)
            val pTy = (currentCenterTileY shr 1).coerceIn(0, parentMax)
            for (dx in -1..1) {
                for (dy in -1..1) {
                    requestTileAsync(parentZ, (pTx + dx).coerceIn(0, parentMax), (pTy + dy).coerceIn(0, parentMax))
                }
            }
        }

        // 3º: Anel externo 5x5 para pré-carregar as ruas vizinhas
        for (dx in -2..2) {
            for (dy in -2..2) {
                if (abs(dx) == 2 || abs(dy) == 2) {
                    val tx = (currentCenterTileX + dx).coerceIn(0, maxTile)
                    val ty = (currentCenterTileY + dy).coerceIn(0, maxTile)
                    requestTileAsync(tileZoom, tx, ty)
                }
            }
        }
    }

    val lagoaPolygon = remember {
        listOf(
            -22.9630 to -43.2170,
            -22.9660 to -43.2040,
            -22.9765 to -43.1995,
            -22.9810 to -43.2085,
            -22.9775 to -43.2185,
            -22.9685 to -43.2215
        )
    }

    val coastlineOrla = remember {
        listOf(
            -22.8050 to -43.1850, // Ilha do Governador / Baía
            -22.8600 to -43.2200, // Fundão / Manguinhos
            -22.8950 to -43.1800, // Porto / Centro
            -22.9050 to -43.1680,
            -22.9200 to -43.1700,
            -22.9420 to -43.1780,
            -22.9480 to -43.1630,
            -22.9620 to -43.1650,
            -22.9715 to -43.1825,
            -22.9865 to -43.1900,
            -22.9865 to -43.2050,
            -22.9885 to -43.2260,
            -22.9995 to -43.2650,
            -23.0050 to -43.3150, // Barra da Tijuca
            -23.0120 to -43.4100, // Reserva / Recreio
            -23.0250 to -43.4800
        )
    }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .background(if (crisisMode) Color.Black else Color(0xFF0B1526))
            // Duplo-toque para dar zoom rápido na rua + Toque Longo (Segurar o dedo) para Cravar Pin Estratégico na rua clicada!
            .pointerInput(Unit) {
                val baseTilePx = 256.dp.toPx().toDouble()
                detectTapGestures(
                    onDoubleTap = {
                        onTransformGesture(0.0, 0.0, 1.85f)
                    },
                    onLongPress = { tapOffset ->
                        val zInt = currentZoomFloat.toInt().coerceIn(9, 19)
                        val subScale = 2.0.pow((currentZoomFloat - zInt).toDouble())
                        val nTiles = 2.0.pow(zInt.toDouble())
                        val tilePx = baseTilePx * subScale

                        val cLat = currentCenterLat.coerceIn(-85.0511, 85.0511)
                        val cLon = currentCenterLon
                        val cWx = (cLon + 180.0) / 360.0 * nTiles * tilePx
                        val cLatRad = Math.toRadians(cLat)
                        val cWy = (1.0 - ln(tan(cLatRad) + 1.0 / cos(cLatRad)) / PI) / 2.0 * nTiles * tilePx

                        val targetWx = cWx + (tapOffset.x - size.width / 2.0)
                        val targetWy = cWy + (tapOffset.y - size.height / 2.0)

                        val clickedLon = (targetWx / (nTiles * tilePx)) * 360.0 - 180.0
                        val mercN = PI - 2.0 * PI * targetWy / (nTiles * tilePx)
                        val clickedLat = Math.toDegrees(atan(0.5 * (exp(mercN) - exp(-mercN))))

                        currentOnLongPress(
                            clickedLat.coerceIn(-85.0, 85.0),
                            clickedLon.coerceIn(-180.0, 180.0)
                        )
                    }
                )
            }
            // Gestor customizado que consome 100% dos eventos de arraste e pinça no mapa
            .pointerInput(Unit) {
                val baseTilePx = 256.dp.toPx().toDouble()
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val zoomChange = event.calculateZoom()
                        val pan = event.calculatePan()
                        if (zoomChange != 1f || pan != Offset.Zero) {
                            val worldScale = 2.0.pow(currentZoomFloat.toDouble()) * baseTilePx
                            val dLon = -(pan.x / worldScale) * 360.0
                            val dLat = (pan.y / worldScale) * 360.0 * cos(Math.toRadians(currentCenterLat.coerceIn(-80.0, 80.0)))
                            val safeZoom = zoomChange.coerceIn(0.65f, 1.55f)
                            onTransformGesture(dLat, dLon, safeZoom)
                            event.changes.forEach { it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
    ) {
        val renderTick = tileRenderVersion
        if (renderTick < 0) return@Canvas

        val w = size.width
        val h = size.height
        val tileSizePx = 256.dp.toPx() * subTileScale

        fun latLonToWorldPx(lat: Double, lon: Double): Pair<Double, Double> {
            val clampedLat = lat.coerceIn(-85.0511, 85.0511)
            val x = (lon + 180.0) / 360.0 * n * tileSizePx
            val latRad = Math.toRadians(clampedLat)
            val y = (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * n * tileSizePx
            return Pair(x, y)
        }

        val (centerWorldX, centerWorldY) = latLonToWorldPx(centerLat, centerLon)

        fun toScreen(lat: Double, lon: Double): Offset {
            val (wx, wy) = latLonToWorldPx(lat, lon)
            val sx = (wx - centerWorldX + w / 2.0).toFloat()
            val sy = (wy - centerWorldY + h / 2.0).toFloat()
            return Offset(sx, sy)
        }

        var loadedTilesInView = 0

        // 1. Camada de Tiles OpenStreetMap com Fallback Hierárquico (Parent/Child Tile Scaling)
        if (showStreetTiles) {
            val centerTileX = (centerWorldX / tileSizePx).toInt()
            val centerTileY = (centerWorldY / tileSizePx).toInt()
            val maxTile = n.toInt() - 1

            for (dx in -2..2) {
                for (dy in -2..2) {
                    val tx = centerTileX + dx
                    val ty = centerTileY + dy
                    if (tx in 0..maxTile && ty in 0..maxTile) {
                        val tileScreenX = (tx * tileSizePx - centerWorldX + w / 2.0).roundToInt()
                        val tileScreenY = (ty * tileSizePx - centerWorldY + h / 2.0).roundToInt()
                        val dstSizePx = IntSize(tileSizePx.roundToInt() + 2, tileSizePx.roundToInt() + 2)

                        val key = "$tileZoom/$tx/$ty"
                        val bmp = tileBitmapCache[key]
                        if (bmp != null) {
                            loadedTilesInView++
                            drawImage(
                                image = bmp,
                                dstOffset = IntOffset(tileScreenX, tileScreenY),
                                dstSize = dstSizePx,
                                alpha = if (crisisMode) 0.75f else 1.0f,
                                filterQuality = FilterQuality.High
                            )
                        } else {
                            // Fallback 1: procura tile Pai (até 6 níveis acima, permitindo navegar com os blocos da cidade toda!)
                            var drawnFallback = false
                            for (deltaZ in 1..6) {
                                val parentZ = tileZoom - deltaZ
                                if (parentZ < 9) break
                                val parentTx = tx shr deltaZ
                                val parentTy = ty shr deltaZ
                                val parentBmp = tileBitmapCache["$parentZ/$parentTx/$parentTy"]
                                if (parentBmp != null) {
                                    val subDiv = 1 shl deltaZ
                                    val subX = tx and (subDiv - 1)
                                    val subY = ty and (subDiv - 1)
                                    val srcW = (parentBmp.width / subDiv).coerceAtLeast(1)
                                    val srcH = (parentBmp.height / subDiv).coerceAtLeast(1)
                                    drawImage(
                                        image = parentBmp,
                                        srcOffset = IntOffset(subX * srcW, subY * srcH),
                                        srcSize = IntSize(srcW, srcH),
                                        dstOffset = IntOffset(tileScreenX, tileScreenY),
                                        dstSize = dstSizePx,
                                        alpha = if (crisisMode) 0.70f else 0.96f,
                                        filterQuality = FilterQuality.Medium
                                    )
                                    drawnFallback = true
                                    loadedTilesInView++
                                    break
                                }
                            }

                            // Fallback 2: procura tiles Filhos (zoom + 1) ao dar Zoom Out
                            if (!drawnFallback && tileZoom < 19) {
                                val childZ = tileZoom + 1
                                val halfSize = (tileSizePx / 2.0).roundToInt() + 2
                                for (cx in 0..1) {
                                    for (cy in 0..1) {
                                        val childBmp = tileBitmapCache["$childZ/${tx * 2 + cx}/${ty * 2 + cy}"]
                                        if (childBmp != null) {
                                            drawImage(
                                                image = childBmp,
                                                dstOffset = IntOffset(
                                                    tileScreenX + (cx * tileSizePx / 2.0).roundToInt(),
                                                    tileScreenY + (cy * tileSizePx / 2.0).roundToInt()
                                                ),
                                                dstSize = IntSize(halfSize, halfSize),
                                                alpha = if (crisisMode) 0.72f else 0.98f,
                                                filterQuality = FilterQuality.Medium
                                            )
                                            drawnFallback = true
                                        }
                                    }
                                }
                            }

                            // Fallback 3: Reticulado urbano tático de quadras quando 100% sem cache naquela coordenada
                            if (!drawnFallback) {
                                val tileF = tileSizePx.toFloat()
                                val sx = tileScreenX.toFloat()
                                val sy = tileScreenY.toFloat()
                                drawRect(
                                    color = if (crisisMode) Color(0xFF090E17) else Color(0xFF132036),
                                    topLeft = Offset(sx, sy),
                                    size = androidx.compose.ui.geometry.Size(tileF, tileF)
                                )
                                val subStep = tileF / 4f
                                for (k in 0..3) {
                                    val lineOffset = k * subStep
                                    drawLine(
                                        color = Color(0xFF334155).copy(alpha = 0.55f),
                                        start = Offset(sx + lineOffset, sy),
                                        end = Offset(sx + lineOffset, sy + tileF),
                                        strokeWidth = if (k == 0) 2.2f else 1.1f
                                    )
                                    drawLine(
                                        color = Color(0xFF334155).copy(alpha = 0.55f),
                                        start = Offset(sx, sy + lineOffset),
                                        end = Offset(sx + tileF, sy + lineOffset),
                                        strokeWidth = if (k == 0) 2.2f else 1.1f
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. Grade Cartográfica Tática Discreta
        val gridAlpha = if (loadedTilesInView > 0 && showStreetTiles) 0.12f else 0.35f
        val gridStep = 72.dp.toPx()
        var gx = (w / 2f) % gridStep
        while (gx < w) {
            drawLine(
                color = Color(0xFF38BDF8).copy(alpha = gridAlpha),
                start = Offset(gx, 0f),
                end = Offset(gx, h),
                strokeWidth = 1f
            )
            gx += gridStep
        }
        var gy = (h / 2f) % gridStep
        while (gy < h) {
            drawLine(
                color = Color(0xFF38BDF8).copy(alpha = gridAlpha),
                start = Offset(0f, gy),
                end = Offset(w, gy),
                strokeWidth = 1f
            )
            gy += gridStep
        }

        // 3. Cartografia Vetorial Completa do Município do Rio de Janeiro (Del Castilho, Zona Norte, Sul, Centro e Oeste)
        val isNearRioMunicipio = centerLat in -23.15..-22.70 && centerLon in -43.80..-43.05
        if (isNearRioMunicipio) {
            val lagoaPath = Path().apply {
                lagoaPolygon.forEachIndexed { idx, (lat, lon) ->
                    val pt = toScreen(lat, lon)
                    if (idx == 0) moveTo(pt.x, pt.y) else lineTo(pt.x, pt.y)
                }
                close()
            }
            drawPath(
                path = lagoaPath,
                color = Color(0xFF0284C7).copy(alpha = if (showStreetTiles && loadedTilesInView > 0) 0.14f else 0.38f)
            )
            drawPath(
                path = lagoaPath,
                color = Color(0xFF38BDF8).copy(alpha = 0.55f),
                style = Stroke(width = 1.5.dp.toPx())
            )

            val coastPath = Path().apply {
                coastlineOrla.forEachIndexed { idx, (lat, lon) ->
                    val pt = toScreen(lat, lon)
                    if (idx == 0) moveTo(pt.x, pt.y) else lineTo(pt.x, pt.y)
                }
            }
            drawPath(
                path = coastPath,
                color = Color(0xFF0EA5E9).copy(alpha = 0.50f),
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            val roadLabelPaint = Paint().apply {
                color = android.graphics.Color.argb(245, 254, 240, 138)
                textSize = 11.2.sp.toPx()
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
                isAntiAlias = true
                setShadowLayer(6f, 0f, 1f, android.graphics.Color.BLACK)
            }

            ALL_RIO_NAMED_ROADS.forEach { road ->
                if (!showStreetTiles || loadedTilesInView == 0 || isDrivingWazeMode) {
                    val roadPath = Path().apply {
                        road.points.forEachIndexed { idx, (lat, lon) ->
                            val pt = toScreen(lat, lon)
                            if (idx == 0) moveTo(pt.x, pt.y) else lineTo(pt.x, pt.y)
                        }
                    }
                    val roadColor = when {
                        road.isExpressway && (!showStreetTiles || loadedTilesInView == 0) -> Color(0xFFF59E0B).copy(alpha = 0.78f)
                        !showStreetTiles || loadedTilesInView == 0 -> Color(0xFF94A3B8).copy(alpha = 0.80f)
                        else -> Color(0xFFF59E0B).copy(alpha = 0.28f)
                    }
                    drawPath(
                        path = roadPath,
                        color = roadColor,
                        style = Stroke(
                            width = if (road.isExpressway) 3.8.dp.toPx() else 2.8.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    )
                }
                if (zoomFloat >= 13.0f || !showStreetTiles || loadedTilesInView == 0) {
                    val lblPt = toScreen(road.labelLat, road.labelLon)
                    if (lblPt.x in 20f..(w - 20f) && lblPt.y in 20f..(h - 20f)) {
                        drawContext.canvas.nativeCanvas.drawText(road.name, lblPt.x, lblPt.y, roadLabelPaint)
                    }
                }
            }

            val neighborhoodPaint = Paint().apply {
                color = android.graphics.Color.argb(225, 241, 245, 249)
                textSize = if (zoomFloat >= 15f) 11.5.sp.toPx() else 9.8.sp.toPx()
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
                isAntiAlias = true
                setShadowLayer(6f, 0f, 1f, android.graphics.Color.BLACK)
            }
            ALL_RIO_NEIGHBORHOODS.forEach { (name, lat, lon) ->
                val pt = toScreen(lat, lon)
                if (pt.x in 0f..w && pt.y in 0f..h) {
                    drawContext.canvas.nativeCanvas.drawText(name, pt.x, pt.y, neighborhoodPaint)
                }
            }
        }

        // 4. TRILHA DE MOVIMENTAÇÃO DO USUÁRIO (BREADCRUMB TRAIL)
        if (movementTrail.size >= 2) {
            val trailPath = Path()
            var hasVisibleSegment = false
            movementTrail.forEachIndexed { index, (tLat, tLon) ->
                val pt = toScreen(tLat, tLon)
                if (pt.x in -w..(2 * w) && pt.y in -h..(2 * h)) {
                    hasVisibleSegment = true
                }
                if (index == 0) trailPath.moveTo(pt.x, pt.y) else trailPath.lineTo(pt.x, pt.y)
            }
            if (hasVisibleSegment) {
                drawPath(
                    path = trailPath,
                    color = Color(0xFF0284C7).copy(alpha = 0.55f),
                    style = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
                drawPath(
                    path = trailPath,
                    color = Color(0xFF38BDF8),
                    style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }
        }

        // 5. ROTA ATIVA ESTILO WAZE (COM FAIXA NEON E SETAS DIRECIONAIS DE FLUXO)
        val userScreen = toScreen(userLat, userLon)
        if (focusedPoi != null) {
            val targetScreen = toScreen(focusedPoi.latitude, focusedPoi.longitude)
            val clampedTargetX = targetScreen.x.coerceIn(-w * 2, 3 * w)
            val clampedTargetY = targetScreen.y.coerceIn(-h * 2, 3 * h)
            val clampedUserX = userScreen.x.coerceIn(-w * 2, 3 * w)
            val clampedUserY = userScreen.y.coerceIn(-h * 2, 3 * h)
            val startPt = Offset(clampedUserX, clampedUserY)
            val endPt = Offset(clampedTargetX, clampedTargetY)

            if (isDrivingWazeMode) {
                // Faixa externa escura estilo Waze
                drawLine(
                    color = Color(0xFF2E1065).copy(alpha = 0.90f),
                    start = startPt,
                    end = endPt,
                    strokeWidth = 11.dp.toPx(),
                    cap = StrokeCap.Round
                )
                // Faixa principal neon roxo/ciano estilo Waze
                drawLine(
                    color = Color(0xFFA855F7),
                    start = startPt,
                    end = endPt,
                    strokeWidth = 6.5.dp.toPx(),
                    cap = StrokeCap.Round
                )
                // Chevrons / Setas brancas ao longo da rota indicando o fluxo até o Pin
                val dx = endPt.x - startPt.x
                val dy = endPt.y - startPt.y
                val distPx = hypot(dx, dy)
                if (distPx > 35f) {
                    val angleRad = atan2(dy.toDouble(), dx.toDouble())
                    val stepPx = 48.dp.toPx()
                    var d = stepPx * 0.6f
                    val wingLen = 6.5.dp.toPx()
                    while (d < distPx - 20f) {
                        val cx = startPt.x + (dx / distPx) * d
                        val cy = startPt.y + (dy / distPx) * d
                        val leftWing = angleRad + Math.toRadians(145.0)
                        val rightWing = angleRad - Math.toRadians(145.0)
                        drawLine(
                            color = Color.White,
                            start = Offset(cx, cy),
                            end = Offset(
                                cx + (cos(leftWing) * wingLen).toFloat(),
                                cy + (sin(leftWing) * wingLen).toFloat()
                            ),
                            strokeWidth = 2.2.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                        drawLine(
                            color = Color.White,
                            start = Offset(cx, cy),
                            end = Offset(
                                cx + (cos(rightWing) * wingLen).toFloat(),
                                cy + (sin(rightWing) * wingLen).toFloat()
                            ),
                            strokeWidth = 2.2.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                        d += stepPx
                    }
                }
            } else {
                drawLine(
                    color = Color(0xFFFBBF24),
                    start = startPt,
                    end = endPt,
                    strokeWidth = 3.2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 10f), 0f)
                )
            }
        }

        // 6. Marcadores Geográficos de todos os Pins Estratégicos e POIs do Banco Room
        val poiLabelPaint = Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 12.sp.toPx()
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
            setShadowLayer(6f, 0f, 1f, android.graphics.Color.BLACK)
        }

        pois.forEach { poi ->
            val pt = toScreen(poi.latitude, poi.longitude)
            if (pt.x in -40f..(w + 40f) && pt.y in -40f..(h + 40f)) {
                val isFocused = poi.id == focusedPoi?.id
                val pinColor = when (poi.type) {
                    PoiType.FAMILY_MEETING -> Color(0xFFF59E0B)
                    PoiType.STRATEGIC_PIN -> Color(0xFFA855F7)
                    PoiType.HOME_BASE -> Color(0xFF38BDF8)
                    PoiType.SUPPLY_FUEL -> Color(0xFFEAB308)
                    PoiType.HOSPITAL -> Color(0xFFEF4444)
                    PoiType.FIRE_DEPT -> Color(0xFFF97316)
                    PoiType.SHELTER -> Color(0xFF10B981)
                }

                if (isFocused) {
                    drawCircle(
                        color = pinColor.copy(alpha = 0.38f),
                        radius = 20.dp.toPx(),
                        center = pt
                    )
                }
                drawCircle(
                    color = Color.Black,
                    radius = if (isFocused) 10.dp.toPx() else 8.dp.toPx(),
                    center = pt
                )
                drawCircle(
                    color = pinColor,
                    radius = if (isFocused) 8.dp.toPx() else 6.dp.toPx(),
                    center = pt
                )
                drawCircle(
                    color = Color.White,
                    radius = 2.5.dp.toPx(),
                    center = pt
                )

                val shortLabel = poi.title.substringBefore(" -").take(24)
                drawContext.canvas.nativeCanvas.drawText(
                    shortLabel,
                    pt.x + 12.dp.toPx(),
                    pt.y + 4.dp.toPx(),
                    poiLabelPaint
                )
            }
        }

        // 7. Pino Automotivo / Posição Atual do Usuário + Cone de Farol/Direção + Nome da Rua Atual
        if (userScreen.x in -60f..(w + 60f) && userScreen.y in -60f..(h + 60f)) {
            if (!showStreetTiles || loadedTilesInView == 0) {
                drawCircle(
                    color = Color(0xFF38BDF8).copy(alpha = 0.25f),
                    radius = 58.dp.toPx(),
                    center = userScreen,
                    style = Stroke(width = 1.2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f))
                )
            }

            drawCircle(
                color = when {
                    isDrivingWazeMode -> Color(0xFFA855F7).copy(alpha = 0.32f)
                    isLiveTracking -> Color(0xFF10B981).copy(alpha = 0.30f)
                    else -> Color(0xFF0284C7).copy(alpha = 0.30f)
                },
                radius = if (userAccuracyMeters > 0f) userAccuracyMeters.coerceIn(18f, 42f).dp.toPx() else 24.dp.toPx(),
                center = userScreen
            )

            // Seta / Cone indicando para onde o carro/usuário está apontando
            val headingRad = Math.toRadians((userHeadingDegrees - 90f).toDouble())
            val tipLength = if (isDrivingWazeMode) 28.dp.toPx() else 24.dp.toPx()
            val tipX = userScreen.x + (cos(headingRad) * tipLength).toFloat()
            val tipY = userScreen.y + (sin(headingRad) * tipLength).toFloat()

            val leftWingRad = Math.toRadians((userHeadingDegrees - 90f - 28f).toDouble())
            val rightWingRad = Math.toRadians((userHeadingDegrees - 90f + 28f).toDouble())
            val wingLen = if (isDrivingWazeMode) 14.dp.toPx() else 12.dp.toPx()

            val conePath = Path().apply {
                moveTo(tipX, tipY)
                lineTo(
                    userScreen.x + (cos(leftWingRad) * wingLen).toFloat(),
                    userScreen.y + (sin(leftWingRad) * wingLen).toFloat()
                )
                lineTo(
                    userScreen.x + (cos(rightWingRad) * wingLen).toFloat(),
                    userScreen.y + (sin(rightWingRad) * wingLen).toFloat()
                )
                close()
            }
            drawPath(
                path = conePath,
                color = when {
                    isDrivingWazeMode -> Color(0xFFD8B4FE)
                    isLiveTracking -> Color(0xFF34D399)
                    else -> Color(0xFF38BDF8)
                }
            )

            drawCircle(
                color = Color.White,
                radius = if (isDrivingWazeMode) 10.dp.toPx() else 8.5.dp.toPx(),
                center = userScreen
            )
            drawCircle(
                color = when {
                    isDrivingWazeMode -> Color(0xFF9333EA)
                    isLiveTracking -> Color(0xFF10B981)
                    else -> Color(0xFF0284C7)
                },
                radius = if (isDrivingWazeMode) 7.2.dp.toPx() else 6.dp.toPx(),
                center = userScreen
            )

            val userStreetPaint = Paint().apply {
                color = android.graphics.Color.argb(255, 254, 240, 138)
                textSize = 12.sp.toPx()
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
                isAntiAlias = true
                setShadowLayer(8f, 0f, 1.5f, android.graphics.Color.BLACK)
            }
            drawContext.canvas.nativeCanvas.drawText(
                currentStreetAddress.take(36),
                userScreen.x,
                userScreen.y - 17.dp.toPx(),
                userStreetPaint
            )
        }
    }
}

/**
 * Modal Rápido para Cravar Pin Estratégico no Mapa (ao segurar o dedo numa rua ou tocar em "📌 Cravar Pin").
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickStrategicPinDialog(
    initialLat: Double,
    initialLon: Double,
    resolvedStreetAddress: String,
    userLat: Double,
    userLon: Double,
    onDismiss: () -> Unit,
    onConfirmPin: (
        title: String,
        type: PoiType,
        lat: Double,
        lon: Double,
        address: String,
        phone: String?,
        startWazeImmediately: Boolean
    ) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(PoiType.STRATEGIC_PIN) }
    var latText by remember(initialLat) { mutableStateOf(String.format(Locale.US, "%.5f", initialLat)) }
    var lonText by remember(initialLon) { mutableStateOf(String.format(Locale.US, "%.5f", initialLon)) }
    var address by remember(resolvedStreetAddress) { mutableStateOf(resolvedStreetAddress) }
    var phone by remember { mutableStateOf("") }

    val quickTitleSuggestions = remember(selectedType) {
        when (selectedType) {
            PoiType.STRATEGIC_PIN -> listOf("Pin Estratégico Del Castilho", "Acesso Linha Amarela", "Ponto de Apoio na Rota", "Rota Alternativa Segura")
            PoiType.HOME_BASE -> listOf("Minha Casa / Base", "Casa de Familiar (Del Castilho)", "Base Segura Zona Norte")
            PoiType.SUPPLY_FUEL -> listOf("Posto de Combustível 24h", "Fonte de Água / Suprimentos", "Farmácia / Apoio")
            PoiType.FAMILY_MEETING -> listOf("Encontro Shopping Nova América", "Encontro Familiar Primário", "Ponto de Reunião")
            PoiType.HOSPITAL -> listOf("Hospital / UPA 24h", "Pronto-Socorro de Referência")
            PoiType.FIRE_DEPT -> listOf("Quartel Corpo de Bombeiros", "Posto Defesa Civil / Resgate")
            PoiType.SHELTER -> listOf("Abrigo Alto / Ponto Seguro", "Escola / Ginásio de Abrigo")
        }
    }

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        focusedBorderColor = Color(0xFFFBBF24),
        unfocusedBorderColor = Color(0xFF475569)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF111827),
        titleContentColor = Color.White,
        textContentColor = Color.White,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PushPin, contentDescription = null, tint = Color(0xFFFBBF24))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Cravar Pin Estratégico", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Categoria do Pin Estratégico:",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFFFBBF24)
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(PoiType.values()) { t ->
                        val isSel = selectedType == t
                        FilterChip(
                            selected = isSel,
                            onClick = { selectedType = t },
                            label = {
                                Text(
                                    t.displayName,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSel) Color(0xFF090D16) else Color.White
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFFBBF24),
                                containerColor = Color(0xFF1E293B)
                            )
                        )
                    }
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Nome do Pin / Local Estratégico", color = Color(0xFFCBD5E1)) },
                    placeholder = { Text("Ex: Casa Del Castilho, Posto, Saída...", fontSize = 11.sp, color = Color(0xFF64748B)) },
                    colors = textFieldColors,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Sugestões rápidas (toque para preencher):", fontSize = 10.sp, color = Color(0xFF94A3B8))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(quickTitleSuggestions) { suggestion ->
                        SuggestionChip(
                            onClick = { title = suggestion },
                            label = {
                                Text(suggestion, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            },
                            colors = SuggestionChipDefaults.suggestionChipColors(containerColor = Color(0xFF1E293B))
                        )
                    }
                }

                // Atalhos de Coordenada (Ponto clicado, Minha Posição ou Del Castilho)
                Text("Localização do Pin:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = {
                            latText = String.format(Locale.US, "%.5f", userLat)
                            lonText = String.format(Locale.US, "%.5f", userLon)
                            address = resolveNearestVectorStreetAndNeighborhood(userLat, userLon)
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text("Meu GPS", fontSize = 10.sp, color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = {
                            latText = String.format(Locale.US, "%.5f", DEL_CASTILHO_LAT)
                            lonText = String.format(Locale.US, "%.5f", DEL_CASTILHO_LON)
                            address = "Av. Pastor Martin Luther King Jr. • Del Castilho"
                            if (title.isBlank()) title = "Ponto Estratégico Del Castilho"
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text("Del Castilho", fontSize = 10.sp, color = Color(0xFF34D399), fontWeight = FontWeight.Bold)
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = latText,
                        onValueChange = { latText = it },
                        label = { Text("Latitude", color = Color(0xFFCBD5E1)) },
                        colors = textFieldColors,
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = lonText,
                        onValueChange = { lonText = it },
                        label = { Text("Longitude", color = Color(0xFFCBD5E1)) },
                        colors = textFieldColors,
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Rua / Bairro / Referência", color = Color(0xFFCBD5E1)) },
                    colors = textFieldColors,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Observação / Telefone / Rádio (Opcional)", color = Color(0xFFCBD5E1)) },
                    colors = textFieldColors,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(
                    onClick = {
                        val finalTitle = title.trim().ifBlank { selectedType.displayName }
                        val lat = latText.replace(",", ".").toDoubleOrNull() ?: initialLat
                        val lon = lonText.replace(",", ".").toDoubleOrNull() ?: initialLon
                        onConfirmPin(
                            finalTitle,
                            selectedType,
                            lat,
                            lon,
                            address.ifBlank { resolveNearestVectorStreetAndNeighborhood(lat, lon) },
                            phone.ifBlank { null },
                            false
                        )
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFF59E0B),
                        contentColor = Color(0xFF090D16)
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Text("📌 Salvar Pin", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                }

                Button(
                    onClick = {
                        val finalTitle = title.trim().ifBlank { selectedType.displayName }
                        val lat = latText.replace(",", ".").toDoubleOrNull() ?: initialLat
                        val lon = lonText.replace(",", ".").toDoubleOrNull() ?: initialLon
                        onConfirmPin(
                            finalTitle,
                            selectedType,
                            lat,
                            lon,
                            address.ifBlank { resolveNearestVectorStreetAndNeighborhood(lat, lon) },
                            phone.ifBlank { null },
                            true
                        )
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF9333EA),
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Text("🚗 Salvar e Ir (Waze)", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = Color(0xFFCBD5E1))
            }
        }
    )
}

/**
 * Modal Gerenciador de Download Offline (Cidade do Rio de Janeiro Inteira + Rotas Waze + Bairro Atual).
 */
@Composable
private fun OfflineCityDownloadDialog(
    cachedTilesCount: Int,
    poisCount: Int,
    isDownloading: Boolean,
    progressCurrent: Int,
    progressTotal: Int,
    statusText: String?,
    onDismiss: () -> Unit,
    onSelectPackage: (OfflineDownloadScope) -> Unit,
    onCancelDownload: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF111827),
        titleContentColor = Color.White,
        textContentColor = Color.White,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.DownloadForOffline, contentDescription = null, tint = Color(0xFF38BDF8))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Salvar Mapa Offline no Celular", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Surface(
                    color = Color(0xFF090D16),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0xFF334155)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "📦 Armazenamento & Backup Offline: $cachedTilesCount blocos salvos",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF34D399)
                        )
                        Text(
                            text = "Todos os blocos de mapa, nomes de ruas e Pins Estratégicos são salvos na memória interna e incluídos automaticamente no Backup (.zip). Ao restaurar um backup, o mapa inteiro volta 100% sem precisar de internet!",
                            fontSize = 11.sp,
                            color = Color(0xFFCBD5E1)
                        )
                    }
                }

                if (isDownloading) {
                    Surface(
                        color = Color(0xFF0F2942),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFF38BDF8)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = statusText ?: "Baixando blocos de ruas...",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { (progressCurrent.toFloat() / progressTotal.coerceAtLeast(1)).coerceIn(0f, 1f) },
                                color = Color(0xFF38BDF8),
                                trackColor = Color(0xFF1E293B),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = onCancelDownload,
                                border = BorderStroke(1.dp, Color(0xFFF87171)),
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Text("Pausar Download", fontSize = 11.sp, color = Color(0xFFF87171), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // OPÇÃO 1: CIDADE DO RIO DE JANEIRO INTEIRA + DEL CASTILHO + MEUS PINS HD
                Surface(
                    color = Color(0xFF0F2942),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.5.dp, Color(0xFF38BDF8)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !isDownloading) {
                            onSelectPackage(OfflineDownloadScope.ENTIRE_RIO_CITY_PLUS_PINS)
                        }
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "🏙️ Cidade do Rio de Janeiro Inteira + Pins HD",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFFFDE047),
                                modifier = Modifier.weight(1f)
                            )
                            Surface(
                                color = Color(0xFF0284C7),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "RECOMENDADO",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Salva o mapa de toda a cidade do Rio (Zona Norte com Del Castilho/Méier/Madureira/Penha/Ilha, Centro, Zona Sul e Zona Oeste) + Ruas HD ao redor da sua posição e dos seus $poisCount Pins Estratégicos.",
                            fontSize = 11.sp,
                            color = Color(0xFFE2E8F0)
                        )
                    }
                }

                // OPÇÃO 2: CORREDOR DE ROTAS WAZE (ORIGEM -> TODOS OS PINS EM HD)
                Surface(
                    color = Color(0xFF1E1035),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFFA855F7)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !isDownloading) {
                            onSelectPackage(OfflineDownloadScope.CORRIDOR_ROUTES_HD)
                        }
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "🚗 Corredor Completo das Rotas Waze (Zoom 14 a 16)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFFD8B4FE)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Baixa em alta definição todas as ruas ao longo do trajeto entre onde você está, Del Castilho e cada um dos seus Pins Estratégicos para dirigir 100% offline.",
                            fontSize = 11.sp,
                            color = Color(0xFFE2E8F0)
                        )
                    }
                }

                // OPÇÃO 3: BAIRRO ATUAL ULTRA-HD
                Surface(
                    color = Color(0xFF1E293B),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFF475569)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !isDownloading) {
                            onSelectPackage(OfflineDownloadScope.CURRENT_NEIGHBORHOOD_HD)
                        }
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "📍 Bairro Atual na Tela em Ultra-HD (Zoom 14 a 17)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Download rápido (~75 blocos) cobrindo todas as esquinas e ruas secundárias da região que você está olhando agora.",
                            fontSize = 11.sp,
                            color = Color(0xFFCBD5E1)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF334155),
                    contentColor = Color.White
                )
            ) {
                Text("Fechar", fontWeight = FontWeight.Bold)
            }
        }
    )
}

/**
 * Calcula orientação de direção estilo Waze (Seta de manobra, diferença angular, corredor sugerido e ETA de carro).
 */
private fun computeDrivingManeuverGuidance(
    userLat: Double,
    userLon: Double,
    userSpeedKmh: Float,
    userHeadingDeg: Float,
    poiNav: RationingAndAlertEngine.PoiNavigationInfo,
    poiEntity: MapPoiEntity
): DrivingManeuverGuidance {
    val distMeters = poiNav.distanceMeters
    val hasArrived = distMeters <= 60.0

    // Diferença angular normalizada (-180° a +180°) entre o rumo do carro e o azimute do Pin
    val rawDiff = (poiNav.bearingDegrees.toFloat() - userHeadingDeg + 540f) % 360f - 180f

    val instructionTitle = when {
        hasArrived -> "🏁 VOCÊ CHEGOU AO DESTINO!"
        abs(rawDiff) <= 25f -> "⬆️ SIGA EM FRENTE (${poiNav.cardinalCode})"
        rawDiff in 25f..65f -> "↗️ MANTENHA À DIREITA (+${rawDiff.roundToInt()}°)"
        rawDiff in 65f..135f -> "➡️ VIRE À DIREITA (+${rawDiff.roundToInt()}°)"
        rawDiff in -65f..-25f -> "↖️ MANTENHA À ESQUERDA (${abs(rawDiff).roundToInt()}°)"
        rawDiff in -135f..-65f -> "⬅️ VIRE À ESQUERDA (${abs(rawDiff).roundToInt()}°)"
        else -> "🔄 FAÇA O RETORNO (${poiNav.cardinalDetailed})"
    }

    //Velocidade efetiva para ETA automotivo: usa velocidade real se em movimento (>= 8 km/h) ou média urbana de 38 km/h
    val effectiveSpeedKmh = if (userSpeedKmh >= 8f) userSpeedKmh.toDouble() else 38.0
    val speedMetersPerMin = (effectiveSpeedKmh * 1000.0) / 60.0
    val etaMinutes = (distMeters / speedMetersPerMin).roundToInt().coerceAtLeast(1)

    val arrivalMillis = System.currentTimeMillis() + (etaMinutes * 60_000L)
    val arrivalFormatted = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(arrivalMillis))

    // Identifica o corredor viário principal mais estratégico entre o usuário e o Pin
    val midLat = (userLat + poiEntity.latitude) / 2.0
    val midLon = (userLon + poiEntity.longitude) / 2.0
    val closestExpressway = ALL_RIO_NAMED_ROADS
        .minByOrNull { road ->
            road.points.minOf { (rLat, rLon) ->
                hypot(rLat - midLat, rLon - midLon)
            }
        }
    val suggestedCorridor = if (closestExpressway != null && distMeters > 600.0) {
        "Via sugerida: ${closestExpressway.name} • Azimute ${poiNav.bearingDegrees.toInt()}°"
    } else {
        "Rota Direta • Rumo ${poiNav.cardinalDetailed}"
    }

    return DrivingManeuverGuidance(
        instructionTitle = instructionTitle,
        instructionSub = poiNav.address,
        suggestedCorridor = suggestedCorridor,
        turnDeltaDegrees = rawDiff,
        etaMinutes = etaMinutes,
        arrivalTimeFormatted = arrivalFormatted,
        hasArrived = hasArrived
    )
}

/**
 * Resolve o nome da rua/avenida e bairro mais próximos usando a base vetorial embutida do Rio de Janeiro (100% offline).
 */
private fun resolveNearestVectorStreetAndNeighborhood(lat: Double, lon: Double): String {
    var bestRoadName: String? = null
    var bestRoadDistDeg = Double.MAX_VALUE

    for (road in ALL_RIO_NAMED_ROADS) {
        for ((rLat, rLon) in road.points) {
            val d = hypot(lat - rLat, lon - rLon)
            if (d < bestRoadDistDeg) {
                bestRoadDistDeg = d
                bestRoadName = road.name
            }
        }
    }

    var bestNeighborhood: String? = null
    var bestNeighDistDeg = Double.MAX_VALUE
    for ((nName, nLat, nLon) in ALL_RIO_NEIGHBORHOODS) {
        val d = hypot(lat - nLat, lon - nLon)
        if (d < bestNeighDistDeg) {
            bestNeighDistDeg = d
            bestNeighborhood = nName.lowercase(Locale.getDefault()).replaceFirstChar { it.uppercase() }
        }
    }

    // Se estiver dentro da região metropolitana do Rio (~15 km)
    if (bestNeighDistDeg <= 0.15) {
        return when {
            bestRoadName != null && bestRoadDistDeg <= 0.025 && bestNeighborhood != null ->
                "$bestRoadName • $bestNeighborhood"
            bestNeighborhood != null ->
                "$bestNeighborhood • Rio de Janeiro"
            else -> String.format(Locale.US, "Coord %.4f, %.4f", lat, lon)
        }
    }
    return String.format(Locale.US, "Coord %.4f, %.4f", lat, lon)
}

private fun purgePoisonedLegacyTiles(context: Context) {
    try {
        listOf("osm_tiles", "osm_tiles_v2", "osm_tiles_v3").forEach { oldName ->
            val oldDir = File(context.filesDir, oldName)
            if (oldDir.exists()) {
                oldDir.deleteRecursively()
            }
        }
        val currentDir = File(context.filesDir, TILE_CACHE_DIR_NAME)
        if (currentDir.exists()) {
            currentDir.walkTopDown().forEach { f ->
                if (f.isFile && (f.length() == 6725L || f.length() == 6672L || f.length() == 2049L || f.length() < 60L)) {
                    f.delete()
                }
            }
        }
    } catch (_: Exception) {
    }
}

private fun loadOrDownloadTileWithStatus(
    context: Context,
    zoom: Int,
    x: Int,
    y: Int
): Pair<ImageBitmap, Boolean>? {
    return try {
        val tileFile = File(context.filesDir, "$TILE_CACHE_DIR_NAME/$zoom/$x/$y.png")
        if (tileFile.exists() && tileFile.length() >= 60L &&
            tileFile.length() != 6725L && tileFile.length() != 6672L && tileFile.length() != 2049L
        ) {
            val bmp = BitmapFactory.decodeFile(tileFile.absolutePath)
            if (bmp != null) return Pair(bmp.asImageBitmap(), false)
        }

        tileFile.parentFile?.mkdirs()

        val mirrorIndex = abs(x + y) % 4
        val mirrors = listOf(
            "https://tile.openstreetmap.org/$zoom/$x/$y.png",
            "https://tile.openstreetmap.de/$zoom/$x/$y.png",
            "https://a.tile.openstreetmap.fr/osmfr/$zoom/$x/$y.png",
            "https://b.tile.openstreetmap.fr/osmfr/$zoom/$x/$y.png"
        )
        val orderedUrls = mirrors.drop(mirrorIndex) + mirrors.take(mirrorIndex)

        for (urlStr in orderedUrls) {
            try {
                val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 3500
                    readTimeout = 3500
                    setRequestProperty("User-Agent", VALID_TILE_USER_AGENT)
                    setRequestProperty("Accept", "image/png,image/*;q=0.9")
                }

                val blockedHeader = conn.getHeaderField("x-blocked")
                if (conn.responseCode == 200 && blockedHeader == null) {
                    val bytes = conn.inputStream.use { it.readBytes() }
                    if (bytes.size >= 60 && bytes.size != 6725 && bytes.size != 6672 && bytes.size != 2049) {
                        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        if (bmp != null) {
                            FileOutputStream(tileFile).use { it.write(bytes) }
                            return Pair(bmp.asImageBitmap(), true)
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }
        null
    } catch (_: Exception) {
        null
    }
}

private suspend fun resolveStreetNameOfflineOrOnline(
    context: Context,
    lat: Double,
    lon: Double
): String = withContext(Dispatchers.IO) {
    val gridKey = String.format(Locale.US, "%.4f,%.4f", lat, lon)
    val cacheFile = File(context.filesDir, "street_names_cache.json")

    val cachedJson = try {
        if (cacheFile.exists()) JSONObject(cacheFile.readText()) else JSONObject()
    } catch (_: Exception) {
        JSONObject()
    }
    val cachedName = cachedJson.optString(gridKey, "")
    if (cachedName.isNotBlank()) return@withContext cachedName

    fun saveToCache(addressText: String) {
        try {
            cachedJson.put(gridKey, addressText)
            cacheFile.writeText(cachedJson.toString())
        } catch (_: Exception) {}
    }

    try {
        if (Geocoder.isPresent()) {
            val geocoder = Geocoder(context, Locale("pt", "BR"))
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocation(lat, lon, 1)
            val addr = addresses?.firstOrNull()
            if (addr != null) {
                val thoroughfare = addr.thoroughfare ?: addr.featureName
                val subLocality = addr.subLocality ?: addr.locality
                val formatted = buildString {
                    if (!thoroughfare.isNullOrBlank()) append(thoroughfare)
                    if (!subLocality.isNullOrBlank() && subLocality != thoroughfare) {
                        if (isNotEmpty()) append(" • ")
                        append(subLocality)
                    }
                }
                if (formatted.isNotBlank()) {
                    saveToCache(formatted)
                    return@withContext formatted
                }
            }
        }
    } catch (_: Exception) {}

    try {
        val url = URL("https://nominatim.openstreetmap.org/reverse?format=jsonv2&lat=$lat&lon=$lon&zoom=18&accept-language=pt-BR")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 2500
            readTimeout = 2500
            setRequestProperty("User-Agent", VALID_TILE_USER_AGENT)
        }
        if (conn.responseCode == 200) {
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            val addrObj = json.optJSONObject("address")
            if (addrObj != null) {
                val road = addrObj.optString("road")
                    .ifBlank { addrObj.optString("pedestrian") }
                    .ifBlank { addrObj.optString("suburb") }
                val suburb = addrObj.optString("suburb")
                    .ifBlank { addrObj.optString("neighbourhood") }
                    .ifBlank { addrObj.optString("city_district") }
                val formatted = buildString {
                    if (road.isNotBlank()) append(road)
                    if (suburb.isNotBlank() && suburb != road) {
                        if (isNotEmpty()) append(" • ")
                        append(suburb)
                    }
                }
                if (formatted.isNotBlank()) {
                    saveToCache(formatted)
                    return@withContext formatted
                }
            }
        }
    } catch (_: Exception) {}

    // Fallback 100% Offline: Usa a malha vetorial embutida do Rio de Janeiro (Del Castilho, Zona Norte, Sul, Oeste e Centro)
    resolveNearestVectorStreetAndNeighborhood(lat, lon)
}

/**
 * Converte (lat, lon, zoom) em coordenadas de tile (x, y) do OpenStreetMap.
 */
private fun latLonToTileXY(lat: Double, lon: Double, zoom: Int): Pair<Int, Int> {
    val n = 2.0.pow(zoom.toDouble())
    val maxTile = n.toInt() - 1
    val clampedLat = lat.coerceIn(-85.0511, 85.0511)
    val latRad = Math.toRadians(clampedLat)
    val tx = (((lon + 180.0) / 360.0 * n).toInt()).coerceIn(0, maxTile)
    val ty = (((1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * n).toInt()).coerceIn(0, maxTile)
    return tx to ty
}

/**
 * Baixa pacotes completos de mapa offline:
 * - `ENTIRE_RIO_CITY_PLUS_PINS`: Malha completa da Cidade do Rio de Janeiro (Zooms 11, 12, 13 e eixos 14) + Ruas HD (Zooms 15 e 16) em Del Castilho, posição do usuário e todos os Pins cadastrados!
 * - `CORRIDOR_ROUTES_HD`: Todos os blocos de rua (Zooms 14, 15 e 16) ao longo dos corredores entre o usuário, Del Castilho e os Pins!
 * - `CURRENT_NEIGHBORHOOD_HD`: Grade detalhada (Zooms 14 a 17) ao redor da visão atual.
 */
private suspend fun downloadOfflineMapPackage(
    context: Context,
    packageScope: OfflineDownloadScope,
    centerLat: Double,
    centerLon: Double,
    userLat: Double,
    userLon: Double,
    zoom: Int,
    pois: List<MapPoiEntity>,
    cache: MutableMap<String, ImageBitmap>,
    onProgress: (done: Int, total: Int) -> Unit
): Int = withContext(Dispatchers.IO) {
    val tilesToFetch = LinkedHashSet<Triple<Int, Int, Int>>() // (z, x, y)

    fun addGridAround(lat: Double, lon: Double, z: Int, radius: Int) {
        val maxTile = (1 shl z) - 1
        val (cx, cy) = latLonToTileXY(lat, lon, z)
        for (dx in -radius..radius) {
            for (dy in -radius..radius) {
                tilesToFetch.add(Triple(z, (cx + dx).coerceIn(0, maxTile), (cy + dy).coerceIn(0, maxTile)))
            }
        }
    }

    fun addBoundingBox(minLat: Double, maxLat: Double, minLon: Double, maxLon: Double, z: Int) {
        val (x1, y1) = latLonToTileXY(maxLat, minLon, z) // Noroeste
        val (x2, y2) = latLonToTileXY(minLat, maxLon, z) // Sudeste
        for (tx in min(x1, x2)..max(x1, x2)) {
            for (ty in min(y1, y2)..max(y1, y2)) {
                tilesToFetch.add(Triple(z, tx, ty))
            }
        }
    }

    fun addCorridorBetween(lat1: Double, lon1: Double, lat2: Double, lon2: Double, z: Int, steps: Int = 8) {
        for (i in 0..steps) {
            val frac = i.toDouble() / steps.toDouble()
            val lat = lat1 + (lat2 - lat1) * frac
            val lon = lon1 + (lon2 - lon1) * frac
            addGridAround(lat, lon, z, radius = 1)
        }
    }

    when (packageScope) {
        OfflineDownloadScope.CURRENT_NEIGHBORHOOD_HD -> {
            val baseZ = zoom.coerceIn(14, 17)
            val zooms = listOf((baseZ - 1).coerceAtLeast(13), baseZ, (baseZ + 1).coerceAtMost(17)).distinct()
            for (z in zooms) {
                addGridAround(centerLat, centerLon, z, radius = 2)
            }
        }

        OfflineDownloadScope.CORRIDOR_ROUTES_HD -> {
            val strategicTargets = mutableListOf(
                DEL_CASTILHO_LAT to DEL_CASTILHO_LON,
                centerLat to centerLon
            )
            pois.forEach { strategicTargets.add(it.latitude to it.longitude) }

            for ((tLat, tLon) in strategicTargets) {
                addCorridorBetween(userLat, userLon, tLat, tLon, z = 14, steps = 8)
                addCorridorBetween(userLat, userLon, tLat, tLon, z = 15, steps = 10)
                addGridAround(tLat, tLon, z = 16, radius = 1)
            }
            addGridAround(userLat, userLon, z = 16, radius = 2)
        }

        OfflineDownloadScope.ENTIRE_RIO_CITY_PLUS_PINS -> {
            // 1. Município do Rio de Janeiro Inteiro (Zona Oeste/Santa Cruz/Barra até Zona Norte/Del Castilho/Ilha, Centro e Zona Sul)
            addBoundingBox(minLat = -23.06, maxLat = -22.78, minLon = -43.72, maxLon = -43.12, z = 11)
            addBoundingBox(minLat = -23.05, maxLat = -22.79, minLon = -43.68, maxLon = -43.14, z = 12)
            addBoundingBox(minLat = -23.03, maxLat = -22.80, minLon = -43.58, maxLon = -43.15, z = 13)

            // 2. Malha Urbana Principal em Zoom 14 (Zona Norte completa com Del Castilho, Centro, Zona Sul e Barra/Jacarepaguá)
            addBoundingBox(minLat = -23.01, maxLat = -22.81, minLon = -43.42, maxLon = -43.16, z = 14)

            // 3. Alta Definição de Ruas (Zoom 15 e 16) em Del Castilho, Posição Atual, Centro do Mapa e Todos os Pins do Usuário
            addGridAround(DEL_CASTILHO_LAT, DEL_CASTILHO_LON, z = 15, radius = 2)
            addGridAround(DEL_CASTILHO_LAT, DEL_CASTILHO_LON, z = 16, radius = 2)

            addGridAround(userLat, userLon, z = 15, radius = 2)
            addGridAround(userLat, userLon, z = 16, radius = 2)

            addGridAround(centerLat, centerLon, z = 15, radius = 2)
            addGridAround(centerLat, centerLon, z = 16, radius = 1)

            pois.take(12).forEach { poi ->
                addGridAround(poi.latitude, poi.longitude, z = 15, radius = 1)
                addGridAround(poi.latitude, poi.longitude, z = 16, radius = 1)
            }
        }
    }

    val totalCount = tilesToFetch.size.coerceAtLeast(1)
    var processed = 0
    var newlyDownloaded = 0

    for ((z, tx, ty) in tilesToFetch) {
        if (!isActive) break
        val key = "$z/$tx/$ty"
        val res = loadOrDownloadTileWithStatus(context, z, tx, ty)
        processed++
        if (res != null) {
            val (bmp, isNew) = res
            if (isNew) newlyDownloaded++
            withContext(Dispatchers.Main) {
                cache[key] = bmp
                onProgress(processed, totalCount)
            }
        } else {
            withContext(Dispatchers.Main) {
                onProgress(processed, totalCount)
            }
        }
    }

    newlyDownloaded
}

private fun countCachedTilesOnDisk(context: Context): Int {
    val root = File(context.filesDir, TILE_CACHE_DIR_NAME)
    if (!root.exists()) return 0
    return root.walkTopDown().count { it.isFile && it.extension == "png" && it.length() >= 60L }
}

@Composable
fun PoiOfflineCard(
    nav: RationingAndAlertEngine.PoiNavigationInfo,
    isSelected: Boolean,
    isDrivingToThis: Boolean = false,
    crisisMode: Boolean,
    currentSpeedKmh: Float = 0f,
    onClick: () -> Unit,
    onStartWazeNavigation: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null
) {
    val isFamily = nav.poiType.contains("Famili", ignoreCase = true)
    val isStrategic = nav.poiType.contains("Estratégico", ignoreCase = true) || nav.poiType.contains("Casa", ignoreCase = true)
    val isSupply = nav.poiType.contains("Posto", ignoreCase = true) || nav.poiType.contains("Suprimento", ignoreCase = true)
    val isHospital = nav.poiType.contains("Hospital", ignoreCase = true)
    val isFire = nav.poiType.contains("Bombeiro", ignoreCase = true)

    val badgeColor = when {
        isStrategic -> Color(0xFFA855F7)
        isFamily -> Color(0xFFFBBF24)
        isSupply -> Color(0xFFEAB308)
        isHospital -> Color(0xFFF87171)
        isFire -> Color(0xFF38BDF8)
        else -> Color(0xFF34D399)
    }

    val effectiveCarSpeedKmh = if (currentSpeedKmh >= 8f) currentSpeedKmh.toDouble() else 38.0
    val carMinutes = (nav.distanceMeters / ((effectiveCarSpeedKmh * 1000.0) / 60.0)).roundToInt().coerceAtLeast(1)

    Card(
        colors = CardDefaults.cardColors(
            containerColor = when {
                crisisMode -> Color.Black
                isDrivingToThis -> Color(0xFF1E1035)
                isSelected -> Color(0xFF17253B)
                else -> Color(0xFF111827)
            }
        ),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            if (isSelected || isDrivingToThis) 2.dp else 1.dp,
            when {
                isDrivingToThis -> Color(0xFFA855F7)
                isSelected -> Color(0xFFFBBF24)
                else -> Color(0xFF334155)
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("map_poi_item_${nav.poiId}")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .background(badgeColor.copy(alpha = 0.18f), CircleShape)
            ) {
                Icon(
                    Icons.Default.Navigation,
                    contentDescription = null,
                    tint = badgeColor,
                    modifier = Modifier
                        .size(22.dp)
                        .rotate(nav.bearingDegrees.toFloat())
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = badgeColor.copy(alpha = 0.22f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = nav.poiType,
                            color = badgeColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${nav.cardinalCode} (${nav.bearingDegrees.toInt()}°) • 🚗 ~$carMinutes min",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFBBF24)
                    )
                }

                Spacer(modifier = Modifier.height(3.dp))

                Text(
                    text = nav.poiTitle,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp,
                    color = Color.White
                )

                Text(
                    text = nav.address,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = Color(0xFFCBD5E1)
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = nav.distanceFormatted,
                        fontWeight = FontWeight.Black,
                        fontSize = 16.sp,
                        color = if (isFamily || isStrategic) Color(0xFFFBBF24) else Color.White
                    )
                    if (onDelete != null) {
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(
                                Icons.Default.DeleteOutline,
                                contentDescription = "Apagar Ponto",
                                tint = Color(0xFFF87171),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                if (onStartWazeNavigation != null) {
                    Surface(
                        color = if (isDrivingToThis) Color(0xFF059669) else Color(0xFF9333EA),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.clickable { onStartWazeNavigation() }
                    ) {
                        Text(
                            text = if (isDrivingToThis) "🚗 Navegando" else "🚗 Ir (Waze)",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                } else if (nav.phone != null) {
                    Text(
                        text = nav.phone,
                        fontSize = 11.sp,
                        color = Color(0xFF38BDF8),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
