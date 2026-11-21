package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface VaultMediaDao {
    @Query("SELECT * FROM vault_media WHERE vaultScope = :scope ORDER BY createdAt DESC")
    fun observeAllByScope(scope: String): Flow<List<VaultMediaEntity>>

    @Query("SELECT * FROM vault_media WHERE vaultScope = :scope AND mediaType = :type ORDER BY createdAt DESC")
    fun observeByScopeAndType(scope: String, type: MediaType): Flow<List<VaultMediaEntity>>

    @Query("SELECT * FROM vault_media WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): VaultMediaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: VaultMediaEntity)

    @Query("DELETE FROM vault_media WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface IntruderLogDao {
    @Query("SELECT * FROM intruder_logs ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<IntruderLogEntity>>

    @Query("SELECT COUNT(*) FROM intruder_logs")
    fun observeCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: IntruderLogEntity)

    @Query("SELECT * FROM intruder_logs")
    suspend fun getAllOnce(): List<IntruderLogEntity>

    @Query("DELETE FROM intruder_logs")
    suspend fun clearAll()
}

@Dao
interface AppLockDao {
    @Query("SELECT * FROM app_lock_rules ORDER BY appName ASC")
    fun observeRules(): Flow<List<AppLockRuleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: AppLockRuleEntity)

    @Query("UPDATE app_lock_rules SET isLocked = :locked WHERE packageName = :packageName")
    suspend fun setLocked(packageName: String, locked: Boolean)
}
