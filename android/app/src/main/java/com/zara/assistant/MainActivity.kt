package com.zara.assistant

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.switchmaterial.SwitchMaterial
import com.zara.assistant.overlay.FloatingOverlayManager
import com.zara.assistant.services.FloatingBubbleService
import com.zara.assistant.services.ZaraAccessibilityService
import com.zara.assistant.services.ZaraWakeWordService

class MainActivity : AppCompatActivity() {

    private lateinit var btnGrantOverlay: Button
    private lateinit var btnGrantAccessibility: Button
    private lateinit var btnGrantMic: Button
    private lateinit var btnLaunchSiri: Button
    private lateinit var switchFloatingBubble: SwitchMaterial
    private lateinit var etGeminiKey: EditText
    private lateinit var btnSaveGeminiKey: Button
    private lateinit var tvGetGeminiKey: TextView
    private lateinit var heroSiriOrbContainer: View
    private lateinit var tvHeaderLiveStatus: TextView

    private var heroPulseAnimator: ObjectAnimator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnGrantOverlay = findViewById(R.id.btnGrantOverlay)
        btnGrantAccessibility = findViewById(R.id.btnGrantAccessibility)
        btnGrantMic = findViewById(R.id.btnGrantMic)
        btnLaunchSiri = findViewById(R.id.btnLaunchSiri)
        switchFloatingBubble = findViewById(R.id.switchFloatingBubble)
        etGeminiKey = findViewById(R.id.etGeminiKey)
        btnSaveGeminiKey = findViewById(R.id.btnSaveGeminiKey)
        tvGetGeminiKey = findViewById(R.id.tvGetGeminiKey)
        heroSiriOrbContainer = findViewById(R.id.heroSiriOrbContainer)
        tvHeaderLiveStatus = findViewById(R.id.tvHeaderLiveStatus)

        loadSavedSettings()
        startHeroOrbAnimation()
        requestMicrophonePermission()
        requestBatteryOptimizationExemption()
        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatus()
        startBackgroundWakeServiceIfPermitted()
        manageFloatingBubbleService()
    }

    override fun onDestroy() {
        super.onDestroy()
        heroPulseAnimator?.cancel()
    }

    private fun startHeroOrbAnimation() {
        val midRing = findViewById<View>(R.id.heroGlowMiddle)
        val scaleX = PropertyValuesHolder.ofFloat(View.SCALE_X, 0.94f, 1.12f)
        val scaleY = PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.94f, 1.12f)
        heroPulseAnimator = ObjectAnimator.ofPropertyValuesHolder(midRing, scaleX, scaleY).apply {
            duration = 1500
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    private fun loadSavedSettings() {
        val prefs = getSharedPreferences("zara_prefs", Context.MODE_PRIVATE)
        val defaultKey = try {
            String(android.util.Base64.decode("QVEuQWI4Uk42SUFXckRlY19OMHNNWnNWRUMteEVNOXRJY19ibDd3OEZBMEhSTWJtYTM0WFE=", android.util.Base64.DEFAULT), Charsets.UTF_8).trim()
        } catch (e: Exception) {
            ""
        }
        val savedKey = prefs.getString("gemini_api_key", null)?.takeIf { it.isNotBlank() } ?: defaultKey
        etGeminiKey.setText(savedKey)
        if (!prefs.contains("gemini_api_key") && defaultKey.isNotEmpty()) {
            prefs.edit().putString("gemini_api_key", defaultKey).apply()
        }
        switchFloatingBubble.isChecked = prefs.getBoolean("enable_floating_bubble", true)
    }

    private fun setupListeners() {
        // Tapping the Centerpiece Orb activates Siri immediately
        heroSiriOrbContainer.setOnClickListener {
            triggerSiriOverlay()
        }

        btnLaunchSiri.setOnClickListener {
            triggerSiriOverlay()
        }

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
            Toast.makeText(this, "Find 'Zara AI' and switch it ON", Toast.LENGTH_LONG).show()
        }

        btnGrantMic.setOnClickListener {
            requestMicrophonePermission()
        }

        switchFloatingBubble.setOnCheckedChangeListener { _, isChecked ->
            val prefs = getSharedPreferences("zara_prefs", Context.MODE_PRIVATE)
            prefs.edit().putBoolean("enable_floating_bubble", isChecked).apply()
            manageFloatingBubbleService()
        }

        btnSaveGeminiKey.setOnClickListener {
            val key = etGeminiKey.text.toString().trim()
            val prefs = getSharedPreferences("zara_prefs", Context.MODE_PRIVATE)
            prefs.edit().putString("gemini_api_key", key).apply()
            Toast.makeText(this, "Gemini AI Brain Connected! 🧠✨", Toast.LENGTH_SHORT).show()
        }

        tvGetGeminiKey.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/app/apikey"))
            startActivity(intent)
        }
    }

    private fun triggerSiriOverlay() {
        if (!hasOverlayPermission()) {
            Toast.makeText(this, "Please enable 'Siri Floating Display' first!", Toast.LENGTH_SHORT).show()
            return
        }
        FloatingOverlayManager.show(this)
    }

    private fun updatePermissionStatus() {
        val hasOverlay = hasOverlayPermission()
        val hasAccessibility = isAccessibilityServiceEnabled(this, ZaraAccessibilityService::class.java)
        val hasMic = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

        if (hasOverlay) {
            btnGrantOverlay.text = "Active ✓"
            btnGrantOverlay.isEnabled = false
            btnGrantOverlay.alpha = 0.6f
        } else {
            btnGrantOverlay.text = "Enable"
            btnGrantOverlay.isEnabled = true
            btnGrantOverlay.alpha = 1.0f
        }

        if (hasAccessibility) {
            btnGrantAccessibility.text = "Active ✓"
            btnGrantAccessibility.isEnabled = false
            btnGrantAccessibility.alpha = 0.6f
        } else {
            btnGrantAccessibility.text = "Enable"
            btnGrantAccessibility.isEnabled = true
            btnGrantAccessibility.alpha = 1.0f
        }

        if (hasMic) {
            btnGrantMic.text = "Active ✓"
            btnGrantMic.isEnabled = false
            btnGrantMic.alpha = 0.6f
        } else {
            btnGrantMic.text = "Enable"
            btnGrantMic.isEnabled = true
            btnGrantMic.alpha = 1.0f
        }

        if (hasOverlay && hasAccessibility && hasMic) {
            tvHeaderLiveStatus.text = "ONLINE"
            tvHeaderLiveStatus.setTextColor(ContextCompat.getColor(this, R.color.emerald_400))
        } else {
            tvHeaderLiveStatus.text = "SETUP"
            tvHeaderLiveStatus.setTextColor(ContextCompat.getColor(this, R.color.zinc_400))
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
        val permissions = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.RECORD_AUDIO)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.CALL_PHONE)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.READ_CONTACTS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 101)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        updatePermissionStatus()
        startBackgroundWakeServiceIfPermitted()
        manageFloatingBubbleService()
    }

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
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent)
                } else {
                    startService(serviceIntent)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun manageFloatingBubbleService() {
        val prefs = getSharedPreferences("zara_prefs", Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean("enable_floating_bubble", true)
        val bubbleIntent = Intent(this, FloatingBubbleService::class.java)

        if (enabled && hasOverlayPermission()) {
            try {
                startService(bubbleIntent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            try {
                stopService(bubbleIntent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
