package com.checkmymega.check_my_mega

import android.app.AppOpsManager
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.TrafficStats
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.telephony.TelephonyManager
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {
    companion object {
        private const val CHANNEL = "com.checkmymega/network_stats"
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL)
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "hasUsageStatsPermission" -> {
                        result.success(hasUsageStatsPermission())
                    }
                    "openUsageStatsSettings" -> {
                        openUsageStatsSettings()
                        result.success(true)
                    }
                    "getMobileCountersSnapshot" -> {
                        val startTimeMs = call.argument<Number>("startTimeMs")?.toLong() ?: 0L
                        val endTimeMs = call.argument<Number>("endTimeMs")?.toLong()
                            ?: System.currentTimeMillis()
                        result.success(getMobileCountersSnapshot(startTimeMs, endTimeMs))
                    }
                    "getPerAppMobileUsage" -> {
                        val startTimeMs = call.argument<Number>("startTimeMs")?.toLong() ?: 0L
                        val endTimeMs = call.argument<Number>("endTimeMs")?.toLong()
                            ?: System.currentTimeMillis()
                        result.success(getPerAppMobileUsage(startTimeMs, endTimeMs))
                    }
                    else -> result.notImplemented()
                }
            }
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun openUsageStatsSettings() {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
    }

    private fun getMobileCountersSnapshot(startTimeMs: Long, endTimeMs: Long): Map<String, Any> {
        val rawRx = TrafficStats.getMobileRxBytes().coerceAtLeast(0L)
        val rawTx = TrafficStats.getMobileTxBytes().coerceAtLeast(0L)

        var bucketRx = 0L
        var bucketTx = 0L
        if (hasUsageStatsPermission() && startTimeMs > 0L) {
            try {
                val netStatsManager =
                    getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager
                @Suppress("DEPRECATION")
                val bucket = netStatsManager.querySummaryForDevice(
                    ConnectivityManager.TYPE_MOBILE,
                    null,
                    startTimeMs,
                    endTimeMs
                )
                if (bucket != null) {
                    bucketRx = bucket.rxBytes
                    bucketTx = bucket.txBytes
                }
            } catch (_: Exception) {
                // Fallback to TrafficStats hardware interface counters
            }
        }

        val telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        val operatorName = telephonyManager?.networkOperatorName?.takeIf { it.isNotBlank() }
            ?: "Réseau Mobile Cellulaire"

        return mapOf(
            "trafficRxBytes" to rawRx,
            "trafficTxBytes" to rawTx,
            "bucketRxBytes" to bucketRx,
            "bucketTxBytes" to bucketTx,
            "operatorName" to operatorName,
            "hasPermission" to hasUsageStatsPermission(),
            "timestampMs" to System.currentTimeMillis()
        )
    }

    private fun getPerAppMobileUsage(
        startTimeMs: Long,
        endTimeMs: Long
    ): List<Map<String, Any>> {
        if (!hasUsageStatsPermission() || startTimeMs <= 0L) {
            return emptyList()
        }

        val usageByUid = mutableMapOf<Int, Pair<Long, Long>>()
        try {
            val netStatsManager =
                getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager
            @Suppress("DEPRECATION")
            val stats = netStatsManager.querySummary(
                ConnectivityManager.TYPE_MOBILE,
                null,
                startTimeMs,
                endTimeMs
            )
            val bucket = NetworkStats.Bucket()
            while (stats.hasNextBucket()) {
                stats.getNextBucket(bucket)
                val uid = bucket.uid
                val current = usageByUid[uid] ?: Pair(0L, 0L)
                usageByUid[uid] = Pair(
                    current.first + bucket.rxBytes,
                    current.second + bucket.txBytes
                )
            }
            stats.close()
        } catch (_: Exception) {
            return emptyList()
        }

        val pm = packageManager
        return usageByUid.entries
            .filter { (it.value.first + it.value.second) > 0L }
            .map { (uid, bytes) ->
                val packages = pm.getPackagesForUid(uid)
                val pkgName = packages?.firstOrNull() ?: "android.uid.$uid"
                val appLabel = try {
                    val appInfo = pm.getApplicationInfo(pkgName, 0)
                    pm.getApplicationLabel(appInfo).toString()
                } catch (_: Exception) {
                    when (uid) {
                        NetworkStats.Bucket.UID_REMOVED -> "Apps Désinstallées"
                        NetworkStats.Bucket.UID_TETHERING -> "Partage de Connexion (Hotspot)"
                        Process.SYSTEM_UID -> "Système Android (OS / DNS)"
                        else -> pkgName
                    }
                }

                mapOf(
                    "uid" to uid,
                    "packageName" to pkgName,
                    "appName" to appLabel,
                    "rxBytes" to bytes.first,
                    "txBytes" to bytes.second,
                    "totalBytes" to (bytes.first + bytes.second)
                )
            }
            .sortedByDescending { it["totalBytes"] as Long }
    }
}
