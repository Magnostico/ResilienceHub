package com.example.data

import com.example.data.entity.ManualChunkFtsEntity

/**
 * Base de Conhecimento Oficial 100% Offline de Sobrevivência, Defesa Civil, Medicina de Emergência,
 * Catástrofes Naturais, Guerras/Conflitos Armados e Ameaças QBRN (Química, Biológica, Radiológica e Nuclear).
 *
 * Compilada a partir das diretrizes públicas oficiais:
 * - Defesa Civil Nacional (SINPDEC) & CEMADEN
 * - Ministério da Saúde (MS) / SAMU 192 / Instituto Butantan / ANVISA
 * - Organização Mundial da Saúde (OMS / WHO) & UNICEF / FAO
 * - Comitê Internacional da Cruz Vermelha (CICV / ICRC)
 * - FEMA (Ready.gov) & CNEN (Comissão Nacional de Energia Nuclear)
 * - Corpo de Bombeiros Militar (CBMERJ)
 */
object OfficialSurvivalKnowledgeBase {

    const val MIN_EXPECTED_OFFICIAL_CHUNKS = 30

    fun getOfficialChunks(): List<ManualChunkFtsEntity> = listOf(
        ManualChunkFtsEntity(
            rowId = 1,
            docTitle = "Ministério da Saúde & Defesa Civil: Purificação Química de Água (Hipoclorito e Clorin)",
            category = "WATER",
            pageNumber = 1,
            contentText = "DIRETRIZ OFICIAL (MINISTÉRIO DA SAÚDE / DEFESA CIVIL): Nunca consuma água de procedência duvidosa sem tratamento. " +
                "1) SE A ÁGUA ESTIVER TURVA: Filtre primeiro usando pano limpo de algodão dobrado, filtro de café ou filtro de barro/vela cerâmica para remover partículas. " +
                "2) HIPOCLORITO DE SÓDIO A 2,5%: Adicione 2 gotas de hipoclorito de sódio (2,5%) para cada 1 litro de água (ou 40 gotas / 2 ml para um galão de 20 litros). Misture bem e aguarde 30 minutos em recipiente fechado antes de beber. " +
                "3) PASTILHAS CLORIN (Dicloroisocianurato de Sódio): Adicione 1 pastilha de Clorin (para 1 litro) e aguarde 30 minutos. Se a água estiver muito fria ou levemente turva, dobre o tempo de espera para 60 minutos. " +
                "ATENÇÃO: Nunca use água sanitária com perfume, corante ou detergente; utilize apenas hipoclorito puro 2,5% próprio para desinfecção de água."
        ),
        ManualChunkFtsEntity(
            rowId = 2,
            docTitle = "OMS & Defesa Civil: Fervura de Água, Destilação e Coleta de Água da Chuva",
            category = "WATER",
            pageNumber = 2,
            contentText = "DIRETRIZ OFICIAL (OMS / DEFESA CIVIL): Na falta de cloro ou pastilhas purificadoras, a fervura é o método biológico mais seguro contra vírus, bactérias e protozoários. " +
                "1) FERVURA SEGURA: Filtre a água com tecido limpo e leve ao fogo até atingir fervura vigorosa (com bolhas grandes). Mantenha fervendo por 1 a 5 minutos contínuos. Após esfriar, passe a água de um recipiente limpo para outro algumas vezes para reincorporar oxigênio e melhorar o sabor. " +
                "2) COLETA DE ÁGUA DA CHUVA: Descarte os primeiros 10 a 15 minutos de chuva que escorrem do telhado (pois lavam poeira e fezes de aves). Colete a água seguinte em lonas limpas ou baldes higienizados e ainda assim trate com 2 gotas de hipoclorito 2,5% por litro ou fervura. " +
                "3) FONTES PROIBIDAS: Nunca beba água salgada do mar sem destilação, água com espuma química, cheiro de combustível ou água de enchente."
        ),
        ManualChunkFtsEntity(
            rowId = 3,
            docTitle = "Defesa Civil Nacional & FEMA: Kit de Sobrevivência e Evacuação 72 Horas (Bugout Bag)",
            category = "EVACUATION",
            pageNumber = 1,
            contentText = "DIRETRIZ OFICIAL (DEFESA CIVIL / FEMA READY.GOV): Toda família deve manter uma Mochila de Emergência 72 Horas pronta próxima à saída. " +
                "1) ÁGUA E ALIMENTO: Mínimo de 3 litros de água potável por pessoa por dia (ou garrafas seladas + pastilhas Clorin) e alimentos não perecíveis prontos para consumo sem cozimento (barras energéticas, atum/sardinha em lata com abertura fácil, castanhas, mel, ração operacional). " +
                "2) EQUIPAMENTOS CRÍTICOS: Lanterna LED de cabeça (mãos livres), rádio AM/FM/Ondas Curtas a pilha ou dínamo, power bank carregado, apito de emergência 120dB (para sinalização sob escombros), faca/canivete multifuncional, isqueiro/fósforos impermeáveis e manta térmica aluminizada (Mylar) para cada membro. " +
                "3) SAÚDE E DOCUMENTOS: Medicações contínuas da família para pelo menos 7 dias, kit de primeiros socorros com torniquete e gaze, dinheiro em espécie em notas pequenas, cópias plastificadas de RG/CPF/Certidões/Receitas e mapa físico com Ponto de Encontro Familiar."
        ),
        ManualChunkFtsEntity(
            rowId = 4,
            docTitle = "Defesa Civil & Busca e Salvamento (SAR): Regra dos Três e Prioridades de Sobrevivência",
            category = "GENERAL",
            pageNumber = 1,
            contentText = "DIRETRIZ OFICIAL (BUSCA E SALVAMENTO / DEFESA CIVIL): Em qualquer catástrofe, priorize suas ações pela Regra dos Três da fisiologia humana: " +
                "• 3 SEGUNDOS sem atenção e controle emocional levam ao pânico (aplique o protocolo PARE: Pare, Respire, Observe, Planeje). " +
                "• 3 MINUTOS sem oxigênio respirável ou com hemorragia arterial massiva levam à morte (prioridade zero: sair de fumaça/gás e estancar sangramento grave). " +
                "• 3 HORAS sem abrigo adequado em frio extremo, chuva com vento ou calor extremo levam à hipotermia ou insolação fatal. " +
                "• 3 DIAS sem água potável causam falência renal e colapso circulatório. " +
                "• 3 SEMANAS sem comida são suportáveis se houver hidratação adequada e repouso térmico."
        ),
        ManualChunkFtsEntity(
            rowId = 5,
            docTitle = "Cruz Vermelha Internacional (CICV): Sobrevivência em Guerra Urbana, Bombardeios e Artilharia",
            category = "WAR_CONFLICT",
            pageNumber = 1,
            contentText = "DIRETRIZ OFICIAL (COMITÊ INTERNACIONAL DA CRUZ VERMELHA - CICV): Como agir sob ataques aéreos, mísseis, drones, morteiros ou tiroteios pesados: " +
                "1) REGRA DAS DUAS PAREDES (NO INTERIOR): Afaste-se imediatamente das janelas. Abrigue-se no subsolo/porão ou em um corredor interno/banheiro sem janelas de modo que existam pelo menos DUAS paredes sólidas entre você e a fachada externa (a primeira parede absorve o impacto do projétil e a segunda bloqueia estilhaços secundários e vidro mortal). " +
                "2) POSIÇÃO CONTRA ONDA DE CHOQUE (EXPLOSÃO IMINENTE): Ao ouvir o silvo de bomba ou explosão próxima, jogue-se imediatamente no chão de bruços, proteja a nuca com as mãos, apoie o tórax nos cotovelos (levemente desencostado do solo para não transmitir a onda sísmica aos pulmões), feche os olhos, tape os ouvidos e MANTENHA A BOCA ENTREABERTA para equalizar a pressão e evitar ruptura dos tímpanos. " +
                "3) PREVENÇÃO NO IMÓVEL: Feche o registro geral de gás e a chave elétrica para evitar incêndios pós-impacto. Cole fitas adesivas em 'X' e feche cortinas pesadas nas janelas para reduzir projéteis de vidro."
        ),
        ManualChunkFtsEntity(
            rowId = 6,
            docTitle = "Cruz Vermelha Internacional (CICV) & ONU: Conduta Civil em Zona de Guerra e Munições Não Detonadas (UXO)",
            category = "WAR_CONFLICT",
            pageNumber = 2,
            contentText = "DIRETRIZ OFICIAL (CICV / ONU): Proteção da família em zonas de conflito armado e ocupação: " +
                "1) NA RUA DURANTE BOMBARDEIO OU TIROTEIO: Não corra em pé (a maioria das mortes ocorre por fragmentos voando entre 30cm e 1,5m de altura). Deite-se imediatamente no chão, em uma vala, sarjeta profunda ou atrás de mureta de concreto/terra; nunca se esconda atrás de veículos (projéteis atravessam lataria e o tanque pode explodir). " +
                "2) IDENTIFICAÇÃO CIVIL: Vista roupas civis neutras (nunca use camuflado, coturnos militares ou mochilas táticas verde-oliva/coyote em zona de guerra para não ser confundido com combatente). Em deslocamento humanitário, porte pano branco visível. " +
                "3) MUNIÇÕES NÃO DETONADAS (UXO) E MINAS: Nunca toque, chute ou mexa em objetos metálicos estranhos, drones caídos, submunições ou granadas não explodidas. Não use rádio transmissor ou celular próximo ao artefato. Afaste as crianças e recue pelas mesmas pegadas por onde entrou."
        ),
        ManualChunkFtsEntity(
            rowId = 7,
            docTitle = "FEMA (Ready.gov) & CNEN: Emergência Nuclear, Explosão Atômica e Fallout Radioativo",
            category = "NUCLEAR_CBRN",
            pageNumber = 1,
            contentText = "DIRETRIZ OFICIAL (FEMA READY.GOV / CNEN): Protocolo oficial 'Entre, Fique Dentro e Sintonize' para detonação nuclear ou acidente radiológico: " +
                "1) CLARÃO CEGANTE: Nunca olhe para o clarão ou bola de fogo (causa cegueira imediata). Jogue-se no chão atrás de cobertura sólida, proteja a cabeça e aguarde a passagem da onda de choque (que pode levar de 10 a 30 segundos após o clarão). " +
                "2) JANELA CRÍTICA DE 10 A 15 MINUTOS: Após a onda de choque, você tem cerca de 10 a 15 minutos antes que a poeira radioativa letal (Fallout) comece a cair do céu. Entre IMEDIATAMENTE no edifício de concreto ou tijolo mais robusto ou subsolo disponível. Nunca fique dentro de um carro. " +
                "3) BLINDAGEM E REGRA 7/10: Vá para o centro do prédio ou subsolo (longe do telhado e das paredes externas onde a poeira se acumula). Desligue ar-condicionado e ventilação externa. Permaneça abrigado por pelo menos 24 a 48 horas: pela Regra 7/10 da física nuclear, a cada aumento de 7 vezes no tempo após a explosão, a radiação cai para 1/10 (em 7h cai 90%; em 49h cai 99%)."
        ),
        ManualChunkFtsEntity(
            rowId = 8,
            docTitle = "CNEN & OMS: Descontaminação Radiológica Familiar e Proteção de Água/Alimentos",
            category = "NUCLEAR_CBRN",
            pageNumber = 2,
            contentText = "DIRETRIZ OFICIAL (CNEN / OMS / FEMA): Caso alguém da família tenha ficado exposto à poeira externa após um evento radiológico/nuclear: " +
                "1) REMOÇÃO DE 90% DA CONTAMINAÇÃO: Antes de entrar no cômodo interno do abrigo, retire toda a camada externa de roupas e calçados (isso elimina até 90% da poeira radioativa). Coloque essas roupas em saco plástico fechado e deixe longe das pessoas. " +
                "2) BANHO DE DESCONTAMINAÇÃO: Lave a pele exposta e os cabelos suavemente com água morna e sabão neutro. NÃO esfregue a pele com bucha (para não abrir microfissuras) e NÃO use condicionador de cabelo (pois a química do condicionador fixa partículas radioativas nos fios). Assoe o nariz e limpe pálpebras e orelhas com pano úmido. " +
                "3) ÁGUA E ALIMENTOS: Só consuma água de garrafas/galões lacrados ou da caixa d'água se ela estiver totalmente vedada. Alimentos em latas ou embalagens fechadas são 100% seguros (basta limpar a embalagem por fora com pano úmido antes de abrir). " +
                "4) IODETO DE POTÁSSIO (KI): Só deve ser administrado sob orientação das autoridades de saúde para proteger a tireoide contra iodo radioativo (especialmente crianças e gestantes)."
        ),
        ManualChunkFtsEntity(
            rowId = 9,
            docTitle = "OMS & Defesa Civil: Ataques Químicos, Gases Tóxicos e Agentes Biológicos (Vedação de Abrigo)",
            category = "NUCLEAR_CBRN",
            pageNumber = 3,
            contentText = "DIRETRIZ OFICIAL (OMS / DEFESA CIVIL / OPCW): Como proteger a família contra nuvens tóxicas, vazamentos químicos industriais ou armas químicas/biológicas: " +
                "1) SUBA PARA ANDARES ALTOS: Diferente da poeira nuclear (onde o subsolo é melhor), a maioria dos gases químicos (como cloro, fosgênio e agentes nervosos) é MAIS PESADA QUE O AR e se acumula em porões, baixadas e andares térreos. Abrigue-se em cômodo interno em andar elevado. " +
                "2) VEDAÇÃO DO CÔMODO (SHELTER-IN-PLACE): Desligue imediatamente ventiladores e ar-condicionado. Feche janelas, trincos e basculantes. Coloque toalhas ou panos molhados na fresta inferior das portas e vede frestas de janelas e tomadas com fita adesiva larga (silver tape) e lona plástica. " +
                "3) PROTEÇÃO RESPIRATÓRIA DE EMERGÊNCIA: Se precisar atravessar área com gás ou fumaça química para fugir (sempre contra o vento e para terreno mais alto), respire através de máscara PFF2/N95 ou tecido de algodão dobrado em várias camadas umedecido com água."
        ),
        ManualChunkFtsEntity(
            rowId = 10,
            docTitle = "Defesa Civil Nacional & Ministério da Saúde: Enchentes, Inundações e Prevenção de Leptospirose",
            category = "NATURAL_DISASTER",
            pageNumber = 1,
            contentText = "DIRETRIZ OFICIAL (DEFESA CIVIL / MINISTÉRIO DA SAÚDE): Procedimentos de sobrevivência em inundações, enxurradas e alagamentos: " +
                "1) ENERGIA E GÁS: Assim que a água começar a subir, desligue imediatamente o disjuntor geral de eletricidade (para evitar choque elétrico letal na água) e feche o registro do gás. " +
                "2) REGRA DOS 15 CM E 30 CM: Nunca tente atravessar enxurradas a pé ou de carro. Apenas 15 cm de água em movimento rápido derrubam um adulto; 30 cm a 60 cm de água arrastam qualquer automóvel ou SUV. Se o carro parar na enchente, abandone-o imediatamente e suba para local alto. " +
                "3) EVACUAÇÃO VERTICAL: Nunca suba para um sótão fechado sem saída para o telhado (risco de ficar preso se a água subir). Suba para lajes abertas ou telhados levando apito, lanterna e capa impermeável. " +
                "4) LEPTOSPIROSE E LIMPEZA PÓS-ENCHENTE: A água e a lama de enchente são contaminadas por esgoto e urina de rato. Descarte todos os alimentos e remédios que tiveram contato com a água da enchente. Para limpar a casa, use botas e luvas (ou sacos plásticos duplos nos pés/mãos) e desinfete pisos com 1 copo (200ml) de água sanitária para cada balde de 20L de água. Em caso de febre e dor na panturrilha após contato com enchente, procure atendimento médico imediato."
        ),
        ManualChunkFtsEntity(
            rowId = 11,
            docTitle = "Defesa Civil & CEMADEN: Deslizamentos de Terra e Sinais Iminentes de Colapso de Encosta",
            category = "NATURAL_DISASTER",
            pageNumber = 2,
            contentText = "DIRETRIZ OFICIAL (DEFESA CIVIL / CEMADEN): Como identificar sinais de deslizamento de terra e salvar a família antes do desmoronamento: " +
                "1) SINAIS DE ALERTA IMEDIATO: Abandone o imóvel na hora se observar: rachaduras novas ou que aumentam rapidamente nas paredes ou no chão; portas e janelas emperrando de repente; água barrenta brotando na base do barranco; postes, árvores ou muros de contenção ficando inclinados; ou estalos vindos da estrutura/solo. " +
                "2) CHUVA PERSISTENTE: O maior risco de deslizamento ocorre quando o solo já está encharcado após vários dias de chuva (mesmo que a chuva diminua momentaneamente). Ao soar a sirene da Defesa Civil, pegue a Mochila 72h e vá para o Ponto de Apoio. " +
                "3) ROTA DE FUGA: Afaste-se lateralmente da trajetória do deslizamento (nunca corra na mesma direção em que a lama desce). Jamais retorne ao imóvel interditado para buscar bens materiais."
        ),
        ManualChunkFtsEntity(
            rowId = 12,
            docTitle = "FEMA & Defesa Civil: Terremotos, Desabamento de Prédios e Sobrevivência sob Escombros",
            category = "NATURAL_DISASTER",
            pageNumber = 3,
            contentText = "DIRETRIZ OFICIAL (FEMA / DEFESA CIVIL / CBMERJ): Como sobreviver a tremores sísmicos, explosões estruturais e colapso de edificações: " +
                "1) ABAIXAR, COBRIR E SEGURAR (DROP, COVER, HOLD ON): Não tente correr pelas escadas nem use elevadores durante o tremor ou colapso. Ajoelhe-se, entre debaixo de uma mesa resistente ou encoste-se junto a uma coluna estrutural interna (longe de janelas e armários altos) e proteja a cabeça e o pescoço com os braços. " +
                "2) SE FICAR PRESO SOB ESCOMBROS: NUNCA acenda fósforos ou isqueiros (há alto risco de vazamento de gás). Cubra a boca e o nariz com a camiseta para não inalar poeira de concreto (que causa asfixia). " +
                "3) SINALIZAÇÃO SEM GASTAR OXIGÊNIO: Evite gritar continuamente (gritar consome oxigênio e faz inalar poeira). Use o APITO da mochila ou bata ritmadamente (3 batidas pausadas — SOS) com uma pedra ou metal em canos metálicos ou vigas (o som se propaga muito mais longe pela estrutura do que a voz)."
        ),
        ManualChunkFtsEntity(
            rowId = 13,
            docTitle = "Cruz Vermelha & Ministério da Saúde: Controle de Hemorragia Massiva e Torniquete Tático (MARCH)",
            category = "MEDS",
            pageNumber = 1,
            contentText = "DIRETRIZ OFICIAL (CRUZ VERMELHA / SAMU 192 / PROTOCOLO MARCH): Uma pessoa com sangramento arterial grave pode morrer em menos de 3 minutos. Aja imediatamente: " +
                "1) COMPRESSÃO DIRETA FORTE: Aplique pressão firme e contínua diretamente sobre a ferida usando gaze estéril ou um pano limpo dobrado. Se o pano encharcar de sangue, NÃO o retire (pois isso arranca o coágulo em formação): coloque outro pano por cima e mantenha a pressão com as duas mãos. " +
                "2) PREENCHIMENTO DE FERIDA (WOUND PACKING): Em ferimentos profundos no pescoço, axila ou virilha (onde não cabe torniquete), introduza gaze ou tecido limpo dentro da cavidade do ferimento até o fundo e comprima com força máxima por no mínimo 3 a 5 minutos. " +
                "3) TORNIQUETE EM BRAÇOS OU PERNAS: Se o sangramento em membro for esguichante (arterial), incontrolável ou houver amputação traumática, aplique o Torniquete Tático (CAT) de 5 a 7 cm ACIMA do ferimento (nunca sobre o joelho ou cotovelo). Aperte a fita ao máximo e gire o molinete até o sangramento parar completamente. Trave a haste, ANOTE A HORA EXATA DA APLICAÇÃO na testa da vítima ou no torniquete e NÃO afrouxe o torniquete até chegar ao centro cirúrgico."
        ),
        ManualChunkFtsEntity(
            rowId = 14,
            docTitle = "OMS & SAMU 192: Ressuscitação Cardiopulmonar (RCP) em Adultos, Crianças e Bebês",
            category = "MEDS",
            pageNumber = 2,
            contentText = "DIRETRIZ OFICIAL (OMS / SAMU 192 / AHA): Se a vítima está inconsciente e NÃO respira (ou apresenta apenas respiração agônica 'gasping'): " +
                "1) ADULTO (COMPRESSÕES CONTÍNUAS): Deite a vítima de costas em superfície rígida. Posicione a base de uma mão no centro do peito (metade inferior do osso esterno), coloque a outra mão por cima entrelaçando os dedos e mantenha os braços retos (90 graus). Comprima forte e rápido: profundidade de 5 a 6 cm, na frequência de 100 a 120 compressões por minuto (quase 2 por segundo), permitindo que o tórax retorne totalmente após cada compressão. " +
                "2) CRIANÇA (1 ANO ATÉ PUBERDADE): Comprima o centro do tórax cerca de 5 cm de profundidade (1/3 da altura do tórax) usando uma ou duas mãos, no ritmo de 100 a 120/min. Se souber fazer ventilação boca-a-boca com segurança em crianças, alterne 30 compressões para 2 sopros suaves de 1 segundo. " +
                "3) BEBÊ (MENOR DE 1 ANO): Use apenas 2 DEDOS no centro do peito (logo abaixo da linha dos mamilos), comprimindo cerca de 4 cm de profundidade no ritmo de 100 a 120/min (30 compressões para 2 ventilações suaves cobrindo boca e nariz do bebê)."
        ),
        ManualChunkFtsEntity(
            rowId = 15,
            docTitle = "Ministério da Saúde & Corpo de Bombeiros: Engasgo e Asfixia (Manobra de Heimlich Adulto e Bebê)",
            category = "MEDS",
            pageNumber = 3,
            contentText = "DIRETRIZ OFICIAL (MINISTÉRIO DA SAÚDE / CORPO DE BOMBEIROS): Quando a pessoa engasga totalmente (não consegue falar, tossir nem respirar, levando as mãos ao pescoço): " +
                "1) ADULTO E CRIANÇA MAIOR DE 1 ANO (MANOBRA DE HEIMLICH): Posicione-se atrás da vítima (ajoelhe-se se for criança). Envolva a cintura com os braços. Feche uma das mãos em punho e posicione o lado do polegar na boca do estômago (entre o umbigo e o final das costelas). Segure o punho com a outra mão e faça compressões rápidas e vigorosas para DENTRO e para CIMA (movimento em 'J') até o objeto ser expelido. Em gestantes ou obesos, faça as compressões no centro do osso do peito (esterno). " +
                "2) BEBÊ MENOR DE 1 ANO (5 TAPAS + 5 COMPRESSÕES): NUNCA sacuda o bebê nem faça compressão abdominal. Apoie o bebê de bruços sobre o seu antebraço, segurando firme o queixo dele com os dedos (sem tapar a boca) e mantendo a cabeça MAIS BAIXA que o tronco. Dê 5 tapas firmes com a base da mão no meio das costas (entre as escápulas). Vire o bebê de barriga para cima no outro antebraço e faça 5 compressões rápidas com 2 dedos no centro do peito. Repita o ciclo 5+5 até o bebê chorar ou desengasgar."
        ),
        ManualChunkFtsEntity(
            rowId = 16,
            docTitle = "Ministério da Saúde & SBQ: Tratamento Oficial de Queimaduras Térmicas, Químicas e Elétricas",
            category = "MEDS",
            pageNumber = 4,
            contentText = "DIRETRIZ OFICIAL (MINISTÉRIO DA SAÚDE / SOCIEDADE BRASILEIRA DE QUEIMADURAS): " +
                "1) RESFRIAMENTO IMEDIATO: Coloque a área queimada sob ÁGUA CORRENTE FRIA (em temperatura ambiente, jato suave) por 10 a 20 minutos para interromper a progressão térmica nas camadas profundas da pele. " +
                "2) REMOÇÃO DE ADORNOS: Retire imediatamente anéis, alianças, relógios, pulseiras e roupas que NÃO estejam grudadas na pele antes que o membro inche (se o tecido estiver colado na ferida, corte ao redor e NÃO puxe). " +
                "3) O QUE É ESTRITAMENTE PROIBIDO: NUNCA passe pasta de dente, manteiga, óleo, clara de ovo, pó de café ou pomadas caseiras. NUNCA aplique gelo direto (causa necrose por frio) e NUNCA fure ou estoure as bolhas (as bolhas são o curativo biológico estéril que evita infecção fatal). " +
                "4) PROTEÇÃO: Cubra a queimadura frouxamente com pano limpo úmido ou gaze estéril umedecida com soro fisiológico. Em queimaduras químicas por pó (como cal), escove o pó seco primeiro antes de lavar com muita água corrente por 20 minutos."
        ),
        ManualChunkFtsEntity(
            rowId = 17,
            docTitle = "OMS, UNICEF & Ministério da Saúde: Soro Caseiro (SRO) e Combate à Desidratação e Diarreia",
            category = "MEDS",
            pageNumber = 5,
            contentText = "DIRETRIZ OFICIAL (OMS / UNICEF / MINISTÉRIO DA SAÚDE): Em catástrofes e guerras, a desidratação por diarreia aguda mata mais crianças do que traumas físicos. " +
                "1) RECEITA OFICIAL DO SORO CASEIRO (PARA 1 LITRO DE ÁGUA): Em 1 litro de água potável (filtrada, fervida ou clorada e já fria), dissolva: " +
                "• 1 colher de café rasa de SAL de cozinha (cerca de 3,5g) + " +
                "• 2 colheres de sopa rasas de AÇÚCAR (cerca de 20g a 40g). " +
                "Misture bem e PROVE antes de dar à criança: o soro nunca deve ser mais salgado que uma lágrima! (Validade máxima: 24 horas). " +
                "2) COMO ADMINISTRAR: Ofereça em pequenos goles ou colheradas contínuas após cada evacuação líquida ou vômito. Crianças: 50 a 100 ml por kg de peso ao longo de 4 horas. Se a criança vomitar, aguarde 10 minutos e volte a oferecer devagar (1 colher de chá a cada 2 minutos). " +
                "3) SINAIS DE DESIDRATAÇÃO GRAVE: Olhos fundos, ausência de lágrimas ao chorar, boca muito seca, moleira baixa em bebês, pouca urina escura e sonolência excessiva."
        ),
        ManualChunkFtsEntity(
            rowId = 18,
            docTitle = "Corpo de Bombeiros (CBMERJ) & Cruz Vermelha: Fraturas, Imobilização e Suspeita de Lesão na Coluna",
            category = "MEDS",
            pageNumber = 6,
            contentText = "DIRETRIZ OFICIAL (CORPO DE BOMBEIROS / CRUZ VERMELHA): " +
                "1) FRATURAS EM BRAÇOS E PERNAS: NUNCA tente colocar o osso de volta no lugar nem alinhar fraturas deformadas (risco de romper artérias e nervos). Imobilize o membro exatamente na posição encontrada usando talas improvisadas (papelão grosso dobrado, revista grossa, tábua ou galho reto acolchoado com pano). A tala deve imobilizar a articulação ACIMA e a articulação ABAIXO da fratura. Amarre com tiras de tecido sem bloquear a circulação sanguínea (verifique se a ponta dos dedos continua rosada e quente). " +
                "2) FRATURA EXPOSTA (OSSO VISÍVEL): Não empurre o osso para dentro. Cubra o ferimento com gaze ou pano limpo estéril, controle o sangramento com pressão ao redor do osso e imobilize. " +
                "3) SUSPEITA DE TRAUMA NA COLUNA / PESCOÇO (Quedas de altura, soterramento, explosão): Não mova a vítima, a menos que haja risco iminente de morte no local (incêndio, desabamento ou inundação). Mantenha a cabeça e o pescoço alinhados manualmente com o tronco."
        ),
        ManualChunkFtsEntity(
            rowId = 19,
            docTitle = "OMS & Cruz Vermelha: Hipotermia Severa, Exaustão Térmica e Insolação (Golpe de Calor)",
            category = "MEDS",
            pageNumber = 7,
            contentText = "DIRETRIZ OFICIAL (OMS / CRUZ VERMELHA): " +
                "1) HIPOTERMIA (Temperatura corporal abaixo de 35°C por frio/chuva): Sinais incluem tremores intensos (que param quando a hipotermia fica grave!), confusão mental, fala arrastada e pele fria/pálida. AÇÃO: Leve para local abrigado do vento, REMOVA IMEDIATAMENTE todas as roupas molhadas, seque a pessoa e envolva-a em cobertores secos e Manta Térmica Aluminizada (incluindo a cabeça). Aqueça primeiro o centro do corpo (tórax, pescoço e virilha) com garrafas de água morna envoltas em pano ou calor corporal humano. Dê líquidos mornos adocicados apenas se estiver consciente (nunca dê álcool e não esfregue os braços/pernas). " +
                "2) INSOLAÇÃO / GOLPE DE CALOR (Emergência fatal acima de 40°C): Pele muito quente, confusão ou desmaio. AÇÃO: Leve para a sombra, retire o excesso de roupas, borrife água fria em todo o corpo enquanto abana vigorosamente e coloque compressas frias no pescoço, axilas e virilha."
        ),
        ManualChunkFtsEntity(
            rowId = 20,
            docTitle = "Instituto Butantan & Ministério da Saúde: Picadas de Cobras, Escorpiões e Aranhas",
            category = "MEDS",
            pageNumber = 8,
            contentText = "DIRETRIZ OFICIAL (INSTITUTO BUTANTAN / MINISTÉRIO DA SAÚDE): " +
                "1) O QUE FAZER IMEDIATAMENTE: Mantenha a vítima em repouso absoluto e deitada (caminhar ou correr acelera a absorção do veneno pelo sangue). Lave o local da picada apenas com água e sabão. Retire anéis, pulseiras e calçados apertados do membro atingido antes que inche. Mantenha o membro picado levemente elevado ou no nível do coração. " +
                "2) O QUE NUNCA FAZER (PROIBIDO): NUNCA faça torniquete ou garrote em picada de cobra (concentra o veneno e causa gangrena e amputação!). NUNCA corte ou fure o local da picada, NUNCA tente chupar o veneno com a boca e não coloque folhas, terra ou querosene na ferida. " +
                "3) PICADA DE ESCORPIÃO (Especialmente perigosa em crianças): Aplique compressas MORNAS no local para aliviar a dor intensa e leve com urgência ao ponto médico com soro antiescorpiônico. Se possível, fotografe o animal com segurança para identificação."
        ),
        ManualChunkFtsEntity(
            rowId = 21,
            docTitle = "OMS & Ministério da Saúde: Tratamento de Feridas Contaminadas e Prevenção de Infecção sem Hospital",
            category = "MEDS",
            pageNumber = 9,
            contentText = "DIRETRIZ OFICIAL (OMS / MINISTÉRIO DA SAÚDE): Em cenários de colapso onde hospitais estão inacessíveis, uma simples ferida infectada pode evoluir para sepse ou tétano: " +
                "1) IRRIGAÇÃO SOB PRESSÃO (O PASSO MAIS IMPORTANTE): Lave suas mãos antes. Irrigue a ferida abundantemente com jato forte de água limpa tratada (fervida/clorada) ou soro fisiológico 0,9% para expulsar terra, farpas e bactérias. Lave ao redor com sabão neutro. " +
                "2) ANTISSEPSIA E CURATIVO: Aplique antisséptico (Clorexidina aquosa ou Povidine tópico) e cubra com gaze estéril seca trocada diariamente (ou sempre que umedecer). Não feche feridas sujas, mordidas de animais ou cortes profundos por estilhaços com cola ou pontos improvisados (devem cicatrizar abertas de dentro para fora para drenar). " +
                "3) SINAIS DE ALERTA DE INFECÇÃO: Vermelhidão que se espalha ao redor do corte, calor local, inchaço crescente, pus amarelado, linhas vermelhas subindo pelo membro ou febre."
        ),
        ManualChunkFtsEntity(
            rowId = 22,
            docTitle = "SAMU 192 & Ministério da Saúde: Crises Médicas Agudas (Anafilaxia, Asma, Convulsão, Hipoglicemia e Infarto)",
            category = "MEDS",
            pageNumber = 10,
            contentText = "DIRETRIZ OFICIAL (SAMU 192 / MINISTÉRIO DA SAÚDE): " +
                "1) CONVULSÃO / ATAQUE EPILÉPTICO: Proteja a cabeça da pessoa com uma roupa dobrada, afaste objetos perigosos e afrouxe roupas no pescoço. NUNCA tente segurar os movimentos nem coloque dedos ou objetos dentro da boca da vítima. Assim que os abalos pararem, vire a pessoa de lado (Posição Lateral de Segurança) para que a saliva escorra sem sufocar. " +
                "2) CRISE ASMÁTICA / FALTA DE AR: Mantenha a pessoa SENTADA inclinada levemente para a frente (nunca deitada de costas). Aplique a bombinha broncodilatadora (Salbutamol: 2 a 4 jatos com espaçador a cada 20 min na primeira hora). " +
                "3) HIPOGLICEMIA (Diabético com suor frio, tremor e confusão): Se estiver consciente e conseguindo engolir, dê imediatamente 1 colher de sopa de açúcar, mel ou meio copo de água com açúcar. " +
                "4) SUSPEITA DE INFARTO (Dor ou aperto forte no peito irradiando para braço esquerdo, mandíbula ou costas): Repouso absoluto sentado; se não tiver alergia nem sangramento, ofereça 1 a 2 comprimidos de AAS infantil (100mg) mastigados."
        ),
        ManualChunkFtsEntity(
            rowId = 23,
            docTitle = "FEMA & Corpo de Bombeiros: Apagão Elétrico Prolongado (Blackout) e Risco de Monóxido de Carbono (CO)",
            category = "ENERGY_GEAR",
            pageNumber = 1,
            contentText = "DIRETRIZ OFICIAL (FEMA / CORPO DE BOMBEIROS): " +
                "1) PROTEÇÃO DE ELETRÔNICOS E GELADEIRA: No início de um apagão geral, tire os aparelhos da tomada (deixando apenas 1 lâmpada acesa como aviso) para que o pico de tensão no retorno da energia não queime tudo. Mantenha a geladeira e o freezer FECHADOS: uma geladeira fechada mantém os alimentos frios por 4 horas; um freezer cheio mantém o congelamento por até 48 horas. " +
                "2) PERIGO MORTAL DE MONÓXIDO DE CARBONO (CO): O gás CO é invisível, não tem cheiro e mata a família dormindo. NUNCA use geradores a gasolina/diesel, churrasqueiras a carvão, fogareiros de acampamento ou motores dentro de casa, na garagem ou perto de janelas (geradores devem ficar a no mínimo 6 metros da residência ao ar livre). " +
                "3) ILUMINAÇÃO SEGURA: Priorize sempre lanternas LED e luzes recarregáveis. Evite velas acesas sem vigilância (principal causa de incêndios residenciais durante apagões)."
        ),
        ManualChunkFtsEntity(
            rowId = 24,
            docTitle = "FEMA & Defesa Civil: Pulso Eletromagnético (EMP), Tempestade Solar e Gaiola de Faraday Caseira",
            category = "ENERGY_GEAR",
            pageNumber = 2,
            contentText = "DIRETRIZ OFICIAL (FEMA / DEFESA CIVIL): Um Pulso Eletromagnético de alta altitude (HEMP) ou uma supertempestade solar geomagnética pode induzir correntes destrutivas na rede elétrica e queimar circuitos eletrônicos: " +
                "1) DESCONEXÃO FÍSICA: Aparelhos conectados à tomada ou com longas antenas externas funcionam como coletores do pulso. Guarde seus equipamentos de reserva totalmente desconectados de cabos. " +
                "2) COMO CONSTRUIR UMA GAIOLA DE FARADAY CASEIRA: Para proteger um celular antigo com este aplicativo (ResilienceHub), rádio portátil AM/FM/SW, lanterna e power bank: envolva cada aparelho primeiro em um isolamento não condutor (papelão, plástico bolha ou pano seco) para que o aparelho NÃO encoste no metal. Em seguida, embrulhe-o com pelo menos 3 a 4 camadas bem fechadas de papel-alumínio grosso (sem frestas) ou coloque dentro de uma lata metálica de biscoito/tinta com tampa metálica justa forrada internamente com papelão."
        ),
        ManualChunkFtsEntity(
            rowId = 25,
            docTitle = "OMS & Cruz Vermelha: Saneamento de Emergência e Banheiro Seco (Colapso de Água e Esgoto)",
            category = "SANITATION",
            pageNumber = 1,
            contentText = "DIRETRIZ OFICIAL (OMS / CRUZ VERMELHA): Se a rede de água e esgoto parar de funcionar durante uma guerra ou desastre, nunca dê descarga sem água e nunca acumule dejetos expostos (risco gravíssimo de cólera, febre tifoide e disenteria): " +
                "1) VEDAÇÃO DO VASO SANITÁRIO: Se a tubulação do prédio romper ou entupir, feche o vaso sanitário para evitar refluxo de esgoto. " +
                "2) SISTEMA DO BANHEIRO SECO DE DOIS BALDES (TWIN-BUCKET TOILET): Separe 2 baldes de 20 litros forrados com sacos de lixo grossos resistentes. Use o Balde 1 APENAS para urina (com um pouco de água e algumas gotas de água sanitária) e o Balde 2 APENAS para fezes sólidas. Separar o líquido do sólido reduz em 80% o mau cheiro e o volume! Após cada uso do balde de sólidos, cubra imediatamente com uma camada de serragem, areia seca, terra seca, cinzas, cal hidratada ou pó de café usado. Amarre bem o saco quando atingir metade e descarte em vala rasa afastada no mínimo 30 metros de qualquer fonte de água."
        ),
        ManualChunkFtsEntity(
            rowId = 26,
            docTitle = "FAO, OMS & Defesa Civil: Conservação de Alimentos sem Geladeira e Racionamento Calórico Familiar",
            category = "FOOD",
            pageNumber = 1,
            contentText = "DIRETRIZ OFICIAL (FAO / OMS / DEFESA CIVIL): Como gerenciar o estoque de alimentos da família em crises prolongadas: " +
                "1) ORDEM DE CONSUMO NO APAGÃO: Fase 1 (Primeiras 4h a 12h): Consuma primeiro os alimentos frescos da geladeira (leite aberto, carnes frescas, laticínios, sobras cozidas). Fase 2 (Até 48h): Consuma os itens do freezer conforme descongelam (cozinhe carnes imediatamente e salgue se necessário). Fase 3 (Longo prazo): Passe para o estoque seco e enlatados aplicando a regra FEFO (Primeiro que Vence é o Primeiro que Sai). " +
                "2) REGRAS DE SEGURANÇA ALIMENTAR: NUNCA consuma latas estufadas, amassadas nas bordas, enferrujadas ou que espirrem gás/espuma ao abrir (risco letal de toxina botulínica — Botulismo). Aproveite o líquido de conservação de latas de legumes/milho/ervilha para cozinhar arroz ou sopas e economizar água potável. " +
                "3) RACIONAMENTO INTELIGENTE: Nunca racione a água abaixo do mínimo vital se você estiver comendo alimentos secos ou muito protéicos/salgados, pois a digestão de proteínas e sal exige água dos rins. Se houver pouquíssima água, reduza a ingestão de alimentos salgados e priorize carboidratos leves."
        ),
        ManualChunkFtsEntity(
            rowId = 27,
            docTitle = "Corpo de Bombeiros (CBMERJ): Incêndios Residenciais, Fumaça Tóxica e Regra da Porta Quente",
            category = "EVACUATION",
            pageNumber = 2,
            contentText = "DIRETRIZ OFICIAL (CORPO DE BOMBEIROS - CBMERJ): Em incêndios urbanos ou após explosões, 80% das vítimas morrem por inalação de fumaça tóxica e gases superaquecidos, não pelas chamas: " +
                "1) RASTEJE JUNTO AO CHÃO: O ar mais limpo e mais frio fica nos 30 a 50 cm próximos ao piso. Desloque-se agachado ou rastejando, cobrindo nariz e boca com pano úmido. " +
                "2) REGRA DO DORSO DA MÃO NA PORTA: Antes de abrir qualquer porta fechada em um incêndio, toque a madeira e a maçaneta com as COSTAS DA MÃO (nunca com a palma, pois se der choque ou queimar, o reflexo fecha os dedos na maçaneta). Se a porta estiver quente ou sair fumaça pelas frestas, NÃO ABRA! Existe fogo intenso do outro lado (abrir causará explosão de fumaça / Backdraft). " +
                "3) SE NÃO PUDER SAIR: Feche a porta do quarto, vede as frestas com toalhas molhadas, vá até a janela e sinalize com um pano claro ou lanterna. Se suas roupas pegarem fogo: PARE, DEITE NO CHÃO E ROLE cobrindo o rosto com as mãos."
        ),
        ManualChunkFtsEntity(
            rowId = 28,
            docTitle = "ITU, Defesa Civil & SAR: Comunicações de Emergência, Rádio AM/FM/SW e Sinais Terra-Ar",
            category = "ENERGY_GEAR",
            pageNumber = 3,
            contentText = "DIRETRIZ OFICIAL (ITU / DEFESA CIVIL / BUSCA E SALVAMENTO): Quando internet e redes de telefonia celular caem totalmente: " +
                "1) RÁDIO RECEPTOR (AM / FM / ONDAS CURTAS - SW): É o meio oficial da Defesa Civil para transmitir rotas de evacuação, locais de distribuição de água e alertas de segurança. Emissoras AM e Ondas Curtas (SW) alcançam centenas ou milhares de quilômetros, especialmente à noite. Ligue o rádio apenas nos horários cheios (ex: 5 minutos a cada hora) para economizar pilhas. " +
                "2) SINAL UNIVERSAL SOS (CÓDIGO MORSE): 3 sinais curtos, 3 sinais longos e 3 sinais curtos (• • • — — — • • •) feitos com lanterna (use a aba Ferramentas deste app), apito ou batidas. " +
                "3) SINAIS INTERNACIONAIS TERRA-AR (Para helicópteros e aeronaves de resgate): Desenhe no solo aberto com pedras, galhos ou lonas contrastantes letras gigantes de no mínimo 3 metros de comprimento: 'V' = Precisamos de Assistência; 'X' = Precisamos de Socorro Médico Urgente; 'Y' = Sim; 'N' = Não; 'SOS' = Perigo extremo."
        ),
        ManualChunkFtsEntity(
            rowId = 29,
            docTitle = "Defesa Civil & CICV: Segurança Residencial em Crises Civis, Abrigo Prolongado e Disciplina Tática",
            category = "WAR_CONFLICT",
            pageNumber = 3,
            contentText = "DIRETRIZ OFICIAL (DEFESA CIVIL / CICV): Em situações de colapso da ordem pública, toque de recolher ou conflito prolongado onde permanecer em casa (Stay-In / Shelter-in-Place) é mais seguro do que sair às ruas: " +
                "1) DISCIPLINA DE LUZ, SOM E CHEIRO: À noite, cubra as janelas com papelão escuro ou cobertores grossos para que nenhuma fresta de luz de lanterna ou vela seja vista da rua (Blackout visual). Use apenas luz vermelha ou fraca no interior. Ao cozinhar alimentos muito aromáticos em crise de abastecimento, faça-o nos horários de vento ou em cômodo ventilado internamente. " +
                "2) CÔMODO DE SEGURANÇA FAMILIAR: Defina um quarto interno trancável onde ficam concentrados os galões de água, o kit de primeiros socorros, documentos, extintor e as Mochilas 72h prontas caso seja necessário abandonar o imóvel em menos de 60 segundos. " +
                "3) REFORÇO DE ACESSO: Mantenha portas trancadas com trincos internos ou cunhas de madeira sob a porta e estabeleça turnos de vigília entre os adultos da família durante a noite."
        ),
        ManualChunkFtsEntity(
            rowId = 30,
            docTitle = "OMS, UNICEF & Cruz Vermelha: Primeiros Socorros Psicológicos e Proteção de Crianças em Crises e Guerras",
            category = "GENERAL",
            pageNumber = 2,
            contentText = "DIRETRIZ OFICIAL (OMS / UNICEF / CRUZ VERMELHA): O pânico e o choque emocional paralisam a tomada de decisão e colocam toda a família em risco: " +
                "1) CONTROLE DO PÂNICO AGUDO (RESPIRAÇÃO TÁTICA 4-4-4-4): Se alguém da família entrar em hiperventilação ou crise de pânico durante um bombardeio ou desastre, segure as mãos da pessoa, olhe nos olhos e guie a respiração: inspire pelo nariz contando até 4, segure o ar por 4 segundos, solte pela boca contando até 4 e aguarde 4 segundos. " +
                "2) MANEJO DE CRIANÇAS EM CATÁSTROFES: Crianças espelham a reação emocional dos pais. Fale em tom calmo, baixo e firme. Dê pequenas tarefas úteis à criança (como segurar a lanterna, cuidar de um bichinho de pelúcia ou contar os passos) — ter uma função ativa reduz drasticamente o trauma psicológico infantil. " +
                "3) IDENTIFICAÇÃO DE EMERGÊNCIA NAS CRIANÇAS: Antes de qualquer evacuação, escreva com caneta permanente no antebraço da criança (ou coloque pulseira impermeável no pulso/dentro do bolso) o nome completo dela, tipo sanguíneo, alergias, nome dos pais e o endereço do Ponto de Encontro Familiar."
        )
    )
}
