package com.aightlabs.bustrack

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import kotlinx.coroutines.launch

class LocationService : Service() {

    companion object {
        const val TAG = "LocationService"
        const val CHANNEL_ID = "bustrack_location_channel"
        const val NOTIFICATION_ID = 1
        const val AUTO_STOP_NOTIFICATION_ID = 2
        const val ACTION_STOP = "com.aightlabs.bustrack.ACTION_STOP"
        const val LOCATION_INTERVAL_MS = 30_000L
        const val AUTO_STOP_DELAY_MS = 3 * 60 * 60 * 1000L // 3 hours

        fun start(context: Context) {
            val intent = Intent(context, LocationService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, LocationService::class.java)
            context.stopService(intent)
        }
    }

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var prefs: PrefsManager
    private val database = FirebaseDatabase.getInstance(BusTrackApp.DB_URL).reference
    private val handler = Handler(Looper.getMainLooper())
    private var vehicleId: String = ""

    private val autoStopRunnable = Runnable {
        Log.i(TAG, "Auto-stopping after 3 hours")
        stopTracking()
        showAutoStopNotification()
    }

    override fun onCreate() {
        super.onCreate()
        prefs = PrefsManager(this)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopTracking()
            return START_NOT_STICKY
        }

        vehicleId = prefs.getVehicleId()
        if (vehicleId.isEmpty()) {
            stopSelf()
            return START_NOT_STICKY
        }

        startForegroundWithNotification()
        startLocationUpdates()
        setStatusActive()

        // Schedule auto-stop after 3 hours
        handler.postDelayed(autoStopRunnable, AUTO_STOP_DELAY_MS)

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(autoStopRunnable)
        stopLocationUpdates()
        setStatusInactive()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "위치 공유",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "버스 위치 공유 중 표시"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun startForegroundWithNotification() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, LocationService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val mainIntent = Intent(this, MainActivity::class.java)
        val mainPendingIntent = PendingIntent.getActivity(
            this, 0, mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val kindergartenName = prefs.getKindergartenName()
        val vehicleName = prefs.getVehicleName()

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("위치 공유 중")
            .setContentText("${kindergartenName} ${vehicleName} - 운행 중")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(mainPendingIntent)
            .setOngoing(true)
            .addAction(
                android.R.drawable.ic_media_pause,
                "운행 종료",
                stopPendingIntent
            )
            .build()
    }

    private fun startLocationUpdates() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "Location permission not granted")
            stopSelf()
            return
        }

        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            LOCATION_INTERVAL_MS
        ).apply {
            setMinUpdateIntervalMillis(LOCATION_INTERVAL_MS / 2)
            setWaitForAccurateLocation(false)
        }.build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                sendLocationToFirebase(location.latitude, location.longitude, location.speed)
            }
        }

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
    }

    private fun stopLocationUpdates() {
        if (::locationCallback.isInitialized) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
    }

    private fun sendLocationToFirebase(lat: Double, lng: Double, speed: Float) {
        if (vehicleId.isEmpty()) return

        val locationData = mapOf(
            "lat" to lat,
            "lng" to lng,
            "speed" to speed.toDouble(),
            "updatedAt" to ServerValue.TIMESTAMP
        )

        database.child("vehicles").child(vehicleId)
            .child("location").setValue(locationData)
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to send location: ${e.message}")
            }
    }

    private fun setStatusActive() {
        if (vehicleId.isEmpty()) return
        database.child("vehicles").child(vehicleId)
            .child("status").setValue("active")
    }

    private fun setStatusInactive() {
        if (vehicleId.isEmpty()) return
        database.child("vehicles").child(vehicleId)
            .child("status").setValue("inactive")
    }

    private fun stopTracking() {
        handler.removeCallbacks(autoStopRunnable)
        prefs.setActive(false)
        setStatusInactive()
        // Glance 상태 변경 + 위젯 갱신을 서비스 종료 전에 수행
        kotlinx.coroutines.MainScope().launch {
            BusTrackWidget.setActiveStateAll(this@LocationService, false)
            stopSelf()
        }
    }

    private fun showAutoStopNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("운행 자동 종료")
            .setContentText("운행이 자동 종료되었습니다 (3시간 경과)")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setAutoCancel(true)
            .build()
        manager.notify(AUTO_STOP_NOTIFICATION_ID, notification)
    }
}
