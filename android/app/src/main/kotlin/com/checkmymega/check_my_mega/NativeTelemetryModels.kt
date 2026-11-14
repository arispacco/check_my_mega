package com.checkmymega.check_my_mega

import android.graphics.Color
import java.util.Currency
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class IspBrand(
    val id: String,
    val displayName: String,
    val shortName: String,
    val logoKey: String,
    val colorInt: Int
) {
    CAMTEL("camtel", "Camtel / Blue", "Blue", "blue", Color.parseColor("#0033AB")),
    MTN("mtn", "MTN", "MTN", "mtn", Color.parseColor("#FFCC00")),
    ORANGE("orange", "Orange", "Orange", "orange", Color.parseColor("#FF7900")),
    YOOMEE("yoomee", "YooMee", "YooMee", "yoomee", Color.parseColor("#E30613")),
    GLOBAL_OTHER("global", "Opérateur SIM", "SIM", "sim", Color.parseColor("#0284C7"));

    companion object {
        fun fromText(text: String): IspBrand {
            val upper = text.uppercase()
            return when {
                upper.contains("62405") || upper.contains("CAMTEL") || upper.contains("BLUE") -> CAMTEL
                upper.contains("62401") || upper.contains("MTN") || upper.contains("WANDA") ||
                    upper.contains("62130") || upper.contains("61205") || upper.contains("62001") -> MTN
                upper.contains("62402") || upper.contains("ORANGE") || upper.contains("20801") ||
                    upper.contains("61203") || upper.contains("60801") -> ORANGE
                upper.contains("YOOMEE") || upper.contains("62403") -> YOOMEE
                else -> GLOBAL_OTHER
            }
        }

        fun resolveCurrency(countryIso: String): String {
            val iso = countryIso.trim().uppercase()
            if (iso in setOf("CM", "CF", "TD", "CG", "GQ", "GA", "SN", "CI", "BF", "ML", "NE", "TG", "BJ")) {
                return "FCFA"
            }
            return try {
                if (iso.length == 2) {
                    Currency.getInstance(Locale("", iso)).symbol
                } else {
                    "FCFA"
                }
            } catch (_: Exception) {
                "FCFA"
            }
        }
    }
}

data class SimSlotInfo(
    val subId: Int,
    val slotIndex: Int,
    val displayName: String,
    val carrierName: String,
    val mccMnc: String,
    val countryIso: String,
    val isDefaultData: Boolean
)

data class AppSettings(
    var isDarkMode: Boolean = false,
    var forceCellularSpeedtest: Boolean = true,
    var selectedSimSubId: Int = -1,
    var aiProvider: String = "google_gemma", // "google_gemma", "openrouter", "ollama_local"
    var aiModelName: String = "gemma-4-27b-it",
    var aiApiKey: String = "",
    var ollamaBaseUrl: String = "http://127.0.0.1:11434"
)

data class SmsBundleRecord(
    val id: Long,
    val address: String,
    val body: String,
    val dateMs: Long,
    val isp: IspBrand,
    val isActivation: Boolean,
    val isExhaustion: Boolean,
    val promisedMb: Double,
    val priceAmount: Int,
    val currencyLabel: String,
    val planName: String
)

data class AppUidUsage(
    val uid: Int,
    val appName: String,
    val packageName: String,
    val rxBytes: Long,
    val txBytes: Long
) {
    val totalBytes: Long get() = rxBytes + txBytes
    val totalMbDecimal: Double get() = totalBytes / 1_000_000.0
}

