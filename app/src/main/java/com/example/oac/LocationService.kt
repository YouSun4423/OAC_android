package com.example.oac

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
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
import java.util.concurrent.TimeUnit

class LocationService : Service() {
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        startForegroundService()  // フォアグラウンドサービスとして開始
        startLocationUpdates()    // 位置情報の更新を開始
    }

    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.create().apply {
            interval = 10000 // 10秒
            fastestInterval = 5000 // 最短間隔（5秒）
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                for (location in locationResult.locations) {
                    // 位置情報をログに出力
                    Log.d("LocationService", "Lat: ${location.latitude}, Lon: ${location.longitude}")

                    // 必要に応じてサーバーへ送信
                    postLocation(location)
                }
            }
        }

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
    }

    private fun postLocation(location: Location) {
        val latitude = location.latitude.toString()
        val longitude = location.longitude.toString()
        val timestamp = System.currentTimeMillis().toString()
        val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) // Device IDを取得

        // クエリパラメータをURLに追加
        val url = "http://arta.exp.mnb.ees.saitama-u.ac.jp/oac/common/post_location.php?" +
                "dev=$deviceId&lat=$latitude&lon=$longitude&ts=$timestamp"

        // OkHttpクライアントの設定
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        // リクエストを作成
        val request = Request.Builder()
            .url(url)
            .header("Connection", "close")
            .get()
            .build()

        // 非同期リクエストを実行
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("LocationService", "Failed to send location: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val responseBody = response.body?.string().orEmpty()
                    Log.d("LocationService", "Server Response: $responseBody")
                    if (response.isSuccessful) {
                        Log.d("LocationService", "Location sent successfully")
                    } else {
                        Log.e("LocationService", "Failed to send location: HTTP ${response.code}")
                    }
                } catch (e: Exception) {
                    Log.e("LocationService", "Error processing response: ${e.message}")
                }
            }
        })
    }

    private fun startForegroundService() {
        // 通知チャネルを作成
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "location_channel",  // チャネルID
                "Location Service",   // チャネル名
                NotificationManager.IMPORTANCE_LOW  // 通知の重要度
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        // 通知の設定
        val notification = NotificationCompat.Builder(this, "location_channel")
            .setContentTitle("Location Service")
            .setContentText("Running in the background to track location.")
            .build()

        // サービスをフォアグラウンドで開始
        startForeground(1, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
