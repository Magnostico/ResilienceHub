package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.entity.FamilyProfileEntity
import com.example.data.entity.InventoryItemEntity
import com.example.data.model.FamilyRole
import com.example.data.model.ItemCategory
import com.example.data.model.PoiType
import com.example.data.model.StorageLocation
import com.example.ui.FamilyDocument
import com.example.ui.MainUiState
import com.example.ui.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.Normalizer
import java.util.Calendar
import java.util.Locale

private val MONTH_OPTIONS = listOf(
    1 to "01 • Jan",
    2 to "02 • Fev",
    3 to "03 • Mar",
    4 to "04 • Abr",
    5 to "05 • Mai",
    6 to "06 • Jun",
    7 to "07 • Jul",
    8 to "08 • Ago",
    9 to "09 • Set",
    10 to "10 • Out",
    11 to "11 • Nov",
    12 to "12 • Dez"
)

private val YEAR_OPTIONS = (2026..2035).toList()

private fun getCategoryProtocolBannerText(category: ItemCategory): String = when (category) {
    ItemCategory.WATER ->
        "🛡️ Protocolo Automático OMS/Defesa Civil (Água): Monitora giro FEFO aos 90d/30d, cálculo de autonomia hídrica (2,5L/d apto e 2L/d mochila) e alerta de fervura/cloração se vencer."
    ItemCategory.FOOD ->
        "🛡️ Protocolo Automático FEMA/ANVISA (Alimentos): Monitora giro alimentar 90d/30d e emite alerta de inspeção contra estufamento/botulismo ao vencer."
    ItemCategory.MEDS ->
        "🛡️ Protocolo Automático Min. Saúde/Cruz Vermelha (Farmácia): Monitora reposição preventiva 90d/30d e bloqueia uso de medicamentos com princípio ativo expirado."
    ItemCategory.ENERGY_GEAR ->
        "🛡️ Protocolo Automático FEMA/Bombeiros (Energia): Dispara aviso automático de teste e recarga obrigatória de bateria a cada 90 dias."
    ItemCategory.HYGIENE_DOCS ->
        "🛡️ Protocolo Automático Defesa Civil/OMS (Higiene & Docs): Monitora renovação documental e validade de químicos sanitários (cloro/álcool 70%)."
}

