package com.example.ui.components

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.Typeface
import android.location.Geocoder
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.example.domain.RationingAndAlertEngine
import com.example.ui.MainUiState
import com.example.ui.MainViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.*

private const val TILE_CACHE_DIR_NAME = "osm_tiles_v4"
private const val VALID_TILE_USER_AGENT =
    "ResilienceHubApp/2.0 (Android; https://github.com/Magnostico/ResilienceHub)"

private data class NamedRoadVector(
    val name: String,
    val labelLat: Double,
    val labelLon: Double,
    val points: List<Pair<Double, Double>>
)

/**
 * Motor Cartográfico Híbrido 100% Kotlin/Compose:
 * 1. Projeção Web Mercator com Escala DPI Nativa (256.dp por tile + FilterQuality.High) para exibir nomes de todas as ruas com nitidez.
 * 2. Fila de download controlada (máx. 3 conexões simultâneas) em múltiplos servidores OpenStreetMap (OSM Oficial, OSM.de e OSM.fr) até Zoom 19, sem bloqueio por Varnish e sem tiles cinzas.
 * 3. Redesenho reativo garantido a cada tile carregado (`tileRenderVersion`) + Fallback Multi-Nível (Parent/Child Tile Scaling) + Grade Tática Local em qualquer coordenada GPS.
 * 4. Suporte fluido a Pulso GPS (1x), Rastreio Vivo em Movimento, Pinça (Zoom In/Out), Duplo-Toque e Arraste.
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
    val tileDownloadDispatcher = remember { Dispatchers.IO.limitedParallelism(3) }
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

    // Inicia em nível de rua (16.0f) onde todos os nomes de ruas e avenidas aparecem com clareza imediata
    var mapCenterLat by remember { mutableDoubleStateOf(state.currentLat) }
    var mapCenterLon by remember { mutableDoubleStateOf(state.currentLon) }
    var mapZoomFloat by remember { mutableFloatStateOf(16.0f) }
    val mapZoomInt = mapZoomFloat.toInt().coerceIn(10, 19)

    var isMapFullscreen by remember { mutableStateOf(false) }
    var showBottomPoisInFullscreen by remember { mutableStateOf(true) }
    var autoFollowUser by remember { mutableStateOf(true) }
    var showStreetTiles by remember { mutableStateOf(true) }
    var currentStreetAddress by remember { mutableStateOf("Localizando rua/avenida...") }

    // Cache em memória, versão de renderização reativa (força redraw imediato do Canvas) e contador de blocos em disco
    val tileBitmapCache = remember { mutableStateMapOf<String, ImageBitmap>() }
    var tileRenderVersion by remember { mutableIntStateOf(0) }
    var cachedTilesCount by remember {
        mutableIntStateOf(countCachedTilesOnDisk(context))
    }
    var isPreloadingTiles by remember { mutableStateOf(false) }
    var preloadStatusText by remember { mutableStateOf<String?>(null) }
    var showConfirmClearPois by remember { mutableStateOf(false) }

    // Limpa na inicialização qualquer tile antigo corrompido ("403 Access blocked" ou cinza do ArcGIS) das pastas legadas
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            purgePoisonedLegacyTiles(context)
        }
        cachedTilesCount = countCachedTilesOnDisk(context)
        tileRenderVersion++
    }

    // Quando o GPS atualiza a posição (Pulso 1x ou Rastreio Vivo) e o modo Auto-Seguir está ativo, move o centro do mapa junto com o usuário
    LaunchedEffect(state.currentLat, state.currentLon, state.gpsFixCount) {
        if (autoFollowUser) {
            mapCenterLat = state.currentLat
            mapCenterLon = state.currentLon
        }
    }

    // Atualiza o nome da rua/avenida atual conforme o usuário se move ou aciona o Pulso GPS
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

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
        modifier = modifier
            .fillMaxSize()
            .testTag("offline_map_screen")
    ) {
        // --- 1. PAINEL BÚSSOLA, RUA ATUAL, TELEMETRIA DE MOVIMENTO & CONTROLES GPS ---
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (crisisMode) Color.Black else Color(0xFF111827)
                ),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (state.isLiveTracking) Color(0xFF10B981)
                    else if (crisisMode) MaterialTheme.colorScheme.primary
                    else Color(0xFF334155)
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
                                    Icons.Default.Explore,
                                    contentDescription = null,
                                    tint = Color(0xFFFBBF24),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Navegação & Mapa Tático Offline",
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

                    // Botões de Rastreio Vivo (Em Movimento) e GPS Pulso (1x)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
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
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                            modifier = Modifier
                                .weight(1.2f)
                                .testTag("btn_live_tracking_toggle")
                        ) {
                            Icon(
                                if (state.isLiveTracking) Icons.Default.DirectionsRun else Icons.Default.LocationSearching,
                                contentDescription = null,
                                tint = if (state.isLiveTracking) Color.White else Color(0xFF34D399),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (state.isLiveTracking) "Rastreio Vivo: ON" else "Rastrear Movimento",
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
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_gps_pulse_map")
                        ) {
                            Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "GPS Pulso (1x)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
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
                            label = "TRILHA GPS",
                            value = "${state.movementTrail.size} pts",
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

                    // Painel da Bússola apontando para o Abrigo/Hospital focado
                    if (focusedPoi != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF090D16), RoundedCornerShape(10.dp))
                                .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(10.dp))
                                .padding(10.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.size(54.dp)
                            ) {
                                Canvas(modifier = Modifier.size(54.dp)) {
                                    drawCircle(
                                        color = Color(0xFF334155),
                                        style = Stroke(width = 2.dp.toPx())
                                    )
                                    drawLine(
                                        color = Color(0xFFEF4444),
                                        start = Offset(size.width / 2, 3.dp.toPx()),
                                        end = Offset(size.width / 2, 10.dp.toPx()),
                                        strokeWidth = 2.5.dp.toPx()
                                    )
                                }
                                Icon(
                                    Icons.Default.Navigation,
                                    contentDescription = null,
                                    tint = Color(0xFFFBBF24),
                                    modifier = Modifier
                                        .size(28.dp)
                                        .rotate(focusedPoi.bearingDegrees.toFloat())
                                )
                            }

                            Column(
                                modifier = Modifier
                                    .padding(start = 10.dp)
                                    .weight(1f)
                            ) {
                                Text(
                                    text = "DESTINO SELECIONADO:",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF94A3B8)
                                )
                                Text(
                                    text = focusedPoi.poiTitle,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = Color.White
                                )
                                val walkMinutes = (focusedPoi.distanceMeters / 75.0).roundToInt().coerceAtLeast(1)
                                Text(
                                    text = "${focusedPoi.cardinalDetailed} • Azimute ${focusedPoi.bearingDegrees.toInt()}° (~$walkMinutes min a pé)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFBBF24)
                                )
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = focusedPoi.distanceFormatted,
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFF38BDF8)
                                )
                                Text(
                                    text = "Linha Direta",
                                    fontSize = 10.sp,
                                    color = Color(0xFFCBD5E1)
                                )
                            }
                        }
                    }
                }
            }
        }

        // --- 2. MAPA INTERATIVO COM NOMES DE RUAS HD, PINÇA (ZOOM IN/OUT) E TELA CHEIA ---
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF070B14)
                ),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.5.dp,
                    if (state.isLiveTracking) Color(0xFF10B981) else Color(0xFF0284C7)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(370.dp)
            ) {
                MapViewportBox(
                    context = context,
                    state = state,
                    mapCenterLat = mapCenterLat,
                    mapCenterLon = mapCenterLon,
                    mapZoomFloat = mapZoomFloat,
                    autoFollowUser = autoFollowUser,
                    showStreetTiles = showStreetTiles,
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
                    onTileLoaded = { newlySavedToDisk ->
                        tileRenderVersion++
                        if (newlySavedToDisk) cachedTilesCount++
                    },
                    onTransformMap = { dLat, dLon, zoomFactor ->
                        if (abs(dLat) > 1e-7 || abs(dLon) > 1e-7) {
                            autoFollowUser = false
                            mapCenterLat = (mapCenterLat + dLat).coerceIn(-85.0, 85.0)
                            mapCenterLon = (mapCenterLon + dLon).coerceIn(-180.0, 180.0)
                        }
                        if (zoomFactor != 1.0f) {
                            val deltaZ = (ln(zoomFactor.toDouble()) / ln(2.0)).toFloat()
                            mapZoomFloat = (mapZoomFloat + deltaZ).coerceIn(10.0f, 19.2f)
                        }
                    },
                    onZoomChange = { newZoom -> mapZoomFloat = newZoom.coerceIn(10.0f, 19.2f) },
                    onCenterUser = {
                        autoFollowUser = true
                        mapCenterLat = state.currentLat
                        mapCenterLon = state.currentLon
                        if (mapZoomFloat < 16.0f) mapZoomFloat = 16.5f
                    },
                    onToggleStreetTiles = { showStreetTiles = !showStreetTiles },
                    onToggleFullscreen = { isMapFullscreen = true },
                    onPreloadTiles = {
                        scope.launch {
                            isPreloadingTiles = true
                            preloadStatusText = "Baixando ruas detalhadas..."
                            val downloaded = preloadRegionTiles(
                                context = context,
                                centerLat = mapCenterLat,
                                centerLon = mapCenterLon,
                                zoom = mapZoomInt,
                                cache = tileBitmapCache,
                                onEachTileLoaded = { tileRenderVersion++ }
                            )
                            cachedTilesCount = withContext(Dispatchers.IO) { countCachedTilesOnDisk(context) }
                            tileRenderVersion++
                            preloadStatusText = if (downloaded > 0) "✓ +$downloaded blocos de ruas salvos" else "✓ Ruas da região em cache"
                            isPreloadingTiles = false
                        }
                    }
                )
            }
        }

        // --- 3. CABEÇALHO DA LISTA DE PONTOS DE APOIO ---
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
            ) {
                Text(
                    text = "Pontos Seguros e Abrigos (${navList.size}):",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = onOpenAddPoiDialog,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Icon(Icons.Default.AddLocationAlt, contentDescription = null, tint = Color(0xFFFBBF24), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Novo Ponto", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFBBF24))
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

        // --- 4. LISTA DE PONTOS DE ENCONTRO E APOIO ---
        if (navList.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Text(
                            text = "Nenhum Ponto de Encontro ou Apoio cadastrado.",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = onOpenAddPoiDialog,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFF59E0B),
                                contentColor = Color(0xFF090D16)
                            )
                        ) {
                            Icon(Icons.Default.AddLocationAlt, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Cadastrar Meu Ponto de Encontro", fontWeight = FontWeight.ExtraBold)
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
                    crisisMode = crisisMode,
                    onClick = {
                        selectedPoiId = nav.poiId
                        val targetPoi = state.pois.find { it.id == nav.poiId }
                        if (targetPoi != null) {
                            autoFollowUser = false
                            mapCenterLat = targetPoi.latitude
                            mapCenterLon = targetPoi.longitude
                            if (mapZoomFloat < 16.5f) mapZoomFloat = 16.8f
                        }
                    },
                    onDelete = { viewModel.deletePoi(nav.poiId) }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(76.dp))
        }
    }

    if (showConfirmClearPois) {
        ConfirmClearDialog(
            title = "Limpar Todos os Pontos do Mapa?",
            message = "Todos os pontos de encontro, hospitais e abrigos fictícios serão removidos para que você cadastre os seus próprios locais.",
            confirmButtonText = "Apagar Pontos",
            onDismiss = { showConfirmClearPois = false },
            onConfirm = { viewModel.clearAllPois() }
        )
    }

    // =========================================================================
    // MODAL DE MAPA EM TELA CHEIA (100% DA TELA DO CELULAR COM ZOOM DE PINÇA)
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
                        onTileLoaded = { newlySavedToDisk ->
                            tileRenderVersion++
                            if (newlySavedToDisk) cachedTilesCount++
                        },
                        onTransformMap = { dLat, dLon, zoomFactor ->
                            if (abs(dLat) > 1e-7 || abs(dLon) > 1e-7) {
                                autoFollowUser = false
                                mapCenterLat = (mapCenterLat + dLat).coerceIn(-85.0, 85.0)
                                mapCenterLon = (mapCenterLon + dLon).coerceIn(-180.0, 180.0)
                            }
                            if (zoomFactor != 1.0f) {
                                val deltaZ = (ln(zoomFactor.toDouble()) / ln(2.0)).toFloat()
                                mapZoomFloat = (mapZoomFloat + deltaZ).coerceIn(10.0f, 19.2f)
                            }
                        },
                        onZoomChange = { newZoom -> mapZoomFloat = newZoom.coerceIn(10.0f, 19.2f) },
                        onCenterUser = {
                            autoFollowUser = true
                            mapCenterLat = state.currentLat
                            mapCenterLon = state.currentLon
                            if (mapZoomFloat < 16.0f) mapZoomFloat = 16.8f
                        },
                        onToggleStreetTiles = { showStreetTiles = !showStreetTiles },
                        onToggleFullscreen = { isMapFullscreen = false },
                        onPreloadTiles = {
                            scope.launch {
                                isPreloadingTiles = true
                                preloadStatusText = "Baixando ruas detalhadas..."
                                val downloaded = preloadRegionTiles(
                                    context = context,
                                    centerLat = mapCenterLat,
                                    centerLon = mapCenterLon,
                                    zoom = mapZoomInt,
                                    cache = tileBitmapCache,
                                    onEachTileLoaded = { tileRenderVersion++ }
                                )
                                cachedTilesCount = withContext(Dispatchers.IO) { countCachedTilesOnDisk(context) }
                                tileRenderVersion++
                                preloadStatusText = if (downloaded > 0) "✓ +$downloaded blocos de ruas salvos" else "✓ Região em cache"
                                isPreloadingTiles = false
                            }
                        }
                    )

                    // HUD Tático Superior em Tela Cheia (Bússola + Rua Atual + Velocidade + Botão Fechar)
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF090D16).copy(alpha = 0.92f)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
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
                                    if (focusedPoi != null) {
                                        Icon(
                                            Icons.Default.Navigation,
                                            contentDescription = null,
                                            tint = Color(0xFFFBBF24),
                                            modifier = Modifier
                                                .size(24.dp)
                                                .rotate(focusedPoi.bearingDegrees.toFloat())
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "📍 $currentStreetAddress",
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 12.sp,
                                            color = Color(0xFFFDE047),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = if (focusedPoi != null) {
                                                "${focusedPoi.poiTitle} • ${focusedPoi.distanceFormatted} • Zoom ${String.format(Locale.US, "%.1f", mapZoomFloat)}x"
                                            } else {
                                                "Zoom ${String.format(Locale.US, "%.1f", mapZoomFloat)}x • Vel: ${String.format(Locale.US, "%.1f km/h", state.currentSpeedKmh)}"
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

                            // Barra Rápida de Rastreio Vivo + GPS Pulso + Auto-Seguir + Mostrar/Ocultar Lista dentro da Tela Cheia
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
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
                                        text = if (state.isLiveTracking) "Movimento: ON" else "Rastrear",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Button(
                                    onClick = {
                                        autoFollowUser = true
                                        viewModel.triggerSingleShotGpsPulse()
                                        mapCenterLat = state.currentLat
                                        mapCenterLon = state.currentLon
                                        if (mapZoomFloat < 16.5f) mapZoomFloat = 17.0f
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFF59E0B),
                                        contentColor = Color(0xFF090D16)
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("Pulso 1x", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                                }

                                OutlinedButton(
                                    onClick = {
                                        autoFollowUser = !autoFollowUser
                                        if (autoFollowUser) {
                                            mapCenterLat = state.currentLat
                                            mapCenterLon = state.currentLon
                                        }
                                    },
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (autoFollowUser) Color(0xFF38BDF8) else Color(0xFF475569)
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.GpsFixed,
                                        contentDescription = null,
                                        tint = if (autoFollowUser) Color(0xFF38BDF8) else Color(0xFF94A3B8),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (autoFollowUser) "Seguindo" else "Livre",
                                        fontSize = 11.sp,
                                        color = Color.White
                                    )
                                }

                                if (navList.isNotEmpty()) {
                                    OutlinedButton(
                                        onClick = { showBottomPoisInFullscreen = !showBottomPoisInFullscreen },
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFBBF24)),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Text(
                                            text = if (showBottomPoisInFullscreen) "Ocultar" else "Abrigos",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFFBBF24)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Carrossel Inferior de POIs em Tela Cheia (retrátil para liberar 100% da visão das ruas)
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
                                        color = if (isSel) Color(0xFF0F2942) else Color(0xFF090D16).copy(alpha = 0.92f),
                                        shape = RoundedCornerShape(10.dp),
                                        border = androidx.compose.foundation.BorderStroke(
                                            if (isSel) 2.dp else 1.dp,
                                            if (isSel) Color(0xFFFBBF24) else Color(0xFF334155)
                                        ),
                                        modifier = Modifier
                                            .width(220.dp)
                                            .clickable {
                                                selectedPoiId = nav.poiId
                                                val target = state.pois.find { it.id == nav.poiId }
                                                if (target != null) {
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
                                            Text(
                                                text = "${nav.cardinalDetailed} (${nav.bearingDegrees.toInt()}°) • ${nav.poiType}",
                                                fontSize = 10.sp,
                                                color = Color(0xFF38BDF8),
                                                maxLines = 1
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
    onTileLoaded: (newlySavedToDisk: Boolean) -> Unit,
    onTransformMap: (dLat: Double, dLon: Double, zoomFactor: Float) -> Unit,
    onZoomChange: (Float) -> Unit,
    onCenterUser: () -> Unit,
    onToggleStreetTiles: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onPreloadTiles: () -> Unit
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
            onTransformGesture = onTransformMap
        )

        // Barra Superior do Mapa (na visualização normal)
        if (!isFullscreen) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Surface(
                    color = Color.Black.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(6.dp),
                    border = androidx.compose.foundation.BorderStroke(0.5.dp, Color(0xFF334155))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .background(
                                    if (state.isLiveTracking) Color(0xFF10B981) else Color(0xFF38BDF8),
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
                        border = androidx.compose.foundation.BorderStroke(0.5.dp, Color(0xFF38BDF8)),
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
                        modifier = Modifier.clickable(enabled = !isPreloadingTiles) { onPreloadTiles() }
                    ) {
                        Text(
                            text = if (isPreloadingTiles) "Salvando..." else "⬇ Salvar Offline",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }

        // Controles Flutuantes Laterais (+ / - / Centralizar / Tela Cheia / Camada)
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
                    contentDescription = "Salvar Região Offline",
                    tint = Color(0xFF38BDF8),
                    onClick = onPreloadTiles
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

        // Rodapé do Mapa: Dica de Pinça com os Dedos e Legenda
        if (!isFullscreen) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.82f))
                    .padding(horizontal = 8.dp, vertical = 5.dp)
            ) {
                Text(
                    text = preloadStatusText ?: "Pinça (2 dedos), arraste ou + / - para Zoom nas ruas",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LegendDot(color = Color(0xFF38BDF8), label = "Você")
                    LegendDot(color = Color(0xFFF59E0B), label = "Encontro")
                    LegendDot(color = Color(0xFFEF4444), label = "Hospital")
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
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF475569)),
        modifier = Modifier
            .size(40.dp)
            .clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(21.dp))
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
        Spacer(modifier = Modifier.width(4.dp))
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
    onTransformGesture: (dLat: Double, dLon: Double, zoomFactor: Float) -> Unit
) {
    val currentCenterLat by rememberUpdatedState(centerLat)
    val currentZoomFloat by rememberUpdatedState(zoomFloat)
    val currentOnTileLoaded by rememberUpdatedState(onTileLoaded)

    // Zoom inteiro do tile OpenStreetMap (10 a 19) e fator de escala contínuo entre níveis
    val tileZoom = zoomFloat.toInt().coerceIn(10, 19)
    val subTileScale = 2.0.pow((zoomFloat - tileZoom).toDouble())
    val n = 2.0.pow(tileZoom.toDouble())

    val clampedCenterLat = centerLat.coerceIn(-85.0511, 85.0511)
    val centerLatRad = Math.toRadians(clampedCenterLat)
    val currentCenterTileX = ((centerLon + 180.0) / 360.0 * n).toInt()
    val currentCenterTileY = ((1.0 - ln(tan(centerLatRad) + 1.0 / cos(centerLatRad)) / PI) / 2.0 * n).toInt()

    // Dispara carregamento assíncrono em fila controlada (máx. 3 conexões paralelas) quando a grade de tiles muda
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

        // 1º: Anel central 3x3 (prioridade máxima: começa pelo próprio tile central (0,0))
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

        // 2º: Tiles pais (zoom - 1) para fallback instantâneo durante zoom in/out
        if (tileZoom > 10) {
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
            -22.8950 to -43.1800,
            -22.9050 to -43.1680,
            -22.9200 to -43.1700,
            -22.9420 to -43.1780,
            -22.9480 to -43.1630,
            -22.9620 to -43.1650,
            -22.9715 to -43.1825,
            -22.9865 to -43.1900,
            -22.9865 to -43.2050,
            -22.9885 to -43.2260,
            -22.9995 to -43.2650
        )
    }

    val namedRoads = remember {
        listOf(
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
                name = "Túnel Rebouças",
                labelLat = -22.9460,
                labelLon = -43.2075,
                points = listOf(-22.9615 to -43.2095, -22.9460 to -43.2075, -22.9310 to -43.2060)
            ),
            NamedRoadVector(
                name = "Av. Presidente Vargas",
                labelLat = -22.9045,
                labelLon = -43.1910,
                points = listOf(-22.9015 to -43.1780, -22.9045 to -43.1910, -22.9095 to -43.2090)
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
            )
        )
    }

    val neighborhoods = remember {
        listOf(
            Triple("CENTRO", -22.9035, -43.1820),
            Triple("BOTAFOGO", -22.9510, -43.1860),
            Triple("COPACABANA", -22.9700, -43.1860),
            Triple("IPANEMA", -22.9840, -43.2040),
            Triple("LEBLON", -22.9845, -43.2215),
            Triple("LAGOA", -22.9710, -43.2105),
            Triple("JARDIM BOTÂNICO", -22.9650, -43.2230),
            Triple("HUMAITÁ", -22.9560, -43.1995),
            Triple("URCA", -22.9485, -43.1665),
            Triple("FLAMENGO", -22.9320, -43.1760),
            Triple("LARANJEIRAS", -22.9360, -43.1920),
            Triple("TIJUCA", -22.9240, -43.2320),
            Triple("MARACANÃ", -22.9125, -43.2300),
            Triple("GÁVEA", -22.9790, -43.2330)
        )
    }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .background(if (crisisMode) Color.Black else Color(0xFF0B1526))
            // Duplo-toque para dar zoom rápido na rua
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        onTransformGesture(0.0, 0.0, 1.85f)
                    }
                )
            }
            // Gestor customizado que consome 100% dos eventos de toque no mapa (impede o LazyColumn de roubar o arraste vertical ou a pinça!)
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
        // Garante invalidação imediata do DrawScope sempre que um novo tile chega na memória
        val renderTick = tileRenderVersion
        if (renderTick < 0) return@Canvas

        val w = size.width
        val h = size.height

        // Tamanho físico real do tile na tela do celular (256.dp escalado pelo zoom fracionário):
        // Garante que os nomes das ruas fiquem grandes e nítidos em telas FullHD/QHD!
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

        // 1. Camada de Tiles OpenStreetMap com Fallback Hierárquico (Parent/Child Tile Scaling) + Blueprint de Quadras
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
                            // Fallback 1: procura tile Pai (zoom menor já em memória) e recorta o sub-quadrante exato
                            var drawnFallback = false
                            for (deltaZ in 1..5) {
                                val parentZ = tileZoom - deltaZ
                                if (parentZ < 10) break
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

                            // Fallback 3: Enquanto o tile está baixando (ou se 100% sem internet numa região nova),
                            // desenha o reticulado urbano tático de quadras para que o mapa nunca fique escuro/vazio
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
        val gridAlpha = if (loadedTilesInView > 0 && showStreetTiles) 0.14f else 0.35f
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

        // 3. Cartografia Vetorial Offline do Rio de Janeiro (apenas quando a câmera está próxima à região para evitar Paths fora de tela)
        val isNearRioZonaSul = abs(centerLat - (-22.95)) < 0.28 && abs(centerLon - (-43.20)) < 0.28
        if (isNearRioZonaSul) {
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
                textSize = 11.5.sp.toPx()
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
                isAntiAlias = true
                setShadowLayer(6f, 0f, 1f, android.graphics.Color.BLACK)
            }

            namedRoads.forEach { road ->
                if (!showStreetTiles || loadedTilesInView == 0) {
                    val roadPath = Path().apply {
                        road.points.forEachIndexed { idx, (lat, lon) ->
                            val pt = toScreen(lat, lon)
                            if (idx == 0) moveTo(pt.x, pt.y) else lineTo(pt.x, pt.y)
                        }
                    }
                    drawPath(
                        path = roadPath,
                        color = Color(0xFF94A3B8).copy(alpha = 0.80f),
                        style = Stroke(width = 3.2.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
                if (zoomFloat >= 13.5f || !showStreetTiles || loadedTilesInView == 0) {
                    val lblPt = toScreen(road.labelLat, road.labelLon)
                    if (lblPt.x in 20f..(w - 20f) && lblPt.y in 20f..(h - 20f)) {
                        drawContext.canvas.nativeCanvas.drawText(road.name, lblPt.x, lblPt.y, roadLabelPaint)
                    }
                }
            }

            val neighborhoodPaint = Paint().apply {
                color = android.graphics.Color.argb(225, 241, 245, 249)
                textSize = if (zoomFloat >= 15f) 12.sp.toPx() else 10.sp.toPx()
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
                isAntiAlias = true
                setShadowLayer(6f, 0f, 1f, android.graphics.Color.BLACK)
            }
            neighborhoods.forEach { (name, lat, lon) ->
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
            movementTrail.forEach { (tLat, tLon) ->
                val pt = toScreen(tLat, tLon)
                if (pt.x in 0f..w && pt.y in 0f..h) {
                    drawCircle(
                        color = Color.White,
                        radius = 2.8.dp.toPx(),
                        center = pt
                    )
                }
            }
        }

        // 5. Vetor Direto de Rota Haversine entre Usuário e o POI Selecionado
        val userScreen = toScreen(userLat, userLon)
        if (focusedPoi != null) {
            val targetScreen = toScreen(focusedPoi.latitude, focusedPoi.longitude)
            val clampedTargetX = targetScreen.x.coerceIn(-w, 2 * w)
            val clampedTargetY = targetScreen.y.coerceIn(-h, 2 * h)
            val clampedUserX = userScreen.x.coerceIn(-w, 2 * w)
            val clampedUserY = userScreen.y.coerceIn(-h, 2 * h)
            drawLine(
                color = Color(0xFFFBBF24),
                start = Offset(clampedUserX, clampedUserY),
                end = Offset(clampedTargetX, clampedTargetY),
                strokeWidth = 3.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 10f), 0f)
            )
        }

        // 6. Marcadores Geográficos de todos os POIs do Banco Room
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
                val pinColor = when {
                    poi.type.name.contains("FAMILY") -> Color(0xFFF59E0B)
                    poi.type.name.contains("HOSPITAL") -> Color(0xFFEF4444)
                    else -> Color(0xFF10B981)
                }

                if (isFocused) {
                    drawCircle(
                        color = pinColor.copy(alpha = 0.38f),
                        radius = 19.dp.toPx(),
                        center = pt
                    )
                }
                drawCircle(
                    color = Color.Black,
                    radius = if (isFocused) 9.5.dp.toPx() else 7.5.dp.toPx(),
                    center = pt
                )
                drawCircle(
                    color = pinColor,
                    radius = if (isFocused) 7.5.dp.toPx() else 5.5.dp.toPx(),
                    center = pt
                )

                val shortLabel = poi.title.substringBefore(" -").take(22)
                drawContext.canvas.nativeCanvas.drawText(
                    shortLabel,
                    pt.x + 12.dp.toPx(),
                    pt.y + 4.dp.toPx(),
                    poiLabelPaint
                )
            }
        }

        // 7. Pino da Posição Atual do Usuário + Anel Tático + Cone de Direção + Nome da Rua Atual
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
                color = if (isLiveTracking) Color(0xFF10B981).copy(alpha = 0.30f) else Color(0xFF0284C7).copy(alpha = 0.30f),
                radius = if (userAccuracyMeters > 0f) userAccuracyMeters.coerceIn(18f, 42f).dp.toPx() else 22.dp.toPx(),
                center = userScreen
            )

            // Cone / Seta indicando para onde o usuário está se movendo
            val headingRad = Math.toRadians((userHeadingDegrees - 90f).toDouble())
            val tipLength = 24.dp.toPx()
            val tipX = userScreen.x + (cos(headingRad) * tipLength).toFloat()
            val tipY = userScreen.y + (sin(headingRad) * tipLength).toFloat()

            val leftWingRad = Math.toRadians((userHeadingDegrees - 90f - 28f).toDouble())
            val rightWingRad = Math.toRadians((userHeadingDegrees - 90f + 28f).toDouble())
            val wingLen = 12.dp.toPx()

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
                color = if (isLiveTracking) Color(0xFF34D399) else Color(0xFF38BDF8)
            )

            drawCircle(
                color = Color.White,
                radius = 8.5.dp.toPx(),
                center = userScreen
            )
            drawCircle(
                color = if (isLiveTracking) Color(0xFF10B981) else Color(0xFF0284C7),
                radius = 6.dp.toPx(),
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
                userScreen.y - 16.dp.toPx(),
                userStreetPaint
            )
        }
    }
}

/**
 * Remove pastas legadas (`osm_tiles`, `osm_tiles_v2`, `osm_tiles_v3`) que continham tiles "403 Access blocked"
 * ou tiles cinzas "Map Data Not Available" do ArcGIS em zoom alto, garantindo cache limpo em `osm_tiles_v4`.
 */
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

