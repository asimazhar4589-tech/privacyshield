package com.privacyshield.app

import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.SeekBar
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import android.widget.TextView
import android.view.View
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var switchEnable: MaterialSwitch
    private lateinit var statusText: TextView
    private lateinit var statusDot: View
    private lateinit var sliderTilt: Slider
    private lateinit var sliderOpacity: Slider
    private lateinit var tiltValueText: TextView
    private lateinit var opacityValueText: TextView

    private val overlayPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (Settings.canDrawOverlays(this)) {
                startPrivacyService()
                updateUi(true)
            } else {
                switchEnable.isChecked = false
                updateUi(false)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences("privacy_shield_prefs", MODE_PRIVATE)

        switchEnable = findViewById(R.id.switchEnable)
        statusText = findViewById(R.id.statusText)
        statusDot = findViewById(R.id.statusDot)
        sliderTilt = findViewById(R.id.sliderTilt)
        sliderOpacity = findViewById(R.id.sliderOpacity)
        tiltValueText = findViewById(R.id.tiltValueText)
        opacityValueText = findViewById(R.id.opacityValueText)

        // Load saved preferences
        val savedTilt = prefs.getFloat("tilt_threshold", 20f)
        val savedOpacity = prefs.getFloat("opacity", 100f)
        val isEnabled = prefs.getBoolean("enabled", false)

        sliderTilt.value = savedTilt
        sliderOpacity.value = savedOpacity
        tiltValueText.text = "${savedTilt.toInt()}°"
        opacityValueText.text = "${savedOpacity.toInt()}%"
        switchEnable.isChecked = isEnabled

        updateUi(isEnabled)

        switchEnable.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("enabled", isChecked).apply()
            if (isChecked) {
                if (!Settings.canDrawOverlays(this)) {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    overlayPermissionLauncher.launch(intent)
                } else {
                    startPrivacyService()
                    updateUi(true)
                }
            } else {
                stopPrivacyService()
                updateUi(false)
            }
        }

        sliderTilt.addOnChangeListener { _, value, _ ->
            tiltValueText.text = "${value.toInt()}°"
            prefs.edit().putFloat("tilt_threshold", value).apply()
            if (switchEnable.isChecked) restartService()
        }

        sliderOpacity.addOnChangeListener { _, value, _ ->
            opacityValueText.text = "${value.toInt()}%"
            prefs.edit().putFloat("opacity", value).apply()
            if (switchEnable.isChecked) restartService()
        }

        // Request notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 100)
        }
    }

    private fun updateUi(active: Boolean) {
        if (active) {
            statusText.text = getString(R.string.status_active)
            statusDot.setBackgroundResource(R.drawable.dot_green)
        } else {
            statusText.text = getString(R.string.status_inactive)
            statusDot.setBackgroundResource(R.drawable.dot_red)
        }
    }

    private fun startPrivacyService() {
        val intent = Intent(this, PrivacyService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }

    private fun stopPrivacyService() {
        stopService(Intent(this, PrivacyService::class.java))
    }

    private fun restartService() {
        stopPrivacyService()
        startPrivacyService()
    }
}