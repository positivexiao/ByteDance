package com.bytedace.doubaoapp.data.benchmark

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Debug
import kotlin.math.max

object DeviceMetrics {

    fun collectDeviceInfo(context: Context): DeviceInfo {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        val abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty()
        return DeviceInfo(
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            androidVersion = Build.VERSION.RELEASE,
            sdkInt = Build.VERSION.SDK_INT,
            abi = abi,
            cpuCores = Runtime.getRuntime().availableProcessors(),
            totalRamMb = memInfo.totalMem / (1024 * 1024),
        )
    }

    fun currentPssMb(): Long {
        val info = Debug.MemoryInfo()
        Debug.getMemoryInfo(info)
        return info.totalPss / 1024L
    }

    class PeakTracker {
        private var peak = 0L

        fun sample() {
            peak = max(peak, currentPssMb())
        }

        fun peakMb(): Long = peak
    }
}

data class DeviceInfo(
    val manufacturer: String,
    val model: String,
    val androidVersion: String,
    val sdkInt: Int,
    val abi: String,
    val cpuCores: Int,
    val totalRamMb: Long,
) {
    fun displayName(): String = "$manufacturer $model"
}
