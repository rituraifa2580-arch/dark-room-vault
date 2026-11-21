package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.EnhancedEncryption
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.MediaType
import com.example.data.VaultMediaEntity
import com.example.data.VaultScope
import com.example.security.VaultConfigSnapshot
import com.example.ui.components.SecurityBadgeChip
import com.example.ui.components.formatBytes
import com.example.ui.components.formatTimestamp
import com.example.ui.theme.ChampagneGold
import com.example.ui.theme.CoolSlateText
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.DeepSlateNavy
import com.example.ui.theme.ElevatedNavyCard
import com.example.ui.theme.EmeraldVerified
import com.example.ui.theme.GoldWash
import com.example.ui.theme.ImperialGold
import com.example.ui.theme.IvoryText
import com.example.ui.theme.NavyOutline
import com.example.ui.theme.ObsidianBlack

@Composable
fun VaultDashboardScreen(
    vaultScope: VaultScope,
    config: VaultConfigSnapshot,
    allMedia: List<VaultMediaEntity>,
    intruderCount: Int,
    accessibilityActive: Boolean,
    hardwareKeystoreActive: Boolean,
    onOpenPhotos: () -> Unit,
    onOpenVideos: () -> Unit,
    onOpenAudio: () -> Unit,
    onOpenShield: () -> Unit,
    onQuickLock: () -> Unit
) {
    val photoCount = allMedia.count { it.mediaType == MediaType.PHOTO }
    val videoCount = allMedia.count { it.mediaType == MediaType.VIDEO }
    val audioCount = allMedia.count { it.mediaType == MediaType.AUDIO }
    val totalEncryptedBytes = allMedia.sumOf { it.originalSizeBytes }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero Crest Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 720.dp),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, ImperialGold.copy(alpha = 0.4f)),
                colors = CardDefaults.cardColors(containerColor = DeepSlateNavy)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(176.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_vault_crest_banner),
                        contentDescription = "Dark Room Security Crest",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        ObsidianBlack.copy(alpha = 0.94f),
                                        ObsidianBlack.copy(alpha = 0.72f),
                                        Color.Transparent
                                    )
                                )
                            )
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SecurityBadgeChip(
                                label = if (hardwareKeystoreActive) "KEYSTORE · AES-256-GCM" else "AES-256-GCM",
                                accentColor = EmeraldVerified
                            )
                        }
                        Column {
                            Text(
                                text = "PRIVATE SANCTUM",
                                style = MaterialTheme.typography.headlineMedium,
                                color = ChampagneGold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${allMedia.size} encrypted assets · ${formatBytes(totalEncryptedBytes)} protected offline",
                                style = MaterialTheme.typography.bodyMedium,
                                color = CoolSlateText
                            )
                        }
                    }
                }
            }
        }

        // Category Quick Cards
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                VaultCategoryCard(
                    title = "Photos",
                    count = photoCount,
                    icon = Icons.Filled.PhotoLibrary,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("dashboard_photos_card"),
                    onClick = onOpenPhotos
                )
                VaultCategoryCard(
                    title = "Videos",
                    count = videoCount,
                    icon = Icons.Filled.VideoLibrary,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("dashboard_videos_card"),
                    onClick = onOpenVideos
                )
                VaultCategoryCard(
                    title = "Audio",
                    count = audioCount,
                    icon = Icons.Filled.AudioFile,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("dashboard_audio_card"),
                    onClick = onOpenAudio
                )
            }
        }

        // Security Posture Strip
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenShield),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = DeepSlateNavy),
                border = BorderStroke(1.dp, NavyOutline)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "SECURITY POSTURE",
                            style = MaterialTheme.typography.labelLarge,
                            color = ChampagneGold
                        )
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = GoldWash,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable(onClick = onQuickLock)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Lock,
                                    contentDescription = null,
                                    tint = ImperialGold,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = "LOCK NOW",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ImperialGold
                                )
                            }
                        }
                    }

                    SecurityPostureRow(
                        icon = Icons.Filled.EnhancedEncryption,
                        title = "Zero-Server Offline Vault",
                        status = "Active",
                        accent = EmeraldVerified
                    )
                    SecurityPostureRow(
                        icon = Icons.Filled.Fingerprint,
                        title = "Biometric Authentication",
                        status = if (config.biometricEnabled) "Enabled" else "PIN Only",
                        accent = if (config.biometricEnabled) EmeraldVerified else CoolSlateText
                    )
                    SecurityPostureRow(
                        icon = Icons.Filled.TouchApp,
                        title = "Three-Finger Secret Gesture",
                        status = if (config.threeFingerGestureEnabled) "Armed" else "Off",
                        accent = if (config.threeFingerGestureEnabled) ImperialGold else CoolSlateText
                    )
                    if (vaultScope == VaultScope.MASTER) {
                        SecurityPostureRow(
                            icon = Icons.Filled.Shield,
                            title = "App Lock Protection",
                            status = if (config.appLockEnabled && accessibilityActive) {
                                "${config.lockedPackages.size} apps"
                            } else {
                                "Standby"
                            },
                            accent = if (config.appLockEnabled && accessibilityActive) EmeraldVerified else CoolSlateText
                        )
                        SecurityPostureRow(
                            icon = Icons.Filled.WarningAmber,
                            title = "Intruder Attempt Log",
                            status = if (intruderCount == 0) "0 alerts" else "$intruderCount recorded",
                            accent = if (intruderCount == 0) EmeraldVerified else CrimsonAlert
                        )
                    }
                }
            }
        }

        // Recent Encrypted Assets
        item {
            Text(
                text = "RECENT ENCRYPTED FILES",
                style = MaterialTheme.typography.labelLarge,
                color = CoolSlateText
            )
        }

        if (allMedia.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = DeepSlateNavy.copy(alpha = 0.7f)),
                    border = BorderStroke(1.dp, NavyOutline),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Filled.EnhancedEncryption,
                            contentDescription = null,
                            tint = ImperialGold,
                            modifier = Modifier.size(34.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Vault is Empty & Armed",
                            style = MaterialTheme.typography.titleMedium,
                            color = IvoryText
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Import private photos, videos, or audio files. Every byte is encrypted on-device with AES-256-GCM.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = CoolSlateText
                        )
                    }
                }
            }
        } else {
            items(allMedia.take(6), key = { it.id }) { item ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            when (item.mediaType) {
                                MediaType.PHOTO -> onOpenPhotos()
                                MediaType.VIDEO -> onOpenVideos()
                                MediaType.AUDIO -> onOpenAudio()
                            }
                        },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = ElevatedNavyCard),
                    border = BorderStroke(1.dp, NavyOutline)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = GoldWash,
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = when (item.mediaType) {
                                        MediaType.PHOTO -> Icons.Filled.PhotoLibrary
                                        MediaType.VIDEO -> Icons.Filled.VideoLibrary
                                        MediaType.AUDIO -> Icons.Filled.AudioFile
                                    },
                                    contentDescription = null,
                                    tint = ImperialGold,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.size(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.displayName,
                                style = MaterialTheme.typography.titleMedium,
                                color = IvoryText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${formatBytes(item.originalSizeBytes)} · ${formatTimestamp(item.createdAt)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = CoolSlateText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = "Encrypted",
                            tint = EmeraldVerified,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(8.dp)) }
    }
}

@Composable
private fun VaultCategoryCard(
    title: String,
    count: Int,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = DeepSlateNavy),
        border = BorderStroke(1.dp, NavyOutline)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = ImperialGold,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = "$count",
                style = MaterialTheme.typography.headlineMedium,
                color = IvoryText
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = CoolSlateText
            )
        }
    }
}

@Composable
private fun SecurityPostureRow(
    icon: ImageVector,
    title: String,
    status: String,
    accent: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = ImperialGold,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.size(10.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = IvoryText,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = status,
            style = MaterialTheme.typography.labelMedium,
            color = accent
        )
    }
}
