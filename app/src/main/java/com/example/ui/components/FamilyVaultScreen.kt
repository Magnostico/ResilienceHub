package com.example.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.FamilyProfileEntity
import com.example.data.model.FamilyRole
import com.example.ui.FamilyDocument
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
    var showConfirmClearProfiles by remember { mutableStateOf(false) }
    var editingProfile by remember { mutableStateOf<FamilyProfileEntity?>(null) }

    // Estados para Receitas Médicas e Documentos da Família em PDF
    var pendingPdfUri by remember { mutableStateOf<Uri?>(null) }
    var pendingPdfDefaultTitle by remember { mutableStateOf("Receita Médica") }
    var viewingFamilyDoc by remember { mutableStateOf<FamilyDocument?>(null) }
    var showBackupModal by remember { mutableStateOf(false) }

    val familyPdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val rawName = viewModel.pdfRagManager.getFileNameFromUri(uri)
                ?.removeSuffix(".pdf")
                ?.replace("_", " ")
                ?: "Receita ou Documento"
            pendingPdfDefaultTitle = rawName
            pendingPdfUri = uri
        }
    }

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
                            text = "Fichas Médicas, Receitas & Documentos da Família",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp,
                            color = Color.White
                        )
                        Text(
                            text = "Cadastre e edite familiares, calcule dosagens pediátricas e guarde PDFs de receitas médicas e documentos 100% offline.",
                            fontSize = 12.sp,
                            color = Color(0xFFCBD5E1)
                        )
                    }
                }
            }
        }

        // --- 2. CABEÇALHO, BOTÃO NOVO FAMILIAR & LIMPAR FAMÍLIA ---
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Núcleo Familiar (${state.profiles.size}):",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp,
                    color = Color.White
                )

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(
                        onClick = onOpenAddProfileDialog,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFF59E0B),
                            contentColor = Color(0xFF090D16)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(5.dp))
                        Text("Novo Familiar", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                    }

                    if (state.profiles.isNotEmpty()) {
                        OutlinedButton(
                            onClick = { showConfirmClearProfiles = true },
                            border = BorderStroke(1.dp, Color(0xFFF87171)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Limpar Família", tint = Color(0xFFF87171), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Limpar", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF87171))
                        }
                    }
                }
            }
        }

        // --- 3. LISTA DE FICHAS FAMILIARES COM BOTÃO DE EDIÇÃO ---
        if (state.profiles.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFF334155)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                    ) {
                        Text(
                            text = "Nenhum membro familiar cadastrado.",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Cadastre os adultos e crianças da sua família para cálculo exato de água e fichas médicas.",
                            color = Color(0xFFCBD5E1),
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = onOpenAddProfileDialog,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFF59E0B),
                                contentColor = Color(0xFF090D16)
                            )
                        ) {
                            Icon(Icons.Default.PersonAdd, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Cadastrar Familiar", fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
            }
        } else {
            items(state.profiles, key = { it.id }) { profile ->
                FamilyVaultProfileCard(
                    profile = profile,
                    crisisMode = crisisMode,
                    onEdit = { editingProfile = profile },
                    onDelete = { viewModel.deleteProfile(profile.id) }
                )
            }
        }

        // --- 4. SEÇÃO DE RECEITAS MÉDICAS & DOCUMENTAÇÃO DA FAMÍLIA (PDF) ---
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (crisisMode) Color.Black else Color(0xFF0C1928)
                ),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.5.dp, Color(0xFF38BDF8)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("family_documents_section")
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Default.PictureAsPdf,
                                contentDescription = null,
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Receitas Médicas & Documentos (PDF)",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 14.sp,
                                    color = Color.White
                                )
                                Text(
                                    text = "${state.familyDocuments.size} arquivo(s) salvos no Cofre Offline",
                                    fontSize = 11.sp,
                                    color = Color(0xFF38BDF8)
                                )
                            }
                        }

                        Button(
                            onClick = { familyPdfPickerLauncher.launch("application/pdf") },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF0284C7),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            modifier = Modifier.testTag("btn_import_family_pdf")
                        ) {
                            Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(5.dp))
                            Text("Importar PDF", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Guarde aqui receitas médicas de uso contínuo, laudos, carteiras de vacinação e cópias de RG/CPF/Certidões em PDF para acesso sem internet.",
                        fontSize = 11.sp,
                        color = Color(0xFFCBD5E1)
                    )

                    if (state.familyDocuments.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            state.familyDocuments.forEach { doc ->
                                FamilyDocumentRowCard(
                                    doc = doc,
                                    onOpen = { viewingFamilyDoc = doc },
                                    onDelete = { viewModel.deleteFamilyDocument(doc.id) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // --- 5. CALCULADORA PEDIÁTRICA RÁPIDA DE EMERGÊNCIA ---
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

        // --- 6. CARD DE BACKUP AUTOMÁTICO EXTERNO & RECUPERAÇÃO DE DADOS ---
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (crisisMode) Color.Black else Color(0xFF06241B)
                ),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.5.dp, Color(0xFF10B981)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("family_backup_card")
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Default.Backup,
                                contentDescription = null,
                                tint = Color(0xFF34D399),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Rotina de Backup Seguro (Sem Entupir)",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 14.sp,
                                    color = Color.White
                                )
                                val latestSnap = state.availableBackups.firstOrNull()
                                Text(
                                    text = if (latestSnap != null)
                                        "Último: ${latestSnap.formattedDate} (${latestSnap.sizeKb} KB)"
                                    else
                                        "Pasta: Download/ResilienceHub_Backups (Máx 3 arquivos)",
                                    fontSize = 11.sp,
                                    color = Color(0xFF34D399)
                                )
                            }
                        }

                        Button(
                            onClick = { showBackupModal = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF10B981),
                                contentColor = Color(0xFF090D16)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text("Gerenciar / Restaurar", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Seus dados (Estoque, Família, Pontos do Mapa e PDFs) são salvos automaticamente em formato leve (.zip) fora da pasta interna do app, mantendo apenas os 3 backups mais recentes para proteger contra desinstalação sem ocupar espaço.",
                        fontSize = 11.sp,
                        color = Color(0xFFE2E8F0)
                    )
                }
            }
        }
    }

    if (showBackupModal) {
        BackupRestoreDialog(
            state = state,
            viewModel = viewModel,
            onDismiss = { showBackupModal = false }
        )
    }

    // Modal para Editar Familiar
    editingProfile?.let { profileToEdit ->
        EditProfileDialog(
            profile = profileToEdit,
            onDismiss = { editingProfile = null },
            onConfirm = { id, name, role, weightKg, bloodType, allergies, medications ->
                viewModel.updateProfile(id, name, role, weightKg, bloodType, allergies, medications)
            }
        )
    }

    // Modal para Configurar PDF Importado (Receita / Documento)
    pendingPdfUri?.let { uri ->
        ImportFamilyDocDialog(
            pendingUri = uri,
            defaultTitle = pendingPdfDefaultTitle,
            familyMembers = state.profiles,
            onDismiss = { pendingPdfUri = null },
            onConfirm = { confirmedUri, title, docType, memberName ->
                viewModel.importFamilyDocumentPdf(confirmedUri, title, docType, memberName)
            }
        )
    }

    // Modal Visualizador de PDF Offline
    viewingFamilyDoc?.let { doc ->
        FamilyPdfViewerDialog(
            document = doc,
            onDismiss = { viewingFamilyDoc = null }
        )
    }

    if (showConfirmClearProfiles) {
        ConfirmClearDialog(
            title = "Limpar Fichas Familiares?",
            message = "Todos os perfis familiares cadastrados serão apagados para que você cadastre os membros da sua família.",
            confirmButtonText = "Apagar Perfis",
            onDismiss = { showConfirmClearProfiles = false },
            onConfirm = { viewModel.clearAllProfiles() }
        )
    }
}