private fun normalizeForSearch(input: String): String {
    val nfd = Normalizer.normalize(input.lowercase(Locale.getDefault()), Normalizer.Form.NFD)
    return Regex("\\p{InCombiningDiacriticalMarks}+").replace(nfd, "").trim()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddItemDialog(
    existingItems: List<InventoryItemEntity> = emptyList(),
    onDismiss: () -> Unit,
    onIncrementExistingItem: ((itemId: Long, deltaQty: Double) -> Unit)? = null,
    onConfirmWithLotOption: ((
        name: String,
        barcode: String?,
        category: ItemCategory,
        location: StorageLocation,
        quantity: Double,
        unit: String,
        expirationMonth: Int?,
        expirationYear: Int?,
        forceSeparateLot: Boolean
    ) -> Unit)? = null,
    onConfirm: (
        name: String,
        barcode: String?,
        category: ItemCategory,
        location: StorageLocation,
        quantity: Double,
        unit: String,
        expirationMonth: Int?,
        expirationYear: Int?
    ) -> Unit = { _, _, _, _, _, _, _, _ -> }
) {
    val currentCal = remember { Calendar.getInstance() }
    val defaultMonth = remember { ((currentCal.get(Calendar.MONTH) + 1 + 6 - 1) % 12) + 1 }
    val defaultYear = remember { currentCal.get(Calendar.YEAR) + if (currentCal.get(Calendar.MONTH) + 7 > 12) 1 else 0 }

    var name by remember { mutableStateOf("") }
    var barcode by remember { mutableStateOf("") }
    var showBarcodeScanner by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf(ItemCategory.WATER) }
    var selectedLocation by remember { mutableStateOf(StorageLocation.APARTMENT) }
    var quantityText by remember { mutableStateOf("1") }
    var unitText by remember { mutableStateOf("L") }

    var hasExpiration by remember { mutableStateOf(true) }
    var monthText by remember { mutableStateOf(String.format(Locale.US, "%02d", defaultMonth)) }
    var yearText by remember { mutableStateOf(defaultYear.toString()) }
    var hideSuggestionsAfterPick by remember { mutableStateOf(false) }
    var forceSeparateLot by remember { mutableStateOf(false) }

    // Item já existente no estoque com o mesmo código de barras
    val matchedBarcodeItem = remember(barcode, existingItems, selectedLocation) {
        val cleanCode = barcode.trim()
        if (cleanCode.isBlank()) null
        else existingItems.find { it.barcode?.trim() == cleanCode && it.location == selectedLocation }
            ?: existingItems.find { it.barcode?.trim() == cleanCode }
    }

    // Lista sem duplicatas dos itens já cadastrados pelo usuário para sugestão inteligente
    val uniqueRegisteredItems = remember(existingItems) {
        existingItems
            .sortedByDescending { it.id }
            .distinctBy { normalizeForSearch(it.name) }
    }

    // Filtra itens já cadastrados conforme o usuário digita
    val matchingSuggestions = remember(name, uniqueRegisteredItems, hideSuggestionsAfterPick) {
        if (hideSuggestionsAfterPick) {
            emptyList()
        } else {
            val queryNorm = normalizeForSearch(name)
            if (queryNorm.isEmpty()) {
                emptyList()
            } else {
                uniqueRegisteredItems.filter { item ->
                    val itemNorm = normalizeForSearch(item.name)
                    itemNorm.contains(queryNorm) && itemNorm != queryNorm
                }.take(5)
            }
        }
    }

    val applySuggestion: (InventoryItemEntity) -> Unit = { suggested ->
        name = suggested.name
        if (!suggested.barcode.isNullOrBlank()) {
            barcode = suggested.barcode
        }
        selectedCategory = suggested.category
        selectedLocation = suggested.location
        unitText = suggested.unit
        hasExpiration = suggested.expirationDate != null || suggested.category != ItemCategory.ENERGY_GEAR
        if (suggested.expirationDate != null) {
            val cal = Calendar.getInstance().apply { timeInMillis = suggested.expirationDate }
            monthText = String.format(Locale.US, "%02d", cal.get(Calendar.MONTH) + 1)
            yearText = cal.get(Calendar.YEAR).toString()
        }
        hideSuggestionsAfterPick = true
    }

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        focusedBorderColor = Color(0xFFFBBF24),
        unfocusedBorderColor = Color(0xFF475569)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF111827),
        titleContentColor = Color.White,
        textContentColor = Color.White,
        title = { Text("Cadastrar Item no Estoque", fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        hideSuggestionsAfterPick = false
                    },
                    label = { Text("Nome do Item", color = Color(0xFFCBD5E1)) },
                    placeholder = { Text("Digite para ver sugestões do seu estoque...", fontSize = 11.sp, color = Color(0xFF64748B)) },
                    colors = textFieldColors,
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_item_name")
                )

                // --- SUGESTÕES EM TEMPO REAL CONFORME O USUÁRIO DIGITA ---
                if (matchingSuggestions.isNotEmpty()) {
                    Surface(
                        color = Color(0xFF090D16),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFFFBBF24)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = Color(0xFFFBBF24),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "Sugestões baseadas no seu estoque (toque para preencher):",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFFFBBF24)
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))

                            matchingSuggestions.forEach { suggested ->
                                Surface(
                                    color = Color(0xFF1E293B),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp)
                                        .clickable { applySuggestion(suggested) }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = suggested.name,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = Color.White,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${suggested.category.displayName} • ${if (suggested.location == StorageLocation.APARTMENT) "Apartamento" else "Mochila 72h"} • Un: ${suggested.unit}",
                                                fontSize = 10.sp,
                                                color = Color(0xFF38BDF8)
                                            )
                                        }
                                        Icon(
                                            Icons.Default.NorthWest,
                                            contentDescription = "Preencher",
                                            tint = Color(0xFFFBBF24),
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else if (name.isBlank() && uniqueRegisteredItems.isNotEmpty()) {
                    // Chips de preenchimento rápido com os itens já cadastrados mesmo antes de digitar
                    Column {
                        Text(
                            text = "Seus itens cadastrados (toque para repetir cadastro):",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF94A3B8)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(uniqueRegisteredItems.take(10)) { existing ->
                                SuggestionChip(
                                    onClick = { applySuggestion(existing) },
                                    label = {
                                        Text(
                                            text = existing.name,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            maxLines = 1
                                        )
                                    },
                                    colors = SuggestionChipDefaults.suggestionChipColors(
                                        containerColor = Color(0xFF1E293B)
                                    )
                                )
                            }
                        }
                    }
                }

                // Código de barras com botão de escaneamento por câmera
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = barcode,
                        onValueChange = { code ->
                            barcode = code
                            // Se o código de barras já existir no estoque, preenche automaticamente e prepara para somar +1
                            val matchByCode = existingItems.find { it.barcode?.trim() == code.trim() && code.isNotBlank() }
                            if (matchByCode != null) {
                                applySuggestion(matchByCode)
                                quantityText = "1"
                                forceSeparateLot = false
                            }
                        },
                        label = { Text("Código EAN-13 (Opcional)", color = Color(0xFFCBD5E1)) },
                        leadingIcon = {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = Color(0xFF38BDF8))
                        },
                        colors = textFieldColors,
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("input_item_barcode")
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Button(
                        onClick = { showBarcodeScanner = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF0284C7),
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                        modifier = Modifier.testTag("btn_scan_barcode_form")
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = "Escanear Código de Barras",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Câmera", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // --- AVISO INTELIGENTE QUANDO O CÓDIGO DE BARRAS JÁ EXISTE NO ESTOQUE ---
                if (matchedBarcodeItem != null) {
                    val currentQtyFormatted = if (matchedBarcodeItem.quantity % 1.0 == 0.0) {
                        matchedBarcodeItem.quantity.toInt().toString()
                    } else {
                        String.format(Locale.US, "%.1f", matchedBarcodeItem.quantity)
                    }
                    Surface(
                        color = Color(0xFF062E22),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.2.dp, Color(0xFF34D399)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF34D399),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Produto já cadastrado reconhecido pelo EAN!",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF34D399)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${matchedBarcodeItem.name} • Estoque atual: $currentQtyFormatted ${matchedBarcodeItem.unit}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = if (!forceSeparateLot)
                                    "Em vez de duplicar o produto, vamos acrescentar +${quantityText.ifBlank { "1" }} ${unitText} ao registro já existente."
                                else
                                    "Modo Lote Separado ativo: será criado um card separado com a nova validade informada abaixo.",
                                fontSize = 10.sp,
                                lineHeight = 14.sp,
                                color = Color(0xFFA7F3D0)
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Botão rápido de 1 toque para somar +1 e fechar na hora
                            Button(
                                onClick = {
                                    val qtyToAdd = quantityText.replace(",", ".").toDoubleOrNull() ?: 1.0
                                    if (onIncrementExistingItem != null) {
                                        onIncrementExistingItem(matchedBarcodeItem.id, qtyToAdd)
                                    } else {
                                        val expMonth = if (hasExpiration) monthText.toIntOrNull()?.coerceIn(1, 12) else null
                                        val expYear = if (hasExpiration) yearText.toIntOrNull()?.coerceIn(2020, 2100) else null
                                        onConfirmWithLotOption?.invoke(
                                            matchedBarcodeItem.name,
                                            matchedBarcodeItem.barcode,
                                            matchedBarcodeItem.category,
                                            matchedBarcodeItem.location,
                                            qtyToAdd,
                                            matchedBarcodeItem.unit,
                                            expMonth,
                                            expYear,
                                            false
                                        ) ?: onConfirm(
                                            matchedBarcodeItem.name,
                                            matchedBarcodeItem.barcode,
                                            matchedBarcodeItem.category,
                                            matchedBarcodeItem.location,
                                            qtyToAdd,
                                            matchedBarcodeItem.unit,
                                            expMonth,
                                            expYear
                                        )
                                    }
                                    onDismiss()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF10B981),
                                    contentColor = Color(0xFF090D16)
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.AddCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Acrescentar +${quantityText.ifBlank { "1" }} ${matchedBarcodeItem.unit} Agora",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // Seletor para caso a safra/validade seja diferente
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                FilterChip(
                                    selected = !forceSeparateLot,
                                    onClick = { forceSeparateLot = false },
                                    label = {
                                        Text(
                                            "Somar no existente",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (!forceSeparateLot) Color(0xFF090D16) else Color.White
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(0xFF34D399),
                                        containerColor = Color(0xFF090D16)
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                                FilterChip(
                                    selected = forceSeparateLot,
                                    onClick = { forceSeparateLot = true },
                                    label = {
                                        Text(
                                            "Safra/Validade diferente",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (forceSeparateLot) Color(0xFF090D16) else Color.White
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(0xFFFBBF24),
                                        containerColor = Color(0xFF090D16)
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                // Location selector
                Text("Local de Armazenamento:", style = MaterialTheme.typography.labelMedium, color = Color(0xFFFBBF24), fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StorageLocation.values().forEach { loc ->
                        val isSel = selectedLocation == loc
                        FilterChip(
                            selected = isSel,
                            onClick = { selectedLocation = loc },
                            label = {
                                Text(
                                    if (loc == StorageLocation.APARTMENT) "Apartamento" else "Mochila 72h",
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSel) Color(0xFF090D16) else Color.White
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFFBBF24),
                                containerColor = Color(0xFF1E293B)
                            )
                        )
                    }
                }

                // Category selector
                Text("Categoria (Define o Protocolo de Alerta Automático):", style = MaterialTheme.typography.labelMedium, color = Color(0xFFFBBF24), fontWeight = FontWeight.Bold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(ItemCategory.values()) { cat ->
                        val isSel = selectedCategory == cat
                        FilterChip(
                            selected = isSel,
                            onClick = {
                                selectedCategory = cat
                                when (cat) {
                                    ItemCategory.WATER -> {
                                        unitText = "L"
                                        hasExpiration = true
                                    }
                                    ItemCategory.FOOD -> {
                                        unitText = "kg"
                                        hasExpiration = true
                                    }
                                    ItemCategory.MEDS -> {
                                        unitText = "unidades"
                                        hasExpiration = true
                                    }
                                    ItemCategory.ENERGY_GEAR -> {
                                        unitText = "unidades"
                                        hasExpiration = false
                                    }
                                    ItemCategory.HYGIENE_DOCS -> {
                                        unitText = "unidades"
                                    }
                                }
                            },
                            label = {
                                Text(
                                    cat.displayName,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSel) Color.White else Color(0xFFCBD5E1)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF0284C7),
                                containerColor = Color(0xFF1E293B)
                            )
                        )
                    }
                }

                // Aviso do protocolo automático vinculado à categoria
                Surface(
                    color = Color(0xFF0C2434),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color(0xFF38BDF8)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = getCategoryProtocolBannerText(selectedCategory),
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        color = Color(0xFFE0F2FE),
                        modifier = Modifier.padding(8.dp)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = quantityText,
                        onValueChange = { quantityText = it },
                        label = { Text("Quantidade", color = Color(0xFFCBD5E1)) },
                        colors = textFieldColors,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = unitText,
                        onValueChange = { unitText = it },
                        label = { Text("Unidade (L, kg, latas)", color = Color(0xFFCBD5E1)) },
                        colors = textFieldColors,
                        modifier = Modifier.weight(1f)
                    )
                }

                // --- SELETOR DE VALIDADE POR MÊS E ANO (MM/AAAA) ---
                Surface(
                    color = Color(0xFF090D16),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0xFF334155)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column {
                                Text(
                                    text = "Validade (Mês e Ano)",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFFFBBF24)
                                )
                                Text(
                                    text = if (hasExpiration) "Formato MM / AAAA" else "Item sem validade (Ex: Equipamento)",
                                    fontSize = 11.sp,
                                    color = Color(0xFFCBD5E1)
                                )
                            }
                            Switch(
                                checked = hasExpiration,
                                onCheckedChange = { hasExpiration = it }
                            )
                        }

                        if (hasExpiration) {
                            Spacer(modifier = Modifier.height(8.dp))

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = monthText,
                                    onValueChange = { input ->
                                        if (input.length <= 2 && input.all { it.isDigit() }) {
                                            monthText = input
                                        }
                                    },
                                    label = { Text("Mês (01 a 12)", color = Color(0xFFCBD5E1)) },
                                    colors = textFieldColors,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = yearText,
                                    onValueChange = { input ->
                                        if (input.length <= 4 && input.all { it.isDigit() }) {
                                            yearText = input
                                        }
                                    },
                                    label = { Text("Ano (Ex: 2027)", color = Color(0xFFCBD5E1)) },
                                    colors = textFieldColors,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Toque rápido — Mês:", fontSize = 10.sp, color = Color(0xFF94A3B8))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                items(MONTH_OPTIONS) { (mNum, mLabel) ->
                                    val isSel = monthText.toIntOrNull() == mNum
                                    FilterChip(
                                        selected = isSel,
                                        onClick = { monthText = String.format(Locale.US, "%02d", mNum) },
                                        label = {
                                            Text(
                                                mLabel,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSel) Color(0xFF090D16) else Color.White
                                            )
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = Color(0xFFFBBF24),
                                            containerColor = Color(0xFF1E293B)
                                        )
                                    )
                                }
                            }

                            Text("Toque rápido — Ano:", fontSize = 10.sp, color = Color(0xFF94A3B8))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                items(YEAR_OPTIONS) { yNum ->
                                    val isSel = yearText.toIntOrNull() == yNum
                                    FilterChip(
                                        selected = isSel,
                                        onClick = { yearText = yNum.toString() },
                                        label = {
                                            Text(
                                                yNum.toString(),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSel) Color.White else Color(0xFFCBD5E1)
                                            )
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = Color(0xFF0284C7),
                                            containerColor = Color(0xFF1E293B)
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            val isAddingToExisting = matchedBarcodeItem != null && !forceSeparateLot
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        val qty = quantityText.replace(",", ".").toDoubleOrNull() ?: 1.0
                        val expMonth = if (hasExpiration) monthText.toIntOrNull()?.coerceIn(1, 12) else null
                        val expYear = if (hasExpiration) yearText.toIntOrNull()?.coerceIn(2020, 2100) else null
                        if (onConfirmWithLotOption != null) {
                            onConfirmWithLotOption(
                                name.trim(),
                                barcode.ifBlank { null },
                                selectedCategory,
                                selectedLocation,
                                qty,
                                unitText.ifBlank { "un" },
                                expMonth,
                                expYear,
                                forceSeparateLot
                            )
                        } else {
                            onConfirm(
                                name.trim(),
                                barcode.ifBlank { null },
                                selectedCategory,
                                selectedLocation,
                                qty,
                                unitText.ifBlank { "un" },
                                expMonth,
                                expYear
                            )
                        }
                        onDismiss()
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isAddingToExisting) Color(0xFF10B981) else Color(0xFFF59E0B),
                    contentColor = Color(0xFF090D16)
                ),
                modifier = Modifier.testTag("btn_confirm_add_item")
            ) {
                Text(
                    text = if (isAddingToExisting) "Somar +${quantityText.ifBlank { "1" }} ao Existente" else "Salvar Item",
                    fontWeight = FontWeight.ExtraBold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = Color(0xFFCBD5E1))
            }
        }
    )

    if (showBarcodeScanner) {
        BarcodeScannerModal(
            onDismiss = { showBarcodeScanner = false },
            onBarcodeScanned = { scannedCode ->
                barcode = scannedCode
                val matchByCode = existingItems.find { it.barcode?.trim() == scannedCode.trim() }
                if (matchByCode != null) {
                    applySuggestion(matchByCode)
                    quantityText = "1"
                    forceSeparateLot = false
                }
            }
        )
    }
}

