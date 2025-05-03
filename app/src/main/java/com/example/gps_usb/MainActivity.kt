package com.example.gps_usb

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 主活动，用于控制GPS USB服务并显示状态
 */
class MainActivity : ComponentActivity() {
    
    companion object {
        private const val DEVICE_PORT = 12345
        private const val PC_PORT = 54321
        const val ACTION_GPS_DATA_UPDATED = "com.example.gps_usb.GPS_DATA_UPDATED"
        const val ACTION_DATA_SENT = "com.example.gps_usb.DATA_SENT"
        const val EXTRA_LATITUDE = "extra_latitude"
        const val EXTRA_LONGITUDE = "extra_longitude"
        const val EXTRA_ACCURACY = "extra_accuracy"
        const val EXTRA_TIMESTAMP = "extra_timestamp"
        const val EXTRA_CLIENTS_COUNT = "extra_clients_count"
    }
    
    private lateinit var startServiceButton: Button
    private lateinit var stopServiceButton: Button
    private lateinit var statusTextView: TextView
    private lateinit var portTextView: TextView
    private lateinit var clientCountTextView: TextView
    private lateinit var adbCommandTextView: TextView
    private lateinit var gpsInfoTextView: TextView
    private lateinit var dataTransferTextView: TextView
    
    // 是否已经启动服务
    private var isServiceRunning = false
    
    // 数据格式化
    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    
    // 广播接收器 - 接收GPS数据更新
    private val gpsDataReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_GPS_DATA_UPDATED -> {
                    val latitude = intent.getDoubleExtra(EXTRA_LATITUDE, 0.0)
                    val longitude = intent.getDoubleExtra(EXTRA_LONGITUDE, 0.0)
                    val accuracy = intent.getFloatExtra(EXTRA_ACCURACY, 0f)
                    val timestamp = intent.getLongExtra(EXTRA_TIMESTAMP, 0L)
                    
                    val time = dateFormat.format(Date(timestamp))
                    gpsInfoTextView.text = "GPS信息：\n" +
                            "纬度：$latitude\n" +
                            "经度：$longitude\n" +
                            "精度：${accuracy}米\n" +
                            "时间：$time"
                }
                ACTION_DATA_SENT -> {
                    val clientsCount = intent.getIntExtra(EXTRA_CLIENTS_COUNT, 0)
                    if (clientsCount > 0) {
                        dataTransferTextView.text = "✓ 数据已成功传输到电脑(${clientsCount}个客户端)"
                        clientCountTextView.text = "客户端：$clientsCount"
                    } else {
                        dataTransferTextView.text = "! 等待电脑连接..."
                        clientCountTextView.text = "客户端：0"
                    }
                }
            }
        }
    }
    
    // 位置权限请求
    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val backgroundLocationGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissions[Manifest.permission.ACCESS_BACKGROUND_LOCATION] ?: false
        } else {
            true
        }
        val notificationPermissionGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions[Manifest.permission.POST_NOTIFICATIONS] ?: false
        } else {
            true
        }
        
        if (fineLocationGranted && backgroundLocationGranted && notificationPermissionGranted) {
            startGpsService()
        } else {
            Toast.makeText(this, "需要位置权限才能运行此应用", Toast.LENGTH_LONG).show()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // 初始化视图
        initViews()
        
        // 设置按钮点击监听器
        setupClickListeners()
        
        // 初始状态更新
        updateServiceStatus(false)
        
        // 注册广播接收器
        val intentFilter = IntentFilter().apply {
            addAction(ACTION_GPS_DATA_UPDATED)
            addAction(ACTION_DATA_SENT)
        }
        LocalBroadcastManager.getInstance(this).registerReceiver(gpsDataReceiver, intentFilter)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // 注销广播接收器
        LocalBroadcastManager.getInstance(this).unregisterReceiver(gpsDataReceiver)
    }
    
    /**
     * 初始化视图引用
     */
    private fun initViews() {
        startServiceButton = findViewById(R.id.startServiceButton)
        stopServiceButton = findViewById(R.id.stopServiceButton)
        statusTextView = findViewById(R.id.statusTextView)
        portTextView = findViewById(R.id.portTextView)
        clientCountTextView = findViewById(R.id.clientCountTextView)
        adbCommandTextView = findViewById(R.id.adbCommandTextView)
        gpsInfoTextView = findViewById(R.id.gpsInfoTextView)
        dataTransferTextView = findViewById(R.id.dataTransferTextView)
        
        // 设置ADB命令提示
        adbCommandTextView.text = "请在PC上运行：adb forward tcp:$PC_PORT tcp:$DEVICE_PORT"
    }
    
    /**
     * 设置按钮点击监听器
     */
    private fun setupClickListeners() {
        startServiceButton.setOnClickListener {
            if (!isServiceRunning) {
                checkAndRequestPermissions()
            }
        }
        
        stopServiceButton.setOnClickListener {
            if (isServiceRunning) {
                stopGpsService()
            }
        }
    }
    
    /**
     * 检查并请求必要的权限
     */
    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.INTERNET
        )
        
        // Android 10+需要后台位置权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissionsToRequest.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
        
        // Android 13+需要通知权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        
        if (permissionsToRequest.all { permission ->
                ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
            }) {
            // 如果所有权限都已授予，则启动服务
            startGpsService()
        } else {
            // 请求权限
            locationPermissionRequest.launch(permissionsToRequest.toTypedArray())
        }
    }
    
    /**
     * 启动GPS服务
     */
    private fun startGpsService() {
        val serviceIntent = Intent(this, UsbGpsService::class.java).apply {
            action = UsbGpsService.ACTION_START_SERVICE
        }
        
        ContextCompat.startForegroundService(this, serviceIntent)
        updateServiceStatus(true)
    }
    
    /**
     * 停止GPS服务
     */
    private fun stopGpsService() {
        val serviceIntent = Intent(this, UsbGpsService::class.java).apply {
            action = UsbGpsService.ACTION_STOP_SERVICE
        }
        
        startService(serviceIntent)
        updateServiceStatus(false)
    }
    
    /**
     * 更新服务状态UI
     */
    private fun updateServiceStatus(running: Boolean) {
        isServiceRunning = running
        
        statusTextView.text = if (running) "状态：运行中" else "状态：已停止"
        portTextView.text = "端口：设备 $DEVICE_PORT / PC $PC_PORT"
        clientCountTextView.text = "客户端：${if (running) "..." else "0"}"
        
        // 重置GPS和传输信息
        if (!running) {
            gpsInfoTextView.text = "GPS信息：等待服务启动..."
            dataTransferTextView.text = "数据传输：服务未启动"
        } else {
            gpsInfoTextView.text = "GPS信息：正在获取..."
            dataTransferTextView.text = "等待GPS数据和电脑连接..."
        }
        
        startServiceButton.isEnabled = !running
        stopServiceButton.isEnabled = running
    }
    
    // 注意：此处可以添加ViewModel + LiveData/StateFlow以实现与服务的状态同步
    // 例如：
    // private val viewModel: GpsServiceViewModel by viewModels()
    // 监听viewModel中的状态数据以更新UI
}