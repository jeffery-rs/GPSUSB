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

/**
 * 主活动，用于控制GPS USB服务并显示状态
 */
class MainActivity : ComponentActivity() {
    
    private lateinit var startServiceButton: Button
    private lateinit var stopServiceButton: Button
    private lateinit var statusTextView: TextView
    private lateinit var portTextView: TextView
    private lateinit var clientCountTextView: TextView
    private lateinit var adbCommandTextView: TextView
    
    // 是否已经启动服务
    private var isServiceRunning = false
    
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
        
        // 设置ADB命令提示
        adbCommandTextView.text = "请在PC上运行：adb forward tcp:12345 tcp:12345"
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
        portTextView.text = "端口：12345"
        clientCountTextView.text = "客户端：${if (running) "..." else "0"}"
        
        startServiceButton.isEnabled = !running
        stopServiceButton.isEnabled = running
    }
    
    // 注意：此处可以添加ViewModel + LiveData/StateFlow以实现与服务的状态同步
    // 例如：
    // private val viewModel: GpsServiceViewModel by viewModels()
    // 监听viewModel中的状态数据以更新UI
}