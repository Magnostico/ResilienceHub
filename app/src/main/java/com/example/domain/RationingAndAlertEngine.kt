package com.example.domain

import com.example.data.entity.FamilyProfileEntity
import com.example.data.entity.InventoryItemEntity
import com.example.data.entity.MapPoiEntity
import com.example.data.model.FamilyRole
import com.example.data.model.ItemAlertStatus
import com.example.data.model.ItemCategory
import com.example.data.model.StorageLocation
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Motor central de lógica civil de sobrevivência:
 * - Cálculo de autonomia e racionamento hídrico familiar (Apartamento e Mochila 72h).
 * - Monitoramento FEFO (First-Expired, First-Out) e ciclos de recarga de baterias (90 dias).
 * - Navegação matemática offline via Haversine e Azimute/Cardeais sem uso de internet.
 */
class RationingAndAlertEngine {

    companion object {
        const val WATER_RATE_APARTMENT_LITERS_PER_PERSON_DAY = 2.5
        const val WATER_RATE_BUGOUT_LITERS_PER_PERSON_DAY = 2.0
        const val BATTERY_MAINTENANCE_INTERVAL_DAYS = 90L
        const val EARTH_RADIUS_METERS = 6371000.0
    }

    data class WaterAutonomyReport(
        val apartmentStockLiters: Double,
        val apartmentDailyConsumptionLiters: Double,
        val apartmentAutonomyDays: Double,
        val bugoutStockLiters: Double,
        val bugoutDailyConsumptionLiters: Double,
        val bugoutAutonomyHours: Double,
        val bugoutAutonomyDays: Double,
        val bugoutTarget72hLiters: Double,
        val bugoutIs72hReady: Boolean,
        val adultsCount: Int,
        val childrenCount: Int
    )

    data class ItemEvaluationResult(
        val item: InventoryItemEntity,
        val status: ItemAlertStatus,
        val daysUntilExpiration: Double?,
        val daysSinceMaintenance: Double?,
        val alertMessage: String,
        val protocolRuleSummary: String = ""
    )

    data class PoiNavigationInfo(
        val poiId: Long,
        val poiTitle: String,
        val poiType: String,
        val address: String,
        val phone: String?,
        val distanceMeters: Double,
        val distanceFormatted: String,
        val bearingDegrees: Double,
        val cardinalCode: String,      // N, S, L, O, NE, SE, SO, NO
        val cardinalDetailed: String   // "N (Norte)", "L (Leste)", etc.
    )

    // =========================================================================
    // 1. CÁLCULOS DE AUTONOMIA E RACIONAMENTO HÍDRICO FAMILIAR
    // =========================================================================

    /**
     * Calcula a autonomia em dias para estoque do Apartamento.
     * Taxa civil recomendada: 2.5L / pessoa / dia.
     */
    fun calculateApartmentWaterAutonomyDays(
        waterStockLiters: Double,
        adultsCount: Int = 2,
        childrenCount: Int = 1
    ): Double {
        val peopleCount = (adultsCount + childrenCount).coerceAtLeast(1)
        val dailyNeed = peopleCount * WATER_RATE_APARTMENT_LITERS_PER_PERSON_DAY
        return if (dailyNeed > 0) waterStockLiters / dailyNeed else 0.0
    }

    /**
     * Calcula a autonomia em dias e horas para a Mochila 72h (Evacuação).
     * Taxa de racionamento dinâmico: 2.0L / pessoa / dia.
     */
    fun calculateBugoutWaterAutonomyDays(
        waterStockLiters: Double,
        adultsCount: Int = 2,
        childrenCount: Int = 1
    ): Double {
        val peopleCount = (adultsCount + childrenCount).coerceAtLeast(1)
        val dailyNeed = peopleCount * WATER_RATE_BUGOUT_LITERS_PER_PERSON_DAY
        return if (dailyNeed > 0) waterStockLiters / dailyNeed else 0.0
    }