@Composable
private fun FamilyDocumentRowCard(
    doc: FamilyDocument,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    val badgeLabel = when (doc.docType) {
        "RECEITA_MEDICA" -> "💊 Receita Médica"
        "DOCUMENTO_PESSOAL" -> "🪪 Documento"
        "LAUDO_EXAME" -> "📋 Exame / Laudo"
        "VACINACAO" -> "💉 Vacinação"
        else -> "📄 PDF"
    }

    Surface(
        color = Color(0xFF111827),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, Color(0xFF1E293B)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen() }
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        color = Color(0xFF0F2942),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(0.5.dp, Color(0xFF38BDF8))
                    ) {
                        Text(
                            text = badgeLabel,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF38BDF8),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }

                    Surface(
                        color = Color(0xFF1E293B),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "👤 ${doc.memberName}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFBBF24),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = onOpen,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            Icons.Default.Visibility,
                            contentDescription = "Abrir PDF",
                            tint = Color(0xFF34D399),
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "Abrir (${doc.pageCount} pág)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF34D399)
                        )
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = "Remover PDF",
                            tint = Color(0xFFF87171),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = doc.title,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 14.sp,
                color = Color.White
            )

            if (doc.extractedText.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = doc.extractedText,
                    fontSize = 11.sp,
                    color = Color(0xFFCBD5E1),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
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
    crisisMode: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit
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
            .clickable { onEdit() }
            .testTag("family_profile_${profile.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
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

                Row(verticalAlignment = Alignment.CenterVertically) {
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

                    Spacer(modifier = Modifier.width(6.dp))

                    // Botão de Editar Familiar
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier
                            .size(28.dp)
                            .testTag("btn_edit_profile_${profile.id}")
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Editar Familiar",
                            tint = Color(0xFFFBBF24),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(2.dp))

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .size(28.dp)
                            .testTag("btn_delete_profile_${profile.id}")
                    ) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = "Apagar Familiar",
                            tint = Color(0xFFF87171),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "${profile.role.displayName} • Peso: ${profile.weightKg.toInt()} kg",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF38BDF8)
                )
                Text(
                    text = "Toque para editar ✎",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF94A3B8)
                )
            }

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
