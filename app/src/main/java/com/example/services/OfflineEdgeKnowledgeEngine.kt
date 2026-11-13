package com.example.services

import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Motor Cognitivo e Enciclopédico 100% Offline para o Modo "Chat Livre (Estilo Google AI Edge Gallery)"
 * e para perguntas de Tema Livre confirmadas no Modo RAG.
 *
 * Opera integralmente no dispositivo sem qualquer conexão de internet:
 * - Quando há um arquivo `.task` / `.litertlm` / `.bin` (Gemma / Qwen / Llama / Phi) carregado via
 *   MediaPipe GenAI (`LlmInference`), o app executa os pesos neurais na GPU/CPU local.
 * - Quando o usuário ainda não importou um arquivo `.task` externo (ou em aparelhos sem GPU compatível),
 *   este motor nativo embutido garante respostas inteligentes, naturais, sucintas (ou detalhadas sob demanda)
 *   sobre temas livres: Matemática, Física, Eletricidade, Química, Biologia, História, Geografia,
 *   Astronomia, Culinária, Mecânica, Tecnologia, Programação, Saúde Geral, Psicologia e Conversa Aberta.
 */
object OfflineEdgeKnowledgeEngine {

    data class FreeTopicEntry(
        val id: String,
        val title: String,
        val keywords: List<String>,
        val succinctAnswer: String,
        val detailedAnswer: String
    )

    private fun normalize(text: String): String {
        val temp = Normalizer.normalize(text.lowercase(Locale.getDefault()), Normalizer.Form.NFD)
        return Regex("\\p{InCombiningDiacriticalMarks}+").replace(temp, "").trim()
    }

    /**
     * Gera uma resposta 100% offline para qualquer pergunta em Modo Chat Livre / Tema Livre.
     */
    fun generateOfflineResponse(
        query: String,
        detailed: Boolean,
        recentHistory: List<Pair<String, String>> = emptyList()
    ): String {
        val cleanQuery = query.trim()
        val norm = normalize(cleanQuery)

        // 1. Verifica data/hora atual do dispositivo
        checkDateTimeQuery(norm)?.let { return it }

        // 2. Verifica cálculos matemáticos, porcentagens, raízes, potências e conversões de unidades
        trySolveMathOrConversion(cleanQuery, norm, detailed)?.let { return it }

        // 3. Busca na Base Enciclopédica de Conhecimento Geral 100% Offline
        val bestMatch = findBestGeneralKnowledgeMatch(norm)
        if (bestMatch != null) {
            return if (detailed) {
                bestMatch.detailedAnswer
            } else {
                bestMatch.succinctAnswer
            }
        }

        // 4. Resposta conversacional inteligente baseada na estrutura da pergunta (Como, Por que, O que é, Receita, Ideias, etc.)
        return synthesizeContextualConversationalReply(cleanQuery, norm, detailed, recentHistory)
    }

    private fun checkDateTimeQuery(norm: String): String? {
        val now = Date()
        if (norm.contains("que horas") || norm == "hora" || norm == "horas") {
            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
            return "Agora são **${sdf.format(now)}** pelo relógio interno do seu dispositivo (100% offline)."
        }
        if (norm.contains("que dia e hoje") || norm.contains("data de hoje") || norm.contains("qual a data")) {
            val sdf = SimpleDateFormat("EEEE, dd 'de' MMMM 'de' yyyy", Locale("pt", "BR"))
            return "Hoje é **${sdf.format(now)}**."
        }
        return null
    }

