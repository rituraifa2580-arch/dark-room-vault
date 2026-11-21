package com.example.ui.screens

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults.SecondaryIndicator
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import com.example.data.MediaType
import com.example.data.VaultMediaEntity
import com.example.data.VaultRepository
import com.example.media.EncryptedVaultDataSource
import com.example.security.VaultCryptoEngine
import com.example.ui.components.SecurityBadgeChip
import com.example.ui.components.formatBytes
import com.example.ui.components.formatDurationMs
import com.example.ui.components.formatTimestamp
import com.example.ui.theme.ChampagneGold
import com.example.ui.theme.CoolSlateText
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.DeepSlateNavy
import com.example.ui.theme.ElevatedNavyCard
import com.example.ui.theme.GoldWash
import com.example.ui.theme.ImperialGold
import com.example.ui.theme.IvoryText
import com.example.ui.theme.MidnightNavy
import com.example.ui.theme.NavyOutline
import com.example.ui.theme.ObsidianBlack
import kotlinx.coroutines.delay
import java.io.File

@Composable
fun MediaVaultScreen(
    initialTab: Int,
    videos: List<VaultMediaEntity>,
    audioTracks: List<VaultMediaEntity>,
    repository: VaultRepository,
    cryptoEngine: VaultCryptoEngine,
    onImportMedia: (List<Uri>, MediaType) -> Unit,
    onExportMedia: (VaultMediaEntity, Uri) -> Unit,
    onDeleteMedia: (VaultMediaEntity) -> Unit
) {
    var selectedTab by remember(initialTab) { mutableIntStateOf(initialTab) }
    var activeVideo by remember { mutableStateOf<VaultMediaEntity?>(null) }
    var activeAudio by remember { mutableStateOf<VaultMediaEntity?>(null) }
    var pendingExport by remember { mutableStateOf<VaultMediaEntity?>(null) }
    var pendingDelete by remember { mutableStateOf<VaultMediaEntity?>(null) }

    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 15)
    ) { uris ->
        if (uris.isNotEmpty()) onImportMedia(uris, MediaType.VIDEO)
    }

    val audioPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) onImportMedia(uris, MediaType.AUDIO)
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { destUri ->
        val item = pendingExport
        pendingExport = null
        if (destUri != null && item != null) {
            onExportMedia(item, destUri)
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
                modifier = Modifier.testTag("media_tab_videos"),
                text = { Text("Videos (${videos.size})") }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                modifier = Modifier.testTag("media_tab_audio"),
                text = { Text("Audio (${audioTracks.size})") }
            )
        }

        Box(modifier = Modifier.fillMaxSize()) {
            val activeList = if (selectedTab == 0) videos else audioTracks
            val isVideoTab = selectedTab == 0

            if (activeList.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Surface(
                        shape = CircleShape,
                        color = ElevatedNavyCard,
                        border = BorderStroke(1.dp, ImperialGold.copy(alpha = 0.45f)),
                        modifier = Modifier.size(84.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isVideoTab) Icons.Filled.VideoLibrary else Icons.Filled.AudioFile,
                                contentDescription = null,
                                tint = ImperialGold,
                                modifier = Modifier.size(38.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = if (isVideoTab) "Private Video Vault" else "Private Audio Vault",
                        style = MaterialTheme.typography.headlineMedium,
                        color = IvoryText
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (isVideoTab) {
                            "Videos are encrypted in 256 KB AES-256-GCM chunks and streamed directly in RAM without plaintext temp files."
                        } else {
                            "Import private voice memos, recordings, or audio files. Decrypted strictly in RAM during playback."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = CoolSlateText,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = {
                            if (isVideoTab) {
                                videoPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                )
                            } else {
                                audioPicker.launch(arrayOf("audio/*"))
                            }
                        },
                        modifier = Modifier.testTag("import_media_empty_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ImperialGold,
                            contentColor = ObsidianBlack
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(if (isVideoTab) "Import Videos" else "Import Audio")
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 14.dp,
                        bottom = 88.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(activeList, key = { it.id }) { item ->
                        if (isVideoTab) {
                            EncryptedVideoRowCard(
                                item = item,
                                repository = repository,
                                onPlay = { activeVideo = item },
                                onExport = {
                                    pendingExport = item
                                    exportLauncher.launch(item.displayName)
                                },
                                onDelete = { pendingDelete = item }
                            )
                        } else {
                            EncryptedAudioRowCard(
                                item = item,
                                onPlay = { activeAudio = item },
                                onExport = {
                                    pendingExport = item
                                    exportLauncher.launch(item.displayName)
                                },
                                onDelete = { pendingDelete = item }
                            )
                        }
                    }
                }

                FloatingActionButton(
                    onClick = {
                        if (isVideoTab) {
                            videoPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                            )
                        } else {
                            audioPicker.launch(arrayOf("audio/*"))
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(20.dp)
                        .testTag("import_media_fab"),
                    containerColor = ImperialGold,
                    contentColor = ObsidianBlack,
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = if (isVideoTab) "Import videos" else "Import audio"
                    )
                }
            }
        }
    }

    activeVideo?.let { video ->
        EncryptedVideoPlayerDialog(
            item = video,
            cryptoEngine = cryptoEngine,
            onDismiss = { activeVideo = null }
        )
    }

    activeAudio?.let { audio ->
        EncryptedAudioPlayerDialog(
            item = audio,
            cryptoEngine = cryptoEngine,
            onDismiss = { activeAudio = null }
        )
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor = DeepSlateNavy,
            title = { Text("Shred Encrypted Media?", color = IvoryText) },
            text = {
                Text(
                    "\"${target.displayName}\" will be securely overwritten and permanently erased from the vault.",
                    color = CoolSlateText
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteMedia(target)
                        pendingDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonAlert)
                ) {
                    Text("Shred Permanently", color = IvoryText)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("Cancel", color = CoolSlateText)
                }
            }
        )
    }
}

