package com.example.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.example.data.entity.ManualChunkFtsEntity
import com.example.ui.MainUiState
import com.example.ui.MainViewModel

@Composable
fun ManualFtsScreen(
    state: MainUiState,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val crisisMode = state.crisisMode
    val quickTopics = listOf("Clorin", "Água", "Torniquete", "Bateria", "Evacuação", "Regra dos Três")
    var ragQuestionInput by remember { mutableStateOf("") }

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.importPdfFromUri(it) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("manual_fts_screen")
    ) {
        // --- Header Actions: PDF Import & Search Mode Switch ---
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
        ) {
            FilledTonalButton(
                onClick = { pdfPickerLauncher.launch("application/pdf") },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.testTag("btn_import_pdf")
            ) {
                Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Importar PDF", fontSize = 12.sp)
            }

            // Mode Selector: Rápido (FTS) vs IA Local (RAG)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (state.ragModeAi) "Modo IA Local (RAG)" else "Modo Rápido (Zero Bateria)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (state.ragModeAi) MaterialTheme.colorScheme.primary else Color(0xFF10B981)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Switch(
                    checked = state.ragModeAi,
                    onCheckedChange = { viewModel.setRagModeAi(it) },
                    modifier = Modifier.testTag("switch_rag_mode")
                )
            }
        }

        // PDF Import notification status
        state.pdfImportStatus?.let { status ->
            Surface(
                color = if (crisisMode) Color(0xFF03101A) else MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
            ) {
                Text(
                    text = status,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (crisisMode) Color.White else MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }

        if (state.ragModeAi) {
            // =================================================================
            // MODO B: IA LOCAL (RAG) - PROMPT ESTRITO EM TÓPICOS
            // =================================================================
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (crisisMode) Color(0xFF080808) else MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (crisisMode) Color(0xFF27272A) else MaterialTheme.colorScheme.outlineVariant
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Assistente de Emergência Civil Offline (RAG)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Recupera os 3 trechos mais relevantes do FTS e responde estritamente com base neles em tópicos.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = ragQuestionInput,
                            onValueChange = { ragQuestionInput = it },
                            placeholder = { Text("Ex: Como tratar água ou estancar hemorragia?", fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_rag_question")
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Button(
                            onClick = { viewModel.askRagQuestion(ragQuestionInput) },
                            enabled = ragQuestionInput.isNotBlank() && !state.isRagLoading,
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            modifier = Modifier.testTag("btn_ask_rag")
                        ) {
                            if (state.isRagLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Icon(Icons.Default.Send, contentDescription = "Consultar")
                            }
                        }
                    }
                }
            }

            // RAG Answer View
            state.ragResponse?.let { rag ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (crisisMode) Color(0xFF04140B) else Color(0xFFF0FDF4)
                    ),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Resposta Oficial Estruturada:",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color(0xFF10B981)
                            )
                            Surface(
                                color = Color(0xFF10B981).copy(alpha = 0.2f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = rag.executionMode,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF10B981),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = rag.answer,
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Fontes Oficiais Consultadas:",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        rag.citedSources.forEach { s ->
                            Text(
                                text = "• ${s.docTitle} (Pág. ${s.pageNumber})",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        } else {
            // =================================================================
            // MODO A: RÁPIDO (FTS4 ZERO BATERIA)
            // =================================================================
            OutlinedTextField(
                value = state.manualQuery,
                onValueChange = { viewModel.setManualQuery(it) },
                label = { Text("Pesquisa Rápida FTS4 (Zero Consumo)") },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = "Buscar")
                },
                trailingIcon = {
                    if (state.manualQuery.isNotBlank()) {
                        IconButton(onClick = { viewModel.setManualQuery("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Limpar")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
                    .testTag("input_fts_search")
            )

            // Chips
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                items(quickTopics) { topic ->
                    SuggestionChip(
                        onClick = { viewModel.setManualQuery(topic) },
                        label = { Text(topic) },
                        icon = {
                            Icon(
                                Icons.Default.BookmarkBorder,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    )
                }
            }
        }

        // --- Chunks Count & List ---
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp)
        ) {
            Text(
                text = "${state.manualChunks.size} trecho(s) indexado(s) em SQLite FTS4",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 80.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(state.manualChunks, key = { it.rowId }) { chunk ->
                ManualChunkCard(chunk = chunk, crisisMode = crisisMode)
            }
        }
    }
}

@Composable
fun ManualChunkCard(
    chunk: ManualChunkFtsEntity,
    crisisMode: Boolean
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (crisisMode) Color(0xFF080808) else MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (crisisMode) Color(0xFF27272A) else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = chunk.category,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Text(
                    text = "Pág. ${chunk.pageNumber}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = chunk.docTitle,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = chunk.contentText,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
