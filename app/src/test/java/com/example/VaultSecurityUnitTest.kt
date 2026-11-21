package com.example

import com.example.security.AutoLockDelay
import com.example.security.VaultConfigSnapshot
import com.example.security.VaultCryptoEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultSecurityUnitTest {

    @Test
    fun `pbkdf2 pin hashing verifies matching pin and rejects wrong pin`() {
        val crypto = VaultCryptoEngine()
        val salt = crypto.generateSalt()
        val hash = crypto.hashPin("739105", salt)

        assertEquals(32, hash.size)
        assertTrue(crypto.verifyPin("739105", salt, hash))
        assertFalse(crypto.verifyPin("739104", salt, hash))
        assertFalse(crypto.verifyPin("7391", salt, hash))
    }

    @Test
    fun `different salts produce distinct hashes for identical pin`() {
        val crypto = VaultCryptoEngine()
        val saltA = crypto.generateSalt()
        val saltB = crypto.generateSalt()
        val hashA = crypto.hashPin("4829", saltA)
        val hashB = crypto.hashPin("4829", saltB)

        assertFalse(saltA.contentEquals(saltB))
        assertFalse(hashA.contentEquals(hashB))
    }

    @Test
    fun `vault config snapshot tracks setup and decoy states accurately`() {
        val empty = VaultConfigSnapshot()
        assertFalse(empty.isSetupComplete)
        assertFalse(empty.hasDecoyPin)
        assertEquals(AutoLockDelay.IMMEDIATE, empty.autoLockDelay)

        val configured = empty.copy(
            masterPinHashB64 = "abc",
            masterPinSaltB64 = "def",
            decoyPinHashB64 = "ghi",
            decoyPinSaltB64 = "jkl",
            autoLockDelayName = AutoLockDelay.ONE_MINUTE.name
        )
        assertTrue(configured.isSetupComplete)
        assertTrue(configured.hasDecoyPin)
        assertEquals(AutoLockDelay.ONE_MINUTE, configured.autoLockDelay)
    }
}
