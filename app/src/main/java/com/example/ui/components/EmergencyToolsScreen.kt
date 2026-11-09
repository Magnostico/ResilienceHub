package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.MainUiState
import com.example.ui.MainViewModel

data class EmergencyContact(
    val number: String,
    val name: String,
    val description: String,
    val color: Color
)

data class RadioFrequency(
    val station: String,
    val frequency: String,
    val band: String,
    val purpose: String
)

@Composable
fun EmergencyToolsScreen(
    state: MainUiState,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val crisisMode = state.crisisMode

    val emergencyContacts = listOf(
        EmergencyContact("199", "199 — Defesa Civil", "Deslizamentos, enchentes e alertas de evacuação", Color(0xFFF59E0B)),
        EmergencyContact("193", "193 — Bombeiros (CBMERJ)", "Resgate em escombros, soterramentos e incêndios", Color(0xFFEF4444)),
        EmergencyContact("192", "192 — SAMU", "Urgências clínicas e trauma grave", Color(0xFFDC2626)),
        EmergencyContact("190", "190 — Polícia Militar", "Segurança pública e patrulhamento de emergência", Color(0xFF0284C7)),
        EmergencyContact("08007226001", "0800-722-6001 — ANVISA", "Disque-Intoxicação (produtos químicos, animais peçonhentos)", Color(0xFF10B981))
    )

    val radioStations = listOf(
        RadioFrequency("CBN Rio", "92.5 MHz", "FM", "Plantão contínuo de notícias, trânsito e Defesa Civil"),
        RadioFrequency("BandNews FM Rio", "90.3 MHz", "FM", "Cobertura em tempo real de desastres e utilidade pública"),
        RadioFrequency("Super Rádio Tupi", "96.5 FM / 1280 AM", "FM/AM", "Maior alcance comunitário e alertas populares"),
        RadioFrequency("Rádio Nacional RJ", "1130 kHz", "AM", "Transmissor de alta potência e alcance regional/nacional")
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp)
            .testTag("emergency_tools_screen")
    ) {
        // --- 1. DISCAGEM RÁPIDA DE 1-TOQUE (ACTION_DIAL) ---
        Text(
            text = "Discagem Rápida de Emergência (1-Toque)",
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        emergencyContacts.forEach { contact ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (crisisMode) Color.Black else MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (crisisMode) contact.color else MaterialTheme.colorScheme.outlineVariant
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
                    .clickable {
                        dialNumber(context, contact.number)
                    }
                    .testTag("dial_btn_${contact.number}")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(10.dp)
                ) {
                    FilledTonalIconButton(
                        onClick = { dialNumber(context, contact.number) },
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = contact.color.copy(alpha = 0.2f),
                            contentColor = contact.color
                        )
                    ) {
                        Icon(Icons.Default.PhoneInTalk, contentDescription = "Ligar")
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = contact.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = if (crisisMode) Color.White else contact.color
                        )
                        Text(
                            text = contact.description,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // --- 2. BOTÕES GRANDES DE HARDWARE: SOS MORSE E APITO 3000 Hz ---
        Text(
            text = "Acionadores de Sinais de Resgate (Hardware)",
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // Botão Lanterna SOS Morse
            Button(
                onClick = { viewModel.toggleSosMorse() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (state.isSosActive) Color(0xFFDC2626) else Color(0xFFEF4444)
                ),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(80.dp)
                    .testTag("btn_sos_morse_large")
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        if (state.isSosActive) Icons.Default.FlashlightOff else Icons.Default.FlashlightOn,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (state.isSosActive) "PARAR SOS" else "LANTERNA SOS",
                        fontWeight = FontWeight.Black,
                        fontSize = 12.sp
                    )
                    Text("Código Morse (... --- ...)", fontSize = 9.sp)
                }
            }

            // Botão Apito 3000 Hz
            Button(
                onClick = { viewModel.toggleWhistle() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (state.isWhistleActive) Color(0xFFEA580C) else Color(0xFFF97316)
                ),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(80.dp)
                    .testTag("btn_whistle_large")
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        if (state.isWhistleActive) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (state.isWhistleActive) "PARAR APITO" else "APITO RESGATE",
                        fontWeight = FontWeight.Black,
                        fontSize = 12.sp
                    )
                    Text("3000 Hz para Escombros", fontSize = 9.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // --- 3. TABELA FIXA DE FREQUÊNCIAS DE RÁDIO AM/FM (RIO DE JANEIRO) ---
        Text(
            text = "Rádios de Emergência e Notícias (Rio de Janeiro)",
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (crisisMode) Color.Black else MaterialTheme.colorScheme.surface
            ),
            shape = RoundedCornerShape(10.dp),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (crisisMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "Em situações de colapso de sinal de celular ou blackout elétrico, sintonize no rádio a pilha/dínamo:",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                radioStations.forEachIndexed { index, station ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = station.station,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = station.purpose,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Surface(
                            color = if (crisisMode) Color(0xFF1E293B) else MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = station.frequency,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 12.sp,
                                color = if (crisisMode) Color(0xFF38BDF8) else MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    if (index < radioStations.size - 1) {
                        Divider(
                            color = if (crisisMode) Color(0xFF27272A) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(80.dp))
    }
}

private fun dialNumber(context: Context, number: String) {
    try {
        val intent = Intent(Intent.ACTION_DIAL).apply {
            data = Uri.parse("tel:$number")
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        // Fallback
    }
}
