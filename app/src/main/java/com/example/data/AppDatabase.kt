package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.dao.FamilyProfileDao
import com.example.data.dao.InventoryDao
import com.example.data.dao.ManualChunkDao
import com.example.data.dao.MapPoiDao
import com.example.data.entity.FamilyProfileEntity
import com.example.data.entity.InventoryItemEntity
import com.example.data.entity.ManualChunkFtsEntity
import com.example.data.entity.MapPoiEntity
import com.example.data.model.FamilyRole
import com.example.data.model.ItemCategory
import com.example.data.model.PoiType
import com.example.data.model.StorageLocation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        InventoryItemEntity::class,
        ManualChunkFtsEntity::class,
        MapPoiEntity::class,
        FamilyProfileEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun inventoryDao(): InventoryDao
    abstract fun manualChunkDao(): ManualChunkDao
    abstract fun mapPoiDao(): MapPoiDao
    abstract fun familyProfileDao(): FamilyProfileDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "resilience_hub.db"
                )
                    .addCallback(DatabaseCallback())
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                val database = INSTANCE ?: return
                CoroutineScope(Dispatchers.IO).launch {
                    populateInitialData(database)
                }
            }
        }

        suspend fun populateInitialData(database: AppDatabase) {
            val now = System.currentTimeMillis()
            val dayMillis = 24L * 3600 * 1000

            // 1. Initial Family Profiles (2 Adults + 1 Child)
            val initialProfiles = listOf(
                FamilyProfileEntity(
                    id = 1,
                    name = "Carlos Silva",
                    role = FamilyRole.ADULT,
                    weightKg = 78.0,
                    bloodType = "O+",
                    allergies = "Penicilina",
                    medications = "Losartana 50mg (Uso contínuo)"
                ),
                FamilyProfileEntity(
                    id = 2,
                    name = "Mariana Silva",
                    role = FamilyRole.ADULT,
                    weightKg = 62.0,
                    bloodType = "A+",
                    allergies = "Nenhuma declarada",
                    medications = "Nenhuma"
                ),
                FamilyProfileEntity(
                    id = 3,
                    name = "Lucas Silva",
                    role = FamilyRole.CHILD,
                    weightKg = 24.0,
                    bloodType = "O+",
                    allergies = "Frutos do mar e picada de abelha",
                    medications = "Anti-histamínico infantil de emergência"
                )
            )
            database.familyProfileDao().insertAll(initialProfiles)

            // 2. Initial Inventory Items (Apartment Base Stock & 72h Bugout Bag)
            val initialInventory = listOf(
                // --- APARTMENT: Água ---
                InventoryItemEntity(
                    id = 1,
                    name = "Galão de Água Mineral 20L (Estoque Base)",
                    barcode = "7891000100011",
                    category = ItemCategory.WATER,
                    location = StorageLocation.APARTMENT,
                    quantity = 60.0,
                    unit = "L",
                    expirationDate = now + (180L * dayMillis),
                    lastMaintenanceDate = null,
                    alertDaysBefore = 90
                ),
                InventoryItemEntity(
                    id = 2,
                    name = "Pastilhas Clorin 10.000mg Purificador de Água",
                    barcode = "7891000100028",
                    category = ItemCategory.WATER,
                    location = StorageLocation.APARTMENT,
                    quantity = 50.0,
                    unit = "tabletes",
                    expirationDate = now + (730L * dayMillis),
                    lastMaintenanceDate = null,
                    alertDaysBefore = 90
                ),
                InventoryItemEntity(
                    id = 3,
                    name = "Filtro Cerâmico de Gravidade c/ Vela de Carvão",
                    barcode = "7891000100035",
                    category = ItemCategory.WATER,
                    location = StorageLocation.APARTMENT,
                    quantity = 1.0,
                    unit = "unidade",
                    expirationDate = null,
                    lastMaintenanceDate = null,
                    alertDaysBefore = 90
                ),

                // --- APARTMENT: Energia & Equipamentos ---
                InventoryItemEntity(
                    id = 4,
                    name = "Power Bank Solar 20.000 mAh Saída 20W",
                    barcode = "7891000100042",
                    category = ItemCategory.ENERGY_GEAR,
                    location = StorageLocation.APARTMENT,
                    quantity = 1.0,
                    unit = "unidade",
                    expirationDate = null,
                    lastMaintenanceDate = now - (75L * dayMillis), // Ciclo se aproximando de 90 dias
                    alertDaysBefore = 90
                ),
                InventoryItemEntity(
                    id = 5,
                    name = "Rádio AM/FM/SW Emergência c/ Dínamo e Painel Solar",
                    barcode = "7891000100059",
                    category = ItemCategory.ENERGY_GEAR,
                    location = StorageLocation.APARTMENT,
                    quantity = 1.0,
                    unit = "unidade",
                    expirationDate = null,
                    lastMaintenanceDate = now - (85L * dayMillis),
                    alertDaysBefore = 90
                ),
                InventoryItemEntity(
                    id = 6,
                    name = "Lanterna Tática LED 2000 Lumens Recarregável",
                    barcode = "7891000100066",
                    category = ItemCategory.ENERGY_GEAR,
                    location = StorageLocation.APARTMENT,
                    quantity = 2.0,
                    unit = "unidades",
                    expirationDate = null,
                    lastMaintenanceDate = now - (30L * dayMillis),
                    alertDaysBefore = 90
                ),
                InventoryItemEntity(
                    id = 7,
                    name = "Kit Pilhas Eneloop Pro AA/AAA Recarregáveis (8 un)",
                    barcode = "7891000100073",
                    category = ItemCategory.ENERGY_GEAR,
                    location = StorageLocation.APARTMENT,
                    quantity = 8.0,
                    unit = "unidades",
                    expirationDate = null,
                    lastMaintenanceDate = now - (95L * dayMillis), // >= 90 dias! RECARREGAR_BATERIA
                    alertDaysBefore = 90
                ),

                // --- APARTMENT: Alimentos (FEFO) ---
                InventoryItemEntity(
                    id = 8,
                    name = "Arroz Parboilizado Pacote a Vácuo 1kg",
                    barcode = "7891000100080",
                    category = ItemCategory.FOOD,
                    location = StorageLocation.APARTMENT,
                    quantity = 5.0,
                    unit = "kg",
                    expirationDate = now + (365L * dayMillis),
                    lastMaintenanceDate = null,
                    alertDaysBefore = 90
                ),
                InventoryItemEntity(
                    id = 9,
                    name = "Feijão Preto em Embalagem a Vácuo 1kg",
                    barcode = "7891000100097",
                    category = ItemCategory.FOOD,
                    location = StorageLocation.APARTMENT,
                    quantity = 4.0,
                    unit = "kg",
                    expirationDate = now + (365L * dayMillis),
                    lastMaintenanceDate = null,
                    alertDaysBefore = 90
                ),
                InventoryItemEntity(
                    id = 10,
                    name = "Atum Sólido em Óleo Lata 170g",
                    barcode = "7891000100103",
                    category = ItemCategory.FOOD,
                    location = StorageLocation.APARTMENT,
                    quantity = 12.0,
                    unit = "latas",
                    expirationDate = now + (60L * dayMillis), // <= 90 dias: GIRAR_90_DIAS
                    lastMaintenanceDate = null,
                    alertDaysBefore = 90
                ),
                InventoryItemEntity(
                    id = 11,
                    name = "Sardinha em Molho de Tomate Lata 125g",
                    barcode = "7891000100110",
                    category = ItemCategory.FOOD,
                    location = StorageLocation.APARTMENT,
                    quantity = 6.0,
                    unit = "latas",
                    expirationDate = now + (20L * dayMillis), // <= 30 dias: CRITICO_30_DIAS
                    lastMaintenanceDate = null,
                    alertDaysBefore = 90
                ),

                // --- APARTMENT: Saúde & Documentos ---
                InventoryItemEntity(
                    id = 12,
                    name = "Kit Primeiros Socorros Residencial & Curativos",
                    barcode = "7891000100127",
                    category = ItemCategory.MEDS,
                    location = StorageLocation.APARTMENT,
                    quantity = 1.0,
                    unit = "kit",
                    expirationDate = now + (450L * dayMillis),
                    lastMaintenanceDate = null,
                    alertDaysBefore = 90
                ),
                InventoryItemEntity(
                    id = 13,
                    name = "Dipirona 500mg Caixa 20 Comprimidos",
                    barcode = "7891000100134",
                    category = ItemCategory.MEDS,
                    location = StorageLocation.APARTMENT,
                    quantity = 20.0,
                    unit = "comprimidos",
                    expirationDate = now + (180L * dayMillis),
                    lastMaintenanceDate = null,
                    alertDaysBefore = 90
                ),
                InventoryItemEntity(
                    id = 14,
                    name = "Pasta de Documentos à Prova de Fogo e Água",
                    barcode = "7891000100141",
                    category = ItemCategory.HYGIENE_DOCS,
                    location = StorageLocation.APARTMENT,
                    quantity = 1.0,
                    unit = "unidade",
                    expirationDate = null,
                    lastMaintenanceDate = null,
                    alertDaysBefore = 90
                ),
                InventoryItemEntity(
                    id = 15,
                    name = "Sabão Neutro e Álcool Gel 70% 500ml",
                    barcode = "7891000100158",
                    category = ItemCategory.HYGIENE_DOCS,
                    location = StorageLocation.APARTMENT,
                    quantity = 4.0,
                    unit = "frascos",
                    expirationDate = now + (300L * dayMillis),
                    lastMaintenanceDate = null,
                    alertDaysBefore = 90
                ),

                // --- MOCHILA 72 HORAS (BUGOUT BAG) ---
                InventoryItemEntity(
                    id = 16,
                    name = "Água Mineral Selada 1.5L Mochila 72h",
                    barcode = "7891000100165",
                    category = ItemCategory.WATER,
                    location = StorageLocation.BUGOUT_BAG,
                    quantity = 12.0,
                    unit = "L",
                    expirationDate = now + (240L * dayMillis),
                    lastMaintenanceDate = null,
                    alertDaysBefore = 90
                ),
                InventoryItemEntity(
                    id = 17,
                    name = "Pastilhas Clorin 1mg (Garrafas 1L Mochila)",
                    barcode = "7891000100172",
                    category = ItemCategory.WATER,
                    location = StorageLocation.BUGOUT_BAG,
                    quantity = 30.0,
                    unit = "tabletes",
                    expirationDate = now + (500L * dayMillis),
                    lastMaintenanceDate = null,
                    alertDaysBefore = 90
                ),
                InventoryItemEntity(
                    id = 18,
                    name = "Ração Operacional Militar MRE 24h",
                    barcode = "7891000100189",
                    category = ItemCategory.FOOD,
                    location = StorageLocation.BUGOUT_BAG,
                    quantity = 6.0,
                    unit = "rações",
                    expirationDate = now + (540L * dayMillis),
                    lastMaintenanceDate = null,
                    alertDaysBefore = 90
                ),
                InventoryItemEntity(
                    id = 19,
                    name = "Barras Energéticas e de Proteína",
                    barcode = "7891000100196",
                    category = ItemCategory.FOOD,
                    location = StorageLocation.BUGOUT_BAG,
                    quantity = 15.0,
                    unit = "barras",
                    expirationDate = now + (80L * dayMillis), // <= 90 dias: GIRAR_90_DIAS
                    lastMaintenanceDate = null,
                    alertDaysBefore = 90
                ),
                InventoryItemEntity(
                    id = 20,
                    name = "Torniquete Tático de Aplicação Rápida (CAT 7th)",
                    barcode = "7891000100202",
                    category = ItemCategory.MEDS,
                    location = StorageLocation.BUGOUT_BAG,
                    quantity = 2.0,
                    unit = "unidades",
                    expirationDate = null,
                    lastMaintenanceDate = null,
                    alertDaysBefore = 90
                ),
                InventoryItemEntity(
                    id = 21,
                    name = "Bandagem de Compressão Estéril Tipo Israelense",
                    barcode = "7891000100219",
                    category = ItemCategory.MEDS,
                    location = StorageLocation.BUGOUT_BAG,
                    quantity = 2.0,
                    unit = "unidades",
                    expirationDate = now + (700L * dayMillis),
                    lastMaintenanceDate = null,
                    alertDaysBefore = 90
                ),
                InventoryItemEntity(
                    id = 22,
                    name = "Lanterna de Cabeça (Headlamp) Vermelha/Branca",
                    barcode = "7891000100226",
                    category = ItemCategory.ENERGY_GEAR,
                    location = StorageLocation.BUGOUT_BAG,
                    quantity = 2.0,
                    unit = "unidades",
                    expirationDate = null,
                    lastMaintenanceDate = now - (40L * dayMillis),
                    alertDaysBefore = 90
                ),
                InventoryItemEntity(
                    id = 23,
                    name = "Power Bank Compacto 10.000 mAh c/ Cabos Integrados",
                    barcode = "7891000100233",
                    category = ItemCategory.ENERGY_GEAR,
                    location = StorageLocation.BUGOUT_BAG,
                    quantity = 1.0,
                    unit = "unidade",
                    expirationDate = null,
                    lastMaintenanceDate = now - (20L * dayMillis),
                    alertDaysBefore = 90
                ),
                InventoryItemEntity(
                    id = 24,
                    name = "Mantas Térmicas Aluminizadas Mylar (Kit 4 un)",
                    barcode = "7891000100240",
                    category = ItemCategory.HYGIENE_DOCS,
                    location = StorageLocation.BUGOUT_BAG,
                    quantity = 4.0,
                    unit = "unidades",
                    expirationDate = null,
                    lastMaintenanceDate = null,
                    alertDaysBefore = 90
                ),
                InventoryItemEntity(
                    id = 25,
                    name = "Apito de Sobrevivência 120dB sem esfera",
                    barcode = "7891000100257",
                    category = ItemCategory.ENERGY_GEAR,
                    location = StorageLocation.BUGOUT_BAG,
                    quantity = 3.0,
                    unit = "unidades",
                    expirationDate = null,
                    lastMaintenanceDate = null,
                    alertDaysBefore = 90
                ),
                InventoryItemEntity(
                    id = 26,
                    name = "PenDrive Criptografado com Cópias de Documentos",
                    barcode = "7891000100264",
                    category = ItemCategory.HYGIENE_DOCS,
                    location = StorageLocation.BUGOUT_BAG,
                    quantity = 2.0,
                    unit = "unidades",
                    expirationDate = null,
                    lastMaintenanceDate = null,
                    alertDaysBefore = 90
                )
            )
            database.inventoryDao().insertAll(initialInventory)

            // 3. Initial Map POIs in Rio de Janeiro
            val initialPois = listOf(
                MapPoiEntity(
                    id = 1,
                    title = "Ponto de Encontro Familiar Primário - Parque Lage",
                    type = PoiType.FAMILY_MEETING,
                    latitude = -22.9599,
                    longitude = -43.2125,
                    address = "Rua Jardim Botânico, 414 - Jardim Botânico, Rio de Janeiro - RJ",
                    phone = "+55 21 3257-1340"
                ),
                MapPoiEntity(
                    id = 2,
                    title = "Ponto de Encontro Familiar Secundário - Praça Afonso Pena",
                    type = PoiType.FAMILY_MEETING,
                    latitude = -22.9184,
                    longitude = -43.2185,
                    address = "Praça Afonso Pena - Tijuca, Rio de Janeiro - RJ",
                    phone = null
                ),
                MapPoiEntity(
                    id = 3,
                    title = "Hospital Municipal Souza Aguiar",
                    type = PoiType.HOSPITAL,
                    latitude = -22.9068,
                    longitude = -43.1906,
                    address = "Praça da República, 111 - Centro, Rio de Janeiro - RJ",
                    phone = "+55 21 3111-2600"
                ),
                MapPoiEntity(
                    id = 4,
                    title = "Hospital Municipal Miguel Couto",
                    type = PoiType.HOSPITAL,
                    latitude = -22.9772,
                    longitude = -43.2241,
                    address = "R. Mário Ribeiro, 117 - Gávea, Rio de Janeiro - RJ",
                    phone = "+55 21 3111-3600"
                ),
                MapPoiEntity(
                    id = 5,
                    title = "Quartel Central do Corpo de Bombeiros Militar (CBMERJ)",
                    type = PoiType.FIRE_DEPT,
                    latitude = -22.9082,
                    longitude = -43.1901,
                    address = "Praça da República, 45 - Centro, Rio de Janeiro - RJ",
                    phone = "193"
                ),
                MapPoiEntity(
                    id = 6,
                    title = "1º Grupamento de Bombeiro Militar (1º GBM Humaitá)",
                    type = PoiType.FIRE_DEPT,
                    latitude = -22.9567,
                    longitude = -43.1994,
                    address = "R. Humaitá, 126 - Humaitá, Rio de Janeiro - RJ",
                    phone = "193"
                ),
                MapPoiEntity(
                    id = 7,
                    title = "Ponto de Triagem e Abrigo Civil - Maracanã",
                    type = PoiType.SHELTER,
                    latitude = -22.9121,
                    longitude = -43.2302,
                    address = "Av. Presidente Castelo Branco - Maracanã, Rio de Janeiro - RJ",
                    phone = "199"
                )
            )
            database.mapPoiDao().insertAll(initialPois)

            // 4. Initial FTS Survival Manual Chunks
            val initialChunks = listOf(
                ManualChunkFtsEntity(
                    rowId = 1,
                    docTitle = "Manual Defesa Civil: Tratamento de Água em Colapso",
                    category = "WATER",
                    pageNumber = 1,
                    contentText = "Em situações de colapso no abastecimento público, ferva a água vigorosamente por pelo menos 1 minuto (3 minutos em altitudes elevadas). Para purificação química com Clorin (dicloroisocianurato de sódio), adicione 1 comprimido de 1mg para cada 1 litro de água límpida e aguarde 30 minutos de repouso antes de consumir. Se a água estiver turva, filtre previamente com tecido limpo ou filtro cerâmico e dobre o tempo de repouso."
                ),
                ManualChunkFtsEntity(
                    rowId = 2,
                    docTitle = "Manual Tático de Sobrevivência: Regra dos Três",
                    category = "GENERAL",
                    pageNumber = 1,
                    contentText = "Priorização fisiológica e psicológica em emergências extremas: 3 minutos sem oxigênio ou em ambiente com gases tóxicos; 3 horas sem abrigo adequado sob condições climáticas severas (risco de hipotermia ou insolação); 3 dias sem água potável antes do colapso das funções renais; 3 semanas sem alimento se houver hidratação mínima."
                ),
                ManualChunkFtsEntity(
                    rowId = 3,
                    docTitle = "Protocolo de Medicina Tática: Torniquete CAT",
                    category = "MEDS",
                    pageNumber = 2,
                    contentText = "Em ferimentos exangues de extremidades: 1) Posicione o torniquete tático de 5 a 7 cm acima da lesão (nunca sobre articulações). 2) Puxe a fita com máxima força eliminando qualquer folga. 3) Gire a haste molinete até cessar o sangramento arterial e sumir o pulso distal. 4) Trave o molinete no clipe de retenção e anote o horário exato da aplicação na fita de registro."
                ),
                ManualChunkFtsEntity(
                    rowId = 4,
                    docTitle = "Protocolo Familiar de Evacuação 72h (Bugout)",
                    category = "EVACUATION",
                    pageNumber = 1,
                    contentText = "Ao soar alerta de evacuação da Defesa Civil: Calce calçados robustos, vista calça e manga comprida. Pegue a Mochila 72h (cada adulto carrega a sua mochila de 12L a 15L de capacidade, criança leva apenas itens leves pessoais). Desligue o disjuntor geral de energia elétrica e o registro geral de gás e água do imóvel. Dirija-se imediatamente a pé ou veículo ao Ponto de Encontro Primário."
                ),
                ManualChunkFtsEntity(
                    rowId = 5,
                    docTitle = "Diretriz de Manutenção Preventiva de Baterias e Rádio",
                    category = "ENERGY_GEAR",
                    pageNumber = 1,
                    contentText = "Baterias de lítio perdem carga residual e sofrem degradação química se mantidas zeradas ou em calor excessivo. A cada 90 dias, execute o ciclo de descarga parcial e recarga até 80-90% de todos os power banks e lanternas. Ligue o rádio AM/FM de emergência por 5 minutos para desoxidar os contatos internos do potenciômetro e verifique se as vedações de silicone mantêm impermeabilidade."
                )
            )
            database.manualChunkDao().insertAll(initialChunks)
        }
    }
}
