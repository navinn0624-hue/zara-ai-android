package com.zara.assistant

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.zara.assistant.overlay.FloatingOverlayManager
import com.zara.assistant.services.ZaraAccessibilityService
import com.zara.assistant.services.ZaraWakeWordService

class MainActivity : AppCompatActivity() {

    private lateinit var btnGrantOverlay: Button
    private lateinit var btnGrantAccessibility: Button
    private lateinit var btnTestZara: Button
    private lateinit var tvServiceStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnGrantOverlay = findViewById(R.id.btnGrantOverlay)
        btnGrantAccessibility = findViewById(R.id.btnGrantAccessibility)
        btnTestZara = findViewById(R.id.btnTestZara)
        tvServiceStatus = findViewById(R.id.tvServiceStatus)

        requestMicrophonePermission()
        requestBatteryOptimizationExemption()
        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatus()
        startBackgroundWakeServiceIfPermitted()
    }

    private fun setupListeners() {
        btnGrantOverlay.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            }
        }

        btnGrantAccessibility.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
            Toast.makeText(this, "Find 'Zara AI' and turn it ON", Toast.LENGTH_LONG).show()
        }

        btnTestZara.setOnClickListener {
            if (!hasOverlayPermission()) {
                Toast.makeText(this, "Please enable 'Display Over Other Apps' first!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            FloatingOverlayManager.show(this)
        }
    }

    private fun updatePermissionStatus() {
        val hasOverlay = hasOverlayPermission()
        val hasAccessibility = isAccessibilityServiceEnabled(this, ZaraAccessibilityService::class.java)

        if (hasOverlay) {
            btnGrantOverlay.text = "✓ Enabled"
            btnGrantOverlay.isEnabled = false
        } else {
            btnGrantOverlay.text = "Enable"
            btnGrantOverlay.isEnabled = true
        }

        if (hasAccessibility) {
            btnGrantAccessibility.text = "✓ Enabled"
            btnGrantAccessibility.isEnabled = false
        } else {
            btnGrantAccessibility.text = "Enable"
            btnGrantAccessibility.isEnabled = true
        }

        if (hasOverlay && hasAccessibility) {
            tvServiceStatus.text = "Status: Zara is Active 24/7! 🚀 (Screen ON/OFF)"
        } else {
            tvServiceStatus.text = "Status: Waiting for permissions..."
        }
    }

    private fun hasOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
    }

    private fun isAccessibilityServiceEnabled(context: Context, service: Class<*>): Boolean {
        val expectedComponentName = "${context.packageName}/${service.name}"
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.contains(expectedComponentName)
    }

    private fun requestMicrophonePermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 101)
        }
    }

    /**
     * Prevents Android from putting Zara to sleep when screen is turned off!
     */
    private fun requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun startBackgroundWakeServiceIfPermitted() {
        if (hasOverlayPermission() && ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            val serviceIntent = Intent(this, ZaraWakeWordService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        }
    }
}
