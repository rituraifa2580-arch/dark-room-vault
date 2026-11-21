package com.example.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults.SecondaryIndicator
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.graphics.drawable.toBitmap
import com.example.applock.AccessibilityServiceHelper
import com.example.data.InstalledAppInfo
import com.example.data.IntruderLogEntity
import com.example.data.VaultRepository
import com.example.ui.components.formatTimestamp
import com.example.ui.theme.ChampagneGold
import com.example.ui.theme.CoolSlateText
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.CrimsonWash
import com.example.ui.theme.DeepSlateNavy
import com.example.ui.theme.ElevatedNavyCard
import com.example.ui.theme.EmeraldVerified
import com.example.ui.theme.GoldWash
import com.example.ui.theme.ImperialGold
import com.example.ui.theme.IvoryText
import com.example.ui.theme.MidnightNavy
import com.example.ui.theme.NavyOutline
import com.example.ui.theme.ObsidianBlack

@Composable
fun SecurityCenterScreen(
    appLockEnabled: Boolean,
    accessibilityServiceActive: Boolean,
    installedApps: List<InstalledAppInfo>,
    isLoadingApps: Boolean,
    intruderLogs: List<IntruderLogEntity>,
    repository: VaultRepository,
    onLoadInstalledApps: () -> Unit,
    onSetAppLockEnabled: (Boolean) -> Unit,
    onToggleAppLocked: (InstalledAppInfo, Boolean) -> Unit,
    onClearIntruderLogs: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var showAccessibilityConsentDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedIntruderPhotoPath by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        if (installedApps.isEmpty()) {
            onLoadInstalledApps()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MidnightNavy,
            contentColor = ChampagneGold,
            indicator = { tabPositions ->
                SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = ImperialGold
                )
            }
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                modifier = Modifier.testTag("shield_tab_applock"),
                text = { Text("App Lock") }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                modifier = Modifier.testTag("shield_tab_intruders"),
                text = { Text("Intruder Log (${intruderLogs.size})") }
            )
        }

        if (selectedTab == 0) {
            val filteredApps = remember(installedApps, searchQuery) {
                if (searchQuery.isBlank()) {
                    installedApps
                } else {
                    val q = searchQuery.trim().lowercase()
                    installedApps.filter {
                        it.appName.lowercase().contains(q) || it.packageName.lowercase().contains(q)
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Accessibility Service Consent & Master Switch Card
                item {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = DeepSlateNavy),
                        border = BorderStroke(
                            1.dp,
                            if (accessibilityServiceActive && appLockEnabled) {
                                EmeraldVerified.copy(alpha = 0.5f)
                            } else {
                                ImperialGold.copy(alpha = 0.4f)
                            }
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Shield,
                                    contentDescription = null,
                                    tint = if (accessibilityServiceActive && appLockEnabled) EmeraldVerified else ImperialGold,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.size(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "App Lock Protection",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = IvoryText
                                    )
                                    Text(
                                        text = when {
                                            !accessibilityServiceActive -> "Accessibility Service permission required"
                                            appLockEnabled -> "Active · Guarding selected third-party apps"
                                            else -> "Service granted · Toggle on to enforce locks"
                                        },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (accessibilityServiceActive && appLockEnabled) EmeraldVerified else CoolSlateText
                                    )
                                }
                                Switch(
                                    checked = appLockEnabled && accessibilityServiceActive,
                                    onCheckedChange = { checked ->
                                        if (checked && !accessibilityServiceActive) {
                                            showAccessibilityConsentDialog = true
                                        } else {
                                            onSetAppLockEnabled(checked)
                                        }
                                    },
                                    modifier = Modifier.testTag("app_lock_master_switch"),
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = ObsidianBlack,
                                        checkedTrackColor = ImperialGold,
                                        uncheckedTrackColor = ElevatedNavyCard
                                    )
                                )
                            }

                            if (!accessibilityServiceActive) {
                                Button(
                                    onClick = { showAccessibilityConsentDialog = true },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("grant_accessibility_button"),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = ImperialGold,
                                        contentColor = ObsidianBlack
                                    )
                                ) {
                                    Icon(Icons.Filled.AccessibilityNew, contentDescription = null)
                                    Spacer(modifier = Modifier.size(8.dp))
                                    Text("Review Disclosure & Enable Service", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // Search bar for apps
                item {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Search installed apps...", color = CoolSlateText) },
                        leadingIcon = {
                            Icon(Icons.Filled.Search, contentDescription = null, tint = ImperialGold)
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ImperialGold,
                            unfocusedBorderColor = NavyOutline,
                            focusedTextColor = IvoryText,
                            unfocusedTextColor = IvoryText,
                            focusedContainerColor = DeepSlateNavy,
                            unfocusedContainerColor = DeepSlateNavy
                        )
                    )
                }

                if (isLoadingApps) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = ImperialGold)
                        }
                    }
                } else if (filteredApps.isEmpty()) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = DeepSlateNavy),
                            border = BorderStroke(1.dp, NavyOutline),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.Filled.Apps, contentDescription = null, tint = ImperialGold)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("No matching launchable apps found", color = CoolSlateText)
                            }
                        }
                    }
                } else {
                    items(filteredApps, key = { it.packageName }) { app ->
                        AppLockRowCard(
                            app = app,
                            onToggle = { locked -> onToggleAppLocked(app, locked) }
                        )
                    }
                }
            }
        } else {
            // Intruder Attempt Log Tab
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "INTRUDER AUDIT TRAIL",
                                style = MaterialTheme.typography.labelLarge,
                                color = ChampagneGold
                            )
                            Text(
                                text = "Failed PIN & biometric attempts stored locally",
                                style = MaterialTheme.typography.bodyMedium,
                                color = CoolSlateText
                            )
                        }
                        if (intruderLogs.isNotEmpty()) {
                            OutlinedButton(
                                onClick = onClearIntruderLogs,
                                border = BorderStroke(1.dp, CrimsonAlert.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    Icons.Filled.DeleteSweep,
                                    contentDescription = "Clear logs",
                                    tint = CrimsonAlert,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.size(6.dp))
                                Text("Clear", color = CrimsonAlert)
                            }
                        }
                    }
                }

                if (intruderLogs.isEmpty()) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = DeepSlateNavy),
                            border = BorderStroke(1.dp, NavyOutline),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(28.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.VerifiedUser,
                                    contentDescription = null,
                                    tint = EmeraldVerified,
                                    modifier = Modifier.size(40.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "No Unauthorized Attempts",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = IvoryText
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Any invalid PIN or biometric failure on Vault Lock or App Lock will be logged here with timestamp.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = CoolSlateText
                                )
                            }
                        }
                    }
                } else {
                    items(intruderLogs, key = { it.id }) { log ->
                        IntruderLogCard(
                            log = log,
                            onViewPhoto = { log.encryptedPhotoPath?.let { selectedIntruderPhotoPath = it } }
                        )
                    }
                }
            }
        }
    }

    // Clear User Consent / Prominent Disclosure Dialog before opening Accessibility Settings
    if (showAccessibilityConsentDialog) {
        AlertDialog(
            onDismissRequest = { showAccessibilityConsentDialog = false },
            containerColor = DeepSlateNavy,
            icon = {
                Icon(Icons.Filled.Security, contentDescription = null, tint = ImperialGold)
            },
            title = {
                Text("Accessibility Service Disclosure", color = IvoryText)
            },
            text = {
                Text(
                    text = "Dark Room uses Android's AccessibilityService strictly on-device to detect when a protected third-party app enters the foreground so it can display the PIN/Biometric App Lock screen.\n\n" +
                        "• Window content retrieval is disabled (canRetrieveWindowContent = false).\n" +
                        "• Zero screen text, passwords, or keystrokes are read.\n" +
                        "• Zero data ever leaves your device.\n\n" +
                        "Tap Continue to enable \"Dark Room App Lock\" in Android Accessibility Settings.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CoolSlateText
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showAccessibilityConsentDialog = false
                        onSetAppLockEnabled(true)
                        AccessibilityServiceHelper.openAccessibilitySettings(context)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ImperialGold,
                        contentColor = ObsidianBlack
                    )
                ) {
                    Text("I Consent · Open Settings")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAccessibilityConsentDialog = false }) {
                    Text("Cancel", color = CoolSlateText)
                }
            }
        )
    }

    selectedIntruderPhotoPath?.let { path ->
        IntruderPhotoDialog(
            encryptedPath = path,
            repository = repository,
            onDismiss = { selectedIntruderPhotoPath = null }
        )
    }
}