data class BundleAuditSession(
    val id: String,
    val isp: IspBrand,
    val carrierDisplayName: String,
    val planName: String,
    val promisedMb: Double,
    val priceAmount: Int,
    val currencyLabel: String,
    val validityHours: Int,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val rxBytes: Long,
    val txBytes: Long,
    val isExhausted: Boolean,
    val workloadTag: String,
    val appUsages: List<AppUidUsage>,
    val isNeutralMonitor: Boolean = false,
    val includesPreActivationBucket: Boolean = false,
    val isManualAnchor: Boolean = false
) {
    val totalBytes: Long get() = rxBytes + txBytes
    val measuredDecimalMb: Double get() = totalBytes / 1_000_000.0
    val measuredBinaryMib: Double get() = totalBytes / (1024.0 * 1024.0)

    val rawConsumedPct: Double
        get() {
            if (promisedMb <= 0.0) return 100.0
            return ((measuredDecimalMb / promisedMb) * 100.0).coerceAtLeast(0.0)
        }

    val veracityScorePct: Double
        get() = if (promisedMb <= 0.0) 100.0 else rawConsumedPct.coerceIn(0.0, 100.0)

    val missingMb: Double
        get() = if (promisedMb > 0.0) max(0.0, promisedMb - measuredDecimalMb) else 0.0

    val overrunMb: Double
        get() = if (promisedMb > 0.0) max(0.0, measuredDecimalMb - promisedMb) else 0.0

    val isOverrun: Boolean
        get() = promisedMb > 0.0 && measuredDecimalMb > promisedMb

    val realCostPerGb: Double
        get() {
            if (priceAmount <= 0) return 0.0
            val deliveredGb = measuredDecimalMb / 1000.0
            if (deliveredGb <= 0.001) return priceAmount.toDouble()
            return priceAmount / deliveredGb
        }

    fun buildCitizenSummary(): String {
        val recu = String.format("%.1f", measuredDecimalMb)
        val recuMib = String.format("%.1f", measuredBinaryMib)
        if (isNeutralMonitor || promisedMb <= 0.0) {
            return "Surveillance matérielle locale sur $carrierDisplayName : Android a mesuré $recu Mo décimaux ($recuMib Mio binaires) sur les dernières 24h (compteur OS local, non fourni par le FAI)."
        }
        val promis = promisedMb.roundToInt()
        val manque = missingMb.roundToInt()
        val vraiPrix = realCostPerGb.roundToInt()
        val pctTxt = String.format("%.1f", rawConsumedPct)
        val priceNote = if (priceAmount > 0) " • Coût réel : $vraiPrix $currencyLabel/Go" else ""
        return when {
            isOverrun -> {
                val extraTxt = String.format("%.1f", overrunMb)
                "Dépassement OS sur $carrierDisplayName ($planName) : Android a mesuré $recu Mo sur $promis Mo annoncés ($pctTxt%, soit +$extraTxt Mo). Origine fréquente : tranche horaire OS de 2h antérieure au SMS, trafic bonus/zero-rating ou double SIM$priceNote."
            }
            isExhausted && missingMb > 0.0 ->
                "Écart détecté après épuisement sur $carrierDisplayName ($planName) : sur $promis Mo annoncés, seulement $recu Mo mesurés par Android ($pctTxt%). Manque constaté : -$manque Mo$priceNote."
            rawConsumedPct >= 90.0 ->
                "Forfait $carrierDisplayName presque complet ou conforme : sur $promis Mo souscrits, Android a mesuré $recu Mo ($pctTxt%)$priceNote."
            else ->
                "Suivi local en cours sur $carrierDisplayName ($planName) : $recu Mo mesurés par Android sur $promis Mo ($pctTxt%). Reste théorique : $manque Mo$priceNote."
        }
    }

    fun buildTechnicalProof(): String {
        val topApps = if (appUsages.isEmpty()) {
            "Aucun trafic d'application significatif sur cette fenêtre"
        } else {
            appUsages.take(5).joinToString(", ") {
                "${it.appName} (${String.format("%.1f", it.totalMbDecimal)} Mo)"
            }
        }
        return "Comptage noyau Android (NetworkStatsManager TYPE_MOBILE) : " +
            "${String.format("%.2f", measuredDecimalMb)} Mo (10^6) | ${String.format("%.2f", measuredBinaryMib)} Mio (2^20) | $totalBytes octets bruts " +
            "[Rx: ${String.format("%.2f", rxBytes / 1_000_000.0)} Mo / Tx: ${String.format("%.2f", txBytes / 1_000_000.0)} Mo]. " +
            "Répartition par UID : $topApps."
    }
}

data class HouseRadarSpot(
    val id: String,
    val roomLabel: String,
    val rsrpDbm: Int,
    val rsrqDb: Int,
    val sinrDb: Int,
    val cqi: Int,
    val speedMbps: Double,
    val pingMs: Int,
    val azimuthDeg: Double,
    val posX: Double,
    val posY: Double,
    val altitudeZ: Double,
    val bandLabel: String,
    val isPureRfMeasurement: Boolean,
    val timestampMs: Long,
    /** Identifies the dead-reckoning coordinate frame (one per app session until re-anchored). */
    val frameId: Long = 0L
) {
    val compositeScore: Double
        get() {
            val rsrpNorm = ((rsrpDbm + 120.0) / 45.0).coerceIn(0.0, 1.0)
            val sinrNorm = ((sinrDb + 5.0) / 25.0).coerceIn(0.0, 1.0)
            val speedNorm = (speedMbps / 25.0).coerceIn(0.0, 1.0)
            return ((rsrpNorm * 0.42 + sinrNorm * 0.36 + speedNorm * 0.22) * 100.0).coerceIn(0.0, 100.0)
        }
}

/** Distance (m) under which the user is considered to have reached the saved spot. */
const val NAV_ARRIVAL_RADIUS_M = 1.2

/** Distance (m) above which an already-reached spot is considered left again (hysteresis). */
const val NAV_LEAVE_RADIUS_M = 1.8

/** Average walking stride used by the step-based dead reckoning. */
const val NAV_STRIDE_METERS = 0.72

