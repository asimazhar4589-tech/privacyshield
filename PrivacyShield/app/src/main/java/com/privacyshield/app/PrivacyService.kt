package com.privacyshield.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import kotlin.math.atan2
import kotlin.math.abs
import kotlin.math.sqrt

class PrivacyService : Service(), SensorEventListener {

    private lateinit var windowManager: WindowManager
    private var overlayView: PrivacyOverlayView? = null
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null

    private var tiltThreshold: Float = 20f
    private var maxOpacity: Float = 1.0f

    companion object {
        const val CHANNEL_ID = "PrivacyShieldChannel"
        const val NOTIFICATION_ID = 1001
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        loadPrefs()
        createNotificationChannel()
        startInForeground()
        addOverlay()
        registerSensor()
    }

    private fun loadPrefs() {
        val prefs = getSharedPreferences("privacy_shield_prefs", MODE_PRIVATE)
        tiltThreshold = prefs.getFloat("tilt_threshold", 20f)
        maxOpacity = prefs.getFloat("opacity", 100f) / 100f
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Privacy Shield",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the privacy overlay running"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun startInForeground() {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("PrivacyShield Active")
            .setContentText("Tilt detection running — your screen is protected.")
            .setSmallIcon(R.drawable.ic_shield_eye)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun addOverlay() {
        overlayView = PrivacyOverlayView(this).apply {
            setMaxAlpha(maxOpacity)
        }

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START

        try {
            windowManager.addView(overlayView, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun registerSensor() {
        accelerometer?.also {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        // Roll angle (left/right tilt). atan2 returns radians; convert to degrees.
        val roll = Math.toDegrees(
            atan2(x.toDouble(), sqrt((y * y + z * z).toDouble()))
        ).toFloat()

        val absRoll = abs(roll)

        // Compute alpha based on threshold range
        val lowerBound = (tiltThreshold - 5f).coerceAtLeast(5f)  // start fade
        val upperBound = tiltThreshold + 5f                       // fully opaque

        val alphaFraction = when {
            absRoll <= lowerBound -> 0f
            absRoll >= upperBound -> 1f
            else -> (absRoll - lowerBound) / (upperBound - lowerBound)
        }

        overlayView?.updateAlpha(alphaFraction)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            sensorManager.unregisterListener(this)
            overlayView?.let { windowManager.removeView(it) }
            overlayView = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}