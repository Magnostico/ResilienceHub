package com.example.services

import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Base Enciclopédica e Solucionador Matemático/Científico 100% Offline de apoio
 * para cálculos rápidos e consultas enciclopédicas quando nenhum modelo `.task` foi carregado.
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

    fun generateOfflineResponse(
        query: String,
        detailed: Boolean,
        recentHistory: List<Pair<String, String>> = emptyList(),
        modelErrorDetail: String? = null
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

        // 4. Se chegou aqui, informa com transparência para selecionar o arquivo .task (ex: gemma-4-E2B-it.task)
        val errorNote = if (!modelErrorDetail.isNullOrBlank()) {
            "\n• **Detalhe técnico do motor:** $modelErrorDetail"
        } else ""

        return "📂 **Selecione o arquivo do modelo local (`.task`) para responder temas livres com IA neural!**\n\n" +
            "• Para que a IA responda livremente a qualquer pergunta aberta (como *\"$cleanQuery\"*) pensando e gerando texto token a token igual ao **Google AI Edge Gallery**, toque no botão **\"📂 Modelo .task\"** no topo da tela e selecione o seu arquivo **`gemma-4-E2B-it.task`**.\n" +
            "• Enquanto o arquivo `.task` não for selecionado, as **30 diretrizes oficiais de sobrevivência do RAG**, os dados do seu **Estoque/Família** e a calculadora matemática continuam funcionando normalmente!$errorNote"
    }

    private fun checkDateTimeQuery(norm: String): String? {
        val now = Date()
        if (norm.contains("que horas") || norm == "hora" || norm == "horas") {
            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
            return "Agora são **${sdf.format(now)}** pelo relógio interno do seu dispositivo."
        }
        if (norm.contains("que dia e hoje") || norm.contains("data de hoje") || norm.contains("qual a data")) {
            val sdf = SimpleDateFormat("EEEE, dd 'de' MMMM 'de' yyyy", Locale("pt", "BR"))
            return "Hoje é **${sdf.format(now)}**."
        }
        return null
    }

    private fun trySolveMathOrConversion(raw: String, norm: String, detailed: Boolean): String? {
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
                        "• Com desconto: ${formatNum(total)} - $fmt = **${formatNum(total - res)}**.\n" +
                        "• Com acréscimo: ${formatNum(total)} + $fmt = **${formatNum(total + res)}**."
                } else {
                    "**${formatNum(pct)}% de ${formatNum(total)}** é igual a **$fmt**."
                }
            }
        }

        val sqrtRegex = Regex("raiz\\s*(?:quadrada)?\\s*de\\s*(\\d+(?:[.,]\\d+)?)")
        sqrtRegex.find(norm)?.let { m ->
            val v = m.groupValues[1].replace(",", ".").toDoubleOrNull()
            if (v != null && v >= 0) {
                val r = sqrt(v)
                return "A raiz quadrada de **${formatNum(v)}** é **${formatNum(r)}**."
            }
        }

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
                        "Cálculo passo a passo:\n• Operação: **${formatNum(a)} $op ${formatNum(b)}**\n• Resultado: **${formatNum(res)}**."
                    } else {
                        "O resultado de **${formatNum(a)} $op ${formatNum(b)}** é **${formatNum(res)}**."
                    }
                }
            }
        }

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
            id = "cabral_descobrimento",
            title = "Pedro Álvares Cabral (1467–1520)",
            keywords = listOf("pedro alvares cabral", "pedro alvarez cabral", "cabral", "descobriu o brasil", "descobrimento do brasil", "1500", "monte pascoal", "porto seguro"),
            succinctAnswer = "**Pedro Álvares Cabral** (1467–1520) foi um fidalgo e navegador português, comandante da segunda frota enviada por Portugal às Índias, que chegou ao litoral do **Brasil em 22 de abril de 1500** (na região de Porto Seguro, Bahia, avistando o Monte Pascoal).",
            detailedAnswer = "### Pedro Álvares Cabral (1467–1520)\n\n" +
                "• **Quem foi:** Nobre, comandante militar e navegador português nascido em Belmonte (c. 1467). Foi nomeado pelo rei **D. Manuel I** para chefiar a esquadra de 13 embarcações rumo a Calecute, na Índia, logo após a viagem de Vasco da Gama.\n" +
                "• **Chegada ao Brasil (22 de abril de 1500):** Seguindo a rota do Atlântico Sul, sua frota avistou o **Monte Pascoal** no sul da Bahia e ancorou em **Porto Seguro** (Cabrália). O escrivão **Pero Vaz de Caminha** registrou o encontro na famosa *Carta a El-Rei D. Manuel*.\n" +
                "• **Continuação da Viagem e Fim da Vida:** Em maio de 1500, Cabral seguiu viagem para a Índia, retornando a Lisboa em 1501 com especiarias. Passou seus últimos anos em Santarém, Portugal, onde faleceu em 1520."
        ),
        FreeTopicEntry(
            id = "santos_dumont",
            title = "Alberto Santos Dumont (1873–1932)",
            keywords = listOf("santos dumont", "14 bis", "pai da aviacao", "demoiselle", "aviao"),
            succinctAnswer = "**Alberto Santos Dumont** (1873–1932) foi um inventor e aeronauta brasileiro, considerado o **Pai da Aviação** por realizar em Paris, em **23 de outubro de 1906**, o primeiro voo público homologado de um avião mais pesado que o ar autopropulsado: o **14-Bis**.",
            detailedAnswer = "### Alberto Santos Dumont (1873–1932)\n\n" +
                "• Nascido em Minas Gerais (Palmira, atual Santos Dumont), destacou-se em Paris desenvolvendo balões dirigíveis (vencendo o Prêmio Deutsch em 1901 ao contornar a Torre Eiffel).\n" +
                "• Em **23 de outubro de 1906**, no Campo de Bagatelle, decolou por meios próprios com o **14-Bis**, percorrendo 60 metros no ar diante da Federação Aeronáutica Internacional.\n" +
                "• Criou também o avião leve **Demoiselle** (cujos projetos deixou em domínio público) e popularizou o uso do relógio de pulso com a Cartier."
        ),
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
            keywords = listOf("historia", "segunda guerra", "primeira guerra", "imperio", "republica", "idade media", "roma", "egito", "independencia", "dom pedro", "tiradentes", "getulio vargas"),
            succinctAnswer = "Resumo histórico rápido:\n" +
                "• **História do Brasil:** Chegada de Pedro Álvares Cabral (1500), Brasil Colônia (1530–1815), Reino Unido a Portugal (1815–1822), **Independência e Império** (1822–1889, D. Pedro I e D. Pedro II) e **República** (1889 até hoje, com Constituição atual de **1988**).\n" +
                "• **Grandes Marcos Mundiais:** Revolução Francesa (1789), Primeira Guerra Mundial (1914–1918), Segunda Guerra Mundial (1939–1945) e Guerra Fria (1947–1991).",
            detailedAnswer = "### Panorama Detalhado: História do Brasil e Marcos Mundiais\n\n" +
                "1️⃣ **Cronologia Fundamental do Brasil:**\n" +
                "• **1500:** Chegada da esquadra de Pedro Álvares Cabral em Porto Seguro (BA).\n" +
                "• **1808:** Transferência da corte portuguesa para o Rio de Janeiro e abertura dos portos.\n" +
                "• **1822:** Independência do Brasil (7 de setembro) proclamada por D. Pedro I.\n" +
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
            keywords = listOf("inteligencia artificial", "llm", "gemma", "mediapipe", "programacao", "python", "kotlin", "android", "computador", "algoritmo", "edge gallery", "litert"),
            succinctAnswer = "Como funcionam **LLMs Locais (como no Google AI Edge Gallery e neste app)**:\n" +
                "• Modelos como **Gemma 4 E2B-IT (`gemma-4-E2B-it.task`)** utilizam o formato **LiteRT-LM** com pesos quantizados para rodar direto na **GPU/CPU do celular**.\n" +
                "• No ResilienceHub, basta selecionar seu arquivo `.task` no botão **\"📂 Modelo .task\"** para combinar a inferência neural do modelo com o RAG de sobrevivência 100% offline.",
            detailedAnswer = "### Arquitetura de IA Local On-Device (LiteRT-LM + RAG)\n\n" +
                "1️⃣ **Execução Neural Direta (`com.google.ai.edge.litertlm`):**\n" +
                "• O arquivo `gemma-4-E2B-it.task` utiliza o cabeçalho binário `LITERTLM` do Google AI Edge. O motor inicializa os pesos na GPU/CPU e transmite cada token gerado via `sendMessageAsync`.\n\n" +
                "2️⃣ **Fusão com o RAG Oficial:**\n" +
                "• Quando sua pergunta aborda sobrevivência, primeiros socorros ou seu estoque/família, o app injeta os dados relevantes do SQLite FTS4 no contexto; para qualquer outro tema livre, o modelo responde livremente usando seus pesos pré-treinados."
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
}