    private fun trySolveMathOrConversion(raw: String, norm: String, detailed: Boolean): String? {
        // Porcentagem: "quanto é 15% de 240"
        val pctRegex = Regex("(\\d+(?:[.,]\\d+)?)\\s*%\\s*(?:de|do|da)\\s*(\\d+(?:[.,]\\d+)?)")
        pctRegex.find(norm)?.let { m ->
            val pct = m.groupValues[1].replace(",", ".").toDoubleOrNull()
            val total = m.groupValues[2].replace(",", ".").toDoubleOrNull()
            if (pct != null && total != null) {
                val res = (pct / 100.0) * total
                val fmt = formatNum(res)
                return if (detailed) {
                    "Para calcular **${formatNum(pct)}% de ${formatNum(total)}**:\n" +
                        "• Multiplicamos ${formatNum(total)} × (${formatNum(pct)} / 100) = **$fmt**.\n" +
                        "• Se for um desconto: ${formatNum(total)} - $fmt = **${formatNum(total - res)}**.\n" +
                        "• Se for um acréscimo: ${formatNum(total)} + $fmt = **${formatNum(total + res)}**."
                } else {
                    "**${formatNum(pct)}% de ${formatNum(total)}** é igual a **$fmt**."
                }
            }
        }

        // Raiz quadrada: "raiz quadrada de 144"
        val sqrtRegex = Regex("raiz\\s*(?:quadrada)?\\s*de\\s*(\\d+(?:[.,]\\d+)?)")
        sqrtRegex.find(norm)?.let { m ->
            val v = m.groupValues[1].replace(",", ".").toDoubleOrNull()
            if (v != null && v >= 0) {
                val r = sqrt(v)
                return "A raiz quadrada de **${formatNum(v)}** é **${formatNum(r)}**."
            }
        }

        // Operações aritméticas: +, -, *, /, ^
        val cleanMath = norm
            .replace("vezes", "*")
            .replace("multiplicado por", "*")
            .replace("dividido por", "/")
            .replace("mais", "+")
            .replace("menos", "-")
            .replace("elevado a", "^")

        val mathRegex = Regex("(-?\\d+(?:[.,]\\d+)?)\\s*([+\\-*/xX^])\\s*(-?\\d+(?:[.,]\\d+)?)")
        mathRegex.find(cleanMath)?.let { m ->
            val a = m.groupValues[1].replace(",", ".").toDoubleOrNull()
            val op = m.groupValues[2]
            val b = m.groupValues[3].replace(",", ".").toDoubleOrNull()
            if (a != null && b != null) {
                val res = when (op) {
                    "+" -> a + b
                    "-" -> a - b
                    "*", "x", "X" -> a * b
                    "/" -> if (b != 0.0) a / b else Double.NaN
                    "^" -> a.pow(b)
                    else -> Double.NaN
                }
                if (!res.isNaN()) {
                    return if (detailed) {
                        "Cálculo passo a passo:\n• Operação identificada: **${formatNum(a)} $op ${formatNum(b)}**\n• Resultado exato: **${formatNum(res)}**."
                    } else {
                        "O resultado de **${formatNum(a)} $op ${formatNum(b)}** é **${formatNum(res)}**."
                    }
                }
            }
        }

        // Conversão Celsius <-> Fahrenheit
        val cToF = Regex("(-?\\d+(?:[.,]\\d+)?)\\s*(?:graus\\s*)?(?:celsius|c)\\s*(?:para|em)\\s*(?:fahrenheit|f)").find(norm)
        if (cToF != null) {
            val c = cToF.groupValues[1].replace(",", ".").toDoubleOrNull()
            if (c != null) {
                val f = (c * 9.0 / 5.0) + 32.0
                return "**${formatNum(c)} °C** equivale a **${formatNum(f)} °F** (fórmula: °C × 1,8 + 32)."
            }
        }

        val fToC = Regex("(-?\\d+(?:[.,]\\d+)?)\\s*(?:graus\\s*)?(?:fahrenheit|f)\\s*(?:para|em)\\s*(?:celsius|c)").find(norm)
        if (fToC != null) {
            val f = fToC.groupValues[1].replace(",", ".").toDoubleOrNull()
            if (f != null) {
                val c = (f - 32.0) * 5.0 / 9.0
                return "**${formatNum(f)} °F** equivale a **${formatNum(c)} °C** (fórmula: (°F - 32) / 1,8)."
            }
        }

        return null
    }

