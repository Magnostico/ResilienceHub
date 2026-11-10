package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
    var customWeightKg by remember(childProfile?.weightKg) {
        mutableDoubleStateOf(childProfile?.weightKg ?: 24.0)
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 92.dp),
        modifier = modifier
            .fillMaxSize()
            .testTag("family_vault_screen")
    ) {
        // --- 1. CABEÇALHO DO COFRE FAMILIAR ---
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (crisisMode) Color.Black else Color(0xFF111827)
                ),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFF334155)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(14.dp)
                ) {
                    Icon(
                        Icons.Default.HealthAndSafety,
                        contentDescription = null,
                        tint = Color(0xFFFBBF24),
                        modifier = Modifier.size(30.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Fichas Médicas de Emergência Civil",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp,
                            color = Color.White
                        )
                        Text(
                            text = "Acesso imediato offline a tipo sanguíneo, alergias medicamentosas e dosagens pediátricas.",
                            fontSize = 12.sp,
                            color = Color(0xFFCBD5E1)
                        )
                    }
                }
            }
        }

        // --- 2. CALCULADORA PEDIÁTRICA RÁPIDA DE EMERGÊNCIA (ALTO CONTRASTE ESCURO) ---
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (crisisMode) Color.Black else Color(0xFF191308)
                ),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.5.dp, Color(0xFFF59E0B)),
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
                                tint = Color(0xFFFBBF24),
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Calculadora Pediátrica de Emergência",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp,
                                color = Color(0xFFFBBF24)
                            )
                        }

                        Surface(
                            color = Color(0xFFF59E0B),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "${customWeightKg.toInt()} kg",
                                fontWeight = FontWeight.Black,
                                fontSize = 13.sp,
                                color = Color(0xFF090D16),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Baseada no peso de ${childProfile?.name ?: "Criança"} (${customWeightKg.toInt()} kg). Ajuste abaixo:",
                        fontSize = 12.sp,
                        color = Color(0xFFE2E8F0)
                    )

                    // Stepper de ajuste de peso com botões claros
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        FilledTonalIconButton(
                            onClick = { customWeightKg = (customWeightKg - 1.0).coerceAtLeast(3.0) },
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = Color(0xFF27272A),
                                contentColor = Color.White
                            )
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = "Diminuir peso")
                        }
                        Text(
                            text = "${String.format(Locale.US, "%.0f", customWeightKg)} kg",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 18.dp)
                        )
                        FilledTonalIconButton(
                            onClick = { customWeightKg = (customWeightKg + 1.0).coerceAtMost(60.0) },
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = Color(0xFFF59E0B),
                                contentColor = Color(0xFF090D16)
                            )
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Aumentar peso")
                        }
                    }

                    Divider(
                        color = Color(0xFF3F2E12),
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
                        interval = "a cada 6 em 6 horas"
                    )

                    PediatricDoseRow(
                        medName = "Paracetamol Gotas (200mg/ml)",
                        rule = "1 gota por kg (máx 35 gotas)",
                        doseCalculated = "$paracetamolGotas gotas",
                        interval = "a cada 6 em 6 horas"
                    )

                    PediatricDoseRow(
                        medName = "SRO — Reidratação Oral (OMS)",
                        rule = "50 a 100 ml / kg em 4 horas",
                        doseCalculated = "$sroMinMl a $sroMaxMl ml",
                        interval = "ingerir aos poucos em colheres"
                    )
                }
            }
        }

        // --- 3. CABEÇALHO E BOTÃO DE NOVO MEMBRO FAMILIAR ---
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Membros do Núcleo Familiar (${state.profiles.size}):",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp,
                    color = Color.White
                )
                Button(
                    onClick = onOpenAddProfileDialog,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF0284C7),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(5.dp))
                    Text("Novo Familiar", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // --- 4. LISTA DE FICHAS FAMILIARES ---
        items(state.profiles, key = { it.id }) { profile ->
            FamilyVaultProfileCard(profile = profile, crisisMode = crisisMode)
        }
    }
}

@Composable
fun PediatricDoseRow(
    medName: String,
    rule: String,
    doseCalculated: String,
    interval: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = medName,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 13.sp,
                color = Color.White
            )
            Text(
                text = "$rule • $interval",
                fontSize = 11.sp,
                color = Color(0xFFCBD5E1)
            )
        }

        Surface(
            color = Color(0xFF090D16),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, Color(0xFFFBBF24))
        ) {
            Text(
                text = doseCalculated,
                fontWeight = FontWeight.Black,
                fontSize = 13.sp,
                color = Color(0xFFFBBF24),
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
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
            containerColor = if (crisisMode) Color.Black else Color(0xFF111827)
        ),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            1.dp,
            if (crisisMode) Color(0xFF3F3F46) else Color(0xFF334155)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("family_profile_${profile.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (profile.role == FamilyRole.ADULT) Icons.Default.Person else Icons.Default.ChildCare,
                        contentDescription = null,
                        tint = Color(0xFFFBBF24),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = profile.name,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp,
                        color = Color.White
                    )
                }

                Surface(
                    color = Color(0xFF3B0A0A),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, Color(0xFFF87171))
                ) {
                    Text(
                        text = "Sangue ${profile.bloodType}",
                        color = Color(0xFFF87171),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "${profile.role.displayName} • Peso: ${profile.weightKg.toInt()} kg",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF38BDF8)
            )

            Divider(
                color = Color(0xFF1E293B),
                modifier = Modifier.padding(vertical = 8.dp)
            )

            // Alergias
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = "⚠ Alergias: ",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 12.sp,
                    color = Color(0xFFF87171)
                )
                Text(
                    text = profile.allergies,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Medicações contínuas
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = "💊 Medicação: ",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 12.sp,
                    color = Color(0xFFFBBF24)
                )
                Text(
                    text = profile.medications,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFFE2E8F0)
                )
            }
        }
    }
}
