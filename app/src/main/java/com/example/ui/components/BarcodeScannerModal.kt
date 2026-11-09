package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BarcodeScannerModal(
    onDismiss: () -> Unit,
    onBarcodeScanned: (String) -> Unit
) {
    var manualBarcode by remember { mutableStateOf("") }

    val sampleBarcodes = listOf(
        Pair("7891000100011", "Galão Água 20L"),
        Pair("7891000100028", "Pastilhas Clorin"),
        Pair("7891000100103", "Atum em Óleo"),
        Pair("7891000100110", "Sardinha em Lata"),
        Pair("7891000100042", "Power Bank Solar"),
        Pair("7891000100073", "Pilhas Eneloop"),
        Pair("7891000100202", "Torniquete CAT")
    )

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF0284C7)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
                .testTag("barcode_scanner_modal")
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(16.dp)
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.QrCodeScanner,
                            contentDescription = null,
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Leitor de Código de Barras",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color.White
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Fechar", tint = Color.LightGray)
                    }
                }

                Text(
                    text = "Posicione o código de barras (EAN-13, EAN-8) ou QR Code no visor:",
                    fontSize = 11.sp,
                    color = Color(0xFF94A3B8),
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Viewfinder com mira óptica e linha laser
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .background(Color.Black, RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0xFF334155), RoundedCornerShape(12.dp))
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height

                        // Cantos de foco
                        val cornerLen = 24.dp.toPx()
                        val pad = 20.dp.toPx()
                        val color = Color(0xFF38BDF8)

                        // Canto Superior Esquerdo
                        drawLine(color, Offset(pad, pad), Offset(pad + cornerLen, pad), 3f)
                        drawLine(color, Offset(pad, pad), Offset(pad, pad + cornerLen), 3f)

                        // Canto Superior Direito
                        drawLine(color, Offset(w - pad, pad), Offset(w - pad - cornerLen, pad), 3f)
                        drawLine(color, Offset(w - pad, pad), Offset(w - pad, pad + cornerLen), 3f)

                        // Canto Inferior Esquerdo
                        drawLine(color, Offset(pad, h - pad), Offset(pad + cornerLen, h - pad), 3f)
                        drawLine(color, Offset(pad, h - pad), Offset(pad, h - pad - cornerLen), 3f)

                        // Canto Inferior Direito
                        drawLine(color, Offset(w - pad, h - pad), Offset(w - pad - cornerLen, h - pad), 3f)
                        drawLine(color, Offset(w - pad, h - pad), Offset(w - pad, h - pad - cornerLen), 3f)

                        // Linha laser central vermelha
                        drawLine(
                            color = Color(0xFFEF4444),
                            start = Offset(pad + 10.dp.toPx(), h / 2),
                            end = Offset(w - pad - 10.dp.toPx(), h / 2),
                            strokeWidth = 2.dp.toPx()
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.CameraAlt,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.4f),
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "SENSOR ÓPTICO ATIVO",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF10B981)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Leitura Rápida Simulada (ideal para testar imediatamente no emulador ou bancada)
                Text(
                    text = "Códigos Detectados / Teste Rápido:",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFE2E8F0),
                    modifier = Modifier.align(Alignment.Start)
                )

                Spacer(modifier = Modifier.height(4.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(sampleBarcodes) { (code, label) ->
                        Surface(
                            color = Color(0xFF1E293B),
                            shape = RoundedCornerShape(6.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF0284C7)),
                            modifier = Modifier.clickable {
                                onBarcodeScanned(code)
                                onDismiss()
                            }
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                                Text(code, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8))
                                Text(label, fontSize = 9.sp, color = Color.LightGray)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Campo manual para digitar ou colar
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = manualBarcode,
                        onValueChange = { manualBarcode = it },
                        placeholder = { Text("Digitar código EAN-13 manualmente", fontSize = 11.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF38BDF8),
                            unfocusedBorderColor = Color(0xFF475569)
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (manualBarcode.isNotBlank()) {
                                onBarcodeScanned(manualBarcode.trim())
                                onDismiss()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
                    ) {
                        Text("OK", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