/**
 * Modal completo para Editar um Produto já cadastrado (incluindo sua CATEGORIA, Local, Nome,
 * Código EAN, Quantidade, Unidade e Validade Mês/Ano), aplicando automaticamente os
 * protocolos de sobrevivência e avisos da categoria escolhida.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditInventoryItemDialog(
    item: InventoryItemEntity,
    onDismiss: () -> Unit,
    onConfirm: (
        name: String,
        barcode: String?,
        category: ItemCategory,
        location: StorageLocation,
        quantity: Double,
        unit: String,
        expirationMonth: Int?,
        expirationYear: Int?,
        resetBatteryMaintenanceNow: Boolean
    ) -> Unit
) {
    val initCal = remember(item.expirationDate) {
        Calendar.getInstance().apply {
            if (item.expirationDate != null) {
                timeInMillis = item.expirationDate
            } else {
                add(Calendar.MONTH, 6)
            }
        }
    }

    var name by remember { mutableStateOf(item.name) }
    var barcode by remember { mutableStateOf(item.barcode ?: "") }
    var selectedCategory by remember { mutableStateOf(item.category) }
    var selectedLocation by remember { mutableStateOf(item.location) }
    var quantityText by remember {
        mutableStateOf(
            if (item.quantity % 1.0 == 0.0) item.quantity.toInt().toString()
            else String.format(Locale.US, "%.1f", item.quantity)
        )
    }
    var unitText by remember { mutableStateOf(item.unit) }
    var hasExpiration by remember { mutableStateOf(item.expirationDate != null) }
    var monthText by remember { mutableStateOf(String.format(Locale.US, "%02d", initCal.get(Calendar.MONTH) + 1)) }
    var yearText by remember { mutableStateOf(initCal.get(Calendar.YEAR).toString()) }
    var resetBatteryMaintenanceNow by remember { mutableStateOf(false) }

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        focusedBorderColor = Color(0xFFFBBF24),
        unfocusedBorderColor = Color(0xFF475569)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF111827),
        titleContentColor = Color.White,
        textContentColor = Color.White,
        title = { Text("Editar Produto & Categoria", fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                // Seletor de Categoria em destaque no topo para facilitar a troca de categoria
                Text(
                    text = "Categoria do Produto (Ajusta Avisos Automaticamente):",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFFFBBF24)
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(ItemCategory.values()) { cat ->
                        val isSel = selectedCategory == cat
                        FilterChip(
                            selected = isSel,
                            onClick = {
                                selectedCategory = cat
                                if (cat == ItemCategory.ENERGY_GEAR && item.expirationDate == null) {
                                    hasExpiration = false
                                } else if (cat != ItemCategory.ENERGY_GEAR && !hasExpiration) {
                                    hasExpiration = true
                                }
                            },
                            label = {
                                Text(
                                    cat.displayName,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (isSel) Color.White else Color(0xFFCBD5E1)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF0284C7),
                                containerColor = Color(0xFF1E293B)
                            )
                        )
                    }
                }

                // Banner mostrando a regra de sobrevivência que entra em vigor automaticamente
                Surface(
                    color = Color(0xFF0C2434),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color(0xFF38BDF8)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = getCategoryProtocolBannerText(selectedCategory),
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        color = Color(0xFFE0F2FE),
                        modifier = Modifier.padding(8.dp)
                    )
                }

                if (selectedCategory == ItemCategory.ENERGY_GEAR) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Registrar carga/teste de bateria hoje (ciclo 90d):",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF34D399),
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = resetBatteryMaintenanceNow,
                            onCheckedChange = { resetBatteryMaintenanceNow = it }
                        )
                    }
                }

                // Local de Armazenamento
                Text("Local de Armazenamento:", fontSize = 12.sp, color = Color(0xFFFBBF24), fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StorageLocation.values().forEach { loc ->
                        val isSel = selectedLocation == loc
                        FilterChip(
                            selected = isSel,
                            onClick = { selectedLocation = loc },
                            label = {
                                Text(
                                    if (loc == StorageLocation.APARTMENT) "Apartamento" else "Mochila 72h",
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSel) Color(0xFF090D16) else Color.White
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFFBBF24),
                                containerColor = Color(0xFF1E293B)
                            )
                        )
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nome do Produto", color = Color(0xFFCBD5E1)) },
                    colors = textFieldColors,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = barcode,
                    onValueChange = { barcode = it },
                    label = { Text("Código EAN-13 (Opcional)", color = Color(0xFFCBD5E1)) },
                    colors = textFieldColors,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = quantityText,
                        onValueChange = { quantityText = it },
                        label = { Text("Quantidade", color = Color(0xFFCBD5E1)) },
                        colors = textFieldColors,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = unitText,
                        onValueChange = { unitText = it },
                        label = { Text("Unidade", color = Color(0xFFCBD5E1)) },
                        colors = textFieldColors,
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Validade Mês e Ano
                Surface(
                    color = Color(0xFF090D16),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0xFF334155)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Possui Data de Validade (MM/AAAA)?",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFFFBBF24)
                            )
                            Switch(
                                checked = hasExpiration,
                                onCheckedChange = { hasExpiration = it }
                            )
                        }

                        if (hasExpiration) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = monthText,
                                    onValueChange = { if (it.length <= 2 && it.all { c -> c.isDigit() }) monthText = it },
                                    label = { Text("Mês (01-12)", color = Color(0xFFCBD5E1)) },
                                    colors = textFieldColors,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = yearText,
                                    onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) yearText = it },
                                    label = { Text("Ano (AAAA)", color = Color(0xFFCBD5E1)) },
                                    colors = textFieldColors,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val qty = quantityText.replace(",", ".").toDoubleOrNull() ?: item.quantity
                    val expM = if (hasExpiration) monthText.toIntOrNull()?.coerceIn(1, 12) else null
                    val expY = if (hasExpiration) yearText.toIntOrNull()?.coerceIn(2020, 2100) else null
                    onConfirm(
                        name,
                        barcode.ifBlank { null },
                        selectedCategory,
                        selectedLocation,
                        qty,
                        unitText,
                        expM,
                        expY,
                        resetBatteryMaintenanceNow
                    )
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFF59E0B),
                    contentColor = Color(0xFF090D16)
                )
            ) {
                Text("Salvar Alterações", fontWeight = FontWeight.ExtraBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = Color(0xFFCBD5E1))
            }
        }
    )
}

/**
 * Modal para Editar / Girar a Validade (Mês e Ano) de um item existente do estoque.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditExpirationMonthYearDialog(
    itemName: String,
    currentExpirationMillis: Long?,
    onDismiss: () -> Unit,
    onConfirm: (month: Int, year: Int) -> Unit
) {
    val initCal = remember(currentExpirationMillis) {
        Calendar.getInstance().apply {
            if (currentExpirationMillis != null) {
                timeInMillis = currentExpirationMillis
            } else {
                add(Calendar.MONTH, 6)
            }
        }
    }

    var monthText by remember { mutableStateOf(String.format(Locale.US, "%02d", initCal.get(Calendar.MONTH) + 1)) }
    var yearText by remember { mutableStateOf(initCal.get(Calendar.YEAR).toString()) }

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        focusedBorderColor = Color(0xFFFBBF24),
        unfocusedBorderColor = Color(0xFF475569)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF111827),
        titleContentColor = Color.White,
        textContentColor = Color.White,
        title = { Text("Definir Validade (Mês / Ano)", fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Item: $itemName",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFBBF24)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = monthText,
                        onValueChange = { if (it.length <= 2 && it.all { c -> c.isDigit() }) monthText = it },
                        label = { Text("Mês (01-12)", color = Color(0xFFCBD5E1)) },
                        colors = textFieldColors,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = yearText,
                        onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) yearText = it },
                        label = { Text("Ano (AAAA)", color = Color(0xFFCBD5E1)) },
                        colors = textFieldColors,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Text("Selecionar Mês:", fontSize = 11.sp, color = Color(0xFFCBD5E1))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(MONTH_OPTIONS) { (mNum, mLabel) ->
                        val isSel = monthText.toIntOrNull() == mNum
                        FilterChip(
                            selected = isSel,
                            onClick = { monthText = String.format(Locale.US, "%02d", mNum) },
                            label = {
                                Text(
                                    mLabel,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSel) Color(0xFF090D16) else Color.White
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFFBBF24),
                                containerColor = Color(0xFF1E293B)
                            )
                        )
                    }
                }

                Text("Selecionar Ano:", fontSize = 11.sp, color = Color(0xFFCBD5E1))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(YEAR_OPTIONS) { yNum ->
                        val isSel = yearText.toIntOrNull() == yNum
                        FilterChip(
                            selected = isSel,
                            onClick = { yearText = yNum.toString() },
                            label = {
                                Text(
                                    yNum.toString(),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSel) Color.White else Color(0xFFCBD5E1)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF0284C7),
                                containerColor = Color(0xFF1E293B)
                            )
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val m = (monthText.toIntOrNull() ?: 12).coerceIn(1, 12)
                    val y = (yearText.toIntOrNull() ?: 2027).coerceIn(2020, 2100)
                    onConfirm(m, y)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFF59E0B),
                    contentColor = Color(0xFF090D16)
                )
            ) {
                Text("Atualizar Validade", fontWeight = FontWeight.ExtraBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = Color(0xFFCBD5E1))
            }
        }
    )
}

@Composable
fun AddPoiDialog(
    currentUserLat: Double = -22.9519,
    currentUserLon: Double = -43.1850,
    onDismiss: () -> Unit,
    onConfirm: (title: String, type: PoiType, lat: Double, lon: Double, address: String, phone: String?) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(PoiType.FAMILY_MEETING) }
    var latText by remember { mutableStateOf(String.format(Locale.US, "%.5f", currentUserLat)) }
    var lonText by remember { mutableStateOf(String.format(Locale.US, "%.5f", currentUserLon)) }
    var address by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        focusedBorderColor = Color(0xFFFBBF24),
        unfocusedBorderColor = Color(0xFF475569)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF111827),
        titleContentColor = Color.White,
        textContentColor = Color.White,
        title = { Text("Cadastrar Ponto Seguro / Encontro", fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Nome do Ponto", color = Color(0xFFCBD5E1)) },
                    colors = textFieldColors,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Tipo de Ponto:", style = MaterialTheme.typography.labelMedium, color = Color(0xFFFBBF24), fontWeight = FontWeight.Bold)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    PoiType.values().forEach { t ->
                        val isSel = selectedType == t
                        FilterChip(
                            selected = isSel,
                            onClick = { selectedType = t },
                            label = {
                                Text(
                                    t.displayName,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSel) Color(0xFF090D16) else Color.White
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFFBBF24),
                                containerColor = Color(0xFF1E293B)
                            )
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Coordenadas Geográficas:", fontSize = 11.sp, color = Color(0xFFCBD5E1))
                    TextButton(
                        onClick = {
                            latText = String.format(Locale.US, "%.5f", currentUserLat)
                            lonText = String.format(Locale.US, "%.5f", currentUserLon)
                        },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Icon(Icons.Default.MyLocation, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Usar Minha Posição", fontSize = 11.sp, color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold)
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = latText,
                        onValueChange = { latText = it },
                        label = { Text("Latitude", color = Color(0xFFCBD5E1)) },
                        colors = textFieldColors,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = lonText,
                        onValueChange = { lonText = it },
                        label = { Text("Longitude", color = Color(0xFFCBD5E1)) },
                        colors = textFieldColors,
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Endereço / Referência", color = Color(0xFFCBD5E1)) },
                    colors = textFieldColors,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Telefone / Frequência Rádio (Opcional)", color = Color(0xFFCBD5E1)) },
                    colors = textFieldColors,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        val lat = latText.replace(",", ".").toDoubleOrNull() ?: currentUserLat
                        val lon = lonText.replace(",", ".").toDoubleOrNull() ?: currentUserLon
                        onConfirm(title.trim(), selectedType, lat, lon, address.ifBlank { "Coordenada GPS" }, phone.ifBlank { null })
                        onDismiss()
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFF59E0B),
                    contentColor = Color(0xFF090D16)
                )
            ) {
                Text("Salvar Ponto", fontWeight = FontWeight.ExtraBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = Color(0xFFCBD5E1))
            }
        }
    )
}

@Composable
fun AddProfileDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, role: FamilyRole, weightKg: Double, bloodType: String, allergies: String, medications: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var selectedRole by remember { mutableStateOf(FamilyRole.ADULT) }
    var weightText by remember { mutableStateOf("70") }
    var bloodType by remember { mutableStateOf("O+") }
    var allergies by remember { mutableStateOf("") }
    var medications by remember { mutableStateOf("") }

    val bloodTypes = listOf("O+", "O-", "A+", "A-", "B+", "B-", "AB+", "AB-")

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        focusedBorderColor = Color(0xFFFBBF24),
        unfocusedBorderColor = Color(0xFF475569)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF111827),
        titleContentColor = Color.White,
        textContentColor = Color.White,
        title = { Text("Cadastrar Ficha Familiar", fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nome do Familiar", color = Color(0xFFCBD5E1)) },
                    colors = textFieldColors,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Perfil Etário:", style = MaterialTheme.typography.labelMedium, color = Color(0xFFFBBF24), fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FamilyRole.values().forEach { r ->
                        val isSel = selectedRole == r
                        FilterChip(
                            selected = isSel,
                            onClick = {
                                selectedRole = r
                                if (r == FamilyRole.CHILD) weightText = "24" else weightText = "70"
                            },
                            label = {
                                Text(
                                    r.displayName,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSel) Color(0xFF090D16) else Color.White
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFFBBF24),
                                containerColor = Color(0xFF1E293B)
                            )
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = weightText,
                        onValueChange = { weightText = it },
                        label = { Text("Peso (kg)", color = Color(0xFFCBD5E1)) },
                        colors = textFieldColors,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = bloodType,
                        onValueChange = { bloodType = it },
                        label = { Text("Tipo Sanguíneo", color = Color(0xFFCBD5E1)) },
                        colors = textFieldColors,
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(bloodTypes) { bt ->
                        val isSel = bloodType.equals(bt, ignoreCase = true)
                        FilterChip(
                            selected = isSel,
                            onClick = { bloodType = bt },
                            label = {
                                Text(
                                    bt,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSel) Color.White else Color(0xFFCBD5E1)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFDC2626),
                                containerColor = Color(0xFF1E293B)
                            )
                        )
                    }
                }

                OutlinedTextField(
                    value = allergies,
                    onValueChange = { allergies = it },
                    label = { Text("Alergias Medicamentosas / Alimentares", color = Color(0xFFCBD5E1)) },
                    placeholder = { Text("Ex: Penicilina, Dipirona ou Nenhuma", fontSize = 11.sp, color = Color(0xFF64748B)) },
                    colors = textFieldColors,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = medications,
                    onValueChange = { medications = it },
                    label = { Text("Medicações Contínuas / Posologia", color = Color(0xFFCBD5E1)) },
                    placeholder = { Text("Ex: Losartana 50mg 1x ao dia ou Nenhuma", fontSize = 11.sp, color = Color(0xFF64748B)) },
                    colors = textFieldColors,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        val w = weightText.replace(",", ".").toDoubleOrNull() ?: 60.0
                        onConfirm(
                            name.trim(),
                            selectedRole,
                            w,
                            bloodType.ifBlank { "O+" },
                            allergies.ifBlank { "Nenhuma declarada" },
                            medications.ifBlank { "Nenhuma de uso contínuo" }
                        )
                        onDismiss()
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFF59E0B),
                    contentColor = Color(0xFF090D16)
                )
            ) {
                Text("Salvar Familiar", fontWeight = FontWeight.ExtraBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = Color(0xFFCBD5E1))
            }
        }
    )
}

/**
 * Dialog para Editar um membro familiar já cadastrado.
 */
