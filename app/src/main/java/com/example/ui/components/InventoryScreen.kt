package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ItemAlertStatus
import com.example.data.model.ItemCategory
import com.example.data.model.StorageLocation
import com.example.domain.RationingAndAlertEngine
import com.example.ui.MainUiState
import com.example.ui.MainViewModel
import com.example.ui.theme.getCrisisBorderColor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun InventoryScreen(
    state: MainUiState,
    viewModel: MainViewModel,
    onOpenAddDialog: () -> Unit,
    modifier: Modifier = Modifier
) {
    val crisisMode = state.crisisMode
    val report = state.waterReport

    var showBarcodeScannerModal by remember { mutableStateOf(false) }
    var activeBarcodeFilter by remember { mutableStateOf<String?>(null) }

    val displayedItems = remember(state.evaluatedItems, activeBarcodeFilter) {
        if (activeBarcodeFilter.isNullOrBlank()) {
            state.evaluatedItems
        } else {
            state.evaluatedItems.filter { eval ->
                eval.item.barcode != null && eval.item.barcode.contains(activeBarcodeFilter!!.trim())
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp)
    ) {
        // --- 1. CARDS DE AUTONOMIA DE ÁGUA NO TOPO ---
        if (report != null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 8.dp)
            ) {
                // Card Apartamento
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (crisisMode) Color.Black else MaterialTheme.colorScheme.surface
                    ),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (crisisMode) Color(0xFF0284C7) else MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Home, contentDescription = null, tint = Color(0xFF0284C7), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Apartamento", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0284C7))
                        }
                        Text(
                            text = "${String.format(Locale.US, "%.1f", report.apartmentAutonomyDays)} Dias",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${String.format(Locale.US, "%.0f", report.apartmentStockLiters)}L (${report.adultsCount + report.childrenCount} pess. a 2.5L/d)",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Card Mochila 72h
                val bugoutReady = report.bugoutIs72hReady
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (crisisMode) Color.Black else MaterialTheme.colorScheme.surface
                    ),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (crisisMode) (if (bugoutReady) Color(0xFF10B981) else Color(0xFFF97316)) else MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Backpack, contentDescription = null, tint = if (bugoutReady) Color(0xFF10B981) else Color(0xFFF97316), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Mochila 72h", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (bugoutReady) Color(0xFF10B981) else Color(0xFFF97316))
                        }
                        Text(
                            text = "${String.format(Locale.US, "%.0f", report.bugoutAutonomyHours)} Horas",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (bugoutReady) "✓ 72h Cobertas (${report.bugoutStockLiters.toInt()}L)" else "⚠ Déficit ${(report.bugoutTarget72hLiters - report.bugoutStockLiters).toInt()}L",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // --- 2. BARRA DE AÇÃO: BOTÃO LEITOR DE CÓDIGO DE BARRAS & STATUS ---
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            FilledTonalButton(
                onClick = { showBarcodeScannerModal = true },
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = if (crisisMode) Color(0xFF1E293B) else MaterialTheme.colorScheme.primaryContainer,
                    contentColor = if (crisisMode) Color(0xFF38BDF8) else MaterialTheme.colorScheme.onPrimaryContainer
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                modifier = Modifier.testTag("btn_scan_barcode_inventory")
            ) {
                Icon(
                    imageVector = Icons.Default.QrCodeScanner,
                    contentDescription = "Escanear Código de Barras",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Escanear Código de Barras",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            if (activeBarcodeFilter != null) {
                InputChip(
                    selected = true,
                    onClick = { activeBarcodeFilter = null },
                    label = { Text("EAN: $activeBarcodeFilter", fontSize = 11.sp) },
                    trailingIcon = {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Limpar Filtro",
                            modifier = Modifier.size(14.dp)
                        )
                    },
                    modifier = Modifier.testTag("chip_active_barcode_filter")
                )
            }
        }

        // --- 3. FAIXA DE ALERTAS DE VENCIMENTO / BATERIA ---
        val alertCount = state.evaluatedItems.count { it.status != ItemAlertStatus.OK }
        if (alertCount > 0) {
            Surface(
                color = if (crisisMode) Color.Black else MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
                    .testTag("inventory_alert_banner")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(10.dp)
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = "Alertas",
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "$alertCount Item(s) em Alerta de Giro ou Carga",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = if (crisisMode) Color.White else MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = "Validades críticas (<=30d), giro FEFO (<=90d) ou baterias sem recarga.",
                            fontSize = 11.sp,
                            color = if (crisisMode) Color.LightGray else MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }

        // --- 4. FILTROS RÁPIDOS (LOCAL E CATEGORIA) ---
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            item {
                FilterChip(
                    selected = state.selectedLocation == null,
                    onClick = { viewModel.setLocationFilter(null) },
                    label = { Text("Todos Locais", fontSize = 12.sp) },
                    modifier = Modifier.testTag("filter_location_all")
                )
            }
            items(StorageLocation.values()) { loc ->
                FilterChip(
                    selected = state.selectedLocation == loc,
                    onClick = {
                        viewModel.setLocationFilter(if (state.selectedLocation == loc) null else loc)
                    },
                    label = { Text(if (loc == StorageLocation.APARTMENT) "Apartamento" else "Mochila 72h", fontSize = 12.sp) },
                    leadingIcon = {
                        Icon(
                            if (loc == StorageLocation.APARTMENT) Icons.Default.Home else Icons.Default.Backpack,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                    },
                    modifier = Modifier.testTag("filter_location_${loc.name}")
                )
            }
        }

        // --- 5. LISTA DE ITENS COM BOTÕES DE AÇÃO ---
        if (displayedItems.isEmpty()) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (activeBarcodeFilter != null) "Nenhum item com o código de barras $activeBarcodeFilter" else "Nenhum item encontrado no filtro selecionado.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (activeBarcodeFilter != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(onClick = { activeBarcodeFilter = null }) {
                            Text("Limpar Filtro de Código de Barras")
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 80.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(displayedItems, key = { it.item.id }) { eval ->
                    InventoryItemCard(
                        eval = eval,
                        crisisMode = crisisMode,
                        onAddQty = { viewModel.updateItemQuantity(eval.item.id, 1.0) },
                        onRemoveQty = { viewModel.updateItemQuantity(eval.item.id, -1.0) },
                        onRotateItem = { viewModel.rotateItem(eval.item.id, 180) },
                        onRegisterMaintenance = { viewModel.registerBatteryMaintenance(eval.item.id) }
                    )
                }
            }
        }
    }

    if (showBarcodeScannerModal) {
        BarcodeScannerModal(
            onDismiss = { showBarcodeScannerModal = false },
            onBarcodeScanned = { scannedCode ->
                activeBarcodeFilter = scannedCode
            }
        )
    }
}

