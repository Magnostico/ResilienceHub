package com.example.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.ChatMessage
import com.example.ui.MainUiState
import com.example.ui.MainViewModel

private data class QuickSurvivalPrompt(
    val label: String,
    val fullQuestion: String
)

private val OFFICIAL_QUICK_PROMPTS = listOf(
    QuickSurvivalPrompt("⚠️ Guerra / Bombardeio", "Como proteger minha família durante um bombardeio, mísseis ou tiroteio?"),
    QuickSurvivalPrompt("☢️ Ameaça Nuclear", "O que fazer nos primeiros 10 minutos de uma explosão nuclear e fallout radioativo?"),
    QuickSurvivalPrompt("💧 Purificar Água", "Como purificar água com hipoclorito 2,5%, Clorin ou fervura?"),
    QuickSurvivalPrompt("🩸 Hemorragia / Torniquete", "Como estancar sangramento grave e aplicar torniquete tático?"),
    QuickSurvivalPrompt("👶 Engasgo Bebê / Adulto", "Como desengasgar um bebê menor de 1 ano e um adulto (Heimlich)?"),
    QuickSurvivalPrompt("❤️ Parada Cardíaca (RCP)", "Como fazer massagem cardíaca RCP em adulto, criança e bebê?"),
    QuickSurvivalPrompt("🥄 Soro Caseiro (OMS)", "Qual a receita exata do Soro Caseiro da OMS para diarreia e desidratação?"),
    QuickSurvivalPrompt("🌊 Enchente / Deslizamento", "Como agir em enchente e quais os sinais de deslizamento de encosta?"),
    QuickSurvivalPrompt("🪣 Banheiro Seco", "Como fazer um banheiro seco de emergência quando acaba a água e o esgoto?"),
    QuickSurvivalPrompt("⚡ Apagão & Gaiola Faraday", "Como fazer uma Gaiola de Faraday contra EMP e agir em apagão elétrico?"),
    QuickSurvivalPrompt("🐍 Cobras e Escorpiões", "O que fazer imediatamente em picada de cobra ou escorpião?"),
    QuickSurvivalPrompt("🔥 Queimaduras / Incêndio", "Como tratar queimaduras graves e escapar de incêndio com fumaça?")
)

