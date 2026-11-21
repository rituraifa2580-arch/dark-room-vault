package com.example.security

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File
import java.util.UUID
import kotlin.coroutines.resume

/**
 * Discreetly captures a front-camera frame on unauthorized access attempts ONLY when
 * the user has explicitly enabled Intruder Selfie and granted CAMERA permission.
 * The image bytes are immediately encrypted via AES-256-GCM without touching disk unencrypted.
 */
object IntruderCameraCapture {

    fun hasCameraPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    suspend fun captureEncryptedSelfie(
        context: Context,
        lifecycleOwner: LifecycleOwner,
        cryptoEngine: VaultCryptoEngine
    ): String? {
        if (!hasCameraPermission(context)) return null

        val jpegBytes = captureFrontCameraJpeg(context, lifecycleOwner) ?: return null
        return withContext(Dispatchers.IO) {
            try {
                val intruderDir = File(context.filesDir, "vault_intruders").apply { mkdirs() }
                val dest = File(intruderDir, "intruder_${UUID.randomUUID()}.enc")
                ByteArrayInputStream(jpegBytes).use { input ->
                    cryptoEngine.encryptStreamToFile(input, dest, jpegBytes.size.toLong())
                }
                dest.absolutePath
            } catch (_: Exception) {
                null
            } finally {
                jpegBytes.fill(0)
            }
        }
    }

    private suspend fun captureFrontCameraJpeg(
        context: Context,
        lifecycleOwner: LifecycleOwner
    ): ByteArray? = suspendCancellableCoroutine { cont ->
        val mainExecutor = ContextCompat.getMainExecutor(context)
        val providerFuture = ProcessCameraProvider.getInstance(context)

        providerFuture.addListener({
            try {
                val cameraProvider = providerFuture.get()
                if (!cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
                    if (cont.isActive) cont.resume(null)
                    return@addListener
                }

                val imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .setFlashMode(ImageCapture.FLASH_MODE_OFF)
                    .build()

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    imageCapture
                )

                imageCapture.takePicture(
                    mainExecutor,
                    object : ImageCapture.OnImageCapturedCallback() {
                        override fun onCaptureSuccess(image: ImageProxy) {
                            val bytes = try {
                                val buffer = image.planes[0].buffer
                                ByteArray(buffer.remaining()).also { buffer.get(it) }
                            } catch (_: Exception) {
                                null
                            } finally {
                                image.close()
                                try {
                                    cameraProvider.unbindAll()
                                } catch (_: Exception) {
                                }
                            }
                            if (cont.isActive) cont.resume(bytes)
                        }

                        override fun onError(exception: ImageCaptureException) {
                            try {
                                cameraProvider.unbindAll()
                            } catch (_: Exception) {
                            }
                            if (cont.isActive) cont.resume(null)
                        }
                    }
                )
            } catch (_: Exception) {
                if (cont.isActive) cont.resume(null)
            }
        }, mainExecutor)
    }
}
