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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.text.Normalizer
import java.util.Locale

/**
 * Motor Conversacional Híbrido (RAG Oficial de Sobrevivência + Modo Tema Livre com Confirmação):
 * - Respostas naturais e sucintas por padrão (com opção de aprofundamento sob demanda).
 * - Base principal fundamentada nos 30 protocolos dos Órgãos Oficiais + dados reais do estoque/família do usuário.
 * - Detecção de perguntas fora do escopo (Tema Livre): pergunta antes ao usuário se ele deseja sair do escopo
 *   e, mediante confirmação ("Sim"), responde livremente usando a capacidade do modelo.
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
    // 2. CLASSIFICAÇÃO DE ESCOPO, NATURALIDADE E SUCINTUDE
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

    fun isAffirmativeConfirmation(input: String): Boolean {
        val norm = normalizeAccents(input).trim()
        val exactConfirmations = setOf(
            "sim", "s", "quero", "pode", "claro", "com certeza", "por favor",
            "pode responder", "quero sim", "responde", "responda", "manda",
            "ok", "beleza", "vamos", "bora", "autorizo", "afirmativo", "isso"
        )
        return norm in exactConfirmations ||
            norm.startsWith("sim ") ||
            norm.startsWith("pode ") ||
            norm.startsWith("quero ")
    }

    fun isNegativeConfirmation(input: String): Boolean {
        val norm = normalizeAccents(input).trim()
        val exactNegatives = setOf(
            "nao", "n", "nao quero", "deixa", "esquece", "cancelar", "voltar"
        )
        return norm in exactNegatives || norm.startsWith("nao ")
    }

    private fun checkGreetingOrSmallTalk(query: String): String? {
        val norm = normalizeAccents(query).replace(Regex("[^a-z0-9\\s]"), "").trim()
        return when {
            norm in setOf("oi", "ola", "opa", "bom dia", "boa tarde", "boa noite", "e ai", "tudo bem", "oi tudo bem", "ola tudo bem") ->
                "Olá! Tudo ótimo por aqui. Estou pronto para ajudar você e sua família com orientações rápidas de sobrevivência, primeiros socorros, estoque ou qualquer dúvida que surgir. O que você precisa agora?"

            norm in setOf("obrigado", "obrigada", "valeu", "muito obrigado", "show", "perfeito", "entendi") ->
                "Por nada! Sempre que precisar consultar um procedimento rápido ou tirar outra dúvida, é só chamar."

            norm.contains("quem e voce") || norm.contains("o que voce faz") || norm.contains("como voce funciona") ->
                "Sou o assistente da sua família no ResilienceHub. Minha base principal reúne 30 diretrizes oficiais (Defesa Civil, OMS, Ministério da Saúde, Cruz Vermelha e FEMA), além dos dados do seu estoque e família. Respondo de forma direta e sucinta, dou detalhes quando você pedir e, se quiser conversar sobre algum tema livre fora de sobrevivência, basta me confirmar!"

            else -> null
        }
    }

    /**
     * Verifica se o usuário está perguntando diretamente sobre os dados cadastrados no próprio app
     * (seu estoque atual, água armazenada, membros da família ou pontos de encontro).
     */
    private suspend fun checkPersonalAppDatabaseQuery(query: String): String? {
        val norm = normalizeAccents(query)
        val asksAboutMyStock = (norm.contains("meu estoque") || norm.contains("meus itens") ||
            norm.contains("quantos litros") || norm.contains("minha agua") ||
            norm.contains("vencendo") || norm.contains("tenho cadastrado"))
        val asksAboutMyFamily = (norm.contains("minha familia") || norm.contains("meus familiares") ||
            norm.contains("alergia") && (norm.contains("familia") || norm.contains("quem")))

        if (!asksAboutMyStock && !asksAboutMyFamily) return null

        val items = database.inventoryDao().getAllItems().first()
        val profiles = database.familyProfileDao().getAllProfiles().first()

        return if (asksAboutMyStock) {
            if (items.isEmpty()) {
                "No momento você ainda não tem itens cadastrados no estoque. Você pode adicioná-los na aba **Estoque**."
            } else {
                val totalWaterL = items.filter { it.category.name == "WATER" && it.unit.equals("L", true) }.sumOf { it.quantity }
                val sampleNames = items.take(5).joinToString(", ") { "${it.name} (${it.quantity.toInt()} ${it.unit})" }
                "Hoje você tem **${items.size} item(ns)** cadastrados no estoque (incluindo **${totalWaterL.toInt()} L de água**). Alguns dos seus itens: $sampleNames. Quer que eu avalie como usar ou conservar algum deles?"
            }
        } else {
            if (profiles.isEmpty()) {
                "Você ainda não cadastrou membros na aba **Família**."
            } else {
                val summary = profiles.joinToString("; ") { "${it.name} (Sangue ${it.bloodType}, ${it.weightKg.toInt()}kg, Alergias: ${it.allergies})" }
                "Sua família cadastrada conta com **${profiles.size} pessoa(s)**: $summary."
            }
        }
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
     * Consulta principal do Chat:
     * 1) Responde saudações e perguntas sobre o estoque/família com naturalidade.
     * 2) Verifica se a pergunta pertence ao escopo de sobrevivência/emergência/proteção familiar.
     * 3) Se estiver no escopo: responde de forma natural e sucinta (ou detalhada se `forceDetailed = true` ou pedido pelo usuário).
     * 4) Se estiver FORA do escopo: pergunta educadamente se o usuário quer falar sobre esse tema livre antes de responder.
     */
    suspend fun searchLocalAiRag(
        query: String,
        customModelPath: String? = null,
        forceDetailed: Boolean = false
    ): RagResponse = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()

        // 1. Saudação ou conversa rápida natural
        checkGreetingOrSmallTalk(cleanQuery)?.let { greetingReply ->
            return@withContext RagResponse(
                query = cleanQuery,
                promptBuilt = cleanQuery,
                answer = greetingReply,
                citedSources = emptyList(),
                executionMode = "ASSISTENTE NATURAL"
            )
        }

        // 2. Consulta direta aos dados cadastrados pelo próprio usuário no app
        checkPersonalAppDatabaseQuery(cleanQuery)?.let { personalReply ->
            return@withContext RagResponse(
                query = cleanQuery,
                promptBuilt = cleanQuery,
                answer = personalReply,
                citedSources = emptyList(),
                executionMode = "DADOS DO SEU APP"
            )
        }

        // 3. Verifica se o usuário pediu detalhes já na própria frase
        val wantsDetails = forceDetailed || isUserAskingForMoreDetails(cleanQuery)

        val rawKeywords = extractKeywords(cleanQuery)
        val expandedTerms = expandQueryKeywords(rawKeywords)

        var allChunks = database.manualChunkDao().getAllChunks().first()
        if (allChunks.size < OfficialSurvivalKnowledgeBase.MIN_EXPECTED_OFFICIAL_CHUNKS) {
            val official = OfficialSurvivalKnowledgeBase.getOfficialChunks()
            database.manualChunkDao().insertAll(official)
            allChunks = database.manualChunkDao().getAllChunks().first()
        }

        val scoredChunks = if (expandedTerms.isEmpty()) {
            emptyList()
        } else {
            allChunks.map { chunk ->
                val normContent = normalizeAccents(chunk.contentText)
                val normTitle = normalizeAccents(chunk.docTitle)
                val normCat = normalizeAccents(chunk.category)
                var score = 0
                for ((term, weight) in expandedTerms) {
                    // Exige casamento de palavra ou prefixo significativo para não ativar falsos positivos
                    val wordRegex = Regex("\\b${Regex.escape(term)}")
                    if (wordRegex.containsMatchIn(normTitle)) score += (weight * 2)
                    if (wordRegex.containsMatchIn(normContent)) score += weight
                    if (normCat.contains(term)) score += (weight / 2)
                }
                Pair(chunk, score)
            }
                .filter { it.second >= 12 } // Limiar mínimo de relevância real no escopo de sobrevivência
                .sortedByDescending { it.second }
        }

        // 4. SE NÃO HÁ CORRESPONDÊNCIA NO ESCOPO DE SOBREVIVÊNCIA -> QUESTIONA SE QUER TEMA LIVRE!
        if (scoredChunks.isEmpty()) {
            val confirmText = "Notei que sua pergunta (**\"$cleanQuery\"**) está fora do meu escopo principal de sobrevivência, saúde de emergência e proteção da família.\n\n" +
                "Você quer que eu responda sobre esse assunto de **tema livre** mesmo assim?"
            return@withContext RagResponse(
                query = cleanQuery,
                promptBuilt = cleanQuery,
                answer = confirmText,
                citedSources = emptyList(),
                executionMode = "CONFIRMAÇÃO DE TEMA LIVRE",
                requiresFreeTopicConfirmation = true,
                pendingFreeTopicQuery = cleanQuery
            )
        }

        // 5. ESTÁ NO ESCOPO OFICIAL DE SOBREVIVÊNCIA!
        val topChunks = if (wantsDetails) {
            scoredChunks.take(3).map { it.first }
        } else {
            scoredChunks.take(2).map { it.first }
        }

        val contextBuilder = StringBuilder()
        topChunks.forEachIndexed { index, chunk ->
            contextBuilder.append("Fonte ${index + 1} (${chunk.docTitle}): ${chunk.contentText}\n\n")
        }

        val styleInstruction = if (wantsDetails) {
            "O usuário pediu informações DETALHADAS. Explique passo a passo todos os detalhes, dosagens e cuidados com naturalidade."
        } else {
            "Responda com naturalidade e de forma SUCINTA (máximo de 3 a 4 pontos curtos e diretos com o essencial)."
        }

        val fullPrompt = buildString {
            append(STRICT_RAG_SYSTEM_PROMPT_TEMPLATE)
            append(styleInstruction).append("\n\n")
            append(contextBuilder.toString())
            append("Pergunta: ").append(cleanQuery).append("\nResposta:\n")
        }

        val (answerText, modeLabel) = executeInScopeInference(
            prompt = fullPrompt,
            query = cleanQuery,
            sources = topChunks,
            wantsDetails = wantsDetails,
            modelPath = customModelPath
        )

        RagResponse(
            query = cleanQuery,
            promptBuilt = fullPrompt,
            answer = answerText,
            citedSources = topChunks,
            executionMode = modeLabel,
            requiresFreeTopicConfirmation = false,
            canExpandDetails = !wantsDetails,
            topicQueryForExpansion = cleanQuery
        )
    }

    /**
     * Modo 2: CHAT LIVRE (Estilo Google AI Edge Gallery — 100% Offline).
     * Permite conversar livremente sobre qualquer assunto (ciências, matemática, culinária,
     * história, tecnologia, mecânica ou sobrevivência) sem bloqueio de escopo e mantendo
     * o histórico de múltiplos turnos da conversa.
     */
    suspend fun chatFreelyEdgeGallery(
        query: String,
        recentHistory: List<Pair<String, String>> = emptyList(),
        detailed: Boolean = false,
        customModelPath: String? = null
    ): RagResponse = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        val wantsDetails = detailed || isUserAskingForMoreDetails(cleanQuery)

        // 1. Saudação ou conversa rápida natural
        checkGreetingOrSmallTalk(cleanQuery)?.let { greetingReply ->
            return@withContext RagResponse(
                query = cleanQuery,
                promptBuilt = cleanQuery,
                answer = greetingReply,
                citedSources = emptyList(),
                executionMode = "EDGE GALLERY • CHAT LIVRE OFFLINE"
            )
        }

        // 2. Pergunta direta sobre o estoque ou família do usuário no app
        checkPersonalAppDatabaseQuery(cleanQuery)?.let { personalReply ->
            return@withContext RagResponse(
                query = cleanQuery,
                promptBuilt = cleanQuery,
                answer = personalReply,
                citedSources = emptyList(),
                executionMode = "EDGE GALLERY • DADOS DO APP"
            )
        }

        // 3. Constrói prompt multi-turn no padrão Gemma / Google AI Edge Gallery
        val multiTurnPrompt = buildEdgeGalleryMultiTurnPrompt(cleanQuery, recentHistory, wantsDetails)

        // 4. Se o arquivo de modelo neural (.task / .litertlm / .bin) estiver carregado no aparelho via MediaPipe
        val modelFile = findLocalModelFile(customModelPath)
        if (modelFile != null && modelFile.exists()) {
            try {
                val llm = getOrCreateLlmInference(modelFile, isFreeTopic = true)
                val llmAnswer = llm.generateResponse(multiTurnPrompt).trim()
                if (llmAnswer.isNotBlank()) {
                    return@withContext RagResponse(
                        query = cleanQuery,
                        promptBuilt = multiTurnPrompt,
                        answer = llmAnswer,
                        citedSources = emptyList(),
                        executionMode = "EDGE GALLERY • ${modelFile.name}",
                        canExpandDetails = !wantsDetails,
                        topicQueryForExpansion = cleanQuery
                    )
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Alternando para motor cognitivo local embarcado", t)
            }
        }

        // 5. No modo Chat Livre, também verifica se a pergunta é de sobrevivência (respondendo direto sem bloqueio)
        //    ou se é de conhecimento geral (respondendo pelo OfflineEdgeKnowledgeEngine 100% offline)
        val rawKeywords = extractKeywords(cleanQuery)
        val expandedTerms = expandQueryKeywords(rawKeywords)
        val allChunks = database.manualChunkDao().getAllChunks().first()
        val matchingSurvival = if (expandedTerms.isNotEmpty()) {
            allChunks.map { chunk ->
                val normContent = normalizeAccents(chunk.contentText)
                val normTitle = normalizeAccents(chunk.docTitle)
                var score = 0
                for ((term, weight) in expandedTerms) {
                    val wordRegex = Regex("\\b${Regex.escape(term)}")
                    if (wordRegex.containsMatchIn(normTitle)) score += (weight * 2)
                    if (wordRegex.containsMatchIn(normContent)) score += weight
                }
                Pair(chunk, score)
            }.filter { it.second >= 16 }.sortedByDescending { it.second }
        } else emptyList()

        if (matchingSurvival.isNotEmpty()) {
            val topChunks = matchingSurvival.take(if (wantsDetails) 3 else 2).map { it.first }
            val (ans, _) = naturalOfficialSynthesis(cleanQuery, topChunks, wantsDetails)
            return@withContext RagResponse(
                query = cleanQuery,
                promptBuilt = multiTurnPrompt,
                answer = ans,
                citedSources = topChunks,
                executionMode = "EDGE GALLERY • BASE LOCAL OFFLINE",
                canExpandDetails = !wantsDetails,
                topicQueryForExpansion = cleanQuery
            )
        }

        val edgeOfflineReply = OfflineEdgeKnowledgeEngine.generateOfflineResponse(
            query = cleanQuery,
            detailed = wantsDetails,
            recentHistory = recentHistory
        )

        RagResponse(
            query = cleanQuery,
            promptBuilt = multiTurnPrompt,
            answer = edgeOfflineReply,
            citedSources = emptyList(),
            executionMode = "EDGE GALLERY • MOTOR LOCAL OFFLINE",
            canExpandDetails = !wantsDetails,
            topicQueryForExpansion = cleanQuery
        )
    }

    private fun buildEdgeGalleryMultiTurnPrompt(
        currentQuery: String,
        recentHistory: List<Pair<String, String>>,
        wantsDetails: Boolean
    ): String {
        val styleRule = if (wantsDetails) {
            "Responda em português de forma natural, completa, detalhada e passo a passo."
        } else {
            "Responda em português com naturalidade e de forma sucinta, clara e direta (3 a 5 frases)."
        }
        return buildString {
            append("<start_of_turn>user\n")
            append("Instrução do Sistema: Você é uma IA local rodando 100% offline no dispositivo (estilo Google AI Edge Gallery). $styleRule\n")
            append("<end_of_turn>\n")
            recentHistory.takeLast(3).forEach { (userTurn, aiTurn) ->
                append("<start_of_turn>user\n").append(userTurn).append("\n<end_of_turn>\n")
                append("<start_of_turn>model\n").append(aiTurn).append("\n<end_of_turn>\n")
            }
            append("<start_of_turn>user\n").append(currentQuery).append("\n<end_of_turn>\n")
            append("<start_of_turn>model\n")
        }
    }

    /**
     * Executado quando o usuário confirma ("Sim") que deseja que a IA responda a uma pergunta
     * fora do escopo de sobrevivência (Tema Livre) no Modo RAG, utilizando toda a capacidade local 100% offline.
     */
    suspend fun answerFreeTopicQuestion(
        freeQuery: String,
        detailed: Boolean = false,
        customModelPath: String? = null,
        recentHistory: List<Pair<String, String>> = emptyList()
    ): RagResponse = withContext(Dispatchers.IO) {
        val wantsDetails = detailed || isUserAskingForMoreDetails(freeQuery)
        val multiTurnPrompt = buildEdgeGalleryMultiTurnPrompt(freeQuery, recentHistory, wantsDetails)

        // Camada 1: Se o modelo LLM local MediaPipe (.task / .litertlm / .bin) estiver carregado no aparelho
        val modelFile = findLocalModelFile(customModelPath)
        if (modelFile != null && modelFile.exists()) {
            try {
                val llm = getOrCreateLlmInference(modelFile, isFreeTopic = true)
                val llmAnswer = llm.generateResponse(multiTurnPrompt).trim()
                if (llmAnswer.isNotBlank()) {
                    return@withContext RagResponse(
                        query = freeQuery,
                        promptBuilt = multiTurnPrompt,
                        answer = llmAnswer,
                        citedSources = emptyList(),
                        executionMode = "TEMA LIVRE • IA LOCAL (${modelFile.name})",
                        canExpandDetails = !wantsDetails,
                        topicQueryForExpansion = freeQuery
                    )
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Alternando para motor cognitivo local embarcado", t)
            }
        }

        // Camada 2: Motor Cognitivo e Enciclopédico 100% Offline Embarcado
        val offlineFreeAnswer = OfflineEdgeKnowledgeEngine.generateOfflineResponse(
            query = freeQuery,
            detailed = wantsDetails,
            recentHistory = recentHistory
        )
        RagResponse(
            query = freeQuery,
            promptBuilt = multiTurnPrompt,
            answer = offlineFreeAnswer,
            citedSources = emptyList(),
            executionMode = "TEMA LIVRE • IA LOCAL OFFLINE",
            canExpandDetails = !wantsDetails,
            topicQueryForExpansion = freeQuery
        )
    }

    // =========================================================================
    // 3. MOTORES DE RESPOSTA NATURAL (SUCINTA vs DETALHADA E TEMA LIVRE)
    // =========================================================================

    private var cachedLlmInference: LlmInference? = null
    private var cachedModelPath: String? = null

    private fun getOrCreateLlmInference(modelFile: File, isFreeTopic: Boolean): LlmInference {
        if (cachedLlmInference != null && cachedModelPath == modelFile.absolutePath) {
            return cachedLlmInference!!
        }
        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelFile.absolutePath)
            .setMaxTokens(if (isFreeTopic) 600 else 512)
            .setTemperature(if (isFreeTopic) 0.5f else 0.2f)
            .setTopK(if (isFreeTopic) 20 else 5)
            .build()
        return LlmInference.createFromOptions(context, options).also {
            cachedLlmInference = it
            cachedModelPath = modelFile.absolutePath
        }
    }

    private fun executeInScopeInference(
        prompt: String,
        query: String,
        sources: List<ManualChunkFtsEntity>,
        wantsDetails: Boolean,
        modelPath: String?
    ): Pair<String, String> {
        val modelFile = findLocalModelFile(modelPath)

        if (modelFile != null && modelFile.exists()) {
            return try {
                val llm = getOrCreateLlmInference(modelFile, isFreeTopic = false)
                val response = llm.generateResponse(prompt).trim()
                Pair(response, "IA LOCAL GEMMA (${modelFile.name})")
            } catch (t: Throwable) {
                Log.w(TAG, "Alternando para síntese natural sobre base oficial", t)
                naturalOfficialSynthesis(query, sources, wantsDetails)
            }
        } else {
            return naturalOfficialSynthesis(query, sources, wantsDetails)
        }
    }

    /**
     * Gera respostas naturais, fluidas e humanas a partir da Base Oficial de Sobrevivência:
     * - Por padrão (`wantsDetails == false`): resposta curta, direta e conversacional (3 a 4 pontos essenciais).
     * - Quando solicitado (`wantsDetails == true`): explicação aprofundada e completa com todos os passos e alertas.
     */
    private fun naturalOfficialSynthesis(
        query: String,
        sources: List<ManualChunkFtsEntity>,
        wantsDetails: Boolean
    ): Pair<String, String> {
        val primary = sources.first()
        val agencyName = primary.docTitle.substringBefore(":").trim()
        val topicTitle = primary.docTitle.substringAfter(":", primary.docTitle).trim()

        // Limpa o prefixo técnico para soar como uma conversa natural
        val cleanPrimaryBody = primary.contentText
            .replace(Regex("^DIRETRIZ OFICIAL\\s*\\([^)]*\\):\\s*"), "")
            .trim()

        if (!wantsDetails) {
            // MODO SUCINTO E NATURAL (PADRÃO)
            val numberedSteps = cleanPrimaryBody.split(Regex("(?=\\b[1-4]\\)\\s)"))
                .map { it.trim() }
                .filter { it.isNotBlank() }

            val sb = StringBuilder()
            sb.append("Sobre **${topicTitle.lowercase(Locale.getDefault())}**, a orientação prática da **$agencyName** é direta:\n\n")

            if (numberedSteps.size >= 2) {
                // Resume cada etapa para a frase principal sem despejar textos gigantes
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

            sb.append("\nSe quiser o passo a passo detalhado ou cuidados adicionais, é só tocar em **\"🔍 Mais detalhes\"** ou me pedir!")
            return Pair(sb.toString(), "RESPOSTA SUCINTA • $agencyName")
        } else {
            // MODO DETALHADO E COMPLETO (QUANDO O USUÁRIO PEDE MAIS INFORMAÇÕES)
            val sb = StringBuilder()
            sb.append("Aqui está o detalhamento completo segundo **$agencyName** sobre **$topicTitle**:\n\n")

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

    /**
     * Enxuga um parágrafo longo mantendo a regra/dosagem principal de forma natural e rápida de ler.
     */
    private fun shortenToEssentialSentence(stepText: String): String {
        val withoutNumber = stepText.replace(Regex("^[1-9]\\)\\s*"), "").trim()
        val sentences = withoutNumber.split(Regex("(?<=\\.)\\s+"))
        return if (sentences.size <= 2 || withoutNumber.length <= 210) {
            withoutNumber
        } else {
            sentences.take(2).joinToString(" ")
        }
    }

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

            cachedLlmInference = null
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
            "Modelo Neural Ativo: ${model.name} (${sizeMb} MB • MediaPipe Edge)"
        } else {
            "Motor Cognitivo Local Embarcado Ativo (100% Offline • Pronto para .task/.litertlm)"
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
