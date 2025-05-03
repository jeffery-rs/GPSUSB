package com.example.gps_usb

/**
 * 数据类，表示要通过TCP发送的GPS数据
 * 
 * @property latitude 纬度
 * @property longitude 经度
 * @property timestamp 时间戳（毫秒级，从1970年1月1日开始）
 * @property accuracy 精度（米）
 * @property speed 速度（米/秒，可选）
 * @property altitude 海拔（米，可选）
 */
data class GpsData(
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long,
    val accuracy: Float,
    val speed: Float? = null,
    val altitude: Double? = null
) 