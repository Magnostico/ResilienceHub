package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.services.ApiCostGuard
import com.example.ui.MainUiState
import com.example.ui.MainViewModel
import java.util.Locale

/**
 * Painel Central de Ferramentas Online Opcionais (Firebase Cloud Sync + Google Maps / Street View)
 * com Arquitetura 100% Offline-First e Trava Rígida Anti-Gasto de API.
 */
@Composable
fun CloudAndOnlineHubDialog(
    state: MainUiState,
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val guard = state.apiCostGuard
    val auth = state.cloudAuthState

    var emailInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var mapsKeyInput by remember { mutableStateOf(viewModel.apiCostGuard.getMapsApiKey()) }

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        focusedBorderColor = Color(0xFF38BDF8),
        unfocusedBorderColor = Color(0xFF475569)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0F172A),
        titleContentColor = Color.White,
        textContentColor = Color.White,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (guard.onlineToolsEnabled) Icons.Default.CloudDone else Icons.Default.CloudOff,
                    contentDescription = null,
                    tint = if (guard.onlineToolsEnabled) Color(0xFF38BDF8) else Color(0xFF34D399)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Nuvem Firebase & APIs Online",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = if (guard.onlineToolsEnabled) "Modo Híbrido (Offline-First + Online)" else "Modo 100% Offline-First (Padrão)",
                        fontSize = 10.sp,
                        color = if (guard.onlineToolsEnabled) Color(0xFF38BDF8) else Color(0xFF34D399),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                // 1. INTERRUPTOR PRINCIPAL OFFLINE-FIRST / ONLINE OPCIONAL
                Surface(
                    color = if (guard.onlineToolsEnabled) Color(0xFF0C2434) else Color(0xFF091B18),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(
                        1.5.dp,
                        if (guard.onlineToolsEnabled) Color(0xFF38BDF8) else Color(0xFF10B981)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.padding(10.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = if (guard.onlineToolsEnabled) "🌐 Ferramentas Online: LIGADAS" else "✈️ Modo 100% Offline: ATIVO",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                            Text(
                                text = "O app continua salvando tudo primeiro no celular (Room). Ligue só quando quiser sincronizar com o Firebase ou ver Satélite/Street View.",
                                fontSize = 10.sp,
                                color = Color(0xFFCBD5E1)
                            )
                        }
                        Switch(
                            checked = guard.onlineToolsEnabled,
                            onCheckedChange = { viewModel.setOnlineToolsEnabled(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF0284C7),
                                uncheckedThumbColor = Color(0xFF34D399),
                                uncheckedTrackColor = Color(0xFF1E293B)
                            )
                        )
                    }
                }

                // 2. PAINEL DE PROTEÇÃO ANTI-GASTO (TRAVA DIÁRIA DE API)
                Surface(
                    color = Color(0xFF090D16),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0xFFF59E0B)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier.padding(10.dp)
                    ) {
                        Text(
                            text = "🛡 Cofre Anti-Gasto de APIs (Cota Gratuita)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFFFBBF24)
                        )
                        Text(
                            text = "• Firebase Spark (Grátis): ${guard.cloudOpsToday}/${guard.cloudOpsDailyLimit} ops hoje (cada sync usa só 3 ops; limite grátis = 20.000/dia).",
                            fontSize = 10.sp,
                            color = Color(0xFFE2E8F0)
                        )
                        Text(
                            text = "• Street View API: ${guard.streetViewCallsToday}/${guard.streetViewDailyLimit} fotos hoje • ${guard.cachedStreetViewImagesCount} fotos já em cache offline (R\$ 0,00).",
                            fontSize = 10.sp,
                            color = Color(0xFFE2E8F0)
                        )
                        Text(
                            text = "• Google Maps 360° & Navegação via App Maps: R\$ 0,00 ILIMITADO (sempre grátis!).",
                            fontSize = 10.sp,
                            color = Color(0xFF34D399),
                            fontWeight = FontWeight.Bold
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                        ) {
                            Text(
                                text = "Teto Street View/dia: ${guard.streetViewDailyLimit}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF38BDF8)
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        viewModel.updateApiDailyLimits(
                                            streetViewLimit = (guard.streetViewDailyLimit - 2).coerceAtLeast(2),
                                            cloudOpsLimit = guard.cloudOpsDailyLimit
                                        )
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                    modifier = Modifier.height(26.dp)
                                ) {
                                    Text("-2", fontSize = 10.sp, color = Color.White)
                                }
                                OutlinedButton(
                                    onClick = {
                                        viewModel.updateApiDailyLimits(
                                            streetViewLimit = (guard.streetViewDailyLimit + 2).coerceAtMost(30),
                                            cloudOpsLimit = guard.cloudOpsDailyLimit
                                        )
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                    modifier = Modifier.height(26.dp)
                                ) {
                                    Text("+2", fontSize = 10.sp, color = Color.White)
                                }
                            }
                        }

                        // Chave Google Maps / Street View (salva apenas localmente no aparelho — nunca vai pro Git!)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (guard.hasMapsApiKey) {
                                "🔑 Street View Embutido: CHAVE ATIVA"
                            } else {
                                "🔑 Chave Google Maps / Street View (opcional — salva só no celular):"
                            },
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (guard.hasMapsApiKey) Color(0xFF34D399) else Color(0xFFCBD5E1)
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = mapsKeyInput,
                                onValueChange = { mapsKeyInput = it },
                                placeholder = { Text("Cole sua MAPS_API_KEY aqui", fontSize = 10.sp, color = Color(0xFF64748B)) },
                                colors = textFieldColors,
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = { viewModel.saveCustomMapsApiKey(mapsKeyInput) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFF59E0B),
                                    contentColor = Color(0xFF090D16)
                                ),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                            ) {
                                Text("Salvar", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }
                }

                // 3. FEEDBACK RECENTE DA NUVEM
                state.cloudSyncStatusMessage?.let { msg ->
                    Surface(
                        color = Color(0xFF1E293B),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color(0xFF38BDF8)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(8.dp)
                        ) {
                            if (state.isCloudBusy) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = Color(0xFFFBBF24)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(text = msg, fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                // 4. CONTA FIREBASE & SINCRONIZAÇÃO SOB DEMANDA (3 DOCUMENTOS)
                Surface(
                    color = Color(0xFF111827),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0xFF334155)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(10.dp)
                    ) {
                        Text(
                            text = "☁️ Banco de Dados Firebase (Cloud Firestore)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF38BDF8)
                        )

                        if (auth.isSignedIn) {
                            Text(
                                text = if (auth.isAnonymous) {
                                    "Conectado: Sessão Rápida (ID ${auth.userUid?.take(8)}...)"
                                } else {
                                    "Conectado: ${auth.userEmail ?: auth.userUid}"
                                },
                                fontSize = 11.sp,
                                color = Color(0xFF34D399),
                                fontWeight = FontWeight.Bold
                            )
                            auth.lastSyncText?.let { last ->
                                Text(
                                    text = "Último sync: $last",
                                    fontSize = 10.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Button(
                                    onClick = { viewModel.uploadVaultToFirebase() },
                                    enabled = !state.isCloudBusy && guard.onlineToolsEnabled,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF0284C7),
                                        contentColor = Color.White
                                    ),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Enviar Nuvem", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                                }

                                Button(
                                    onClick = { viewModel.downloadVaultFromFirebase() },
                                    enabled = !state.isCloudBusy && guard.onlineToolsEnabled,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF059669),
                                        contentColor = Color.White
                                    ),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Baixar Nuvem", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                                }
                            }

                            if (!guard.onlineToolsEnabled) {
                                Text(
                                    text = "Ligue 'Ferramentas Online' no topo acima para enviar ou baixar.",
                                    fontSize = 10.sp,
                                    color = Color(0xFFFBBF24)
                                )
                            }

                            TextButton(
                                onClick = { viewModel.signOutCloud() },
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Text("Sair da Conta Firebase", fontSize = 11.sp, color = Color(0xFFF87171))
                            }
                        } else {
                            Text(
                                text = "Entre com E-mail/Senha (ideal para sincronizar entre celulares da família) ou use Sessão Rápida:",
                                fontSize = 10.sp,
                                color = Color(0xFFCBD5E1)
                            )
                            OutlinedTextField(
                                value = emailInput,
                                onValueChange = { emailInput = it },
                                label = { Text("E-mail", fontSize = 11.sp, color = Color(0xFFCBD5E1)) },
                                colors = textFieldColors,
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = passwordInput,
                                onValueChange = { passwordInput = it },
                                label = { Text("Senha (mín. 6 caracteres)", fontSize = 11.sp, color = Color(0xFFCBD5E1)) },
                                visualTransformation = PasswordVisualTransformation(),
                                colors = textFieldColors,
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Button(
                                    onClick = { viewModel.signInCloudWithEmail(emailInput, passwordInput) },
                                    enabled = !state.isCloudBusy,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF0284C7),
                                        contentColor = Color.White
                                    ),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Entrar", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = { viewModel.registerCloudWithEmail(emailInput, passwordInput) },
                                    enabled = !state.isCloudBusy,
                                    border = BorderStroke(1.dp, Color(0xFF38BDF8)),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Criar Conta", fontSize = 11.sp, color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold)
                                }
                            }
                            OutlinedButton(
                                onClick = { viewModel.signInCloudAnonymously() },
                                enabled = !state.isCloudBusy,
                                border = BorderStroke(1.dp, Color(0xFF34D399)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("⚡ Sessão Rápida (Backup Anônimo sem Cadastro)", fontSize = 10.sp, color = Color(0xFF34D399), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF334155),
                    contentColor = Color.White
                )
            ) {
                Text("Fechar", fontWeight = FontWeight.Bold)
            }
        }
    )
}

