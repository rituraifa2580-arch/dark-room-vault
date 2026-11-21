package com.example

import android.app.Application
import com.example.data.VaultDatabase
import com.example.data.VaultRepository
import com.example.security.SecureVaultPrefs
import com.example.security.VaultCryptoEngine

class AppContainer(application: Application) {
    val cryptoEngine: VaultCryptoEngine by lazy { VaultCryptoEngine() }
    val prefs: SecureVaultPrefs by lazy { SecureVaultPrefs(application, cryptoEngine) }
    val database: VaultDatabase by lazy { VaultDatabase.build(application) }
    val repository: VaultRepository by lazy {
        VaultRepository(
            context = application,
            database = database,
            cryptoEngine = cryptoEngine,
            prefs = prefs
        )
    }
}

class DarkRoomApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
