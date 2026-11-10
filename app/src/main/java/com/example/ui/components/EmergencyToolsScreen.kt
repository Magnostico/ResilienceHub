package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
        EmergencyContact("199", "199 — Defesa Civil", "Deslizamentos, enchentes e alertas de evacuação", Color(0xFFFBBF24)),
        EmergencyContact("193", "193 — Bombeiros (CBMERJ)", "Resgate em escombros, soterramentos e incêndios", Color(0xFFF87171)),
        EmergencyContact("192", "192 — SAMU", "Urgências clínicas e trauma grave", Color(0xFFFB923C)),
        EmergencyContact("190", "190 — Polícia Militar", "Segurança pública e patrulhamento de emergência", Color(0xFF38BDF8)),
        EmergencyContact("08007226001", "0800-722-6001 — ANVISA", "Disque-Intoxicação (produtos químicos, animais peçonhentos)", Color(0xFF34D399))
    )

    val radioStations = listOf(
        RadioFrequency("CBN Rio", "92.5 MHz", "FM", "Plantão contínuo de notícias, trânsito e Defesa Civil"),
        RadioFrequency("BandNews FM Rio", "90.3 MHz", "FM", "Cobertura em tempo real de desastres e utilidade pública"),
        RadioFrequency("Super Rádio Tupi", "96.5 FM / 1280 AM", "FM/AM", "Maior alcance comunitário e alertas populares"),
        RadioFrequency("Rádio Nacional RJ", "1130 kHz", "AM", "Transmissor de alta potência e alcance regional/nacional")
    )

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 88.dp),
        modifier = modifier
            .fillMaxSize()
            .testTag("emergency_tools_screen")
    ) {
        // --- 1. BOTÕES GRANDES DE HARDWARE: LANTERNA SOS MORSE, APITO 3000 Hz E LANTERNA CONTÍNUA ---
        item {
            Text(
                text = "Sinalizadores de Resgate (Hardware Nativo)",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 15.sp,
                color = Color.White
            )
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Botão Lanterna SOS Morse
                    Button(
                        onClick = { viewModel.toggleSosMorse() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (state.isSosActive) Color(0xFF991B1B) else Color(0xFFDC2626),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.5.dp, Color(0xFFFCA5A5)),
                        contentPadding = PaddingValues(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(88.dp)
                            .testTag("btn_sos_morse_large")
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                if (state.isSosActive) Icons.Default.FlashlightOff else Icons.Default.FlashlightOn,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (state.isSosActive) "PARAR SOS" else "LANTERNA SOS",
                                fontWeight = FontWeight.Black,
                                fontSize = 13.sp,
                                color = Color.White
                            )
                            Text(
                                text = "Morse (... --- ...)",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFFEE2E2)
                            )
                        }
                    }

                    // Botão Apito 3000 Hz
                    Button(
                        onClick = { viewModel.toggleWhistle() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (state.isWhistleActive) Color(0xFF9A3412) else Color(0xFFEA580C),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.5.dp, Color(0xFFFDBA74)),
                        contentPadding = PaddingValues(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(88.dp)
                            .testTag("btn_whistle_large")
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                if (state.isWhistleActive) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (state.isWhistleActive) "PARAR APITO" else "APITO 3000 Hz",
                                fontWeight = FontWeight.Black,
                                fontSize = 13.sp,
                                color = Color.White
                            )
                            Text(
                                text = "Onda Pura Escombros",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFFFEDD5)
                            )
                        }
                    }
                }

                // Lanterna Contínua Rápida
                OutlinedButton(
                    onClick = { viewModel.toggleTorch() },
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (state.isTorchOn) Color(0xFFF59E0B) else Color(0xFF111827),
                        contentColor = if (state.isTorchOn) Color(0xFF090D16) else Color.White
                    ),
                    border = BorderStroke(1.dp, Color(0xFFFBBF24)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        if (state.isTorchOn) Icons.Default.HighlightOff else Icons.Default.Highlight,
                        contentDescription = null,
                        tint = if (state.isTorchOn) Color(0xFF090D16) else Color(0xFFFBBF24),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (state.isTorchOn) "DESLIGAR LANTERNA CONTÍNUA" else "LIGAR LANTERNA CONTÍNUA (FEIXE FIXO)",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // --- 2. DISCAGEM RÁPIDA DE 1-TOQUE (ACTION_DIAL) ---
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Discagem Rápida de Emergência (1-Toque)",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 15.sp,
                color = Color.White
            )
        }

        items(emergencyContacts, key = { it.number }) { contact ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (crisisMode) Color.Black else Color(0xFF111827)
                ),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, contact.color.copy(alpha = 0.75f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { dialNumber(context, contact.number) }
                    .testTag("dial_btn_${contact.number}")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(12.dp)
                ) {
                    FilledTonalIconButton(
                        onClick = { dialNumber(context, contact.number) },
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = contact.color.copy(alpha = 0.22f),
                            contentColor = contact.color
                        )
                    ) {
                        Icon(Icons.Default.PhoneInTalk, contentDescription = "Ligar")
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = contact.name,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp,
                            color = contact.color
                        )
                        Text(
                            text = contact.description,
                            fontSize = 12.sp,
                            color = Color(0xFFE2E8F0)
                        )
                    }

                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = Color.White
                    )
                }
            }
        }

        // --- 3. TABELA FIXA DE FREQUÊNCIAS DE RÁDIO AM/FM (RIO DE JANEIRO) ---
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Frequências de Rádio de Emergência (RJ)",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 15.sp,
                color = Color.White
            )
        }

        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (crisisMode) Color.Black else Color(0xFF111827)
                ),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFF38BDF8)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Em colapso de sinal celular ou blackout elétrico, sintonize no rádio a pilha ou dínamo:",
                        fontSize = 12.sp,
                        color = Color(0xFFCBD5E1),
                        modifier = Modifier.padding(bottom = 10.dp)
                    )

                    radioStations.forEachIndexed { index, station ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = station.station,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 14.sp,
                                    color = Color.White
                                )
                                Text(
                                    text = station.purpose,
                                    fontSize = 11.sp,
                                    color = Color(0xFFCBD5E1)
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Surface(
                                color = Color(0xFF090D16),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color(0xFF38BDF8))
                            ) {
                                Text(
                                    text = station.frequency,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 12.sp,
                                    color = Color(0xFF38BDF8),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }

                        if (index < radioStations.size - 1) {
                            Divider(
                                color = Color(0xFF1E293B),
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun dialNumber(context: Context, number: String) {
    try {
        val intent = Intent(Intent.ACTION_DIAL).apply {
            data = Uri.parse("tel:$number")
        }
        context.startActivity(intent)
    } catch (_: Exception) {
    }
}