@Composable
fun EditProfileDialog(
    profile: FamilyProfileEntity,
    onDismiss: () -> Unit,
    onConfirm: (id: Long, name: String, role: FamilyRole, weightKg: Double, bloodType: String, allergies: String, medications: String) -> Unit
) {
    var name by remember(profile.id) { mutableStateOf(profile.name) }
    var selectedRole by remember(profile.id) { mutableStateOf(profile.role) }
    var weightText by remember(profile.id) { mutableStateOf(String.format(Locale.US, "%.0f", profile.weightKg)) }
    var bloodType by remember(profile.id) { mutableStateOf(profile.bloodType) }
    var allergies by remember(profile.id) { mutableStateOf(profile.allergies) }
    var medications by remember(profile.id) { mutableStateOf(profile.medications) }

    val bloodTypes = listOf("O+", "O-", "A+", "A-", "B+", "B-", "AB+", "AB-")

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        focusedBorderColor = Color(0xFFFBBF24),
        unfocusedBorderColor = Color(0xFF475569)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF111827),
        titleContentColor = Color.White,
        textContentColor = Color.White,
        title = { Text("Editar Ficha Familiar", fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nome do Familiar", color = Color(0xFFCBD5E1)) },
                    colors = textFieldColors,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Perfil Etário:", style = MaterialTheme.typography.labelMedium, color = Color(0xFFFBBF24), fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FamilyRole.values().forEach { r ->
                        val isSel = selectedRole == r
                        FilterChip(
                            selected = isSel,
                            onClick = { selectedRole = r },
                            label = {
                                Text(
                                    r.displayName,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSel) Color(0xFF090D16) else Color.White
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFFBBF24),
                                containerColor = Color(0xFF1E293B)
                            )
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = weightText,
                        onValueChange = { weightText = it },
                        label = { Text("Peso (kg)", color = Color(0xFFCBD5E1)) },
                        colors = textFieldColors,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = bloodType,
                        onValueChange = { bloodType = it },
                        label = { Text("Tipo Sanguíneo", color = Color(0xFFCBD5E1)) },
                        colors = textFieldColors,
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(bloodTypes) { bt ->
                        val isSel = bloodType.equals(bt, ignoreCase = true)
                        FilterChip(
                            selected = isSel,
                            onClick = { bloodType = bt },
                            label = {
                                Text(
                                    bt,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSel) Color.White else Color(0xFFCBD5E1)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFDC2626),
                                containerColor = Color(0xFF1E293B)
                            )
                        )
                    }
                }

                OutlinedTextField(
                    value = allergies,
                    onValueChange = { allergies = it },
                    label = { Text("Alergias Medicamentosas / Alimentares", color = Color(0xFFCBD5E1)) },
                    colors = textFieldColors,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = medications,
                    onValueChange = { medications = it },
                    label = { Text("Medicações Contínuas / Posologia", color = Color(0xFFCBD5E1)) },
                    colors = textFieldColors,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        val w = weightText.replace(",", ".").toDoubleOrNull() ?: profile.weightKg
                        onConfirm(
                            profile.id,
                            name.trim(),
                            selectedRole,
                            w,
                            bloodType.ifBlank { "O+" },
                            allergies.ifBlank { "Nenhuma declarada" },
                            medications.ifBlank { "Nenhuma de uso contínuo" }
                        )
                        onDismiss()
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFF59E0B),
                    contentColor = Color(0xFF090D16)
                )
            ) {
                Text("Salvar Alterações", fontWeight = FontWeight.ExtraBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = Color(0xFFCBD5E1))
            }
        }
    )
}

