package com.example.ui.components

import android.content.Context
import android.os.Bundle
import android.util.Log
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

// Estilo tático com fonte raster OpenStreetMap + fallback escuro
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

    // Inicializa o MapLibre Singleton uma única vez
    LaunchedEffect(Unit) {
        try {
            MapLibre.getInstance(context)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao inicializar MapLibre", e)
        }
    }

    // Seleciona o ponto de foco (padrão é o Ponto de Encontro Familiar mais próximo)
    var selectedPoiId by remember { mutableStateOf<Long?>(null) }
    val focusedPoi = navList.find { it.poiId == selectedPoiId } ?: navList.firstOrNull()

    var mapLibreInstance by remember { mutableStateOf<MapLibreMap?>(null) }
    var isMapExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp)
            .testTag("offline_map_screen")
    ) {
        // --- 1. PAINEL BÚSSOLA & DISTÂNCIA DE EMERGÊNCIA ---
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
                                    val userPos = LatLng(state.currentLat, state.currentLon)
                                    map.animateCamera(CameraUpdateFactory.newLatLngZoom(userPos, 14.0))
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

        // --- 2. MAPA REAL MAPLIBRE NATIVE SDK COM SUPORTE OFFLINE ---
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
                val mapView = remember {
                    MapView(context).apply {
                        onCreate(null)
                    }
                }

                // Gerenciamento de ciclo de vida do MapView
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        when (event) {
                            Lifecycle.Event.ON_START -> mapView.onStart()
                            Lifecycle.Event.ON_RESUME -> mapView.onResume()
                            Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                            Lifecycle.Event.ON_STOP -> mapView.onStop()
                            Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                            else -> {}
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                        mapView.onDestroy()
                    }
                }

                AndroidView(
                    factory = { mapView },
                    modifier = Modifier.fillMaxSize()
                ) { view ->
                    view.getMapAsync { map ->
                        mapLibreInstance = map

                        // Configura Estilo (Local se existir arquivo .mbtiles / .pmtiles ou estilo tático embutido)
                        val localMbtiles = File(context.filesDir, "rio_janeiro.mbtiles")
                        val localStyle = File(context.filesDir, "style.json")

                        val styleBuilder = when {
                            localStyle.exists() -> Style.Builder().fromUri("file://${localStyle.absolutePath}")
                            localMbtiles.exists() -> Style.Builder().fromJson(OFFLINE_DARK_STYLE_JSON)
                            else -> Style.Builder().fromJson(OFFLINE_DARK_STYLE_JSON)
                        }

                        map.setStyle(styleBuilder) {
                            // Limpa e reinsere marcadores
                            map.clear()

                            // Marcador do Usuário (GPS Pulso)
                            val userLocation = LatLng(state.currentLat, state.currentLon)
                            map.addMarker(
                                MarkerOptions()
                                    .position(userLocation)
                                    .title("Minha Posição (GPS Pulso)")
                                    .snippet("Coordenadas: ${state.currentLat}, ${state.currentLon}")
                            )

                            // Marcadores dos POIs do banco
                            state.pois.forEach { poi ->
                                map.addMarker(
                                    MarkerOptions()
                                        .position(LatLng(poi.latitude, poi.longitude))
                                        .title(poi.title)
                                        .snippet("${poi.type.name} • ${poi.address}")
                                )
                            }

                            // Posiciona câmera inicial
                            val initialCamera = CameraPosition.Builder()
                                .target(userLocation)
                                .zoom(12.5)
                                .build()
                            map.cameraPosition = initialCamera
                        }
                    }
                }

                // Tag sobreposta MapLibre Native
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                ) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.8f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "MapLibre Native SDK • Vetores Offline",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Surface(
                        color = Color.Black.copy(alpha = 0.8f),
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

        // --- 3. LISTA DE PONTOS DE ENCONTRO E APOIO COM ZOOM INTERATIVO ---
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
                        // Anima câmera do mapa para o POI selecionado
                        val targetPoi = state.pois.find { it.id == nav.poiId }
                        if (targetPoi != null && mapLibreInstance != null) {
                            val targetLocation = LatLng(targetPoi.latitude, targetPoi.longitude)
                            mapLibreInstance?.animateCamera(
                                CameraUpdateFactory.newLatLngZoom(targetLocation, 14.5),
                                1000
                            )
                        }
                    }
                )
            }
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
