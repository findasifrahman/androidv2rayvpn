package com.v2ray.ang.ui

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import com.tbruyelle.rxpermissions3.RxPermissions
import com.v2ray.ang.R
import com.v2ray.ang.extension.toast
import com.v2ray.ang.handler.AngConfigManager

class ScScannerActivity : BaseActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private var isScanning = false
    private var retryCount = 0
    private val MAX_RETRIES = 3
    private val TAG = "ScScannerActivity"

    ////
    // Launcher for requesting the CAMERA permission
    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                // Launch the appropriate scanner based on the flag
                scanQRCode.launch(Intent(this, ScannerActivity::class.java))
            } else {
                // Permission denied; show a toast message
                Toast.makeText(this, R.string.toast_permission_denied, Toast.LENGTH_SHORT).show()
            }
        }
    ////

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_none)
        Log.d(TAG, "onCreate: Starting scanner activity")
        
        // Delay the initial scan to ensure proper activity creation
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
        importQRcode()
    }

    fun importQRcode(): Boolean {
        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        return true
    }

    private fun handleScanError() {
        Log.d(TAG, "handleScanError: retryCount=$retryCount")
        cleanupScanner()
        if (retryCount < MAX_RETRIES) {
            handler.postDelayed({
                if (!isFinishing && !isDestroyed) {
                    Log.d(TAG, "Retrying scan...")
                    startScanner()
                }
            }, 2000)
        } else {
            Log.w(TAG, "Max retries reached, showing failure toast")
            toast(R.string.toast_failure)
            finish()
        }
    }

    private val scanQRCode = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        cleanupScanner()
        Log.d(TAG, "Received activity result with code: ${it.resultCode}")
        
        if (it.resultCode == RESULT_OK) {
            try {
                val extras = it.data?.extras
                Log.d(TAG, "Result intent extras: ${extras?.keySet()?.joinToString()}")
                val scanResult = it.data?.getStringExtra("SCAN_RESULT")
                if (scanResult == null) {
                    Log.e(TAG, "Scan result is null")
                    handleScanError()
                    return@registerForActivityResult
                }
                
                Log.d(TAG, "Scan result received, first 20 chars: ${scanResult.take(20)}...")
                Log.d(TAG, "Scan result protocol: ${scanResult.substringBefore("://")}")
                Log.d(TAG, "Full scan result for debugging: $scanResult")
                
                try {
                    Log.d(TAG, "Attempting to import config...")
                    val count = AngConfigManager.importBatchConfig(scanResult, "", false)
                    Log.d(TAG, "Import result count: $count")

                    handler.postDelayed({
                        if (!isFinishing) {
                            if (count > 0) {
                                Log.d(TAG, "Import successful, showing success toast")
                                toast(R.string.toast_success)
                                handler.postDelayed({
                                    if (!isFinishing) {
                                        Log.d(TAG, "Starting MainActivity")
                                        startActivity(Intent(this, MainActivity::class.java))
                                        finish()
                                    }
                                }, 800)
                            } else {
                                Log.e(TAG, "Import failed with count: $count, protocol type might be unsupported")
                                handleScanError()
                            }
                        }
                    }, 500)
                } catch (e: Exception) {
                    Log.e(TAG, "Config import error: ${e.message}", e)
                    e.printStackTrace()
                    handleScanError()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Scan result processing error: ${e.message}", e)
                e.printStackTrace()
                handleScanError()
            }
        } else {
            Log.w(TAG, "Scan cancelled or failed with resultCode: ${it.resultCode}")
            handleScanError()
        }
    }
}
