package com.example.gps_usb

/**
 * 数据类，表示要通过TCP发送的GPS数据
 * 
 * @property latitude 纬度
 * @property longitude 经度
 * @property latitudeDirection 纬度方向 ("N"北纬或"S"南纬)
 * @property longitudeDirection 经度方向 ("E"东经或"W"西经)
 * @property timestamp 时间戳（毫秒级，从1970年1月1日开始）
 * @property accuracy 精度（米）
 * @property speed 速度（米/秒，可选）
 * @property altitude 海拔（米，可选）
 */
data class GpsData(
    val latitude: Double,
    val longitude: Double,
    val latitudeDirection: String,
    val longitudeDirection: String,
    val timestamp: Long,
    val accuracy: Float,
    val speed: Float? = null,
    val altitude: Double? = null
) 