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
                    topBar = {
                        ResilienceTopBar(
                            crisisMode = state.crisisMode,
                            isSosActive = state.isSosActive,
                            isWhistleActive = state.isWhistleActive,
                            onToggleCrisisMode = { viewModel.toggleCrisisMode() }
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
                        if (state.selectedTab == 0) {
                            FloatingActionButton(
                                onClick = { showAddItemDialog = true },
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.testTag("fab_add_item")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Adicionar Item")
                            }
                        } else if (state.selectedTab == 1) {
                            FloatingActionButton(
                                onClick = { showAddPoiDialog = true },
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.testTag("fab_add_poi")
                            ) {
                                Icon(Icons.Default.AddLocationAlt, contentDescription = "Adicionar Ponto")
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
                                viewModel = viewModel
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
                            onDismiss = { showAddItemDialog = false },
                            onConfirm = { name, barcode, category, location, qty, unit, expDays ->
                                viewModel.addNewItem(name, category, location, qty, unit, expDays, barcode)
                            }
                        )
                    }

                    if (showAddPoiDialog) {
                        AddPoiDialog(
                            onDismiss = { showAddPoiDialog = false },
                            onConfirm = { title, type, lat, lon, address, phone ->
                                viewModel.addNewPoi(title, type, lat, lon, address, phone)
                            }
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
    onToggleCrisisMode: () -> Unit
) {
    TopAppBar(
        title = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Shield,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(19.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "ResilienceHub",
                        fontWeight = FontWeight.Black,
                        fontSize = 17.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (isSosActive || isWhistleActive) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = Color.Red,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "BEACON ATIVO",
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(Color(0xFF10B981), androidx.compose.foundation.shape.CircleShape)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "100% OFFLINE • PROTOCOLO FEFO",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF10B981)
                    )
                }
            }
        },
        actions = {
            // Switch MODO CRISE OLED com alto contraste
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(
                        if (crisisMode) Color(0xFF1A1A1A) else MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(8.dp)
                    )
                    .border(
                        1.dp,
                        if (crisisMode) Color(0xFFF59E0B) else Color.Transparent,
                        RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "CRISE OLED",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        color = if (crisisMode) Color(0xFFFBBF24) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (crisisMode) "#000000 ATIVO" else "NORMAL",
                        fontSize = 8.sp,
                        color = if (crisisMode) Color(0xFF10B981) else MaterialTheme.colorScheme.outline
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
            containerColor = if (crisisMode) Color.Black else MaterialTheme.colorScheme.surface
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
    NavigationBar(
        containerColor = if (crisisMode) Color.Black else MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("resilience_bottom_bar")
    ) {
        NavigationBarItem(
            selected = selectedTab == 0,
            onClick = { onTabSelected(0) },
            icon = { Icon(Icons.Default.Inventory2, contentDescription = "Estoque") },
            label = { Text("Estoque", fontSize = 10.sp, fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal) },
            modifier = Modifier.testTag("tab_inventory")
        )
        NavigationBarItem(
            selected = selectedTab == 1,
            onClick = { onTabSelected(1) },
            icon = { Icon(Icons.Default.Explore, contentDescription = "Mapas") },
            label = { Text("Mapas", fontSize = 10.sp, fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal) },
            modifier = Modifier.testTag("tab_maps")
        )
        NavigationBarItem(
            selected = selectedTab == 2,
            onClick = { onTabSelected(2) },
            icon = { Icon(Icons.Default.MenuBook, contentDescription = "Manuais") },
            label = { Text("Manuais", fontSize = 10.sp, fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Normal) },
            modifier = Modifier.testTag("tab_manuals")
        )
        NavigationBarItem(
            selected = selectedTab == 3,
            onClick = { onTabSelected(3) },
            icon = { Icon(Icons.Default.FlashOn, contentDescription = "Ferramentas") },
            label = { Text("Ferramentas", fontSize = 10.sp, fontWeight = if (selectedTab == 3) FontWeight.Bold else FontWeight.Normal) },
            modifier = Modifier.testTag("tab_tools")
        )
        NavigationBarItem(
            selected = selectedTab == 4,
            onClick = { onTabSelected(4) },
            icon = { Icon(Icons.Default.Shield, contentDescription = "Família") },
            label = { Text("Família", fontSize = 10.sp, fontWeight = if (selectedTab == 4) FontWeight.Bold else FontWeight.Normal) },
            modifier = Modifier.testTag("tab_family")
        )
    }
}