    /**
     * Gera relatório completo de autonomia hídrica com base no estoque real e membros cadastrados.
     */
    fun generateWaterReport(
        items: List<InventoryItemEntity>,
        profiles: List<FamilyProfileEntity>
    ): WaterAutonomyReport {
        val adults = profiles.count { it.role == FamilyRole.ADULT }.coerceAtLeast(if (profiles.isEmpty()) 2 else 0)
        val children = profiles.count { it.role == FamilyRole.CHILD }.coerceAtLeast(if (profiles.isEmpty()) 1 else 0)
        val totalPeople = (adults + children).coerceAtLeast(1)

        val apartmentWaterLiters = items
            .filter { it.category == ItemCategory.WATER && it.location == StorageLocation.APARTMENT }
            .filter { it.unit.equals("L", ignoreCase = true) || it.unit.contains("litro", ignoreCase = true) }
            .sumOf { it.quantity }

        val bugoutWaterLiters = items
            .filter { it.category == ItemCategory.WATER && it.location == StorageLocation.BUGOUT_BAG }
            .filter { it.unit.equals("L", ignoreCase = true) || it.unit.contains("litro", ignoreCase = true) }
            .sumOf { it.quantity }

        val apartmentDailyNeed = totalPeople * WATER_RATE_APARTMENT_LITERS_PER_PERSON_DAY
        val bugoutDailyNeed = totalPeople * WATER_RATE_BUGOUT_LITERS_PER_PERSON_DAY

        val apartmentDays = if (apartmentDailyNeed > 0) apartmentWaterLiters / apartmentDailyNeed else 0.0
        val bugoutDays = if (bugoutDailyNeed > 0) bugoutWaterLiters / bugoutDailyNeed else 0.0
        val bugoutHours = bugoutDays * 24.0

        // Meta para 72 horas (3 dias de autonomia)
        val target72hLiters = bugoutDailyNeed * 3.0
        val is72hReady = bugoutWaterLiters >= target72hLiters

        return WaterAutonomyReport(
            apartmentStockLiters = apartmentWaterLiters,
            apartmentDailyConsumptionLiters = apartmentDailyNeed,
            apartmentAutonomyDays = apartmentDays,
            bugoutStockLiters = bugoutWaterLiters,
            bugoutDailyConsumptionLiters = bugoutDailyNeed,
            bugoutAutonomyHours = bugoutHours,
            bugoutAutonomyDays = bugoutDays,
            bugoutTarget72hLiters = target72hLiters,
            bugoutIs72hReady = is72hReady,
            adultsCount = adults,
            childrenCount = children
        )
    }

    // =========================================================================
    // 2. REGRAS DE VALIDADE E CICLOS DE RECARGA (FEFO & BATERIAS)
    // =========================================================================

    /**
     * Retorna a diretriz oficial de sobrevivência monitorada automaticamente para cada categoria.
     */
    fun getCategorySurvivalProtocolRule(category: ItemCategory): String {
        return when (category) {
            ItemCategory.WATER ->
                "Protocolo OMS & Defesa Civil (Água): Giro FEFO aos 90d/30d; reserva 2,5L/dia (Apto) e 2L/dia (Mochila 72h); se vencida, ferver 1 min ou 2 gotas hipoclorito 2,5%/L."
            ItemCategory.FOOD ->
                "Protocolo FEMA & ANVISA (Alimentos): Giro FEFO 90d/30d; inspeção rigorosa contra latas estufadas/botulismo e proteção hermética."
            ItemCategory.MEDS ->
                "Protocolo Min. Saúde & Cruz Vermelha (Farmácia): Alerta rigoroso 90d/30d; proibição de antibióticos/colírios vencidos e revisão de receitas contínuas."
            ItemCategory.ENERGY_GEAR ->
                "Protocolo FEMA & Bombeiros (Energia/Rádio): Ciclo obrigatório de teste e recarga a cada 90 dias; guardar pilhas fora do aparelho contra vazamento."
            ItemCategory.HYGIENE_DOCS ->
                "Protocolo Defesa Civil & OMS (Higiene/Docs): Revisão 90d/30d de químicos sanitários (cloro/álcool perdem força) e vedação impermeável de documentos."
        }
    }

