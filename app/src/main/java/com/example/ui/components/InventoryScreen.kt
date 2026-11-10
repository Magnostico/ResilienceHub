package com.example.ui.components

import androidx.compose.foundation.BorderStroke
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
import com.example.data.entity.InventoryItemEntity
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
    var itemToEditExpiration by remember { mutableStateOf<InventoryItemEntity?>(null) }
    var showConfirmClearAllItems by remember { mutableStateOf(false) }

    val displayedItems = remember(state.evaluatedItems, activeBarcodeFilter) {
        if (activeBarcodeFilter.isNullOrBlank()) {
            state.evaluatedItems
        } else {
            state.evaluatedItems.filter { eval ->
                eval.item.barcode != null && eval.item.barcode.contains(activeBarcodeFilter!!.trim())
            }
        }
    }

    // Tela inteira unificada em um único LazyColumn para scroll fluido e amplo
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 92.dp),
        modifier = modifier
            .fillMaxSize()
            .testTag("inventory_screen")
    ) {
        // --- 1. CARDS DE AUTONOMIA DE ÁGUA NO TOPO ---
        if (report != null) {
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Card Apartamento
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (crisisMode) Color.Black else Color(0xFF0F1E33)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFF38BDF8)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Home,
                                    contentDescription = null,
                                    tint = Color(0xFF38BDF8),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "Apartamento",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF38BDF8)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${String.format(Locale.US, "%.1f", report.apartmentAutonomyDays)} Dias",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                            Text(
                                text = "${String.format(Locale.US, "%.0f", report.apartmentStockLiters)}L (${report.adultsCount + report.childrenCount} pess. a 2.5L/d)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFFCBD5E1)
                            )
                        }
                    }

                    // Card Mochila 72h
                    val bugoutReady = report.bugoutIs72hReady
                    val bugoutColor = if (bugoutReady) Color(0xFF34D399) else Color(0xFFFB923C)
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (crisisMode) Color.Black else if (bugoutReady) Color(0xFF07261D) else Color(0xFF2B1608)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, bugoutColor),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Backpack,
                                    contentDescription = null,
                                    tint = bugoutColor,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "Mochila 72h",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = bugoutColor
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${String.format(Locale.US, "%.0f", report.bugoutAutonomyHours)} Horas",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                            Text(
                                text = if (bugoutReady) "✓ 72h Cobertas (${report.bugoutStockLiters.toInt()}L)"
                                else "⚠ Déficit ${(report.bugoutTarget72hLiters - report.bugoutStockLiters).toInt()}L",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFE2E8F0)
                            )
                        }
                    }
                }
            }
        }

        // --- 2. BARRA DE AÇÃO: LEITOR DE CÓDIGO DE BARRAS, NOVO ITEM & LIMPAR ESTOQUE ---
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = { showBarcodeScannerModal = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF0284C7),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("btn_scan_barcode_inventory")
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = "Escanear Código de Barras",
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "Escanear EAN",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                FilledTonalButton(
                    onClick = onOpenAddDialog,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = Color(0xFFF59E0B),
                        contentColor = Color(0xFF090D16)
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Novo Item", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                }

                if (state.items.isNotEmpty()) {
                    OutlinedButton(
                        onClick = { showConfirmClearAllItems = true },
                        border = BorderStroke(1.dp, Color(0xFFF87171)),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp)
                    ) {
                        Icon(
                            Icons.Default.DeleteSweep,
                            contentDescription = "Limpar Estoque",
                            tint = Color(0xFFF87171),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Limpar", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF87171))
                    }
                }
            }
        }

        if (activeBarcodeFilter != null) {
            item {
                InputChip(
                    selected = true,
                    onClick = { activeBarcodeFilter = null },
                    label = {
                        Text(
                            "Filtro EAN Ativo: $activeBarcodeFilter (Toque para limpar)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    },
                    trailingIcon = {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Limpar Filtro",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    colors = InputChipDefaults.inputChipColors(
                        selectedContainerColor = Color(0xFF0284C7),
                        selectedLabelColor = Color.White
                    ),
                    modifier = Modifier.testTag("chip_active_barcode_filter")
                )
            }
        }

        // --- 3. FAIXA DE ALERTAS DE VENCIMENTO / BATERIA ---
        val alertCount = state.evaluatedItems.count { it.status != ItemAlertStatus.OK }
        if (alertCount > 0) {
            item {
                Surface(
                    color = if (crisisMode) Color(0xFF1F0707) else Color(0xFF3B0A0A),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0xFFF87171)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("inventory_alert_banner")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = "Alertas",
                            tint = Color(0xFFF87171),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "$alertCount Item(s) em Alerta de Giro FEFO ou Carga",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.sp,
                                color = Color.White
                            )
                            Text(
                                text = "Validades críticas (<=30d), giro preventivo (<=90d) ou baterias sem recarga.",
                                fontSize = 11.sp,
                                color = Color(0xFFFECACA)
                            )
                        }
                    }
                }
            }
        }

        // --- 4. FILTROS RÁPIDOS (LOCAL E CATEGORIA) ---
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    item {
                        FilterChip(
                            selected = state.selectedLocation == null,
                            onClick = { viewModel.setLocationFilter(null) },
                            label = {
                                Text(
                                    "Todos Locais",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (state.selectedLocation == null) Color(0xFF090D16) else Color.White
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFFBBF24),
                                containerColor = Color(0xFF1E293B)
                            ),
                            modifier = Modifier.testTag("filter_location_all")
                        )
                    }
                    items(StorageLocation.values()) { loc ->
                        val isSelected = state.selectedLocation == loc
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                viewModel.setLocationFilter(if (isSelected) null else loc)
                            },
                            label = {
                                Text(
                                    if (loc == StorageLocation.APARTMENT) "Apartamento" else "Mochila 72h",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color(0xFF090D16) else Color.White
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    if (loc == StorageLocation.APARTMENT) Icons.Default.Home else Icons.Default.Backpack,
                                    contentDescription = null,
                                    tint = if (isSelected) Color(0xFF090D16) else Color(0xFF38BDF8),
                                    modifier = Modifier.size(15.dp)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFFBBF24),
                                containerColor = Color(0xFF1E293B)
                            ),
                            modifier = Modifier.testTag("filter_location_${loc.name}")
                        )
                    }
                }

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    item {
                        val isAllCat = state.selectedCategory == null
                        FilterChip(
                            selected = isAllCat,
                            onClick = { viewModel.setCategoryFilter(null) },
                            label = {
                                Text(
                                    "Todas Categorias",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isAllCat) Color.White else Color(0xFFCBD5E1)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF0284C7),
                                containerColor = Color(0xFF111827)
                            )
                        )
                    }
                    items(ItemCategory.values()) { cat ->
                        val isSel = state.selectedCategory == cat
                        FilterChip(
                            selected = isSel,
                            onClick = { viewModel.setCategoryFilter(if (isSel) null else cat) },
                            label = {
                                Text(
                                    cat.displayName,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSel) Color.White else Color(0xFFCBD5E1)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF0284C7),
                                containerColor = Color(0xFF111827)
                            )
                        )
                    }
                }
            }
        }

        // --- 5. LISTA DE ITENS COM VALIDADE MÊS/ANO E BOTÃO DE EXCLUIR ---
        if (displayedItems.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFF334155)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                    ) {
                        Text(
                            if (activeBarcodeFilter != null) "Nenhum item com o código EAN $activeBarcodeFilter"
                            else "Estoque vazio. Toque em 'Novo Item' para cadastrar seus suprimentos.",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        if (activeBarcodeFilter != null) {
                            Button(onClick = { activeBarcodeFilter = null }) {
                                Text("Limpar Filtro de Código de Barras")
                            }
                        } else {
                            Button(
                                onClick = onOpenAddDialog,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFF59E0B),
                                    contentColor = Color(0xFF090D16)
                                )
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Cadastrar Meu Primeiro Item", fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }
                }
            }
        } else {
            items(displayedItems, key = { it.item.id }) { eval ->
                InventoryItemCard(
                    eval = eval,
                    crisisMode = crisisMode,
                    onAddQty = { viewModel.updateItemQuantity(eval.item.id, 1.0) },
                    onRemoveQty = { viewModel.updateItemQuantity(eval.item.id, -1.0) },
                    onEditExpirationMonthYear = { itemToEditExpiration = eval.item },
                    onRegisterMaintenance = { viewModel.registerBatteryMaintenance(eval.item.id) },
                    onDeleteItem = { viewModel.deleteItem(eval.item.id) }
                )
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

    itemToEditExpiration?.let { targetItem ->
        EditExpirationMonthYearDialog(
            itemName = targetItem.name,
            currentExpirationMillis = targetItem.expirationDate,
            onDismiss = { itemToEditExpiration = null },
            onConfirm = { newMonth, newYear ->
                viewModel.updateItemExpirationMonthYear(targetItem.id, newMonth, newYear)
            }
        )
    }

    if (showConfirmClearAllItems) {
        ConfirmClearDialog(
            title = "Limpar Todo o Estoque?",
            message = "Todos os itens fictícios ou cadastrados no estoque serão removidos para que você possa cadastrar apenas os seus.",
            confirmButtonText = "Apagar Estoque",
            onDismiss = { showConfirmClearAllItems = false },
            onConfirm = { viewModel.clearAllItems() }
        )
    }
}

@Composable
fun InventoryItemCard(
    eval: RationingAndAlertEngine.ItemEvaluationResult,
    crisisMode: Boolean,
    onAddQty: () -> Unit,
    onRemoveQty: () -> Unit,
    onEditExpirationMonthYear: () -> Unit,
    onRegisterMaintenance: () -> Unit,
    onDeleteItem: () -> Unit
) {
    val item = eval.item
    val status = eval.status

    val borderColor = getCrisisBorderColor(status.severityLevel, crisisMode)

    val statusBgColor = when (status) {
        ItemAlertStatus.OK -> Color(0xFF062E22)
        ItemAlertStatus.GIRAR_90_DIAS -> Color(0xFF2E2105)
        ItemAlertStatus.CRITICO_30_DIAS -> Color(0xFF361507)
        ItemAlertStatus.VENCIDO -> Color(0xFF3B0A0A)
        ItemAlertStatus.RECARREGAR_BATERIA -> Color(0xFF0C2434)
    }

    val statusTextColor = when (status) {
        ItemAlertStatus.OK -> Color(0xFF34D399)
        ItemAlertStatus.GIRAR_90_DIAS -> Color(0xFFFBBF24)
        ItemAlertStatus.CRITICO_30_DIAS -> Color(0xFFFB923C)
        ItemAlertStatus.VENCIDO -> Color(0xFFF87171)
        ItemAlertStatus.RECARREGAR_BATERIA -> Color(0xFF38BDF8)
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (crisisMode) Color.Black else Color(0xFF111827)
        ),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.2.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("inventory_item_${item.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header: Category, Location, Status Badge & Delete Button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        if (item.location == StorageLocation.APARTMENT) Icons.Default.Home else Icons.Default.Backpack,
                        contentDescription = null,
                        tint = Color(0xFFFBBF24),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "${item.location.displayName} • ${item.category.displayName}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFBBF24)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = statusBgColor,
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, statusTextColor)
                    ) {
                        Text(
                            text = status.title,
                            color = statusTextColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    IconButton(
                        onClick = onDeleteItem,
                        modifier = Modifier
                            .size(28.dp)
                            .testTag("btn_delete_item_${item.id}")
                    ) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = "Apagar Item",
                            tint = Color(0xFFF87171),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Item Name
            Text(
                text = item.name,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 16.sp,
                color = Color.White
            )

            // Barcode EAN Badge
            if (!item.barcode.isNullOrBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(top = 5.dp)
                        .background(
                            color = Color(0xFF090D16),
                            shape = RoundedCornerShape(6.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = Color(0xFF334155),
                            shape = RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = "Código de Barras",
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "EAN: ${item.barcode}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        color = Color(0xFFE2E8F0)
                    )
                }
            }

            // Alert message if not OK
            if (status != ItemAlertStatus.OK) {
                Text(
                    text = eval.alertMessage,
                    fontSize = 12.sp,
                    color = statusTextColor,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 5.dp)
                )
            }

            // Exibição da Validade em Mês/Ano (MM/yyyy) e Manutenção
            val monthYearFormat = SimpleDateFormat("MM/yyyy", Locale.getDefault())
            val fullDateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                Column {
                    if (item.expirationDate != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Event,
                                contentDescription = null,
                                tint = Color(0xFFFBBF24),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Validade (Mês/Ano): ${monthYearFormat.format(Date(item.expirationDate))}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                        }
                    } else if (item.category != ItemCategory.ENERGY_GEAR) {
                        Text(
                            text = "Sem prazo de validade definido",
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                    if (item.lastMaintenanceDate != null) {
                        Text(
                            text = "Última Carga: ${fullDateFormat.format(Date(item.lastMaintenanceDate))}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFCBD5E1)
                        )
                    }
                }

                // Action buttons: "Mês/Ano" (Girar/Editar Validade) ou "Recarregar"
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (item.category == ItemCategory.ENERGY_GEAR) {
                        Button(
                            onClick = onRegisterMaintenance,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF0284C7),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier
                                .height(32.dp)
                                .testTag("btn_recharge_${item.id}")
                        ) {
                            Icon(Icons.Default.BatteryChargingFull, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Recarregar", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        OutlinedButton(
                            onClick = onEditExpirationMonthYear,
                            border = BorderStroke(1.dp, Color(0xFFFBBF24)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier
                                .height(32.dp)
                                .testTag("btn_rotate_${item.id}")
                        ) {
                            Icon(Icons.Default.EditCalendar, contentDescription = null, tint = Color(0xFFFBBF24), modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Mês/Ano", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFBBF24))
                        }
                    }
                }
            }

            Divider(
                color = Color(0xFF1E293B),
                modifier = Modifier.padding(vertical = 8.dp)
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
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = item.unit,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF94A3B8),
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalIconButton(
                        onClick = onRemoveQty,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = Color(0xFF1E293B),
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("btn_qty_minus_${item.id}")
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Diminuir Quantidade", modifier = Modifier.size(18.dp))
                    }
                    FilledTonalIconButton(
                        onClick = onAddQty,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = Color(0xFFF59E0B),
                            contentColor = Color(0xFF090D16)
                        ),
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("btn_qty_plus_${item.id}")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Aumentar Quantidade", modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}