/** Linear-acceleration magnitude (m/s²) above which the phone is considered moving. */
const val MOTION_THRESHOLD_MS2 = 0.4

/** A step reported more than this long after the last motion is rejected as a false positive. */
const val STATIONARY_GATE_NS = 1_000_000_000L

/** Number of breadcrumb positions kept for the compass trail. */
const val TRAIL_MAX_POINTS = 240

/** Live guidance toward the selected saved spot via topological A* path. [relTurnDeg] > 0 means "turn right". */
data class NavSnapshot(
    val spot: HouseRadarSpot,
    val distanceM: Double,
    val bearingDeg: Double,
    val relTurnDeg: Double,
    val arrived: Boolean,
    val straightDistanceM: Double = distanceM,
    val nextTurnDistanceM: Double = distanceM,
    val routeSteps: List<RouteStep> = emptyList(),
    val justRerouted: Boolean = false,
    val isDirectFallback: Boolean = false,
    val hasBlockedSegments: Boolean = false
) {
    val approxSteps: Int get() = (distanceM / NAV_STRIDE_METERS).toInt().coerceAtLeast(0)
}

class LiveRadioAndSensorState {
    @Volatile var hasSimCard: Boolean = true
    @Volatile var operatorName: String = "Détection SIM..."
    @Volatile var simOperatorCode: String = ""
    @Volatile var simCountryIso: String = "cm"
    @Volatile var currencyLabel: String = "FCFA"
    @Volatile var networkType: String = "4G LTE"
    @Volatile var bandLabel: String = "Bande 4G"
    @Volatile var earfcn: Int = 0
    @Volatile var pci: Int = 0
    @Volatile var cellId: Long = 0L

    // Real-time RF metrics
    @Volatile var rsrpDbm: Int = -95
    @Volatile var rsrqDb: Int = -10
    @Volatile var sinrDb: Int = 5
    @Volatile var rssiDbm: Int = -75
    @Volatile var cqi: Int = 8
    @Volatile var asuLevel: Int = 45
    @Volatile var timingAdvance: Int = -1
    @Volatile var estimatedDistanceMeters: Int = 0
    @Volatile var neighborCellsCount: Int = 0
    @Volatile var lastRfUpdateMs: Long = System.currentTimeMillis()

    // Throughput & active probe
    @Volatile var linkDownKbps: Int = 0
    @Volatile var linkUpKbps: Int = 0
    @Volatile var passiveRxMbps: Double = 0.0
    @Volatile var passiveTxMbps: Double = 0.0
    @Volatile var lastActiveSpeedMbps: Double = 0.0
    @Volatile var lastActivePingMs: Int = 0
    @Volatile var isRunningSpeedTest: Boolean = false
    @Volatile var lastSpeedTestTransportLabel: String = "SIM 4G (Signal physique)"

    // 120 Hz 3D Tilt-Compensated Orientation & Localization
    @Volatile var azimuthDeg: Double = 0.0
    @Volatile var pitchDeg: Double = 0.0
    @Volatile var rollDeg: Double = 0.0
    @Volatile var stepCount: Int = 0
    @Volatile var posX: Double = 0.0
    @Volatile var posY: Double = 0.0
    @Volatile var pressureHpa: Float = 1013.25f
    @Volatile var relativeAltitudeMeters: Double = 0.0

    // GPS / Fused Location
    @Volatile var gpsLat: Double = 0.0
    @Volatile var gpsLon: Double = 0.0
    @Volatile var gpsAccuracyMeters: Float = 0f
    @Volatile var hasGpsFix: Boolean = false

    // AI Gemma state
    @Volatile var isRunningAiAudit: Boolean = false
    @Volatile var lastGemmaReport: String = ""

    // Rolling 48-point RSRP oscilloscope trace
    val rsrpTrace = FloatArray(48) { -90f }
    @Volatile var rsrpTraceIndex: Int = 0

    fun pushRsrpSample(dbm: Int) {
        val clamped = min(-50, max(-130, dbm)).toFloat()
        val nextIdx = (rsrpTraceIndex + 1) % rsrpTrace.size
        rsrpTrace[nextIdx] = clamped
        rsrpTraceIndex = nextIdx
    }

    val signalQualityLabel: String
        get() = when {
            rsrpDbm >= -85 && sinrDb >= 8 -> "Excellent (Optimal Téléchargement)"
            rsrpDbm >= -95 && sinrDb >= 3 -> "Très Bon (4G Stable)"
            rsrpDbm >= -105 -> "Moyen (Atténuation Murs)"
            else -> "Faible (Zone d'ombre Indoor)"
        }

    val cardinalLabel: String
        get() {
            val dirs = arrayOf("N", "NE", "E", "SE", "S", "SO", "O", "NO")
            val idx = (((azimuthDeg + 22.5) % 360.0) / 45.0).toInt().coerceIn(0, 7)
            return dirs[idx]
        }
}
