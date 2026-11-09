package com.example.data.model

enum class ItemCategory(val displayName: String) {
    WATER("Água e Purificação"),
    FOOD("Alimentos e Ração"),
    MEDS("Medicamentos e Trauma"),
    ENERGY_GEAR("Energia, Luz e Rádio"),
    HYGIENE_DOCS("Higiene e Documentos")
}

enum class StorageLocation(val displayName: String) {
    APARTMENT("Apartamento (Estoque Base)"),
    BUGOUT_BAG("Mochila 72h (Evacuação)")
}

enum class PoiType(val displayName: String) {
    FAMILY_MEETING("Ponto de Encontro Familiar"),
    HOSPITAL("Hospital / Pronto-Socorro"),
    FIRE_DEPT("Corpo de Bombeiros"),
    SHELTER("Abrigo / Defesa Civil")
}

enum class FamilyRole(val displayName: String) {
    ADULT("Adulto"),
    CHILD("Criança")
}

enum class ItemAlertStatus(
    val code: String,
    val title: String,
    val description: String,
    val severityLevel: Int // 0: Normal, 1: Atenção, 2: Urgente, 3: Crítico
) {
    OK("OK", "Em Condição Normal", "Item dentro do prazo seguro de validade e manutenção.", 0),
    GIRAR_90_DIAS("GIRAR_90_DIAS", "Girar Estoque (<= 90 Dias)", "Consumir na rotina doméstica da casa e repor no estoque de crise.", 1),
    CRITICO_30_DIAS("CRITICO_30_DIAS", "Validade Crítica (<= 30 Dias)", "Validade iminente! Risco de perda de utilidade.", 2),
    VENCIDO("VENCIDO", "Item Vencido", "Item ultrapassou a data de validade estipulada pelo fabricante.", 3),
    RECARREGAR_BATERIA("RECARREGAR_BATERIA", "Ciclo de Carga Expirado", "Equipamento eletrônico/bateria sem ciclo de manutenção há 90 dias ou mais.", 2)
}
