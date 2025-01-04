package com.example.oac

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.preference.PreferenceManager
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.LinearLayout
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit


class MainActivity : Activity() {
    private lateinit var mapView: MapView
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private var latitude: Double? = null
    private var longitude: Double? = null
    private var deviceId: String? = null
    private var lastUpdateTime: Long = 0 // 最後の更新時刻
    private var lastLatitude: Double? = null
    private var lastLongitude: Double? = null

    private var timeOut = "300000"
    private var readTime = "100000"
    private val client = OkHttpClient.Builder()
        .connectTimeout(timeOut.toLong(), TimeUnit.MILLISECONDS)
        .readTimeout(readTime.toLong(), TimeUnit.MILLISECONDS)
        .build()


    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // MapViewの設定
        Configuration.getInstance().load(applicationContext,
            PreferenceManager.getDefaultSharedPreferences(applicationContext))
        setContentView(R.layout.activity_main)

        // MapViewの初期化
        mapView = findViewById(R.id.mapView)
        mapView.setTileSource(TileSourceFactory.MAPNIK)

        // FusedLocationProviderClientの初期化
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // 位置情報の権限を確認
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_BACKGROUND_LOCATION
                    )
                } else {
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
                },
                1000 // 権限要求のリクエストコード
            )
        } else {
            // 権限が許可されている場合、現在地を取得して位置情報の更新を開始
            getCurrentLocation()
            startLocationUpdates()
        }

        // Device IDの取得
        deviceId = getDeviceUniqueId(this)

        // LocationServiceの起動 (位置情報をバックグラウンドで更新)
        val serviceIntent = Intent(this, LocationService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent) // Android O以上ではフォアグラウンドサービスを使用
        } else {
            startService(serviceIntent) // それ以外は通常のサービスを開始
        }
    }



    private fun getCurrentLocation() {
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
        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            if (location != null) {
                val geoPoint = GeoPoint(location.latitude, location.longitude)
                mapView.controller.setZoom(17.0)
                mapView.controller.setCenter(geoPoint)

                val marker = Marker(mapView)
                marker.position = geoPoint
                marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                mapView.overlays.add(marker)
                mapView.invalidate()
            }
        }.addOnFailureListener {
            // エラー処理
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1000 && grantResults.isNotEmpty()) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    if (ActivityCompat.checkSelfPermission(
                            this,
                            Manifest.permission.ACCESS_BACKGROUND_LOCATION
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        Log.d("Permission", "Request BackgroundLocationPermission.")
                        requestBackgroundLocationPermission()
                    } else {
                        Log.d("Permission", "Start LocationUpdates.")
                        startLocationUpdates() // ここで位置情報更新を開始
                    }
                } else {
                    Log.d("Permission", "Start LocationUpdates.")
                    startLocationUpdates() // Android 9以下はこのまま開始
                }
            } else {
                Log.d("Permission", "Foreground location permission denied.")
            }
        }
    }



    private fun requestBackgroundLocationPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
            2000
        )
    }

    fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        val locationRequest = LocationRequest.create().apply {
            interval = 10000 // 10秒ごとに位置情報を取得
            fastestInterval = 5000 // 最短間隔（5秒ごと）
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }

        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                for (location in locationResult.locations) {
                    val currentTime = getFormattedCurrentDateTime().toLong()

                    // 5メートル以上移動しているか、5秒以上経過しているかをチェック
                    if (isLocationChanged(location) || isTimeElapsed(currentTime)) {
                        latitude = location.latitude
                        longitude = location.longitude
                        lastLatitude = latitude
                        lastLongitude = longitude
                        lastUpdateTime = currentTime

                        // POST処理
                        // Callback to handle the result
                        val endProcess: (String) -> Unit = { result ->
                            // Handle the result here
                            if (result == "位置情報送信成功") {
                                Log.d("LocationStatus", "Location sent successfully.")
                            } else {
                                Log.e("LocationStatus", "Location send failed.")
                            }
                        }
                        //Call the postLocation method
                        postLocation(deviceId!!, latitude.toString(), longitude.toString(), lastUpdateTime.toString(), endProcess)
                    }
                }
            }
        }

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
    }

    // 位置が5メートル以上移動したかどうかを確認
    private fun isLocationChanged(location: Location): Boolean {
        lastLatitude?.let {
            lastLongitude?.let {
                val distance = FloatArray(1)
                Location.distanceBetween(it, it, location.latitude, location.longitude, distance)
                return distance[0] >= 5 // 5メートル以上移動した場合
            }
        }
        return false
    }

    // 5秒以上経過したかどうかを確認
    private fun isTimeElapsed(currentTime: Long): Boolean {
        return currentTime - lastUpdateTime >= 5000 // 5秒以上経過している場合
    }

    fun getDeviceUniqueId(context: Context): String {
        return Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        )
    }

    fun getFormattedCurrentDateTime(): String {
        val currentDate = Date()
        val dateFormat = SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault())
        return dateFormat.format(currentDate)
    }

    fun postLocation(deviceId: String, lat: String, lon: String, currentDateTime: String, endProcess: (String) -> Unit) {
        var compMsg = "位置情報送信失敗"

        println("dev"+deviceId)
        println("lat"+ lat)
        println("lon"+ lon)
        println("ts"+ currentDateTime)

        // クエリパラメータをURLに追加
        val url = "http://arta.exp.mnb.ees.saitama-u.ac.jp/oac/common/post_location.php?" +
                "dev=$deviceId&lat=$lat&lon=$lon&ts=$currentDateTime"

        // リクエストを作成
        val request = Request.Builder()
            .url(url) // クエリパラメータ付きURL
            .header("Connection", "close")
            .get() // GETリクエストに変更
            .build()

        // 非同期リクエストを実行
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("Error", e.toString())
                endProcess(compMsg) // 処理が失敗した場合
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val responseBody = response.body?.string().orEmpty()
                    Log.d("Response", responseBody)
                    if (response.code == 200) {
                        compMsg = "位置情報送信成功"
                    }
                } catch (e: Exception) {
                    Log.e("Error", e.toString())
                } finally {
                    // 終了処理
                }
            }
        })
    }

}


