package com.example.ui.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.FamilyRole
import com.example.data.model.ItemCategory
import com.example.data.model.PoiType
import com.example.data.model.StorageLocation
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddItemDialog(
    onDismiss: () -> Unit,
    onConfirm: (
        name: String,
        barcode: String?,
        category: ItemCategory,
        location: StorageLocation,
        quantity: Double,
        unit: String,
        expirationMonth: Int?,
        expirationYear: Int?
    ) -> Unit
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
                    onValueChange = { name = it },
                    label = { Text("Nome do Item", color = Color(0xFFCBD5E1)) },
                    colors = textFieldColors,
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_item_name")
                )

                // Código de barras com botão de escaneamento por câmera
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = barcode,
                        onValueChange = { barcode = it },
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
                Text("Categoria:", style = MaterialTheme.typography.labelMedium, color = Color(0xFFFBBF24), fontWeight = FontWeight.Bold)
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
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        val qty = quantityText.replace(",", ".").toDoubleOrNull() ?: 1.0
                        val expMonth = if (hasExpiration) monthText.toIntOrNull()?.coerceIn(1, 12) else null
                        val expYear = if (hasExpiration) yearText.toIntOrNull()?.coerceIn(2020, 2100) else null
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
                        onDismiss()
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFF59E0B),
                    contentColor = Color(0xFF090D16)
                ),
                modifier = Modifier.testTag("btn_confirm_add_item")
            ) {
                Text("Salvar Item", fontWeight = FontWeight.ExtraBold)
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
            }
        )
    }
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
 * Dialog para o usuário criar seu próprio Manual / Protocolo / Anotação de Sobrevivência offline.
 */
@Composable
fun AddManualChunkDialog(
    onDismiss: () -> Unit,
    onConfirm: (docTitle: String, category: String, pageNumber: Int, contentText: String) -> Unit
) {
    var docTitle by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("WATER") }
    var pageText by remember { mutableStateOf("1") }
    var contentText by remember { mutableStateOf("") }

    val categories = listOf(
        "WATER" to "Água & Purificação",
        "MEDS" to "Primeiros Socorros",
        "EVACUATION" to "Evacuação 72h",
        "ENERGY_GEAR" to "Energia & Rádio",
        "GENERAL" to "Protocolo Geral"
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
        title = { Text("Criar Novo Manual / Protocolo", fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = docTitle,
                    onValueChange = { docTitle = it },
                    label = { Text("Título do Manual / Protocolo", color = Color(0xFFCBD5E1)) },
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
                    value = pageText,
                    onValueChange = { if (it.all { c -> c.isDigit() }) pageText = it },
                    label = { Text("Página / Seção", color = Color(0xFFCBD5E1)) },
                    colors = textFieldColors,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = contentText,
                    onValueChange = { contentText = it },
                    label = { Text("Conteúdo / Instruções Detalhadas", color = Color(0xFFCBD5E1)) },
                    placeholder = { Text("Digite as orientações que ficarão salvas e pesquisáveis na IA Local e FTS...", fontSize = 11.sp, color = Color(0xFF64748B)) },
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
                Text("Salvar Manual", fontWeight = FontWeight.ExtraBold)
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
