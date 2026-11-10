package com.example.ui.components

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.MapPoiEntity
import com.example.domain.RationingAndAlertEngine
import com.example.ui.MainUiState
import com.example.ui.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.*

/**
 * Motor Cartográfico Híbrido 100% Kotlin/Compose (Zero dependências C++ / Zero Crash):
 * 1. Projeção Web Mercator real com suporte a Pan (arrastar) e Zoom (níveis 10 a 17).
 * 2. Cache automático de Tiles OpenStreetMap em disco local (filesDir/osm_tiles/{z}/{x}/{y}.png).
 * 3. Cartografia Vetorial Offline Embutida do Rio de Janeiro (Orla, Lagoa, Baía de Guanabara,
 *    Maciço da Tijuca, corredores de evacuação e bairros) garantindo mapa visível mesmo sem internet.
 */
@Composable
fun OfflineMapScreen(
    state: MainUiState,
    viewModel: MainViewModel,
    onOpenAddPoiDialog: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val crisisMode = state.crisisMode
    val navList = state.poiNavigationList

    var selectedPoiId by remember { mutableStateOf<Long?>(null) }
    val focusedPoi = navList.find { it.poiId == selectedPoiId } ?: navList.firstOrNull()
    val focusedPoiEntity = state.pois.find { it.id == focusedPoi?.poiId }

    // Estado da câmera do mapa (Web Mercator)
    var mapCenterLat by remember { mutableDoubleStateOf(state.currentLat) }
    var mapCenterLon by remember { mutableDoubleStateOf(state.currentLon) }
    var mapZoom by remember { mutableIntStateOf(13) }
    var isMapExpanded by remember { mutableStateOf(false) }
    var showStreetTiles by remember { mutableStateOf(true) }

    // Sincroniza quando o GPS Pulso atualiza a coordenada do usuário
    LaunchedEffect(state.currentLat, state.currentLon) {
        mapCenterLat = state.currentLat
        mapCenterLon = state.currentLon
    }

    // Cache em memória e status de download de tiles offline
    val tileBitmapCache = remember { mutableStateMapOf<String, ImageBitmap>() }
    var cachedTilesCount by remember {
        mutableIntStateOf(countCachedTilesOnDisk(context))
    }
    var isPreloadingTiles by remember { mutableStateOf(false) }
    var preloadStatusText by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp)
            .testTag("offline_map_screen")
    ) {
        // --- 1. PAINEL BÚSSOLA & DISTÂNCIA DE EMERGÊNCIA (100% OFFLINE) ---
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (crisisMode) Color.Black else MaterialTheme.colorScheme.surface
            ),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (crisisMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Explore,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Bússola & Mapa Offline",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                        Text(
                            text = state.gpsProviderInfo,
                            fontSize = 10.sp,
                            color = Color(0xFF38BDF8),
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Button(
                            onClick = {
                                viewModel.triggerSingleShotGpsPulse()
                                mapCenterLat = state.currentLat
                                mapCenterLon = state.currentLon
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.testTag("btn_gps_pulse_map")
                        ) {
                            Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("GPS Pulso", fontSize = 11.sp)
                        }

                        IconButton(
                            onClick = { isMapExpanded = !isMapExpanded },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                if (isMapExpanded) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                contentDescription = "Expandir Mapa",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Compass & Bearing Target Display
                if (focusedPoi != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (crisisMode) Color(0xFF0D0D0D) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(8.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(52.dp)
                        ) {
                            Canvas(modifier = Modifier.size(52.dp)) {
                                drawCircle(
                                    color = if (crisisMode) Color(0xFF27272A) else Color.LightGray,
                                    style = Stroke(width = 2.dp.toPx())
                                )
                                drawLine(
                                    color = Color(0xFFEF4444),
                                    start = Offset(size.width / 2, 3.dp.toPx()),
                                    end = Offset(size.width / 2, 9.dp.toPx()),
                                    strokeWidth = 2.dp.toPx()
                                )
                            }
                            Icon(
                                Icons.Default.Navigation,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .size(26.dp)
                                    .rotate(focusedPoi.bearingDegrees.toFloat())
                            )
                        }

                        Column(
                            modifier = Modifier
                                .padding(start = 10.dp)
                                .weight(1f)
                        ) {
                            Text(
                                text = "Rumo ao Alvo Selecionado:",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = focusedPoi.poiTitle,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                maxLines = 1,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            val walkMinutes = (focusedPoi.distanceMeters / 75.0).roundToInt().coerceAtLeast(1)
                            Text(
                                text = "${focusedPoi.cardinalDetailed} • ${focusedPoi.bearingDegrees.toInt()}° (~$walkMinutes min a pé)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = focusedPoi.distanceFormatted,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Haversine",
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // --- 2. MAPA INTERATIVO VETORIAL & RASTER OFFLINE (100% KOTLIN) ---
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (crisisMode) Color.Black else Color(0xFF070B14)
            ),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(
                1.5.dp,
                if (crisisMode) MaterialTheme.colorScheme.primary else Color(0xFF0284C7)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(if (isMapExpanded) 340.dp else 210.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
            ) {
                InteractiveOfflineMapCanvas(
                    context = context,
                    centerLat = mapCenterLat,
                    centerLon = mapCenterLon,
                    zoom = mapZoom,
                    userLat = state.currentLat,
                    userLon = state.currentLon,
                    pois = state.pois,
                    focusedPoi = focusedPoiEntity,
                    crisisMode = crisisMode,
                    showStreetTiles = showStreetTiles,
                    tileBitmapCache = tileBitmapCache,
                    onTileCached = { cachedTilesCount = countCachedTilesOnDisk(context) },
                    onPanLatLon = { dLat, dLon ->
                        mapCenterLat = (mapCenterLat + dLat).coerceIn(-23.15, -22.70)
                        mapCenterLon = (mapCenterLon + dLon).coerceIn(-43.80, -42.95)
                    }
                )

                // Barra Superior do Mapa: Status Offline + Botão Baixar Região
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                ) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.82f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .background(Color(0xFF10B981), CircleShape)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Mapa RJ • Zoom $mapZoom • $cachedTilesCount tiles salvos",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        // Alternar Camada Rua / Vetor Tático
                        Surface(
                            color = Color.Black.copy(alpha = 0.82f),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.clickable { showStreetTiles = !showStreetTiles }
                        ) {
                            Text(
                                text = if (showStreetTiles) "Ruas + Vetor" else "Só Vetor OLED",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF38BDF8),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }

                        // Pré-baixar tiles da região atual para uso 100% offline
                        Surface(
                            color = Color(0xFF0284C7).copy(alpha = 0.9f),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.clickable(enabled = !isPreloadingTiles) {
                                scope.launch {
                                    isPreloadingTiles = true
                                    preloadStatusText = "Baixando mapa local..."
                                    val downloaded = preloadRegionTiles(
                                        context = context,
                                        centerLat = mapCenterLat,
                                        centerLon = mapCenterLon,
                                        zoom = mapZoom,
                                        cache = tileBitmapCache
                                    )
                                    cachedTilesCount = countCachedTilesOnDisk(context)
                                    preloadStatusText = if (downloaded > 0) "✓ +$downloaded blocos salvos offline" else "✓ Região em cache"
                                    isPreloadingTiles = false
                                }
                            }
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

                // Controles Flutuantes de Zoom (+ / - / Centralizar GPS) à direita
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 8.dp)
                ) {
                    SmallFloatingButton(
                        icon = Icons.Default.Add,
                        contentDescription = "Mais Zoom",
                        onClick = { if (mapZoom < 17) mapZoom++ }
                    )
                    SmallFloatingButton(
                        icon = Icons.Default.Remove,
                        contentDescription = "Menos Zoom",
                        onClick = { if (mapZoom > 10) mapZoom-- }
                    )
                    SmallFloatingButton(
                        icon = Icons.Default.MyLocation,
                        contentDescription = "Centralizar no Usuário",
                        tint = Color(0xFF38BDF8),
                        onClick = {
                            mapCenterLat = state.currentLat
                            mapCenterLon = state.currentLon
                            mapZoom = 14
                        }
                    )
                }

                // Rodapé do Mapa: Coordenadas e Legenda
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.72f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = preloadStatusText ?: String.format(Locale.US, "Centro: %.4f, %.4f (Arraste para mover)", mapCenterLat, mapCenterLon),
                        fontSize = 9.sp,
                        color = Color(0xFFCBD5E1)
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LegendDot(color = Color(0xFF38BDF8), label = "Você")
                        LegendDot(color = Color(0xFFF59E0B), label = "Encontro")
                        LegendDot(color = Color(0xFFEF4444), label = "Hospital/BM")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // --- 3. LISTA DE PONTOS DE ENCONTRO E APOIO COM FOCO NO MAPA ---
        Text(
            text = "Pontos Seguros e Abrigos (Toque para focar no mapa):",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 80.dp),
            modifier = Modifier.fillMaxSize()
        ) {
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
                            mapCenterLat = targetPoi.latitude
                            mapCenterLon = targetPoi.longitude
                            if (mapZoom < 14) mapZoom = 14
                        }
                    }
                )
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
        color = Color.Black.copy(alpha = 0.82f),
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
        modifier = Modifier
            .size(32.dp)
            .clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(color, CircleShape)
        )
        Spacer(modifier = Modifier.width(3.dp))
        Text(text = label, fontSize = 9.sp, color = Color.LightGray)
    }
}