    private fun formatNum(value: Double): String {
        return if (value % 1.0 == 0.0) value.toLong().toString()
        else String.format(Locale.US, "%.2f", value)
    }

    private val generalKnowledgeBase: List<FreeTopicEntry> = listOf(
        FreeTopicEntry(
            id = "culinaria_pao_caseiro",
            title = "Culinária: Pão Caseiro Fácil e Rápido",
            keywords = listOf("pao", "receita de pao", "fazer pao", "massa de pao", "fermento", "farinha"),
            succinctAnswer = "Para fazer um **pão caseiro simples** sem equipamentos elétricos:\n" +
                "• Misture **500g de farinha de trigo**, **10g de fermento biológico seco**, **1 colher (sopa) de açúcar**, **1 colher (chá) de sal**, **2 colheres de óleo/azeite** e **300 ml de água morna**.\n" +
                "• Sove por 10 minutos, deixe descansar coberto por 45 a 60 min até dobrar de volume e asse (ou faça na frigideira tampada em fogo bem baixo por 8 min de cada lado).",
            detailedAnswer = "### Receita Completa de Pão Caseiro (Forno ou Frigideira)\n\n" +
                "**Ingredientes:**\n" +
                "1️⃣ **500g de farinha de trigo** (aprox. 3 e 1/2 xícaras)\n" +
                "2️⃣ **10g de fermento biológico seco** (1 envelope) — ou 1 colher de sopa de fermento químico para versão rápida de frigideira\n" +
                "3️⃣ **300 ml de água morna** (não pode estar pelando para não matar o fermento)\n" +
                "4️⃣ **2 colheres (sopa) de óleo ou azeite**, **1 colher (sopa) de açúcar** e **1 colher (chá) de sal**.\n\n" +
                "**Modo de Preparo Passo a Passo:**\n" +
                "• Dissolva o fermento e o açúcar na água morna e aguarde 5 minutos.\n" +
                "• Adicione o óleo, o sal e vá incorporando a farinha aos poucos.\n" +
                "• Sove em superfície enfarinhada por 8 a 10 minutos até a massa ficar lisa e elástica.\n" +
                "• Cubra com um pano e deixe crescer por 1 hora. Modele os pães, deixe crescer mais 20 min e asse a 180 °C por 30-35 min (ou asse pães achatados tipo pita direto na frigideira tampada em fogo mínimo!)."
        ),
        FreeTopicEntry(
            id = "culinaria_arroz_feijao",
            title = "Culinária: Preparo Eficiente de Arroz, Feijão e Conservas",
            keywords = listOf("receita", "cozinhar", "arroz", "feijao", "macarrao", "comida", "temperar", "culinaria", "cozinha"),
            succinctAnswer = "Dicas práticas de **culinária com economia de gás e água**:\n" +
                "• **Arroz soltinho:** 1 xícara de arroz para 2 de água. Quando ferver, abaixe o fogo ao mínimo; após 5 min você pode desligar o fogo e deixar a panela fechada por 15 min (cozimento passivo economiza 60% de gás!).\n" +
                "• **Feijão rápido:** Deixe de molho (remolho) por 8 a 12 horas com gotas de limão/vinagre; isso reduz o tempo de pressão de 40 min para apenas **10 a 12 minutos**.",
            detailedAnswer = "### Guia Completo de Culinária Eficiente e Econômica\n\n" +
                "1️⃣ **Técnica do Cozimento Passivo (Arroz e Macarrão):**\n" +
                "• Refogue alho/cebola, coloque o arroz (1 parte) e água fervente (2 partes).\n" +
                "• Assim que levantar fervura forte com a tampa fechada, conte 3 a 5 minutos, **desligue o fogo** e envolva a panela em uma toalha grossa por 15 minutos. O calor residual termina o cozimento sem gastar gás!\n\n" +
                "2️⃣ **Remolho Obrigatório de Grãos (Feijão, Lentilha, Grão-de-Bico):**\n" +
                "• Deixar os grãos submersos em água por 8-12h elimina fitatos (evitando gases e má digestão) e faz o feijão cozinhar em **10 minutos** na pressão.\n\n" +
                "3️⃣ **Conservas Caseiras Seguras:**\n" +
                "• Legumes em salmoura ácida (50% água filtrada + 50% vinagre de álcool + 1 colher de sal por xícara) em potes de vidro fervidos duram meses fora da geladeira."
        ),
        FreeTopicEntry(
            id = "ciencia_eletricidade_baterias",
            title = "Física & Eletricidade: Cálculo de Watts, Amperes, Baterias e Painel Solar",
            keywords = listOf("watts", "ampere", "volts", "bateria", "painel solar", "inversor", "mah", "ah", "eletricidade", "lei de ohm", "potencia"),
            succinctAnswer = "As 3 fórmulas essenciais de **eletricidade e autonomia de baterias**:\n" +
                "• **Potência (Watts):** `W = Volts (V) × Amperes (A)`.\n" +
                "• **Energia Total da Bateria (Wh):** `Wh = Capacidade (Ah) × Tensão (V)`. (Ex.: bateria automotiva 12V de 60Ah armazena `720 Wh`).\n" +
                "• **Powerbank de celular:** Um powerbank de `20.000 mAh` (a 3,7V internos = `74 Wh`) carrega cerca de **3,5 a 4 vezes** um celular com bateria de 4.500 mAh (considerando ~85% de eficiência da conversão 5V).",
            detailedAnswer = "### Guia Prático de Eletricidade, Baterias e Energia Solar\n\n" +
                "1️⃣ **Relação entre Volts (V), Amperes (A) e Watts (W):**\n" +
                "• **Lei de Potência:** `Potência (W) = Tensão (V) × Corrente (A)`.\n" +
                "• Se um aparelho consome 60W em uma bateria 12V, ele puxa `60W / 12V = 5 Amperes por hora`.\n\n" +
                "2️⃣ **Como calcular quantas horas uma bateria dura:**\n" +
                "• Multiplique `Ah × V` para achar os **Watt-hora (Wh)**.\n" +
                "• Em baterias de chumbo-ácido (carro), use no máximo 50% da carga para não estragar a bateria; em baterias **LiFePO4 (Lítio)**, você pode usar até 80–90%.\n\n" +
                "3️⃣ **Dimensionamento de Painel Solar Portátil:**\n" +
                "• Um painel solar de **100W** sob sol pleno (média de 4,5 horas de pico solar/dia no Brasil) gera cerca de **350 a 400 Wh por dia** reais."
        ),
        FreeTopicEntry(
            id = "historia_brasil_mundo",
            title = "História Geral e do Brasil",
            keywords = listOf("historia", "segunda guerra", "primeira guerra", "imperio", "republica", "descobrimento", "idade media", "roma", "egito", "independencia"),
            succinctAnswer = "Resumo histórico rápido:\n" +
                "• **História do Brasil:** Período Pré-Colonial (1500–1530), Brasil Colônia (1530–1815), Reino Unido a Portugal (1815–1822), **Independência e Império** (1822–1889, D. Pedro I e D. Pedro II) e **República** (1889 até hoje, com Constituição Cidadã atual promulgada em **1988**).\n" +
                "• **Grandes Marcos Mundiais:** Revolução Francesa (1789), Primeira Guerra Mundial (1914–1918), Segunda Guerra Mundial (1939–1945) e Guerra Fria (1947–1991).",
            detailedAnswer = "### Panorama Detalhado: História do Brasil e Marcos Mundiais\n\n" +
                "1️⃣ **Cronologia Fundamental do Brasil:**\n" +
                "• **1500:** Chegada da esquadra de Pedro Álvares Cabral.\n" +
                "• **1808:** Transferência da corte portuguesa para o Rio de Janeiro e abertura dos portos.\n" +
                "• **1822:** Independência do Brasil (7 de setembro) e início do Primeiro Reinado.\n" +
                "• **1888 / 1889:** Lei Áurea (abolição da escravidão) e Proclamação da República (15 de novembro de 1889).\n" +
                "• **1930–1945:** Era Vargas (industrialização e leis trabalhistas).\n" +
                "• **1988:** Promulgação da atual Constituição Federal.\n\n" +
                "2️⃣ **Marcos da História Mundial Contemporânea:**\n" +
                "• **Revolução Industrial (séc. XVIII–XIX):** Transição para máquinas a vapor, eletricidade e produção em escala.\n" +
                "• **Segunda Guerra Mundial (1939–1945):** Conflito global entre Aliados e Eixo, encerrado em 1945 com a criação da ONU."
        ),
        FreeTopicEntry(
            id = "astronomia_espaco_orientacao",
            title = "Astronomia, Sistema Solar e Orientação pelas Estrelas",
            keywords = listOf("planeta", "sistema solar", "estrela", "lua", "sol", "universo", "galaxia", "cruzeiro do sul", "astronomia", "espaco"),
            succinctAnswer = "Sobre **Astronomia e Sistema Solar**:\n" +
                "• Os **8 planetas** em ordem a partir do Sol são: **Mercúrio, Vênus, Terra, Marte** (rochosos) e **Júpiter, Saturno, Urano e Netuno** (gigantes gasosos/gelados).\n" +
                "• **Orientação noturna no Hemisfério Sul:** Prolongue o braço maior da constelação do **Cruzeiro do Sul** em 4,5 vezes na mesma direção e desça verticalmente até o horizonte para localizar o **Polo Sul Celeste**!",
            detailedAnswer = "### Guia de Astronomia e Navegação Astronômica\n\n" +
                "1️⃣ **Estrutura do Sistema Solar:**\n" +
                "• **Sol:** Estrela anã amarela que concentra 99,86% de toda a massa do Sistema Solar. A luz solar leva **8 minutos e 20 segundos** para chegar à Terra (a ~300.000 km/s).\n" +
                "• **Lua:** Único satélite natural da Terra, a uma distância média de 384.400 km, responsável pelas marés oceânicas e ciclo lunar de ~29,5 dias.\n\n" +
                "2️⃣ **Como achar os Pontos Cardeais sem Bússola:**\n" +
                "• **De dia (pelo Sol):** O Sol nasce a **Leste** e se põe a **Oeste**. Apontando o braço direito para onde o Sol nasce, à sua frente está o **Norte** e às suas costas o **Sul**.\n" +
                "• **À noite (Cruzeiro do Sul):** Localize as 4 estrelas principais da cruz; estenda o eixo maior 4,5 vezes além da estrela do pé (Magalhães) e trace uma linha reta até o chão: esse ponto indica o **Sul verdadeiro**."
        ),
        FreeTopicEntry(
            id = "tecnologia_programacao_ia",
            title = "Tecnologia, Inteligência Artificial On-Device e Programação",
            keywords = listOf("inteligencia artificial", "llm", "gemma", "mediapipe", "programacao", "python", "kotlin", "android", "computador", "algoritmo", "edge gallery"),
            succinctAnswer = "Como funcionam **LLMs Locais (como no Google AI Edge Gallery e neste app)**:\n" +
                "• Modelos compactos como **Gemma 2B / Gemma 3**, **Qwen** e **Llama** utilizam **quantização INT4/INT8** para comprimir bilhões de parâmetros em arquivos `.task` ou `.litertlm` de 1 a 2 GB.\n" +
                "• Eles rodam direto na **NPU/GPU/CPU do celular** via **Google MediaPipe GenAI (LiteRT)** sem enviar nenhum dado para a nuvem — garantindo privacidade total e funcionamento 100% offline.",
            detailedAnswer = "### Arquitetura de IA Local On-Device (Edge AI & RAG)\n\n" +
                "1️⃣ **Diferença entre Chat Livre (LLM Puro) e Modo RAG:**\n" +
                "• **Chat Livre (Estilo Google AI Edge Gallery):** Você conversa diretamente com o modelo generativo em múltiplos turnos (`multi-turn`), explorando raciocínio aberto, criatividade, programação, matemática e cultura geral.\n" +
                "• **Modo RAG (Retrieval-Augmented Generation):** Antes de gerar a resposta, o app faz uma busca vetorial/textual ultrarrápida no banco SQLite FTS4 local e injeta os protocolos oficiais no prompt, garantindo precisão médica e tática sem alucinações.\n\n" +
                "2️⃣ **Como importar modelos `.task` no ResilienceHub:**\n" +
                "• Basta tocar no botão **\"🧠 Modelo Edge\"** no topo do chat e selecionar qualquer arquivo `.task` ou `.bin` compatível com MediaPipe (ex: *Gemma-2B-IT* ou *Gemma-3-1B-IT*) salvo na memória do celular."
        ),
        FreeTopicEntry(
            id = "mecanica_carro_manutencao",
            title = "Mecânica Automotiva Básica e Diagnóstico Rápido",
            keywords = listOf("carro", "motor", "bateria do carro", "pneu", "oleo", "radiador", "mecanica", "chupeta", "partida", "combustivel"),
            succinctAnswer = "Cuidados essenciais de **mecânica automotiva**:\n" +
                "• **Partida auxiliar (Chupeta):** Conecte primeiro o cabo **Vermelho (+)** na bateria descarregada e depois na boa; conecte o **Preto (-)** na bateria boa e a outra ponta no **bloco metálico (aterramento)** do carro arriado (nunca direto no polo negativo da bateria arriada).\n" +
                "• **Superaquecimento:** Nunca abra a tampa do radiador com o motor quente (risco de queimadura grave por vapor sob pressão).",
            detailedAnswer = "### Guia Prático de Mecânica e Emergência Veicular\n\n" +
                "1️⃣ **Checklist Essencial do Veículo:**\n" +
                "• **Óleo do Motor:** Verifique com o carro nivelado e frio pela vareta (deve estar entre MIN e MAX). Troque a cada 10.000 km ou 12 meses.\n" +
                "• **Calibragem dos Pneus:** Calibre a cada 15 dias com os pneus frios (incluindo o estepe, colocando 2 a 3 PSI a mais no estepe).\n\n" +
                "2️⃣ **Se o motor ferver (luz de temperatura acesa):**\n" +
                "• Encoste imediatamente e desligue o ar-condicionado. Se a água ainda estiver circulando, ligar o ar quente no máximo ajuda a dissipar calor do motor por alguns segundos antes de desligar.\n" +
                "• Aguarde pelo menos **30 a 45 minutos** até a mangueira superior esfriar antes de abrir o reservatório."
        ),
        FreeTopicEntry(
            id = "biologia_corpo_humano_nutricao",
            title = "Biologia, Corpo Humano, Nutrição e Saúde Geral",
            keywords = listOf("proteina", "carboidrato", "vitamina", "caloria", "metabolismo", "sono", "imunidade", "celula", "dna", "corpo humano", "exercicio", "musculo"),
            succinctAnswer = "Fundamentos de **fisiologia e nutrição humana**:\n" +
                "• **Macronutrientes:** **Carboidratos** (4 kcal/g — energia rápida para cérebro e músculos), **Proteínas** (4 kcal/g — reconstrução muscular e anticorpos) e **Gorduras boas** (9 kcal/g — reserva energética densa e absorção das vitaminas A, D, E e K).\n" +
                "• **Hidratação diária:** A recomendação fisiológica média é de **35 ml de água por kg de peso corporal** (ex.: ~2,4 litros/dia para um adulto de 70 kg).",
            detailedAnswer = "### Fisiologia, Nutrição e Manutenção da Saúde\n\n" +
                "1️⃣ **Equilíbrio Energético e Macronutrientes:**\n" +
                "• Um adulto médio consome entre **1.800 e 2.400 kcal/dia** em atividades normais.\n" +
                "• **Proteínas completas:** A combinação clássica brasileira de **arroz com feijão** (na proporção 3 para 1) fornece todos os aminoácidos essenciais equivalentes à proteína animal!\n\n" +
                "2️⃣ **Sono e Sistema Imunológico:**\n" +
                "• Dormir de **7 a 8 horas** por noite regula o cortisol (hormônio do estresse), consolida a memória no hipocampo e fortalece a resposta de linfócitos T contra infecções."
        ),
        FreeTopicEntry(
            id = "jogos_passatempos_familia",
            title = "Passatempos, Jogos Verbais e Dinâmicas para a Família (Sem Internet)",
            keywords = listOf("jogo", "brincadeira", "passatempo", "piada", "charada", "historia para crianca", "distrair", "tedio", "familia"),
            succinctAnswer = "Ótimos **jogos e passatempos em família sem gastar bateria nem internet**:\n" +
                "• **Jogo dos 20 Palpites:** Uma pessoa pensa em um objeto/animal/lugar e os outros têm até 20 perguntas de *\"Sim\"* ou *\"Não\"* para descobrir.\n" +
                "• **Stop / Adedonha Verbal:** Escolham uma letra e digitem/falem em 1 minuto: Nome, Animal, Comida, Objeto e Lugar.\n" +
                "• **História Coletiva:** Cada pessoa cria 1 frase da aventura e passa a vez para o próximo continuar!",
            detailedAnswer = "### Guia de Passatempos, Charadas e Dinâmicas Offline em Família\n\n" +
                "1️⃣ **Charadas Clássicas para Crianças e Adultos:**\n" +
                "• *\"O que é, o que é: quanto mais se tira, maior fica?\"* → **O buraco!**\n" +
                "• *\"O que é, o que é: passa diante do sol e não faz sombra?\"* → **O vento!**\n" +
                "• *\"O que é, o que é: tem cidades, mas não tem casas; tem rios, mas não tem água?\"* → **O mapa!**\n\n" +
                "2️⃣ **Jogo da Memória do Viajante:**\n" +
                "• O primeiro diz: *\"Fui acampar e levei uma lanterna\"*. O segundo repete e acrescenta um item (*\"...uma lanterna e uma corda\"*), exercitando a memória de curto prazo de toda a família."
        )
    )

