package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.RationingAndAlertEngine
import com.example.ui.MainUiState
import com.example.ui.MainViewModel
import java.util.Locale

@Composable
fun MeetingPointsScreen(
    state: MainUiState,
    viewModel: MainViewModel,
    onOpenAddPoiDialog: () -> Unit,
    modifier: Modifier = Modifier
) {
    val crisisMode = state.crisisMode

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("meeting_points_screen")
    ) {
        // --- Single-Shot GPS Telemetry Card ---
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (crisisMode) Color(0xFF080808) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            shape = RoundedCornerShape(12.dp),
            border = if (crisisMode) androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF27272A)) else null,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(
                                    if (state.isGpsActive) Color(0xFF10B981) else Color(0xFFEAB308),
                                    CircleShape
                                )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (state.isGpsActive) "GPS ON-DEMAND ATIVO (5s)" else "GPS EM STANDBY (Bateria Salva)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (state.isGpsActive) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    FilledTonalButton(
                        onClick = {
                            // On-demand single-shot GPS simulation (ou real location)
                            viewModel.triggerSingleShotGps(-22.9519, -43.1850)
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("btn_single_shot_gps")
                    ) {
                        Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Fixar GPS", fontSize = 11.sp)
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Posição Atual: Lat ${String.format(Locale.US, "%.4f", state.currentLat)}, Lon ${String.format(Locale.US, "%.4f", state.currentLon)} (Rio de Janeiro)",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Cálculo matemático 100% offline via fórmula geodésica de Haversine.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // --- POI List with Distance & Bearing ---
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 80.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(state.poiNavigationList, key = { it.poiId }) { nav ->
                PoiNavigationCard(nav = nav, crisisMode = crisisMode)
            }
        }
    }
}

@Composable
fun PoiNavigationCard(
    nav: RationingAndAlertEngine.PoiNavigationInfo,
    crisisMode: Boolean
) {
    val isFamilyMeeting = nav.poiType.contains("Famili", ignoreCase = true)

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (crisisMode) Color(0xFF080808) else MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isFamilyMeeting) MaterialTheme.colorScheme.primary else if (crisisMode) Color(0xFF27272A) else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("poi_card_${nav.poiId}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header: Type badge & Cardinal direction
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    color = if (isFamilyMeeting) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = nav.poiType,
                        color = if (isFamilyMeeting) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                // Bearing & Cardinal Badge
                Surface(
                    color = if (crisisMode) Color(0xFF161616) else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(6.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        0.5.dp,
                        MaterialTheme.colorScheme.primary
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            Icons.Default.Navigation,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(16.dp)
                                .rotate(nav.bearingDegrees.toFloat())
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${nav.cardinalDetailed} • ${nav.bearingDegrees.toInt()}°",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Title & Distance
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = nav.poiTitle,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )

                Text(
                    text = nav.distanceFormatted,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                    color = if (isFamilyMeeting) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Address
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(14.dp)
                        .padding(top = 2.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = nav.address,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Phone if available
            if (!nav.phone.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Phone,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = nav.phone,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
