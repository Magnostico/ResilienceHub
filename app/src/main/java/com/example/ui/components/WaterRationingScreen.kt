package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.RationingAndAlertEngine
import com.example.ui.MainUiState
import java.util.Locale

@Composable
fun WaterRationingScreen(
    state: MainUiState,
    modifier: Modifier = Modifier
) {
    val report = state.waterReport ?: return
    val crisisMode = state.crisisMode

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("water_rationing_screen")
    ) {
        // --- Household Parameter Header ---
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
                modifier = Modifier.padding(16.dp)
            ) {
                Icon(
                    Icons.Default.People,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = "Parâmetros do Núcleo Familiar",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${report.adultsCount} Adultos + ${report.childrenCount} Criança (Total: ${report.adultsCount + report.childrenCount} pessoas)",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- Card 1: Apartamento (Estoque Base) ---
        WaterAutonomyCard(
            title = "Apartamento — Estoque Base",
            subtitle = "Taxa recomendada: 2.5 Litros / pessoa / dia",
            stockLiters = report.apartmentStockLiters,
            dailyConsumption = report.apartmentDailyConsumptionLiters,
            autonomyValue = String.format(Locale.US, "%.1f", report.apartmentAutonomyDays),
            autonomyUnit = "Dias de Autonomia",
            icon = Icons.Default.Home,
            color = Color(0xFF0284C7),
            crisisMode = crisisMode,
            footerText = if (report.apartmentAutonomyDays >= 14.0) {
                "✓ Meta mínima de 14 dias atingida para o núcleo familiar."
            } else {
                val needed = (14.0 * report.apartmentDailyConsumptionLiters) - report.apartmentStockLiters
                "⚠ Faltam ${String.format(Locale.US, "%.0f", needed)} L para atingir os 14 dias recomendados pela Defesa Civil."
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // --- Card 2: Mochila 72 Horas (Evacuação Imediata) ---
        WaterAutonomyCard(
            title = "Mochila 72h — Evacuação Rápida",
            subtitle = "Taxa de racionamento tático: 2.0 Litros / pessoa / dia",
            stockLiters = report.bugoutStockLiters,
            dailyConsumption = report.bugoutDailyConsumptionLiters,
            autonomyValue = String.format(Locale.US, "%.0f", report.bugoutAutonomyHours),
            autonomyUnit = "Horas de Autonomia (${String.format(Locale.US, "%.1f", report.bugoutAutonomyDays)} dias)",
            icon = Icons.Default.Backpack,
            color = if (report.bugoutIs72hReady) Color(0xFF10B981) else Color(0xFFF97316),
            crisisMode = crisisMode,
            footerText = if (report.bugoutIs72hReady) {
                "✓ Pronto para 72 Horas! Estoque cobre 3 dias completos de evacuação."
            } else {
                val deficit = report.bugoutTarget72hLiters - report.bugoutStockLiters
                "⚠ Déficit de ${String.format(Locale.US, "%.1f", deficit)} L para cobrir as 72 horas críticas (Meta: ${report.bugoutTarget72hLiters} L)."
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // --- Purificação & Tratamento Químico ---
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
                        Icons.Default.Sanitizer,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Protocolo de Purificação Química (Clorin)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "• Clorin 1mg (Mochila): 1 comprimido para cada 1 Litro de água límpida. Aguardar 30 minutos.\n" +
                           "• Clorin 10.000mg (Apartamento): Purificação em massa de caixas d'água e reservatórios.\n" +
                           "• Fervura física: 1 minuto contínuo em ebulição borbulhante destrói bactérias, vírus e protozoários.",
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(40.dp))
    }
}

@Composable
fun WaterAutonomyCard(
    title: String,
    subtitle: String,
    stockLiters: Double,
    dailyConsumption: Double,
    autonomyValue: String,
    autonomyUnit: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    crisisMode: Boolean,
    footerText: String
) {
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
                        icon,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
            )

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (crisisMode) Color(0xFF03101A) else color.copy(alpha = 0.08f),
                        RoundedCornerShape(8.dp)
                    )
                    .padding(14.dp)
            ) {
                Column {
                    Text(
                        text = "Estoque Total",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${String.format(Locale.US, "%.1f", stockLiters)} L",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = color
                    )
                    Text(
                        text = "Consumo: ${String.format(Locale.US, "%.1f", dailyConsumption)} L/dia",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = autonomyValue,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = color
                    )
                    Text(
                        text = autonomyUnit,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = footerText,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = if (footerText.startsWith("✓")) Color(0xFF10B981) else Color(0xFFF59E0B)
            )
        }
    }
}
