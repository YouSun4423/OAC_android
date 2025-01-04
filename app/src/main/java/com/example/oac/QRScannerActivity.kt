package com.example.oac

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.common.util.concurrent.ListenableFuture
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.ExecutionException

class QRScannerActivity : AppCompatActivity() {

    private val PERMISSION_REQUEST_CODE = 101
    private lateinit var cameraSelector: CameraSelector
    private lateinit var preview: Preview
    private lateinit var barcodeScanner: BarcodeScanner

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //setContentView(R.layout.activity_qrscanner)

        // Initialize the barcode scanner
        barcodeScanner = BarcodeScanning.getClient()

        // Request permissions if needed
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)
            != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                arrayOf(android.Manifest.permission.CAMERA),
                PERMISSION_REQUEST_CODE)
        } else {
            startCamera()
        }
    }

    private fun startCamera() {
        val cameraProviderFuture: ListenableFuture<ProcessCameraProvider> =
            ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                cameraSelector = CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK).build()
                preview = Preview.Builder().build()

                //preview.setSurfaceProvider(findViewById<PreviewView>(R.id.view_finder).surfaceProvider)

                cameraProvider.bindToLifecycle(this, cameraSelector, preview)
            } catch (e: ExecutionException) {
                Log.e("QRScanner", "Error starting camera", e)
            } catch (e: InterruptedException) {
                Log.e("QRScanner", "Camera provider interrupted", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    // This method should be called when the camera captures a frame
    private fun processBarcode(image: InputImage) {
        barcodeScanner.process(image)
            .addOnSuccessListener { barcodes ->
                for (barcode in barcodes) {
                    val value = barcode.displayValue
                    value?.let {
                        Toast.makeText(this, "QR Code: $it", Toast.LENGTH_LONG).show()
                        finish()  // Close the activity after successful scan
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e("QRScanner", "Barcode scanning failed", e)
            }
    }
}
