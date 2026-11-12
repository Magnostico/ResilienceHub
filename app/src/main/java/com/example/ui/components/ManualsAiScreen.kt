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

private val EDGE_GALLERY_FREE_PROMPTS = listOf(
    QuickSurvivalPrompt("🍞 Receita Pão Caseiro", "Como fazer uma receita de pão caseiro fácil na frigideira ou forno?"),
    QuickSurvivalPrompt("🔋 Cálculo Bateria (Wh)", "Como calcular quantos Watts e horas dura uma bateria ou painel solar?"),
    QuickSurvivalPrompt("🌌 Orientação Estrelas", "Como achar o Sul verdadeiro pelo Cruzeiro do Sul e os planetas do Sistema Solar?"),
    QuickSurvivalPrompt("🚗 Mecânica: Chupeta", "Como fazer partida auxiliar (chupeta) na bateria do carro com segurança?"),
    QuickSurvivalPrompt("🧮 Conta: 18% de 450", "Quanto é 18% de 450?"),
    QuickSurvivalPrompt("🎲 Jogos sem Internet", "Sugira jogos e passatempos em família que não gastam bateria nem internet"),
    QuickSurvivalPrompt("🤖 Como funciona IA Edge", "Como funcionam os modelos locais Gemma .task no Google AI Edge Gallery?")
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
    var showEdgeModelDialog by remember { mutableStateOf(false) }
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
                            onClick = { showEdgeModelDialog = true },
                            border = BorderStroke(1.dp, Color(0xFFA855F7)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier
                                .height(30.dp)
                                .testTag("btn_import_model")
                        ) {
                            Icon(
                                Icons.Default.Memory,
                                contentDescription = null,
                                tint = Color(0xFFC084FC),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Modelo Edge", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFC084FC))
                        }
                    }
                }

                // --- SELETOR DE MODO DA IA LOCAL: MODO RAG vs CHAT LIVRE (ESTILO GOOGLE AI EDGE GALLERY) ---
                if (showChatView) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val isRag = state.ragModeAi
                        Surface(
                            onClick = { viewModel.setRagModeAi(true) },
                            color = if (isRag) Color(0xFF062E22) else Color(0xFF0F172A),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(
                                width = if (isRag) 1.5.dp else 1.dp,
                                color = if (isRag) Color(0xFF34D399) else Color(0xFF334155)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("mode_selector_rag")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                            ) {
                                Icon(
                                    Icons.Default.Shield,
                                    contentDescription = null,
                                    tint = if (isRag) Color(0xFF34D399) else Color(0xFF94A3B8),
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(
                                        text = "Modo RAG (Oficial)",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (isRag) Color(0xFF34D399) else Color.White
                                    )
                                    Text(
                                        text = "Protocolos + Seu Estoque",
                                        fontSize = 9.sp,
                                        color = Color(0xFF94A3B8)
                                    )
                                }
                            }
                        }

                        Surface(
                            onClick = { viewModel.setRagModeAi(false) },
                            color = if (!isRag) Color(0xFF2E1065) else Color(0xFF0F172A),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(
                                width = if (!isRag) 1.5.dp else 1.dp,
                                color = if (!isRag) Color(0xFFA855F7) else Color(0xFF334155)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("mode_selector_free_edge")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                            ) {
                                Icon(
                                    Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = if (!isRag) Color(0xFFC084FC) else Color(0xFF94A3B8),
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(
                                        text = "Chat Livre (Edge Gallery)",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (!isRag) Color(0xFFC084FC) else Color.White
                                    )
                                    Text(
                                        text = "Conversa Aberta 100% Offline",
                                        fontSize = 9.sp,
                                        color = Color(0xFFCBD5E1)
                                    )
                                }
                            }
                        }
                    }
                }

                // Selo de Status Offline
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(if (state.ragModeAi) Color(0xFF34D399) else Color(0xFFC084FC), CircleShape)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (state.ragModeAi)
                            "100% Offline • RAG Ativo: Defesa Civil • OMS • Min. Saúde • Cruz Vermelha • FEMA"
                        else
                            "100% Offline • Modo Google AI Edge Gallery: Converse livremente sobre qualquer assunto",
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
                        crisisMode = crisisMode,
                        onConfirmFreeTopic = { query -> viewModel.confirmFreeTopicQuestion(query) },
                        onRejectFreeTopic = { viewModel.rejectFreeTopicQuestion() },
                        onRequestMoreDetails = { query, isFree -> viewModel.requestMoreDetailsOnTopic(query, isFree) }
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

            // --- BARRA DE TEMAS RÁPIDOS + CAMPO DE MENSAGEM ---
            val activeQuickPrompts = if (state.ragModeAi) OFFICIAL_QUICK_PROMPTS else EDGE_GALLERY_FREE_PROMPTS
            Surface(
                color = if (crisisMode) Color.Black else Color(0xFF0F172A),
                border = BorderStroke(1.dp, Color(0xFF1E293B)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                    Text(
                        text = if (state.ragModeAi)
                            "Toque rápido em uma emergência (Modo RAG) ou digite sua pergunta:"
                        else
                            "Sugestões de Chat Livre (Estilo Edge Gallery 100% Offline):",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (state.ragModeAi) Color(0xFFFBBF24) else Color(0xFFC084FC),
                        modifier = Modifier.padding(bottom = 4.dp, start = 2.dp)
                    )

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        items(activeQuickPrompts) { prompt ->
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
                                    if (state.ragModeAi)
                                        "Pergunte no Modo RAG (ex: purificar água, meu estoque...)"
                                    else
                                        "Converse livremente 100% offline (ciência, receitas, contas...)",
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

    if (showEdgeModelDialog) {
        AlertDialog(
            onDismissRequest = { showEdgeModelDialog = false },
            containerColor = Color(0xFF111827),
            titleContentColor = Color.White,
            textContentColor = Color.White,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Memory, contentDescription = null, tint = Color(0xFFC084FC))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Motor IA Local (Edge Gallery)", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        color = Color(0xFF090D16),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFF34D399)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "✓ Status do Motor 100% Offline:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF34D399)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = viewModel.getLocalModelStatusText(),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    Text(
                        text = "• O app já conta com um Motor Cognitivo & Enciclopédico 100% Offline nativo embutido para responder tanto no Modo RAG quanto no Modo Chat Livre sem precisar de internet.\n" +
                            "• Se você tiver um arquivo de modelo do Google AI Edge Gallery (.task, .litertlm ou .bin, como Gemma-2B-IT ou Gemma-3-1B) no celular, pode carregá-lo abaixo para inferência neural direta via MediaPipe!",
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = Color(0xFFCBD5E1)
                    )

                    Button(
                        onClick = {
                            showEdgeModelDialog = false
                            modelPickerLauncher.launch("*/*")
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFA855F7),
                            contentColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Importar Arquivo .task / .litertlm / .bin", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showEdgeModelDialog = false }) {
                    Text("Fechar", color = Color(0xFFFBBF24), fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@Composable
private fun ChatMessageBubble(
    message: ChatMessage,
    crisisMode: Boolean,
    onConfirmFreeTopic: (String) -> Unit,
    onRejectFreeTopic: () -> Unit,
    onRequestMoreDetails: (String, Boolean) -> Unit
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
                when {
                    message.isUser -> Color(0xFFFBBF24)
                    message.requiresFreeTopicConfirmation -> Color(0xFF38BDF8)
                    message.isFreeTopic -> Color(0xFFA855F7)
                    else -> Color(0xFF10B981)
                }
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
                            imageVector = when {
                                message.isUser -> Icons.Default.Person
                                message.isFreeTopic -> Icons.Default.AutoAwesome
                                else -> Icons.Default.Shield
                            },
                            contentDescription = null,
                            tint = when {
                                message.isUser -> Color(0xFF090D16)
                                message.isFreeTopic -> Color(0xFFC084FC)
                                else -> Color(0xFF34D399)
                            },
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = when {
                                message.isUser -> "Você"
                                message.isFreeTopic -> "IA Modo Tema Livre"
                                else -> "IA Defesa Civil & Sobrevivência"
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = when {
                                message.isUser -> Color(0xFF090D16)
                                message.isFreeTopic -> Color(0xFFC084FC)
                                else -> Color(0xFF34D399)
                            }
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

                // Portão de Confirmação para Tema Livre (Fora do Escopo de Sobrevivência)
                if (!message.isUser && message.requiresFreeTopicConfirmation && !message.pendingFreeTopicQuery.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = { onConfirmFreeTopic(message.pendingFreeTopicQuery) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF10B981),
                                contentColor = Color(0xFF090D16)
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "✅ Sim, responder",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }

                        OutlinedButton(
                            onClick = onRejectFreeTopic,
                            border = BorderStroke(1.dp, Color(0xFF64748B)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "❌ Não, manter foco",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE2E8F0)
                            )
                        }
                    }
                }

                // Botão de 1 toque para expandir detalhes sob demanda
                if (!message.isUser && message.canExpandDetails && !message.topicQueryForExpansion.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { onRequestMoreDetails(message.topicQueryForExpansion, message.isFreeTopic) },
                        border = BorderStroke(1.dp, Color(0xFF38BDF8)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color(0xFF0F172A)
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ZoomIn,
                            contentDescription = null,
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Quero mais detalhes",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF38BDF8)
                        )
                    }
                }

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
