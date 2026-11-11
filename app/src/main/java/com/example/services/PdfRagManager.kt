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

/**
 * Motor RAG Conversacional 100% Offline:
 * - Consulta a Base Oficial de Sobrevivência (Defesa Civil, OMS, Ministério da Saúde, Cruz Vermelha/CICV, FEMA, CNEN, CBMERJ).
 * - Expansão semântica de sinônimos de emergência para compreender perguntas em linguagem natural de chat.
 * - Modo IA Local (MediaPipe Gemma LLM) + Motor Conversacional Estruturado Offline (Zero Alucinação).
 * - Suporte a leitura e extração de Receitas Médicas e Documentos da Família em PDF.
 */
class PdfRagManager(
    private val context: Context,
    private val database: AppDatabase
) {

    companion object {
        private const val TAG = "PdfRagManager"
        const val CHUNK_TARGET_SIZE = 500
        const val CHUNK_OVERLAP = 50

        const val STRICT_RAG_SYSTEM_PROMPT_TEMPLATE =
            "Você é um assistente de emergência civil offline. " +
            "Responda APENAS com base nas cartilhas oficiais abaixo, de forma direta, conversacional e em tópicos:\n\n"
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
        val executionMode: String
    )

    // =========================================================================
    // 1. EXTRAÇÃO DE PDF PARA DOCUMENTOS E RECEITAS DA FAMÍLIA
    // =========================================================================

    /**
     * Extrai a quantidade de páginas e o texto completo de um PDF de Receita Médica ou Documento Familiar
     * salvo no armazenamento interno para consulta rápida offline.
     */
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
                cleanText.ifBlank { "Documento PDF digitalizado ($totalPages página(s)). Toque em 'Abrir PDF' para visualizar as páginas." }
            )
        } catch (e: Exception) {
            Log.w(TAG, "Não foi possível extrair texto do PDF familiar (pode ser imagem escaneada)", e)
            Pair(1, "Documento PDF armazenado offline. Toque em 'Abrir PDF' para visualizar.")
        } finally {
            try {
                document?.close()
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Mantido para compatibilidade caso um PDF externo seja indexado no FTS4.
     */
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
    // 2. BUSCA INTELIGENTE COM EXPANSÃO SEMÂNTICA PARA CHAT DE SOBREVIVÊNCIA
    // =========================================================================

    private val stopWords = setOf(
        "como", "para", "qual", "quais", "que", "com", "sem", "nos", "nas",
        "dos", "das", "por", "uma", "uns", "umas", "isso", "este", "esta",
        "onde", "quando", "porque", "deve", "devo", "fazer", "usar", "tem",
        "sao", "meu", "minha", "meus", "minhas", "estao", "esta", "estou",
        "pode", "posso", "ajuda", "socorro", "faco", "caso", "tipo", "sobre"
    )

    /**
     * Mapa de expansão semântica: conecta termos coloquiais do usuário aos conceitos técnicos
     * das diretrizes oficiais (Defesa Civil, OMS, CICV, FEMA, Ministério da Saúde).
     */
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
        "frio" to listOf("hipotermia", "manta termica", "molhadas", "tremores"),
        "calor" to listOf("insolacao", "exaustao termica", "sombra", "hidratacao"),
        "cobra" to listOf("peconhentos", "serpente", "escorpiao", "aranha", "veneno", "picada", "butantan"),
        "escorpiao" to listOf("peconhentos", "picada", "compressas mornas", "veneno", "butantan"),
        "picada" to listOf("peconhentos", "cobra", "escorpiao", "aranha", "alergia", "anafilaxia"),
        "enchente" to listOf("inundacao", "alagamento", "enxurrada", "leptospirose", "disjuntor"),
        "alagamento" to listOf("enchente", "inundacao", "enxurrada", "leptospirose"),
        "chuva" to listOf("enchente", "deslizamento", "encosta", "inundacao", "agua da chuva"),
        "deslizamento" to listOf("encosta", "barranco", "rachaduras", "terra", "lama", "cemaden"),
        "barranco" to listOf("deslizamento", "encosta", "rachaduras", "cemaden"),
        "terremoto" to listOf("abalo", "escombros", "desabamento", "soterramento", "apito"),
        "escombros" to listOf("terremoto", "desabamento", "apito", "poeira", "soterrado"),
        "apagao" to listOf("blackout", "energia", "geladeira", "gerador", "monoxido", "lanterna"),
        "energia" to listOf("apagao", "blackout", "bateria", "solar", "faraday", "emp"),
        "emp" to listOf("pulso eletromagnetico", "faraday", "papel-aluminio", "tempestade solar"),
        "faraday" to listOf("pulso eletromagnetico", "emp", "papel-aluminio", "eletronicos"),
        "banheiro" to listOf("saneamento", "esgoto", "dois baldes", "fezes", "urina", "colera"),
        "esgoto" to listOf("saneamento", "banheiro seco", "dois baldes", "vaso sanitario"),
        "comida" to listOf("alimentos", "geladeira", "latas", "botulismo", "racionamento", "fefo"),
        "fome" to listOf("alimentos", "racionamento", "calorico", "comida"),
        "incendio" to listOf("fogo", "fumaca", "porta quente", "rasteje", "bombeiros"),
        "fumaca" to listOf("incendio", "rasteje", "porta", "toalhas molhadas", "gas"),
        "mochila" to listOf("72 horas", "bugout", "evacuacao", "kit", "emergencia"),
        "evacuacao" to listOf("mochila", "72 horas", "ponto de encontro", "disjuntor", "fuga"),
        "radio" to listOf("ondas curtas", "comunicacoes", "morse", "sos", "sinais terra-ar"),
        "medo" to listOf("panico", "psicologicos", "respiracao", "criancas", "calma"),
        "panico" to listOf("psicologicos", "respiracao 4-4-4-4", "criancas", "ansiedade")
    )

    private fun normalizeAccents(input: String): String {
        val temp = Normalizer.normalize(input.lowercase(), Normalizer.Form.NFD)
        return Regex("\\p{InCombiningDiacriticalMarks}+").replace(temp, "")
    }

    private fun extractKeywords(query: String): List<String> {
        val cleaned = query.replace(Regex("[^\\p{L}\\p{N}\\s]"), " ").trim()
        val tokens = cleaned.split(Regex("\\s+")).filter { it.length >= 2 }
        val filtered = tokens.filter { normalizeAccents(it) !in stopWords && it.length >= 3 }
        return if (filtered.isNotEmpty()) filtered else tokens
    }

    private fun expandQueryKeywords(rawKeywords: List<String>): List< Pair<String, Int> > {
        val weightedTerms = mutableMapOf<String, Int>()
        for (kw in rawKeywords) {
            val norm = normalizeAccents(kw)
            if (norm.isBlank()) continue
            weightedTerms[norm] = maxOf(weightedTerms[norm] ?: 0, 12)

            // Adiciona radical (prefixo de 4 letras) para casar plurais e conjugações
            if (norm.length >= 5) {
                val stem = norm.take(5)
                weightedTerms[stem] = maxOf(weightedTerms[stem] ?: 0, 6)
            }

            // Expansão de sinônimos de emergência
            semanticSynonyms.forEach { (trigger, synonyms) ->
                if (norm.contains(trigger) || trigger.contains(norm)) {
                    for (syn in synonyms) {
                        val normSyn = normalizeAccents(syn)
                        weightedTerms[normSyn] = maxOf(weightedTerms[normSyn] ?: 0, 8)
                    }
                }
            }
        }
        return weightedTerms.entries.map { it.key to it.value }
    }

    /**
     * Modo Rápido FTS4.
     */
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
     * Motor Conversacional RAG sobre a Base Oficial de Sobrevivência.
     * Recupera os protocolos oficiais mais relevantes, constrói o contexto verificado
     * e gera uma resposta conversacional detalhada citando os órgãos oficiais.
     */
    suspend fun searchLocalAiRag(
        query: String,
        customModelPath: String? = null
    ): RagResponse = withContext(Dispatchers.IO) {
        val rawKeywords = extractKeywords(query)
        val expandedTerms = expandQueryKeywords(rawKeywords)

        var allChunks = database.manualChunkDao().getAllChunks().first()
        if (allChunks.size < OfficialSurvivalKnowledgeBase.MIN_EXPECTED_OFFICIAL_CHUNKS) {
            // Garantia extra de que a base oficial está carregada
            val official = OfficialSurvivalKnowledgeBase.getOfficialChunks()
            database.manualChunkDao().insertAll(official)
            allChunks = database.manualChunkDao().getAllChunks().first()
        }

        // Pontuação híbrida (Termos diretos + Sinônimos Semânticos + Título + Categoria)
        val scoredChunks = if (expandedTerms.isEmpty()) {
            allChunks.take(4)
        } else {
            allChunks.map { chunk ->
                val normContent = normalizeAccents(chunk.contentText)
                val normTitle = normalizeAccents(chunk.docTitle)
                val normCat = normalizeAccents(chunk.category)
                var score = 0
                for ((term, weight) in expandedTerms) {
                    if (normTitle.contains(term)) score += (weight * 2)
                    if (normContent.contains(term)) score += weight
                    if (normCat.contains(term)) score += (weight / 2)
                }
                Pair(chunk, score)
            }
                .filter { it.second > 0 }
                .sortedByDescending { it.second }
                .map { it.first }
        }

        // Seleciona até 3 diretrizes oficiais mais relevantes (ou visão geral se a pergunta for ampla)
        val topChunks = if (scoredChunks.isNotEmpty()) {
            scoredChunks.take(3)
        } else {
            // Se o usuário mandou uma saudação ou pergunta genérica ("o que fazer em uma catástrofe?"),
            // traz Regra dos Três, Kit 72h e Purificação de Água
            allChunks.filter { it.rowId in listOf(4, 3, 1, 5) }.take(3)
        }

        val contextBuilder = StringBuilder()
        contextBuilder.append("[DIRETRIZES OFICIAIS RECUPERADAS DO BANCO LOCAL]\n")
        topChunks.forEachIndexed { index, chunk ->
            contextBuilder.append("Fonte Oficial ${index + 1} (${chunk.docTitle} - Cat: ${chunk.category}):\n")
            contextBuilder.append("\"${chunk.contentText}\"\n\n")
        }

        val fullPrompt = buildString {
            append(STRICT_RAG_SYSTEM_PROMPT_TEMPLATE)
            append(contextBuilder.toString())
            append("[MENSAGEM DO CIDADÃO NO CHAT]\n")
            append(query)
            append("\n\n[RESPOSTA ESTRUTURADA PASSO A PASSO]:\n")
        }

        val answer = executeRagInference(fullPrompt, query, topChunks, customModelPath)

        RagResponse(
            query = query,
            promptBuilt = fullPrompt,
            answer = answer.first,
            citedSources = topChunks,
            executionMode = answer.second
        )
    }

    private var cachedLlmInference: LlmInference? = null
    private var cachedModelPath: String? = null

    suspend fun importModelFromUri(modelUri: Uri): Result<String> = withContext(Dispatchers.IO) {
        try {
            val rawName = getFileNameFromUri(modelUri) ?: "gemma-4-E2B-it.task"
            val safeName = if (rawName.endsWith(".task") || rawName.endsWith(".bin")) rawName else "$rawName.task"
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

    fun findLocalModelFile(customPath: String? = null): File? {
        if (!customPath.isNullOrBlank()) {
            val f = File(customPath)
            if (f.exists() && f.length() > 1024) return f
        }
        if (!cachedModelPath.isNullOrBlank()) {
            val f = File(cachedModelPath!!)
            if (f.exists() && f.length() > 1024) return f
        }

        val dynamicSearchDirs = listOfNotNull(context.filesDir, context.getExternalFilesDir(null))
        for (dir in dynamicSearchDirs) {
            val found = dir.listFiles()?.firstOrNull {
                it.isFile && (it.name.endsWith(".task", true) || it.name.endsWith(".bin", true)) && it.length() > 1024 * 1024
            }
            if (found != null) return found
        }

        val candidateLocations = listOf(
            File(context.filesDir, "gemma-4-E2B-it.task"),
            File(context.filesDir, "gemma2-2b-it-gpu-int8.task"),
            File(context.filesDir, "gemma-2b-it-cpu-int4.bin"),
            File(context.filesDir, "gemma-2b.bin"),
            File(context.filesDir, "model.bin"),
            File(context.getExternalFilesDir(null), "gemma-4-E2B-it.task"),
            File("/sdcard/Download/gemma-4-E2B-it.task"),
            File("/data/local/tmp/gemma-4-E2B-it.task"),
            File("/data/local/tmp/gemma-2b-it-cpu-int4.bin")
        )
        return candidateLocations.firstOrNull { it.exists() }
    }

    private fun executeRagInference(
        prompt: String,
        query: String,
        sources: List<ManualChunkFtsEntity>,
        modelPath: String?
    ): Pair<String, String> {
        val modelFile = findLocalModelFile(modelPath)

        if (modelFile != null && modelFile.exists()) {
            return try {
                val llm = if (cachedLlmInference != null && cachedModelPath == modelFile.absolutePath) {
                    cachedLlmInference!!
                } else {
                    val options = LlmInference.LlmInferenceOptions.builder()
                        .setModelPath(modelFile.absolutePath)
                        .setMaxTokens(512)
                        .setTemperature(0.1f)
                        .setTopK(1)
                        .build()
                    LlmInference.createFromOptions(context, options).also {
                        cachedLlmInference = it
                        cachedModelPath = modelFile.absolutePath
                    }
                }

                val response = llm.generateResponse(prompt)
                Pair(response, "IA LOCAL GEMMA (${modelFile.name})")
            } catch (t: Throwable) {
                Log.w(TAG, "Falha na inferência MediaPipe LlmInference, usando síntese conversacional oficial", t)
                conversationalOfficialSynthesis(query, sources)
            }
        } else {
            return conversationalOfficialSynthesis(query, sources)
        }
    }

    /**
     * Sintetiza uma resposta conversacional rica, estruturada e 100% fiel aos protocolos
     * dos órgãos oficiais (Defesa Civil, OMS, Cruz Vermelha, Ministério da Saúde, FEMA, CNEN, CBMERJ).
     */
    private fun conversationalOfficialSynthesis(
        query: String,
        sources: List<ManualChunkFtsEntity>
    ): Pair<String, String> {
        val mode = "RAG OFICIAL OFFLINE (Defesa Civil • OMS • CICV • FEMA)"

        if (sources.isEmpty()) {
            return Pair(
                "Não encontrei um protocolo específico para \"$query\", mas posso orientar você e sua família sobre:\n" +
                    "• Guerras, Bombardeios e Conflito Urbano (CICV)\n" +
                    "• Emergência Nuclear, Fallout e Armas Químicas (FEMA / CNEN)\n" +
                    "• Purificação de Água (Hipoclorito 2,5%, Clorin, Fervura)\n" +
                    "• Primeiros Socorros (Hemorragia/Torniquete, RCP, Engasgo Adulto/Bebê, Queimaduras, Soro Caseiro)\n" +
                    "• Enchentes, Deslizamentos, Terremotos, Apagão e Banheiro Seco.\n\n" +
                    "Digite sua dúvida ou toque em um dos temas rápidos abaixo!",
                mode
            )
        }

        val sb = StringBuilder()
        val primary = sources.first()
        sb.append("🛡 **Orientação Oficial de Sobrevivência para sua Família**\n")
        sb.append("Com base nas diretrizes oficiais consultadas, siga rigorosamente estes passos:\n\n")

        sources.forEachIndexed { idx, chunk ->
            val headerIcon = when (chunk.category) {
                "WAR_CONFLICT" -> "⚠️"
                "NUCLEAR_CBRN" -> "☢️"
                "MEDS" -> "🚑"
                "WATER" -> "💧"
                "NATURAL_DISASTER" -> "🌊"
                "EVACUATION" -> "🎒"
                "SANITATION" -> "🪣"
                "FOOD" -> "🥫"
                else -> "⚡"
            }
            sb.append("$headerIcon **${idx + 1}. ${chunk.docTitle}**\n")

            // Formata os passos numerados (1), 2), 3) ou •) em linhas claras para leitura rápida sob estresse
            val formattedBody = chunk.contentText
                .replace("DIRETRIZ OFICIAL", "• Fonte")
                .replace(" 1) ", "\n   1️⃣ ")
                .replace(" 2) ", "\n   2️⃣ ")
                .replace(" 3) ", "\n   3️⃣ ")
                .replace(" 4) ", "\n   4️⃣ ")
                .replace(" • ", "\n   • ")
                .trim()

            sb.append("   $formattedBody\n\n")
        }

        sb.append("✅ *Protocolo verificado nos manuais oficiais offline (${primary.category}). Se precisar de detalhes sobre outro cenário ou dosagem pediátrica, pergunte abaixo.*")
        return Pair(sb.toString(), mode)
    }
}
