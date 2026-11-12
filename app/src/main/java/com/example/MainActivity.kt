package com.example

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.MainViewModel
import com.example.ui.components.*
import com.example.ui.theme.ResilienceHubTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            var showAddItemDialog by remember { mutableStateOf(false) }
            var showAddPoiDialog by remember { mutableStateOf(false) }
            var showAddProfileDialog by remember { mutableStateOf(false) }
            var showAddManualDialog by remember { mutableStateOf(false) }
            var showConfirmClearAllApp by remember { mutableStateOf(false) }
            var showBackupRestoreDialog by remember { mutableStateOf(false) }

            val permissionsToRequest = remember {
                val list = mutableListOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    list.add(Manifest.permission.POST_NOTIFICATIONS)
                }
                list.toTypedArray()
            }

            val multiplePermissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestMultiplePermissions()
            ) { _ ->
                // Permissões de hardware avaliadas
            }

            LaunchedEffect(Unit) {
                multiplePermissionLauncher.launch(permissionsToRequest)
            }

            ResilienceHubTheme(isCrisisMode = state.crisisMode) {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = if (state.crisisMode) Color.Black else Color(0xFF090D16),
                    topBar = {
                        ResilienceTopBar(
                            crisisMode = state.crisisMode,
                            isSosActive = state.isSosActive,
                            isWhistleActive = state.isWhistleActive,
                            isLiveTracking = state.isLiveTracking,
                            onToggleCrisisMode = { viewModel.toggleCrisisMode() },
                            onOpenBackupDialog = { showBackupRestoreDialog = true },
                            onRequestClearAllData = { showConfirmClearAllApp = true }
                        )
                    },
                    bottomBar = {
                        ResilienceBottomBar(
                            selectedTab = state.selectedTab,
                            onTabSelected = { viewModel.setSelectedTab(it) },
                            crisisMode = state.crisisMode
                        )
                    },
                    floatingActionButton = {
                        when (state.selectedTab) {
                            0 -> {
                                FloatingActionButton(
                                    onClick = { showAddItemDialog = true },
                                    containerColor = Color(0xFFF59E0B),
                                    contentColor = Color(0xFF090D16),
                                    modifier = Modifier.testTag("fab_add_item")
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Adicionar Item")
                                }
                            }
                            1 -> {
                                FloatingActionButton(
                                    onClick = { showAddPoiDialog = true },
                                    containerColor = Color(0xFFF59E0B),
                                    contentColor = Color(0xFF090D16),
                                    modifier = Modifier.testTag("fab_add_poi")
                                ) {
                                    Icon(Icons.Default.AddLocationAlt, contentDescription = "Adicionar Ponto")
                                }
                            }
                            4 -> {
                                FloatingActionButton(
                                    onClick = { showAddProfileDialog = true },
                                    containerColor = Color(0xFFF59E0B),
                                    contentColor = Color(0xFF090D16),
                                    modifier = Modifier.testTag("fab_add_profile")
                                ) {
                                    Icon(Icons.Default.PersonAdd, contentDescription = "Adicionar Familiar")
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        when (state.selectedTab) {
                            0 -> InventoryScreen(
                                state = state,
                                viewModel = viewModel,
                                onOpenAddDialog = { showAddItemDialog = true }
                            )
                            1 -> OfflineMapScreen(
                                state = state,
                                viewModel = viewModel,
                                onOpenAddPoiDialog = { showAddPoiDialog = true }
                            )
                            2 -> ManualsAiScreen(
                                state = state,
                                viewModel = viewModel,
                                onOpenAddManualDialog = { showAddManualDialog = true }
                            )
                            3 -> EmergencyToolsScreen(
                                state = state,
                                viewModel = viewModel
                            )
                            4 -> FamilyVaultScreen(
                                state = state,
                                viewModel = viewModel,
                                onOpenAddProfileDialog = { showAddProfileDialog = true }
                            )
                        }
                    }

                    if (showAddItemDialog) {
                        AddItemDialog(
                            existingItems = state.allRegisteredItems,
                            onDismiss = { showAddItemDialog = false },
                            onIncrementExistingItem = { itemId, deltaQty ->
                                viewModel.updateItemQuantity(itemId, deltaQty)
                            },
                            onConfirmWithLotOption = { name, barcode, category, location, qty, unit, expMonth, expYear, forceSeparateLot ->
                                viewModel.addNewItemWithMonthYear(
                                    name = name,
                                    category = category,
                                    location = location,
                                    quantity = qty,
                                    unit = unit,
                                    expirationMonth = expMonth,
                                    expirationYear = expYear,
                                    barcode = barcode,
                                    forceSeparateLot = forceSeparateLot
                                )
                            }
                        )
                    }

                    if (showAddPoiDialog) {
                        AddPoiDialog(
                            currentUserLat = state.currentLat,
                            currentUserLon = state.currentLon,
                            onDismiss = { showAddPoiDialog = false },
                            onConfirm = { title, type, lat, lon, address, phone ->
                                viewModel.addNewPoi(title, type, lat, lon, address, phone)
                            }
                        )
                    }

                    if (showAddProfileDialog) {
                        AddProfileDialog(
                            onDismiss = { showAddProfileDialog = false },
                            onConfirm = { name, role, weightKg, bloodType, allergies, medications ->
                                viewModel.addNewProfile(name, role, weightKg, bloodType, allergies, medications)
                            }
                        )
                    }

                    if (showAddManualDialog) {
                        AddManualChunkDialog(
                            onDismiss = { showAddManualDialog = false },
                            onConfirm = { docTitle, category, pageNumber, contentText ->
                                viewModel.addNewManualChunk(docTitle, category, pageNumber, contentText)
                            }
                        )
                    }

                    if (showConfirmClearAllApp) {
                        ConfirmClearDialog(
                            title = "Apagar Dados Fictícios?",
                            message = "Isso apagará todos os itens fictícios de Estoque, Família e Pontos do Mapa para você cadastrar os seus próprios dados (a Base Oficial de Sobrevivência do Chat IA será mantida).",
                            confirmButtonText = "Sim, Zerar Dados",
                            onDismiss = { showConfirmClearAllApp = false },
                            onConfirm = { viewModel.clearAllFictionalData() }
                        )
                    }

                    if (showBackupRestoreDialog) {
                        BackupRestoreDialog(
                            state = state,
                            viewModel = viewModel,
                            onDismiss = { showBackupRestoreDialog = false }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResilienceTopBar(
    crisisMode: Boolean,
    isSosActive: Boolean,
    isWhistleActive: Boolean,
    isLiveTracking: Boolean,
    onToggleCrisisMode: () -> Unit,
    onOpenBackupDialog: () -> Unit,
    onRequestClearAllData: () -> Unit
) {
    TopAppBar(
        title = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Shield,
                        contentDescription = null,
                        tint = Color(0xFFFBBF24),
                        modifier = Modifier.size(19.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "ResilienceHub",
                        fontWeight = FontWeight.Black,
                        fontSize = 17.sp,
                        color = Color.White
                    )

                    if (isSosActive || isWhistleActive) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = Color(0xFFDC2626),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "BEACON",
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    } else if (isLiveTracking) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = Color(0xFF059669),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "GPS VIVO",
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(Color(0xFF34D399), CircleShape)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "100% OFFLINE • PROTOCOLO FEFO",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF34D399)
                    )
                }
            }
        },
        actions = {
            // Botão global de Backup Automático & Restauração
            IconButton(
                onClick = onOpenBackupDialog,
                modifier = Modifier.testTag("btn_open_backup_dialog")
            ) {
                Icon(
                    Icons.Default.Backup,
                    contentDescription = "Backup e Restauração",
                    tint = Color(0xFF34D399)
                )
            }

            // Botão global para apagar todos os dados fictícios do app de uma só vez
            IconButton(
                onClick = onRequestClearAllData,
                modifier = Modifier.testTag("btn_clear_all_fictional_data")
            ) {
                Icon(
                    Icons.Default.DeleteSweep,
                    contentDescription = "Apagar Dados Fictícios",
                    tint = Color(0xFFF87171)
                )
            }

            // Switch MODO CRISE OLED com alto contraste
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(end = 6.dp)
                    .background(
                        if (crisisMode) Color(0xFF141414) else Color(0xFF1E293B),
                        RoundedCornerShape(8.dp)
                    )
                    .border(
                        1.dp,
                        if (crisisMode) Color(0xFFFBBF24) else Color(0xFF334155),
                        RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "CRISE OLED",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        color = if (crisisMode) Color(0xFFFBBF24) else Color.White
                    )
                    Text(
                        text = if (crisisMode) "#000000 ATIVO" else "TÁTICO ESCURO",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (crisisMode) Color(0xFF34D399) else Color(0xFFCBD5E1)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Switch(
                    checked = crisisMode,
                    onCheckedChange = { onToggleCrisisMode() },
                    modifier = Modifier
                        .scale(0.85f)
                        .testTag("switch_crisis_mode")
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = if (crisisMode) Color.Black else Color(0xFF111827)
        )
    )
}

private fun Modifier.scale(scale: Float): Modifier = this.then(
    Modifier.size((48 * scale).dp, (32 * scale).dp)
)

@Composable
fun ResilienceBottomBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    crisisMode: Boolean
) {
    val itemColors = NavigationBarItemDefaults.colors(
        selectedIconColor = Color(0xFF090D16),
        selectedTextColor = Color(0xFFFBBF24),
        indicatorColor = Color(0xFFFBBF24),
        unselectedIconColor = Color(0xFFCBD5E1),
        unselectedTextColor = Color(0xFFCBD5E1)
    )

    NavigationBar(
        containerColor = if (crisisMode) Color.Black else Color(0xFF111827),
        tonalElevation = 8.dp,
        modifier = Modifier.testTag("resilience_bottom_bar")
    ) {
        NavigationBarItem(
            selected = selectedTab == 0,
            onClick = { onTabSelected(0) },
            icon = { Icon(Icons.Default.Inventory2, contentDescription = "Estoque") },
            label = { Text("Estoque", fontSize = 11.sp, fontWeight = if (selectedTab == 0) FontWeight.ExtraBold else FontWeight.SemiBold) },
            colors = itemColors,
            modifier = Modifier.testTag("tab_inventory")
        )
        NavigationBarItem(
            selected = selectedTab == 1,
            onClick = { onTabSelected(1) },
            icon = { Icon(Icons.Default.Explore, contentDescription = "Mapas") },
            label = { Text("Mapas", fontSize = 11.sp, fontWeight = if (selectedTab == 1) FontWeight.ExtraBold else FontWeight.SemiBold) },
            colors = itemColors,
            modifier = Modifier.testTag("tab_maps")
        )
        NavigationBarItem(
            selected = selectedTab == 2,
            onClick = { onTabSelected(2) },
            icon = { Icon(Icons.Default.Forum, contentDescription = "Chat IA") },
            label = { Text("Chat IA", fontSize = 11.sp, fontWeight = if (selectedTab == 2) FontWeight.ExtraBold else FontWeight.SemiBold) },
            colors = itemColors,
            modifier = Modifier.testTag("tab_manuals")
        )
        NavigationBarItem(
            selected = selectedTab == 3,
            onClick = { onTabSelected(3) },
            icon = { Icon(Icons.Default.FlashOn, contentDescription = "Ferramentas") },
            label = { Text("Ferramentas", fontSize = 11.sp, fontWeight = if (selectedTab == 3) FontWeight.ExtraBold else FontWeight.SemiBold) },
            colors = itemColors,
            modifier = Modifier.testTag("tab_tools")
        )
        NavigationBarItem(
            selected = selectedTab == 4,
            onClick = { onTabSelected(4) },
            icon = { Icon(Icons.Default.Shield, contentDescription = "Família") },
            label = { Text("Família", fontSize = 11.sp, fontWeight = if (selectedTab == 4) FontWeight.ExtraBold else FontWeight.SemiBold) },
            colors = itemColors,
            modifier = Modifier.testTag("tab_family")
        )
    }
}
