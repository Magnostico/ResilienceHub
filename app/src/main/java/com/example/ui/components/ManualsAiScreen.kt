package com.example.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import com.example.ui.MainUiState
import com.example.ui.MainViewModel

@Composable
fun ManualsAiScreen(
    state: MainUiState,
    viewModel: MainViewModel,
    onOpenAddManualDialog: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val crisisMode = state.crisisMode
    val quickTopics = listOf("Clorin", "Água", "Torniquete", "Bateria", "Evacuação", "Regra dos Três")
    var ragQuestionInput by remember { mutableStateOf("") }
    var showConfirmClearManuals by remember { mutableStateOf(false) }

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.importPdfFromUri(it) }
    }

    val modelPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.importModelFromUri(it) }
    }

    // Tela inteira unificada em um único LazyColumn para scroll fluido sem comprimir os resultados
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 88.dp),
        modifier = modifier
            .fillMaxSize()
            .testTag("manuals_ai_screen")
    ) {
        // --- 1. AÇÕES DO TOPO: CRIAR MANUAL, IMPORTAR PDF, MODELO IA & SWITCH MODO IA LOCAL ---
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (crisisMode) Color.Black else Color(0xFF111827)
                ),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFF334155)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Button(
                                onClick = onOpenAddManualDialog,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFF59E0B),
                                    contentColor = Color(0xFF090D16)
                                ),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier.testTag("btn_create_manual")
                            ) {
                                Icon(Icons.Default.NoteAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Novo Manual", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                            }

                            Button(
                                onClick = { pdfPickerLauncher.launch("application/pdf") },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF0284C7),
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier.testTag("btn_import_pdf")
                            ) {
                                Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("PDF", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = { modelPickerLauncher.launch("*/*") },
                                border = BorderStroke(1.dp, Color(0xFFFBBF24)),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                modifier = Modifier.testTag("btn_import_model")
                            ) {
                                Icon(Icons.Default.Memory, contentDescription = null, tint = Color(0xFFFBBF24), modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(".task", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFBBF24))
                            }
                        }

                        // Chave para ativar IA Local (MediaPipe LLM / RAG)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (state.ragModeAi) "IA Local" else "FTS",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (state.ragModeAi) Color(0xFFFBBF24) else Color(0xFF34D399)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Switch(
                                checked = state.ragModeAi,
                                onCheckedChange = { viewModel.setRagModeAi(it) },
                                modifier = Modifier.testTag("switch_rag_mode")
                            )
                        }
                    }

                    // Status de importação
                    state.pdfImportStatus?.let { status ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            color = Color(0xFF0C2434),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0xFF38BDF8)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = status,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            }
        }

        if (state.ragModeAi) {
            // --- 2A. MODO IA LOCAL (RAG COM PROMPT ESTRITO EM TÓPICOS) ---
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (crisisMode) Color.Black else Color(0xFF111827)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFFFBBF24)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Psychology,
                                contentDescription = null,
                                tint = Color(0xFFFBBF24),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Assistente de Emergência Civil Offline (RAG Local)",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp,
                                color = Color(0xFFFBBF24)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Responde com base nos seus manuais e PDFs cadastrados (Gemma 4 / Motor Extrativo Offline).",
                            fontSize = 12.sp,
                            color = Color(0xFFCBD5E1)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = ragQuestionInput,
                                onValueChange = { ragQuestionInput = it },
                                placeholder = {
                                    Text(
                                        "Ex: Como purificar água com Clorin?",
                                        fontSize = 12.sp,
                                        color = Color(0xFF94A3B8)
                                    )
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = Color(0xFFFBBF24),
                                    unfocusedBorderColor = Color(0xFF475569)
                                ),
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("input_rag_question")
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = { viewModel.askRagQuestion(ragQuestionInput) },
                                enabled = ragQuestionInput.isNotBlank() && !state.isRagLoading,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFF59E0B),
                                    contentColor = Color(0xFF090D16)
                                ),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
                                modifier = Modifier.testTag("btn_ask_rag")
                            ) {
                                if (state.isRagLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.5.dp,
                                        color = Color(0xFF090D16)
                                    )
                                } else {
                                    Icon(Icons.Default.Send, contentDescription = "Consultar")
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Sugestões rápidas também no modo IA Local
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(quickTopics) { topic ->
                                SuggestionChip(
                                    onClick = {
                                        ragQuestionInput = topic
                                        viewModel.askRagQuestion(topic)
                                    },
                                    label = {
                                        Text(
                                            topic,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    },
                                    colors = SuggestionChipDefaults.suggestionChipColors(
                                        containerColor = Color(0xFF1E293B)
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // Resposta gerada pela IA Local com Alto Contraste
            state.ragResponse?.let { rag ->
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (crisisMode) Color(0xFF03140D) else Color(0xFF062E22)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.5.dp, Color(0xFF34D399)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Diretriz Oficial Sintetizada:",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 14.sp,
                                    color = Color(0xFF34D399)
                                )
                                Surface(
                                    color = Color(0xFF090D16),
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(1.dp, Color(0xFF34D399))
                                ) {
                                    Text(
                                        text = rag.executionMode,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF34D399),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = rag.answer,
                                fontSize = 13.sp,
                                lineHeight = 20.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = "Auditoria Visual (Fontes Utilizadas):",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 12.sp,
                                color = Color(0xFFFBBF24)
                            )
                            Spacer(modifier = Modifier.height(4.dp))

                            rag.citedSources.forEach { s ->
                                Surface(
                                    color = Color(0xFF090D16),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, Color(0xFF1E293B)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text(
                                            text = "📄 ${s.docTitle} (Página ${s.pageNumber} • ${s.category}):",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF38BDF8)
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "\"${s.contentText}\"",
                                            fontSize = 11.sp,
                                            lineHeight = 16.sp,
                                            color = Color(0xFFE2E8F0)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // --- 2B. MODO RÁPIDO (FTS4 ZERO BATERIA) ---
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = state.manualQuery,
                        onValueChange = { viewModel.setManualQuery(it) },
                        label = {
                            Text(
                                "Pesquisa Rápida FTS4 (Zero Consumo)",
                                color = Color(0xFFCBD5E1)
                            )
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = "Buscar", tint = Color(0xFFFBBF24))
                        },
                        trailingIcon = {
                            if (state.manualQuery.isNotBlank()) {
                                IconButton(onClick = { viewModel.setManualQuery("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Limpar", tint = Color.White)
                                }
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFFFBBF24),
                            unfocusedBorderColor = Color(0xFF475569)
                        ),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_fts_search")
                    )

                    // Chips rápidos de termos frequentes
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(quickTopics) { topic ->
                            SuggestionChip(
                                onClick = { viewModel.setManualQuery(topic) },
                                label = {
                                    Text(
                                        topic,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                },
                                icon = {
                                    Icon(
                                        Icons.Default.BookmarkBorder,
                                        contentDescription = null,
                                        tint = Color(0xFFFBBF24),
                                        modifier = Modifier.size(14.dp)
                                    )
                                },
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = Color(0xFF1E293B)
                                )
                            )
                        }
                    }
                }
            }
        }

        // --- 3. CABEÇALHO E BOTÃO DE LIMPAR MANUAIS ---
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "${state.manualChunks.size} protocolo(s) / manual(is) cadastrado(s):",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )

                if (state.manualChunks.isNotEmpty()) {
                    TextButton(
                        onClick = { showConfirmClearManuals = true },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = Color(0xFFF87171), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Limpar Manuais", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF87171))
                    }
                }
            }
        }

        if (state.manualChunks.isEmpty()) {
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
                            text = "Nenhum manual ou protocolo cadastrado.",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Crie seu próprio manual/protocolo ou importe um arquivo PDF.",
                            color = Color(0xFFCBD5E1),
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = onOpenAddManualDialog,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFF59E0B),
                                contentColor = Color(0xFF090D16)
                            )
                        ) {
                            Icon(Icons.Default.NoteAdd, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Criar Meu Primeiro Manual", fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
            }
        } else {
            items(state.manualChunks, key = { it.rowId }) { chunk ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (crisisMode) Color.Black else Color(0xFF111827)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(
                        1.dp,
                        if (crisisMode) Color(0xFF3F3F46) else Color(0xFF334155)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    color = Color(0xFF0F2942),
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(0.5.dp, Color(0xFF38BDF8))
                                ) {
                                    Text(
                                        text = chunk.category,
                                        color = Color(0xFF38BDF8),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Pág. ${chunk.pageNumber}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFFFBBF24)
                                )
                            }

                            IconButton(
                                onClick = { viewModel.deleteManualChunk(chunk.rowId) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    Icons.Default.DeleteOutline,
                                    contentDescription = "Apagar Manual",
                                    tint = Color(0xFFF87171),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = chunk.docTitle,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp,
                            color = Color.White
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = chunk.contentText,
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            color = Color(0xFFE2E8F0)
                        )
                    }
                }
            }
        }
    }

    if (showConfirmClearManuals) {
        ConfirmClearDialog(
            title = "Apagar Todos os Manuais?",
            message = "Todos os manuais e trechos fictícios serão apagados. Você poderá criar seus próprios protocolos ou importar novos PDFs.",
            confirmButtonText = "Apagar Manuais",
            onDismiss = { showConfirmClearManuals = false },
            onConfirm = { viewModel.clearAllManualChunks() }
        )
    }
}
