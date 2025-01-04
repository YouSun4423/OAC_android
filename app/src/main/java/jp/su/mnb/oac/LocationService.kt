package jp.su.mnb.oac

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class LocationService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val client = OkHttpClient.Builder()
        .connectTimeout(300000, TimeUnit.MILLISECONDS)
        .readTimeout(100000, TimeUnit.MILLISECONDS)
        .build()

    override fun onCreate() {
        super.onCreate()

        // 通知を作成して startForeground をすぐに呼び出す
        val notification = createNotification()
        startForeground(1, notification)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        startLocationUpdates()
    }

    private fun createNotification(): Notification {
        val notificationChannelId = "LOCATION_SERVICE_CHANNEL"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                notificationChannelId,
                "Location Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notificationBuilder = NotificationCompat.Builder(this, notificationChannelId)
            .setContentTitle("位置情報を取得中")
            .setContentText("バックグラウンドで位置情報を取得しています")
            .setPriority(NotificationCompat.PRIORITY_LOW)

        return notificationBuilder.build()
    }

    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.create().apply {
            interval = 10000 // 10秒ごとに位置情報を取得
            fastestInterval = 5000
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }

        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                for (location in locationResult.locations) {
                    val latitude = location.latitude
                    val longitude = location.longitude
                    val currentDateTime = getFormattedCurrentDateTime()

                    // ブロードキャストで位置情報を送信
                    val intent = Intent("com.example.oac.LOCATION_UPDATE")
                    intent.putExtra("latitude", latitude)
                    intent.putExtra("longitude", longitude)
                    sendBroadcast(intent)

                    postLocation(latitude, longitude, currentDateTime) { result ->
                        Log.d("LocationStatus", result)
                    }
                }
            }
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        }
    }

    private fun postLocation(lat: Double, lon: Double, currentDateTime: String, endProcess: (String) -> Unit) {
        val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        val url = "http://arta.exp.mnb.ees.saitama-u.ac.jp/oac/common/post_location.php?" +
                "dev=$deviceId&lat=$lat&lon=$lon&ts=$currentDateTime"

        val request = Request.Builder()
            .url(url)
            .header("Connection", "close")
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                endProcess("位置情報送信失敗")
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    endProcess("位置情報送信成功")
                } else {
                    endProcess("位置情報送信失敗")
                }
            }
        })
    }

    private fun getFormattedCurrentDateTime(): String {
        val currentDate = Date()
        val dateFormat = SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault())
        return dateFormat.format(currentDate)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
