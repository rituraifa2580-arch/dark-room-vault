package com.example.data

import android.graphics.drawable.Drawable
import androidx.room.Entity
import androidx.room.PrimaryKey

enum class MediaType {
    PHOTO,
    VIDEO,
    AUDIO
}

enum class VaultScope(val key: String) {
    MASTER("master"),
    DECOY("decoy")
}

@Entity(tableName = "vault_media")
data class VaultMediaEntity(
    @PrimaryKey val id: String,
    val vaultScope: String,
    val mediaType: MediaType,
    val displayName: String,
    val mimeType: String,
    val originalSizeBytes: Long,
    val durationMs: Long,
    val encryptedFilePath: String,
    val encryptedThumbPath: String?,
    val createdAt: Long,
    val sha256Checksum: String
)

@Entity(tableName = "intruder_logs")
data class IntruderLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val timestamp: Long,
    val source: String,
    val attemptCount: Int,
    val enteredLength: Int,
    val outcome: String,
    val encryptedPhotoPath: String?
)

@Entity(tableName = "app_lock_rules")
data class AppLockRuleEntity(
    @PrimaryKey val packageName: String,
    val appName: String,
    val isLocked: Boolean,
    val addedAt: Long
)

data class InstalledAppInfo(
    val packageName: String,
    val appName: String,
    val icon: Drawable?,
    val isLocked: Boolean
)