@Composable
fun InventoryItemCard(
    eval: RationingAndAlertEngine.ItemEvaluationResult,
    crisisMode: Boolean,
    onAddQty: () -> Unit,
    onRemoveQty: () -> Unit,
    onRotateItem: () -> Unit,
    onRegisterMaintenance: () -> Unit
) {
    val item = eval.item
    val status = eval.status

    val borderColor = getCrisisBorderColor(status.severityLevel, crisisMode)

    val statusBgColor = when (status) {
        ItemAlertStatus.OK -> if (crisisMode) Color.Black else Color(0xFFE8F5E9)
        ItemAlertStatus.GIRAR_90_DIAS -> if (crisisMode) Color.Black else Color(0xFFFFF9C4)
        ItemAlertStatus.CRITICO_30_DIAS -> if (crisisMode) Color.Black else Color(0xFFFFE0B2)
        ItemAlertStatus.VENCIDO -> if (crisisMode) Color.Black else Color(0xFFFFEBEE)
        ItemAlertStatus.RECARREGAR_BATERIA -> if (crisisMode) Color.Black else Color(0xFFE1F5FE)
    }

    val statusTextColor = when (status) {
        ItemAlertStatus.OK -> Color(0xFF2E7D32)
        ItemAlertStatus.GIRAR_90_DIAS -> Color(0xFFF57F17)
        ItemAlertStatus.CRITICO_30_DIAS -> Color(0xFFE65100)
        ItemAlertStatus.VENCIDO -> Color(0xFFC62828)
        ItemAlertStatus.RECARREGAR_BATERIA -> Color(0xFF0288D1)
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (crisisMode) Color.Black else MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("inventory_item_${item.id}")
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header: Category, Location & Status Badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (item.location == StorageLocation.APARTMENT) Icons.Default.Home else Icons.Default.Backpack,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = item.location.displayName,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // Status Badge
                Surface(
                    color = statusBgColor,
                    shape = RoundedCornerShape(4.dp),
                    border = androidx.compose.foundation.BorderStroke(0.5.dp, statusTextColor),
                    modifier = Modifier.padding(start = 6.dp)
                ) {
                    Text(
                        text = status.title,
                        color = statusTextColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Item Name
            Text(
                text = item.name,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Barcode EAN Badge (com Ícone de Código de Barras visível)
            if (!item.barcode.isNullOrBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(top = 3.dp)
                        .background(
                            color = if (crisisMode) Color(0xFF0F172A) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(4.dp)
                        )
                        .border(
                            width = 0.5.dp,
                            color = if (crisisMode) Color(0xFF0284C7) else MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = "Código de Barras",
                        tint = if (crisisMode) Color(0xFF38BDF8) else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "EAN: ${item.barcode}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        color = if (crisisMode) Color(0xFFE2E8F0) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Alert message if not OK
            if (status != ItemAlertStatus.OK) {
                Text(
                    text = eval.alertMessage,
                    fontSize = 11.sp,
                    color = statusTextColor,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            // Expiration and Maintenance info
            val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
            ) {
                Column {
                    if (item.expirationDate != null) {
                        Text(
                            text = "Validade: ${dateFormat.format(Date(item.expirationDate))}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (item.lastMaintenanceDate != null) {
                        Text(
                            text = "Carga: ${dateFormat.format(Date(item.lastMaintenanceDate))}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Action buttons: "Girar/Renovar" ou "Registrar Carga"
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (item.category == ItemCategory.ENERGY_GEAR) {
                        FilledTonalButton(
                            onClick = onRegisterMaintenance,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.testTag("btn_recharge_${item.id}")
                        ) {
                            Icon(Icons.Default.BatteryChargingFull, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("Recarregar", fontSize = 10.sp)
                        }
                    } else if (item.expirationDate != null) {
                        OutlinedButton(
                            onClick = onRotateItem,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.testTag("btn_rotate_${item.id}")
                        ) {
                            Icon(Icons.Default.Autorenew, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("Girar/Renovar", fontSize = 10.sp)
                        }
                    }
                }
            }

            Divider(
                color = if (crisisMode) Color(0xFF1E1E1E) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                modifier = Modifier.padding(vertical = 6.dp)
            )

            // Quantity Control
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = if (item.quantity % 1.0 == 0.0) "${item.quantity.toInt()}" else String.format(Locale.US, "%.1f", item.quantity),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = item.unit,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = onRemoveQty,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("btn_qty_minus_${item.id}")
                    ) {
                        Icon(Icons.Default.RemoveCircleOutline, contentDescription = "Diminuir Quantidade", modifier = Modifier.size(20.dp))
                    }
                    IconButton(
                        onClick = onAddQty,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("btn_qty_plus_${item.id}")
                    ) {
                        Icon(Icons.Default.AddCircleOutline, contentDescription = "Aumentar Quantidade", modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}