/**
 * Dialog para classificar e vincular um PDF de Receita Médica ou Documento da Família.
 */
@Composable
fun ImportFamilyDocDialog(
    pendingUri: Uri,
    defaultTitle: String,
    familyMembers: List<FamilyProfileEntity>,
    onDismiss: () -> Unit,
    onConfirm: (uri: Uri, title: String, docType: String, memberName: String) -> Unit
) {
    var title by remember(pendingUri) { mutableStateOf(defaultTitle) }
    var selectedType by remember { mutableStateOf("RECEITA_MEDICA") }
    var selectedMember by remember { mutableStateOf(familyMembers.firstOrNull()?.name ?: "Toda a Família") }

    val docTypes = listOf(
        "RECEITA_MEDICA" to "💊 Receita Médica",
        "DOCUMENTO_PESSOAL" to "🪪 RG / CPF / Certidão",
        "LAUDO_EXAME" to "📋 Laudo / Exame",
        "VACINACAO" to "💉 Carteira Vacinação"
    )

    val memberOptions = remember(familyMembers) {
        listOf("Toda a Família") + familyMembers.map { it.name }
    }

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        focusedBorderColor = Color(0xFFFBBF24),
        unfocusedBorderColor = Color(0xFF475569)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF111827),
        titleContentColor = Color.White,
        textContentColor = Color.White,
        title = { Text("Importar PDF da Família", fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Título da Receita ou Documento", color = Color(0xFFCBD5E1)) },
                    placeholder = { Text("Ex: Receita Uso Contínuo / RG Digitalizado", fontSize = 11.sp, color = Color(0xFF64748B)) },
                    colors = textFieldColors,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Categoria do PDF:", style = MaterialTheme.typography.labelMedium, color = Color(0xFFFBBF24), fontWeight = FontWeight.Bold)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    docTypes.forEach { (code, label) ->
                        val isSel = selectedType == code
                        FilterChip(
                            selected = isSel,
                            onClick = { selectedType = code },
                            label = {
                                Text(
                                    label,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSel) Color(0xFF090D16) else Color.White
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFFBBF24),
                                containerColor = Color(0xFF1E293B)
                            )
                        )
                    }
                }

                Text("Vincular ao Familiar:", style = MaterialTheme.typography.labelMedium, color = Color(0xFFFBBF24), fontWeight = FontWeight.Bold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(memberOptions) { member ->
                        val isSel = selectedMember == member
                        FilterChip(
                            selected = isSel,
                            onClick = { selectedMember = member },
                            label = {
                                Text(
                                    member,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSel) Color.White else Color(0xFFCBD5E1)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF0284C7),
                                containerColor = Color(0xFF1E293B)
                            )
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        pendingUri,
                        title.trim().ifBlank { "Documento Familiar" },
                        selectedType,
                        selectedMember
                    )
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFF59E0B),
                    contentColor = Color(0xFF090D16)
                )
            ) {
                Text("Salvar PDF Offline", fontWeight = FontWeight.ExtraBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = Color(0xFFCBD5E1))
            }
        }
    )
}

