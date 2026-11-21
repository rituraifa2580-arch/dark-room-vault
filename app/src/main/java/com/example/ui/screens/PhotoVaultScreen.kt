package com.example.ui.screens

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.VaultMediaEntity
import com.example.data.VaultRepository
import com.example.ui.components.formatBytes
import com.example.ui.theme.ChampagneGold
import com.example.ui.theme.CoolSlateText
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.DeepSlateNavy
import com.example.ui.theme.ElevatedNavyCard
import com.example.ui.theme.ImperialGold
import com.example.ui.theme.IvoryText
import com.example.ui.theme.NavyOutline
import com.example.ui.theme.ObsidianBlack

@Composable
fun PhotoVaultScreen(
    photos: List<VaultMediaEntity>,
    repository: VaultRepository,
    onImportPhotos: (List<Uri>) -> Unit,
    onExportPhoto: (VaultMediaEntity, Uri) -> Unit,
    onDeletePhoto: (VaultMediaEntity) -> Unit
) {
    var selectedPhoto by remember { mutableStateOf<VaultMediaEntity?>(null) }
    var pendingExportPhoto by remember { mutableStateOf<VaultMediaEntity?>(null) }
    var pendingDeletePhoto by remember { mutableStateOf<VaultMediaEntity?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 25)
    ) { uris ->
        if (uris.isNotEmpty()) onImportPhotos(uris)
    }

    val exportDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/jpeg")
    ) { destUri ->
        val item = pendingExportPhoto
        pendingExportPhoto = null
        if (destUri != null && item != null) {
            onExportPhoto(item, destUri)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (photos.isEmpty()) {
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
                            imageVector = Icons.Filled.PhotoLibrary,
                            contentDescription = null,
                            tint = ImperialGold,
                            modifier = Modifier.size(38.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Private Photo Vault",
                    style = MaterialTheme.typography.headlineMedium,
                    color = IvoryText
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Select photos via the zero-permission Android Photo Picker. Files are encrypted immediately with AES-256-GCM.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CoolSlateText,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier.testTag("import_photos_empty_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ImperialGold,
                        contentColor = ObsidianBlack
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Import Photos")
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 112.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 14.dp,
                    end = 14.dp,
                    top = 14.dp,
                    bottom = 88.dp
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(photos, key = { it.id }) { photo ->
                    EncryptedPhotoGridTile(
                        item = photo,
                        repository = repository,
                        onClick = { selectedPhoto = photo }
                    )
                }
            }

            FloatingActionButton(
                onClick = {
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp)
                    .testTag("import_photos_fab"),
                containerColor = ImperialGold,
                contentColor = ObsidianBlack,
                shape = RoundedCornerShape(18.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.AddPhotoAlternate,
                    contentDescription = "Import photos into vault"
                )
            }
        }
    }

    selectedPhoto?.let { activePhoto ->
        SecurePhotoViewerDialog(
            item = activePhoto,
            repository = repository,
            onDismiss = { selectedPhoto = null },
            onExportClick = {
                pendingExportPhoto = activePhoto
                exportDocLauncher.launch(activePhoto.displayName)
            },
            onDeleteClick = {
                pendingDeletePhoto = activePhoto
            }
        )
    }

    pendingDeletePhoto?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDeletePhoto = null },
            containerColor = DeepSlateNavy,
            title = { Text("Shred Encrypted Photo?", color = IvoryText) },
            text = {
                Text(
                    "\"${target.displayName}\" will be permanently overwritten and deleted from the vault. This cannot be undone.",
                    color = CoolSlateText
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeletePhoto(target)
                        pendingDeletePhoto = null
                        selectedPhoto = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonAlert)
                ) {
                    Text("Shred Permanently", color = IvoryText)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeletePhoto = null }) {
                    Text("Cancel", color = CoolSlateText)
                }
            }
        )
    }
}

@Composable
private fun EncryptedPhotoGridTile(
    item: VaultMediaEntity,
    repository: VaultRepository,
    onClick: () -> Unit
) {
    val thumbBitmap by produceState<ImageBitmap?>(initialValue = null, key1 = item.id) {
        val bytes = repository.getDecryptedThumbnail(item)
        if (bytes != null) {
            value = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }
    }

    Surface(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .testTag("photo_tile_${item.id}"),
        shape = RoundedCornerShape(14.dp),
        color = ElevatedNavyCard,
        border = BorderStroke(1.dp, NavyOutline)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (thumbBitmap != null) {
                Image(
                    bitmap = thumbBitmap!!,
                    contentDescription = item.displayName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = null,
                        tint = ImperialGold.copy(alpha = 0.55f)
                    )
                }
            }
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp),
                shape = CircleShape,
                color = ObsidianBlack.copy(alpha = 0.72f)
            ) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = "AES-256 encrypted",
                    tint = ChampagneGold,
                    modifier = Modifier
                        .padding(4.dp)
                        .size(12.dp)
                )
            }
        }
    }
}

@Composable
private fun SecurePhotoViewerDialog(
    item: VaultMediaEntity,
    repository: VaultRepository,
    onDismiss: () -> Unit,
    onExportClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val fullImage by produceState<ImageBitmap?>(initialValue = null, key1 = item.id) {
        val bytes = repository.decryptFullBytes(item.encryptedFilePath)
        if (bytes != null) {
            value = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            bytes.fill(0)
        }
    }

    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = ObsidianBlack
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
            ) {
                if (fullImage == null) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = ImperialGold
                    )
                } else {
                    Image(
                        bitmap = fullImage!!,
                        contentDescription = item.displayName,
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    scale = (scale * zoom).coerceIn(1f, 5f)
                                    if (scale > 1f) {
                                        offsetX += pan.x
                                        offsetY += pan.y
                                    } else {
                                        offsetX = 0f
                                        offsetY = 0f
                                    }
                                }
                            }
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offsetX,
                                translationY = offsetY
                            ),
                        contentScale = ContentScale.Fit
                    )
                }

                // Top Overlay Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .background(ObsidianBlack.copy(alpha = 0.72f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close viewer",
                            tint = IvoryText
                        )
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
                            text = "${formatBytes(item.originalSizeBytes)} · AES-256-GCM",
                            style = MaterialTheme.typography.labelSmall,
                            color = ChampagneGold
                        )
                    }
                    IconButton(onClick = onExportClick) {
                        Icon(
                            imageVector = Icons.Filled.Download,
                            contentDescription = "Export decrypted copy",
                            tint = ImperialGold
                        )
                    }
                    IconButton(onClick = onDeleteClick) {
                        Icon(
                            imageVector = Icons.Filled.DeleteForever,
                            contentDescription = "Shred photo",
                            tint = CrimsonAlert
                        )
                    }
                }
            }
        }
    }
}