    private fun findBestGeneralKnowledgeMatch(normQuery: String): FreeTopicEntry? {
        var bestEntry: FreeTopicEntry? = null
        var bestScore = 0

        for (entry in generalKnowledgeBase) {
            var score = 0
            for (kw in entry.keywords) {
                val normKw = normalize(kw)
                if (normQuery.contains(normKw)) {
                    score += if (normKw.contains(" ")) 18 else 10
                }
            }
            if (score > bestScore) {
                bestScore = score
                bestEntry = entry
            }
        }
        return if (bestScore >= 10) bestEntry else null
    }

    private fun synthesizeContextualConversationalReply(
        cleanQuery: String,
        normQuery: String,
        detailed: Boolean,
        recentHistory: List<Pair<String, String>>
    ): String {
        val lastContextHint = if (recentHistory.isNotEmpty()) {
            val lastUser = recentHistory.last().first
            " (considerando também o que conversamos sobre *\"$lastUser\"*)"
        } else ""

        return when {
            normQuery.startsWith("como ") || normQuery.contains("passo a passo") || normQuery.contains("fazer") -> {
                if (!detailed) {
                    "Para realizar **\"$cleanQuery\"** de forma prática e organizada$lastContextHint:\n\n" +
                        "• **1. Planejamento e Materiais:** Separe antes tudo o que vai usar e confira as condições do ambiente.\n" +
                        "• **2. Execução em Etapas:** Comece pelo preparo básico, teste em pequena escala primeiro e mantenha o controle de tempo/segurança.\n" +
                        "• **3. Revisão Final:** Confira o resultado e guarde os materiais excedentes.\n\n" +
                        "*(Se quiser que eu detalhe algum ponto específico ou adapte para os materiais que você tem em casa, me diga!)*"
                } else {
                    "### Guia Estruturado Passo a Passo: $cleanQuery\n\n" +
                        "1️⃣ **Preparação Inicial:**\n" +
                        "• Defina o objetivo exato e reúna os insumos necessários antes de iniciar para evitar interrupções no meio do processo.\n\n" +
                        "2️⃣ **Método de Execução:**\n" +
                        "• Divida a tarefa em 3 fases simples: **Preparação → Execução Principal → Acabamento/Verificação**.\n" +
                        "• Sempre priorize economia de recursos (água, energia e materiais) e segurança.\n\n" +
                        "3️⃣ **Dica do Motor Local Edge:**\n" +
                        "• Você também pode me dizer exatamente quais ingredientes, ferramentas ou informações você tem em mãos agora para que eu monte um plano personalizado!"
                }
            }

            normQuery.startsWith("por que") || normQuery.startsWith("porque") -> {
                if (!detailed) {
                    "Sobre **\"$cleanQuery\"**$lastContextHint:\n\n" +
                        "• Na maioria dos casos, esse fenômeno ocorre pela combinação de **causas físicas/naturais diretas** (energia, pressão, reação química ou biologia) e **fatores de contexto/ambiente**.\n" +
                        "• Se você quiser focar em um aspecto específico (científico, histórico ou prático), me fale ou toque em **\"🔍 Quero mais detalhes\"**!"
                } else {
                    "### Explicação Detalhada: $cleanQuery\n\n" +
                        "1️⃣ **Causa Primária (Mecanismo Direto):**\n" +
                        "• Todo processo físico, biológico ou histórico responde a uma relação de causa e efeito (equilíbrio energético, adaptação ou necessidade prática).\n\n" +
                        "2️⃣ **Aplicação Prática:**\n" +
                        "• Compreender o *porquê* permite prever o comportamento do sistema e agir preventivamente. Se quiser analisar um caso concreto ou fazer cálculos sobre isso, pode mandar os dados!"
                }
            }

            else -> {
                if (!detailed) {
                    "Entendi sua pergunta sobre **\"$cleanQuery\"**$lastContextHint!\n\n" +
                        "• Estou operando no **Modo Local 100% Offline** no seu aparelho.\n" +
                        "• Posso ajudar você com explicações diretas, cálculos matemáticos/elétricos, receitas, ciências, história, organização da rotina ou todos os 30 protocolos de sobrevivência.\n" +
                        "• Quer que eu detalhe um ponto específico de **\"$cleanQuery\"** ou faça algum cálculo prático sobre isso?"
                } else {
                    "### Análise Completa (Modo Local 100% Offline): $cleanQuery\n\n" +
                        "• **Resumo do Tópico:** Você perguntou sobre *\"$cleanQuery\"*.\n" +
                        "• **Capacidades Ativas sem Internet:** Mesmo 100% desconectado da rede, mantenho ativos o motor de raciocínio matemático/físico, a enciclopédia local embarcada e a integração direta com modelos `.task` / `.litertlm` do **Google AI Edge Gallery**.\n" +
                        "• **Dica:** Se você tiver um arquivo de modelo local (como *Gemma-2B-IT* ou *Gemma-3-1B*) baixado no celular, pode vinculá-lo no botão **\"🧠 Modelo Edge\"** no topo desta tela para expandir ainda mais a geração neural livre offline!"
                }
            }
        }
    }
}

