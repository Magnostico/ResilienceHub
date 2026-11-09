package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.MainUiState
import com.example.ui.MainViewModel

@Composable
fun HardwareToolsScreen(
    state: MainUiState,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val crisisMode = state.crisisMode

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("hardware_tools_screen")
    ) {
        // --- Header ---
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (crisisMode) Color(0xFF080808) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            shape = RoundedCornerShape(12.dp),
            border = if (crisisMode) androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF27272A)) else null,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(14.dp)
            ) {
                Icon(
                    Icons.Default.BuildCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Ferramentas Críticas de Hardware",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Operação autônoma sem tela, sinal acústico de resgate e GPS sob demanda.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // --- TOOL 1: LANTERNA SOS MORSE ---
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (crisisMode) Color(0xFF080808) else MaterialTheme.colorScheme.surface
            ),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (state.isSosActive) Color.Red else if (crisisMode) Color(0xFF27272A) else MaterialTheme.colorScheme.outlineVariant
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.FlashlightOn,
                            contentDescription = null,
                            tint = if (state.isSosActive) Color.Red else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Lanterna SOS Morse (... --- ...)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    if (state.isSosActive) {
                        Surface(
                            color = if (crisisMode) Color(0xFF330505) else Color(0xFFFFEBEE),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "TRANSMITINDO SOS",
                                color = Color.Red,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Padrão internacional de resgate em loop via CameraManager. Permanece operando em background mesmo com a tela do smartphone apagada para economizar bateria.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = { viewModel.toggleSosMorse() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (state.isSosActive) Color.Red else MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_sos_morse")
                    ) {
                        Icon(
                            if (state.isSosActive) Icons.Default.Stop else Icons.Default.Warning,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (state.isSosActive) "Interromper SOS" else "Ativar SOS Morse")
                    }

                    OutlinedButton(
                        onClick = { viewModel.toggleTorch() },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_toggle_torch")
                    ) {
                        Icon(Icons.Default.Highlight, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (state.isTorchOn) "Luz Fixa: ON" else "Luz Fixa: OFF")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // --- TOOL 2: APITO ACÚSTICO 3000 Hz ---
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (crisisMode) Color(0xFF080808) else MaterialTheme.colorScheme.surface
            ),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (state.isWhistleActive) MaterialTheme.colorScheme.primary else if (crisisMode) Color(0xFF27272A) else MaterialTheme.colorScheme.outlineVariant
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Campaign,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Apito de Resgate Acústico (3000 Hz)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    if (state.isWhistleActive) {
                        Surface(
                            color = if (crisisMode) Color(0xFF052B14) else Color(0xFFE8F5E9),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "EMITINDO 3 kHz",
                                color = Color(0xFF2E7D32),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Geração matemática pura via AudioTrack (frequência de pico da sensibilidade coclear humana). Projetado para transpassar camadas de concreto e escombros sem depender de arquivos de áudio externos.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = { viewModel.toggleWhistle() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (state.isWhistleActive) Color(0xFFE65100) else MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("btn_whistle")
                ) {
                    Icon(
                        if (state.isWhistleActive) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (state.isWhistleActive) "Silenciar Apito" else "Disparar Apito Acústico (3000 Hz)")
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // --- TOOL 3: GPS PULSO ON-DEMAND ---
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (crisisMode) Color(0xFF080808) else MaterialTheme.colorScheme.surface
            ),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (crisisMode) Color(0xFF27272A) else MaterialTheme.colorScheme.outlineVariant
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.GpsFixed,
                            contentDescription = null,
                            tint = Color(0xFF0284C7),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "GPS Pulso (\"On-Demand\")",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(
                                if (state.isGpsActive) Color(0xFF10B981) else Color(0xFFEAB308),
                                CircleShape
                            )
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Arquitetura ultra-econômica de bateria: busca 1 única coordenada precisa com timeout de 8 segundos pelo LocationManager nativo e desliga o rádio GPS de imediato.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                Surface(
                    color = if (crisisMode) Color(0xFF03101A) else Color(0xFFF1F5F9),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = state.gpsProviderInfo,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF0284C7),
                        modifier = Modifier.padding(8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = { viewModel.triggerSingleShotGpsPulse() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("btn_gps_pulse")
                ) {
                    Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Emitir Pulso GPS (Fix de 8s)")
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // --- TOOL 4: WORKMANAGER DIÁRIO DE FEFO & BATERIAS ---
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (crisisMode) Color(0xFF080808) else MaterialTheme.colorScheme.surface
            ),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (crisisMode) Color(0xFF27272A) else MaterialTheme.colorScheme.outlineVariant
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.NotificationsActive,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Android WorkManager (FEFO & Baterias)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Verificação autônoma diária configurada em segundo plano. Notifica sobre itens com <= 90 dias (giro), <= 30 dias (crítico) e baterias com >= 90 dias sem recarga.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = { viewModel.triggerImmediateExpirationCheck() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("btn_trigger_worker")
                ) {
                    Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Executar Verificação Manual Imediata")
                }
            }
        }

        Spacer(modifier = Modifier.height(30.dp))
    }
}
