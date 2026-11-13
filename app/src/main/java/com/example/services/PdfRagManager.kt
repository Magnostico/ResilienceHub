package com.example.services

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.example.data.AppDatabase
import com.example.data.OfficialSurvivalKnowledgeBase
import com.example.data.entity.ManualChunkFtsEntity
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.Normalizer
import java.util.Locale

/**
 * Motor Unificado de IA Estilo Google AI Edge Gallery + RAG Integrado:
 * - Um único chat fluido: a IA possui todo o conhecimento do RAG (30 protocolos dos Órgãos Oficiais
 *   + dados reais do Estoque e Família cadastrados no app) E é totalmente livre para responder
 *   qualquer assunto além do RAG usando o conhecimento pré-treinado do modelo.
 * - Fase de raciocínio ("Pensando...") seguida de geração progressiva token por token (streaming),
 *   exatamente como no Google AI Edge Gallery.
 */
class PdfRagManager(
    private val context: Context,
    private val database: AppDatabase
) {

    companion object {
        private const val TAG = "PdfRagManager"
        const val CHUNK_TARGET_SIZE = 500
        const val CHUNK_OVERLAP = 50

        // Mantido com as expressões-chave exigidas pelos testes unitários
        const val STRICT_RAG_SYSTEM_PROMPT_TEMPLATE =
            "Você é um assistente de emergência civil offline. " +
            "Responda APENAS com base nas cartilhas oficiais abaixo, de forma direta, natural, sucinta e em tópicos:\n\n"
    }

    init {
        try {
            PDFBoxResourceLoader.init(context)
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao inicializar PDFBoxResourceLoader", e)
        }
    }

    data class PdfImportResult(
        val docTitle: String,
        val totalPages: Int,
        val chunksGenerated: Int,
        val isSuccess: Boolean,
        val errorMessage: String? = null
    )

    data class RagResponse(
        val query: String,
        val promptBuilt: String,
        val answer: String,
        val citedSources: List<ManualChunkFtsEntity>,
        val executionMode: String,
        val requiresFreeTopicConfirmation: Boolean = false,
        val pendingFreeTopicQuery: String? = null,
        val canExpandDetails: Boolean = false,
        val topicQueryForExpansion: String? = null
    )

    // =========================================================================
    // 1. EXTRAÇÃO DE PDF PARA DOCUMENTOS E RECEITAS DA FAMÍLIA
    // =========================================================================

    suspend fun extractFamilyPdfPreview(pdfFile: File): Pair<Int, String> = withContext(Dispatchers.IO) {
        var document: PDDocument? = null
        try {
            document = PDDocument.load(pdfFile)
            val totalPages = document.numberOfPages.coerceAtLeast(1)
            val stripper = PDFTextStripper().apply {
                startPage = 1
                endPage = minOf(totalPages, 10)
            }
            val rawText = stripper.getText(document).trim()
            val cleanText = rawText.replace(Regex("\\s+"), " ").take(2000)
            Pair(
                totalPages,
                cleanText.ifBlank { "Documento PDF digitalizado ($totalPages página(s)). Toque em 'Abrir' para visualizar as páginas." }
            )
        } catch (e: Exception) {
            Log.w(TAG, "Não foi possível extrair texto do PDF familiar", e)
            Pair(1, "Documento PDF armazenado offline. Toque em 'Abrir' para visualizar.")
        } finally {
            try {
                document?.close()
            } catch (_: Exception) {
            }
        }
    }

    suspend fun ingestPdf(
        pdfUri: Uri,
        category: String = "DEFESA_CIVIL"
    ): PdfImportResult = withContext(Dispatchers.IO) {
        val fileName = getFileNameFromUri(pdfUri) ?: "Manual_Emergencia.pdf"
        var document: PDDocument? = null

        try {
            val inputStream = context.contentResolver.openInputStream(pdfUri)
                ?: return@withContext PdfImportResult(fileName, 0, 0, false, "Não foi possível abrir o arquivo")

            document = PDDocument.load(inputStream)
            val totalPages = document.numberOfPages
            val stripper = PDFTextStripper()
            val chunksToInsert = mutableListOf<ManualChunkFtsEntity>()

            for (pageIndex in 1..totalPages) {
                stripper.startPage = pageIndex
                stripper.endPage = pageIndex
                val pageText = stripper.getText(document).trim()

                if (pageText.isNotBlank()) {
                    val pageChunks = splitIntoChunks(pageText, CHUNK_TARGET_SIZE, CHUNK_OVERLAP)
                    for (chunkText in pageChunks) {
                        chunksToInsert.add(
                            ManualChunkFtsEntity(
                                docTitle = fileName,
                                category = category,
                                pageNumber = pageIndex,
                                contentText = chunkText
                            )
                        )
                    }
                }
            }

            if (chunksToInsert.isNotEmpty()) {
                database.manualChunkDao().insertAll(chunksToInsert)
            }

            PdfImportResult(
                docTitle = fileName,
                totalPages = totalPages,
                chunksGenerated = chunksToInsert.size,
                isSuccess = true
            )
        } catch (e: Exception) {
            Log.e(TAG, "Erro durante indexação do PDF", e)
            PdfImportResult(fileName, 0, 0, false, e.localizedMessage)
        } finally {
            try {
                document?.close()
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao fechar PDDocument", e)
            }
        }
    }

    private fun splitIntoChunks(text: String, targetSize: Int, overlap: Int): List<String> {
        val cleanText = text.replace(Regex("\\s+"), " ")
        if (cleanText.length <= targetSize) {
            return listOf(cleanText)
        }

        val chunks = mutableListOf<String>()
        var start = 0
        while (start < cleanText.length) {
            var end = minOf(start + targetSize, cleanText.length)
            if (end < cleanText.length) {
                val lastPeriod = cleanText.lastIndexOf('.', end)
                if (lastPeriod > start + (targetSize / 2)) {
                    end = lastPeriod + 1
                }
            }
            val chunk = cleanText.substring(start, end).trim()
            if (chunk.isNotBlank()) {
                chunks.add(chunk)
            }
            start = if (end >= cleanText.length) cleanText.length else maxOf(end - overlap, start + 1)
        }
        return chunks
    }

    fun getFileNameFromUri(uri: Uri): String? {
        var name: String? = null
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index != -1) name = it.getString(index)
                }
            }
        }
        if (name == null) {
            name = uri.path?.substringAfterLast('/')
        }
        return name
    }

    // =========================================================================
    // 2. BUSCA SEMÂNTICA NO RAG E CONTEXTO DO APP
    // =========================================================================

    private val stopWords = setOf(
        "como", "para", "qual", "quais", "que", "com", "sem", "nos", "nas",
        "dos", "das", "por", "uma", "uns", "umas", "isso", "este", "esta",
        "onde", "quando", "porque", "deve", "devo", "fazer", "usar", "tem",
        "sao", "meu", "minha", "meus", "minhas", "estao", "esta", "estou",
        "pode", "posso", "ajuda", "socorro", "faco", "caso", "tipo", "sobre",
        "mais", "menos", "muito", "pouco", "quero", "queria", "saber", "diga",
        "fale", "explique", "explica", "favor", "voce", "seria", "bom", "boa"
    )

    private val detailRequestKeywords = listOf(
        "detalh", "complet", "aprofund", "passo a passo", "tudo sobre",
        "mais informac", "explica melhor", "explique melhor", "mais sobre"
    )

    private val semanticSynonyms: Map<String, List<String>> = mapOf(
        "agua" to listOf("hipoclorito", "clorin", "fervura", "purificacao", "potavel", "filtrar", "sede", "chuva"),
        "beber" to listOf("agua", "hipoclorito", "clorin", "fervura", "potavel", "hidratacao"),
        "cloro" to listOf("hipoclorito", "clorin", "agua", "gotas", "purificacao"),
        "guerra" to listOf("conflito", "bombardeio", "artilharia", "duas paredes", "onda de choque", "misseis", "tiroteio", "cicv", "uxo"),
        "bomba" to listOf("bombardeio", "onda de choque", "duas paredes", "nuclear", "explosao", "artilharia"),
        "misseis" to listOf("bombardeio", "guerra", "duas paredes", "onda de choque", "subsolo"),
        "tiro" to listOf("tiroteio", "guerra", "conflito", "chao", "valeta", "cicv"),
        "nuclear" to listOf("radiologica", "fallout", "atomica", "radia", "regra 7/10", "iodeto", "descontaminacao", "cnen"),
        "radiacao" to listOf("nuclear", "fallout", "regra 7/10", "descontaminacao", "iodeto", "cnen"),
        "quimico" to listOf("gas", "toxico", "biologico", "vedacao", "andares altos", "mascara"),
        "gas" to listOf("quimico", "monoxido", "vazamento", "toxico", "vedacao"),
        "sangue" to listOf("hemorragia", "torniquete", "compressao", "sangramento", "arterial", "march", "ferida"),
        "sangrando" to listOf("hemorragia", "torniquete", "compressao", "arterial", "corte"),
        "corte" to listOf("hemorragia", "ferida", "infeccao", "torniquete", "antisseptico", "irrigacao"),
        "ferida" to listOf("corte", "infeccao", "irrigacao", "antisseptico", "hemorragia", "curativo"),
        "torniquete" to listOf("hemorragia", "cat", "molinete", "sangramento", "march"),
        "engasgo" to listOf("engasgou", "heimlich", "asfixia", "bebe", "tapas nas costas", "obstrucao"),
        "engasgou" to listOf("engasgo", "heimlich", "asfixia", "bebe", "tapas nas costas"),
        "bebe" to listOf("crianca", "engasgo", "rcp", "soro caseiro", "desidratacao"),
        "coracao" to listOf("rcp", "parada", "compressoes", "peito", "infarto", "massagem cardiaca"),
        "parada" to listOf("rcp", "ressuscitacao", "compressoes", "inconsciente", "respirar"),
        "desmaiou" to listOf("inconsciente", "rcp", "convulsao", "hipoglicemia"),
        "queimadura" to listOf("queimou", "fogo", "agua corrente", "bolhas", "termica"),
        "queimou" to listOf("queimadura", "agua corrente", "bolhas"),
        "diarreia" to listOf("soro caseiro", "desidratacao", "sro", "vomito", "colera", "sal", "acucar"),
        "vomito" to listOf("soro caseiro", "desidratacao", "sro", "diarreia"),
        "soro" to listOf("soro caseiro", "sro", "desidratacao", "sal", "acucar"),
        "osso" to listOf("fratura", "tala", "imobilizacao", "coluna", "quebrou"),
        "quebrou" to listOf("fratura", "tala", "imobilizacao"),
        "fratura" to listOf("osso", "tala", "imobilizacao", "coluna"),
        "frio" to listOf("hipotermia", "manta termica", "molhadas", "tremores"),
        "calor" to listOf("insolacao", "exaustao termica", "sombra", "hidratacao"),
        "cobra" to listOf("peconhentos", "serpente", "escorpiao", "aranha", "veneno", "picada", "butantan"),
        "escorpiao" to listOf("peconhentos", "picada", "compressas mornas", "veneno", "butantan"),
        "aranha" to listOf("peconhentos", "picada", "veneno", "butantan"),
        "picada" to listOf("peconhentos", "cobra", "escorpiao", "aranha", "alergia", "anafilaxia"),
        "enchente" to listOf("inundacao", "alagamento", "enxurrada", "leptospirose", "disjuntor"),
        "alagamento" to listOf("enchente", "inundacao", "enxurrada", "leptospirose"),
        "chuva" to listOf("enchente", "deslizamento", "encosta", "inundacao", "agua da chuva"),
        "deslizamento" to listOf("encosta", "barranco", "rachaduras", "terra", "lama", "cemaden"),
        "barranco" to listOf("deslizamento", "encosta", "rachaduras", "cemaden"),
        "terremoto" to listOf("abalo", "escombros", "desabamento", "soterramento", "apito"),
        "escombros" to listOf("terremoto", "desabamento", "apito", "poeira", "soterrado"),
        "apagao" to listOf("blackout", "energia", "geladeira", "gerador", "monoxido", "lanterna"),
        "blackout" to listOf("apagao", "energia", "geladeira", "monoxido"),
        "emp" to listOf("pulso eletromagnetico", "faraday", "papel-aluminio", "tempestade solar"),
        "faraday" to listOf("pulso eletromagnetico", "emp", "papel-aluminio", "eletronicos"),
        "banheiro" to listOf("saneamento", "esgoto", "dois baldes", "fezes", "urina", "colera"),
        "esgoto" to listOf("saneamento", "banheiro seco", "dois baldes", "vaso sanitario"),
        "comida" to listOf("alimentos", "geladeira", "latas", "botulismo", "racionamento", "fefo"),
        "alimento" to listOf("comida", "geladeira", "latas", "botulismo", "racionamento"),
        "incendio" to listOf("fogo", "fumaca", "porta quente", "rasteje", "bombeiros"),
        "fumaca" to listOf("incendio", "rasteje", "porta", "toalhas molhadas", "gas"),
        "mochila" to listOf("72 horas", "bugout", "evacuacao", "kit", "emergencia"),
        "evacuacao" to listOf("mochila", "72 horas", "ponto de encontro", "disjuntor", "fuga"),
        "radio" to listOf("ondas curtas", "comunicacoes", "morse", "sos", "sinais terra-ar"),
        "medo" to listOf("panico", "psicologicos", "respiracao", "criancas", "calma"),
        "panico" to listOf("psicologicos", "respiracao 4-4-4-4", "criancas", "ansiedade"),
        "convulsao" to listOf("epileptico", "crises medicas", "posicao lateral"),
        "asma" to listOf("falta de ar", "bombinha", "salbutamol", "crises medicas"),
        "infarto" to listOf("peito", "aas", "coracao", "crises medicas"),
        "catastrofe" to listOf("regra dos tres", "evacuacao", "mochila", "emergencia"),
        "sobrevivencia" to listOf("regra dos tres", "mochila", "agua", "abrigo")
    )

    fun normalizeAccents(input: String): String {
        val temp = Normalizer.normalize(input.lowercase(Locale.getDefault()), Normalizer.Form.NFD)
        return Regex("\\p{InCombiningDiacriticalMarks}+").replace(temp, "")
    }

    private fun extractKeywords(query: String): List<String> {
        val cleaned = query.replace(Regex("[^\\p{L}\\p{N}\\s]"), " ").trim()
        val tokens = cleaned.split(Regex("\\s+")).filter { it.length >= 2 }
        val filtered = tokens.filter { normalizeAccents(it) !in stopWords && it.length >= 3 }
        return if (filtered.isNotEmpty()) filtered else tokens
    }

    fun isUserAskingForMoreDetails(query: String): Boolean {
        val norm = normalizeAccents(query)
        return detailRequestKeywords.any { norm.contains(it) }
    }

    private fun expandQueryKeywords(rawKeywords: List<String>): List<Pair<String, Int>> {
        val weightedTerms = mutableMapOf<String, Int>()
        for (kw in rawKeywords) {
            val norm = normalizeAccents(kw)
            if (norm.isBlank() || norm in stopWords) continue
            weightedTerms[norm] = maxOf(weightedTerms[norm] ?: 0, 14)

            if (norm.length >= 5) {
                val stem = norm.take(5)
                weightedTerms[stem] = maxOf(weightedTerms[stem] ?: 0, 6)
            }

            semanticSynonyms.forEach { (trigger, synonyms) ->
                if (norm == trigger || (norm.length >= 4 && (norm.contains(trigger) || trigger.contains(norm)))) {
                    for (syn in synonyms) {
                        val normSyn = normalizeAccents(syn)
                        weightedTerms[normSyn] = maxOf(weightedTerms[normSyn] ?: 0, 9)
                    }
                }
            }
        }
        return weightedTerms.entries.map { it.key to it.value }
    }

    fun searchFastFts(query: String): Flow<List<ManualChunkFtsEntity>> {
        val keywords = extractKeywords(query)
        return if (keywords.isEmpty()) {
            database.manualChunkDao().getAllChunks()
        } else {
            val ftsQuery = keywords.joinToString(" OR ") { "$it*" }
            database.manualChunkDao().searchChunks(ftsQuery)
        }
    }

    /**
     * Recupera trechos relevantes do RAG (Base Oficial de Sobrevivência) caso a pergunta tenha relação com eles.
     */
    private suspend fun retrieveRelevantRagChunks(query: String, maxChunks: Int = 3): List<ManualChunkFtsEntity> {
        val rawKeywords = extractKeywords(query)
        val expandedTerms = expandQueryKeywords(rawKeywords)
        if (expandedTerms.isEmpty()) return emptyList()

        var allChunks = database.manualChunkDao().getAllChunks().first()
        if (allChunks.size < OfficialSurvivalKnowledgeBase.MIN_EXPECTED_OFFICIAL_CHUNKS) {
            val official = OfficialSurvivalKnowledgeBase.getOfficialChunks()
            database.manualChunkDao().insertAll(official)
            allChunks = database.manualChunkDao().getAllChunks().first()
        }

        return allChunks.map { chunk ->
            val normContent = normalizeAccents(chunk.contentText)
            val normTitle = normalizeAccents(chunk.docTitle)
            val normCat = normalizeAccents(chunk.category)
            var score = 0
            for ((term, weight) in expandedTerms) {
                val wordRegex = Regex("\\b${Regex.escape(term)}")
                if (wordRegex.containsMatchIn(normTitle)) score += (weight * 2)
                if (wordRegex.containsMatchIn(normContent)) score += weight
                if (normCat.contains(term)) score += (weight / 2)
            }
            Pair(chunk, score)
        }
            .filter { it.second >= 12 }
            .sortedByDescending { it.second }
            .take(maxChunks)
            .map { it.first }
    }

    /**
     * Monta resumo dinâmico do estoque e da família cadastrados no app para que a IA conheça a realidade do usuário.
     */
    private suspend fun buildUserAppContextSummary(): String {
        val items = database.inventoryDao().getAllItems().first()
        val profiles = database.familyProfileDao().getAllProfiles().first()

        val stockText = if (items.isEmpty()) {
            "Estoque atual no app: vazio."
        } else {
            val totalWater = items.filter { it.category.name == "WATER" && it.unit.equals("L", true) }.sumOf { it.quantity }
            val itemsList = items.take(8).joinToString(", ") { "${it.name} (${it.quantity.toInt()} ${it.unit} - ${it.category.displayName})" }
            "Estoque atual no app (${items.size} itens, ${totalWater.toInt()}L de água): $itemsList."
        }

        val familyText = if (profiles.isEmpty()) {
            "Família cadastrada no app: nenhum perfil cadastrado ainda."
        } else {
            val famList = profiles.joinToString("; ") { "${it.name} (${it.role.displayName}, ${it.weightKg.toInt()}kg, Sangue ${it.bloodType}, Alergias: ${it.allergies}, Meds: ${it.medications})" }
            "Família cadastrada (${profiles.size} pessoas): $famList."
        }

        return "$stockText\n$familyText"
    }

    // =========================================================================
    // 3. MOTOR DE STREAMING UNIFICADO (PENSANDO + TOKEN A TOKEN ESTILO EDGE GALLERY)
    // =========================================================================

    /**
     * Executa a inferência unificada (conhecimento do RAG + conhecimento livre do modelo treinado)
     * com fase de pensamento ("Pensando...") e transmissão progressiva token por token (`onPartialAnswer`),
     * exatamente como no Google AI Edge Gallery.
     */
    suspend fun streamUnifiedAiResponse(
        query: String,
        recentHistory: List<Pair<String, String>> = emptyList(),
        forceDetailed: Boolean = false,
        customModelPath: String? = null,
        onThinkingStatus: suspend (String) -> Unit,
        onPartialAnswer: suspend (String) -> Unit
    ): RagResponse = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        val wantsDetails = forceDetailed || isUserAskingForMoreDetails(cleanQuery)

        // Fase 1: Pensando e analisando contexto (visível para o usuário como no Edge Gallery)
        onThinkingStatus("🧠 Pensando e consultando base de conhecimento...")
        delay(350)

        val ragChunks = retrieveRelevantRagChunks(cleanQuery, maxChunks = if (wantsDetails) 3 else 2)
        val appContextSummary = buildUserAppContextSummary()

        if (ragChunks.isNotEmpty()) {
            onThinkingStatus("🧠 Analisando ${ragChunks.size} diretriz(es) oficial(is) do RAG e gerando resposta...")
        } else {
            onThinkingStatus("🧠 Raciocinando sobre sua pergunta...")
        }
        delay(380)

        val unifiedPrompt = buildUnifiedModelPrompt(
            query = cleanQuery,
            ragChunks = ragChunks,
            appContextSummary = appContextSummary,
            recentHistory = recentHistory,
            wantsDetails = wantsDetails
        )

        // Camada 1: Se houver arquivo de modelo local (.task / .litertlm / .bin) no aparelho via MediaPipe LlmInference
        val localModelFile = findLocalModelFile(customModelPath)
        if (localModelFile != null && localModelFile.exists()) {
            val streamedFromLocal = tryStreamWithMediaPipeLlm(
                modelFile = localModelFile,
                prompt = unifiedPrompt,
                onPartialAnswer = onPartialAnswer
            )
            if (!streamedFromLocal.isNullOrBlank()) {
                return@withContext RagResponse(
                    query = cleanQuery,
                    promptBuilt = unifiedPrompt,
                    answer = streamedFromLocal,
                    citedSources = ragChunks,
                    executionMode = if (ragChunks.isNotEmpty()) "IA EDGE LOCAL (${localModelFile.name}) + RAG" else "IA EDGE LOCAL (${localModelFile.name})",
                    canExpandDetails = !wantsDetails,
                    topicQueryForExpansion = cleanQuery
                )
            }
        }

        // Camada 2: Inferência no Modelo Generativo Pré-Treinado Completo (RAG + Conhecimento Geral Livre)
        val trainedModelAnswer = tryTrainedGenerativeModel(
            query = cleanQuery,
            ragChunks = ragChunks,
            appContextSummary = appContextSummary,
            recentHistory = recentHistory,
            wantsDetails = wantsDetails
        )
        if (!trainedModelAnswer.isNullOrBlank()) {
            streamTextProgressively(trainedModelAnswer, onPartialAnswer)
            return@withContext RagResponse(
                query = cleanQuery,
                promptBuilt = unifiedPrompt,
                answer = trainedModelAnswer,
                citedSources = ragChunks,
                executionMode = if (ragChunks.isNotEmpty()) "IA GENERATIVA + RAG OFICIAL" else "IA GENERATIVA • CONHECIMENTO DO MODELO",
                canExpandDetails = !wantsDetails,
                topicQueryForExpansion = cleanQuery
            )
        }

        // Camada 3: Síntese Fluida 100% Offline (quando o aparelho está sem internet e sem arquivo .task externo)
        val offlineAnswer = generateFluidOfflineAnswer(
            query = cleanQuery,
            ragChunks = ragChunks,
            wantsDetails = wantsDetails,
            recentHistory = recentHistory
        )
        streamTextProgressively(offlineAnswer, onPartialAnswer)

        RagResponse(
            query = cleanQuery,
            promptBuilt = unifiedPrompt,
            answer = offlineAnswer,
            citedSources = ragChunks,
            executionMode = if (ragChunks.isNotEmpty()) "MOTOR LOCAL OFFLINE + RAG" else "MOTOR LOCAL OFFLINE",
            canExpandDetails = !wantsDetails,
            topicQueryForExpansion = cleanQuery
        )
    }

    /**
     * Mantido também para chamadas diretas.
     */
    suspend fun searchLocalAiRag(
        query: String,
        customModelPath: String? = null,
        forceDetailed: Boolean = false
    ): RagResponse = streamUnifiedAiResponse(
        query = query,
        recentHistory = emptyList(),
        forceDetailed = forceDetailed,
        customModelPath = customModelPath,
        onThinkingStatus = {},
        onPartialAnswer = {}
    )

    /**
     * Transmite o texto aos poucos (token por token / palavra por palavra) em tempo real,
     * proporcionando a mesma experiência visual fluida de geração do Google AI Edge Gallery.
     */
    private suspend fun streamTextProgressively(
        fullText: String,
        onPartialAnswer: suspend (String) -> Unit
    ) {
        val tokens = Regex("\\S+\\s*").findAll(fullText).map { it.value }.toList()
        if (tokens.isEmpty()) {
            onPartialAnswer(fullText)
            return
        }

        val sb = StringBuilder()
        // Agrupa de 1 a 2 palavras por pulso com intervalo de ~28ms (~35-55 tokens/seg, igual ao Gemma 2B on-device)
        val stepSize = when {
            tokens.size > 160 -> 3
            tokens.size > 70 -> 2
            else -> 1
        }
        var i = 0
        while (i < tokens.size) {
            val end = minOf(i + stepSize, tokens.size)
            for (j in i until end) {
                sb.append(tokens[j])
            }
            onPartialAnswer(sb.toString())
            val lastToken = tokens[end - 1]
            val pauseMs = when {
                lastToken.contains("\n") -> 55L
                lastToken.endsWith(".") || lastToken.endsWith("!") || lastToken.endsWith("?") -> 45L
                else -> 24L
            }
            delay(pauseMs)
            i = end
        }
    }

    private fun buildUnifiedModelPrompt(
        query: String,
        ragChunks: List<ManualChunkFtsEntity>,
        appContextSummary: String,
        recentHistory: List<Pair<String, String>>,
        wantsDetails: Boolean
    ): String {
        val lengthGuidance = if (wantsDetails) {
            "O usuário pediu uma resposta DETALHADA. Explique com profundidade, clareza, passo a passo e exemplos práticos."
        } else {
            "Responda com naturalidade, fluidez e de forma SUCINTA e direta (1 parágrafo fluido ou 3 a 4 pontos objetivos, sem enrolação)."
        }

        return buildString {
            append("<start_of_turn>user\n")
            append("Você é a IA inteligente do aplicativo ResilienceHub. ")
            append("Você combina duas capacidades:\n")
            append("1) Você possui acesso ao RAG de Sobrevivência e Proteção Civil (Defesa Civil, OMS, Ministério da Saúde, Cruz Vermelha, FEMA) e aos dados reais do estoque/família do usuário.\n")
            append("2) Você também é totalmente livre para responder sobre QUALQUER outro assunto (ciências, história, culinária, tecnologia, matemática, dicas do dia a dia, saúde geral, etc.) usando todo o seu conhecimento treinado, sem recusar perguntas fora de sobrevivência.\n")
            append("$lengthGuidance\n\n")
            append("Dados atuais do app do usuário:\n$appContextSummary\n\n")
            if (ragChunks.isNotEmpty()) {
                append("Contexto relevante encontrado no RAG Oficial:\n")
                ragChunks.forEachIndexed { idx, c ->
                    append("[Fonte ${idx + 1} - ${c.docTitle}]: ${c.contentText}\n")
                }
                append("\n")
            }
            recentHistory.takeLast(3).forEach { (u, a) ->
                append("Usuário: $u\nAssistente: $a\n\n")
            }
            append("Pergunta atual do usuário: $query\n")
            append("<end_of_turn>\n<start_of_turn>model\n")
        }
    }

    /**
     * Executa inferência neural local com streaming real token-a-token via MediaPipe LlmInference
     * quando um arquivo `.task` / `.litertlm` / `.bin` está disponível no dispositivo.
     */
    private suspend fun tryStreamWithMediaPipeLlm(
        modelFile: File,
        prompt: String,
        onPartialAnswer: suspend (String) -> Unit
    ): String? {
        return try {
            val accumulator = StringBuilder()
            val completion = CompletableDeferred<String>()

            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelFile.absolutePath)
                .setMaxTokens(768)
                .setTemperature(0.45f)
                .setTopK(20)
                .setResultListener { partialResult, done ->
                    if (!partialResult.isNullOrEmpty()) {
                        accumulator.append(partialResult)
                    }
                    if (done) {
                        completion.complete(accumulator.toString().trim())
                    }
                }
                .setErrorListener { err ->
                    completion.completeExceptionally(err)
                }
                .build()

            LlmInference.createFromOptions(context, options).use { llm ->
                llm.generateResponseAsync(prompt)
                while (!completion.isCompleted) {
                    val current = accumulator.toString()
                    if (current.isNotBlank()) {
                        onPartialAnswer(current)
                    }
                    delay(40)
                }
                val finalResult = completion.await()
                if (finalResult.isNotBlank()) {
                    onPartialAnswer(finalResult)
                }
                finalResult.ifBlank { null }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "MediaPipe LlmInference async falhou ou modelo incompatível, usando camada seguinte", t)
            null
        }
    }

    /**
     * Consulta o modelo LLM pré-treinado injetando o contexto do RAG (quando relevante),
     * os dados do estoque/família do usuário e o histórico da conversa, permitindo responder
     * de forma natural, inteligente e fluida tanto sobre o RAG quanto sobre qualquer tema livre.
     */
    private fun tryTrainedGenerativeModel(
        query: String,
        ragChunks: List<ManualChunkFtsEntity>,
        appContextSummary: String,
        recentHistory: List<Pair<String, String>>,
        wantsDetails: Boolean
    ): String? {
        val systemPrompt = buildString {
            append("Você é a IA assistente do aplicativo ResilienceHub. ")
            append("Responda sempre em Português do Brasil de forma natural, humana, inteligente e fluida (como no Google AI Edge Gallery). ")
            if (wantsDetails) {
                append("O usuário pediu detalhes: forneça uma explicação completa, bem estruturada e aprofundada. ")
            } else {
                append("Seja sucinto, claro e direto ao ponto (responda em 1 parágrafo curto ou 3 a 4 tópicos breves e naturais). ")
            }
            append("Você conhece os protocolos oficiais de sobrevivência do RAG abaixo e os dados do estoque/família do usuário, MAS você também é 100% livre para responder sobre QUALQUER assunto geral (culinária, ciência, história, matemática, tecnologia, mecânica, curiosidades, conversa cotidiana, etc.) usando seu conhecimento treinado, sem jamais bloquear ou recusar temas fora de sobrevivência.\n\n")
            append("Dados do Usuário no App:\n$appContextSummary\n")
            if (ragChunks.isNotEmpty()) {
                append("\nDiretrizes Oficiais do RAG relacionadas à pergunta:\n")
                ragChunks.forEach { c ->
                    append("- ${c.docTitle}: ${c.contentText}\n")
                }
            }
        }

        // 1. Tentativa via POST JSON OpenAI-compatible (Pollinations OpenAI Endpoint)
        try {
            val url = URL("https://text.pollinations.ai/openai")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 6500
                readTimeout = 9000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("User-Agent", "ResilienceHub-EdgeAI/2.0")
            }

            val messagesArray = JSONArray().apply {
                put(JSONObject().put("role", "system").put("content", systemPrompt))
                recentHistory.takeLast(4).forEach { (u, a) ->
                    put(JSONObject().put("role", "user").put("content", u))
                    put(JSONObject().put("role", "assistant").put("content", a))
                }
                put(JSONObject().put("role", "user").put("content", query))
            }

            val payload = JSONObject().apply {
                put("model", "openai")
                put("messages", messagesArray)
                put("temperature", 0.5)
            }

            conn.outputStream.use { os ->
                os.write(payload.toString().toByteArray(Charsets.UTF_8))
            }

            if (conn.responseCode in 200..299) {
                val rawResp = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }.trim()
                val json = JSONObject(rawResp)
                val choices = json.optJSONArray("choices")
                if (choices != null && choices.length() > 0) {
                    val content = choices.getJSONObject(0)
                        .optJSONObject("message")
                        ?.optString("content", "")
                        ?.trim()
                    if (!content.isNullOrBlank()) {
                        return content
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Endpoint POST OpenAI indisponível, tentando endpoint GET direto", e)
        }

        // 2. Fallback via GET Pollinations Text Endpoint com contexto compacto
        try {
            val ragSnippet = if (ragChunks.isNotEmpty()) {
                " [Contexto RAG: ${ragChunks.first().docTitle} - ${ragChunks.first().contentText.take(280)}]"
            } else ""
            val style = if (wantsDetails) "Responda detalhadamente em português: " else "Responda de forma natural, fluida e sucinta em português: "
            val fullGetPrompt = "$style$query$ragSnippet"
            val encoded = URLEncoder.encode(fullGetPrompt, "UTF-8")
            val url = URL("https://text.pollinations.ai/$encoded")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5500
                readTimeout = 7000
                setRequestProperty("User-Agent", "ResilienceHub-EdgeAI/2.0")
            }
            if (conn.responseCode in 200..299) {
                val text = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }.trim()
                if (text.isNotBlank() && text.length > 8 && !text.startsWith("<!DOCTYPE", true)) {
                    return text
                }
            }
        } catch (_: Exception) {
        }

        return null
    }

    /**
     * Gera resposta fluida 100% offline quando o celular está em Modo Avião e sem arquivo `.task` importado.
     */
    private suspend fun generateFluidOfflineAnswer(
        query: String,
        ragChunks: List<ManualChunkFtsEntity>,
        wantsDetails: Boolean,
        recentHistory: List<Pair<String, String>>
    ): String {
        val norm = normalizeAccents(query)

        // Saudação natural
        if (norm in setOf("oi", "ola", "opa", "bom dia", "boa tarde", "boa noite", "e ai", "tudo bem", "oi tudo bem", "ola tudo bem")) {
            return "Olá! Tudo ótimo por aqui. Estou pronto para conversar com você sobre qualquer assunto — desde os protocolos de sobrevivência e seu estoque até ciências, receitas, cálculos ou dúvidas do dia a dia. Como posso ajudar?"
        }

        // Consulta sobre estoque ou família do usuário
        if (norm.contains("meu estoque") || norm.contains("meus itens") || norm.contains("minha agua") || norm.contains("minha familia") || norm.contains("meus familiares")) {
            return buildUserAppContextSummary()
        }

        // Se encontrou diretrizes no RAG Oficial de Sobrevivência
        if (ragChunks.isNotEmpty()) {
            return naturalOfficialSynthesis(query, ragChunks, wantsDetails).first
        }

        // Caso contrário, usa a base enciclopédica e solucionador matemático/físico local
        return OfflineEdgeKnowledgeEngine.generateOfflineResponse(
            query = query,
            detailed = wantsDetails,
            recentHistory = recentHistory
        )
    }

    private fun naturalOfficialSynthesis(
        query: String,
        sources: List<ManualChunkFtsEntity>,
        wantsDetails: Boolean
    ): Pair<String, String> {
        val primary = sources.first()
        val agencyName = primary.docTitle.substringBefore(":").trim()
        val topicTitle = primary.docTitle.substringAfter(":", primary.docTitle).trim()

        val cleanPrimaryBody = primary.contentText
            .replace(Regex("^DIRETRIZ OFICIAL\\s*\\([^)]*\\):\\s*"), "")
            .trim()

        if (!wantsDetails) {
            val numberedSteps = cleanPrimaryBody.split(Regex("(?=\\b[1-4]\\)\\s)"))
                .map { it.trim() }
                .filter { it.isNotBlank() }

            val sb = StringBuilder()
            sb.append("Pelas diretrizes da **$agencyName** sobre **${topicTitle.lowercase(Locale.getDefault())}**, o essencial é:\n\n")

            if (numberedSteps.size >= 2) {
                numberedSteps.take(3).forEach { step ->
                    val shortStep = shortenToEssentialSentence(step)
                    sb.append("• $shortStep\n")
                }
            } else {
                val sentences = cleanPrimaryBody.split(Regex("(?<=\\.)\\s+"))
                sentences.take(3).forEach { s ->
                    if (s.isNotBlank()) sb.append("• ${s.trim()}\n")
                }
            }

            return Pair(sb.toString().trim(), "RAG OFICIAL • $agencyName")
        } else {
            val sb = StringBuilder()
            sb.append("Aqui está o protocolo detalhado da **$agencyName** sobre **$topicTitle**:\n\n")

            sources.forEachIndexed { idx, chunk ->
                if (idx > 0) {
                    sb.append("\n📌 **Complemento (${chunk.docTitle}):**\n")
                }
                val formatted = chunk.contentText
                    .replace(Regex("^DIRETRIZ OFICIAL\\s*\\([^)]*\\):\\s*"), "")
                    .replace(" 1) ", "\n\n1️⃣ ")
                    .replace(" 2) ", "\n\n2️⃣ ")
                    .replace(" 3) ", "\n\n3️⃣ ")
                    .replace(" 4) ", "\n\n4️⃣ ")
                    .replace(" • ", "\n• ")
                    .trim()
                sb.append(formatted).append("\n")
            }

            return Pair(sb.toString().trim(), "GUIA DETALHADO • $agencyName")
        }
    }

    private fun shortenToEssentialSentence(stepText: String): String {
        val withoutNumber = stepText.replace(Regex("^[1-9]\\)\\s*"), "").trim()
        val sentences = withoutNumber.split(Regex("(?<=\\.)\\s+"))
        return if (sentences.size <= 2 || withoutNumber.length <= 210) {
            withoutNumber
        } else {
            sentences.take(2).joinToString(" ")
        }
    }

    // =========================================================================
    // 4. GERENCIAMENTO DE MODELOS LOCAIS (.task / .litertlm / .bin)
    // =========================================================================

    private var cachedModelPath: String? = null

    suspend fun importModelFromUri(modelUri: Uri): Result<String> = withContext(Dispatchers.IO) {
        try {
            val rawName = getFileNameFromUri(modelUri) ?: "gemma-edge-local.task"
            val isValidExt = rawName.endsWith(".task", true) ||
                rawName.endsWith(".litertlm", true) ||
                rawName.endsWith(".bin", true) ||
                rawName.endsWith(".tflite", true)
            val safeName = if (isValidExt) rawName else "$rawName.task"
            val destFile = File(context.filesDir, safeName)

            val inputStream = context.contentResolver.openInputStream(modelUri)
                ?: return@withContext Result.failure(Exception("Não foi possível ler o arquivo do modelo"))

            inputStream.use { input ->
                destFile.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                    }
                    output.flush()
                }
            }

            cachedModelPath = destFile.absolutePath
            Result.success(destFile.name)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao importar modelo LLM local", e)
            Result.failure(e)
        }
    }

    fun getActiveLocalModelDescription(): String {
        val model = findLocalModelFile()
        return if (model != null && model.exists()) {
            val sizeMb = model.length() / (1024 * 1024)
            "Pesos Locais Carregados: ${model.name} (${sizeMb} MB • MediaPipe Edge) + RAG Oficial"
        } else {
            "Motor Unificado Ativo (RAG Oficial + Conhecimento Geral do Modelo com Streaming)"
        }
    }

    fun findLocalModelFile(customPath: String? = null): File? {
        if (!customPath.isNullOrBlank()) {
            val f = File(customPath)
            if (f.exists() && f.length() > 1024) return f
        }
        if (!cachedModelPath.isNullOrBlank()) {
            val f = File(cachedModelPath!!)
            if (f.exists() && f.length() > 1024) return f
        }

        val supportedExts = listOf(".task", ".litertlm", ".bin", ".tflite")
        val dynamicSearchDirs = listOfNotNull(
            context.filesDir,
            context.getExternalFilesDir(null),
            File("/sdcard/Download"),
            File("/sdcard/Android/data/com.google.ai.edge.gallery/files")
        )
        for (dir in dynamicSearchDirs) {
            if (!dir.exists() || !dir.canRead()) continue
            val found = dir.walkTopDown().maxDepth(3).firstOrNull { file ->
                file.isFile &&
                    supportedExts.any { ext -> file.name.endsWith(ext, ignoreCase = true) } &&
                    file.length() > 1024 * 1024
            }
            if (found != null) return found
        }

        val candidateLocations = listOf(
            File(context.filesDir, "gemma-4-E2B-it.task"),
            File(context.filesDir, "gemma3-1b-it-int4.task"),
            File(context.filesDir, "gemma2-2b-it-gpu-int8.task"),
            File(context.filesDir, "gemma-2b-it-cpu-int4.bin"),
            File(context.filesDir, "gemma-2b.bin"),
            File(context.filesDir, "model.bin"),
            File(context.getExternalFilesDir(null), "gemma-4-E2B-it.task"),
            File("/sdcard/Download/gemma-4-E2B-it.task"),
            File("/sdcard/Download/gemma3-1b-it-int4.task"),
            File("/data/local/tmp/gemma-4-E2B-it.task"),
            File("/data/local/tmp/gemma-2b-it-cpu-int4.bin")
        )
        return candidateLocations.firstOrNull { it.exists() }
    }
}
