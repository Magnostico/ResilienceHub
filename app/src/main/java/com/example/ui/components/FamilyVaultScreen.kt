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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.FamilyProfileEntity
import com.example.data.model.FamilyRole
import com.example.ui.MainUiState
import com.example.ui.MainViewModel
import java.util.Locale

@Composable
fun FamilyVaultScreen(
    state: MainUiState,
    viewModel: MainViewModel,
    onOpenAddProfileDialog: () -> Unit,
    modifier: Modifier = Modifier
) {
    val crisisMode = state.crisisMode

    // Pega o peso da primeira criança cadastrada ou padrão de 24.0 kg
    val childProfile = state.profiles.find { it.role == FamilyRole.CHILD }
    var customWeightKg by remember { mutableStateOf(childProfile?.weightKg ?: 24.0) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp)
            .testTag("family_vault_screen")
    ) {
        // --- 1. CABEÇALHO DO COFRE FAMILIAR ---
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (crisisMode) Color.Black else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            shape = RoundedCornerShape(12.dp),
            border = if (crisisMode) androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF27272A)) else null,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(12.dp)
            ) {
                Icon(
                    Icons.Default.HealthAndSafety,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Fichas Médicas de Emergência Civil",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Acesso instantâneo offline a tipo sanguíneo, alergias a antibióticos e dosagens pediátricas.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // --- 2. CALCULADORA PEDIÁTRICA RÁPIDA DE EMERGÊNCIA ---
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (crisisMode) Color.Black else Color(0xFFFFFBEB)
            ),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (crisisMode) Color(0xFFF59E0B) else Color(0xFFFDE68A)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("pediatric_calculator_card")
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Calculate,
                            contentDescription = null,
                            tint = Color(0xFFD97706),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Calculadora Pediátrica de Emergência",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = if (crisisMode) Color(0xFFFBBF24) else Color(0xFF92400E)
                        )
                    }

                    Surface(
                        color = if (crisisMode) Color(0xFF291B02) else Color(0xFFFEF3C7),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "${customWeightKg.toInt()} kg",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.sp,
                            color = Color(0xFFD97706),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Baseada no peso de ${childProfile?.name ?: "Criança"} (${customWeightKg.toInt()} kg). Ajuste conforme necessário:",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Stepper de ajuste de peso
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    IconButton(onClick = { customWeightKg = (customWeightKg - 1.0).coerceAtLeast(3.0) }) {
                        Icon(Icons.Default.RemoveCircleOutline, contentDescription = "Diminuir peso")
                    }
                    Text(
                        text = "${String.format(Locale.US, "%.0f", customWeightKg)} kg",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                    IconButton(onClick = { customWeightKg = (customWeightKg + 1.0).coerceAtMost(60.0) }) {
                        Icon(Icons.Default.AddCircleOutline, contentDescription = "Aumentar peso")
                    }
                }

                Divider(
                    color = if (crisisMode) Color(0xFF27272A) else Color(0xFFFDE68A),
                    modifier = Modifier.padding(vertical = 6.dp)
                )

                // Dosagens Calculadas
                val weight = customWeightKg
                val dipironaGotas = minOf(weight.toInt(), 40)
                val paracetamolGotas = minOf(weight.toInt(), 35)
                val sroMinMl = (weight * 50).toInt()
                val sroMaxMl = (weight * 100).toInt()

                PediatricDoseRow(
                    medName = "Dipirona Gotas (500mg/ml)",
                    rule = "1 gota por kg (máx 40 gotas)",
                    doseCalculated = "$dipironaGotas gotas",
                    interval = "a cada 6 em 6 horas",
                    crisisMode = crisisMode
                )

                PediatricDoseRow(
                    medName = "Paracetamol Gotas (200mg/ml)",
                    rule = "1 gota por kg (máx 35 gotas)",
                    doseCalculated = "$paracetamolGotas gotas",
                    interval = "a cada 6 em 6 horas",
                    crisisMode = crisisMode
                )

                PediatricDoseRow(
                    medName = "SRO — Reidratação Oral (OMS)",
                    rule = "50 a 100 ml / kg em 4 horas",
                    doseCalculated = "$sroMinMl a $sroMaxMl ml",
                    interval = "ingerir aos poucos em colheres",
                    crisisMode = crisisMode
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // --- 3. LISTA DE FICHAS FAMILIARES ---
        Text(
            text = "Membros Cadastrados do Núcleo Familiar:",
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        state.profiles.forEach { profile ->
            FamilyVaultProfileCard(profile = profile, crisisMode = crisisMode)
            Spacer(modifier = Modifier.height(8.dp))
        }

        Spacer(modifier = Modifier.height(80.dp))
    }
}

@Composable
fun PediatricDoseRow(
    medName: String,
    rule: String,
    doseCalculated: String,
    interval: String,
    crisisMode: Boolean
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = medName,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "$rule • $interval",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Surface(
            color = if (crisisMode) Color(0xFF1E293B) else Color(0xFFE2E8F0),
            shape = RoundedCornerShape(6.dp)
        ) {
            Text(
                text = doseCalculated,
                fontWeight = FontWeight.Black,
                fontSize = 13.sp,
                color = if (crisisMode) Color(0xFFFBBF24) else Color(0xFFB45309),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
    }
}

@Composable
fun FamilyVaultProfileCard(
    profile: FamilyProfileEntity,
    crisisMode: Boolean
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (crisisMode) Color.Black else MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (crisisMode) Color(0xFF27272A) else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("family_profile_${profile.id}")
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (profile.role == FamilyRole.ADULT) Icons.Default.Person else Icons.Default.ChildCare,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = profile.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Surface(
                    color = if (crisisMode) Color(0xFF2B0A0A) else Color(0xFFFFEBEE),
                    shape = RoundedCornerShape(4.dp),
                    border = androidx.compose.foundation.BorderStroke(0.5.dp, Color(0xFFC62828))
                ) {
                    Text(
                        text = "Tipo ${profile.bloodType}",
                        color = Color(0xFFC62828),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = "${profile.role.displayName} • ${profile.weightKg.toInt()} kg",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Divider(
                color = if (crisisMode) Color(0xFF1E1E1E) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                modifier = Modifier.padding(vertical = 6.dp)
            )

            // Alergias
            Row {
                Text(
                    text = "Alergias: ",
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.error
                )
                Text(
                    text = profile.allergies,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Medicações contínuas
            Row(modifier = Modifier.padding(top = 2.dp)) {
                Text(
                    text = "Medicação: ",
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = profile.medications,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