/**
 * Renderizador Cartográfico Web Mercator em Compose Canvas:
 * Combina tiles OpenStreetMap cacheados localmente com vetores geográficos do Rio de Janeiro.
 */
@Composable
private fun InteractiveOfflineMapCanvas(
    context: Context,
    centerLat: Double,
    centerLon: Double,
    zoom: Int,
    userLat: Double,
    userLon: Double,
    pois: List<MapPoiEntity>,
    focusedPoi: MapPoiEntity?,
    crisisMode: Boolean,
    showStreetTiles: Boolean,
    tileBitmapCache: MutableMap<String, ImageBitmap>,
    onTileCached: () -> Unit,
    onPanLatLon: (dLat: Double, dLon: Double) -> Unit
) {
    val tileSize = 256.0
    val n = 2.0.pow(zoom.toDouble())

    // Converte (lat, lon) em coordenada global de pixel Web Mercator no zoom atual
    fun latLonToWorldPx(lat: Double, lon: Double): Pair<Double, Double> {
        val clampedLat = lat.coerceIn(-85.0511, 85.0511)
        val x = (lon + 180.0) / 360.0 * n * tileSize
        val latRad = Math.toRadians(clampedLat)
        val y = (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * n * tileSize
        return Pair(x, y)
    }

    val (centerWorldX, centerWorldY) = latLonToWorldPx(centerLat, centerLon)

    // Dispara carregamento assíncrono dos tiles visíveis ao redor do centro
    LaunchedEffect(centerLat, centerLon, zoom, showStreetTiles) {
        if (!showStreetTiles) return@LaunchedEffect
        val centerTileX = (centerWorldX / tileSize).toInt()
        val centerTileY = (centerWorldY / tileSize).toInt()
        val maxTile = n.toInt() - 1

        for (dx in -2..2) {
            for (dy in -1..1) {
                val tx = (centerTileX + dx).coerceIn(0, maxTile)
                val ty = (centerTileY + dy).coerceIn(0, maxTile)
                val key = "$zoom/$tx/$ty"
                if (!tileBitmapCache.containsKey(key)) {
                    launch(Dispatchers.IO) {
                        val bmp = loadOrDownloadTile(context, zoom, tx, ty)
                        if (bmp != null) {
                            withContext(Dispatchers.Main) {
                                tileBitmapCache[key] = bmp
                                onTileCached()
                            }
                        }
                    }
                }
            }
        }
    }

    // Vetores Geográficos Reais do Rio de Janeiro (Orla, Lagoa, Vias de Evacuação e Bairros)
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
            -22.8950 to -43.1800, // Porto / Mauá
            -22.9050 to -43.1680, // Praça XV / Santos Dumont
            -22.9200 to -43.1700, // Glória / Flamengo
            -22.9420 to -43.1780, // Enseada de Botafogo
            -22.9480 to -43.1630, // Urca / Pão de Açúcar
            -22.9620 to -43.1650, // Leme
            -22.9715 to -43.1825, // Copacabana
            -22.9865 to -43.1900, // Arpoador
            -22.9865 to -43.2050, // Ipanema
            -22.9885 to -43.2260, // Leblon
            -22.9995 to -43.2650  // São Conrado
        )
    }

    val arterialRoads = remember {
        listOf(
            // Aterro do Flamengo -> Botafogo -> Túnel Novo -> Copacabana
            listOf(
                -22.9100 to -43.1720,
                -22.9300 to -43.1735,
                -22.9460 to -43.1825,
                -22.9650 to -43.1800,
                -22.9830 to -43.1920
            ),
            // Corredor Túnel Rebouças (Lagoa <-> Rio Comprido / Tijuca / Centro)
            listOf(
                -22.9780 to -43.2150,
                -22.9610 to -43.2090,
                -22.9310 to -43.2060,
                -22.9120 to -43.2100
            ),
            // Av. Presidente Vargas -> Maracanã -> Tijuca
            listOf(
                -22.9015 to -43.1780,
                -22.9070 to -43.2050,
                -22.9130 to -43.2280,
                -22.9245 to -43.2325
            ),
            // Jardim Botânico -> Gávea -> Humaitá -> Laranjeiras
            listOf(
                -22.9760 to -43.2290,
                -22.9665 to -43.2230,
                -22.9560 to -43.1990,
                -22.9350 to -43.1880
            )
        )
    }

    val neighborhoods = remember {
        listOf(
            Triple("CENTRO", -22.9035, -43.1820),
            Triple("BOTAFOGO", -22.9510, -43.1860),
            Triple("COPACABANA", -22.9700, -43.1860),
            Triple("IPANEMA", -22.9840, -43.2040),
            Triple("LAGOA", -22.9710, -43.2105),
            Triple("J. BOTÂNICO", -22.9650, -43.2230),
            Triple("FLAMENGO", -22.9320, -43.1760),
            Triple("TIJUCA", -22.9240, -43.2320),
            Triple("GÁVEA", -22.9790, -43.2330),
            Triple("LARANJEIRAS", -22.9360, -43.1920)
        )
    }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .background(if (crisisMode) Color.Black else Color(0xFF08101E))
            .pointerInput(zoom) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    val worldScale = 2.0.pow(zoom.toDouble()) * 256.0
                    val dLon = -(dragAmount.x / worldScale) * 360.0
                    val dLat = (dragAmount.y / worldScale) * 360.0 * cos(Math.toRadians(centerLat))
                    onPanLatLon(dLat, dLon)
                }
            }
    ) {
        val w = size.width
        val h = size.height

        fun toScreen(lat: Double, lon: Double): Offset {
            val (wx, wy) = latLonToWorldPx(lat, lon)
            val sx = (wx - centerWorldX + w / 2.0).toFloat()
            val sy = (wy - centerWorldY + h / 2.0).toFloat()
            return Offset(sx, sy)
        }

        // 1. Camada de Tiles OpenStreetMap (se habilitada e disponível no cache)
        if (showStreetTiles) {
            val centerTileX = (centerWorldX / tileSize).toInt()
            val centerTileY = (centerWorldY / tileSize).toInt()
            val maxTile = n.toInt() - 1

            for (dx in -2..2) {
                for (dy in -2..2) {
                    val tx = centerTileX + dx
                    val ty = centerTileY + dy
                    if (tx in 0..maxTile && ty in 0..maxTile) {
                        val key = "$zoom/$tx/$ty"
                        val bmp = tileBitmapCache[key]
                        if (bmp != null) {
                            val tileScreenX = (tx * tileSize - centerWorldX + w / 2.0).roundToInt()
                            val tileScreenY = (ty * tileSize - centerWorldY + h / 2.0).roundToInt()
                            drawImage(
                                image = bmp,
                                dstOffset = IntOffset(tileScreenX, tileScreenY),
                                dstSize = IntSize(tileSize.roundToInt() + 1, tileSize.roundToInt() + 1),
                                alpha = if (crisisMode) 0.45f else 0.78f
                            )
                        }
                    }
                }
            }
        }

        // 2. Grade Cartográfica Tática de Coordenadas
        val gridStep = 64.dp.toPx()
        var gx = (w / 2f) % gridStep
        while (gx < w) {
            drawLine(
                color = Color(0xFF1E293B).copy(alpha = 0.45f),
                start = Offset(gx, 0f),
                end = Offset(gx, h),
                strokeWidth = 1f
            )
            gx += gridStep
        }
        var gy = (h / 2f) % gridStep
        while (gy < h) {
            drawLine(
                color = Color(0xFF1E293B).copy(alpha = 0.45f),
                start = Offset(0f, gy),
                end = Offset(w, gy),
                strokeWidth = 1f
            )
            gy += gridStep
        }

        // 3. Cartografia Vetorial Offline do Rio de Janeiro (Lagoa Rodrigo de Freitas)
        val lagoaPath = Path().apply {
            lagoaPolygon.forEachIndexed { idx, (lat, lon) ->
                val pt = toScreen(lat, lon)
                if (idx == 0) moveTo(pt.x, pt.y) else lineTo(pt.x, pt.y)
            }
            close()
        }
        drawPath(
            path = lagoaPath,
            color = Color(0xFF0284C7).copy(alpha = 0.30f)
        )
        drawPath(
            path = lagoaPath,
            color = Color(0xFF38BDF8).copy(alpha = 0.65f),
            style = Stroke(width = 1.5.dp.toPx())
        )

        // Linha Costeira / Orla do Rio de Janeiro
        val coastPath = Path().apply {
            coastlineOrla.forEachIndexed { idx, (lat, lon) ->
                val pt = toScreen(lat, lon)
                if (idx == 0) moveTo(pt.x, pt.y) else lineTo(pt.x, pt.y)
            }
        }
        drawPath(
            path = coastPath,
            color = Color(0xFF0EA5E9).copy(alpha = 0.65f),
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // Corredores Viários / Rotas de Evacuação Principais
        arterialRoads.forEach { road ->
            val roadPath = Path().apply {
                road.forEachIndexed { idx, (lat, lon) ->
                    val pt = toScreen(lat, lon)
                    if (idx == 0) moveTo(pt.x, pt.y) else lineTo(pt.x, pt.y)
                }
            }
            drawPath(
                path = roadPath,
                color = Color(0xFF64748B).copy(alpha = 0.6f),
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        // Rótulos de Bairros do Rio de Janeiro
        val labelPaint = Paint().apply {
            color = android.graphics.Color.argb(170, 148, 163, 184)
            textSize = 9.sp.toPx()
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        neighborhoods.forEach { (name, lat, lon) ->
            val pt = toScreen(lat, lon)
            if (pt.x in 0f..w && pt.y in 0f..h) {
                drawContext.canvas.nativeCanvas.drawText(name, pt.x, pt.y, labelPaint)
            }
        }

        // 4. Vetor Direto de Rota Haversine entre Usuário e o POI Selecionado
        val userScreen = toScreen(userLat, userLon)
        if (focusedPoi != null) {
            val targetScreen = toScreen(focusedPoi.latitude, focusedPoi.longitude)
            drawLine(
                color = Color(0xFFF59E0B),
                start = userScreen,
                end = targetScreen,
                strokeWidth = 2.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f), 0f)
            )
        }

        // 5. Marcadores Geográficos de todos os POIs do Banco Room
        val poiLabelPaint = Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 10.sp.toPx()
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
            setShadowLayer(4f, 0f, 1f, android.graphics.Color.BLACK)
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
                        color = pinColor.copy(alpha = 0.35f),
                        radius = 16.dp.toPx(),
                        center = pt
                    )
                }
                drawCircle(
                    color = Color.Black,
                    radius = if (isFocused) 8.dp.toPx() else 6.dp.toPx(),
                    center = pt
                )
                drawCircle(
                    color = pinColor,
                    radius = if (isFocused) 6.5.dp.toPx() else 4.5.dp.toPx(),
                    center = pt
                )

                val shortLabel = poi.title.substringBefore(" -").take(18)
                drawContext.canvas.nativeCanvas.drawText(
                    shortLabel,
                    pt.x + 10.dp.toPx(),
                    pt.y + 4.dp.toPx(),
                    poiLabelPaint
                )
            }
        }

        // 6. Pino da Posição Atual do Usuário (GPS Pulso)
        if (userScreen.x in -50f..(w + 50f) && userScreen.y in -50f..(h + 50f)) {
            drawCircle(
                color = Color(0xFF0284C7).copy(alpha = 0.30f),
                radius = 20.dp.toPx(),
                center = userScreen
            )
            drawCircle(
                color = Color.White,
                radius = 7.dp.toPx(),
                center = userScreen
            )
            drawCircle(
                color = Color(0xFF38BDF8),
                radius = 5.dp.toPx(),
                center = userScreen
            )
        }
    }
}

