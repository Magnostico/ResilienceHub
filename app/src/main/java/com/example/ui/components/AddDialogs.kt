package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddItemDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, barcode: String?, category: ItemCategory, location: StorageLocation, quantity: Double, unit: String, expirationDays: Int?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var barcode by remember { mutableStateOf("") }
    var showBarcodeScanner by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf(ItemCategory.WATER) }
    var selectedLocation by remember { mutableStateOf(StorageLocation.APARTMENT) }
    var quantityText by remember { mutableStateOf("1.0") }
    var unitText by remember { mutableStateOf("L") }
    var expirationDaysText by remember { mutableStateOf("180") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF111827),
        titleContentColor = Color.White,
        textContentColor = Color.White,
        title = { Text("Cadastrar Item no Estoque", fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nome do Item", color = Color(0xFFCBD5E1)) },
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
                        label = { Text("Código EAN-13", color = Color(0xFFCBD5E1)) },
                        leadingIcon = {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = Color(0xFF38BDF8))
                        },
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
                Text("Local de Armazenamento:", style = MaterialTheme.typography.labelMedium, color = Color(0xFFFBBF24))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StorageLocation.values().forEach { loc ->
                        FilterChip(
                            selected = selectedLocation == loc,
                            onClick = { selectedLocation = loc },
                            label = {
                                Text(
                                    if (loc == StorageLocation.APARTMENT) "Apartamento" else "Mochila 72h",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        )
                    }
                }

                // Category selector
                Text("Categoria:", style = MaterialTheme.typography.labelMedium, color = Color(0xFFFBBF24))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ItemCategory.values().forEach { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = {
                                selectedCategory = cat
                                if (cat == ItemCategory.WATER) unitText = "L"
                                else if (cat == ItemCategory.FOOD) unitText = "kg"
                                else unitText = "unidades"
                            },
                            label = { Text(cat.displayName, fontWeight = FontWeight.Bold) }
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = quantityText,
                        onValueChange = { quantityText = it },
                        label = { Text("Quantidade", color = Color(0xFFCBD5E1)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = unitText,
                        onValueChange = { unitText = it },
                        label = { Text("Unidade (L, kg, un)", color = Color(0xFFCBD5E1)) },
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = expirationDaysText,
                    onValueChange = { expirationDaysText = it },
                    label = { Text("Validade em Dias (Opcional)", color = Color(0xFFCBD5E1)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        val qty = quantityText.toDoubleOrNull() ?: 1.0
                        val expDays = expirationDaysText.toIntOrNull()
                        onConfirm(name, barcode.ifBlank { null }, selectedCategory, selectedLocation, qty, unitText, expDays)
                        onDismiss()
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFF59E0B),
                    contentColor = Color(0xFF090D16)
                ),
                modifier = Modifier.testTag("btn_confirm_add_item")
            ) {
                Text("Salvar", fontWeight = FontWeight.ExtraBold)
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

@Composable
fun AddPoiDialog(
    onDismiss: () -> Unit,
    onConfirm: (title: String, type: PoiType, lat: Double, lon: Double, address: String, phone: String?) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(PoiType.FAMILY_MEETING) }
    var latText by remember { mutableStateOf("-22.9519") }
    var lonText by remember { mutableStateOf("-43.1850") }
    var address by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }

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
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Tipo de Ponto:", style = MaterialTheme.typography.labelMedium, color = Color(0xFFFBBF24))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    PoiType.values().forEach { t ->
                        FilterChip(
                            selected = selectedType == t,
                            onClick = { selectedType = t },
                            label = { Text(t.displayName, fontWeight = FontWeight.Bold) }
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = latText,
                        onValueChange = { latText = it },
                        label = { Text("Latitude", color = Color(0xFFCBD5E1)) },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = lonText,
                        onValueChange = { lonText = it },
                        label = { Text("Longitude", color = Color(0xFFCBD5E1)) },
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Endereço / Referência", color = Color(0xFFCBD5E1)) },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Telefone / Frequência Rádio (Opcional)", color = Color(0xFFCBD5E1)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        val lat = latText.toDoubleOrNull() ?: -22.9519
                        val lon = lonText.toDoubleOrNull() ?: -43.1850
                        onConfirm(title, selectedType, lat, lon, address, phone.ifBlank { null })
                        onDismiss()
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFF59E0B),
                    contentColor = Color(0xFF090D16)
                )
            ) {
                Text("Salvar", fontWeight = FontWeight.ExtraBold)
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
    var allergies by remember { mutableStateOf("Nenhuma") }
    var medications by remember { mutableStateOf("Nenhuma de uso contínuo") }

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
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Perfil Etário:", style = MaterialTheme.typography.labelMedium, color = Color(0xFFFBBF24))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FamilyRole.values().forEach { r ->
                        FilterChip(
                            selected = selectedRole == r,
                            onClick = {
                                selectedRole = r
                                if (r == FamilyRole.CHILD) weightText = "24" else weightText = "70"
                            },
                            label = { Text(r.displayName, fontWeight = FontWeight.Bold) }
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = weightText,
                        onValueChange = { weightText = it },
                        label = { Text("Peso (kg)", color = Color(0xFFCBD5E1)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = bloodType,
                        onValueChange = { bloodType = it },
                        label = { Text("Tipo Sanguíneo", color = Color(0xFFCBD5E1)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = allergies,
                    onValueChange = { allergies = it },
                    label = { Text("Alergias Medicamentosas / Alimentares", color = Color(0xFFCBD5E1)) },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = medications,
                    onValueChange = { medications = it },
                    label = { Text("Medicações Contínuas / Posologia", color = Color(0xFFCBD5E1)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        val w = weightText.toDoubleOrNull() ?: 60.0
                        onConfirm(
                            name,
                            selectedRole,
                            w,
                            bloodType.ifBlank { "O+" },
                            allergies.ifBlank { "Nenhuma" },
                            medications.ifBlank { "Nenhuma" }
                        )
                        onDismiss()
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFF59E0B),
                    contentColor = Color(0xFF090D16)
                )
            ) {
                Text("Salvar", fontWeight = FontWeight.ExtraBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = Color(0xFFCBD5E1))
            }
        }
    )
}
