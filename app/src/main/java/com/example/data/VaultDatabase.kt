package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class VaultTypeConverters {
    @TypeConverter
    fun fromMediaType(value: MediaType): String = value.name

    @TypeConverter
    fun toMediaType(value: String): MediaType = MediaType.valueOf(value)
}

@Database(
    entities = [
        VaultMediaEntity::class,
        IntruderLogEntity::class,
        AppLockRuleEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(VaultTypeConverters::class)
abstract class VaultDatabase : RoomDatabase() {
    abstract fun vaultMediaDao(): VaultMediaDao
    abstract fun intruderLogDao(): IntruderLogDao
    abstract fun appLockDao(): AppLockDao

    companion object {
        fun build(context: Context): VaultDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                VaultDatabase::class.java,
                "darkroom_vault_index.db"
            ).build()
        }
    }
}