/**
 * Visualizador Nativo Offline de PDFs de Receitas Médicas e Documentos da Família.
 */
@Composable
fun FamilyPdfViewerDialog(
    document: FamilyDocument,
    onDismiss: () -> Unit
) {
    var currentPage by remember { mutableIntStateOf(0) }
    var totalPages by remember { mutableIntStateOf(document.pageCount.coerceAtLeast(1)) }
    var renderedBitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(document.filePath, currentPage) {
        withContext(Dispatchers.IO) {
            try {
                val file = File(document.filePath)
                if (file.exists()) {
                    val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                    val renderer = PdfRenderer(pfd)
                    totalPages = renderer.pageCount.coerceAtLeast(1)
                    val safeIndex = currentPage.coerceIn(0, totalPages - 1)
                    val page = renderer.openPage(safeIndex)
                    val width = (page.width * 2).coerceAtLeast(600)
                    val height = (page.height * 2).coerceAtLeast(800)
                    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(android.graphics.Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()
                    renderer.close()
                    pfd.close()
                    renderedBitmap = bmp
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = Color(0xFF090D16),
            modifier = Modifier.fillMaxSize()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Topo do leitor de PDF
                Surface(
                    color = Color(0xFF111827),
                    border = BorderStroke(1.dp, Color(0xFF1E293B)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = document.title,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Familiar: ${document.memberName} • Pág. ${currentPage + 1} de $totalPages",
                                fontSize = 11.sp,
                                color = Color(0xFFFBBF24)
                            )
                        }
                        Button(
                            onClick = onDismiss,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFDC2626),
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Fechar", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Corpo com a página renderizada + texto extraído
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val bmp = renderedBitmap
                    if (bmp != null) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "Página do PDF",
                                contentScale = ContentScale.FillWidth,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    } else {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp)
                                .background(Color(0xFF111827), RoundedCornerShape(10.dp))
                        ) {
                            CircularProgressIndicator(color = Color(0xFFFBBF24))
                        }
                    }

                    if (totalPages > 1) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedButton(
                                onClick = { if (currentPage > 0) currentPage-- },
                                enabled = currentPage > 0
                            ) {
                                Text("← Página Anterior", color = Color.White)
                            }
                            Text(
                                text = "${currentPage + 1} / $totalPages",
                                color = Color(0xFFFBBF24),
                                fontWeight = FontWeight.ExtraBold
                            )
                            OutlinedButton(
                                onClick = { if (currentPage < totalPages - 1) currentPage++ },
                                enabled = currentPage < totalPages - 1
                            ) {
                                Text("Próxima Página →", color = Color.White)
                            }
                        }
                    }

                    // Transcrição em texto extraída do PDF
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFF334155)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Texto Extraído para Leitura Rápida:",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 12.sp,
                                color = Color(0xFF38BDF8)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = document.extractedText,
                                fontSize = 12.sp,
                                lineHeight = 18.sp,
                                color = Color(0xFFE2E8F0)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Dialog para o usuário criar uma Nota / Protocolo Familiar adicional na Base de Conhecimento da IA.
 */