/**
 * Carrega um tile 256x256 do cache offline no disco (filesDir/osm_tiles) ou faz o download
 * e grava em disco para uso 100% offline nas próximas consultas.
 */
private fun loadOrDownloadTile(
    context: Context,
    zoom: Int,
    x: Int,
    y: Int
): ImageBitmap? {
    return try {
        val tileFile = File(context.filesDir, "osm_tiles/$zoom/$x/$y.png")
        if (tileFile.exists() && tileFile.length() > 100) {
            val bmp = BitmapFactory.decodeFile(tileFile.absolutePath)
            if (bmp != null) return bmp.asImageBitmap()
        }

        tileFile.parentFile?.mkdirs()
        val url = URL("https://tile.openstreetmap.org/$zoom/$x/$y.png")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 4000
            readTimeout = 4000
            setRequestProperty("User-Agent", "ResilienceHub-CivilPreparedness/1.0")
        }

        if (conn.responseCode == 200) {
            val bytes = conn.inputStream.use { it.readBytes() }
            if (bytes.isNotEmpty()) {
                FileOutputStream(tileFile).use { it.write(bytes) }
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                return bmp?.asImageBitmap()
            }
        }
        null
    } catch (_: Exception) {
        // Sem internet no momento: opera silenciosamente com a camada vetorial offline e tiles já salvos
        null
    }
}

