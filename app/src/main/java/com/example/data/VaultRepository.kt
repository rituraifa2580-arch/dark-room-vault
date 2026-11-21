package com.example.data

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.util.LruCache
import com.example.security.SecureVaultPrefs
import com.example.security.VaultCryptoEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

class VaultRepository(
    private val context: Context,
    private val database: VaultDatabase,
    private val cryptoEngine: VaultCryptoEngine,
    private val prefs: SecureVaultPrefs
) {
    private val mediaDao = database.vaultMediaDao()
    private val intruderDao = database.intruderLogDao()
    private val appLockDao = database.appLockDao()

    private val vaultMediaDir = File(context.filesDir, "vault_media").apply { mkdirs() }
    private val vaultThumbDir = File(context.filesDir, "vault_thumbs").apply { mkdirs() }

    // In-memory LRU cache for decrypted thumbnails (cleared immediately on vault lock)
    private val thumbnailMemoryCache = object : LruCache<String, ByteArray>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ByteArray): Int = value.size
    }

    fun observeAllMedia(scope: VaultScope): Flow<List<VaultMediaEntity>> =
        mediaDao.observeAllByScope(scope.key)

    fun observeMediaByType(scope: VaultScope, type: MediaType): Flow<List<VaultMediaEntity>> =
        mediaDao.observeByScopeAndType(scope.key, type)

    fun observeIntruderLogs(): Flow<List<IntruderLogEntity>> = intruderDao.observeAll()

    fun observeIntruderCount(): Flow<Int> = intruderDao.observeCount()

    fun observeAppLockRules(): Flow<List<AppLockRuleEntity>> = appLockDao.observeRules()

    fun clearInMemoryCaches() {
        thumbnailMemoryCache.evictAll()
    }

    suspend fun importMediaFromUri(
        uri: Uri,
        mediaType: MediaType,
        scope: VaultScope
    ): Result<VaultMediaEntity> = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val meta = queryUriMetadata(uri)
            val mimeType = resolver.getType(uri) ?: defaultMimeType(mediaType)
            val id = UUID.randomUUID().toString()
            val encFile = File(vaultMediaDir, "${scope.key}_${id}.enc")

            // 1. Encrypt original file stream with chunked AES-256-GCM
            val (plainBytes, sha256) = resolver.openInputStream(uri)?.use { input ->
                cryptoEngine.encryptStreamToFile(input, encFile, meta.sizeBytes)
            } ?: error("Unable to open selected file stream")

            // 2. Extract duration & encrypted thumbnail where applicable
            var durationMs = 0L
            var encryptedThumbPath: String? = null

            when (mediaType) {
                MediaType.PHOTO -> {
                    val thumbBytes = createPhotoThumbnail(uri)
                    if (thumbBytes != null) {
                        val thumbFile = File(vaultThumbDir, "${id}_thumb.enc")
                        ByteArrayInputStream(thumbBytes).use {
                            cryptoEngine.encryptStreamToFile(it, thumbFile, thumbBytes.size.toLong())
                        }
                        encryptedThumbPath = thumbFile.absolutePath
                        thumbnailMemoryCache.put(id, thumbBytes)
                    }
                }

                MediaType.VIDEO -> {
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(context, uri)
                        durationMs = retriever.extractMetadata(
                            MediaMetadataRetriever.METADATA_KEY_DURATION
                        )?.toLongOrNull() ?: 0L
                        val frame = retriever.getFrameAtTime(
                            1_000_000L,
                            MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                        ) ?: retriever.frameAtTime
                        if (frame != null) {
                            val thumbBytes = compressBitmapThumbnail(frame)
                            frame.recycle()
                            val thumbFile = File(vaultThumbDir, "${id}_thumb.enc")
                            ByteArrayInputStream(thumbBytes).use {
                                cryptoEngine.encryptStreamToFile(it, thumbFile, thumbBytes.size.toLong())
                            }
                            encryptedThumbPath = thumbFile.absolutePath
                            thumbnailMemoryCache.put(id, thumbBytes)
                        }
                    } catch (_: Exception) {
                        // Video still imported even if thumbnail extraction fails
                    } finally {
                        try {
                            retriever.release()
                        } catch (_: Exception) {
                        }
                    }
                }

                MediaType.AUDIO -> {
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(context, uri)
                        durationMs = retriever.extractMetadata(
                            MediaMetadataRetriever.METADATA_KEY_DURATION
                        )?.toLongOrNull() ?: 0L
                    } catch (_: Exception) {
                    } finally {
                        try {
                            retriever.release()
                        } catch (_: Exception) {
                        }
                    }
                }
            }

            val entity = VaultMediaEntity(
                id = id,
                vaultScope = scope.key,
                mediaType = mediaType,
                displayName = meta.displayName,
                mimeType = mimeType,
                originalSizeBytes = plainBytes,
                durationMs = durationMs,
                encryptedFilePath = encFile.absolutePath,
                encryptedThumbPath = encryptedThumbPath,
                createdAt = System.currentTimeMillis(),
                sha256Checksum = sha256
            )
            mediaDao.insert(entity)
            entity
        }
    }

    suspend fun getDecryptedThumbnail(item: VaultMediaEntity): ByteArray? = withContext(Dispatchers.IO) {
        thumbnailMemoryCache.get(item.id)?.let { return@withContext it }
        val thumbPath = item.encryptedThumbPath ?: return@withContext null
        val file = File(thumbPath)
        if (!file.exists()) return@withContext null
        return@withContext try {
            val bytes = cryptoEngine.decryptFileToBytes(file)
            thumbnailMemoryCache.put(item.id, bytes)
            bytes
        } catch (_: Exception) {
            null
        }
    }

    suspend fun decryptFullBytes(encryptedPath: String): ByteArray? = withContext(Dispatchers.IO) {
        val file = File(encryptedPath)
        if (!file.exists()) return@withContext null
        try {
            cryptoEngine.decryptFileToBytes(file)
        } catch (_: Exception) {
            null
        }
    }

    suspend fun exportDecryptedToUri(item: VaultMediaEntity, destinationUri: Uri): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val encFile = File(item.encryptedFilePath)
                require(encFile.exists()) { "Encrypted vault file not found" }
                context.contentResolver.openOutputStream(destinationUri)?.use { out ->
                    cryptoEngine.decryptFileToStream(encFile, out)
                } ?: error("Unable to open export destination")
            }
        }

    suspend fun deleteVaultItem(item: VaultMediaEntity) = withContext(Dispatchers.IO) {
        thumbnailMemoryCache.remove(item.id)
        cryptoEngine.secureDeleteFile(File(item.encryptedFilePath))
        item.encryptedThumbPath?.let { cryptoEngine.secureDeleteFile(File(it)) }
        mediaDao.deleteById(item.id)
    }

    suspend fun recordIntruderAttempt(
        source: String,
        attemptCount: Int,
        enteredLength: Int,
        outcome: String,
        encryptedPhotoPath: String?
    ) = withContext(Dispatchers.IO) {
        intruderDao.insert(
            IntruderLogEntity(
                timestamp = System.currentTimeMillis(),
                source = source,
                attemptCount = attemptCount,
                enteredLength = enteredLength,
                outcome = outcome,
                encryptedPhotoPath = encryptedPhotoPath
            )
        )
    }

    suspend fun clearIntruderLogs() = withContext(Dispatchers.IO) {
        val allLogs = intruderDao.getAllOnce()
        allLogs.forEach { log ->
            log.encryptedPhotoPath?.let { cryptoEngine.secureDeleteFile(File(it)) }
        }
        intruderDao.clearAll()
    }

    suspend fun loadLaunchableApps(lockedPackages: Set<String>): List<InstalledAppInfo> =
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val mainIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolved = pm.queryIntentActivities(mainIntent, 0)
            resolved
                .asSequence()
                .mapNotNull { info ->
                    val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
                    if (pkg == context.packageName) return@mapNotNull null
                    val label = info.loadLabel(pm)?.toString()?.takeIf { it.isNotBlank() } ?: pkg
                    val icon = try {
                        info.loadIcon(pm)
                    } catch (_: Exception) {
                        null
                    }
                    InstalledAppInfo(
                        packageName = pkg,
                        appName = label,
                        icon = icon,
                        isLocked = lockedPackages.contains(pkg)
                    )
                }
                .distinctBy { it.packageName }
                .sortedWith(compareByDescending<InstalledAppInfo> { it.isLocked }.thenBy { it.appName.lowercase() })
                .toList()
        }

    suspend fun setPackageLocked(packageName: String, appName: String, locked: Boolean) {
        prefs.updateConfig { snap ->
            val next = if (locked) snap.lockedPackages + packageName else snap.lockedPackages - packageName
            snap.copy(lockedPackages = next)
        }
        appLockDao.upsert(
            AppLockRuleEntity(
                packageName = packageName,
                appName = appName,
                isLocked = locked,
                addedAt = System.currentTimeMillis()
            )
        )
    }

    private fun createPhotoThumbnail(uri: Uri): ByteArray? {
        return try {
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, boundsOpts)
            }
            val maxDim = 480
            var sample = 1
            while (boundsOpts.outWidth / sample > maxDim || boundsOpts.outHeight / sample > maxDim) {
                sample *= 2
            }
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
            val bitmap = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOpts)
            } ?: return null
            val bytes = compressBitmapThumbnail(bitmap)
            bitmap.recycle()
            bytes
        } catch (_: Exception) {
            null
        }
    }

    private fun compressBitmapThumbnail(bitmap: Bitmap): ByteArray {
        val maxSide = 480
        val ratio = minOf(maxSide.toFloat() / bitmap.width, maxSide.toFloat() / bitmap.height, 1f)
        val scaled = if (ratio < 1f) {
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * ratio).toInt().coerceAtLeast(1),
                (bitmap.height * ratio).toInt().coerceAtLeast(1),
                true
            )
        } else {
            bitmap
        }
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 82, out)
        if (scaled !== bitmap) scaled.recycle()
        return out.toByteArray()
    }

    private fun queryUriMetadata(uri: Uri): UriMetadata {
        var name = "vault_${System.currentTimeMillis()}"
        var size = -1L
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIdx != -1) name = cursor.getString(nameIdx) ?: name
                if (sizeIdx != -1) size = cursor.getLong(sizeIdx)
            }
        }
        return UriMetadata(name, size)
    }

    private fun defaultMimeType(type: MediaType): String = when (type) {
        MediaType.PHOTO -> "image/jpeg"
        MediaType.VIDEO -> "video/mp4"
        MediaType.AUDIO -> "audio/mpeg"
    }

    private data class UriMetadata(val displayName: String, val sizeBytes: Long)
}
