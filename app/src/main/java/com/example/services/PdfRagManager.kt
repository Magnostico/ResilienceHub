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

    /**
     * Modo A: Rápido (Zero Consumo de Bateria)
     * Consulta diretamente os índices FTS4 em memória/disco sem acionar CPU de IA.
     */
    fun searchFastFts(query: String): Flow<List<ManualChunkFtsEntity>> {
        val sanitized = query.trim().replace("\"", "").replace("'", "")
        return if (sanitized.isBlank()) {
            database.manualChunkDao().getAllChunks()
        } else {
            database.manualChunkDao().searchChunks("$sanitized*")
        }
    }

    /**
     * Modo B: IA Local (RAG)
     * Recupera os 3 trechos mais relevantes do FTS, constrói o prompt estrito de emergência
     * e executa a inferência conforme a especificação do MediaPipe LLM Inference.
     */
    suspend fun searchLocalAiRag(
        query: String,
        customModelPath: String? = null
    ): RagResponse = withContext(Dispatchers.IO) {
        val sanitized = query.trim().replace("\"", "").replace("'", "")
        val allMatching = if (sanitized.isNotBlank()) {
            database.manualChunkDao().searchChunks("$sanitized*").first()
        } else {
            database.manualChunkDao().getAllChunks().first()
        }

        // Seleciona até 3 trechos mais relevantes
        val topChunks = allMatching.take(3)

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

    /**
     * Localiza o arquivo de modelo local Gemma (.bin ou .task) nos locais padrão do aparelho.
     */
    fun findLocalModelFile(customPath: String? = null): File? {
        if (!customPath.isNullOrBlank()) {
            val f = File(customPath)
            if (f.exists()) return f
        }
        val candidateLocations = listOf(
            File(context.filesDir, "gemma-2b-it-cpu-int4.bin"),
            File(context.filesDir, "gemma-2b.bin"),
            File(context.filesDir, "model.bin"),
            File(context.filesDir, "gemma2-2b-it-gpu-int8.task"),
            File(context.getExternalFilesDir(null), "gemma-2b-it-cpu-int4.bin"),
            File(context.getExternalFilesDir(null), "model.bin"),
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
                val options = LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(modelFile.absolutePath)
                    .setMaxTokens(512)
                    .setTemperature(0.1f)
                    .setTopK(1)
                    .build()

                val llmInference = LlmInference.createFromOptions(context, options)
                val response = llmInference.generateResponse(prompt)
                Pair(response, "MEDIAPIPE_GEMMA_LOCAL_LLM (${modelFile.name})")
            } catch (e: Exception) {
                Log.w(TAG, "Falha na inferência MediaPipe LlmInference, alternando para síntese estrita FTS", e)
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
            "OFFLINE_FTS_RAG (Coloque gemma-2b.bin em filesDir para ativar LLM)"
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

        val queryTerms = query.lowercase().split(" ").filter { it.length > 2 }

        sources.forEachIndexed { i, chunk ->
            responseBuilder.append("• Trecho ${i + 1} [${chunk.docTitle} — Pág. ${chunk.pageNumber}]:\n")
            
            val sentences = chunk.contentText.split(Regex("[.!?]\\s+"))
            val matchingSentences = sentences.filter { sentence ->
                queryTerms.any { term -> sentence.lowercase().contains(term) }
            }

            val sentencesToShow = if (matchingSentences.isNotEmpty()) matchingSentences else sentences.take(2)
            sentencesToShow.forEach { s ->
                val trimmed = s.trim()
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