private suspend fun preloadRegionTiles(
    context: Context,
    centerLat: Double,
    centerLon: Double,
    zoom: Int,
    cache: MutableMap<String, ImageBitmap>
): Int = withContext(Dispatchers.IO) {
    var count = 0
    val zoomsToCache = listOf(zoom, (zoom + 1).coerceAtMost(16))
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
                val bmp = loadOrDownloadTile(context, z, tx, ty)
                if (bmp != null) {
                    withContext(Dispatchers.Main) {
                        cache[key] = bmp
                    }
                    count++
                }
            }
        }
    }
    count
}

private fun countCachedTilesOnDisk(context: Context): Int {
    val root = File(context.filesDir, "osm_tiles")
    if (!root.exists()) return 0
    return root.walkTopDown().count { it.isFile && it.extension == "png" }
}

@Composable
fun PoiOfflineCard(
    nav: RationingAndAlertEngine.PoiNavigationInfo,
    isSelected: Boolean,
    crisisMode: Boolean,
    onClick: () -> Unit
) {
    val isFamily = nav.poiType.contains("Famili", ignoreCase = true)
    val isHospital = nav.poiType.contains("Hospital", ignoreCase = true)
    val isFire = nav.poiType.contains("Bombeiro", ignoreCase = true)

    val badgeColor = when {
        isFamily -> Color(0xFFF59E0B)
        isHospital -> Color(0xFFEF4444)
        isFire -> Color(0xFF0284C7)
        else -> Color(0xFF10B981)
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (crisisMode) Color.Black else MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(
            if (isSelected) 2.dp else 1.dp,
            if (isSelected) MaterialTheme.colorScheme.primary else if (crisisMode) Color(0xFF27272A) else MaterialTheme.colorScheme.outlineVariant
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
                    .size(36.dp)
                    .background(badgeColor.copy(alpha = 0.15f), CircleShape)
            ) {
                Icon(
                    Icons.Default.Navigation,
                    contentDescription = null,
                    tint = badgeColor,
                    modifier = Modifier
                        .size(20.dp)
                        .rotate(nav.bearingDegrees.toFloat())
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = badgeColor.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = nav.poiType,
                            color = badgeColor,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${nav.cardinalDetailed} (${nav.bearingDegrees.toInt()}°)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = nav.poiTitle,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = nav.address,
                    fontSize = 11.sp,
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = nav.distanceFormatted,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp,
                    color = if (isFamily) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                if (nav.phone != null) {
                    Text(
                        text = nav.phone,
                        fontSize = 10.sp,
                        color = Color(0xFF0284C7),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
