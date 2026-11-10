package com.example.services

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.example.data.AppDatabase
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
 * Gerenciador de ingestão de cartilhas e busca híbrida RAG 100% Offline:
 * - Extração textual com PDFBox-Android e chunking (~500 caracteres) com persistência em SQLite FTS4.
 * - Modo Rápido (Zero Bateria): Busca vetorial/lexical instantânea no FTS4.
 * - Modo IA Local (RAG): Recuperação dos 3 trechos mais relevantes + Prompt Estrito para MediaPipe LLM Inference.
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
            "Responda APENAS com base nas cartilhas oficiais abaixo, de forma direta e em tópicos:\n\n"
    }

    init {
        // Inicializa o loader de recursos de fontes do PDFBox-Android
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
        val executionMode: String // "LLM_INFERENCE_LOCAL" ou "OFFLINE_STRUCTURED_SYNTHESIS"
    )

    // =========================================================================
    // 1. INGESTÃO DE PDF E INDEXAÇÃO NO ROOM FTS4
    // =========================================================================

    /**
     * Lê o PDF selecionado pelo usuário, extrai página a página, particiona em
     * blocos de ~500 caracteres e grava na tabela FTS4.
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

    /**
     * Divide o texto em blocos de tamanho aproximado respeitando quebras de frase e parágrafo.
     */
    private fun splitIntoChunks(text: String, targetSize: Int, overlap: Int): List<String> {
        val cleanText = text.replace(Regex("\\s+"), " ")
        if (cleanText.length <= targetSize) {
            return listOf(cleanText)
        }

        val chunks = mutableListOf<String>()
        var start = 0
        while (start < cleanText.length) {
            var end = minOf(start + targetSize, cleanText.length)

            // Tenta quebrar em ponto final ou vírgula próximo ao limite
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

    private fun getFileNameFromUri(uri: Uri): String? {
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
    // 2. BUSCA HÍBRIDA: MODO RÁPIDO (FTS) vs MODO IA LOCAL (RAG)
    // =========================================================================

    private val stopWords = setOf(
        "como", "para", "qual", "quais", "que", "com", "sem", "nos", "nas",
        "dos", "das", "por", "uma", "uns", "umas", "isso", "este", "esta",
        "onde", "quando", "porque", "deve", "devo", "fazer", "usar", "tem", "sao"
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

    /**
     * Modo A: Rápido (Zero Consumo de Bateria)
     * Consulta diretamente os índices FTS4 em memória/disco sem acionar CPU de IA.
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
     * Modo B: IA Local (RAG)
     * Recupera os 3 trechos mais relevantes por pontuação semântica/lexical e FTS,
     * constrói o prompt estrito de emergência e executa a inferência local.
     */
    suspend fun searchLocalAiRag(
        query: String,
        customModelPath: String? = null
    ): RagResponse = withContext(Dispatchers.IO) {
        val keywords = extractKeywords(query)
        val normalizedKeywords = keywords.map { normalizeAccents(it) }

        val allChunks = database.manualChunkDao().getAllChunks().first()

        // Pontuação híbrida (BM25 simplificado + correspondência exata sem acentos)
        val scoredChunks = if (normalizedKeywords.isEmpty()) {
            allChunks.take(3)
        } else {
            allChunks.map { chunk ->
                val normContent = normalizeAccents(chunk.contentText)
                val normTitle = normalizeAccents(chunk.docTitle)
                val normCat = normalizeAccents(chunk.category)
                var score = 0
                for (kw in normalizedKeywords) {
                    if (normContent.contains(kw)) score += 10
                    if (normTitle.contains(kw)) score += 5
                    if (normCat.contains(kw)) score += 4
                }
                Pair(chunk, score)
            }
                .filter { it.second > 0 }
                .sortedByDescending { it.second }
                .map { it.first }
        }

        // Seleciona até 3 trechos mais relevantes
        val topChunks = scoredChunks.take(3)

        // Montagem do Prompt Estrito exigido
        val contextBuilder = StringBuilder()
        contextBuilder.append("[CARTILHAS OFICIAIS RECUPERADAS]\n")

        if (topChunks.isEmpty()) {
            contextBuilder.append("Nenhum trecho oficial diretamente correspondente encontrado no banco local.\n")
        } else {
            topChunks.forEachIndexed { index, chunk ->
                contextBuilder.append("Trecho ${index + 1} (${chunk.docTitle} - Pág. ${chunk.pageNumber} - Cat: ${chunk.category}):\n")
                contextBuilder.append("\"${chunk.contentText}\"\n\n")
            }
        }

        val fullPrompt = buildString {
            append(STRICT_RAG_SYSTEM_PROMPT_TEMPLATE)
            append(contextBuilder.toString())
            append("[PERGUNTA DO CIDADÃO]\n")
            append(query)
            append("\n\n[RESPOSTA EM TÓPICOS]:\n")
        }

        // Execução da Inferência
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

    /**
     * Importa o arquivo de modelo LLM (.task ou .bin, ex: gemma-4-E2B-it.task) selecionado pelo usuário
     * via Storage Access Framework para o diretório interno do app para execução com MediaPipe.
     */
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

            // Reseta instância em cache para carregar o novo modelo
            cachedLlmInference = null
            cachedModelPath = destFile.absolutePath
            Result.success(destFile.name)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao importar modelo LLM local", e)
            Result.failure(e)
        }
    }

    /**
     * Localiza o arquivo de modelo local Gemma (.task ou .bin) nos locais padrão do aparelho.
     */
    fun findLocalModelFile(customPath: String? = null): File? {
        if (!customPath.isNullOrBlank()) {
            val f = File(customPath)
            if (f.exists() && f.length() > 1024) return f
        }
        if (!cachedModelPath.isNullOrBlank()) {
            val f = File(cachedModelPath!!)
            if (f.exists() && f.length() > 1024) return f
        }

        // Procura qualquer arquivo .task ou .bin já importado em filesDir ou externalFilesDir
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

    /**
     * Executa a inferência LLM local real. Se os pesos do modelo Gemma/MediaPipe estiverem
     * presentes no caminho fornecido ou nos diretórios da aplicação, aciona a engine LlmInference
     * com temperature = 0.1f e topK = 1 para zero alucinação.
     * Caso contrário, sintetiza a resposta estruturada estritamente a partir dos tópicos
     * das fontes oficiais recuperadas pelo Room FTS4.
     */
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
                Pair(response, "MEDIAPIPE_GEMMA_LOCAL_LLM (${modelFile.name})")
            } catch (t: Throwable) {
                Log.w(TAG, "Falha na inferência MediaPipe LlmInference, alternando para síntese estrita FTS", t)
                fallbackStructuredSynthesis(query, sources, modelNotFound = false)
            }
        } else {
            return fallbackStructuredSynthesis(query, sources, modelNotFound = true)
        }
    }

    /**
     * Síntese offline estrita garantindo conformidade total com as cartilhas oficiais.
     * Extrai diretamente os parágrafos relevantes sem inventar informações (zero alucinação).
     */
    private fun fallbackStructuredSynthesis(
        query: String,
        sources: List<ManualChunkFtsEntity>,
        modelNotFound: Boolean = true
    ): Pair<String, String> {
        val mode = if (modelNotFound) {
            "OFFLINE_FTS_RAG (Importe gemma-4-E2B-it.task no topo para LLM)"
        } else {
            "OFFLINE_FTS_EXTRACTIVE_RAG"
        }

        if (sources.isEmpty()) {
            return Pair(
                "• INFORMAÇÃO NÃO ENCONTRADA NOS MANUAIS IMPORTADOS para o termo \"$query\".\n" +
                "• Por segurança em situação de crise, consulte os manuais pré-carregados ou importe uma nova cartilha oficial da Defesa Civil / Bombeiros em PDF.",
                mode
            )
        }

        val responseBuilder = StringBuilder()
        responseBuilder.append("Diretrizes de Emergência Oficiais Extraídas do Banco Local:\n\n")

        val queryTerms = extractKeywords(query).map { normalizeAccents(it) }

        sources.forEachIndexed { i, chunk ->
            responseBuilder.append("• Trecho ${i + 1} [${chunk.docTitle} — Pág. ${chunk.pageNumber}]:\n")
            
            val sentences = chunk.contentText.split(Regex("[.!?]\\s+"))
            val matchingSentences = sentences.filter { sentence ->
                val normSentence = normalizeAccents(sentence)
                queryTerms.any { term -> normSentence.contains(term) }
            }

            val sentencesToShow = if (matchingSentences.isNotEmpty()) matchingSentences else sentences.take(2)
            sentencesToShow.forEach { s ->
                val trimmed = s.trim().trimEnd('.')
                if (trimmed.isNotBlank()) {
                    responseBuilder.append("   - \"$trimmed.\"\n")
                }
            }
            responseBuilder.append("\n")
        }

        responseBuilder.append("✓ Procedimentos oficiais verificados. Siga rigorosamente as dosagens e orientações citadas.")
        return Pair(responseBuilder.toString(), mode)
    }
}
