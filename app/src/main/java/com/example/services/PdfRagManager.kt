package com.example.services

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.example.data.AppDatabase
import com.example.data.OfficialSurvivalKnowledgeBase
import com.example.data.entity.ManualChunkFtsEntity
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.text.Normalizer
import java.util.Locale

/**
 * Motor Unificado de IA Local (idêntico à arquitetura do Google AI Edge Gallery + RAG Oficial):
 * - Executa o modelo `gemma-4-E2B-it.task` / `.litertlm` via `com.google.ai.edge.litertlm.Engine`
 *   e mantém uma instância persistente de `Conversation` (KV-cache nativo multi-turn) com
 *   `SamplerConfig(topK = 64, topP = 0.95, temperature = 1.0)` e `MessageCallback` assíncrono,
 *   exatamente como em `LlmChatModelHelper.kt` do Google AI Edge Gallery.
 * - Combina de forma fluida e não-engessada o conhecimento do RAG (quando a pergunta é sobre
 *   sobrevivência/estoque/família) com a geração 100% livre do modelo treinado para qualquer assunto.
 */
class PdfRagManager(
    private val context: Context,
    private val database: AppDatabase
) {

    companion object {
        private const val TAG = "PdfRagManager"
        private const val PREFS_NAME = "resilience_ai_model_prefs"
        private const val KEY_SELECTED_MODEL_PATH = "selected_model_path"
        private const val KEY_PREFERRED_BACKEND = "preferred_backend" // "GPU" ou "CPU"
        const val CHUNK_TARGET_SIZE = 500
        const val CHUNK_OVERLAP = 50

        // Parâmetros oficiais padrão do Google AI Edge Gallery (Consts.kt)
        private const val DEFAULT_MAX_TOKEN = 1024
        private const val DEFAULT_TOPK = 64
        private const val DEFAULT_TOPP = 0.95
        private const val DEFAULT_TEMPERATURE = 1.0
        private const val THOUGHT_CHANNEL = "thought"

        // Mantido com as expressões-chave exigidas pelos testes unitários (Etapa2ComponentsTest)
        const val STRICT_RAG_SYSTEM_PROMPT_TEMPLATE =
            "Você é um assistente de emergência civil offline. " +
            "Responda APENAS com base nas cartilhas oficiais abaixo, de forma direta, natural, sucinta e em tópicos:\n\n"
    }

    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val engineMutex = Mutex()
    private val inferenceMutex = Mutex()
    private var cachedLiteRtEngine: Engine? = null
    private var cachedLiteRtConversation: Conversation? = null
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
    // 2. BUSCA SEMÂNTICA PRECISA NO RAG E CONTEXTO DO APP (SEM FALSOS POSITIVOS)
    // =========================================================================

    private val stopWords = setOf(
        "como", "para", "qual", "quais", "que", "com", "sem", "nos", "nas",
        "dos", "das", "por", "uma", "uns", "umas", "isso", "este", "esta",
        "onde", "quando", "porque", "deve", "devo", "fazer", "usar", "tem",
        "sao", "meu", "minha", "meus", "minhas", "estao", "esta", "estou",
        "pode", "posso", "ajuda", "socorro", "faco", "caso", "tipo", "sobre",
        "mais", "menos", "muito", "pouco", "quero", "queria", "saber", "diga",
        "fale", "explique", "explica", "favor", "voce", "seria", "bom", "boa",
        "quem", "foi", "era", "historia", "receita", "conta", "calculo"
    )

    private val detailRequestKeywords = listOf(
        "detalh", "complet", "aprofund", "passo a passo", "tudo sobre",
        "mais informac", "explica melhor", "explique melhor", "mais sobre"
    )

    private val semanticSynonyms: Map<String, List<String>> = mapOf(
        "agua" to listOf("hipoclorito", "clorin", "fervura", "purificacao", "potavel", "filtrar"),
        "potavel" to listOf("agua", "hipoclorito", "clorin", "fervura", "purificacao"),
        "hipoclorito" to listOf("agua", "clorin", "gotas", "purificacao", "potavel"),
        "cloro" to listOf("hipoclorito", "clorin", "agua", "gotas", "purificacao"),
        "guerra" to listOf("conflito", "bombardeio", "artilharia", "duas paredes", "onda de choque", "misseis", "tiroteio", "cicv", "uxo"),
        "bombardeio" to listOf("guerra", "onda de choque", "duas paredes", "abrigamento", "misseis", "artilharia"),
        "misseis" to listOf("bombardeio", "guerra", "duas paredes", "onda de choque", "subsolo"),
        "tiroteio" to listOf("guerra", "conflito", "chao", "valeta", "cicv"),
        "nuclear" to listOf("radiologica", "fallout", "atomica", "regra 7/10", "iodeto", "descontaminacao", "cnen"),
        "radiacao" to listOf("nuclear", "fallout", "regra 7/10", "descontaminacao", "iodeto", "cnen"),
        "fallout" to listOf("nuclear", "radiacao", "regra 7/10", "descontaminacao", "cnen"),
        "quimico" to listOf("toxico", "biologico", "vedacao", "andares altos", "mascara"),
        "sangue" to listOf("hemorragia", "torniquete", "compressao", "sangramento", "arterial", "march"),
        "sangrando" to listOf("hemorragia", "torniquete", "compressao", "arterial"),
        "hemorragia" to listOf("torniquete", "compressao", "sangramento", "arterial", "march"),
        "torniquete" to listOf("hemorragia", "cat", "molinete", "sangramento", "march"),
        "engasgo" to listOf("engasgou", "heimlich", "asfixia", "tapas nas costas", "obstrucao"),
        "engasgou" to listOf("engasgo", "heimlich", "asfixia", "tapas nas costas"),
        "heimlich" to listOf("engasgo", "asfixia", "obstrucao"),
        "rcp" to listOf("parada cardiaca", "ressuscitacao", "compressoes", "massagem cardiaca"),
        "queimadura" to listOf("queimou", "agua corrente", "bolhas", "termica"),
        "diarreia" to listOf("soro caseiro", "desidratacao", "sro", "vomito", "colera"),
        "desidratacao" to listOf("soro caseiro", "sro", "diarreia", "vomito"),
        "fratura" to listOf("osso", "tala", "imobilizacao", "coluna"),
        "hipotermia" to listOf("frio", "manta termica", "roupas molhadas", "tremores"),
        "insolacao" to listOf("exaustao termica", "sombra", "hidratacao"),
        "cobra" to listOf("peconhentos", "serpente", "escorpiao", "aranha", "veneno", "picada", "butantan"),
        "escorpiao" to listOf("peconhentos", "picada", "compressas mornas", "veneno", "butantan"),
        "aranha" to listOf("peconhentos", "picada", "veneno", "butantan"),
        "picada" to listOf("peconhentos", "cobra", "escorpiao", "aranha", "anafilaxia"),
        "enchente" to listOf("inundacao", "alagamento", "enxurrada", "leptospirose", "disjuntor"),
        "alagamento" to listOf("enchente", "inundacao", "enxurrada", "leptospirose"),
        "deslizamento" to listOf("encosta", "barranco", "rachaduras", "lama", "cemaden"),
        "barranco" to listOf("deslizamento", "encosta", "rachaduras", "cemaden"),
        "terremoto" to listOf("abalo", "escombros", "desabamento", "soterramento", "apito"),
        "escombros" to listOf("terremoto", "desabamento", "apito", "poeira", "soterrado"),
        "apagao" to listOf("blackout", "energia", "geladeira", "gerador", "monoxido", "lanterna"),
        "blackout" to listOf("apagao", "energia", "geladeira", "monoxido"),
        "emp" to listOf("pulso eletromagnetico", "faraday", "papel-aluminio", "tempestade solar"),
        "faraday" to listOf("pulso eletromagnetico", "emp", "papel-aluminio", "eletronicos"),
        "banheiro" to listOf("saneamento", "esgoto", "dois baldes", "fezes", "urina", "colera"),
        "saneamento" to listOf("banheiro seco", "dois baldes", "esgoto", "colera"),
        "incendio" to listOf("fogo", "fumaca", "porta quente", "rasteje", "bombeiros"),
        "fumaca" to listOf("incendio", "rasteje", "porta", "toalhas molhadas"),
        "mochila" to listOf("72 horas", "bugout", "evacuacao", "kit", "emergencia"),
        "evacuacao" to listOf("mochila", "72 horas", "ponto de encontro", "disjuntor"),
        "panico" to listOf("psicologicos", "respiracao 4-4-4-4", "ansiedade"),
        "convulsao" to listOf("epileptico", "crises medicas", "posicao lateral"),
        "asma" to listOf("falta de ar", "bombinha", "salbutamol", "crises medicas"),
        "infarto" to listOf("dor no peito", "aas", "coracao", "crises medicas")
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

            // Apenas casamento exato de palavra no mapa de sinônimos de sobrevivência (evita que "vargas" case com "gas")
            semanticSynonyms[norm]?.forEach { syn ->
                val normSyn = normalizeAccents(syn)
                weightedTerms[normSyn] = maxOf(weightedTerms[normSyn] ?: 0, 10)
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
     * Recupera trechos do RAG Oficial apenas quando a pergunta tem relação real e direta com
     * protocolos de sobrevivência/emergência (score >= 24 com palavras inteiras), mantendo perguntas
     * de tema livre 100% limpas como no Google AI Edge Gallery.
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
            var score = 0
            for ((term, weight) in expandedTerms) {
                val exactWordRegex = Regex("\\b${Regex.escape(term)}\\b")
                if (exactWordRegex.containsMatchIn(normTitle)) score += (weight * 2)
                if (exactWordRegex.containsMatchIn(normContent)) score += weight
            }
            Pair(chunk, score)
        }
            .filter { it.second >= 24 }
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
    // 3. INICIALIZAÇÃO DO MODELO LOCAL (.task / .litertlm) ESTILO GOOGLE AI EDGE GALLERY
    // =========================================================================

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

    fun getPreferredBackend(): String {
        return prefs.getString(KEY_PREFERRED_BACKEND, "GPU") ?: "GPU"
    }

    fun setPreferredBackend(backendName: String) {
        val normalized = if (backendName.equals("CPU", ignoreCase = true)) "CPU" else "GPU"
        prefs.edit().putString(KEY_PREFERRED_BACKEND, normalized).apply()
    }

    fun isModelLoadedInMemory(): Boolean {
        return (cachedLiteRtEngine != null || cachedMediaPipeLlm != null) && !loadedModelPath.isNullOrBlank()
    }

    fun getLoadedModelName(): String? {
        val path = loadedModelPath ?: return null
        return File(path).name
    }

    fun getLoadedBackendLabel(): String = loadedBackendLabel

    fun getLastModelError(): String? = lastModelError

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
            val errSuffix = if (!lastModelError.isNullOrBlank()) "\n⚠️ Último erro: $lastModelError" else ""
            return "📂 Arquivo no aparelho: ${diskFile.name} (${sizeMb} MB) — Toque em 'Ativar Salvo' para inicializar na ${getPreferredBackend()}$errSuffix"
        }
        return "⚠️ Nenhum arquivo .task selecionado — Toque em 'Selecionar .task' e escolha seu gemma-4-E2B-it.task"
    }

    /**
     * Retorna os diretórios onde o modelo `.task` pode estar armazenado no app (incluindo
     * `getExternalFilesDir(null)/imports` usado pelo Google AI Edge Gallery, `noBackupFilesDir` e `filesDir`).
     */
    private fun getCandidateModelDirectories(): List<File> {
        val extDir = context.getExternalFilesDir(null)
        return listOfNotNull(
            extDir?.let { File(it, "imports") },
            extDir,
            File(context.noBackupFilesDir, "models"),
            context.noBackupFilesDir,
            context.filesDir
        )
    }

    /**
     * Importa o arquivo `.task` / `.litertlm` selecionado pelo usuário (reaproveitando cópia já existente
     * instantaneamente sem duplicar 2.58 GB) e inicializa o `Engine` + `Conversation` na memória.
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
            val expectedSize = getFileSizeFromUri(modelUri)

            // 1. Verifica se o arquivo já existe em qualquer pasta do app (evita copiar 2.58 GB de novo!)
            val existingCopy = getCandidateModelDirectories()
                .map { File(it, safeName) }
                .firstOrNull { f ->
                    f.exists() && f.isFile && f.length() > 50L * 1024 * 1024 &&
                        (expectedSize == null || expectedSize <= 0L || f.length() == expectedSize)
                }

            val targetFile: File
            if (existingCopy != null) {
                targetFile = existingCopy
                val sizeMb = targetFile.length() / (1024 * 1024)
                onProgressStatus?.invoke("⚡ Arquivo \"$safeName\" ($sizeMb MB) já encontrado no armazenamento do app! Inicializando motor...")
            } else {
                // Usa getExternalFilesDir(null)/imports igual ao Google AI Edge Gallery (sem limite de quota interna e fora do Auto-Backup)
                val baseDir = context.getExternalFilesDir(null) ?: context.noBackupFilesDir
                val importsDir = File(baseDir, "imports").apply { if (!exists()) mkdirs() }
                targetFile = File(importsDir, safeName)

                // Limpa versões antigas/incompletas para garantir espaço livre
                getCandidateModelDirectories().forEach { dir ->
                    dir.listFiles()?.forEach { f ->
                        if (f.isFile && f.absolutePath != targetFile.absolutePath &&
                            (f.name.endsWith(".task", true) || f.name.endsWith(".litertlm", true) || f.name.endsWith(".bin", true))
                        ) {
                            try { f.delete() } catch (_: Exception) {}
                        }
                    }
                }

                val totalMb = if (expectedSize != null && expectedSize > 0) expectedSize / (1024 * 1024) else 0L
                onProgressStatus?.invoke(
                    if (totalMb > 0) "📥 Copiando \"$safeName\" ($totalMb MB) para o app (0%)..."
                    else "📥 Copiando \"$safeName\" para o armazenamento do app..."
                )

                val inputStream = context.contentResolver.openInputStream(modelUri)
                    ?: return@withContext Result.failure(Exception("Não foi possível abrir o arquivo selecionado"))

                inputStream.use { input ->
                    targetFile.outputStream().use { output ->
                        val buffer = ByteArray(1024 * 1024) // 1 MB buffer para cópia rápida
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
            }

            prefs.edit().putString(KEY_SELECTED_MODEL_PATH, targetFile.absolutePath).apply()

            // 2. Inicializa imediatamente o Engine e a Conversation na memória (GPU/CPU)
            val initResult = ensureModelLoadedInMemory(targetFile, forceReload = true, onStatus = onProgressStatus)
            if (initResult.isSuccess) {
                Result.success("${targetFile.name} ($loadedBackendLabel)")
            } else {
                Result.failure(initResult.exceptionOrNull() ?: Exception("Falha ao inicializar os pesos do modelo"))
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Erro ao importar/inicializar modelo LLM local", e)
            lastModelError = e.localizedMessage ?: e.javaClass.simpleName
            Result.failure(Exception(lastModelError, e))
        }
    }

    /**
     * Inicializa na RAM/GPU um arquivo `.task` já salvo no dispositivo.
     */
    suspend fun loadExistingModelIntoMemory(
        customPath: String? = null,
        forceReload: Boolean = false,
        onProgressStatus: (suspend (String) -> Unit)? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        val modelFile = findLocalModelFile(customPath)
            ?: return@withContext Result.failure(Exception("Nenhum arquivo .task encontrado. Toque em 'Selecionar .task' e escolha o gemma-4-E2B-it.task."))
        val res = ensureModelLoadedInMemory(modelFile, forceReload = forceReload, onStatus = onProgressStatus)
        if (res.isSuccess) {
            Result.success("${modelFile.name} ($loadedBackendLabel)")
        } else {
            Result.failure(res.exceptionOrNull() ?: Exception("Falha ao carregar modelo"))
        }
    }

    /**
     * Inicializa o `Engine` e cria a `Conversation` persistente exatamente como em `LlmChatModelHelper.kt`
     * do Google AI Edge Gallery (`DEFAULT_MAX_TOKEN = 1024`, `cacheDir = null`, `topK = 64`, `topP = 0.95`, `temperature = 1.0`).
     */
    private suspend fun ensureModelLoadedInMemory(
        modelFile: File,
        forceReload: Boolean = false,
        onStatus: (suspend (String) -> Unit)? = null
    ): Result<Unit> = engineMutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                if (!forceReload &&
                    loadedModelPath == modelFile.absolutePath &&
                    (cachedLiteRtEngine != null || cachedMediaPipeLlm != null)
                ) {
                    if (cachedLiteRtEngine != null && (cachedLiteRtConversation == null || !cachedLiteRtConversation!!.isAlive)) {
                        cachedLiteRtConversation = createNewLiteRtConversation(cachedLiteRtEngine!!)
                    }
                    return@withContext Result.success(Unit)
                }

                closeCachedEnginesLocked()

                val sizeMb = modelFile.length() / (1024 * 1024)
                val isLiteRt = isLiteRtLmFormat(modelFile)

                if (isLiteRt) {
                    val pref = getPreferredBackend()
                    val backendsToTry = if (pref.equals("CPU", ignoreCase = true)) {
                        listOf("CPU" to Backend.CPU(), "GPU" to Backend.GPU())
                    } else {
                        listOf("GPU" to Backend.GPU(), "CPU" to Backend.CPU())
                    }

                    // Em LlmChatModelHelper.kt do Google AI Edge Gallery, cacheDir é null quando o modelo
                    // está no armazenamento do app, ou externalFilesDir quando está em /data/local/tmp
                    val effectiveCacheDir = if (modelFile.absolutePath.startsWith("/data/local/tmp")) {
                        context.getExternalFilesDir(null)?.absolutePath
                    } else {
                        null
                    }

                    var lastErr: Throwable? = null
                    for ((label, backend) in backendsToTry) {
                        try {
                            onStatus?.invoke("⚙️ Inicializando ${modelFile.name} ($sizeMb MB) via LiteRT-LM ($label)... aguarde")
                            val engineConfig = EngineConfig(
                                modelPath = modelFile.absolutePath,
                                backend = backend,
                                visionBackend = null,
                                audioBackend = null,
                                maxNumTokens = DEFAULT_MAX_TOKEN,
                                cacheDir = effectiveCacheDir
                            )
                            val engine = Engine(engineConfig)
                            engine.initialize()
                            val conversation = createNewLiteRtConversation(engine)

                            cachedLiteRtEngine = engine
                            cachedLiteRtConversation = conversation
                            loadedModelPath = modelFile.absolutePath
                            loadedBackendLabel = label
                            lastModelError = null
                            prefs.edit().putString(KEY_SELECTED_MODEL_PATH, modelFile.absolutePath).apply()
                            Log.i(TAG, "LiteRT-LM Engine + Conversation inicializados com sucesso em $label: ${modelFile.absolutePath}")
                            return@withContext Result.success(Unit)
                        } catch (t: Throwable) {
                            Log.w(TAG, "Falha ao inicializar LiteRT-LM com $label, tentando alternativa...", t)
                            lastErr = t
                        }
                    }
                    val rawMsg = lastErr?.localizedMessage ?: lastErr?.javaClass?.simpleName ?: "Erro desconhecido no LiteRT-LM"
                    val cleanMsg = cleanUpTaskErrorMessage(rawMsg)
                    lastModelError = cleanMsg
                    return@withContext Result.failure(Exception(cleanMsg, lastErr))
                } else {
                    onStatus?.invoke("⚙️ Carregando ${modelFile.name} ($sizeMb MB) no MediaPipe GenAI...")
                    val options = LlmInference.LlmInferenceOptions.builder()
                        .setModelPath(modelFile.absolutePath)
                        .setMaxTokens(DEFAULT_MAX_TOKEN)
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
                val msg = cleanUpTaskErrorMessage(t.localizedMessage ?: t.javaClass.simpleName)
                lastModelError = msg
                Log.e(TAG, "Erro ao carregar modelo na memória", t)
                Result.failure(Exception(msg, t))
            }
        }
    }

    /**
     * Cria uma nova `Conversation` com os mesmos parâmetros de amostragem do Google AI Edge Gallery
     * (`topK = 64`, `topP = 0.95`, `temperature = 1.0`) e instrução de sistema fluida em Português.
     */
    private fun createNewLiteRtConversation(engine: Engine): Conversation {
        val systemInstructionText =
            "Você é um assistente de IA inteligente, fluido e prestativo integrado ao aplicativo ResilienceHub. " +
            "Responda sempre em Português do Brasil de maneira natural, clara, completa e conversacional, " +
            "exatamente como em um bate-papo fluido. " +
            "Quando a mensagem trouxer dados de referência do aplicativo (protocolos de sobrevivência ou estoque/família do usuário) " +
            "e eles forem relevantes para a pergunta, utilize essas informações com precisão na sua explicação natural. " +
            "Para qualquer outro assunto livre (história, ciências, culinária, matemática, tecnologia, curiosidades, etc.), " +
            "responda livremente usando todo o seu conhecimento."

        val samplerConfig = SamplerConfig(
            topK = DEFAULT_TOPK,
            topP = DEFAULT_TOPP,
            temperature = DEFAULT_TEMPERATURE
        )

        return engine.createConversation(
            ConversationConfig(
                systemInstruction = Contents.of(systemInstructionText),
                samplerConfig = samplerConfig
            )
        )
    }

    /**
     * Reinicia a `Conversation` (limpa o KV-cache da conversa mantendo os pesos do `Engine` na RAM/GPU),
     * idêntico ao `resetConversation` do Google AI Edge Gallery quando o usuário limpa o histórico do chat.
     */
    fun resetConversation() {
        CoroutineScope(Dispatchers.IO).launch {
            engineMutex.withLock {
                val engine = cachedLiteRtEngine ?: return@withLock
                val oldConv = cachedLiteRtConversation
                cachedLiteRtConversation = null
                try {
                    oldConv?.cancelProcess()
                } catch (_: Throwable) {
                }
                try {
                    oldConv?.close()
                } catch (_: Throwable) {
                }
                try {
                    cachedLiteRtConversation = createNewLiteRtConversation(engine)
                } catch (t: Throwable) {
                    Log.w(TAG, "Falha ao recriar Conversation após reset", t)
                }
            }
        }
    }

    private fun cleanUpTaskErrorMessage(message: String): String {
        val index = message.indexOf("=== Source Location Trace")
        return if (index >= 0) message.substring(0, index).trim() else message.trim()
    }

    private fun closeCachedEnginesLocked() {
        try {
            cachedLiteRtConversation?.cancelProcess()
        } catch (_: Throwable) {
        }
        try {
            cachedLiteRtConversation?.close()
        } catch (_: Throwable) {
        }
        cachedLiteRtConversation = null

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
        for (dir in getCandidateModelDirectories()) {
            if (!dir.exists() || !dir.canRead()) continue
            val found = dir.listFiles()?.firstOrNull { file ->
                file.isFile &&
                    supportedExts.any { ext -> file.name.endsWith(ext, ignoreCase = true) } &&
                    file.length() > 1024 * 1024
            }
            if (found != null) return found
        }

        val explicitCandidates = listOf(
            File("/sdcard/Download/gemma-4-E2B-it.task"),
            File("/storage/emulated/0/Download/gemma-4-E2B-it.task"),
            File("/data/local/tmp/gemma-4-E2B-it.task")
        )
        return explicitCandidates.firstOrNull { it.exists() && it.canRead() && it.length() > 1024 * 1024 }
    }

    // =========================================================================
    // 4. MOTOR DE STREAMING UNIFICADO (IDÊNTICO AO GOOGLE AI EDGE GALLERY + RAG)
    // =========================================================================

    suspend fun streamUnifiedAiResponse(
        query: String,
        recentHistory: List<Pair<String, String>> = emptyList(),
        forceDetailed: Boolean = false,
        customModelPath: String? = null,
        onThinkingStatus: (String) -> Unit,
        onPartialAnswer: (String) -> Unit
    ): RagResponse = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        val wantsDetails = forceDetailed || isUserAskingForMoreDetails(cleanQuery)

        onThinkingStatus("🧠 Pensando...")

        val ragChunks = retrieveRelevantRagChunks(cleanQuery, maxChunks = 2)
        val asksAboutAppStockOrFamily = isQueryAboutUserInventoryOrFamily(cleanQuery)
        val appContextSummary = if (asksAboutAppStockOrFamily) {
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

        // Camada 1: Se há um modelo local (.task / .litertlm) no aparelho, garante inicialização e executa inferência neural
        val localModelFile = findLocalModelFile(customModelPath)
        if (localModelFile != null && localModelFile.exists()) {
            val loadedOk = ensureModelLoadedInMemory(localModelFile, forceReload = false) { statusMsg ->
                onThinkingStatus(statusMsg)
            }

            if (loadedOk.isSuccess) {
                onThinkingStatus("🧠 Pensando com ${localModelFile.name} ($loadedBackendLabel)...")

                val streamedText = if (cachedLiteRtEngine != null) {
                    streamWithLiteRtLmConversation(
                        engine = cachedLiteRtEngine!!,
                        query = cleanQuery,
                        ragChunks = ragChunks,
                        appContextSummary = appContextSummary,
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
                        canExpandDetails = false,
                        topicQueryForExpansion = null
                    )
                }
            }
        }

        // Camada 2: Só é acionada se nenhum arquivo .task foi selecionado ainda (ou se o hardware falhou ao abrir)
        onThinkingStatus("🧠 Processando resposta...")
        delay(200)
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
            executionMode = if (ragChunks.isNotEmpty()) "RAG OFICIAL OFFLINE" else "MODO OFFLINE (CARREGUE .TASK PARA IA NEURAL)",
            canExpandDetails = false,
            topicQueryForExpansion = null
        )
    }

    /**
     * Executa a inferência na `Conversation` persistente do `LiteRT-LM` usando `MessageCallback`
     * e `Contents.of(listOf(Content.Text(...)))`, exatamente como em `LlmChatModelHelper.runInference`
     * do Google AI Edge Gallery:
     * - Para perguntas de tema livre, envia apenas `query` puro (sem textos extras que engessem o modelo).
     * - Para perguntas sobre sobrevivência/estoque, anexa os dados do RAG como nota de apoio e deixa
     *   o modelo redigir a resposta com total fluidez e naturalidade.
     */
    private suspend fun streamWithLiteRtLmConversation(
        engine: Engine,
        query: String,
        ragChunks: List<ManualChunkFtsEntity>,
        appContextSummary: String,
        onThinkingStatus: (String) -> Unit,
        onPartialAnswer: (String) -> Unit
    ): String? = inferenceMutex.withLock {
        try {
            // Se o contexto acumulado na sessão se aproximar do limite de tokens, renova a Conversation preventivamente
            var conversation = cachedLiteRtConversation
            val currentTokens = try { conversation?.tokenCount ?: 0 } catch (_: Throwable) { 0 }
            if (conversation == null || !conversation.isAlive || currentTokens > (DEFAULT_MAX_TOKEN - 320)) {
                try { conversation?.close() } catch (_: Throwable) {}
                conversation = createNewLiteRtConversation(engine)
                cachedLiteRtConversation = conversation
            }

            // Para perguntas gerais, envia a pergunta pura exatamente como no Google AI Edge Gallery!
            val userMessageText = if (ragChunks.isEmpty() && appContextSummary.isBlank()) {
                query
            } else {
                buildString {
                    append("[Contexto de referência do app ResilienceHub:\n")
                    if (appContextSummary.isNotBlank()) {
                        append(appContextSummary).append("\n")
                    }
                    ragChunks.forEach { c ->
                        append("- ${c.docTitle}: ${c.contentText}\n")
                    }
                    append("]\n\n")
                    append(query)
                }
            }

            val accumulator = StringBuilder()
            val thoughtAccumulator = StringBuilder()
            val doneDeferred = CompletableDeferred<Boolean>()

            // Igual a LlmChatModelHelper.kt do Google AI Edge Gallery:
            val contents = Contents.of(listOf(Content.Text(userMessageText)))
            val extraContext: Map<String, Any> = mapOf("enable_thinking" to false)

            conversation.sendMessageAsync(
                contents,
                object : MessageCallback {
                    override fun onMessage(message: Message) {
                        val thinking = message.channels[THOUGHT_CHANNEL]
                        if (!thinking.isNullOrEmpty() && accumulator.isEmpty()) {
                            thoughtAccumulator.append(thinking)
                            val snippet = thoughtAccumulator.toString()
                                .replace(Regex("\\s+"), " ")
                                .trim()
                                .takeLast(90)
                            onThinkingStatus("🧠 Pensando: $snippet...")
                        }

                        val textDelta = message.toString()
                        if (textDelta.isNotEmpty()) {
                            accumulator.append(textDelta)
                            onPartialAnswer(accumulator.toString())
                        }
                    }

                    override fun onDone() {
                        doneDeferred.complete(true)
                    }

                    override fun onError(throwable: Throwable) {
                        Log.e(TAG, "Erro durante streaming em MessageCallback LiteRT-LM", throwable)
                        lastModelError = cleanUpTaskErrorMessage(
                            throwable.localizedMessage ?: throwable.javaClass.simpleName
                        )
                        doneDeferred.complete(false)
                    }
                },
                extraContext
            )

            val finishedOk = doneDeferred.await()
            val finalAnswer = accumulator.toString().trim()

            // Se a Conversation falhou por limite de KV-cache, recria para a próxima mensagem
            if (!finishedOk && finalAnswer.isBlank()) {
                try { conversation.close() } catch (_: Throwable) {}
                cachedLiteRtConversation = try { createNewLiteRtConversation(engine) } catch (_: Throwable) { null }
                return@withLock null
            }

            if (finalAnswer.isNotBlank()) {
                onPartialAnswer(finalAnswer)
                finalAnswer
            } else {
                null
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Exceção durante inferência LiteRT-LM", t)
            lastModelError = cleanUpTaskErrorMessage(t.localizedMessage ?: t.javaClass.simpleName)
            null
        }
    }

    private suspend fun streamWithMediaPipeLlm(
        llm: LlmInference,
        prompt: String,
        onPartialAnswer: (String) -> Unit
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
        onPartialAnswer: (String) -> Unit
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
                lastToken.contains("\n") -> 40L
                lastToken.endsWith(".") || lastToken.endsWith("!") || lastToken.endsWith("?") -> 30L
                else -> 18L
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
            append("Pergunta do usuário (${if (wantsDetails) "detalhada" else "conversacional"}): $query")
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
                "O modelo **${getLoadedModelName()}** está ativo na memória ($loadedBackendLabel) e pronto para conversar sobre qualquer assunto!"
            } else {
                "Para conversar com geração neural livre idêntica ao Google AI Edge Gallery, toque em **\"Selecionar .task\"** no topo e escolha seu arquivo `gemma-4-E2B-it.task`."
            }
            return "Olá! Tudo ótimo por aqui. Posso ajudar você tanto com os protocolos de sobrevivência e dados do seu estoque quanto com qualquer outro assunto.\n\n$modelHint"
        }

        if (isQueryAboutUserInventoryOrFamily(query)) {
            return buildUserAppContextSummary()
        }

        if (ragChunks.isNotEmpty()) {
            return naturalOfficialSynthesis(ragChunks).first
        }

        return OfflineEdgeKnowledgeEngine.generateOfflineResponse(
            query = query,
            detailed = true,
            recentHistory = recentHistory,
            modelErrorDetail = modelErrorDetail
        )
    }

    private fun naturalOfficialSynthesis(
        sources: List<ManualChunkFtsEntity>
    ): Pair<String, String> {
        val primary = sources.first()
        val agencyName = primary.docTitle.substringBefore(":").trim()
        val topicTitle = primary.docTitle.substringAfter(":", primary.docTitle).trim()

        val sb = StringBuilder()
        sb.append("De acordo com o protocolo oficial da **$agencyName** sobre **$topicTitle**:\n\n")

        sources.forEachIndexed { idx, chunk ->
            if (idx > 0) {
                sb.append("\n📌 **Complemento (${chunk.docTitle}):**\n")
            }
            val formatted = chunk.contentText
                .replace(Regex("^DIRETRIZ OFICIAL\\s*\\([^)]*\\):\\s*"), "")
                .replace(" 1) ", "\n\n1. ")
                .replace(" 2) ", "\n\n2. ")
                .replace(" 3) ", "\n\n3. ")
                .replace(" 4) ", "\n\n4. ")
                .replace(" • ", "\n• ")
                .trim()
            sb.append(formatted).append("\n")
        }

        return Pair(sb.toString().trim(), "RAG OFICIAL • $agencyName")
    }
}
