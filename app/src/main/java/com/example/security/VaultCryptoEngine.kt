package com.example.security

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec

/**
 * Hardware-backed Android Keystore AES-256-GCM cryptographic engine with
 * O(1) seekable chunked encryption for large media files and PBKDF2-HMAC-SHA256 PIN hashing.
 */
class VaultCryptoEngine {

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "darkroom_aes256_gcm_master_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
        private const val GCM_TAG_BYTES = 16
        private const val IV_BYTES = 12

        const val DEFAULT_CHUNK_SIZE = 256 * 1024 // 256 KB per authenticated GCM chunk
        private val FILE_MAGIC = byteArrayOf('D'.code.toByte(), 'R'.code.toByte(), 'M'.code.toByte(), 'V'.code.toByte(), '1'.code.toByte())
        private const val FILE_SALT_BYTES = 16
        const val HEADER_SIZE = 5 + 4 + 8 + FILE_SALT_BYTES // 33 bytes

        private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
        private const val PBKDF2_ITERATIONS = 120_000
        private const val PBKDF2_KEY_BITS = 256
    }

    private val secureRandom = SecureRandom()
    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    }

    @Synchronized
    fun getOrCreateMasterKey(): SecretKey {
        val existing = (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
        if (existing != null) return existing

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                keyGenerator.init(buildKeySpec(useStrongBox = true))
                return keyGenerator.generateKey()
            } catch (_: StrongBoxUnavailableException) {
                // Fallback to standard TEE hardware Keystore (e.g., Vivo Y33s / emulators)
            } catch (_: Exception) {
                // Fallback to TEE if StrongBox throws ProviderException
            }
        }
        keyGenerator.init(buildKeySpec(useStrongBox = false))
        return keyGenerator.generateKey()
    }

    private fun buildKeySpec(useStrongBox: Boolean): KeyGenParameterSpec {
        val builder = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .setUserAuthenticationRequired(false)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && useStrongBox) {
            builder.setIsStrongBoxBacked(true)
        }
        return builder.build()
    }

    fun isKeystoreHardwareBacked(): Boolean {
        return try {
            getOrCreateMasterKey()
            true
        } catch (_: Exception) {
            false
        }
    }

    fun encryptBytes(plaintext: ByteArray, aad: ByteArray? = null): ByteArray {
        val secretKey = getOrCreateMasterKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        if (aad != null) cipher.updateAAD(aad)
        val iv = cipher.iv
        require(iv.size == IV_BYTES) { "Unexpected GCM IV length: ${iv.size}" }
        val ciphertext = cipher.doFinal(plaintext)
        return ByteArray(IV_BYTES + ciphertext.size).apply {
            System.arraycopy(iv, 0, this, 0, IV_BYTES)
            System.arraycopy(ciphertext, 0, this, IV_BYTES, ciphertext.size)
        }
    }

    fun decryptBytes(encryptedBlob: ByteArray, aad: ByteArray? = null): ByteArray {
        require(encryptedBlob.size > IV_BYTES + GCM_TAG_BYTES) { "Corrupt encrypted payload" }
        val secretKey = getOrCreateMasterKey()
        val iv = encryptedBlob.copyOfRange(0, IV_BYTES)
        val ciphertext = encryptedBlob.copyOfRange(IV_BYTES, encryptedBlob.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
        if (aad != null) cipher.updateAAD(aad)
        return cipher.doFinal(ciphertext)
    }

    /**
     * Encrypts an arbitrary-size stream using chunked AES-256-GCM with per-chunk IVs and AAD.
     * Writes atomically to a temp file and renames on completion.
     * Returns (plaintextBytesWritten, sha256HexOfPlaintext).
     */
    fun encryptStreamToFile(
        inputStream: InputStream,
        destinationFile: File,
        expectedLengthHint: Long = -1L
    ): Pair<Long, String> {
        destinationFile.parentFile?.mkdirs()
        val tempFile = File(destinationFile.parentFile, "${destinationFile.name}.tmp")
        val secretKey = getOrCreateMasterKey()
        val fileSalt = ByteArray(FILE_SALT_BYTES).also { secureRandom.nextBytes(it) }
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_CHUNK_SIZE)
        var totalPlaintext = 0L
        var chunkIndex = 0L

        try {
            RandomAccessFile(tempFile, "rw").use { raf ->
                raf.setLength(0)
                // Write placeholder header; plaintextLength is patched once stream finishes
                writeHeader(raf, DEFAULT_CHUNK_SIZE, expectedLengthHint.coerceAtLeast(0L), fileSalt)

                while (true) {
                    val bytesRead = readFullChunk(inputStream, buffer)
                    if (bytesRead <= 0) break

                    digest.update(buffer, 0, bytesRead)
                    val cipher = Cipher.getInstance(TRANSFORMATION)
                    cipher.init(Cipher.ENCRYPT_MODE, secretKey)
                    cipher.updateAAD(buildChunkAad(fileSalt, chunkIndex))
                    val iv = cipher.iv
                    val encryptedChunk = cipher.doFinal(buffer, 0, bytesRead)

                    raf.write(iv)
                    raf.writeInt(encryptedChunk.size)
                    raf.write(encryptedChunk)

                    totalPlaintext += bytesRead
                    chunkIndex++
                }

                // Patch exact plaintext length into header
                raf.seek(FILE_MAGIC.size + 4L)
                raf.writeLong(totalPlaintext)
            }

            if (destinationFile.exists()) {
                destinationFile.delete()
            }
            if (!tempFile.renameTo(destinationFile)) {
                tempFile.copyTo(destinationFile, overwrite = true)
                tempFile.delete()
            }
        } catch (t: Throwable) {
            tempFile.delete()
            throw t
        } finally {
            buffer.fill(0)
        }

        val shaHex = digest.digest().joinToString("") { "%02x".format(it) }
        return totalPlaintext to shaHex
    }

    /**
     * Decrypts a full chunked `.enc` file into memory (for photos / small files / SAF export).
     */
    fun decryptFileToBytes(encryptedFile: File): ByteArray {
        val header = readHeader(encryptedFile)
        val out = ByteArrayOutputStream(header.plaintextLength.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
        RandomAccessFile(encryptedFile, "r").use { raf ->
            raf.seek(HEADER_SIZE.toLong())
            var chunkIndex = 0L
            var remaining = header.plaintextLength
            while (remaining > 0L) {
                val plainChunk = readAndDecryptNextChunk(raf, header.fileSalt, chunkIndex)
                out.write(plainChunk)
                remaining -= plainChunk.size
                chunkIndex++
            }
        }
        return out.toByteArray()
    }

    /**
     * Decrypts a chunked `.enc` file directly to an OutputStream with constant O(256KB) memory.
     */
    fun decryptFileToStream(encryptedFile: File, outputStream: java.io.OutputStream) {
        val header = readHeader(encryptedFile)
        RandomAccessFile(encryptedFile, "r").use { raf ->
            raf.seek(HEADER_SIZE.toLong())
            var chunkIndex = 0L
            var remaining = header.plaintextLength
            while (remaining > 0L) {
                val plainChunk = readAndDecryptNextChunk(raf, header.fileSalt, chunkIndex)
                outputStream.write(plainChunk)
                remaining -= plainChunk.size
                plainChunk.fill(0)
                chunkIndex++
            }
        }
        outputStream.flush()
    }

    fun readHeader(encryptedFile: File): EncryptedFileHeader {
        RandomAccessFile(encryptedFile, "r").use { raf ->
            return readHeader(raf)
        }
    }

    fun readHeader(raf: RandomAccessFile): EncryptedFileHeader {
        raf.seek(0)
        val magic = ByteArray(FILE_MAGIC.size)
        raf.readFully(magic)
        require(magic.contentEquals(FILE_MAGIC)) { "Invalid Dark Room encrypted file header" }
        val chunkSize = raf.readInt()
        val plaintextLength = raf.readLong()
        val fileSalt = ByteArray(FILE_SALT_BYTES)
        raf.readFully(fileSalt)
        return EncryptedFileHeader(chunkSize, plaintextLength, fileSalt)
    }

    /**
     * O(1) random-access chunk decryption for seekable Media3 ExoPlayer playback.
     */
    fun decryptChunkAtIndex(
        raf: RandomAccessFile,
        header: EncryptedFileHeader,
        chunkIndex: Long
    ): ByteArray {
        val fullEncodedChunkBytes = (IV_BYTES + 4 + header.chunkSize + GCM_TAG_BYTES).toLong()
        val seekOffset = HEADER_SIZE.toLong() + chunkIndex * fullEncodedChunkBytes
        raf.seek(seekOffset)
        return readAndDecryptNextChunk(raf, header.fileSalt, chunkIndex)
    }

    private fun readAndDecryptNextChunk(
        raf: RandomAccessFile,
        fileSalt: ByteArray,
        chunkIndex: Long
    ): ByteArray {
        val iv = ByteArray(IV_BYTES)
        raf.readFully(iv)
        val cipherLen = raf.readInt()
        require(cipherLen > GCM_TAG_BYTES && cipherLen <= DEFAULT_CHUNK_SIZE * 2) {
            "Invalid encrypted chunk length: $cipherLen"
        }
        val cipherBytes = ByteArray(cipherLen)
        raf.readFully(cipherBytes)

        val secretKey = getOrCreateMasterKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
        cipher.updateAAD(buildChunkAad(fileSalt, chunkIndex))
        return cipher.doFinal(cipherBytes)
    }

    private fun writeHeader(
        raf: RandomAccessFile,
        chunkSize: Int,
        plaintextLength: Long,
        fileSalt: ByteArray
    ) {
        raf.write(FILE_MAGIC)
        raf.writeInt(chunkSize)
        raf.writeLong(plaintextLength)
        raf.write(fileSalt)
    }

    private fun buildChunkAad(fileSalt: ByteArray, chunkIndex: Long): ByteArray {
        return ByteBuffer.allocate(fileSalt.size + 8)
            .put(fileSalt)
            .putLong(chunkIndex)
            .array()
    }

    private fun readFullChunk(inputStream: InputStream, buffer: ByteArray): Int {
        var total = 0
        while (total < buffer.size) {
            val read = inputStream.read(buffer, total, buffer.size - total)
            if (read == -1) break
            total += read
        }
        return total
    }

    fun generateSalt(bytes: Int = 16): ByteArray {
        return ByteArray(bytes).also { secureRandom.nextBytes(it) }
    }

    fun hashPin(pin: String, salt: ByteArray): ByteArray {
        val chars = pin.toCharArray()
        val spec = PBEKeySpec(chars, salt, PBKDF2_ITERATIONS, PBKDF2_KEY_BITS)
        return try {
            SecretKeyFactory.getInstance(PBKDF2_ALGORITHM).generateSecret(spec).encoded
        } finally {
            chars.fill('\u0000')
            spec.clearPassword()
        }
    }

    fun verifyPin(pin: String, salt: ByteArray, expectedHash: ByteArray): Boolean {
        val candidate = hashPin(pin, salt)
        return try {
            MessageDigest.isEqual(candidate, expectedHash)
        } finally {
            candidate.fill(0)
        }
    }

    fun secureDeleteFile(file: File?) {
        if (file == null || !file.exists()) return
        try {
            val length = file.length()
            if (length in 1L..8_388_608L) {
                RandomAccessFile(file, "rw").use { raf ->
                    val zeros = ByteArray(4096)
                    var remaining = length
                    raf.seek(0)
                    while (remaining > 0) {
                        val toWrite = minOf(zeros.size.toLong(), remaining).toInt()
                        raf.write(zeros, 0, toWrite)
                        remaining -= toWrite
                    }
                }
            }
        } catch (_: Exception) {
            // Fall through to delete
        } finally {
            file.delete()
        }
    }

    data class EncryptedFileHeader(
        val chunkSize: Int,
        val plaintextLength: Long,
        val fileSalt: ByteArray
    )
}