@Composable
fun AddManualChunkDialog(
    onDismiss: () -> Unit,
    onConfirm: (docTitle: String, category: String, pageNumber: Int, contentText: String) -> Unit
) {
    var docTitle by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("GENERAL") }
    var pageText by remember { mutableStateOf("1") }
    var contentText by remember { mutableStateOf("") }

    val categories = listOf(
        "GENERAL" to "Plano Familiar",
        "WAR_CONFLICT" to "Defesa / Conflito",
        "WATER" to "Água",
        "MEDS" to "Saúde / Primeiros Socorros",
        "EVACUATION" to "Evacuação 72h"
    )

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        focusedBorderColor = Color(0xFFFBBF24),
        unfocusedBorderColor = Color(0xFF475569)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF111827),
        titleContentColor = Color.White,
        textContentColor = Color.White,
        title = { Text("Adicionar Nota / Protocolo à IA", fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = docTitle,
                    onValueChange = { docTitle = it },
                    label = { Text("Título do Protocolo", color = Color(0xFFCBD5E1)) },
                    placeholder = { Text("Ex: Plano de Evacuação da Nossa Família", fontSize = 11.sp, color = Color(0xFF64748B)) },
                    colors = textFieldColors,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Categoria:", style = MaterialTheme.typography.labelMedium, color = Color(0xFFFBBF24), fontWeight = FontWeight.Bold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(categories) { (code, label) ->
                        val isSel = selectedCategory == code
                        FilterChip(
                            selected = isSel,
                            onClick = { selectedCategory = code },
                            label = {
                                Text(
                                    label,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSel) Color(0xFF090D16) else Color.White
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFFBBF24),
                                containerColor = Color(0xFF1E293B)
                            )
                        )
                    }
                }

                OutlinedTextField(
                    value = contentText,
                    onValueChange = { contentText = it },
                    label = { Text("Instruções para o RAG da IA", color = Color(0xFFCBD5E1)) },
                    placeholder = { Text("Digite as orientações que também serão respondidas no Chat da IA...", fontSize = 11.sp, color = Color(0xFF64748B)) },
                    colors = textFieldColors,
                    minLines = 4,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (docTitle.isNotBlank() && contentText.isNotBlank()) {
                        val pg = pageText.toIntOrNull() ?: 1
                        onConfirm(docTitle.trim(), selectedCategory, pg, contentText.trim())
                        onDismiss()
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFF59E0B),
                    contentColor = Color(0xFF090D16)
                )
            ) {
                Text("Salvar na Base IA", fontWeight = FontWeight.ExtraBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = Color(0xFFCBD5E1))
            }
        }
    )
}