/**
 * Modal de Street View Híbrido (Cache Offline + Preview API com Trava Anti-Gasto + 360° Grátis no Google Maps).
 */
@Composable
fun StreetViewPreviewDialog(
    title: String,
    lat: Double,
    lon: Double,
    apiCostGuard: ApiCostGuard,
    onlineToolsEnabled: Boolean,
    onDismiss: () -> Unit,
    onOpenFree360: () -> Unit,
    onOpenFreeMapsRoute: () -> Unit
) {
    var result by remember { mutableStateOf<ApiCostGuard.StreetViewResult?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(lat, lon, onlineToolsEnabled) {
        isLoading = true
        result = apiCostGuard.loadStreetViewPreview(
            lat = lat,
            lon = lon,
            allowOnlineFetch = onlineToolsEnabled
        )
        isLoading = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0F172A),
        titleContentColor = Color.White,
        textContentColor = Color.White,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Streetview, contentDescription = null, tint = Color(0xFFFBBF24))
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text("Street View & Maps Online", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                    Text(
                        text = "$title (${String.format(Locale.US, "%.4f, %.4f", lat, lon)})",
                        fontSize = 10.sp,
                        color = Color(0xFF38BDF8)
                    )
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (isLoading) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(170.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF090D16))
                    ) {
                        CircularProgressIndicator(color = Color(0xFF38BDF8))
                    }
                } else {
                    val bmp = result?.bitmap
                    if (bmp != null) {
                        Image(
                            bitmap = bmp,
                            contentDescription = "Preview Street View",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(190.dp)
                                .clip(RoundedCornerShape(10.dp))
                        )
                    } else {
                        Surface(
                            color = Color(0xFF090D16),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0xFF334155)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = result?.message ?: "Preview offline indisponível para este ponto.",
                                    fontSize = 11.sp,
                                    color = Color(0xFFE2E8F0)
                                )
                            }
                        }
                    }

                    if (bmp != null) {
                        Text(
                            text = result?.message ?: "",
                            fontSize = 10.sp,
                            color = Color(0xFF34D399),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // AÇÕES 100% GRATUITAS (R$ 0,00 ILIMITADO VIA GOOGLE MAPS INTENTS)
                Button(
                    onClick = onOpenFree360,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFF59E0B),
                        contentColor = Color(0xFF090D16)
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Streetview, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("🌐 Abrir Street View 360° (100% Grátis)", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                }

                OutlinedButton(
                    onClick = onOpenFreeMapsRoute,
                    border = BorderStroke(1.dp, Color(0xFF38BDF8)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Directions, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("🚗 Rota & Trânsito no Google Maps (Grátis)", fontSize = 11.sp, color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Fechar", color = Color(0xFFCBD5E1))
            }
        }
    )
}