/**
 * Carrega um tile 256x256 do cache offline validado (`osm_tiles_v4`) ou faz o download
 * a partir de múltiplos servidores oficiais OpenStreetMap (OSM Oficial, OSM.de, OSM.fr)
 * que suportam até Zoom 19 no Brasil inteiro com todos os nomes de ruas.
 * Retorna `Pair(ImageBitmap, newlySavedToDisk)`.
 */
private fun loadOrDownloadTileWithStatus(
    context: Context,
    zoom: Int,
    x: Int,
    y: Int
): Pair<ImageBitmap, Boolean>? {
    return try {
        val tileFile = File(context.filesDir, "$TILE_CACHE_DIR_NAME/$zoom/$x/$y.png")
        // Nota: tiles válidos de oceano/lagoa no OSM têm 103 bytes! Rejeitamos apenas tamanhos conhecidos de erro (6725, 6672, 2049) ou vazios (< 60)
        if (tileFile.exists() && tileFile.length() >= 60L &&
            tileFile.length() != 6725L && tileFile.length() != 6672L && tileFile.length() != 2049L
        ) {
            val bmp = BitmapFactory.decodeFile(tileFile.absolutePath)
            if (bmp != null) return Pair(bmp.asImageBitmap(), false)
        }

        tileFile.parentFile?.mkdirs()

        val candidateUrls = listOf(
            "https://tile.openstreetmap.org/$zoom/$x/$y.png",
            "https://tile.openstreetmap.de/$zoom/$x/$y.png",
            "https://a.tile.openstreetmap.fr/osmfr/$zoom/$x/$y.png",
            "https://b.tile.openstreetmap.fr/osmfr/$zoom/$x/$y.png"
        )

        for (urlStr in candidateUrls) {
            try {
                val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 4000
                    readTimeout = 4000
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
                // Tenta o próximo espelho OSM em caso de falha momentânea
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

    String.format(Locale.US, "Coord %.4f, %.4f", lat, lon)
}

private suspend fun preloadRegionTiles(
    context: Context,
    centerLat: Double,
    centerLon: Double,
    zoom: Int,
    cache: MutableMap<String, ImageBitmap>,
    onEachTileLoaded: () -> Unit = {}
): Int = withContext(Dispatchers.IO) {
    var count = 0
    val baseZ = zoom.coerceIn(14, 18)
    val zoomsToCache = listOf((baseZ - 1).coerceAtLeast(13), baseZ, (baseZ + 1).coerceAtMost(18)).distinct()
    for (z in zoomsToCache) {
        val n = 2.0.pow(z.toDouble())
        val latRad = Math.toRadians(centerLat.coerceIn(-85.0, 85.0))
        val cx = ((centerLon + 180.0) / 360.0 * n).toInt()
        val cy = ((1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * n).toInt()
        val maxTile = n.toInt() - 1

        for (dx in -2..2) {
            for (dy in -2..2) {
                val tx = (cx + dx).coerceIn(0, maxTile)
                val ty = (cy + dy).coerceIn(0, maxTile)
                val key = "$z/$tx/$ty"
                val res = loadOrDownloadTileWithStatus(context, z, tx, ty)
                if (res != null) {
                    val (bmp, _) = res
                    withContext(Dispatchers.Main) {
                        cache[key] = bmp
                        onEachTileLoaded()
                    }
                    count++
                }
            }
        }
    }
    count
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
    crisisMode: Boolean,
    onClick: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val isFamily = nav.poiType.contains("Famili", ignoreCase = true)
    val isHospital = nav.poiType.contains("Hospital", ignoreCase = true)
    val isFire = nav.poiType.contains("Bombeiro", ignoreCase = true)

    val badgeColor = when {
        isFamily -> Color(0xFFFBBF24)
        isHospital -> Color(0xFFF87171)
        isFire -> Color(0xFF38BDF8)
        else -> Color(0xFF34D399)
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (crisisMode) Color.Black else if (isSelected) Color(0xFF17253B) else Color(0xFF111827)
        ),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(
            if (isSelected) 2.dp else 1.dp,
            if (isSelected) Color(0xFFFBBF24) else Color(0xFF334155)
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
                        text = "${nav.cardinalDetailed} (${nav.bearingDegrees.toInt()}°)",
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
                    color = Color(0xFFCBD5E1)
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = nav.distanceFormatted,
                        fontWeight = FontWeight.Black,
                        fontSize = 16.sp,
                        color = if (isFamily) Color(0xFFFBBF24) else Color.White
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
                if (nav.phone != null) {
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
