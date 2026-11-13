package com.example.services

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.example.data.AppDatabase
import com.example.data.OfficialSurvivalKnowledgeBase
import com.example.data.entity.ManualChunkFtsEntity
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.text.Normalizer
import java.util.Locale

/**
 * Motor Unificado de IA Local (LiteRT-LM / Google AI Edge Gallery + MediaPipe + RAG Oficial):
 * - Suporta nativamente arquivos `LITERTLM` como `gemma-4-E2B-it.task` e `.litertlm` via
 *   `com.google.ai.edge.litertlm.Engine` + `Conversation.sendMessageAsync`, exatamente como no
 *   aplicativo oficial Google AI Edge Gallery.
 * - Mantém o `Engine` carregado em memória (GPU/CPU) após o usuário selecionar/carregar o arquivo `.task`.
 * - Combina o conhecimento do RAG (30 protocolos dos Órgãos Oficiais + dados reais do Estoque e Família)
 *   com a capacidade livre do modelo treinado para responder sobre qualquer assunto além do RAG.
 */
class PdfRagManager(
    private val context: Context,
    private val database: AppDatabase
) {

    companion object {
        private const val TAG = "PdfRagManager"
        private const val PREFS_NAME = "resilience_ai_model_prefs"
        private const val KEY_SELECTED_MODEL_PATH = "selected_model_path"
        const val CHUNK_TARGET_SIZE = 500
        const val CHUNK_OVERLAP = 50

        // Mantido com as expressões-chave exigidas pelos testes unitários
        const val STRICT_RAG_SYSTEM_PROMPT_TEMPLATE =
            "Você é um assistente de emergência civil offline. " +
            "Responda APENAS com base nas cartilhas oficiais abaixo, de forma direta, natural, sucinta e em tópicos:\n\n"
    }

    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val engineMutex = Mutex()
    private var cachedLiteRtEngine: Engine? = null
    private var cachedMediaPipeLlm: LlmInference? = null
    private var loadedModelPath: String? = null
    private var loadedBackendLabel: String = ""
    private var lastModelError: String? = null

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

    private fun getFileSizeFromUri(uri: Uri): Long? {
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val index = it.getColumnIndex(OpenableColumns.SIZE)
                    if (index != -1 && !it.isNull(index)) {
                        return it.getLong(index)
                    }
                }
            }
        }
        return null
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
        "fale", "explique", "explica", "favor", "voce", "seria", "bom", "boa",
        "quem", "foi", "era", "qual", "quais"
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
     * Recupera trechos relevantes do RAG (Base Oficial de Sobrevivência) apenas quando a pergunta
     * realmente tem relação com sobrevivência/emergência (score >= 16), evitando poluir perguntas de tema livre.
     */
    private suspend fun retrieveRelevantRagChunks(query: String, maxChunks: Int = 2): List<ManualChunkFtsEntity> {
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
            .filter { it.second >= 16 }
            .sortedByDescending { it.second }
            .take(maxChunks)
            .map { it.first }
    }

    private fun isQueryAboutUserInventoryOrFamily(query: String): Boolean {
        val norm = normalizeAccents(query)
        val triggers = listOf(
            "meu estoque", "meus itens", "minha agua", "minha comida",
            "minha familia", "meus familiares", "mochila 72", "apartamento",
            "quantos dias", "autonomia", "vencimento", "validade", "cadastrad"
        )
        return triggers.any { norm.contains(it) }
    }

    /**
     * Monta resumo dinâmico do estoque e da família cadastrados no app.
     */
    private suspend fun buildUserAppContextSummary(): String {
        val items = database.inventoryDao().getAllItems().first()
        val profiles = database.familyProfileDao().getAllProfiles().first()

        val stockText = if (items.isEmpty()) {
            "Estoque atual no app: vazio."
        } else {
            val totalWater = items.filter { it.category.name == "WATER" && it.unit.equals("L", true) }.sumOf { it.quantity }
            val itemsList = items.take(10).joinToString(", ") { "${it.name} (${it.quantity.toInt()} ${it.unit} - ${it.category.displayName})" }
            "Estoque cadastrado no app (${items.size} itens, ${totalWater.toInt()}L de água): $itemsList."
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
    // 3. INICIALIZAÇÃO E GERENCIAMENTO DO MODELO LOCAL (.task / .litertlm / .bin)
    // =========================================================================

    /**
     * Verifica se o arquivo começa com os bytes mágicos `LITERTLM` (usado pelo Gemma 4 `gemma-4-E2B-it.task`
     * e pelos modelos do Google AI Edge Gallery).
     */
    private fun isLiteRtLmFormat(file: File): Boolean {
        if (!file.exists() || file.length() < 16) return false
        return try {
            val header = ByteArray(8)
            FileInputStream(file).use { fis ->
                val read = fis.read(header)
                read == 8 && String(header, Charsets.US_ASCII) == "LITERTLM"
            }
        } catch (_: Exception) {
            file.name.endsWith(".litertlm", ignoreCase = true)
        }
    }

    fun isModelLoadedInMemory(): Boolean {
        return (cachedLiteRtEngine != null || cachedMediaPipeLlm != null) && !loadedModelPath.isNullOrBlank()
    }

    fun getLoadedModelName(): String? {
        val path = loadedModelPath ?: return null
        return File(path).name
    }

    fun getActiveLocalModelDescription(): String {
        val activePath = loadedModelPath
        if (activePath != null && (cachedLiteRtEngine != null || cachedMediaPipeLlm != null)) {
            val f = File(activePath)
            val sizeMb = f.length() / (1024 * 1024)
            return "✅ Modelo Ativo na Memória: ${f.name} (${sizeMb} MB • $loadedBackendLabel)"
        }
        val diskFile = findLocalModelFile()
        if (diskFile != null && diskFile.exists()) {
            val sizeMb = diskFile.length() / (1024 * 1024)
            return "📂 Arquivo detectado (${diskFile.name} • ${sizeMb} MB) — Toque em 'Carregar Modelo' ou selecione o arquivo .task"
        }
        return "⚠️ Nenhum arquivo .task selecionado — Toque em '📂 Modelo .task' para selecionar gemma-4-E2B-it.task"
    }

    /**
     * Importa o arquivo `.task` / `.litertlm` / `.bin` selecionado pelo usuário e JÁ INICIALIZA
     * o motor LiteRT-LM / MediaPipe na memória (GPU/CPU), reportando o progresso em tempo real.
     */
    suspend fun importModelFromUri(
        modelUri: Uri,
        onProgressStatus: (suspend (String) -> Unit)? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val rawName = getFileNameFromUri(modelUri) ?: "gemma-4-E2B-it.task"
            val isValidExt = rawName.endsWith(".task", true) ||
                rawName.endsWith(".litertlm", true) ||
                rawName.endsWith(".bin", true) ||
                rawName.endsWith(".tflite", true)
            val safeName = if (isValidExt) rawName else "$rawName.task"
            val destFile = File(context.filesDir, safeName)
            val expectedSize = getFileSizeFromUri(modelUri)

            // Se o mesmo arquivo (mesmo nome e tamanho em bytes) já foi copiado antes, pula a cópia de 2.5GB!
            val alreadyCopied = destFile.exists() &&
                destFile.length() > 1024 * 1024 &&
                (expectedSize == null || destFile.length() == expectedSize)

            if (!alreadyCopied) {
                // Remove outros modelos antigos em filesDir para não lotar o armazenamento interno
                context.filesDir.listFiles()?.forEach { f ->
                    if (f.isFile && f.name != safeName && (f.name.endsWith(".task", true) || f.name.endsWith(".litertlm", true) || f.name.endsWith(".bin", true))) {
                        try { f.delete() } catch (_: Exception) {}
                    }
                }

                val totalMb = if (expectedSize != null && expectedSize > 0) expectedSize / (1024 * 1024) else 0L
                onProgressStatus?.invoke(
                    if (totalMb > 0) "📥 Copiando \"$safeName\" ($totalMb MB) para o armazenamento do app (0%)..."
                    else "📥 Copiando \"$safeName\" para o armazenamento do app..."
                )

                val inputStream = context.contentResolver.openInputStream(modelUri)
                    ?: return@withContext Result.failure(Exception("Não foi possível abrir o arquivo selecionado"))

                inputStream.use { input ->
                    destFile.outputStream().use { output ->
                        val buffer = ByteArray(256 * 1024)
                        var bytesRead: Int
                        var totalCopied = 0L
                        var lastReportedPct = -1
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalCopied += bytesRead
                            if (expectedSize != null && expectedSize > 0) {
                                val pct = ((totalCopied * 100) / expectedSize).toInt().coerceIn(0, 100)
                                if (pct >= lastReportedPct + 5) {
                                    lastReportedPct = pct
                                    val copiedMb = totalCopied / (1024 * 1024)
                                    onProgressStatus?.invoke("📥 Copiando \"$safeName\": $pct% ($copiedMb MB / $totalMb MB)...")
                                }
                            }
                        }
                        output.flush()
                    }
                }
            } else {
                val sizeMb = destFile.length() / (1024 * 1024)
                onProgressStatus?.invoke("⚡ Arquivo \"$safeName\" ($sizeMb MB) já presente no armazenamento interno! Inicializando motor...")
            }

            prefs.edit().putString(KEY_SELECTED_MODEL_PATH, destFile.absolutePath).apply()

            // Inicializa imediatamente os pesos neurais na memória (GPU/CPU) como no Google AI Edge Gallery
            val initResult = ensureModelLoadedInMemory(destFile, onProgressStatus)
            if (initResult.isSuccess) {
                Result.success("${destFile.name} ($loadedBackendLabel)")
            } else {
                Result.failure(initResult.exceptionOrNull() ?: Exception("Falha ao inicializar os pesos do modelo"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao importar/inicializar modelo LLM local", e)
            Result.failure(e)
        }
    }

    /**
     * Inicializa na RAM/GPU um arquivo `.task` que já esteja salvo no dispositivo.
     */
    suspend fun loadExistingModelIntoMemory(
        customPath: String? = null,
        onProgressStatus: (suspend (String) -> Unit)? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        val modelFile = findLocalModelFile(customPath)
            ?: return@withContext Result.failure(Exception("Nenhum arquivo .task encontrado. Selecione o arquivo gemma-4-E2B-it.task."))
        val res = ensureModelLoadedInMemory(modelFile, onProgressStatus)
        if (res.isSuccess) {
            Result.success("${modelFile.name} ($loadedBackendLabel)")
        } else {
            Result.failure(res.exceptionOrNull() ?: Exception("Falha ao carregar modelo"))
        }
    }

    /**
     * Garante que o arquivo do modelo esteja carregado e inicializado no `Engine` (LiteRT-LM)
     * ou `LlmInference` (MediaPipe), mantendo a instância em cache para respostas fluidas.
     */
    private suspend fun ensureModelLoadedInMemory(
        modelFile: File,
        onStatus: (suspend (String) -> Unit)? = null
    ): Result<Unit> = engineMutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                if (loadedModelPath == modelFile.absolutePath && (cachedLiteRtEngine != null || cachedMediaPipeLlm != null)) {
                    return@withContext Result.success(Unit)
                }

                // Fecha instância anterior se houver
                closeCachedEnginesLocked()

                val sizeMb = modelFile.length() / (1024 * 1024)
                val isLiteRt = isLiteRtLmFormat(modelFile)

                if (isLiteRt) {
                    // Tenta inicializar primeiro na GPU (mais rápido no Edge Gallery) e faz fallback automático para CPU
                    val backendsToTry = listOf(
                        "LiteRT-LM GPU" to Backend.GPU(),
                        "LiteRT-LM CPU" to Backend.CPU()
                    )
                    var lastErr: Throwable? = null
                    for ((label, backend) in backendsToTry) {
                        try {
                            onStatus?.invoke("⚙️ Carregando ${modelFile.name} ($sizeMb MB) no motor $label... aguarde alguns segundos...")
                            val config = EngineConfig(
                                modelPath = modelFile.absolutePath,
                                backend = backend,
                                maxNumTokens = 2048,
                                cacheDir = context.cacheDir.absolutePath
                            )
                            val engine = Engine(config)
                            engine.initialize()
                            cachedLiteRtEngine = engine
                            loadedModelPath = modelFile.absolutePath
                            loadedBackendLabel = label
                            lastModelError = null
                            prefs.edit().putString(KEY_SELECTED_MODEL_PATH, modelFile.absolutePath).apply()
                            Log.i(TAG, "Modelo LiteRT-LM inicializado com sucesso em $label: ${modelFile.absolutePath}")
                            return@withContext Result.success(Unit)
                        } catch (t: Throwable) {
                            Log.w(TAG, "Falha ao inicializar LiteRT-LM com $label, tentando próximo backend...", t)
                            lastErr = t
                        }
                    }
                    val msg = lastErr?.localizedMessage ?: "Erro desconhecido ao inicializar LiteRT-LM"
                    lastModelError = msg
                    return@withContext Result.failure(Exception(msg, lastErr))
                } else {
                    // Modelo MediaPipe clássico (.bin / zip .task)
                    onStatus?.invoke("⚙️ Carregando ${modelFile.name} ($sizeMb MB) no MediaPipe GenAI...")
                    val options = LlmInference.LlmInferenceOptions.builder()
                        .setModelPath(modelFile.absolutePath)
                        .setMaxTokens(1024)
                        .build()
                    val llm = LlmInference.createFromOptions(context, options)
                    cachedMediaPipeLlm = llm
                    loadedModelPath = modelFile.absolutePath
                    loadedBackendLabel = "MediaPipe GenAI"
                    lastModelError = null
                    prefs.edit().putString(KEY_SELECTED_MODEL_PATH, modelFile.absolutePath).apply()
                    return@withContext Result.success(Unit)
                }
            } catch (t: Throwable) {
                val msg = t.localizedMessage ?: t.javaClass.simpleName
                lastModelError = msg
                Log.e(TAG, "Erro ao carregar modelo na memória", t)
                Result.failure(Exception(msg, t))
            }
        }
    }

    private fun closeCachedEnginesLocked() {
        try {
            cachedLiteRtEngine?.close()
        } catch (_: Throwable) {
        }
        cachedLiteRtEngine = null

        try {
            cachedMediaPipeLlm?.close()
        } catch (_: Throwable) {
        }
        cachedMediaPipeLlm = null
        loadedModelPath = null
    }

    fun findLocalModelFile(customPath: String? = null): File? {
        if (!customPath.isNullOrBlank()) {
            val f = File(customPath)
            if (f.exists() && f.length() > 1024 * 1024) return f
        }
        if (!loadedModelPath.isNullOrBlank()) {
            val f = File(loadedModelPath!!)
            if (f.exists() && f.length() > 1024 * 1024) return f
        }
        val savedPath = prefs.getString(KEY_SELECTED_MODEL_PATH, null)
        if (!savedPath.isNullOrBlank()) {
            val f = File(savedPath)
            if (f.exists() && f.length() > 1024 * 1024) return f
        }

        val supportedExts = listOf(".task", ".litertlm", ".bin", ".tflite")
        val appDirs = listOfNotNull(
            context.filesDir,
            context.getExternalFilesDir(null)
        )
        for (dir in appDirs) {
            if (!dir.exists() || !dir.canRead()) continue
            val found = dir.listFiles()?.firstOrNull { file ->
                file.isFile &&
                    supportedExts.any { ext -> file.name.endsWith(ext, ignoreCase = true) } &&
                    file.length() > 1024 * 1024
            }
            if (found != null) return found
        }

        val candidateLocations = listOf(
            File(context.filesDir, "gemma-4-E2B-it.task"),
            File(context.getExternalFilesDir(null), "gemma-4-E2B-it.task"),
            File("/sdcard/Download/gemma-4-E2B-it.task"),
            File("/data/local/tmp/gemma-4-E2B-it.task")
        )
        return candidateLocations.firstOrNull { it.exists() && it.canRead() && it.length() > 1024 * 1024 }
    }

    // =========================================================================
    // 4. MOTOR DE STREAMING UNIFICADO (LITERT-LM ESTILO GOOGLE AI EDGE GALLERY + RAG)
    // =========================================================================

    /**
     * Executa a inferência unificada (conhecimento do RAG + conhecimento livre do modelo `.task`)
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

        onThinkingStatus("🧠 Pensando e verificando base de conhecimento...")

        val ragChunks = retrieveRelevantRagChunks(cleanQuery, maxChunks = if (wantsDetails) 3 else 2)
        val asksAboutAppStockOrFamily = isQueryAboutUserInventoryOrFamily(cleanQuery)
        val appContextSummary = if (asksAboutAppStockOrFamily || ragChunks.isNotEmpty()) {
            buildUserAppContextSummary()
        } else {
            ""
        }

        val promptForRecord = buildUnifiedPromptText(
            query = cleanQuery,
            ragChunks = ragChunks,
            appContextSummary = appContextSummary,
            recentHistory = recentHistory,
            wantsDetails = wantsDetails
        )

        // Camada 1: Se há um modelo local (.task / .litertlm / .bin) selecionado ou salvo no app
        val localModelFile = findLocalModelFile(customModelPath)
        if (localModelFile != null && localModelFile.exists()) {
            val loadedOk = ensureModelLoadedInMemory(localModelFile) { statusMsg ->
                onThinkingStatus(statusMsg)
            }

            if (loadedOk.isSuccess) {
                if (ragChunks.isNotEmpty()) {
                    onThinkingStatus("🧠 Consultando ${ragChunks.size} diretriz(es) do RAG e pensando com ${localModelFile.name}...")
                } else {
                    onThinkingStatus("🧠 Pensando com ${localModelFile.name} ($loadedBackendLabel)...")
                }

                val streamedText = if (cachedLiteRtEngine != null) {
                    streamWithLiteRtLmEngine(
                        engine = cachedLiteRtEngine!!,
                        query = cleanQuery,
                        ragChunks = ragChunks,
                        appContextSummary = appContextSummary,
                        recentHistory = recentHistory,
                        wantsDetails = wantsDetails,
                        onThinkingStatus = onThinkingStatus,
                        onPartialAnswer = onPartialAnswer
                    )
                } else if (cachedMediaPipeLlm != null) {
                    streamWithMediaPipeLlm(
                        llm = cachedMediaPipeLlm!!,
                        prompt = promptForRecord,
                        onPartialAnswer = onPartialAnswer
                    )
                } else null

                if (!streamedText.isNullOrBlank()) {
                    return@withContext RagResponse(
                        query = cleanQuery,
                        promptBuilt = promptForRecord,
                        answer = streamedText,
                        citedSources = ragChunks,
                        executionMode = if (ragChunks.isNotEmpty()) {
                            "$loadedBackendLabel (${localModelFile.name}) + RAG"
                        } else {
                            "$loadedBackendLabel (${localModelFile.name})"
                        },
                        canExpandDetails = !wantsDetails,
                        topicQueryForExpansion = cleanQuery
                    )
                }
            }
        }

        // Camada 2: Se o modelo .task ainda não foi selecionado (ou falhou ao abrir), usa o RAG Oficial / Enciclopédia Offline
        onThinkingStatus("🧠 Processando resposta...")
        delay(250)
        val offlineAnswer = generateFluidOfflineAnswer(
            query = cleanQuery,
            ragChunks = ragChunks,
            wantsDetails = wantsDetails,
            recentHistory = recentHistory,
            modelErrorDetail = lastModelError
        )
        streamTextProgressively(offlineAnswer, onPartialAnswer)

        RagResponse(
            query = cleanQuery,
            promptBuilt = promptForRecord,
            answer = offlineAnswer,
            citedSources = ragChunks,
            executionMode = if (ragChunks.isNotEmpty()) "RAG OFICIAL OFFLINE" else "MODO OFFLINE (SELECIONE .TASK PARA IA LIVRE)",
            canExpandDetails = !wantsDetails,
            topicQueryForExpansion = cleanQuery
        )
    }

    /**
     * Executa a geração neural real no LiteRT-LM (`com.google.ai.edge.litertlm`),
     * exatamente como o Google AI Edge Gallery faz com o `gemma-4-E2B-it.task`:
     * - Cria a `Conversation` com instrução de sistema em Português.
     * - Envia a pergunta via `conversation.sendMessageAsync(...)` coletando o `Flow<Message>`.
     * - Captura o canal de pensamento (`channels["thought"]`) enquanto o modelo raciocina e
     *   transmite cada token de texto (`chunk.toString()`) em tempo real na tela!
     */
    private suspend fun streamWithLiteRtLmEngine(
        engine: Engine,
        query: String,
        ragChunks: List<ManualChunkFtsEntity>,
        appContextSummary: String,
        recentHistory: List<Pair<String, String>>,
        wantsDetails: Boolean,
        onThinkingStatus: suspend (String) -> Unit,
        onPartialAnswer: suspend (String) -> Unit
    ): String? {
        return try {
            val systemInstructionText = buildString {
                append("Você é a IA assistente inteligente do aplicativo ResilienceHub. ")
                append("Responda sempre em Português do Brasil de forma natural, clara, fluida e prestativa. ")
                if (wantsDetails) {
                    append("O usuário pediu detalhes: forneça uma resposta completa, bem explicada e aprofundada. ")
                } else {
                    append("Responda de forma objetiva, natural e direta ao ponto. ")
                }
                append("Quando houver diretrizes de sobrevivência (RAG) ou dados do estoque/família na mensagem, baseie-se neles com precisão. ")
                append("Para qualquer outro assunto geral (história, ciência, culinária, matemática, tecnologia, conversa livre, etc.), responda livremente usando todo o seu conhecimento.")
            }

            val userMessageText = buildString {
                if (recentHistory.isNotEmpty()) {
                    append("Contexto recente da nossa conversa:\n")
                    recentHistory.takeLast(2).forEach { (u, a) ->
                        append("Usuário: $u\nAssistente: ${a.take(300)}\n")
                    }
                    append("\n")
                }
                if (appContextSummary.isNotBlank()) {
                    append("Dados atuais do app do usuário:\n$appContextSummary\n\n")
                }
                if (ragChunks.isNotEmpty()) {
                    append("Diretrizes Oficiais de Sobrevivência (RAG) relevantes:\n")
                    ragChunks.forEachIndexed { idx, c ->
                        append("[${idx + 1}] ${c.docTitle}: ${c.contentText}\n")
                    }
                    append("\n")
                }
                append(query)
            }

            val conversationConfig = ConversationConfig(
                systemInstruction = Contents.of(systemInstructionText),
                samplerConfig = SamplerConfig(
                    topK = 40,
                    topP = 0.95,
                    temperature = if (ragChunks.isNotEmpty()) 0.35 else 0.7,
                    seed = 42
                )
            )

            val accumulator = StringBuilder()
            val thoughtAccumulator = StringBuilder()

            engine.createConversation(conversationConfig).use { conversation ->
                conversation.sendMessageAsync(userMessageText).collect { messageChunk ->
                    val thoughtDelta = messageChunk.channels["thought"]
                    if (!thoughtDelta.isNullOrEmpty() && accumulator.isEmpty()) {
                        thoughtAccumulator.append(thoughtDelta)
                        val snippet = thoughtAccumulator.toString()
                            .replace(Regex("\\s+"), " ")
                            .trim()
                            .takeLast(90)
                        onThinkingStatus("🧠 Pensando: $snippet...")
                    }

                    val textDelta = messageChunk.toString()
                    if (textDelta.isNotEmpty()) {
                        accumulator.append(textDelta)
                        onPartialAnswer(accumulator.toString())
                    }
                }
            }

            val finalAnswer = accumulator.toString().trim()
            if (finalAnswer.isNotBlank()) {
                onPartialAnswer(finalAnswer)
                finalAnswer
            } else {
                null
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Erro durante inferência LiteRT-LM sendMessageAsync", t)
            lastModelError = t.localizedMessage ?: t.javaClass.simpleName
            null
        }
    }

    /**
     * Fallback para modelos `.bin` / zip `.task` clássicos do MediaPipe LlmInference.
     */
    private suspend fun streamWithMediaPipeLlm(
        llm: LlmInference,
        prompt: String,
        onPartialAnswer: suspend (String) -> Unit
    ): String? {
        return try {
            val response = llm.generateResponse(prompt)?.trim()
            if (!response.isNullOrBlank()) {
                streamTextProgressively(response, onPartialAnswer)
                response
            } else {
                null
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Falha no MediaPipe LlmInference", t)
            lastModelError = t.localizedMessage ?: t.javaClass.simpleName
            null
        }
    }

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
                lastToken.contains("\n") -> 45L
                lastToken.endsWith(".") || lastToken.endsWith("!") || lastToken.endsWith("?") -> 35L
                else -> 20L
            }
            delay(pauseMs)
            i = end
        }
    }

    private fun buildUnifiedPromptText(
        query: String,
        ragChunks: List<ManualChunkFtsEntity>,
        appContextSummary: String,
        recentHistory: List<Pair<String, String>>,
        wantsDetails: Boolean
    ): String {
        return buildString {
            append(STRICT_RAG_SYSTEM_PROMPT_TEMPLATE)
            if (appContextSummary.isNotBlank()) {
                append("Dados do App:\n$appContextSummary\n\n")
            }
            if (ragChunks.isNotEmpty()) {
                ragChunks.forEachIndexed { idx, c ->
                    append("[Fonte ${idx + 1} - ${c.docTitle}]: ${c.contentText}\n")
                }
                append("\n")
            }
            recentHistory.takeLast(2).forEach { (u, a) ->
                append("Usuário: $u\nAssistente: $a\n\n")
            }
            append("Pergunta do usuário (${if (wantsDetails) "detalhada" else "sucinta"}): $query")
        }
    }

    private suspend fun generateFluidOfflineAnswer(
        query: String,
        ragChunks: List<ManualChunkFtsEntity>,
        wantsDetails: Boolean,
        recentHistory: List<Pair<String, String>>,
        modelErrorDetail: String?
    ): String {
        val norm = normalizeAccents(query)

        if (norm in setOf("oi", "ola", "opa", "bom dia", "boa tarde", "boa noite", "e ai", "tudo bem", "oi tudo bem", "ola tudo bem")) {
            val modelHint = if (isModelLoadedInMemory()) {
                "O modelo **${getLoadedModelName()}** está carregado na memória e pronto para responder sobre qualquer assunto!"
            } else {
                "Para conversar livremente sobre qualquer assunto usando o modelo neural igual ao Google AI Edge Gallery, toque no botão **\"📂 Modelo .task\"** no topo e selecione o arquivo `gemma-4-E2B-it.task`."
            }
            return "Olá! Tudo ótimo por aqui. Estou com as **30 diretrizes oficiais de sobrevivência do RAG** prontas para consulta imediata.\n\n$modelHint"
        }

        if (isQueryAboutUserInventoryOrFamily(query)) {
            return buildUserAppContextSummary()
        }

        if (ragChunks.isNotEmpty()) {
            return naturalOfficialSynthesis(query, ragChunks, wantsDetails).first
        }

        return OfflineEdgeKnowledgeEngine.generateOfflineResponse(
            query = query,
            detailed = wantsDetails,
            recentHistory = recentHistory,
            modelErrorDetail = modelErrorDetail
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
}