@Composable
private fun AppLockRowCard(
    app: InstalledAppInfo,
    onToggle: (Boolean) -> Unit
) {
    val iconBitmap = remember(app.packageName) {
        try {
            app.icon?.toBitmap(width = 96, height = 96)?.asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DeepSlateNavy),
        border = BorderStroke(1.dp, if (app.isLocked) ImperialGold.copy(alpha = 0.45f) else NavyOutline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (iconBitmap != null) {
                Image(
                    bitmap = iconBitmap,
                    contentDescription = app.appName,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
            } else {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = ElevatedNavyCard,
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Apps, contentDescription = null, tint = ImperialGold)
                    }
                }
            }
            Spacer(modifier = Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.appName,
                    style = MaterialTheme.typography.titleMedium,
                    color = IvoryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.labelSmall,
                    color = CoolSlateText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Switch(
                checked = app.isLocked,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = ObsidianBlack,
                    checkedTrackColor = ImperialGold,
                    uncheckedTrackColor = ElevatedNavyCard
                )
            )
        }
    }
}

@Composable
private fun IntruderLogCard(
    log: IntruderLogEntity,
    onViewPhoto: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DeepSlateNavy),
        border = BorderStroke(1.dp, CrimsonAlert.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = CrimsonWash,
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.WarningAmber,
                        contentDescription = null,
                        tint = CrimsonAlert,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${log.source} · ${log.outcome}",
                    style = MaterialTheme.typography.titleMedium,
                    color = IvoryText
                )
                Text(
                    text = "Attempt #${log.attemptCount}" +
                        if (log.enteredLength > 0) " · ${log.enteredLength}-digit entry" else "",
                    style = MaterialTheme.typography.labelMedium,
                    color = CrimsonAlert
                )
                Text(
                    text = formatTimestamp(log.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = CoolSlateText
                )
            }
            if (!log.encryptedPhotoPath.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = GoldWash,
                    border = BorderStroke(1.dp, ImperialGold.copy(alpha = 0.5f)),
                    modifier = Modifier.clickable(onClick = onViewPhoto)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Filled.Lock, contentDescription = null, tint = ImperialGold, modifier = Modifier.size(14.dp))
                        Text("Selfie", style = MaterialTheme.typography.labelSmall, color = ChampagneGold)
                    }
                }
            }
        }
    }
}

@Composable
private fun IntruderPhotoDialog(
    encryptedPath: String,
    repository: VaultRepository,
    onDismiss: () -> Unit
) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, key1 = encryptedPath) {
        val bytes = repository.decryptFullBytes(encryptedPath)
        if (bytes != null) {
            value = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            bytes.fill(0)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = DeepSlateNavy),
            border = BorderStroke(1.dp, CrimsonAlert.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Encrypted Intruder Capture", style = MaterialTheme.typography.titleMedium, color = IvoryText)
                Spacer(modifier = Modifier.height(12.dp))
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap!!,
                        contentDescription = "Intruder capture",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                            .clip(RoundedCornerShape(14.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = ImperialGold)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                TextButton(onClick = onDismiss) {
                    Text("Close", color = ChampagneGold)
                }
            }
        }
    }
}
