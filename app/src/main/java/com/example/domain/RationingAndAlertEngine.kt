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
        val alertMessage: String
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
     * Avalia o status crítico do item de acordo com a política FEFO e ciclos de 90 dias de bateria.
     * Regras:
     * - ENERGY_GEAR sem manutenção há >= 90 dias -> RECARREGAR_BATERIA
     * - Vencido (< 0 dias) -> VENCIDO
     * - Validade <= 30 dias -> CRITICO_30_DIAS
     * - Validade <= 90 dias -> GIRAR_90_DIAS (consumir em casa e repor)
     * - Demais casos -> OK
     */
    fun evaluateItemStatus(
        item: InventoryItemEntity,
        nowMillis: Long = System.currentTimeMillis()
    ): ItemEvaluationResult {
        val dayMillis = 24.0 * 3600 * 1000

        var daysUntilExpiration: Double? = null
        if (item.expirationDate != null) {
            daysUntilExpiration = (item.expirationDate - nowMillis) / dayMillis
        }

        var daysSinceMaintenance: Double? = null
        if (item.lastMaintenanceDate != null) {
            daysSinceMaintenance = (nowMillis - item.lastMaintenanceDate) / dayMillis
        }

        // 1. Regra de ciclo de bateria/equipamento
        if (item.category == ItemCategory.ENERGY_GEAR) {
            if (item.lastMaintenanceDate == null || (daysSinceMaintenance ?: 0.0) >= BATTERY_MAINTENANCE_INTERVAL_DAYS) {
                val dias = daysSinceMaintenance?.roundToInt() ?: 90
                return ItemEvaluationResult(
                    item = item,
                    status = ItemAlertStatus.RECARREGAR_BATERIA,
                    daysUntilExpiration = daysUntilExpiration,
                    daysSinceMaintenance = daysSinceMaintenance,
                    alertMessage = "Bateria/Equipamento sem ciclo de recarga há $dias dias (meta: máximo 90 dias)."
                )
            }
        }

        // 2. Regra de validade cronológica FEFO
        if (daysUntilExpiration != null) {
            if (daysUntilExpiration < 0) {
                val diasVencido = (-daysUntilExpiration).roundToInt()
                return ItemEvaluationResult(
                    item = item,
                    status = ItemAlertStatus.VENCIDO,
                    daysUntilExpiration = daysUntilExpiration,
                    daysSinceMaintenance = daysSinceMaintenance,
                    alertMessage = "Item vencido há $diasVencido dias. Descarte ou substitua imediatamente."
                )
            }

            if (daysUntilExpiration <= 30.0) {
                val diasRestantes = daysUntilExpiration.roundToInt()
                return ItemEvaluationResult(
                    item = item,
                    status = ItemAlertStatus.CRITICO_30_DIAS,
                    daysUntilExpiration = daysUntilExpiration,
                    daysSinceMaintenance = daysSinceMaintenance,
                    alertMessage = "Validade crítica! Restam apenas $diasRestantes dias para vencer."
                )
            }

            if (daysUntilExpiration <= 90.0) {
                val diasRestantes = daysUntilExpiration.roundToInt()
                return ItemEvaluationResult(
                    item = item,
                    status = ItemAlertStatus.GIRAR_90_DIAS,
                    daysUntilExpiration = daysUntilExpiration,
                    daysSinceMaintenance = daysSinceMaintenance,
                    alertMessage = "Girar estoque: Vence em $diasRestantes dias. Consuma na rotina doméstica e reabasteça."
                )
            }
        }

        return ItemEvaluationResult(
            item = item,
            status = ItemAlertStatus.OK,
            daysUntilExpiration = daysUntilExpiration,
            daysSinceMaintenance = daysSinceMaintenance,
            alertMessage = "Condição operacional e prazo dentro da margem de segurança."
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
