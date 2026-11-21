package com.example.ui.screens

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PermMedia
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PermMedia
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.InstalledAppInfo
import com.example.data.IntruderLogEntity
import com.example.data.MediaType
import com.example.data.VaultMediaEntity
import com.example.data.VaultRepository
import com.example.data.VaultScope
import com.example.security.AutoLockDelay
import com.example.security.BiometricAvailability
import com.example.security.VaultConfigSnapshot
import com.example.security.VaultCryptoEngine
import com.example.ui.components.ObsidianVaultBackground
import com.example.ui.state.ImportStatus
import com.example.ui.theme.ChampagneGold
import com.example.ui.theme.CoolSlateText
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.DeepSlateNavy
import com.example.ui.theme.GoldWash
import com.example.ui.theme.ImperialGold
import com.example.ui.theme.IvoryText
import com.example.ui.theme.MidnightNavy
import com.example.ui.theme.NavyOutline
import com.example.ui.theme.ObsidianBlack
import kotlinx.serialization.Serializable

@Serializable
sealed interface VaultRoute {
    @Serializable
    data object Dashboard : VaultRoute

    @Serializable
    data object Photos : VaultRoute

    @Serializable
    data object Media : VaultRoute

    @Serializable
    data object Shield : VaultRoute

    @Serializable
    data object Settings : VaultRoute
}

