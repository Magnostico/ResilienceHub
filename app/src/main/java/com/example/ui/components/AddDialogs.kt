package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.FamilyRole
import com.example.data.model.ItemCategory
import com.example.data.model.PoiType
import com.example.data.model.StorageLocation

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddItemDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, category: ItemCategory, location: StorageLocation, quantity: Double, unit: String, expirationDays: Int?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(ItemCategory.WATER) }
    var selectedLocation by remember { mutableStateOf(StorageLocation.APARTMENT) }
    var quantityText by remember { mutableStateOf("1.0") }
    var unitText by remember { mutableStateOf("unidades") }
    var expirationDaysText by remember { mutableStateOf("180") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cadastrar Item no Estoque") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nome do Item") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_item_name")
                )

                // Location selector
                Text("Local de Armazenamento:", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StorageLocation.values().forEach { loc ->
                        FilterChip(
                            selected = selectedLocation == loc,
                            onClick = { selectedLocation = loc },
                            label = { Text(if (loc == StorageLocation.APARTMENT) "Apartamento" else "Mochila 72h") }
                        )
                    }
                }

                // Category selector
                Text("Categoria:", style = MaterialTheme.typography.labelMedium)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ItemCategory.values().forEach { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = {
                                selectedCategory = cat
                                if (cat == ItemCategory.WATER) unitText = "L"
                                else if (cat == ItemCategory.FOOD) unitText = "kg"
                            },
                            label = { Text(cat.displayName) }
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = quantityText,
                        onValueChange = { quantityText = it },
                        label = { Text("Quantidade") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = unitText,
                        onValueChange = { unitText = it },
                        label = { Text("Unidade (L, kg, un)") },
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = expirationDaysText,
                    onValueChange = { expirationDaysText = it },
                    label = { Text("Validade em Dias (Opcional)") },
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
                        onConfirm(name, selectedCategory, selectedLocation, qty, unitText, expDays)
                        onDismiss()
                    }
                },
                modifier = Modifier.testTag("btn_confirm_add_item")
            ) {
                Text("Salvar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
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
        title = { Text("Cadastrar Ponto de Apoio / Encontro") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Nome do Ponto") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Tipo:", style = MaterialTheme.typography.labelMedium)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    PoiType.values().forEach { t ->
                        FilterChip(
                            selected = selectedType == t,
                            onClick = { selectedType = t },
                            label = { Text(t.displayName) }
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = latText,
                        onValueChange = { latText = it },
                        label = { Text("Latitude") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = lonText,
                        onValueChange = { lonText = it },
                        label = { Text("Longitude") },
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Endereço / Referência") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Telefone / Frequência Rádio (Opcional)") },
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
                }
            ) {
                Text("Salvar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
