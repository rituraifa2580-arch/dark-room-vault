package com.example.media

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import com.example.security.VaultCryptoEngine
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

/**
 * Custom Media3 DataSource that decrypts AES-256-GCM encrypted media chunks on the fly in RAM.
 * Supports full seeking for Video and Audio playback without ever writing plaintext to disk.
 */
@OptIn(UnstableApi::class)
class EncryptedVaultDataSource(
    private val cryptoEngine: VaultCryptoEngine
) : BaseDataSource(/* isNetwork = */ false) {

    class Factory(
        private val cryptoEngine: VaultCryptoEngine
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource = EncryptedVaultDataSource(cryptoEngine)
    }

    private var raf: RandomAccessFile? = null
    private var header: VaultCryptoEngine.EncryptedFileHeader? = null
    private var currentUri: Uri? = null
    private var currentPosition: Long = 0L
    private var bytesRemaining: Long = 0L
    private var opened = false

    private var cachedChunkIndex: Long = -1L
    private var cachedChunkPlain: ByteArray? = null

    override fun open(dataSpec: DataSpec): Long {
        try {
            currentUri = dataSpec.uri
            transferInitializing(dataSpec)

            val path = dataSpec.uri.path
                ?: throw IOException("Missing encrypted vault file path in URI: ${dataSpec.uri}")
            val file = File(path)
            if (!file.exists()) {
                throw IOException("Encrypted media file not found: $path")
            }

            val accessFile = RandomAccessFile(file, "r")
            raf = accessFile
            val parsedHeader = cryptoEngine.readHeader(accessFile)
            header = parsedHeader

            if (dataSpec.position > parsedHeader.plaintextLength) {
                throw IOException("Requested position beyond encrypted plaintext length")
            }

            currentPosition = dataSpec.position
            bytesRemaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
                minOf(dataSpec.length, parsedHeader.plaintextLength - currentPosition)
            } else {
                parsedHeader.plaintextLength - currentPosition
            }

            opened = true
            transferStarted(dataSpec)
            return bytesRemaining
        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            throw IOException("Failed to open encrypted media stream", e)
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining <= 0L) return C.RESULT_END_OF_INPUT

        val activeHeader = header ?: return C.RESULT_END_OF_INPUT
        val activeRaf = raf ?: return C.RESULT_END_OF_INPUT

        return try {
            val chunkIndex = currentPosition / activeHeader.chunkSize
            val intraOffset = (currentPosition % activeHeader.chunkSize).toInt()

            if (cachedChunkIndex != chunkIndex || cachedChunkPlain == null) {
                cachedChunkPlain?.fill(0)
                cachedChunkPlain = cryptoEngine.decryptChunkAtIndex(activeRaf, activeHeader, chunkIndex)
                cachedChunkIndex = chunkIndex
            }

            val chunkBytes = cachedChunkPlain ?: return C.RESULT_END_OF_INPUT
            val availableInChunk = chunkBytes.size - intraOffset
            if (availableInChunk <= 0) return C.RESULT_END_OF_INPUT

            val toCopy = minOf(length.toLong(), bytesRemaining, availableInChunk.toLong()).toInt()
            System.arraycopy(chunkBytes, intraOffset, buffer, offset, toCopy)

            currentPosition += toCopy
            bytesRemaining -= toCopy
            bytesTransferred(toCopy)
            toCopy
        } catch (e: Exception) {
            throw IOException("AES-256-GCM chunk decryption error during playback", e)
        }
    }

    override fun getUri(): Uri? = currentUri

    override fun close() {
        currentUri = null
        cachedChunkPlain?.fill(0)
        cachedChunkPlain = null
        cachedChunkIndex = -1L
        try {
            raf?.close()
        } finally {
            raf = null
            header = null
            if (opened) {
                opened = false
                transferEnded()
            }
        }
    }
}
