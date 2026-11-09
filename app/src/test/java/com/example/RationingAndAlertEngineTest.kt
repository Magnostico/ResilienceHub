package com.example

import com.example.data.entity.FamilyProfileEntity
import com.example.data.entity.InventoryItemEntity
import com.example.data.entity.MapPoiEntity
import com.example.data.model.FamilyRole
import com.example.data.model.ItemAlertStatus
import com.example.data.model.ItemCategory
import com.example.data.model.PoiType
import com.example.data.model.StorageLocation
import com.example.domain.RationingAndAlertEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RationingAndAlertEngineTest {

    private lateinit var engine: RationingAndAlertEngine

    @Before
    fun setUp() {
        engine = RationingAndAlertEngine()
    }

    @Test
    fun testWaterAutonomyApartment_twoAdultsOneChild() {
        // 2 adults + 1 child = 3 people
        // Apartment rate: 2.5 L/person/day -> 3 * 2.5 = 7.5 L/day
        // 60 L stock -> 60 / 7.5 = 8.0 days
        val days = engine.calculateApartmentWaterAutonomyDays(
            waterStockLiters = 60.0,
            adultsCount = 2,
            childrenCount = 1
        )
        assertEquals(8.0, days, 0.001)
    }

    @Test
    fun testWaterAutonomyBugout_twoAdultsOneChild() {
        // 2 adults + 1 child = 3 people
        // Bugout rate: 2.0 L/person/day -> 3 * 2.0 = 6.0 L/day
        // 12 L stock -> 12 / 6.0 = 2.0 days (48 hours)
        val days = engine.calculateBugoutWaterAutonomyDays(
            waterStockLiters = 12.0,
            adultsCount = 2,
            childrenCount = 1
        )
        assertEquals(2.0, days, 0.001)
    }

    @Test
    fun testAlertRule_energyGearMaintenanceAfter90Days() {
        val now = System.currentTimeMillis()
        val dayMillis = 24L * 3600 * 1000

        val itemOutdated = InventoryItemEntity(
            id = 1,
            name = "Power Bank 20.000mAh",
            category = ItemCategory.ENERGY_GEAR,
            location = StorageLocation.APARTMENT,
            quantity = 1.0,
            unit = "unidade",
            expirationDate = null,
            lastMaintenanceDate = now - (95L * dayMillis) // 95 days ago -> >= 90 days!
        )

        val result = engine.evaluateItemStatus(itemOutdated, now)
        assertEquals(ItemAlertStatus.RECARREGAR_BATERIA, result.status)
    }

    @Test
    fun testAlertRule_fefoExpirationStates() {
        val now = System.currentTimeMillis()
        val dayMillis = 24L * 3600 * 1000

        // 1. Vencido (-5 days)
        val itemExpired = InventoryItemEntity(
            id = 1,
            name = "Leite em Pó",
            category = ItemCategory.FOOD,
            location = StorageLocation.APARTMENT,
            quantity = 2.0,
            unit = "latas",
            expirationDate = now - (5L * dayMillis)
        )
        assertEquals(ItemAlertStatus.VENCIDO, engine.evaluateItemStatus(itemExpired, now).status)

        // 2. Crítico (20 days <= 30)
        val itemCritical = InventoryItemEntity(
            id = 2,
            name = "Sardinha em Conserva",
            category = ItemCategory.FOOD,
            location = StorageLocation.APARTMENT,
            quantity = 4.0,
            unit = "latas",
            expirationDate = now + (20L * dayMillis)
        )
        assertEquals(ItemAlertStatus.CRITICO_30_DIAS, engine.evaluateItemStatus(itemCritical, now).status)

        // 3. Girar Estoque (60 days <= 90)
        val itemRotate = InventoryItemEntity(
            id = 3,
            name = "Atum em Óleo",
            category = ItemCategory.FOOD,
            location = StorageLocation.APARTMENT,
            quantity = 10.0,
            unit = "latas",
            expirationDate = now + (60L * dayMillis)
        )
        assertEquals(ItemAlertStatus.GIRAR_90_DIAS, engine.evaluateItemStatus(itemRotate, now).status)

        // 4. OK (300 days)
        val itemOk = InventoryItemEntity(
            id = 4,
            name = "Feijão Preto a Vácuo",
            category = ItemCategory.FOOD,
            location = StorageLocation.APARTMENT,
            quantity = 5.0,
            unit = "kg",
            expirationDate = now + (300L * dayMillis)
        )
        assertEquals(ItemAlertStatus.OK, engine.evaluateItemStatus(itemOk, now).status)
    }

    @Test
    fun testHaversineDistanceAndBearingOffline() {
        // Test user in Copacabana/Botafogo (-22.9519, -43.1850)
        // Target: Parque Lage (-22.9599, -43.2125)
        val poi = MapPoiEntity(
            id = 1,
            title = "Ponto de Encontro Familiar Primário - Parque Lage",
            type = PoiType.FAMILY_MEETING,
            latitude = -22.9599,
            longitude = -43.2125,
            address = "Rua Jardim Botânico, 414",
            phone = null
        )

        val nav = engine.calculatePoiNavigation(-22.9519, -43.1850, poi)

        // Distance should be around 2.9 to 3.0 km
        assertTrue("Distance should be around 2.9km but was ${nav.distanceMeters}", nav.distanceMeters in 2500.0..3500.0)
        assertTrue(nav.cardinalDetailed.isNotEmpty())
        assertTrue(nav.cardinalCode in listOf("N", "NE", "L", "SE", "S", "SO", "O", "NO"))
    }
}
