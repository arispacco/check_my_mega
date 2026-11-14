package com.checkmymega.check_my_mega

import android.Manifest
import android.app.AppOpsManager
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.TrafficStats
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.provider.Telephony
import android.telephony.CellIdentityLte
import android.telephony.CellInfo
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellInfoWcdma
import android.telephony.CellSignalStrengthLte
import android.telephony.CellSignalStrengthNr
import android.telephony.SignalStrength
import android.telephony.SubscriptionManager
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

class NativeHardwareEngine(
    private val context: Context,
    private val onDataChangedOnUi: () -> Unit
) : SensorEventListener, LocationListener {

    private val prefs = context.getSharedPreferences("checkmymega_native_prefs", Context.MODE_PRIVATE)

    val settings = AppSettings()
    val state = LiveRadioAndSensorState()
    val simSlots = CopyOnWriteArrayList<SimSlotInfo>()
    val smsRecords = CopyOnWriteArrayList<SmsBundleRecord>()
    val userSessions = CopyOnWriteArrayList<BundleAuditSession>()
    val radarSpots = CopyOnWriteArrayList<HouseRadarSpot>()
    val customRooms = CopyOnWriteArrayList<String>()

    /** Recent walked positions (x, y in metres) drawn as breadcrumbs on the compass. */
    val trail = CopyOnWriteArrayList<DoubleArray>()

    /**
     * Dead-reckoning coordinate frame of the current session. Spots saved in a previous session
     * live in another frame and are only navigable after [recalibrateAtSpot] adopts their frame.
     */
    @Volatile var currentFrameId: Long = System.currentTimeMillis()
    @Volatile var navTargetSpotId: String? = null
    @Volatile private var navArrivedFlag = false

    @Volatile var activeSession: BundleAuditSession? = null
    @Volatile var selectedReportSession: BundleAuditSession? = null
    @Volatile var statusBanner: String = "Moteur 100% Kotlin Natif 120 Hz actif"

    private val mainHandler = Handler(Looper.getMainLooper())
    private val bgExecutor: ScheduledExecutorService = Executors.newScheduledThreadPool(2)

    private var sensorManager: SensorManager? = null
    private var telephonyManager: TelephonyManager? = null
    private var locationManager: LocationManager? = null
    private var telephonyCallbackObj: Any? = null
    private var smsReceiver: BroadcastReceiver? = null
    private var isPaused = false

    // Sensor fusion & ZUPT state
    private var hasRotationVector = false
    private val accelValues = FloatArray(3)
    private val magnetValues = FloatArray(3)
    private var hasAccel = false
    private var hasMagnet = false
    private var baselinePressureHpa: Float = 0f
    private var hasLinearAccel = false
    private var lastMotionNs: Long = 0L
    private var gpsOriginLat: Double = 0.0
    private var gpsOriginLon: Double = 0.0

    // Passive throughput tracking
    private var lastSpeedSampleMs: Long = 0L
    private var lastSpeedRxBytes: Long = 0L
    private var lastSpeedTxBytes: Long = 0L

    // Live TrafficStats session anchor & optional manual bundle tracking
    @Volatile var manualPromisedMb: Double = 0.0
    @Volatile var manualPriceAmount: Int = 0
    @Volatile var manualStartMs: Long = 0L
    private var anchorStartMs: Long = 0L
    private var anchorRxBytes: Long = -1L
    private var anchorTxBytes: Long = -1L
    private var anchorBootEpochMs: Long = 0L

    init {
        loadPersistedState()
    }

    // =========================================================================
    // 0. PERSISTENCE (SHAREDPREFERENCES JSON)
    // =========================================================================
    private fun loadPersistedState() {
        settings.isDarkMode = prefs.getBoolean("isDarkMode", false)
        settings.forceCellularSpeedtest = prefs.getBoolean("forceCellularSpeedtest", true)
        settings.selectedSimSubId = prefs.getInt("selectedSimSubId", -1)
        settings.aiProvider = prefs.getString("aiProvider", "google_gemma") ?: "google_gemma"
        val savedModel = prefs.getString("aiModelName", "gemma-4-27b-it") ?: "gemma-4-27b-it"
        settings.aiModelName = if (savedModel == "gemma-3-27b-it") "gemma-4-27b-it" else savedModel
        settings.aiApiKey = prefs.getString("aiApiKey", "") ?: ""
        settings.ollamaBaseUrl = prefs.getString("ollamaBaseUrl", "http://127.0.0.1:11434") ?: "http://127.0.0.1:11434"
        state.lastGemmaReport = prefs.getString("lastGemmaReport", "") ?: ""

        manualPromisedMb = prefs.getFloat("manualPromisedMb", 0f).toDouble()
        manualPriceAmount = prefs.getInt("manualPriceAmount", 0)
        manualStartMs = prefs.getLong("manualStartMs", 0L)
        anchorStartMs = prefs.getLong("anchorStartMs", 0L)
        anchorRxBytes = prefs.getLong("anchorRxBytes", -1L)
        anchorTxBytes = prefs.getLong("anchorTxBytes", -1L)
        anchorBootEpochMs = prefs.getLong("anchorBootEpochMs", 0L)

        val defaultRooms = listOf("Salon", "Près Fenêtre", "Chambre", "Balcon / Véranda", "Bureau", "Cuisine")
        val roomsJson = prefs.getString("customRoomsJson", null)
        customRooms.clear()
        if (!roomsJson.isNullOrBlank()) {
            try {
                val arr = JSONArray(roomsJson)
                for (i in 0 until arr.length()) {
                    val r = arr.optString(i).trim()
                    if (r.isNotEmpty() && !customRooms.contains(r)) customRooms.add(r)
                }
            } catch (_: Exception) {}
        }
        if (customRooms.isEmpty()) {
            customRooms.addAll(defaultRooms)
        }

        val spotsJson = prefs.getString("radarSpotsJson", null)
        radarSpots.clear()
        if (!spotsJson.isNullOrBlank()) {
            try {
                val arr = JSONArray(spotsJson)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    radarSpots.add(
                        HouseRadarSpot(
                            id = o.optString("id", "SPOT-$i"),
                            roomLabel = o.optString("roomLabel", "Salon"),
                            rsrpDbm = o.optInt("rsrpDbm", -95),
                            rsrqDb = o.optInt("rsrqDb", -10),
                            sinrDb = o.optInt("sinrDb", 5),
                            cqi = o.optInt("cqi", 8),
                            speedMbps = o.optDouble("speedMbps", 5.0),
                            pingMs = o.optInt("pingMs", 50),
                            azimuthDeg = o.optDouble("azimuthDeg", 0.0),
                            posX = o.optDouble("posX", 0.0),
                            posY = o.optDouble("posY", 0.0),
                            altitudeZ = o.optDouble("altitudeZ", 0.0),
                            bandLabel = o.optString("bandLabel", "4G LTE"),
                            isPureRfMeasurement = o.optBoolean("isPureRfMeasurement", true),
                            timestampMs = o.optLong("timestampMs", System.currentTimeMillis()),
                            frameId = o.optLong("frameId", 0L)
                        )
                    )
                }
            } catch (_: Exception) {}
        }
    }

    fun saveSettings() {
        prefs.edit()
            .putBoolean("isDarkMode", settings.isDarkMode)
            .putBoolean("forceCellularSpeedtest", settings.forceCellularSpeedtest)
            .putInt("selectedSimSubId", settings.selectedSimSubId)
            .putString("aiProvider", settings.aiProvider)
            .putString("aiModelName", settings.aiModelName)
            .putString("aiApiKey", settings.aiApiKey)
            .putString("ollamaBaseUrl", settings.ollamaBaseUrl)
            .putString("lastGemmaReport", state.lastGemmaReport)
            .apply()
    }

    fun addCustomRoom(roomName: String) {
        val clean = roomName.trim()
        if (clean.isEmpty()) return
        if (!customRooms.contains(clean)) {
            customRooms.add(0, clean)
            val arr = JSONArray()
            customRooms.forEach { arr.put(it) }
            prefs.edit().putString("customRoomsJson", arr.toString()).apply()
        }
    }

    private fun saveRadarSpotsToDisk() {
        try {
            val arr = JSONArray()
            for (s in radarSpots) {
                val o = JSONObject().apply {
                    put("id", s.id)
                    put("roomLabel", s.roomLabel)
                    put("rsrpDbm", s.rsrpDbm)
                    put("rsrqDb", s.rsrqDb)
                    put("sinrDb", s.sinrDb)
                    put("cqi", s.cqi)
                    put("speedMbps", s.speedMbps)
                    put("pingMs", s.pingMs)
                    put("azimuthDeg", s.azimuthDeg)
                    put("posX", s.posX)
                    put("posY", s.posY)
                    put("altitudeZ", s.altitudeZ)
                    put("bandLabel", s.bandLabel)
                    put("isPureRfMeasurement", s.isPureRfMeasurement)
                    put("timestampMs", s.timestampMs)
                    put("frameId", s.frameId)
                }
                arr.put(o)
            }
            prefs.edit().putString("radarSpotsJson", arr.toString()).apply()
        } catch (_: Exception) {}
    }

    // =========================================================================
    // 1. LIFECYCLE & ENGINE START / PAUSE / RESUME / STOP
    // =========================================================================
    fun start() {
        refreshAvailableSimSlots()
        setupSensors()
        setupLocation()
        setupRealtimeTelephony()
        registerSmsReceiver()

        bgExecutor.scheduleWithFixedDelay({
            if (!isPaused) {
                try {
                    forceModemCellInfoRefresh()
                    samplePassiveThroughput()
                } catch (_: Exception) {}
            }
        }, 100L, 750L, TimeUnit.MILLISECONDS)

        bgExecutor.scheduleWithFixedDelay({
            if (!isPaused) {
                try {
                    refreshActiveSessionCounters()
                } catch (_: Exception) {}
            }
        }, 300L, 2500L, TimeUnit.MILLISECONDS)

        bgExecutor.execute {
            syncSmsAndBuildSessions()
        }
    }

    fun onPauseLifecycle() {
        isPaused = true
        try {
            sensorManager?.unregisterListener(this)
        } catch (_: Exception) {}
        try {
            locationManager?.removeUpdates(this)
        } catch (_: Exception) {}
    }

    fun onResumeLifecycle() {
        if (isPaused) {
            isPaused = false
            setupSensors()
            setupLocation()
            bgExecutor.execute { forceModemCellInfoRefresh() }
        }
    }

    /**
     * Re-binds all hardware/SMS/NetworkStats listeners immediately after the user returns
     * from Android system settings or grants runtime permissions.
     */
    fun onPermissionsUpdated() {
        refreshAvailableSimSlots()
        setupSensors()
        setupLocation()
        setupRealtimeTelephony()
        registerSmsReceiver()
        bgExecutor.execute {
            forceModemCellInfoRefresh()
            syncSmsAndBuildSessions()
            refreshActiveSessionCounters()
        }
    }

    fun stop() {
        onPauseLifecycle()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && telephonyCallbackObj != null) {
                telephonyManager?.unregisterTelephonyCallback(telephonyCallbackObj as TelephonyCallback)
            }
        } catch (_: Exception) {}
        try {
            if (smsReceiver != null) {
                context.unregisterReceiver(smsReceiver)
                smsReceiver = null
            }
        } catch (_: Exception) {}
        bgExecutor.shutdownNow()
    }

    private fun notifyUi() {
        mainHandler.post { onDataChangedOnUi() }
    }

    // =========================================================================
    // 2. 120Hz 3D SENSORS WITH ZUPT (ZERO-VELOCITY UPDATE) & BAROMETER
    // =========================================================================
    private fun setupSensors() {
        try {
            sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            sensorManager?.let { sm ->
                val rotVec = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
                if (rotVec != null) {
                    hasRotationVector = true
                    sm.registerListener(this, rotVec, SensorManager.SENSOR_DELAY_FASTEST)
                } else {
                    sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
                        sm.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST)
                    }
                    sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)?.let {
                        sm.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST)
                    }
                }
                sm.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)?.let {
                    sm.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST)
                }
                sm.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)?.let {
                    hasLinearAccel = true
                    lastMotionNs = SystemClock.elapsedRealtimeNanos()
                    sm.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
                }
                sm.getDefaultSensor(Sensor.TYPE_PRESSURE)?.let {
                    sm.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
                }
            }
        } catch (_: Exception) {}
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> {
                val rotMatrix = FloatArray(9)
                SensorManager.getRotationMatrixFromVector(rotMatrix, event.values)

                val rawOrient = FloatArray(3)
                SensorManager.getOrientation(rotMatrix, rawOrient)
                state.pitchDeg = Math.toDegrees(rawOrient[1].toDouble())
                state.rollDeg = Math.toDegrees(rawOrient[2].toDouble())

                val targetDeg = extractContinuousAzimuthDeg(rotMatrix)
                state.azimuthDeg = smoothAngle(state.azimuthDeg, targetDeg, 0.45)
            }

            Sensor.TYPE_ACCELEROMETER -> {
                if (!hasRotationVector) {
                    System.arraycopy(event.values, 0, accelValues, 0, 3)
                    hasAccel = true
                    updateFallbackOrientation()
                }
            }

            Sensor.TYPE_MAGNETIC_FIELD -> {
                if (!hasRotationVector) {
                    System.arraycopy(event.values, 0, magnetValues, 0, 3)
                    hasMagnet = true
                    updateFallbackOrientation()
                }
            }

            Sensor.TYPE_STEP_DETECTOR -> {
                // ZUPT gate: a step reported while the phone shows no motion at all is a false
                // positive (table vibration, hand tremor), so the position must not move.
                val stationary = hasLinearAccel && (event.timestamp - lastMotionNs) > STATIONARY_GATE_NS
                if (!stationary) {
                    state.stepCount += 1
                    val rad = Math.toRadians(state.azimuthDeg)
                    state.posX += NAV_STRIDE_METERS * sin(rad)
                    state.posY += NAV_STRIDE_METERS * cos(rad)
                    trail.add(doubleArrayOf(state.posX, state.posY))
                    while (trail.size > TRAIL_MAX_POINTS) trail.removeAt(0)
                }
            }

            Sensor.TYPE_LINEAR_ACCELERATION -> {
                // Only used as a motion detector. Double-integrating this sensor drifts by metres
                // within seconds, and the step detector already feeds the position (no double count).
                val ax = event.values[0].toDouble()
                val ay = event.values[1].toDouble()
                val az = event.values[2].toDouble()
                if (sqrt(ax * ax + ay * ay + az * az) > MOTION_THRESHOLD_MS2) {
                    lastMotionNs = event.timestamp
                }
            }

            Sensor.TYPE_PRESSURE -> {
                val hpa = event.values[0]
                if (hpa in 800f..1100f) {
                    state.pressureHpa = hpa
                    if (baselinePressureHpa == 0f) {
                        baselinePressureHpa = hpa
                    }
                    val relAlt = 44330.0 * (1.0 - (hpa / baselinePressureHpa).toDouble().pow(0.190295))
                    state.relativeAltitudeMeters = state.relativeAltitudeMeters * 0.7 + relAlt * 0.3
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun updateFallbackOrientation() {
        if (!hasAccel || !hasMagnet) return
        val r = FloatArray(9)
        val i = FloatArray(9)
        if (SensorManager.getRotationMatrix(r, i, accelValues, magnetValues)) {
            val deg = extractContinuousAzimuthDeg(r)
            state.azimuthDeg = smoothAngle(state.azimuthDeg, deg, 0.38)
        }
    }

    /**
     * Computes horizontal azimuth (0..360 deg) directly from the 3D rotation matrix without
     * Euler gimbal lock or a discontinuous pitch threshold when tilting between portrait and landscape.
     */
    private fun extractContinuousAzimuthDeg(rotMatrix: FloatArray): Double {
        val yE = rotMatrix[1].toDouble()
        val yN = rotMatrix[4].toDouble()
        val lenY = hypot(yE, yN)

        // Blend smoothly toward the camera-normal horizontal vector only near plumb vertical.
        val sign = if (rotMatrix[7] >= 0f) -1.0 else 1.0
        val zE = sign * rotMatrix[2].toDouble()
        val zN = sign * rotMatrix[5].toDouble()
        val lenZ = hypot(zE, zN)

        val u = ((0.35 - lenY) / 0.25).coerceIn(0.0, 1.0)
        val wVert = u * u * (3.0 - 2.0 * u)

        val uyE = if (lenY > 1e-4) yE / lenY else 0.0
        val uyN = if (lenY > 1e-4) yN / lenY else 0.0
        val uzE = if (lenZ > 1e-4) zE / lenZ else uyE
        val uzN = if (lenZ > 1e-4) zN / lenZ else uyN

        val east = (1.0 - wVert) * uyE + wVert * uzE
        val north = (1.0 - wVert) * uyN + wVert * uzN
        return (Math.toDegrees(atan2(east, north)) + 360.0) % 360.0
    }

    private fun smoothAngle(current: Double, target: Double, alpha: Double): Double {
        var diff = target - current
        if (diff > 180.0) diff -= 360.0
        if (diff < -180.0) diff += 360.0
        return (current + alpha * diff + 360.0) % 360.0
    }

    private fun setupLocation() {
        val hasFine = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!hasFine && !hasCoarse) return

        try {
            locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            locationManager?.let { lm ->
                for (p in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
                    if (lm.isProviderEnabled(p)) {
                        lm.requestLocationUpdates(p, 1500L, 0.3f, this, Looper.getMainLooper())
                        lm.getLastKnownLocation(p)?.let { onLocationChanged(it) }
                    }
                }
            }
        } catch (_: Exception) {}
    }

    override fun onLocationChanged(location: Location) {
        state.gpsLat = location.latitude
        state.gpsLon = location.longitude
        state.gpsAccuracyMeters = location.accuracy
        state.hasGpsFix = true
        if (gpsOriginLat == 0.0 && gpsOriginLon == 0.0) {
            gpsOriginLat = location.latitude
            gpsOriginLon = location.longitude
        } else if (location.accuracy <= 6.0f) {
            val dLat = Math.toRadians(location.latitude - gpsOriginLat)
            val dLon = Math.toRadians(location.longitude - gpsOriginLon)
            val earthR = 6378137.0
            val gpsX = dLon * earthR * cos(Math.toRadians(gpsOriginLat))
            val gpsY = dLat * earthR
            state.posX = state.posX * 0.8 + gpsX * 0.2
            state.posY = state.posY * 0.8 + gpsY * 0.2
        }
    }

    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}
    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

    // =========================================================================
    // 3. UNIVERSAL MULTI-COUNTRY & DUAL-SIM TELEPHONY (SUBSCRIPTIONMANAGER)
    // =========================================================================
    fun refreshAvailableSimSlots() {
        simSlots.clear()
        val hasPhonePerm = context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
        if (!hasPhonePerm) return

        try {
            val sm = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
            val defaultDataSubId = SubscriptionManager.getDefaultDataSubscriptionId()
            val activeSubs = sm?.activeSubscriptionInfoList.orEmpty()
            for (sub in activeSubs) {
                val mcc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) (sub.mccString ?: "") else sub.mcc.toString()
                val mnc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) (sub.mncString ?: "") else sub.mnc.toString()
                simSlots.add(
                    SimSlotInfo(
                        subId = sub.subscriptionId,
                        slotIndex = sub.simSlotIndex,
                        displayName = sub.displayName?.toString() ?: "SIM ${sub.simSlotIndex + 1}",
                        carrierName = sub.carrierName?.toString() ?: "",
                        mccMnc = "$mcc$mnc",
                        countryIso = sub.countryIso ?: "cm",
                        isDefaultData = sub.subscriptionId == defaultDataSubId
                    )
                )
            }
        } catch (_: Exception) {}
    }

    fun selectSimSubscription(subId: Int) {
        settings.selectedSimSubId = subId
        saveSettings()
        setupRealtimeTelephony()
        bgExecutor.execute {
            forceModemCellInfoRefresh()
            syncSmsAndBuildSessions()
        }
    }

    private fun resolveActiveTelephonyManager(): TelephonyManager? {
        val baseTm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager ?: return null
        val targetSubId = if (settings.selectedSimSubId != -1) {
            settings.selectedSimSubId
        } else {
            SubscriptionManager.getDefaultDataSubscriptionId()
        }
        return if (targetSubId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            try {
                baseTm.createForSubscriptionId(targetSubId)
            } catch (_: Exception) {
                baseTm
            }
        } else {
            baseTm
        }
    }

    private fun setupRealtimeTelephony() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && telephonyCallbackObj != null) {
                telephonyManager?.unregisterTelephonyCallback(telephonyCallbackObj as TelephonyCallback)
            }
        } catch (_: Exception) {}

        val tm = resolveActiveTelephonyManager() ?: return
        telephonyManager = tm

        forceModemCellInfoRefresh()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val callback = object : TelephonyCallback(),
                    TelephonyCallback.SignalStrengthsListener,
                    TelephonyCallback.CellInfoListener {

                    override fun onSignalStrengthsChanged(signalStrength: SignalStrength) {
                        extractSignalStrengthImmediately(signalStrength)
                        notifyUi()
                    }

                    override fun onCellInfoChanged(cellInfo: MutableList<CellInfo>) {
                        parseCellInfoList(cellInfo)
                        notifyUi()
                    }
                }
                telephonyCallbackObj = callback
                tm.registerTelephonyCallback(bgExecutor, callback)
            } catch (_: Exception) {}
        }
    }

    private fun forceModemCellInfoRefresh() {
        val tm = telephonyManager ?: resolveActiveTelephonyManager() ?: return
        telephonyManager = tm

        // Check physical SIM state honestly (no fake fallback if absent!)
        val simState = try { tm.simState } catch (_: Exception) { TelephonyManager.SIM_STATE_UNKNOWN }
        if (simState == TelephonyManager.SIM_STATE_ABSENT) {
            state.hasSimCard = false
            state.operatorName = "Aucune carte SIM détectée"
            state.simOperatorCode = ""
            notifyUi()
            return
        }
        state.hasSimCard = true

        val simOp = tm.simOperator?.takeIf { it.isNotBlank() }
            ?: tm.networkOperator?.takeIf { it.isNotBlank() } ?: ""
        val simName = tm.simOperatorName?.takeIf { it.isNotBlank() } ?: ""
        val netName = tm.networkOperatorName?.takeIf { it.isNotBlank() } ?: ""
        val countryIso = tm.simCountryIso?.takeIf { it.isNotBlank() }
            ?: tm.networkCountryIso?.takeIf { it.isNotBlank() } ?: "cm"

        state.simOperatorCode = simOp
        state.simCountryIso = countryIso.lowercase()
        state.currencyLabel = IspBrand.resolveCurrency(countryIso)
        state.operatorName = when {
            netName.isNotBlank() && simName.isNotBlank() && !netName.equals(simName, ignoreCase = true) ->
                "$netName ($simName)"
            netName.isNotBlank() -> netName
            simName.isNotBlank() -> simName
            simOp == "62405" -> "CAMTEL / Blue"
            simOp == "62401" -> "MTN Cameroon"
            simOp == "62402" -> "Orange Cameroun"
            simOp.isNotBlank() -> "SIM ($simOp)"
            else -> "Opérateur SIM Actif"
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            tm.signalStrength?.let { extractSignalStrengthImmediately(it) }
        }

        val hasLocPerm = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!hasLocPerm) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                tm.requestCellInfoUpdate(bgExecutor, object : TelephonyManager.CellInfoCallback() {
                    override fun onCellInfo(cellInfo: MutableList<CellInfo>) {
                        parseCellInfoList(cellInfo)
                        notifyUi()
                    }
                })
                return
            } catch (_: Exception) {}
        }

        try {
            val cells = tm.allCellInfo
            if (!cells.isNullOrEmpty()) {
                parseCellInfoList(cells)
                notifyUi()
            }
        } catch (_: Exception) {}
    }

    private fun extractSignalStrengthImmediately(ss: SignalStrength) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            for (css in ss.cellSignalStrengths) {
                if (css is CellSignalStrengthLte) {
                    if (css.rsrp != CellInfo.UNAVAILABLE && css.rsrp in -140..-40) {
                        state.rsrpDbm = css.rsrp
                        state.pushRsrpSample(css.rsrp)
                    } else if (css.dbm in -140..-40) {
                        state.rsrpDbm = css.dbm
                        state.pushRsrpSample(css.dbm)
                    }
                    if (css.rsrq != CellInfo.UNAVAILABLE && css.rsrq in -30..0) state.rsrqDb = css.rsrq
                    if (css.rssnr != CellInfo.UNAVAILABLE && css.rssnr in -20..40) state.sinrDb = css.rssnr
                    if (css.rssi != CellInfo.UNAVAILABLE && css.rssi in -120..-30) state.rssiDbm = css.rssi
                    if (css.cqi != CellInfo.UNAVAILABLE && css.cqi in 0..15) state.cqi = css.cqi
                    state.asuLevel = css.asuLevel
                    if (css.timingAdvance != CellInfo.UNAVAILABLE && css.timingAdvance in 0..1282) {
                        state.timingAdvance = css.timingAdvance
                        state.estimatedDistanceMeters = (css.timingAdvance * 78.125).roundToInt()
                    }
                    state.lastRfUpdateMs = System.currentTimeMillis()
                    return
                } else if (css is CellSignalStrengthNr) {
                    if (css.ssRsrp != CellInfo.UNAVAILABLE && css.ssRsrp in -140..-40) {
                        state.rsrpDbm = css.ssRsrp
                        state.pushRsrpSample(css.ssRsrp)
                    }
                    if (css.ssRsrq != CellInfo.UNAVAILABLE) state.rsrqDb = css.ssRsrq
                    if (css.ssSinr != CellInfo.UNAVAILABLE) state.sinrDb = css.ssSinr
                    state.lastRfUpdateMs = System.currentTimeMillis()
                    return
                }
            }
        }
    }

    private fun parseCellInfoList(cells: List<CellInfo>) {
        if (cells.isEmpty()) return
        var neighbors = 0
        for (cell in cells) {
            if (!cell.isRegistered) neighbors++
        }
        state.neighborCellsCount = neighbors

        val primary = cells.firstOrNull { it.isRegistered } ?: cells.first()
        when (primary) {
            is CellInfoLte -> {
                val ss = primary.cellSignalStrength
                val id = primary.cellIdentity
                state.networkType = "4G LTE"
                if (ss.rsrp != CellInfo.UNAVAILABLE && ss.rsrp in -140..-40) {
                    state.rsrpDbm = ss.rsrp
                    state.pushRsrpSample(ss.rsrp)
                } else if (ss.dbm in -140..-40) {
                    state.rsrpDbm = ss.dbm
                    state.pushRsrpSample(ss.dbm)
                }
                if (ss.rsrq != CellInfo.UNAVAILABLE && ss.rsrq in -30..0) state.rsrqDb = ss.rsrq
                if (ss.rssnr != CellInfo.UNAVAILABLE && ss.rssnr in -20..40) state.sinrDb = ss.rssnr
                if (ss.cqi != CellInfo.UNAVAILABLE && ss.cqi in 0..15) state.cqi = ss.cqi
                state.asuLevel = ss.asuLevel
                if (ss.timingAdvance != CellInfo.UNAVAILABLE && ss.timingAdvance in 0..1282) {
                    state.timingAdvance = ss.timingAdvance
                    state.estimatedDistanceMeters = (ss.timingAdvance * 78.125).roundToInt()
                }
                if (id.ci != CellInfo.UNAVAILABLE) state.cellId = id.ci.toLong()
                if (id.pci != CellInfo.UNAVAILABLE) state.pci = id.pci
                if (id.earfcn != CellInfo.UNAVAILABLE) {
                    state.earfcn = id.earfcn
                    state.bandLabel = resolveLteBand(id)
                }
            }

            is CellInfoNr -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val ss = primary.cellSignalStrength as? CellSignalStrengthNr
                    state.networkType = "5G NR"
                    state.bandLabel = "5G NR"
                    ss?.let {
                        if (it.ssRsrp != CellInfo.UNAVAILABLE && it.ssRsrp in -140..-40) {
                            state.rsrpDbm = it.ssRsrp
                            state.pushRsrpSample(it.ssRsrp)
                        }
                        if (it.ssRsrq != CellInfo.UNAVAILABLE) state.rsrqDb = it.ssRsrq
                        if (it.ssSinr != CellInfo.UNAVAILABLE) state.sinrDb = it.ssSinr
                    }
                }
            }

            is CellInfoWcdma -> {
                val ss = primary.cellSignalStrength
                state.networkType = "3G HSPA+"
                state.bandLabel = "Bande 1 (2100 MHz)"
                if (ss.dbm in -120..-40) {
                    state.rsrpDbm = ss.dbm
                    state.pushRsrpSample(ss.dbm)
                }
            }
        }
        state.lastRfUpdateMs = System.currentTimeMillis()
    }

    private fun resolveLteBand(id: CellIdentityLte): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && id.bands.isNotEmpty()) {
            val b = id.bands.first()
            return when (b) {
                3 -> "Bande 3 FDD (1800 MHz)"
                7 -> "Bande 7 FDD (2600 MHz)"
                20 -> "Bande 20 FDD (800 MHz)"
                28 -> "Bande 28 FDD (700 MHz)"
                40 -> "Bande 40 TDD (2300 MHz)"
                41 -> "Bande 41 TDD (2500 MHz)"
                else -> "Bande LTE B$b"
            }
        }
        val e = id.earfcn
        return when (e) {
            in 1200..1949 -> "Bande 3 FDD (1800 MHz)"
            in 2750..3449 -> "Bande 7 FDD (2600 MHz)"
            in 6150..6449 -> "Bande 20 FDD (800 MHz)"
            in 9210..9659 -> "Bande 28 FDD (700 MHz)"
            in 38650..39649 -> "Bande 40 TDD (2300 MHz)"
            else -> "4G LTE (EARFCN $e)"
        }
    }

    private fun samplePassiveThroughput() {
        val now = SystemClock.elapsedRealtime()
        val rx = TrafficStats.getMobileRxBytes()
        val tx = TrafficStats.getMobileTxBytes()
        if (lastSpeedSampleMs > 0L && now > lastSpeedSampleMs && rx >= lastSpeedRxBytes) {
            val dtSec = (now - lastSpeedSampleMs) / 1000.0
            state.passiveRxMbps = ((rx - lastSpeedRxBytes) * 8.0) / (dtSec * 1_000_000.0)
            state.passiveTxMbps = ((tx - lastSpeedTxBytes) * 8.0) / (dtSec * 1_000_000.0)
        }
        lastSpeedSampleMs = now
        lastSpeedRxBytes = rx
        lastSpeedTxBytes = tx
    }

    // =========================================================================
    // 4. FORCED CELLULAR (TRANSPORT_CELLULAR) SPEED PROBE & PERSISTENT SPOTS
    // =========================================================================
    fun recordInstantSpot(roomLabel: String) {
        val sinrFactor = ((state.sinrDb + 5.0) / 25.0).coerceIn(0.08, 1.0)
        val rsrpFactor = ((state.rsrpDbm + 120.0) / 45.0).coerceIn(0.08, 1.0)
        val cqiFactor = (state.cqi / 15.0).coerceIn(0.15, 1.0)
        val rfEstimatedCapacityMbps = (45.0 * sinrFactor * 0.5 + 35.0 * rsrpFactor * 0.3 + 30.0 * cqiFactor * 0.2).coerceIn(0.5, 65.0)

        val spot = HouseRadarSpot(
            id = "SPOT-${System.currentTimeMillis()}",
            roomLabel = roomLabel,
            rsrpDbm = state.rsrpDbm,
            rsrqDb = state.rsrqDb,
            sinrDb = state.sinrDb,
            cqi = state.cqi,
            speedMbps = rfEstimatedCapacityMbps,
            pingMs = if (state.sinrDb >= 6) 44 else 78,
            azimuthDeg = state.azimuthDeg,
            posX = state.posX,
            posY = state.posY,
            altitudeZ = state.relativeAltitudeMeters,
            bandLabel = state.bandLabel,
            isPureRfMeasurement = true,
            timestampMs = System.currentTimeMillis(),
            frameId = currentFrameId
        )
        radarSpots.add(0, spot)
        saveRadarSpotsToDisk()
        state.lastSpeedTestTransportLabel = "SIM 4G (Signal physique 0 Mo)"
        statusBanner = "Coin « $roomLabel » enregistré sur le signal physique SIM (${spot.rsrpDbm} dBm, SINR ${spot.sinrDb} dB)"
        notifyUi()
    }

    /**
     * Requests a dedicated TRANSPORT_CELLULAR Network handle from Android so that even when
     * Wi-Fi is connected, the speedtest socket binds strictly to the SIM cellular interface!
     */
    private fun acquireCellularNetwork(timeoutMs: Long = 3000L): Network? {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return null
        // Check if activeNetwork is already cellular
        val active = cm.activeNetwork
        val activeCaps = if (active != null) cm.getNetworkCapabilities(active) else null
        if (active != null && activeCaps != null &&
            activeCaps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) &&
            !activeCaps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        ) {
            return active
        }

        val ref = AtomicReference<Network?>(null)
        val latch = CountDownLatch(1)
        val req = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                ref.set(network)
                latch.countDown()
            }

            override fun onUnavailable() {
                latch.countDown()
            }
        }
        return try {
            cm.requestNetwork(req, cb, mainHandler, timeoutMs.toInt())
            latch.await(timeoutMs, TimeUnit.MILLISECONDS)
            val net = ref.get()
            try { cm.unregisterNetworkCallback(cb) } catch (_: Exception) {}
            net
        } catch (_: Exception) {
            try { cm.unregisterNetworkCallback(cb) } catch (_: Exception) {}
            null
        }
    }

    fun runActiveSpeedProbeAndRecord(roomLabel: String) {
        if (state.isRunningSpeedTest) return
        state.isRunningSpeedTest = true
        statusBanner = "Test de débit 4G sur « $roomLabel » en cours..."
        notifyUi()

        bgExecutor.execute {
            var pingMs = 68
            var measuredMbps = 0.0
            var usedPureRfFallback = false

            try {
                val cellNet = if (settings.forceCellularSpeedtest) acquireCellularNetwork(2800L) else null
                if (settings.forceCellularSpeedtest && cellNet == null) {
                    // No active data bundle or mobile data disabled -> do NOT cheat with Wi-Fi!
                    throw IllegalStateException("Interface data SIM inactive (aucun forfait actif)")
                }

                val openConn: (String) -> HttpURLConnection = { urlStr ->
                    val u = URL(urlStr)
                    if (cellNet != null) {
                        cellNet.openConnection(u) as HttpURLConnection
                    } else {
                        u.openConnection() as HttpURLConnection
                    }
                }

                val pingStart = SystemClock.elapsedRealtime()
                val pingConn = openConn("https://speed.cloudflare.com/__down?bytes=128").apply {
                    connectTimeout = 3200
                    readTimeout = 3200
                    useCaches = false
                }
                pingConn.inputStream.use { it.readBytes() }
                pingMs = (SystemClock.elapsedRealtime() - pingStart).toInt().coerceIn(15, 999)
                pingConn.disconnect()

                val rxBefore = TrafficStats.getMobileRxBytes()
                val dlStart = SystemClock.elapsedRealtime()
                val dlConn = openConn("https://speed.cloudflare.com/__down?bytes=1500000").apply {
                    connectTimeout = 4500
                    readTimeout = 5000
                    useCaches = false
                }
                var streamBytes = 0L
                dlConn.inputStream.use { input ->
                    val buf = ByteArray(16384)
                    while (true) {
                        val r = input.read(buf)
                        if (r <= 0) break
                        streamBytes += r
                        if (SystemClock.elapsedRealtime() - dlStart > 3600L) break
                    }
                }
                dlConn.disconnect()
                val elapsedSec = ((SystemClock.elapsedRealtime() - dlStart) / 1000.0).coerceAtLeast(0.15)
                val mobileDelta = TrafficStats.getMobileRxBytes() - rxBefore

                if (settings.forceCellularSpeedtest && mobileDelta <= 1024L && streamBytes <= 1024L) {
                    throw IllegalStateException("Aucun trafic cellulaire reçu (forfait épuisé)")
                }
                val verifiedBytes = if (mobileDelta > 0L) mobileDelta else streamBytes
                measuredMbps = ((verifiedBytes * 8.0) / (elapsedSec * 1_000_000.0)).coerceIn(0.2, 180.0)
                state.lastSpeedTestTransportLabel = if (cellNet != null) "Socket 4G SIM Direct" else "Réseau Actif"
            } catch (_: Exception) {
                // Honest RF capacity calculation when the SIM has 0 FCFA / no active bundle
                usedPureRfFallback = true
                val sinrFactor = ((state.sinrDb + 5.0) / 25.0).coerceIn(0.1, 1.0)
                val rsrpFactor = ((state.rsrpDbm + 120.0) / 45.0).coerceIn(0.1, 1.0)
                val cqiFactor = (state.cqi / 15.0).coerceIn(0.15, 1.0)
                measuredMbps = (42.0 * sinrFactor * 0.5 + 32.0 * rsrpFactor * 0.3 + 26.0 * cqiFactor * 0.2).coerceIn(0.8, 58.0)
                state.lastSpeedTestTransportLabel = "Capacité RF SIM (Sans forfait actif)"
            } finally {
                state.lastActivePingMs = pingMs
                state.lastActiveSpeedMbps = measuredMbps
                state.isRunningSpeedTest = false

                val spot = HouseRadarSpot(
                    id = "SPOT-${System.currentTimeMillis()}",
                    roomLabel = roomLabel,
                    rsrpDbm = state.rsrpDbm,
                    rsrqDb = state.rsrqDb,
                    sinrDb = state.sinrDb,
                    cqi = state.cqi,
                    speedMbps = measuredMbps,
                    pingMs = pingMs,
                    azimuthDeg = state.azimuthDeg,
                    posX = state.posX,
                    posY = state.posY,
                    altitudeZ = state.relativeAltitudeMeters,
                    bandLabel = state.bandLabel,
                    isPureRfMeasurement = usedPureRfFallback,
                    timestampMs = System.currentTimeMillis(),
                    frameId = currentFrameId
                )
                radarSpots.add(0, spot)
                saveRadarSpotsToDisk()
                statusBanner = if (usedPureRfFallback) {
                    "Aucun forfait data actif sur la SIM — « $roomLabel » classé sur le signal 4G pur (${state.rsrpDbm} dBm, ~${String.format("%.1f", measuredMbps)} Mbps)"
                } else {
                    "Débit SIM 4G mesuré dans « $roomLabel » : ${String.format("%.2f", measuredMbps)} Mbps (Ping $pingMs ms)"
                }
                notifyUi()
            }
        }
    }

    fun clearRadarSpots() {
        radarSpots.clear()
        saveRadarSpotsToDisk()
        state.stepCount = 0
        state.posX = 0.0
        state.posY = 0.0
        trail.clear()
        currentFrameId = System.currentTimeMillis()
        navTargetSpotId = null
        navArrivedFlag = false
        baselinePressureHpa = state.pressureHpa
        state.relativeAltitudeMeters = 0.0
        gpsOriginLat = state.gpsLat
        gpsOriginLon = state.gpsLon
        statusBanner = "Coins de la maison et origine 3D réinitialisés."
        notifyUi()
    }

    fun bestSpot(): HouseRadarSpot? {
        if (radarSpots.isEmpty()) return null
        return radarSpots.maxByOrNull { it.compositeScore }
    }

    /** Spots recorded in the current dead-reckoning frame, i.e. the ones the compass can guide to. */
    fun navigableSpots(): List<HouseRadarSpot> = radarSpots.filter { it.frameId == currentFrameId }

    /** The spot the compass guides to: the user's choice, else the best-scoring navigable spot. */
    fun navTargetSpot(): HouseRadarSpot? {
        val candidates = navigableSpots()
        if (candidates.isEmpty()) return null
        return candidates.firstOrNull { it.id == navTargetSpotId }
            ?: candidates.maxByOrNull { it.compositeScore }
    }

    fun setNavTarget(spotId: String?) {
        navTargetSpotId = spotId
        navArrivedFlag = false
        notifyUi()
    }

    /** Live guidance (distance, bearing, turn angle, arrival) toward [navTargetSpot], or null. */
    fun navSnapshot(): NavSnapshot? {
        val spot = navTargetSpot() ?: return null
        val dx = spot.posX - state.posX
        val dy = spot.posY - state.posY
        val dist = sqrt(dx * dx + dy * dy)
        navArrivedFlag = if (navArrivedFlag) dist <= NAV_LEAVE_RADIUS_M else dist <= NAV_ARRIVAL_RADIUS_M
        val bearing = if (dist > 0.05) {
            (Math.toDegrees(atan2(dx, dy)) + 360.0) % 360.0
        } else {
            spot.azimuthDeg
        }
        val rel = ((bearing - state.azimuthDeg + 540.0) % 360.0) - 180.0
        return NavSnapshot(spot, dist, bearing, rel, navArrivedFlag)
    }

    /**
     * Drift correction: the user stands on a saved spot and says so. The dead-reckoned position
     * snaps to that spot and the session adopts its coordinate frame, which is also how spots
     * saved in a previous session become navigable again.
     */
    fun recalibrateAtSpot(spotId: String) {
        val spot = radarSpots.firstOrNull { it.id == spotId } ?: return
        currentFrameId = spot.frameId
        state.posX = spot.posX
        state.posY = spot.posY
        if (state.hasGpsFix) {
            // Keep the GPS blend consistent with the snapped position.
            val earthR = 6378137.0
            gpsOriginLat = state.gpsLat - Math.toDegrees(spot.posY / earthR)
            gpsOriginLon = state.gpsLon - Math.toDegrees(spot.posX / (earthR * cos(Math.toRadians(gpsOriginLat))))
        }
        trail.clear()
        navArrivedFlag = false
        statusBanner = "Position recalée sur « ${spot.roomLabel} »"
        notifyUi()
    }

    // =========================================================================
    // 5. REAL SMS INBOX & NETWORKSTATS MANAGER (ZERO INVENTED BUNDLES!)
    // =========================================================================
    private fun registerSmsReceiver() {
        if (smsReceiver != null) return
        smsReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
                    bgExecutor.execute { syncSmsAndBuildSessions() }
                }
            }
        }
        try {
            val filter = IntentFilter(Telephony.Sms.Intents.SMS_RECEIVED_ACTION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(smsReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(smsReceiver, filter)
            }
        } catch (_: Exception) {}
    }

    private fun saveLiveTrafficAnchor(sessionStartMs: Long) {
        val nowMs = System.currentTimeMillis()
        val rx = TrafficStats.getMobileRxBytes().coerceAtLeast(0L)
        val tx = TrafficStats.getMobileTxBytes().coerceAtLeast(0L)
        val bootEpoch = nowMs - SystemClock.elapsedRealtime()
        anchorStartMs = sessionStartMs
        anchorRxBytes = rx
        anchorTxBytes = tx
        anchorBootEpochMs = bootEpoch
        prefs.edit()
            .putLong("anchorStartMs", sessionStartMs)
            .putLong("anchorRxBytes", rx)
            .putLong("anchorTxBytes", tx)
            .putLong("anchorBootEpochMs", bootEpoch)
            .apply()
    }

    fun setManualBundle(promisedMb: Double, priceAmount: Int = 0) {
        val nowMs = System.currentTimeMillis()
        manualPromisedMb = promisedMb.coerceAtLeast(0.0)
        manualPriceAmount = priceAmount.coerceAtLeast(0)
        manualStartMs = nowMs
        prefs.edit()
            .putFloat("manualPromisedMb", manualPromisedMb.toFloat())
            .putInt("manualPriceAmount", manualPriceAmount)
            .putLong("manualStartMs", manualStartMs)
            .apply()
        saveLiveTrafficAnchor(nowMs)
        bgExecutor.execute { syncSmsAndBuildSessions() }
    }

    fun clearManualBundle() {
        manualPromisedMb = 0.0
        manualPriceAmount = 0
        manualStartMs = 0L
        anchorStartMs = 0L
        anchorRxBytes = -1L
        anchorTxBytes = -1L
        anchorBootEpochMs = 0L
        prefs.edit()
            .remove("manualPromisedMb")
            .remove("manualPriceAmount")
            .remove("manualStartMs")
            .remove("anchorStartMs")
            .remove("anchorRxBytes")
            .remove("anchorTxBytes")
            .remove("anchorBootEpochMs")
            .apply()
        bgExecutor.execute { syncSmsAndBuildSessions() }
    }

    fun syncSmsAndBuildSessions() {
        val parsedSms = readOperatorSmsFromInbox(80)
        smsRecords.clear()
        smsRecords.addAll(parsedSms)

        val nowMs = System.currentTimeMillis()
        val window24hStart = nowMs - 24L * 3600_000L

        val recentActivations = parsedSms.filter { it.isActivation && it.dateMs >= window24hStart }
            .sortedBy { it.dateMs }
        val recentExhaustions = parsedSms.filter { it.isExhaustion && it.dateMs >= window24hStart }
            .sortedBy { it.dateMs }

        val lastExhaustMs = recentExhaustions.lastOrNull()?.dateMs ?: 0L
        val unexhaustedActivations = if (lastExhaustMs > 0L) {
            recentActivations.filter { it.dateMs > lastExhaustMs }
        } else {
            recentActivations
        }

        val hasNewerSmsThanManual = unexhaustedActivations.any { it.dateMs > manualStartMs }
        val useManual = manualPromisedMb > 0.0 && manualStartMs > 0L && !hasNewerSmsThanManual

        val builtSessions = mutableListOf<BundleAuditSession>()
        val activeIsp = IspBrand.fromText("${state.simOperatorCode} ${state.operatorName}")
        val timeFmt = SimpleDateFormat("HH:mm", Locale.FRANCE)

        when {
            useManual -> {
                val (rx, tx, apps, hasPreBucket, usedLiveAnchor) =
                    measureSessionUsage(manualStartMs, nowMs, allowLiveAnchor = true, allowAppQuery = true)
                val anchorNote = when {
                    usedLiveAnchor -> "Suivi exact à 0 Mo depuis ${timeFmt.format(manualStartMs)}"
                    hasPreBucket -> "Depuis ${timeFmt.format(manualStartMs)} (inclut tranche OS 2h)"
                    else -> "Forfait manuel depuis ${timeFmt.format(manualStartMs)}"
                }
                builtSessions.add(
                    BundleAuditSession(
                        id = "MANUAL-$manualStartMs",
                        isp = activeIsp,
                        carrierDisplayName = state.operatorName,
                        planName = "Forfait Manuel (${manualPromisedMb.roundToInt()} Mo)",
                        promisedMb = manualPromisedMb,
                        priceAmount = manualPriceAmount,
                        currencyLabel = state.currencyLabel,
                        validityHours = 24,
                        startTimeMs = manualStartMs,
                        endTimeMs = nowMs,
                        rxBytes = rx,
                        txBytes = tx,
                        isExhausted = false,
                        workloadTag = anchorNote,
                        appUsages = apps,
                        isNeutralMonitor = false,
                        includesPreActivationBucket = hasPreBucket && !usedLiveAnchor,
                        isManualAnchor = true
                    )
                )
            }

            unexhaustedActivations.isNotEmpty() -> {
                val firstStart = unexhaustedActivations.first().dateMs
                val totalPromisedMb = unexhaustedActivations.sumOf { it.promisedMb }
                val totalPrice = unexhaustedActivations.sumOf { it.priceAmount }
                val isp = unexhaustedActivations.last().isp
                val currency = unexhaustedActivations.last().currencyLabel

                // Capture an exact byte baseline if this SMS just arrived while the app runs.
                if (nowMs - firstStart in 0L..120_000L && anchorStartMs != firstStart) {
                    saveLiveTrafficAnchor(firstStart)
                }

                val (rx, tx, apps, hasPreBucket, usedLiveAnchor) =
                    measureSessionUsage(firstStart, nowMs, allowLiveAnchor = true, allowAppQuery = true)
                val count = unexhaustedActivations.size
                val planTitle = if (count > 1) {
                    "Cumul $count Forfaits SMS (${totalPromisedMb.roundToInt()} Mo)"
                } else {
                    unexhaustedActivations.first().planName
                }
                val startClock = timeFmt.format(firstStart)
                val tag = when {
                    usedLiveAnchor -> "Suivi exact dès $startClock ($count SMS actif)"
                    hasPreBucket -> "Dès $startClock ($count SMS • tranche OS 2h)"
                    !hasUsageStatsPermission() -> "Suivi dès ouverture (Données d'usage requises)"
                    else -> "Détecté par SMS dès $startClock ($count actif)"
                }

                builtSessions.add(
                    BundleAuditSession(
                        id = "SMS-CUMUL-$firstStart",
                        isp = isp,
                        carrierDisplayName = state.operatorName,
                        planName = planTitle,
                        promisedMb = totalPromisedMb,
                        priceAmount = totalPrice,
                        currencyLabel = currency,
                        validityHours = 24,
                        startTimeMs = firstStart,
                        endTimeMs = nowMs,
                        rxBytes = rx,
                        txBytes = tx,
                        isExhausted = false,
                        workloadTag = tag,
                        appUsages = apps,
                        isNeutralMonitor = false,
                        includesPreActivationBucket = hasPreBucket && !usedLiveAnchor,
                        isManualAnchor = false
                    )
                )
            }

            recentActivations.isNotEmpty() && lastExhaustMs > recentActivations.last().dateMs -> {
                val prevExhaustMs = recentExhaustions.dropLast(1).lastOrNull()?.dateMs ?: 0L
                val exhaustedChain = recentActivations.filter { it.dateMs > prevExhaustMs }
                    .ifEmpty { listOf(recentActivations.last()) }
                val firstStart = exhaustedChain.first().dateMs
                val totalPromisedMb = exhaustedChain.sumOf { it.promisedMb }
                val totalPrice = exhaustedChain.sumOf { it.priceAmount }
                val isp = exhaustedChain.last().isp
                val currency = exhaustedChain.last().currencyLabel
                val (rx, tx, apps, hasPreBucket, _) =
                    measureSessionUsage(firstStart, lastExhaustMs, allowLiveAnchor = false, allowAppQuery = true)

                builtSessions.add(
                    BundleAuditSession(
                        id = "SMS-EXHAUST-$firstStart",
                        isp = isp,
                        carrierDisplayName = state.operatorName,
                        planName = "${exhaustedChain.last().planName} (Épuisé)",
                        promisedMb = totalPromisedMb,
                        priceAmount = totalPrice,
                        currencyLabel = currency,
                        validityHours = 24,
                        startTimeMs = firstStart,
                        endTimeMs = lastExhaustMs,
                        rxBytes = rx,
                        txBytes = tx,
                        isExhausted = true,
                        workloadTag = "Clôturé par SMS à ${timeFmt.format(lastExhaustMs)}",
                        appUsages = apps,
                        isNeutralMonitor = false,
                        includesPreActivationBucket = hasPreBucket,
                        isManualAnchor = false
                    )
                )
            }

            else -> {
                val (rx, tx, apps, hasPreBucket, usedLiveAnchor) =
                    measureSessionUsage(window24hStart, nowMs, allowLiveAnchor = false, allowAppQuery = true)
                builtSessions.add(
                    BundleAuditSession(
                        id = "NEUTRAL-24H",
                        isp = activeIsp,
                        carrierDisplayName = state.operatorName,
                        planName = "Surveillance SIM Directe (24h)",
                        promisedMb = 0.0,
                        priceAmount = 0,
                        currencyLabel = state.currencyLabel,
                        validityHours = 24,
                        startTimeMs = window24hStart,
                        endTimeMs = nowMs,
                        rxBytes = rx,
                        txBytes = tx,
                        isExhausted = false,
                        workloadTag = if (!hasUsageStatsPermission() || usedLiveAnchor) {
                            "Compteur OS depuis l'ouverture (Usage Access requis)"
                        } else {
                            "Aucun SMS d'activation récent • Compteur OS 24h"
                        },
                        appUsages = apps,
                        isNeutralMonitor = true,
                        includesPreActivationBucket = hasPreBucket,
                        isManualAnchor = false
                    )
                )
            }
        }

        userSessions.clear()
        userSessions.addAll(builtSessions)
        activeSession = builtSessions.firstOrNull()
        selectedReportSession = activeSession
        notifyUi()
    }

    fun refreshActiveSessionCounters() {
        val current = activeSession ?: return
        if (current.isExhausted) return
        val nowMs = System.currentTimeMillis()
        val allowAnchor = !current.isNeutralMonitor
        val (rx, tx, apps, hasPreBucket, usedLiveAnchor) =
            measureSessionUsage(current.startTimeMs, nowMs, allowLiveAnchor = allowAnchor, allowAppQuery = true)
        val updated = current.copy(
            carrierDisplayName = state.operatorName,
            endTimeMs = nowMs,
            rxBytes = rx,
            txBytes = tx,
            appUsages = apps,
            includesPreActivationBucket = hasPreBucket && !usedLiveAnchor
        )
        activeSession = updated
        if (userSessions.isNotEmpty()) userSessions[0] = updated
        selectedReportSession = updated
        notifyUi()
    }

    fun markActiveSessionExhausted() {
        val current = activeSession ?: return
        val finalized = current.copy(isExhausted = true)
        activeSession = finalized
        selectedReportSession = finalized
        if (userSessions.isNotEmpty()) userSessions[0] = finalized
        notifyUi()
    }

    // =========================================================================
    // 6. REAL GEMMA / GEMINI / LOCAL OLLAMA AI AUDIT ENGINE
    // =========================================================================
    fun runGemmaAiAudit() {
        if (state.isRunningAiAudit) return
        state.isRunningAiAudit = true
        notifyUi()

        bgExecutor.execute {
            val session = activeSession
            val best = bestSpot()
            val spotsSummary = if (radarSpots.isEmpty()) {
                "Aucun coin mesuré."
            } else {
                radarSpots.sortedByDescending { it.compositeScore }
                    .joinToString(" | ") { "${it.roomLabel}: ${it.rsrpDbm} dBm, SINR ${it.sinrDb} dB, ~${String.format("%.1f", it.speedMbps)} Mbps" }
            }
            val appsSummary = session?.appUsages?.take(5)?.joinToString(", ") {
                "${it.appName} (${String.format("%.1f", it.totalMbDecimal)} Mo)"
            } ?: "0 Mo"

            val prompt = """
Tu es l'ingénieur télécom et auditeur citoyen IA (modèle Gemma) intégré dans l'application Android native CheckMyMega.
Analyse ces mesures matérielles réelles issues du téléphone de l'utilisateur et donne un rapport structuré, clair et concret en français (en 3 courts paragraphes : 1. Qualité de couverture SIM & Meilleur coin de la maison, 2. Audit de consommation des mégaoctets et applications, 3. Trois conseils pratiques pour optimiser la connexion) :
- Opérateur SIM détecté : ${state.operatorName} (Code MCC/MNC : ${state.simOperatorCode}, Pays : ${state.simCountryIso.uppercase()})
- Réseau & Bande : ${state.networkType} • ${state.bandLabel} • Distance antenne : ~${state.estimatedDistanceMeters}m
- Signal actuel en direct : RSRP ${state.rsrpDbm} dBm (${state.signalQualityLabel}), SINR ${state.sinrDb} dB, RSRQ ${state.rsrqDb} dB, CQI ${state.cqi}/15
- Coins de la maison testés : $spotsSummary
- Meilleur coin identifié : ${best?.roomLabel ?: "Non mesuré"}
- Session Data Android : ${session?.planName ?: "Surveillance 24h"}, Volume reçu OS = ${String.format("%.2f", session?.measuredDecimalMb ?: 0.0)} Mo (Promis SMS = ${session?.promisedMb?.roundToInt() ?: 0} Mo)
- Consommation par application (NetworkStatsManager) : $appsSummary
""".trimIndent()

            var resultText = ""
            try {
                val apiKey = settings.aiApiKey.trim()
                when {
                    settings.aiProvider == "ollama_local" -> {
                        val url = URL("${settings.ollamaBaseUrl.trimEnd('/')}/api/generate")
                        val conn = (url.openConnection() as HttpURLConnection).apply {
                            requestMethod = "POST"
                            connectTimeout = 8000
                            readTimeout = 25000
                            doOutput = true
                            setRequestProperty("Content-Type", "application/json")
                        }
                        val reqBody = JSONObject().apply {
                            put("model", settings.aiModelName.ifBlank { "gemma4:e4b" })
                            put("prompt", prompt)
                            put("stream", false)
                        }
                        OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(reqBody.toString()) }
                        val respStr = conn.inputStream.bufferedReader().use { it.readText() }
                        resultText = JSONObject(respStr).optString("response", "").trim()
                    }

                    apiKey.isNotEmpty() && settings.aiProvider == "openrouter" -> {
                        val url = URL("https://openrouter.ai/api/v1/chat/completions")
                        val conn = (url.openConnection() as HttpURLConnection).apply {
                            requestMethod = "POST"
                            connectTimeout = 10000
                            readTimeout = 25000
                            doOutput = true
                            setRequestProperty("Content-Type", "application/json")
                            setRequestProperty("Authorization", "Bearer $apiKey")
                        }
                        val msgArr = JSONArray().put(JSONObject().put("role", "user").put("content", prompt))
                        val reqBody = JSONObject().apply {
                            put("model", settings.aiModelName.ifBlank { "google/gemma-4-27b-it" })
                            put("messages", msgArr)
                        }
                        OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(reqBody.toString()) }
                        val respStr = conn.inputStream.bufferedReader().use { it.readText() }
                        val choices = JSONObject(respStr).optJSONArray("choices")
                        if (choices != null && choices.length() > 0) {
                            resultText = choices.getJSONObject(0).getJSONObject("message").optString("content", "").trim()
                        }
                    }

                    apiKey.isNotEmpty() -> {
                        // Google AI Studio API (Gemma 4 primary with automatic fallback if endpoint requires gemma-3 slug)
                        val requestedModel = settings.aiModelName.ifBlank { "gemma-4-27b-it" }
                        val candidateModels = listOf(requestedModel, requestedModel.replace("gemma-4-", "gemma-3-"), "gemini-2.5-flash").distinct()
                        for (modelId in candidateModels) {
                            try {
                                val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$modelId:generateContent?key=$apiKey")
                                val conn = (url.openConnection() as HttpURLConnection).apply {
                                    requestMethod = "POST"
                                    connectTimeout = 10000
                                    readTimeout = 25000
                                    doOutput = true
                                    setRequestProperty("Content-Type", "application/json")
                                }
                                val parts = JSONArray().put(JSONObject().put("text", prompt))
                                val contents = JSONArray().put(JSONObject().put("parts", parts))
                                val reqBody = JSONObject().put("contents", contents)
                                OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(reqBody.toString()) }
                                val respStr = conn.inputStream.bufferedReader().use { it.readText() }
                                val cands = JSONObject(respStr).optJSONArray("candidates")
                                if (cands != null && cands.length() > 0) {
                                    val pArr = cands.getJSONObject(0).getJSONObject("content").optJSONArray("parts")
                                    if (pArr != null && pArr.length() > 0) {
                                        resultText = pArr.getJSONObject(0).optString("text", "").trim()
                                        if (resultText.isNotBlank()) break
                                    }
                                }
                            } catch (_: Exception) {
                                // Try next candidate model slug
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                resultText = "Erreur lors de l'appel API (${e.javaClass.simpleName}) — Vérifiez votre clé API Gemma 4 ou votre connexion dans l'onglet Paramètres.\n\n"
            }

            if (resultText.isBlank()) {
                resultText = buildLocalDeterministicReport(session, best, spotsSummary, appsSummary)
            }

            state.lastGemmaReport = resultText
            state.isRunningAiAudit = false
            saveSettings()
            notifyUi()
        }
    }

    private fun buildLocalDeterministicReport(
        session: BundleAuditSession?,
        best: HouseRadarSpot?,
        spotsSummary: String,
        appsSummary: String
    ): String {
        val covVerdict = when {
            state.rsrpDbm >= -85 -> "Excellente couverture 4G (${state.rsrpDbm} dBm, SINR ${state.sinrDb} dB) sur ${state.operatorName}."
            state.rsrpDbm >= -98 -> "Couverture 4G correcte (${state.rsrpDbm} dBm, SINR ${state.sinrDb} dB) sur ${state.operatorName}, mais sensible aux murs intérieurs."
            else -> "Signal 4G faible à l'intérieur (${state.rsrpDbm} dBm) sur ${state.operatorName} : forte atténuation par le béton."
        }
        val spotAdvice = if (best != null) {
            "Le meilleur point de réception mesuré dans votre maison est « ${best.roomLabel} » (${best.rsrpDbm} dBm, ~${String.format("%.1f", best.speedMbps)} Mbps). Posez-y votre téléphone en mode Partage de connexion (Hotspot Wi-Fi) lors de vos téléchargements lourds."
        } else {
            "Rendez-vous dans l'onglet « Boussole 120Hz » et capturez 2 ou 3 pièces pour localiser le point exact où l'antenne (${state.bandLabel}) passe le mieux."
        }
        return buildString {
            appendLine("1. Couverture & Radio : $covVerdict")
            appendLine()
            appendLine("2. Recommandation Maison : $spotAdvice")
            appendLine()
            appendLine("3. Audit Consommation OS : ${session?.buildCitizenSummary() ?: ""} Applications principales : $appsSummary.")
            if (settings.aiApiKey.isBlank() && settings.aiProvider != "ollama_local") {
                appendLine()
                appendLine("[Astuce : Ajoutez votre clé API Google AI Studio (Gemma 4 27B) dans l'onglet Paramètres pour activer la génération LLM en direct.]")
            }
        }
    }

    // =========================================================================
    // 7. OS NETWORKSTATS & UNIVERSAL SMS PARSER
    // =========================================================================
    fun hasUsageStatsPermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private data class SessionUsageResult(
        val rxBytes: Long,
        val txBytes: Long,
        val appUsages: List<AppUidUsage>,
        val includesPreActivationBucket: Boolean,
        val usedLiveAnchor: Boolean
    )

    private fun resolveActiveSubscriberIdOrNull(): String? {
        return try {
            @Suppress("DEPRECATION", "HardwareIds", "MissingPermission")
            telephonyManager?.subscriberId?.takeIf { it.isNotBlank() }
        } catch (_: Throwable) {
            null
        }
    }

    private fun measureSessionUsage(
        startTimeMs: Long,
        endTimeMs: Long,
        allowLiveAnchor: Boolean,
        allowAppQuery: Boolean
    ): SessionUsageResult {
        val nowMs = System.currentTimeMillis()
        val apps = if (allowAppQuery) queryPerAppUsage(startTimeMs, endTimeMs) else emptyList()

        // Use exact kernel byte delta whenever a same-boot live anchor is set for this window.
        if (allowLiveAnchor && anchorStartMs == startTimeMs && anchorRxBytes >= 0L && anchorTxBytes >= 0L) {
            val curBootEpoch = nowMs - SystemClock.elapsedRealtime()
            val curRx = TrafficStats.getMobileRxBytes().coerceAtLeast(0L)
            val curTx = TrafficStats.getMobileTxBytes().coerceAtLeast(0L)
            val sameBoot = abs(curBootEpoch - anchorBootEpochMs) < 15_000L
            if (sameBoot && curRx >= anchorRxBytes && curTx >= anchorTxBytes) {
                return SessionUsageResult(
                    rxBytes = curRx - anchorRxBytes,
                    txBytes = curTx - anchorTxBytes,
                    appUsages = apps,
                    includesPreActivationBucket = false,
                    usedLiveAnchor = true
                )
            }
        }

        if (hasUsageStatsPermission() && startTimeMs > 0L) {
            val subId = resolveActiveSubscriberIdOrNull()
            val (rx, tx, hasPre) = queryDeviceBytesWithBucketInfo(startTimeMs, endTimeMs, subId)
            if (rx >= 0L && tx >= 0L) {
                return SessionUsageResult(rx, tx, apps, hasPre, usedLiveAnchor = false)
            }
        }

        // Without UsageStats permission, avoid returning un-anchored boot totals; start a clean session anchor at 0.
        if (anchorStartMs != startTimeMs) {
            saveLiveTrafficAnchor(startTimeMs)
        }
        val curRx = TrafficStats.getMobileRxBytes().coerceAtLeast(0L)
        val curTx = TrafficStats.getMobileTxBytes().coerceAtLeast(0L)
        val dRx = if (anchorRxBytes in 0L..curRx) curRx - anchorRxBytes else 0L
        val dTx = if (anchorTxBytes in 0L..curTx) curTx - anchorTxBytes else 0L
        return SessionUsageResult(dRx, dTx, apps, includesPreActivationBucket = false, usedLiveAnchor = true)
    }

    private fun queryDeviceBytesWithBucketInfo(
        startTimeMs: Long,
        endTimeMs: Long,
        subId: String?
    ): Triple<Long, Long, Boolean> {
        val candidates = if (subId != null) listOf<String?>(subId, null) else listOf<String?>(null)
        for (candidateSubId in candidates) {
            try {
                val nsm = context.getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager
                @Suppress("DEPRECATION")
                val deviceBucket = nsm.querySummaryForDevice(ConnectivityManager.TYPE_MOBILE, candidateSubId, startTimeMs, endTimeMs)
                var hasPreBucket = false
                try {
                    @Suppress("DEPRECATION")
                    val stats = nsm.querySummary(ConnectivityManager.TYPE_MOBILE, candidateSubId, startTimeMs, endTimeMs)
                    val b = NetworkStats.Bucket()
                    while (stats.hasNextBucket()) {
                        stats.getNextBucket(b)
                        if ((b.rxBytes + b.txBytes) > 0L && b.startTimeStamp < startTimeMs) {
                            hasPreBucket = true
                            break
                        }
                    }
                    stats.close()
                } catch (_: Exception) {}

                if (deviceBucket != null && (deviceBucket.rxBytes + deviceBucket.txBytes) >= 0L) {
                    return Triple(deviceBucket.rxBytes, deviceBucket.txBytes, hasPreBucket)
                }
            } catch (_: Exception) {}
        }
        return Triple(-1L, -1L, false)
    }

    private fun queryPerAppUsage(startTimeMs: Long, endTimeMs: Long): List<AppUidUsage> {
        if (!hasUsageStatsPermission() || startTimeMs <= 0L) return emptyList()
        val map = mutableMapOf<Int, LongArray>()
        val subId = resolveActiveSubscriberIdOrNull()
        val candidates = if (subId != null) listOf<String?>(subId, null) else listOf<String?>(null)
        for (candidateSubId in candidates) {
            try {
                val nsm = context.getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager
                @Suppress("DEPRECATION")
                val stats = nsm.querySummary(ConnectivityManager.TYPE_MOBILE, candidateSubId, startTimeMs, endTimeMs)
                val bucket = NetworkStats.Bucket()
                while (stats.hasNextBucket()) {
                    stats.getNextBucket(bucket)
                    val uid = bucket.uid
                    val tot = bucket.rxBytes + bucket.txBytes
                    if (tot <= 0L) continue
                    val arr = map.getOrPut(uid) { LongArray(2) }
                    arr[0] += bucket.rxBytes
                    arr[1] += bucket.txBytes
                }
                stats.close()
                if (map.isNotEmpty()) break
            } catch (_: Exception) {}
        }

        val pm = context.packageManager
        val list = mutableListOf<AppUidUsage>()
        for ((uid, bytes) in map) {
            if (bytes[0] + bytes[1] < 10_000L) continue
            val (label, pkg) = resolveUidLabel(pm, uid)
            list.add(AppUidUsage(uid = uid, appName = label, packageName = pkg, rxBytes = bytes[0], txBytes = bytes[1]))
        }
        list.sortByDescending { it.totalBytes }
        return list.take(10)
    }

    private fun resolveUidLabel(pm: PackageManager, uid: Int): Pair<String, String> {
        if (uid == NetworkStats.Bucket.UID_REMOVED) return Pair("Applications désinstallées", "android.uid.removed")
        if (uid == NetworkStats.Bucket.UID_TETHERING || uid == -5) {
            return Pair("Partage de Connexion (Hotspot Wi-Fi)", "android.uid.tethering")
        }
        if (uid == Process.SYSTEM_UID || uid == 1000) return Pair("Système Android (OS)", "android.system")
        val pkgs = pm.getPackagesForUid(uid)
        if (!pkgs.isNullOrEmpty()) {
            val pkg = pkgs.first()
            return try {
                val info = pm.getApplicationInfo(pkg, 0)
                val label = pm.getApplicationLabel(info).toString()
                Pair(label.ifBlank { pkg }, pkg)
            } catch (_: Exception) {
                Pair(pkg, pkg)
            }
        }
        return Pair("Autres Apps & Hotspot (UID $uid)", "uid.$uid")
    }

    private fun readOperatorSmsFromInbox(limit: Int): List<SmsBundleRecord> {
        val hasPerm = context.checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
        if (!hasPerm) return emptyList()

        val list = mutableListOf<SmsBundleRecord>()
        try {
            val cursor = context.contentResolver.query(
                Uri.parse("content://sms/inbox"),
                arrayOf("_id", "address", "date", "body"),
                null,
                null,
                "date DESC LIMIT $limit"
            )
            cursor?.use { c ->
                val idIdx = c.getColumnIndex("_id")
                val addrIdx = c.getColumnIndex("address")
                val dateIdx = c.getColumnIndex("date")
                val bodyIdx = c.getColumnIndex("body")
                while (c.moveToNext()) {
                    val id = if (idIdx >= 0) c.getLong(idIdx) else 0L
                    val address = if (addrIdx >= 0) (c.getString(addrIdx) ?: "") else ""
                    val dateMs = if (dateIdx >= 0) c.getLong(dateIdx) else 0L
                    val body = if (bodyIdx >= 0) (c.getString(bodyIdx) ?: "") else ""
                    parseSmsRecord(id, address, dateMs, body)?.let { list.add(it) }
                }
            }
        } catch (_: Exception) {}
        return list
    }

    private fun parseSmsRecord(id: Long, address: String, dateMs: Long, body: String): SmsBundleRecord? {
        val upperAddr = address.uppercase()
        val upperBody = body.uppercase()

        // Universal operator & data bundle detection (Cameroon + Global)
        val hasDataVolume = Regex("""\b(\d+(?:[.,]\d+)?)\s*(MB|MO|GB|GO)\b""", RegexOption.IGNORE_CASE).containsMatchIn(body)
        val isKnownCarrier = upperAddr.contains("CAMTEL") || upperAddr.contains("BLUE") ||
            upperAddr.contains("MTN") || upperAddr.contains("ORANGE") ||
            upperAddr.contains("YOOMEE") || upperAddr.contains("AIRTEL") ||
            upperAddr.contains("FREE") || upperAddr.contains("VODAFONE") ||
            upperBody.contains("BLUE MO") || upperBody.contains("DATA_4G") ||
            (hasDataVolume && (upperBody.contains("FORFAIT") || upperBody.contains("BUNDLE") || upperBody.contains("INTERNET")))

        if (!isKnownCarrier) return null
        if (upperBody.contains("TOP-UP") || upperBody.contains("A ETE RECHARGE") ||
            upperBody.contains("NOT SUFFICIENT FOR THE CALL")
        ) {
            return null
        }

        val isp = IspBrand.fromText("$address $body ${state.operatorName}")
        val isExhaustion = upperBody.contains("DATA_4G BALANCE IS 0") ||
            upperBody.contains("BALANCE IS 0 NOW") ||
            upperBody.contains("EPUISE") || upperBody.contains("ÉPUISÉ") ||
            upperBody.contains("TERMINE") || upperBody.contains("TERMINÉ") ||
            upperBody.contains("0 MO RESTANT") || upperBody.contains("0 MB LEFT") ||
            upperBody.contains("EXPIRED") || upperBody.contains("EXPIRE") ||
            upperBody.contains("EXPIRÉ")

        val mbMatch = Regex("""(\d+(?:[.,]\d+)?)\s*(MB|MO|GB|GO)""", RegexOption.IGNORE_CASE).find(body)
        var promisedMb = 0.0
        if (mbMatch != null) {
            val rawVal = mbMatch.groupValues[1].replace(',', '.').toDoubleOrNull() ?: 0.0
            val unit = mbMatch.groupValues[2].uppercase()
            promisedMb = if (unit.startsWith("G")) rawVal * 1024.0 else rawVal
        }

        val priceMatch = Regex("""(\d+(?:[.,]\d+)?)\s*(FCFA|XAF|XOF|EUR|€|USD|\$|NGN)""", RegexOption.IGNORE_CASE).find(body)
        var priceAmount = 0
        var currencyLabel = state.currencyLabel
        if (priceMatch != null) {
            priceAmount = priceMatch.groupValues[1].replace(',', '.').toDoubleOrNull()?.roundToInt() ?: 0
            currencyLabel = priceMatch.groupValues[2].uppercase()
        }

        val isActivation = !isExhaustion && promisedMb > 0.0 && (
            upperBody.contains("FELICITATIONS") || upperBody.contains("FÉLICITATIONS") ||
                upperBody.contains("DROIT A") || upperBody.contains("ACTIV") ||
                upperBody.contains("SOUSCRIT") || upperBody.contains("SUBSCRIBED") ||
                upperBody.contains("VALIDE")
            )

        val planName = when {
            upperBody.contains("BLUE MO S") -> "Blue Mo S (${if (promisedMb > 0) "${promisedMb.roundToInt()} Mo" else "Info"})"
            promisedMb > 0 -> "Forfait ${promisedMb.roundToInt()} Mo"
            isExhaustion -> "Fin de forfait (0 Mo)"
            else -> "Notification ${address.ifBlank { isp.shortName }}"
        }

        return SmsBundleRecord(
            id = id,
            address = address,
            body = body,
            dateMs = dateMs,
            isp = isp,
            isActivation = isActivation,
            isExhaustion = isExhaustion,
            promisedMb = promisedMb,
            priceAmount = priceAmount,
            currencyLabel = currencyLabel,
            planName = planName
        )
    }
}
