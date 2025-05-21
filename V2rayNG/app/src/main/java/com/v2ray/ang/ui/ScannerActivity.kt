package com.v2ray.ang.ui

import android.Manifest
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Menu
import android.view.MenuItem
import androidx.activity.result.contract.ActivityResultContracts
import com.tbruyelle.rxpermissions3.RxPermissions
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.extension.toast
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.util.QRCodeDecoder
import io.github.g00fy2.quickie.QRResult
import io.github.g00fy2.quickie.ScanCustomCode
import io.github.g00fy2.quickie.config.ScannerConfig
import io.github.g00fy2.quickie.config.BarcodeFormat
import android.util.Log

class ScannerActivity : BaseActivity() {

    private val TAG = "ScannerActivity"
    private val scanQrCode = registerForActivityResult(ScanCustomCode(), ::handleResult)
    private var isScanning = false
    private val handler = Handler(Looper.getMainLooper())
    private var retryCount = 0
    private val MAX_RETRIES = 3

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate: Initializing scanner")
        
        // Wait for activity to be fully created before starting camera
        handler.postDelayed({
            if (!isFinishing && !isDestroyed) {
                startScanner()
            }
        }, 1000)
    }

    override fun onResume() {
        super.onResume()
        if (!isScanning && retryCount < MAX_RETRIES) {
            handler.postDelayed({
                if (!isFinishing && !isDestroyed) {
                    startScanner()
                }
            }, 1000)
        }
    }

    override fun onPause() {
        super.onPause()
        cleanupScanner()
    }

    override fun onStop() {
        super.onStop()
        cleanupScanner()
    }

    override fun onDestroy() {
        super.onDestroy()
        cleanupScanner()
    }

    private fun cleanupScanner() {
        isScanning = false
        handler.removeCallbacksAndMessages(null)
    }

    private fun startScanner() {
        if (isScanning) return
        
        try {
            Log.d(TAG, "Starting QR scanner, attempt #$retryCount")
            isScanning = true
            retryCount++
            
            scanQrCode.launch(
                ScannerConfig.build {
                    setHapticSuccessFeedback(true)
                    setShowTorchToggle(true)
                    setShowCloseButton(true)
                    setUseFrontCamera(false)
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error starting scanner", e)
            e.printStackTrace()
            handleScanError()
        }
    }

    private fun handleScanError() {
        isScanning = false
        if (retryCount < MAX_RETRIES) {
            handler.postDelayed({
                if (!isFinishing && !isDestroyed) {
                    startScanner()
                }
            }, 2000)
        } else {
            toast(R.string.toast_failure)
            finish()
        }
    }

    private fun handleResult(result: QRResult) {
        Log.d(TAG, "QR scan result received: ${result::class.java.simpleName}")
        cleanupScanner()
        
        when (result) {
            is QRResult.QRSuccess -> {
                val content = result.content.rawValue.orEmpty()
                Log.d(TAG, "Successful scan, content length: ${content.length}")
                handler.postDelayed({
                    if (!isFinishing) {
                        finished(content)
                    }
                }, 500)
            }
            else -> {
                Log.w(TAG, "Scan failed or cancelled")
                handleScanError()
            }
        }
    }

    private fun finished(text: String) {
        try {
            Log.d(TAG, "Finishing with scan result length: ${text.length}")
            Log.d(TAG, "Scan result first 20 chars: ${text.take(20)}...")
            Log.d(TAG, "Scan result protocol: ${text.substringBefore("://")}")
            val intent = Intent()
            intent.putExtra("SCAN_RESULT", text)
            Log.d(TAG, "Setting result with intent extras: ${intent.extras?.keySet()?.joinToString()}")
            setResult(RESULT_OK, intent)
            finish()
        } catch (e: Exception) {
            Log.e(TAG, "Error finishing scanner activity", e)
            e.printStackTrace()
            toast(R.string.toast_failure)
            finish()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_scanner, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.scan_code -> {
            if (!isScanning && retryCount < MAX_RETRIES) {
                retryCount = 0
                startScanner()
            }
            true
        }

        R.id.select_photo -> {
            val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Manifest.permission.READ_MEDIA_IMAGES
            } else {
                Manifest.permission.READ_EXTERNAL_STORAGE
            }
            RxPermissions(this)
                .request(permission)
                .subscribe { granted ->
                    if (granted) {
                        showFileChooser()
                    } else {
                        toast(R.string.toast_permission_denied)
                    }
                }
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    private fun showFileChooser() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "image/*"
        intent.addCategory(Intent.CATEGORY_OPENABLE)

        try {
            chooseFile.launch(Intent.createChooser(intent, getString(R.string.title_file_chooser)))
        } catch (ex: android.content.ActivityNotFoundException) {
            toast(R.string.toast_require_file_manager)
        }
    }

    private val chooseFile = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val uri = it.data?.data
        if (it.resultCode == RESULT_OK && uri != null) {
            try {
                val inputStream = contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()

                val text = QRCodeDecoder.syncDecodeQRCode(bitmap)
                if (text.isNullOrEmpty()) {
                    toast(R.string.toast_decoding_failed)
                } else {
                    finished(text)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                toast(R.string.toast_decoding_failed)
            }
        }
    }
}