@Composable
private fun EncryptedVideoRowCard(
    item: VaultMediaEntity,
    repository: VaultRepository,
    onPlay: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit
) {
    val thumbBitmap by produceState<ImageBitmap?>(initialValue = null, key1 = item.id) {
        val bytes = repository.getDecryptedThumbnail(item)
        if (bytes != null) {
            value = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = DeepSlateNavy),
        border = BorderStroke(1.dp, NavyOutline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(width = 96.dp, height = 68.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(ElevatedNavyCard),
                contentAlignment = Alignment.Center
            ) {
                if (thumbBitmap != null) {
                    Image(
                        bitmap = thumbBitmap!!,
                        contentDescription = item.displayName,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                Surface(
                    shape = CircleShape,
                    color = ObsidianBlack.copy(alpha = 0.72f),
                    modifier = Modifier.size(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = "Play encrypted video",
                            tint = ImperialGold,
                            modifier = Modifier.size(20.dp)
                        )
                    }
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
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "${formatDurationMs(item.durationMs)} · ${formatBytes(item.originalSizeBytes)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = ChampagneGold
                )
                Text(
                    text = formatTimestamp(item.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = CoolSlateText
                )
            }

            IconButton(onClick = onExport) {
                Icon(Icons.Filled.Download, contentDescription = "Export video", tint = ImperialGold)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.DeleteForever, contentDescription = "Delete video", tint = CrimsonAlert)
            }
        }
    }
}

@Composable
private fun EncryptedAudioRowCard(
    item: VaultMediaEntity,
    onPlay: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = DeepSlateNavy),
        border = BorderStroke(1.dp, NavyOutline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = GoldWash,
                border = BorderStroke(1.dp, ImperialGold.copy(alpha = 0.45f)),
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.GraphicEq,
                        contentDescription = "Play audio",
                        tint = ImperialGold,
                        modifier = Modifier.size(24.dp)
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
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "${formatDurationMs(item.durationMs)} · ${formatBytes(item.originalSizeBytes)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = ChampagneGold
                )
                Text(
                    text = formatTimestamp(item.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = CoolSlateText
                )
            }
            IconButton(onClick = onExport) {
                Icon(Icons.Filled.Download, contentDescription = "Export audio", tint = ImperialGold)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.DeleteForever, contentDescription = "Delete audio", tint = CrimsonAlert)
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun EncryptedVideoPlayerDialog(
    item: VaultMediaEntity,
    cryptoEngine: VaultCryptoEngine,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val exoPlayer = remember(item.id) {
        val dataSourceFactory = EncryptedVaultDataSource.Factory(cryptoEngine)
        val mediaSourceFactory = ProgressiveMediaSource.Factory(dataSourceFactory)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build().apply {
                val uri = Uri.fromFile(File(item.encryptedFilePath))
                setMediaItem(MediaItem.fromUri(uri))
                prepare()
                playWhenReady = true
            }
    }

    DisposableEffect(item.id) {
        onDispose {
            exoPlayer.release()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = ObsidianBlack
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MidnightNavy)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Close player", tint = IvoryText)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            color = IvoryText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Streaming RAM Decryption · Zero Disk Plaintext",
                            style = MaterialTheme.typography.labelSmall,
                            color = ChampagneGold
                        )
                    }
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = null,
                        tint = ImperialGold,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }

                Box(modifier = Modifier.weight(1f)) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                player = exoPlayer
                                useController = true
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun EncryptedAudioPlayerDialog(
    item: VaultMediaEntity,
    cryptoEngine: VaultCryptoEngine,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val exoPlayer = remember(item.id) {
        val dataSourceFactory = EncryptedVaultDataSource.Factory(cryptoEngine)
        val mediaSourceFactory = ProgressiveMediaSource.Factory(dataSourceFactory)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build().apply {
                val uri = Uri.fromFile(File(item.encryptedFilePath))
                setMediaItem(MediaItem.fromUri(uri))
                prepare()
                playWhenReady = true
            }
    }

    var isPlaying by remember { mutableStateOf(true) }
    var currentPosMs by remember { mutableLongStateOf(0L) }
    var totalDurationMs by remember { mutableLongStateOf(item.durationMs.coerceAtLeast(1L)) }

    DisposableEffect(item.id) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                val dur = exoPlayer.duration
                if (dur > 0L) totalDurationMs = dur
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    LaunchedEffect(item.id) {
        while (true) {
            currentPosMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            if (exoPlayer.duration > 0L) totalDurationMs = exoPlayer.duration
            delay(250L)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = DeepSlateNavy),
            border = BorderStroke(1.dp, ImperialGold.copy(alpha = 0.45f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SecurityBadgeChip(label = "AES-256-GCM AUDIO")
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Close audio player", tint = IvoryText)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Surface(
                    shape = CircleShape,
                    color = GoldWash,
                    border = BorderStroke(1.5.dp, ImperialGold),
                    modifier = Modifier.size(76.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.GraphicEq,
                            contentDescription = null,
                            tint = ChampagneGold,
                            modifier = Modifier.size(38.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = item.displayName,
                    style = MaterialTheme.typography.titleLarge,
                    color = IvoryText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))

                val safeMax = totalDurationMs.coerceAtLeast(1L).toFloat()
                Slider(
                    value = currentPosMs.coerceIn(0L, totalDurationMs).toFloat(),
                    onValueChange = { pos -> exoPlayer.seekTo(pos.toLong()) },
                    valueRange = 0f..safeMax,
                    colors = SliderDefaults.colors(
                        thumbColor = ImperialGold,
                        activeTrackColor = ImperialGold,
                        inactiveTrackColor = NavyOutline
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatDurationMs(currentPosMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = CoolSlateText
                    )
                    Text(
                        text = formatDurationMs(totalDurationMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = CoolSlateText
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        exoPlayer.seekTo((exoPlayer.currentPosition - 10_000L).coerceAtLeast(0L))
                    }) {
                        Icon(Icons.Filled.Replay10, contentDescription = "Back 10 seconds", tint = IvoryText)
                    }

                    Surface(
                        shape = CircleShape,
                        color = ImperialGold,
                        modifier = Modifier
                            .size(60.dp)
                            .clickable {
                                if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                            }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = ObsidianBlack,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    }

                    IconButton(onClick = {
                        exoPlayer.seekTo((exoPlayer.currentPosition + 10_000L).coerceAtMost(totalDurationMs))
                    }) {
                        Icon(Icons.Filled.Forward10, contentDescription = "Forward 10 seconds", tint = IvoryText)
                    }
                }
            }
        }
    }
}
