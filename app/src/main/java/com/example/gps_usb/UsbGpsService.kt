package com.example.gps_usb

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 前台服务，负责获取GPS数据并通过TCP服务器发送数据
 */
class UsbGpsService : Service() {
    
    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val NOTIFICATION_CHANNEL_ID = "gps_usb_channel"
        private const val TCP_PORT = 12345
        
        // 服务命令相关常量
        const val ACTION_START_SERVICE = "com.example.gps_usb.START_SERVICE"
        const val ACTION_STOP_SERVICE = "com.example.gps_usb.STOP_SERVICE"
    }
    
    // 用于服务的协程作用域
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    
    // 组件
    private lateinit var gpsLocationProvider: GpsLocationProvider
    private lateinit var localTcpServerManager: LocalTcpServerManager
    
    // 状态
    private var isServiceRunning = false
    private var clientCount = 0
    
    // JSON 序列化
    private val gson = Gson()
    
    override fun onCreate() {
        super.onCreate()
        
        // 创建GPS位置提供者
        gpsLocationProvider = GpsLocationProvider(this) { location ->
            processLocation(location)
        }
        
        // 创建TCP服务器管理器
        localTcpServerManager = LocalTcpServerManager(
            TCP_PORT,
            serviceScope
        ) { newClientCount ->
            clientCount = newClientCount
            updateNotification()
        }
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_SERVICE -> startGpsService()
            ACTION_STOP_SERVICE -> stopGpsService()
        }
        return START_STICKY
    }
    
    /**
     * 启动GPS服务
     */
    private fun startGpsService() {
        if (isServiceRunning) return
        
        // 创建并启动前台通知
        startForeground(NOTIFICATION_ID, createNotification())
        
        // 启动组件
        serviceScope.launch {
            val gpsStarted = gpsLocationProvider.startLocationUpdates()
            
            if (gpsStarted) {
                if (localTcpServerManager.startServer()) {
                    isServiceRunning = true
                    updateNotification()
                }
            }
        }
    }
    
    /**
     * 停止GPS服务
     */
    private fun stopGpsService() {
        isServiceRunning = false
        stopSelf()
    }
    
    /**
     * 处理接收到的位置信息
     */
    private fun processLocation(location: Location) {
        serviceScope.launch {
            // 转换为GpsData对象
            val gpsData = GpsData(
                latitude = location.latitude,
                longitude = location.longitude,
                timestamp = location.time,
                accuracy = location.accuracy,
                speed = if (location.hasSpeed()) location.speed else null,
                altitude = if (location.hasAltitude()) location.altitude else null
            )
            
            // 序列化为JSON（添加换行符作为消息分隔符）
            val jsonData = gson.toJson(gpsData) + "\n"
            
            // 发送到所有连接的客户端
            localTcpServerManager.sendDataToClients(jsonData)
        }
    }
    
    /**
     * 创建前台通知
     */
    private fun createNotification(): Notification {
        createNotificationChannel()
        
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("GPS Over USB")
            .setContentText("正在运行 | 端口: $TCP_PORT | 客户端: $clientCount")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation) // 使用系统图标作为临时图标
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
    
    /**
     * 创建通知渠道（Android 8.0及以上需要）
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "GPS USB Service"
            val descriptionText = "GPS数据通过USB传输服务"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    /**
     * 更新通知内容
     */
    private fun updateNotification() {
        if (isServiceRunning) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(NOTIFICATION_ID, createNotification())
        }
    }
    
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
    
    override fun onDestroy() {
        isServiceRunning = false
        
        // 停止位置更新
        gpsLocationProvider.stopLocationUpdates()
        
        // 停止TCP服务器
        localTcpServerManager.stopServer()
        
        // 取消所有协程
        serviceScope.cancel()
        
        // 移除前台通知
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        
        super.onDestroy()
    }
} 