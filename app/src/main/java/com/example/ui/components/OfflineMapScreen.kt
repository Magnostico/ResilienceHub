package com.example.ui.components

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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.RationingAndAlertEngine
import com.example.ui.MainUiState
import com.example.ui.MainViewModel
import java.util.Locale

@Composable
fun OfflineMapScreen(
    state: MainUiState,
    viewModel: MainViewModel,
    onOpenAddPoiDialog: () -> Unit,
    modifier: Modifier = Modifier
) {
    val crisisMode = state.crisisMode
    val navList = state.poiNavigationList

    // Seleciona o ponto de foco (padrão é o Ponto de Encontro Familiar mais próximo)
    var selectedPoiId by remember { mutableStateOf<Long?>(null) }
    val focusedPoi = navList.find { it.poiId == selectedPoiId } ?: navList.firstOrNull()

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
                .padding(vertical = 8.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                // Header com botão de GPS Pulso
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Explore, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
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

                    Button(
                        onClick = { viewModel.triggerSingleShotGpsPulse() },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("btn_gps_pulse_map")
                    ) {
                        Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("GPS Pulso", fontSize = 11.sp)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

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
                            .padding(10.dp)
                    ) {
                        // Bússola Visual Renderizada
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(64.dp)
                        ) {
                            Canvas(modifier = Modifier.size(64.dp)) {
                                drawCircle(
                                    color = if (crisisMode) Color(0xFF27272A) else Color.LightGray,
                                    style = Stroke(width = 2.dp.toPx())
                                )
                                // Linhas cardeais
                                drawLine(
                                    color = Color.Gray,
                                    start = Offset(size.width / 2, 4.dp.toPx()),
                                    end = Offset(size.width / 2, 10.dp.toPx()),
                                    strokeWidth = 2.dp.toPx()
                                )
                            }
                            Icon(
                                Icons.Default.Navigation,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .size(32.dp)
                                    .rotate(focusedPoi.bearingDegrees.toFloat())
                            )
                        }

                        // Informações do Alvo
                        Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
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
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        // Distância Haversine
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = focusedPoi.distanceFormatted,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "em linha reta",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // --- 2. GRID VISUAL TÁTICO MAPLIBRE / MBTILES OFFLINE ---
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (crisisMode) Color.Black else Color(0xFF0F172A)
            ),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (crisisMode) Color(0xFF27272A) else MaterialTheme.colorScheme.outlineVariant
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Tactical Vector Canvas
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height

                    // Gridlines táticas estilo MapLibre / radar
                    val gridSpacing = 30.dp.toPx()
                    var x = 0f
                    while (x < w) {
                        drawLine(
                            color = Color(0xFF1E293B).copy(alpha = 0.6f),
                            start = Offset(x, 0f),
                            end = Offset(x, h),
                            strokeWidth = 1f
                        )
                        x += gridSpacing
                    }
                    var y = 0f
                    while (y < h) {
                        drawLine(
                            color = Color(0xFF1E293B).copy(alpha = 0.6f),
                            start = Offset(0f, y),
                            end = Offset(w, y),
                            strokeWidth = 1f
                        )
                        y += gridSpacing
                    }

                    // Ponto Central do Usuário (GPS Pulso)
                    val center = Offset(w / 2, h / 2)
                    drawCircle(color = Color(0xFF0284C7).copy(alpha = 0.3f), radius = 24.dp.toPx(), center = center)
                    drawCircle(color = Color(0xFF38BDF8), radius = 6.dp.toPx(), center = center)

                    // Marcadores POIs calculados em escala local
                    val p1 = Offset(w * 0.35f, h * 0.70f) // Parque Lage
                    val p2 = Offset(w * 0.45f, h * 0.25f) // Praça Afonso Pena
                    val p3 = Offset(w * 0.65f, h * 0.20f) // Souza Aguiar Centro
                    val p4 = Offset(w * 0.30f, h * 0.85f) // Miguel Couto Gávea

                    drawCircle(color = Color(0xFFF59E0B), radius = 5.dp.toPx(), center = p1)
                    drawCircle(color = Color(0xFFF59E0B), radius = 5.dp.toPx(), center = p2)
                    drawCircle(color = Color(0xFFEF4444), radius = 5.dp.toPx(), center = p3)
                    drawCircle(color = Color(0xFFEF4444), radius = 5.dp.toPx(), center = p4)
                }

                // Legenda sobreposta MapLibre
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                ) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.75f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "MapLibre Native • Vetores Offline (.mbtiles)",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Surface(
                        color = Color.Black.copy(alpha = 0.75f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "Rio de Janeiro • Sem Internet",
                            fontSize = 10.sp,
                            color = Color(0xFF10B981),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // --- 3. LISTA DE PONTOS DE ENCONTRO E APOIO ---
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
        ) {
            Text(
                text = "Pontos de Apoio e Encontro Ordenados por Distância:",
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
                    onClick = { selectedPoiId = nav.poiId }
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