    /**
     * Avalia o status crítico do item automaticamente com base na sua CATEGORIA e nos
     * Protocolos Oficiais de Sobrevivência (OMS, Defesa Civil, Ministério da Saúde, ANVISA, FEMA, Cruz Vermelha).
     */
    fun evaluateItemStatus(
        item: InventoryItemEntity,
        nowMillis: Long = System.currentTimeMillis()
    ): ItemEvaluationResult {
        val dayMillis = 24.0 * 3600 * 1000
        val protocolRule = getCategorySurvivalProtocolRule(item.category)

        var daysUntilExpiration: Double? = null
        if (item.expirationDate != null) {
            daysUntilExpiration = (item.expirationDate - nowMillis) / dayMillis
        }

        var daysSinceMaintenance: Double? = null
        if (item.lastMaintenanceDate != null) {
            daysSinceMaintenance = (nowMillis - item.lastMaintenanceDate) / dayMillis
        }

        // 1. Regra automática da categoria ENERGY_GEAR (Protocolo FEMA / Bombeiros: ciclo de 90 dias)
        if (item.category == ItemCategory.ENERGY_GEAR) {
            if (item.lastMaintenanceDate == null || (daysSinceMaintenance ?: 0.0) >= BATTERY_MAINTENANCE_INTERVAL_DAYS) {
                val dias = daysSinceMaintenance?.roundToInt() ?: 90
                return ItemEvaluationResult(
                    item = item,
                    status = ItemAlertStatus.RECARREGAR_BATERIA,
                    daysUntilExpiration = daysUntilExpiration,
                    daysSinceMaintenance = daysSinceMaintenance,
                    alertMessage = "🔋 Protocolo FEMA/Bombeiros (Energia): Sem ciclo de recarga/teste há $dias dias (limite: 90d). Recarregue baterias a 100%, teste lanterna/rádio e retire pilhas alcalinas do compartimento.",
                    protocolRuleSummary = protocolRule
                )
            }
        }

        // 2. Regras automáticas de validade FEFO específicas por Categoria segundo os Protocolos Oficiais
        if (daysUntilExpiration != null) {
            if (daysUntilExpiration < 0) {
                val diasVencido = (-daysUntilExpiration).roundToInt()
                val categoryExpiredMsg = when (item.category) {
                    ItemCategory.WATER ->
                        "⚠️ Protocolo OMS/Min. Saúde (Água): Vencida há $diasVencido dias! Não beba direto: ferva por 1 min ou trate com 2 gotas de Hipoclorito 2,5%/L (30 min) e renove o estoque."
                    ItemCategory.FOOD ->
                        "⚠️ Protocolo ANVISA/FEMA (Alimentos): Vencido há $diasVencido dias! Risco de contaminação/botulismo. Descarte se houver lata estufada, ferrugem ou odor alterado e substitua."
                    ItemCategory.MEDS ->
                        "⚠️ Protocolo ANVISA/Min. Saúde (Farmácia): Medicamento/insumo vencido há $diasVencido dias! Perda de eficácia terapêutica e risco químico. Substitua imediatamente."
                    ItemCategory.ENERGY_GEAR ->
                        "⚠️ Protocolo FEMA (Energia): Pilha/Bateria química vencida há $diasVencido dias! Risco de vazamento ácido nos contatos. Descarte e substitua."
                    ItemCategory.HYGIENE_DOCS ->
                        "⚠️ Protocolo Defesa Civil/OMS (Higiene & Docs): Expirado há $diasVencido dias! Renove a documentação/receita ou substitua cloro/álcool 70% (perda de princípio ativo)."
                }
                return ItemEvaluationResult(
                    item = item,
                    status = ItemAlertStatus.VENCIDO,
                    daysUntilExpiration = daysUntilExpiration,
                    daysSinceMaintenance = daysSinceMaintenance,
                    alertMessage = categoryExpiredMsg,
                    protocolRuleSummary = protocolRule
                )
            }

            if (daysUntilExpiration <= 30.0) {
                val diasRestantes = daysUntilExpiration.roundToInt()
                val categoryCriticalMsg = when (item.category) {
                    ItemCategory.WATER ->
                        "🚨 Protocolo Defesa Civil (Água): Validade crítica ($diasRestantes dias)! Consuma esta água na rotina doméstica agora e abasteça galões novos lacrados."
                    ItemCategory.FOOD ->
                        "🚨 Protocolo FEFO (Alimentos): Validade crítica ($diasRestantes dias)! Priorize no cardápio da casa esta semana e reponha com lote novo."
                    ItemCategory.MEDS ->
                        "🚨 Protocolo Min. Saúde (Farmácia): Validade crítica ($diasRestantes dias)! Providencie receita e reposição imediata para não faltar em emergência."
                    ItemCategory.ENERGY_GEAR ->
                        "🚨 Protocolo FEMA (Energia): Validade química crítica ($diasRestantes dias)! Utilize na rotina e compre pilhas/baterias novas."
                    ItemCategory.HYGIENE_DOCS ->
                        "🚨 Protocolo Defesa Civil (Higiene & Docs): Vence em $diasRestantes dias! Providencie renovação documental ou reposição de itens sanitários."
                }
                return ItemEvaluationResult(
                    item = item,
                    status = ItemAlertStatus.CRITICO_30_DIAS,
                    daysUntilExpiration = daysUntilExpiration,
                    daysSinceMaintenance = daysSinceMaintenance,
                    alertMessage = categoryCriticalMsg,
                    protocolRuleSummary = protocolRule
                )
            }

            if (daysUntilExpiration <= 90.0) {
                val diasRestantes = daysUntilExpiration.roundToInt()
                val categoryRotateMsg = when (item.category) {
                    ItemCategory.WATER ->
                        "🔄 Giro Hídrico FEFO (OMS): Vence em $diasRestantes dias. Use na rotina da casa e reponha (manter no escuro e longe do chão)."
                    ItemCategory.FOOD ->
                        "🔄 Giro Alimentar FEFO (Defesa Civil): Vence em $diasRestantes dias. Consuma na rotina doméstica e reabasteça o estoque de crise."
                    ItemCategory.MEDS ->
                        "🔄 Revisão de Farmácia (Cruz Vermelha): Vence em $diasRestantes dias. Planeje a substituição preventiva no kit de trauma/uso contínuo."
                    ItemCategory.ENERGY_GEAR ->
                        "🔄 Giro de Energia (FEMA): Vence em $diasRestantes dias. Teste a carga e programe reposição preventiva."
                    ItemCategory.HYGIENE_DOCS ->
                        "🔄 Revisão de Higiene & Docs (Defesa Civil): Vence em $diasRestantes dias. Confira vedação estanque (zip-lock) e programe renovação."
                }
                return ItemEvaluationResult(
                    item = item,
                    status = ItemAlertStatus.GIRAR_90_DIAS,
                    daysUntilExpiration = daysUntilExpiration,
                    daysSinceMaintenance = daysSinceMaintenance,
                    alertMessage = categoryRotateMsg,
                    protocolRuleSummary = protocolRule
                )
            }
        }

        val okMsg = when (item.category) {
            ItemCategory.ENERGY_GEAR -> {
                val diasRestantesCarga = (BATTERY_MAINTENANCE_INTERVAL_DAYS - (daysSinceMaintenance?.roundToInt() ?: 0)).coerceAtLeast(0)
                "✓ Protocolo FEMA/Bombeiros em dia: Próximo ciclo obrigatório de recarga em $diasRestantesCarga dias."
            }
            else -> "✓ Em conformidade com o protocolo oficial da categoria (${item.category.displayName})."
        }

        return ItemEvaluationResult(
            item = item,
            status = ItemAlertStatus.OK,
            daysUntilExpiration = daysUntilExpiration,
            daysSinceMaintenance = daysSinceMaintenance,
            alertMessage = okMsg,
            protocolRuleSummary = protocolRule
        )
    }

