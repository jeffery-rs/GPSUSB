package com.example.gps_usb

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

/**
 * 封装与FusedLocationProviderClient的交互，提供定位更新功能
 *
 * @param context 应用程序上下文
 * @param onLocationUpdate 回调函数，当收到新的位置信息时被调用
 */
class GpsLocationProvider(
    private val context: Context,
    private val onLocationUpdate: (Location) -> Unit
) {
    private val fusedLocationClient: FusedLocationProviderClient = 
        LocationServices.getFusedLocationProviderClient(context)
    
    private val locationRequest = LocationRequest.Builder(5000L) // 5秒更新一次
        .setMinUpdateIntervalMillis(2000L) // 最快2秒更新一次
        .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
        .build()
    
    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            locationResult.lastLocation?.let { location ->
                onLocationUpdate(location)
            }
        }
    }
    
    /**
     * 开始请求位置更新
     * @return 如果成功启动位置更新则返回true，否则返回false（通常是因为权限问题）
     */
    fun startLocationUpdates(): Boolean {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        
        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
        
        return true
    }
    
    /**
     * 停止位置更新
     */
    fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }
} 