@Composable
fun ConfirmClearDialog(
    title: String,
    message: String,
    confirmButtonText: String = "Sim, Apagar Tudo",
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF111827),
        titleContentColor = Color.White,
        textContentColor = Color(0xFFE2E8F0),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.DeleteForever, contentDescription = null, tint = Color(0xFFF87171))
                Spacer(modifier = Modifier.width(8.dp))
                Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
            }
        },
        text = {
            Text(message, fontSize = 13.sp, lineHeight = 19.sp)
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm()
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFDC2626),
                    contentColor = Color.White
                )
            ) {
                Text(confirmButtonText, fontWeight = FontWeight.ExtraBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = Color(0xFFCBD5E1))
            }
        }
    )
}

/**
 * Central de Backup Automático & Restauração sem Entupimento do Celular.
 * Salva até 3 arquivos compactados (.zip) na pasta pública Download/ResilienceHub_Backups,
 * que permanecem seguros mesmo se o app for desinstalado ou o cache/dados for limpo.
 */
@Composable
fun BackupRestoreDialog(
    state: MainUiState,
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    LaunchedEffect(Unit) {
        viewModel.refreshAvailableBackups()
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri: Uri? ->
        uri?.let { viewModel.exportBackupToUri(it) }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.restoreFromBackupUri(it) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF111827),
        titleContentColor = Color.White,
        textContentColor = Color.White,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Backup, contentDescription = null, tint = Color(0xFF34D399))
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text("Cofre de Backup & Restauração", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                    Text(
                        "Auto-Backup Inteligente • Zero Entupimento (Máx. 3 arquivos)",
                        fontSize = 10.sp,
                        color = Color(0xFF34D399),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                // Explicação clara da proteção contra perda de dados
                Surface(
                    color = Color(0xFF090D16),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0xFF10B981)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "🛡 Proteção Automática Contra Desinstalação",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF34D399)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "• A cada alteração no Estoque, Família, PDFs ou Mapa, o app salva uma cópia compactada (.zip) na pasta pública Download/ResilienceHub_Backups.\n" +
                                "• Para NÃO entupir seu celular, o sistema mantém no máximo 3 backups recentes e apaga os antigos sozinho.",
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            color = Color(0xFFE2E8F0)
                        )
                    }
                }

                // Status ou feedback recente
                state.backupStatusMessage?.let { msg ->
                    Surface(
                        color = Color(0xFF0C2434),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color(0xFF38BDF8)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(10.dp)
                        ) {
                            if (state.isBackupBusy) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = Color(0xFFFBBF24)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(
                                text = msg,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }

                // Botões principais de ação rápida
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = { viewModel.triggerManualBackupNow() },
                        enabled = !state.isBackupBusy,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF059669),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(5.dp))
                        Text("Backup Agora", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                    }

                    Button(
                        onClick = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) },
                        enabled = !state.isBackupBusy,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFF59E0B),
                            contentColor = Color(0xFF090D16)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(5.dp))
                        Text("Buscar .ZIP", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }

                OutlinedButton(
                    onClick = {
                        exportLauncher.launch("ResilienceHub_Backup_Seguro.zip")
                    },
                    enabled = !state.isBackupBusy,
                    border = BorderStroke(1.dp, Color(0xFF38BDF8)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.CloudUpload, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "Salvar Cópia em Outra Pasta / Google Drive",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF38BDF8)
                    )
                }

                Divider(color = Color(0xFF1E293B))

                // Lista dos últimos 3 snapshots automáticos encontrados na pasta
                Text(
                    text = "Últimos Backups Automáticos no Aparelho (${state.availableBackups.size}/3):",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFFFBBF24)
                )

                if (state.availableBackups.isEmpty()) {
                    Text(
                        text = "Nenhum backup recente listado ainda ou você reinstalou o app agora. Toque em 'Buscar .ZIP' acima e selecione o arquivo na sua pasta Download/ResilienceHub_Backups para recuperar tudo!",
                        fontSize = 11.sp,
                        color = Color(0xFFCBD5E1)
                    )
                } else {
                    state.availableBackups.forEachIndexed { idx, snap ->
                        Surface(
                            color = Color(0xFF1E293B),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(
                                1.dp,
                                if (idx == 0) Color(0xFF34D399) else Color(0xFF334155)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.padding(10.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (idx == 0) "⭐ Mais Recente: ${snap.formattedDate}" else "Snapshot: ${snap.formattedDate}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "${snap.itemsCount} itens • ${snap.profilesCount} familiares • ${snap.pdfsCount} PDFs • ${snap.sizeKb} KB",
                                        fontSize = 10.sp,
                                        color = Color(0xFF38BDF8)
                                    )
                                    Text(
                                        text = "Pasta: Download/ResilienceHub_Backups",
                                        fontSize = 9.sp,
                                        color = Color(0xFF94A3B8)
                                    )
                                }

                                Button(
                                    onClick = { viewModel.restoreFromSnapshot(snap) },
                                    enabled = !state.isBackupBusy,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFF59E0B),
                                        contentColor = Color(0xFF090D16)
                                    ),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text("Restaurar", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Fechar", color = Color(0xFFFBBF24), fontWeight = FontWeight.ExtraBold)
            }
        }
    )
}