    // =========================================================================
    // 3. FÓRMULA DE HAVERSINE E NAVEGAÇÃO CARDEAL OFFLINE
    // =========================================================================

    /**
     * Calcula a distância geodésica em metros entre dois pontos geográficos pela fórmula de Haversine.
     * 100% autônomo e sem conexão de rede.
     */
    fun haversineDistanceMeters(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)

        val lat1Rad = Math.toRadians(lat1)
        val lat2Rad = Math.toRadians(lat2)

        val a = sin(dLat / 2.0).pow(2.0) +
                cos(lat1Rad) * cos(lat2Rad) * sin(dLon / 2.0).pow(2.0)

        val c = 2.0 * atan2(sqrt(a), sqrt(1.0 - a))
        return EARTH_RADIUS_METERS * c
    }

    /**
     * Calcula o azimute inicial (bearing) em graus (0° a 359.9°) do ponto de origem para o destino.
     */
    fun calculateBearingDegrees(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {
        val lat1Rad = Math.toRadians(lat1)
        val lat2Rad = Math.toRadians(lat2)
        val dLon = Math.toRadians(lon2 - lon1)

        val y = sin(dLon) * cos(lat2Rad)
        val x = cos(lat1Rad) * sin(lat2Rad) - sin(lat1Rad) * cos(lat2Rad) * cos(dLon)

        val bearingRad = atan2(y, x)
        return (Math.toDegrees(bearingRad) + 360.0) % 360.0
    }

    /**
     * Converte o ângulo de azimute para a direção cardeal e colateral em Português (N, S, L, O, etc).
     */
    fun bearingToCardinal(bearingDegrees: Double): Pair<String, String> {
        val normalized = (bearingDegrees % 360.0 + 360.0) % 360.0
        return when {
            normalized >= 337.5 || normalized < 22.5 -> Pair("N", "N (Norte)")
            normalized in 22.5..67.5 -> Pair("NE", "NE (Nordeste)")
            normalized in 67.5..112.5 -> Pair("L", "L (Leste)")
            normalized in 112.5..157.5 -> Pair("SE", "SE (Sudeste)")
            normalized in 157.5..202.5 -> Pair("S", "S (Sul)")
            normalized in 202.5..247.5 -> Pair("SO", "SO (Sudoeste)")
            normalized in 247.5..292.5 -> Pair("O", "O (Oeste)")
            else -> Pair("NO", "NO (Noroeste)")
        }
    }

    /**
     * Formata a distância de forma legível (metros ou quilômetros).
     */
    fun formatDistance(meters: Double): String {
        return if (meters < 1000.0) {
            "${meters.roundToInt()} m"
        } else {
            String.format(Locale.US, "%.2f km", meters / 1000.0)
        }
    }

    /**
     * Gera os dados de navegação completos entre a posição do usuário e um Ponto de Interesse/Encontro.
     */
    fun calculatePoiNavigation(
        currentLat: Double,
        currentLon: Double,
        poi: MapPoiEntity
    ): PoiNavigationInfo {
        val distance = haversineDistanceMeters(currentLat, currentLon, poi.latitude, poi.longitude)
        val bearing = calculateBearingDegrees(currentLat, currentLon, poi.latitude, poi.longitude)
        val (cardinalCode, cardinalDetailed) = bearingToCardinal(bearing)

        return PoiNavigationInfo(
            poiId = poi.id,
            poiTitle = poi.title,
            poiType = poi.type.displayName,
            address = poi.address,
            phone = poi.phone,
            distanceMeters = distance,
            distanceFormatted = formatDistance(distance),
            bearingDegrees = bearing,
            cardinalCode = cardinalCode,
            cardinalDetailed = cardinalDetailed
        )
    }
}