private data class VaultNavItem(
    val route: VaultRoute,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultScaffoldScreen(
    vaultScope: VaultScope,
    config: VaultConfigSnapshot,
    allMedia: List<VaultMediaEntity>,
    photos: List<VaultMediaEntity>,
    videos: List<VaultMediaEntity>,
    audioTracks: List<VaultMediaEntity>,
    intruderLogs: List<IntruderLogEntity>,
    installedApps: List<InstalledAppInfo>,
    isLoadingApps: Boolean,
    accessibilityServiceActive: Boolean,
    biometricAvailability: BiometricAvailability,
    hardwareKeystoreActive: Boolean,
    importStatus: ImportStatus,
    secretSwitchSheetVisible: Boolean,
    navigateHomeTrigger: Int,
    repository: VaultRepository,
    cryptoEngine: VaultCryptoEngine,
    onImportMedia: (List<Uri>, MediaType) -> Unit,
    onExportMedia: (VaultMediaEntity, Uri) -> Unit,
    onDeleteMedia: (VaultMediaEntity) -> Unit,
    onLoadInstalledApps: () -> Unit,
    onSetAppLockEnabled: (Boolean) -> Unit,
    onToggleAppLocked: (InstalledAppInfo, Boolean) -> Unit,
    onClearIntruderLogs: () -> Unit,
    onUpdateBiometric: (Boolean) -> Unit,
    onUpdateAutoLock: (AutoLockDelay) -> Unit,
    onUpdateScreenshotShield: (Boolean) -> Unit,
    onUpdateStealthCover: (Boolean) -> Unit,
    onUpdateThreeFingerGesture: (Boolean) -> Unit,
    onUpdateIntruderSelfie: (Boolean) -> Unit,
    onChangeMasterPin: (String, String) -> Boolean,
    onUpdateDecoyPin: (String, String) -> Boolean,
    onRemoveDecoyPin: (String) -> Boolean,
    onVerifyMasterForSecretSwitch: (String) -> Boolean,
    onBiometricSwitchToMaster: () -> Unit,
    onDismissSecretSwitchSheet: () -> Unit,
    onLockVault: () -> Unit
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    var preferredMediaTab by remember { mutableIntStateOf(0) }

    val navItems = remember(vaultScope) {
        buildList {
            add(VaultNavItem(VaultRoute.Dashboard, "Vault", Icons.Filled.Home, Icons.Outlined.Home, "nav_vault"))
            add(VaultNavItem(VaultRoute.Photos, "Photos", Icons.Filled.PhotoLibrary, Icons.Outlined.PhotoLibrary, "nav_photos"))
            add(VaultNavItem(VaultRoute.Media, "Media", Icons.Filled.PermMedia, Icons.Outlined.PermMedia, "nav_media"))
            if (vaultScope == VaultScope.MASTER) {
                add(VaultNavItem(VaultRoute.Shield, "Shield", Icons.Filled.Shield, Icons.Outlined.Shield, "nav_shield"))
            }
            add(VaultNavItem(VaultRoute.Settings, "Settings", Icons.Filled.Settings, Icons.Outlined.Settings, "nav_settings"))
        }
    }

    fun navigateTo(route: VaultRoute) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    LaunchedEffect(navigateHomeTrigger) {
        if (navigateHomeTrigger > 0) {
            navigateTo(VaultRoute.Dashboard)
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 680.dp

        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "DARK ROOM",
                                style = MaterialTheme.typography.headlineMedium,
                                color = ChampagneGold
                            )
                            Text(
                                text = "PRIVATE SECURITY VAULT",
                                style = MaterialTheme.typography.labelSmall,
                                color = CoolSlateText
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = onLockVault,
                            modifier = Modifier.testTag("panic_lock_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Lock,
                                contentDescription = "Panic lock vault",
                                tint = ImperialGold
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MidnightNavy.copy(alpha = 0.94f),
                        titleContentColor = ChampagneGold
                    )
                )
            },
            bottomBar = {
                if (!useNavRail) {
                    NavigationBar(
                        containerColor = MidnightNavy,
                        contentColor = ChampagneGold,
                        windowInsets = WindowInsets.navigationBars
                    ) {
                        navItems.forEach { item ->
                            val selected = currentDestination?.hasRoute(item.route::class) == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTo(item.route) },
                                modifier = Modifier.testTag(item.testTag),
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = item.label
                                    )
                                },
                                label = { Text(item.label) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = ObsidianBlack,
                                    selectedTextColor = ChampagneGold,
                                    indicatorColor = ImperialGold,
                                    unselectedIconColor = CoolSlateText,
                                    unselectedTextColor = CoolSlateText
                                )
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            ObsidianVaultBackground(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                Row(modifier = Modifier.fillMaxSize()) {
                    if (useNavRail) {
                        NavigationRail(
                            modifier = Modifier.fillMaxHeight(),
                            containerColor = MidnightNavy,
                            contentColor = ChampagneGold
                        ) {
                            Spacer(modifier = Modifier.height(12.dp))
                            navItems.forEach { item ->
                                val selected = currentDestination?.hasRoute(item.route::class) == true
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { navigateTo(item.route) },
                                    modifier = Modifier.testTag(item.testTag),
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                            contentDescription = item.label
                                        )
                                    },
                                    label = { Text(item.label) },
                                    colors = NavigationRailItemDefaults.colors(
                                        selectedIconColor = ObsidianBlack,
                                        selectedTextColor = ChampagneGold,
                                        indicatorColor = ImperialGold,
                                        unselectedIconColor = CoolSlateText,
                                        unselectedTextColor = CoolSlateText
                                    )
                                )
                            }
                        }
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        NavHost(
                            navController = navController,
                            startDestination = VaultRoute.Dashboard
                        ) {
                            composable<VaultRoute.Dashboard> {
                                VaultDashboardScreen(
                                    vaultScope = vaultScope,
                                    config = config,
                                    allMedia = allMedia,
                                    intruderCount = intruderLogs.size,
                                    accessibilityActive = accessibilityServiceActive,
                                    hardwareKeystoreActive = hardwareKeystoreActive,
                                    onOpenPhotos = { navigateTo(VaultRoute.Photos) },
                                    onOpenVideos = {
                                        preferredMediaTab = 0
                                        navigateTo(VaultRoute.Media)
                                    },
                                    onOpenAudio = {
                                        preferredMediaTab = 1
                                        navigateTo(VaultRoute.Media)
                                    },
                                    onOpenShield = {
                                        if (vaultScope == VaultScope.MASTER) {
                                            navigateTo(VaultRoute.Shield)
                                        } else {
                                            navigateTo(VaultRoute.Settings)
                                        }
                                    },
                                    onQuickLock = onLockVault
                                )
                            }

                            composable<VaultRoute.Photos> {
                                PhotoVaultScreen(
                                    photos = photos,
                                    repository = repository,
                                    onImportPhotos = { uris -> onImportMedia(uris, MediaType.PHOTO) },
                                    onExportPhoto = onExportMedia,
                                    onDeletePhoto = onDeleteMedia
                                )
                            }

                            composable<VaultRoute.Media> {
                                MediaVaultScreen(
                                    initialTab = preferredMediaTab,
                                    videos = videos,
                                    audioTracks = audioTracks,
                                    repository = repository,
                                    cryptoEngine = cryptoEngine,
                                    onImportMedia = onImportMedia,
                                    onExportMedia = onExportMedia,
                                    onDeleteMedia = onDeleteMedia
                                )
                            }

                            composable<VaultRoute.Shield> {
                                SecurityCenterScreen(
                                    appLockEnabled = config.appLockEnabled,
                                    accessibilityServiceActive = accessibilityServiceActive,
                                    installedApps = installedApps,
                                    isLoadingApps = isLoadingApps,
                                    intruderLogs = intruderLogs,
                                    repository = repository,
                                    onLoadInstalledApps = onLoadInstalledApps,
                                    onSetAppLockEnabled = onSetAppLockEnabled,
                                    onToggleAppLocked = onToggleAppLocked,
                                    onClearIntruderLogs = onClearIntruderLogs
                                )
                            }

                            composable<VaultRoute.Settings> {
                                VaultSettingsScreen(
                                    vaultScope = vaultScope,
                                    config = config,
                                    biometricAvailability = biometricAvailability,
                                    hardwareKeystoreActive = hardwareKeystoreActive,
                                    onUpdateBiometric = onUpdateBiometric,
                                    onUpdateAutoLock = onUpdateAutoLock,
                                    onUpdateScreenshotShield = onUpdateScreenshotShield,
                                    onUpdateStealthCover = onUpdateStealthCover,
                                    onUpdateThreeFingerGesture = onUpdateThreeFingerGesture,
                                    onUpdateIntruderSelfie = onUpdateIntruderSelfie,
                                    onChangeMasterPin = onChangeMasterPin,
                                    onUpdateDecoyPin = onUpdateDecoyPin,
                                    onRemoveDecoyPin = onRemoveDecoyPin,
                                    onLockNow = onLockVault
                                )
                            }
                        }

                        // Encryption in-progress overlay
                        if (importStatus is ImportStatus.Encrypting) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(ObsidianBlack.copy(alpha = 0.78f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Card(
                                    shape = RoundedCornerShape(22.dp),
                                    colors = CardDefaults.cardColors(containerColor = DeepSlateNavy),
                                    border = BorderStroke(1.dp, ImperialGold.copy(alpha = 0.5f))
                                ) {
                                    Column(
                                        modifier = Modifier.padding(28.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        CircularProgressIndicator(color = ImperialGold)
                                        Text(
                                            text = importStatus.label,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = IvoryText
                                        )
                                        Text(
                                            text = "AES-256-GCM · File ${importStatus.current} of ${importStatus.total}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = ChampagneGold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (secretSwitchSheetVisible) {
        SecretVaultMasterSwitchDialog(
            biometricReady = config.biometricEnabled &&
                biometricAvailability == BiometricAvailability.AVAILABLE,
            onDismiss = onDismissSecretSwitchSheet,
            onVerifyMasterPin = onVerifyMasterForSecretSwitch,
            onBiometricClick = onBiometricSwitchToMaster
        )
    }
}

@Composable
private fun SecretVaultMasterSwitchDialog(
    biometricReady: Boolean,
    onDismiss: () -> Unit,
    onVerifyMasterPin: (String) -> Boolean,
    onBiometricClick: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DeepSlateNavy,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(shape = RoundedCornerShape(8.dp), color = GoldWash) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = null,
                        tint = ImperialGold,
                        modifier = Modifier.padding(6.dp)
                    )
                }
                Text("Secret Master Vault", color = ChampagneGold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Three-finger gesture detected. Authenticate with your Master PIN to enter the primary vault.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CoolSlateText
                )
                OutlinedTextField(
                    value = pin,
                    onValueChange = {
                        if (it.length <= 6 && it.all(Char::isDigit)) {
                            pin = it
                            errorText = null
                        }
                    },
                    label = { Text("Master PIN (4–6 digits)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ImperialGold,
                        unfocusedBorderColor = NavyOutline,
                        focusedLabelColor = ChampagneGold,
                        unfocusedLabelColor = CoolSlateText,
                        focusedTextColor = IvoryText,
                        unfocusedTextColor = IvoryText
                    )
                )
                errorText?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = CrimsonAlert)
                }
                if (biometricReady) {
                    OutlinedButton(
                        onClick = onBiometricClick,
                        border = BorderStroke(1.dp, ImperialGold.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Fingerprint, contentDescription = null, tint = ImperialGold)
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("Use Fingerprint", color = ChampagneGold)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (!onVerifyMasterPin(pin)) {
                        errorText = "Invalid Master PIN"
                        pin = ""
                    }
                },
                enabled = pin.length in 4..6,
                colors = ButtonDefaults.buttonColors(containerColor = ImperialGold, contentColor = ObsidianBlack)
            ) {
                Text("Enter Vault")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = CoolSlateText)
            }
        }
    )
}
