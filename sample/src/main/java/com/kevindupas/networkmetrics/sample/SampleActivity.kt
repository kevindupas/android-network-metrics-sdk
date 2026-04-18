package com.kevindupas.networkmetrics.sample

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.kevindupas.networkmetrics.core.NetworkMetricsConfig
import com.kevindupas.networkmetrics.core.NetworkMetricsSdk

class SampleActivity : AppCompatActivity() {

    private val requiredPermissions = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.READ_PHONE_STATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.READ_BASIC_PHONE_STATE)
        }
    }.toTypedArray()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) startSdk()
        else updateStatus("Permissions denied — SDK requires location + phone state")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
        }

        val statusView = TextView(this).apply { text = "SDK stopped" }
        val startBtn = Button(this).apply { text = "Start SDK" }
        val stopBtn  = Button(this).apply { text = "Stop SDK" }

        layout.addView(statusView)
        layout.addView(startBtn)
        layout.addView(stopBtn)
        setContentView(layout)

        fun updateStatus(msg: String) {
            statusView.text = msg
            Log.d("SampleActivity", msg)
        }

        startBtn.setOnClickListener {
            if (allPermissionsGranted()) startSdk()
            else permissionLauncher.launch(requiredPermissions)
        }

        stopBtn.setOnClickListener {
            NetworkMetricsSdk.stop(this)
            updateStatus("SDK stopped")
        }
    }

    private fun startSdk() {
        NetworkMetricsSdk.init(
            context = this,
            config = NetworkMetricsConfig(
                backendUrl = "https://your-backend.example.com/metrics",
                udpHost = "137.74.47.42",
                udpPort = 5005,
                tcpPort = 8230,
                intervalMs = 30 * 60 * 1000L,
                notificationTitle = "Network Metrics",
                notificationText = "Collecting network quality data…",
            )
        )
        NetworkMetricsSdk.start(this)
        Log.d("SampleActivity", "SDK started")
    }

    private fun allPermissionsGranted() = requiredPermissions.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun updateStatus(msg: String) {
        Log.d("SampleActivity", msg)
    }
}
