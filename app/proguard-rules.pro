# Dark Room – Private Security Vault ProGuard / R8 Rules

# Keep Kotlinx Serialization models & generated serializers (used by SecureVaultPrefs & Navigation Compose)
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.example.**$$serializer { *; }
-keepclassmembers class com.example.** {
    *** Companion;
}
-keepclasseswithmembers class com.example.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep Room entities and database classes
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Keep AccessibilityService & Challenge Activity entry points referenced in AndroidManifest.xml
-keep class com.example.applock.VaultAppLockService { *; }
-keep class com.example.applock.AppLockChallengeActivity { *; }
-keep class com.example.DarkRoomApplication { *; }