@Composable
fun ManualsAiScreen(
    state: MainUiState,
    viewModel: MainViewModel,
    onOpenAddManualDialog: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val crisisMode = state.crisisMode
    var chatInput by remember { mutableStateOf("") }
    // true = Chat Conversacional IA (Padrão), false = Explorar Base Oficial Completa
    var showChatView by remember { mutableStateOf(true) }
    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current

    val modelPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.importModelFromUri(it) }
    }

    // Rola automaticamente para a última mensagem sempre que uma nova resposta chegar no Chat
    LaunchedEffect(state.chatMessages.size, state.isRagLoading) {
        if (showChatView && state.chatMessages.isNotEmpty()) {
            listState.animateScrollToItem(state.chatMessages.size - 1)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("manuals_ai_screen")
    ) {
        // --- 1. BARRA SUPERIOR DO CHAT DE SOBREVIVÊNCIA OFICIAL ---
        Surface(
            color = if (crisisMode) Color.Black else Color(0xFF111827),
            border = BorderStroke(1.dp, Color(0xFF1E293B)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Alternador entre Chat IA e Base Oficial
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = showChatView,
                            onClick = { showChatView = true },
                            label = {
                                Text(
                                    "💬 Chat IA Oficial",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (showChatView) Color(0xFF090D16) else Color.White
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFFBBF24),
                                containerColor = Color(0xFF1E293B)
                            ),
                            modifier = Modifier.testTag("tab_chat_view")
                        )

                        FilterChip(
                            selected = !showChatView,
                            onClick = { showChatView = false },
                            label = {
                                Text(
                                    "📚 Base Oficial (${state.manualChunks.size})",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (!showChatView) Color(0xFF090D16) else Color(0xFFCBD5E1)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF34D399),
                                containerColor = Color(0xFF1E293B)
                            ),
                            modifier = Modifier.testTag("tab_knowledge_base_view")
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (showChatView && state.chatMessages.size > 1) {
                            IconButton(
                                onClick = { viewModel.clearChatHistory() },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = "Nova Conversa",
                                    tint = Color(0xFFCBD5E1),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = { modelPickerLauncher.launch("*/*") },
                            border = BorderStroke(1.dp, Color(0xFF334155)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier
                                .height(30.dp)
                                .testTag("btn_import_model")
                        ) {
                            Icon(
                                Icons.Default.Memory,
                                contentDescription = null,
                                tint = Color(0xFFFBBF24),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("LLM .task", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFBBF24))
                        }
                    }
                }

                // Selo de Órgãos Oficiais
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(Color(0xFF34D399), CircleShape)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "RAG 100% Offline: Defesa Civil • OMS • Ministério da Saúde • Cruz Vermelha (CICV) • FEMA • CNEN",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF94A3B8)
                    )
                }

                state.pdfImportStatus?.let { status ->
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        color = Color(0xFF0C2434),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color(0xFF38BDF8)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = status,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            }
        }

        if (showChatView) {
            // =========================================================================
            // VISÃO 1: CHAT CONVERSACIONAL DE SOBREVIVÊNCIA (PADRÃO)
            // =========================================================================
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                items(state.chatMessages, key = { it.id }) { msg ->
                    ChatMessageBubble(
                        message = msg,
                        crisisMode = crisisMode
                    )
                }

                if (state.isRagLoading) {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .padding(8.dp)
                                .background(Color(0xFF111827), RoundedCornerShape(12.dp))
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = Color(0xFF34D399)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Consultando protocolos dos órgãos oficiais...",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF34D399)
                            )
                        }
                    }
                }
            }

            // --- BARRA DE TEMAS RÁPIDOS DE EMERGÊNCIA + CAMPO DE MENSAGEM ---
            Surface(
                color = if (crisisMode) Color.Black else Color(0xFF0F172A),
                border = BorderStroke(1.dp, Color(0xFF1E293B)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                    Text(
                        text = "Toque rápido em uma emergência ou digite sua pergunta:",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFBBF24),
                        modifier = Modifier.padding(bottom = 4.dp, start = 2.dp)
                    )

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        items(OFFICIAL_QUICK_PROMPTS) { prompt ->
                            SuggestionChip(
                                onClick = {
                                    viewModel.askRagQuestion(prompt.fullQuestion)
                                    focusManager.clearFocus()
                                },
                                label = {
                                    Text(
                                        text = prompt.label,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                },
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = Color(0xFF1E293B)
                                ),
                                border = SuggestionChipDefaults.suggestionChipBorder(
                                    enabled = true,
                                    borderColor = Color(0xFF334155)
                                )
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = chatInput,
                            onValueChange = { chatInput = it },
                            placeholder = {
                                Text(
                                    "Pergunte à IA (ex: como agir em bombardeio, purificar água...)",
                                    fontSize = 12.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFFFBBF24),
                                unfocusedBorderColor = Color(0xFF475569),
                                focusedContainerColor = Color(0xFF090D16),
                                unfocusedContainerColor = Color(0xFF090D16)
                            ),
                            shape = RoundedCornerShape(22.dp),
                            maxLines = 3,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(
                                onSend = {
                                    if (chatInput.isNotBlank() && !state.isRagLoading) {
                                        viewModel.askRagQuestion(chatInput)
                                        chatInput = ""
                                        focusManager.clearFocus()
                                    }
                                }
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_rag_question")
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        FloatingActionButton(
                            onClick = {
                                if (chatInput.isNotBlank() && !state.isRagLoading) {
                                    viewModel.askRagQuestion(chatInput)
                                    chatInput = ""
                                    focusManager.clearFocus()
                                }
                            },
                            containerColor = if (chatInput.isNotBlank()) Color(0xFFF59E0B) else Color(0xFF334155),
                            contentColor = if (chatInput.isNotBlank()) Color(0xFF090D16) else Color(0xFF94A3B8),
                            shape = CircleShape,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("btn_ask_rag")
                        ) {
                            Icon(
                                Icons.Default.Send,
                                contentDescription = "Enviar Mensagem"
                            )
                        }
                    }
                }
            }
        } else {
            // =========================================================================
            // VISÃO 2: CATÁLOGO COMPLETO DA BASE OFICIAL DOS ÓRGÃOS OFICIAIS
            // =========================================================================
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 88.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                item {
                    OutlinedTextField(
                        value = state.manualQuery,
                        onValueChange = { viewModel.setManualQuery(it) },
                        label = {
                            Text(
                                "Filtrar nas ${state.manualChunks.size} diretrizes oficiais...",
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
                }

                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "${state.manualChunks.size} diretrizes oficiais indexadas no RAG:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF34D399)
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TextButton(
                                onClick = onOpenAddManualDialog,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Icon(Icons.Default.NoteAdd, contentDescription = null, tint = Color(0xFFFBBF24), modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("+ Nota Familiar", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFBBF24))
                            }

                            TextButton(
                                onClick = { viewModel.restoreOfficialKnowledgeBase() },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Icon(Icons.Default.Restore, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Restaurar Base", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8))
                            }
                        }
                    }
                }

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

                                TextButton(
                                    onClick = {
                                        showChatView = true
                                        viewModel.askRagQuestion(chunk.docTitle)
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                ) {
                                    Text(
                                        "Perguntar no Chat →",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFFBBF24)
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
                                fontSize = 12.sp,
                                lineHeight = 18.sp,
                                color = Color(0xFFE2E8F0)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatMessageBubble(
    message: ChatMessage,
    crisisMode: Boolean
) {
    var showSources by remember { mutableStateOf(false) }

    Column(
        horizontalAlignment = if (message.isUser) Alignment.End else Alignment.Start,
        modifier = Modifier.fillMaxWidth()
    ) {
        Surface(
            color = when {
                message.isUser -> Color(0xFFF59E0B)
                crisisMode -> Color(0xFF051811)
                else -> Color(0xFF111827)
            },
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (message.isUser) 16.dp else 4.dp,
                bottomEnd = if (message.isUser) 4.dp else 16.dp
            ),
            border = BorderStroke(
                1.dp,
                if (message.isUser) Color(0xFFFBBF24) else Color(0xFF10B981)
            ),
            modifier = Modifier.widthIn(max = 340.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // Cabeçalho do balão
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (message.isUser) Icons.Default.Person else Icons.Default.Shield,
                            contentDescription = null,
                            tint = if (message.isUser) Color(0xFF090D16) else Color(0xFF34D399),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = if (message.isUser) "Você" else "IA Defesa Civil & Sobrevivência",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (message.isUser) Color(0xFF090D16) else Color(0xFF34D399)
                        )
                    }

                    Text(
                        text = message.timestamp,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (message.isUser) Color(0xFF1E293B) else Color(0xFF94A3B8)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Texto da mensagem
                Text(
                    text = message.text.replace("**", ""),
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    fontWeight = if (message.isUser) FontWeight.Bold else FontWeight.Medium,
                    color = if (message.isUser) Color(0xFF090D16) else Color.White
                )

                // Fontes oficiais consultadas pelo RAG
                if (!message.isUser && message.citedSources.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Divider(color = Color(0xFF1E293B))
                    Spacer(modifier = Modifier.height(4.dp))

                    TextButton(
                        onClick = { showSources = !showSources },
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                    ) {
                        Icon(
                            if (showSources) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = Color(0xFFFBBF24),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (showSources)
                                "Ocultar Fontes Oficiais (${message.citedSources.size})"
                            else
                                "Ver Fontes Oficiais Utilizadas (${message.citedSources.size})",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFFFBBF24)
                        )
                    }

                    AnimatedVisibility(visible = showSources) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            message.citedSources.forEach { source ->
                                Surface(
                                    color = Color(0xFF090D16),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, Color(0xFF1E293B)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        Text(
                                            text = "📄 ${source.docTitle} (${source.category})",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color(0xFF38BDF8)
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = source.contentText,
                                            fontSize = 10.sp,
                                            lineHeight = 14.sp,
                                            color = Color(0xFFCBD5E1)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
