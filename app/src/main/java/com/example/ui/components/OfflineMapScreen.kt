package com.example.ui.components

import android.content.Context
import android.util.Log
import android.view.View
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.domain.RationingAndAlertEngine
import com.example.ui.MainUiState
import com.example.ui.MainViewModel
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import java.io.File

private const val TAG = "OfflineMapScreen"

// Estilo tático escuro embutido para renderização offline
private val OFFLINE_DARK_STYLE_JSON = """
{
  "version": 8,
  "name": "ResilienceHub Tactical Offline",
  "sources": {
    "open-street-map": {
      "type": "raster",
      "tiles": [
        "https://tile.openstreetmap.org/{z}/{x}/{y}.png"
      ],
      "tileSize": 256,
      "attribution": "© OpenStreetMap contributors"
    }
  },
  "layers": [
    {
      "id": "background-crisis",
      "type": "background",
      "paint": {
        "background-color": "#090d16"
      }
    },
    {
      "id": "osm-raster-layer",
      "type": "raster",
      "source": "open-street-map",
      "minzoom": 0,
      "maxzoom": 19,
      "paint": {
        "raster-opacity": 0.85,
        "raster-contrast": 0.2
      }
    }
  ]
}
""".trimIndent()

@Composable
fun OfflineMapScreen(
    state: MainUiState,
    viewModel: MainViewModel,
    onOpenAddPoiDialog: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val crisisMode = state.crisisMode
    val navList = state.poiNavigationList

    // Inicialização síncrona segura do MapLibre antes de renderizar qualquer View
    val isMapLibreReady = remember {
        try {
            MapLibre.getInstance(context)
            true
        } catch (t: Throwable) {
            Log.e(TAG, "Falha ao inicializar MapLibre nativo", t)
            false
        }
    }

    var selectedPoiId by remember { mutableStateOf<Long?>(null) }
    val focusedPoi = navList.find { it.poiId == selectedPoiId } ?: navList.firstOrNull()

    var mapLibreInstance by remember { mutableStateOf<MapLibreMap?>(null) }
    var activeMapView by remember { mutableStateOf<MapView?>(null) }
    var mapInitError by remember { mutableStateOf<String?>(if (!isMapLibreReady) "Biblioteca nativa MapLibre indisponível neste aparelho" else null) }
    var isMapExpanded by remember { mutableStateOf(false) }

    // Gerenciamento seguro do ciclo de vida do MapView
    DisposableEffect(lifecycleOwner, activeMapView) {
        val mv = activeMapView
        if (mv == null) {
            onDispose { }
        } else {
            val observer = LifecycleEventObserver { _, event ->
                try {
                    when (event) {
                        Lifecycle.Event.ON_START -> mv.onStart()
                        Lifecycle.Event.ON_RESUME -> mv.onResume()
                        Lifecycle.Event.ON_PAUSE -> mv.onPause()
                        Lifecycle.Event.ON_STOP -> mv.onStop()
                        Lifecycle.Event.ON_DESTROY -> mv.onDestroy()
                        else -> {}
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Exceção no ciclo de vida MapView: $event", e)
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                try {
                    mv.onDestroy()
                } catch (e: Exception) {
                    Log.e(TAG, "Erro ao destruir MapView", e)
                }
            }
        }
    }

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
                // Header com botão de GPS Pulso
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Explore,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Bússola de Emergência Offline", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        Text(
                            text = state.gpsProviderInfo,
                            fontSize = 10.sp,
                            color = Color(0xFF0284C7),
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(
                            onClick = {
                                viewModel.triggerSingleShotGpsPulse()
                                mapLibreInstance?.let { map ->
                                    try {
                                        val userPos = LatLng(state.currentLat, state.currentLon)
                                        map.animateCamera(CameraUpdateFactory.newLatLngZoom(userPos, 14.0))
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Erro ao animar câmera para GPS", e)
                                    }
                                }
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
                        // Bússola Visual Renderizada
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(54.dp)
                        ) {
                            Canvas(modifier = Modifier.size(54.dp)) {
                                drawCircle(
                                    color = if (crisisMode) Color(0xFF27272A) else Color.LightGray,
                                    style = Stroke(width = 2.dp.toPx())
                                )
                                drawLine(
                                    color = Color.Gray,
                                    start = Offset(size.width / 2, 4.dp.toPx()),
                                    end = Offset(size.width / 2, 8.dp.toPx()),
                                    strokeWidth = 2.dp.toPx()
                                )
                            }
                            Icon(
                                Icons.Default.Navigation,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .size(28.dp)
                                    .rotate(focusedPoi.bearingDegrees.toFloat())
                            )
                        }

                        // Informações do Alvo
                        Column(modifier = Modifier.padding(start = 10.dp).weight(1f)) {
                            Text(
                                text = "Rumo ao Alvo:",
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
                            Text(
                                text = "${focusedPoi.cardinalDetailed} • Azimute ${focusedPoi.bearingDegrees.toInt()}°",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        // Distância Haversine
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = focusedPoi.distanceFormatted,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "linha reta",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // --- 2. MAPA OFFLINE REAL (À PROVA DE FALHAS / CRASH-PROOF) ---
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (crisisMode) Color.Black else Color(0xFF0F172A)
            ),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(
                1.5.dp,
                if (crisisMode) MaterialTheme.colorScheme.primary else Color(0xFF0284C7)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(if (isMapExpanded) 320.dp else 190.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (mapInitError == null) {
                    // Tenta renderizar o MapView real do MapLibre Native
                    AndroidView(
                        factory = { ctx ->
                            try {
                                MapLibre.getInstance(ctx)
                                val mv = MapView(ctx)
                                mv.onCreate(null)
                                mv.getMapAsync { map ->
                                    mapLibreInstance = map
                                    try {
                                        val localMbtiles = File(ctx.filesDir, "rio_janeiro.mbtiles")
                                        val localStyle = File(ctx.filesDir, "style.json")

                                        val styleBuilder = when {
                                            localStyle.exists() -> Style.Builder().fromUri("file://${localStyle.absolutePath}")
                                            localMbtiles.exists() -> Style.Builder().fromJson(OFFLINE_DARK_STYLE_JSON)
                                            else -> Style.Builder().fromJson(OFFLINE_DARK_STYLE_JSON)
                                        }

                                        map.setStyle(styleBuilder) {
                                            try {
                                                map.clear()

                                                // Posição do Usuário (GPS Pulso)
                                                val userLocation = LatLng(state.currentLat, state.currentLon)
                                                map.addMarker(
                                                    MarkerOptions()
                                                        .position(userLocation)
                                                        .title("Minha Posição")
                                                        .snippet("GPS Pulso: ${state.currentLat}, ${state.currentLon}")
                                                )

                                                // Marcadores dos POIs salvos no Room
                                                state.pois.forEach { poi ->
                                                    map.addMarker(
                                                        MarkerOptions()
                                                            .position(LatLng(poi.latitude, poi.longitude))
                                                            .title(poi.title)
                                                            .snippet("${poi.type.name} • ${poi.address}")
                                                    )
                                                }

                                                val initialCamera = CameraPosition.Builder()
                                                    .target(userLocation)
                                                    .zoom(12.5)
                                                    .build()
                                                map.cameraPosition = initialCamera
                                            } catch (e: Exception) {
                                                Log.e(TAG, "Erro ao popular marcadores no mapa", e)
                                            }
                                        }
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Erro ao configurar estilo no MapLibre", e)
                                    }
                                }
                                activeMapView = mv
                                mv
                            } catch (t: Throwable) {
                                Log.e(TAG, "Exceção crítica ao criar MapView", t)
                                mapInitError = t.localizedMessage ?: "Erro na GPU/OpenGL do dispositivo"
                                View(ctx)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Fallback tático elegante se a aceleração gráfica nativa falhar ou estiver em inicialização
                if (mapInitError != null) {
                    TacticalRadarFallback(
                        currentLat = state.currentLat,
                        currentLon = state.currentLon,
                        navList = navList,
                        errorMessage = mapInitError
                    )
                }

                // Badge sobreposta de status
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                ) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.85f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = if (mapInitError == null) "MapLibre Native SDK • Vetores Offline" else "Modo Tático Vetorial Ativo",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Surface(
                        color = Color.Black.copy(alpha = 0.85f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "Rio de Janeiro • 100% Offline",
                            fontSize = 10.sp,
                            color = Color(0xFF10B981),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // --- 3. LISTA DE PONTOS DE ENCONTRO E APOIO COM FOCO INTERATIVO ---
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp)
        ) {
            Text(
                text = "Pontos de Apoio e Encontro (Toque para focar no mapa):",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

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
                        if (targetPoi != null && mapLibreInstance != null) {
                            try {
                                val targetLocation = LatLng(targetPoi.latitude, targetPoi.longitude)
                                mapLibreInstance?.animateCamera(
                                    CameraUpdateFactory.newLatLngZoom(targetLocation, 14.5),
                                    1000
                                )
                            } catch (e: Exception) {
                                Log.e(TAG, "Erro ao focar POI no mapa", e)
                            }
                        }
                    }
                )
            }
        }
    }
}

/**
 * Radar Tático Vetorial: garante que mesmo em dispositivos com falha de OpenGL/Drivers
 * ou ausência de sinal de mapa, o usuário tenha visualização tática de suas coordenadas e alvos.
 */
@Composable
private fun TacticalRadarFallback(
    currentLat: Double,
    currentLon: Double,
    navList: List<RationingAndAlertEngine.PoiNavigationInfo>,
    errorMessage: String?
) {
    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF090D16))) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val center = Offset(w / 2, h / 2)

            // Círculos concêntricos do radar tático
            drawCircle(Color(0xFF1E293B), radius = h * 0.45f, center = center, style = Stroke(1f))
            drawCircle(Color(0xFF1E293B), radius = h * 0.30f, center = center, style = Stroke(1f))
            drawCircle(Color(0xFF1E293B), radius = h * 0.15f, center = center, style = Stroke(1f))

            // Eixos cardeais N-S e L-O
            drawLine(Color(0xFF1E293B), Offset(w / 2, 0f), Offset(w / 2, h), 1f)
            drawLine(Color(0xFF1E293B), Offset(0f, h / 2), Offset(w, h / 2), 1f)

            // Posição central do usuário
            drawCircle(Color(0xFF0284C7).copy(alpha = 0.3f), radius = 18.dp.toPx(), center = center)
            drawCircle(Color(0xFF38BDF8), radius = 5.dp.toPx(), center = center)

            // Plota os POIs ao redor do radar baseados no azimute real
            navList.take(5).forEachIndexed { index, poi ->
                val angleRad = Math.toRadians(poi.bearingDegrees - 90.0)
                val distNormalized = (poi.distanceMeters / 15000.0).coerceIn(0.2, 0.9)
                val r = (h * 0.45f * distNormalized).toFloat()

                val px = center.x + r * kotlin.math.cos(angleRad).toFloat()
                val py = center.y + r * kotlin.math.sin(angleRad).toFloat()

                val poiColor = if (poi.poiType.contains("Famili", true)) Color(0xFFF59E0B) else Color(0xFFEF4444)
                drawCircle(poiColor, radius = 5.dp.toPx(), center = Offset(px, py))
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp)
        ) {
            Text(
                text = "GPS: %.4f, %.4f".format(currentLat, currentLon),
                fontSize = 10.sp,
                color = Color(0xFF94A3B8),
                fontWeight = FontWeight.Bold
            )
        }
    }
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
            // Seta de Azimute
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
