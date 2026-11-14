package com.checkmymega.check_my_mega

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.Choreographer
import android.view.HapticFeedbackConstants
import android.view.View
import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

enum class VectorIconType {
    HOME,
    COMPASS,
    AI_SPARK,
    SETTINGS,
    MENU,
    PLUS,
    LOCATION_PIN,
    BOLT,
    REFRESH,
    SHIELD_CHECK,
    COPY,
    KEY,
    SIM_CARD,
    MOON,
    SUN,
    TRASH,
    CHEVRON_DOWN,
    CHEVRON_UP,
    TROPHY,
    RADIO_TOWER
}

/**
 * Pure Native Vector Icon View (Feather / Material / Lucide geometric vector strokes).
 * Eliminates all emojis across the application in favor of crisp vector icons.
 */
class VectorIconView(
    context: Context,
    var iconType: VectorIconType,
    var tintColor: Int = Color.parseColor("#0284C7"),
    sizeDp: Float = 22f
) : View(context) {

    private val density = resources.displayMetrics.density
    private fun dp(v: Float): Float = v * density
    private val targetPx = dp(sizeDp).roundToInt()

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val path = Path()
    private val rect = RectF()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(targetPx, targetPx)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f
        val cy = h / 2f
        val r = min(w, h) * 0.38f

        strokePaint.color = tintColor
        strokePaint.strokeWidth = dp(2.0f)
        fillPaint.color = tintColor
        path.reset()

        when (iconType) {
            VectorIconType.HOME -> {
                // Roof + House walls + Door
                path.moveTo(cx - r, cy - r * 0.1f)
                path.lineTo(cx, cy - r * 0.95f)
                path.lineTo(cx + r, cy - r * 0.1f)
                path.moveTo(cx - r * 0.72f, cy - r * 0.22f)
                path.lineTo(cx - r * 0.72f, cy + r * 0.85f)
                path.lineTo(cx + r * 0.72f, cy + r * 0.85f)
                path.lineTo(cx + r * 0.72f, cy - r * 0.22f)
                canvas.drawPath(path, strokePaint)
                rect.set(cx - r * 0.22f, cy + r * 0.2f, cx + r * 0.22f, cy + r * 0.85f)
                canvas.drawRect(rect, strokePaint)
            }

            VectorIconType.COMPASS -> {
                canvas.drawCircle(cx, cy, r * 0.95f, strokePaint)
                // Diamond compass needle inside circle
                path.moveTo(cx + r * 0.42f, cy - r * 0.42f)
                path.lineTo(cx + r * 0.14f, cy + r * 0.14f)
                path.lineTo(cx - r * 0.42f, cy + r * 0.42f)
                path.lineTo(cx - r * 0.14f, cy - r * 0.14f)
                path.close()
                canvas.drawPath(path, strokePaint)
            }

            VectorIconType.AI_SPARK -> {
                // 4-point AI star / Gemma spark
                path.moveTo(cx, cy - r)
                path.quadTo(cx, cy, cx + r, cy)
                path.quadTo(cx, cy, cx, cy + r)
                path.quadTo(cx, cy, cx - r, cy)
                path.quadTo(cx, cy, cx, cy - r)
                path.close()
                canvas.drawPath(path, strokePaint)
                canvas.drawCircle(cx + r * 0.7f, cy - r * 0.7f, dp(1.6f), fillPaint)
            }

            VectorIconType.SETTINGS -> {
                // Gear circle + teeth
                canvas.drawCircle(cx, cy, r * 0.38f, strokePaint)
                for (i in 0 until 8) {
                    val ang = Math.toRadians((i * 45).toDouble())
                    val x1 = cx + (r * 0.62f) * cos(ang).toFloat()
                    val y1 = cy + (r * 0.62f) * sin(ang).toFloat()
                    val x2 = cx + (r * 0.95f) * cos(ang).toFloat()
                    val y2 = cy + (r * 0.95f) * sin(ang).toFloat()
                    canvas.drawLine(x1, y1, x2, y2, strokePaint)
                }
            }

            VectorIconType.MENU -> {
                canvas.drawLine(cx - r * 0.85f, cy - r * 0.55f, cx + r * 0.85f, cy - r * 0.55f, strokePaint)
                canvas.drawLine(cx - r * 0.85f, cy, cx + r * 0.85f, cy, strokePaint)
                canvas.drawLine(cx - r * 0.85f, cy + r * 0.55f, cx + r * 0.85f, cy + r * 0.55f, strokePaint)
            }

            VectorIconType.PLUS -> {
                canvas.drawLine(cx, cy - r * 0.75f, cx, cy + r * 0.75f, strokePaint)
                canvas.drawLine(cx - r * 0.75f, cy, cx + r * 0.75f, cy, strokePaint)
            }

            VectorIconType.LOCATION_PIN -> {
                canvas.drawCircle(cx, cy - r * 0.22f, r * 0.32f, strokePaint)
                path.moveTo(cx, cy + r * 0.95f)
                path.quadTo(cx - r * 0.85f, cy + r * 0.1f, cx - r * 0.72f, cy - r * 0.28f)
                rect.set(cx - r * 0.72f, cy - r * 0.95f, cx + r * 0.72f, cy + r * 0.42f)
                path.arcTo(rect, 180f, 180f, false)
                path.quadTo(cx + r * 0.85f, cy + r * 0.1f, cx, cy + r * 0.95f)
                canvas.drawPath(path, strokePaint)
            }

            VectorIconType.BOLT -> {
                path.moveTo(cx + r * 0.12f, cy - r * 0.95f)
                path.lineTo(cx - r * 0.65f, cy + r * 0.08f)
                path.lineTo(cx + r * 0.05f, cy + r * 0.08f)
                path.lineTo(cx - r * 0.12f, cy + r * 0.95f)
                path.lineTo(cx + r * 0.65f, cy - r * 0.08f)
                path.lineTo(cx - r * 0.05f, cy - r * 0.08f)
                path.close()
                canvas.drawPath(path, strokePaint)
            }

            VectorIconType.REFRESH -> {
                rect.set(cx - r * 0.8f, cy - r * 0.8f, cx + r * 0.8f, cy + r * 0.8f)
                canvas.drawArc(rect, -30f, 280f, false, strokePaint)
                path.moveTo(cx + r * 0.35f, cy - r * 0.85f)
                path.lineTo(cx + r * 0.82f, cy - r * 0.42f)
                path.lineTo(cx + r * 0.25f, cy - r * 0.30f)
                canvas.drawPath(path, strokePaint)
            }

            VectorIconType.SHIELD_CHECK -> {
                path.moveTo(cx, cy - r * 0.95f)
                path.lineTo(cx + r * 0.78f, cy - r * 0.60f)
                path.lineTo(cx + r * 0.78f, cy + r * 0.05f)
                path.quadTo(cx + r * 0.72f, cy + r * 0.72f, cx, cy + r * 0.98f)
                path.quadTo(cx - r * 0.72f, cy + r * 0.72f, cx - r * 0.78f, cy + r * 0.05f)
                path.lineTo(cx - r * 0.78f, cy - r * 0.60f)
                path.close()
                canvas.drawPath(path, strokePaint)

                path.reset()
                path.moveTo(cx - r * 0.32f, cy + r * 0.02f)
                path.lineTo(cx - r * 0.05f, cy + r * 0.30f)
                path.lineTo(cx + r * 0.38f, cy - r * 0.22f)
                canvas.drawPath(path, strokePaint)
            }

            VectorIconType.COPY -> {
                rect.set(cx - r * 0.45f, cy - r * 0.45f, cx + r * 0.82f, cy + r * 0.85f)
                canvas.drawRoundRect(rect, dp(3f), dp(3f), strokePaint)
                path.moveTo(cx - r * 0.45f, cy + r * 0.45f)
                path.lineTo(cx - r * 0.82f, cy + r * 0.45f)
                path.lineTo(cx - r * 0.82f, cy - r * 0.82f)
                path.lineTo(cx + r * 0.42f, cy - r * 0.82f)
                path.lineTo(cx + r * 0.42f, cy - r * 0.45f)
                canvas.drawPath(path, strokePaint)
            }

            VectorIconType.KEY -> {
                canvas.drawCircle(cx - r * 0.38f, cy, r * 0.42f, strokePaint)
                canvas.drawLine(cx + r * 0.04f, cy, cx + r * 0.92f, cy, strokePaint)
                canvas.drawLine(cx + r * 0.58f, cy, cx + r * 0.58f, cy + r * 0.36f, strokePaint)
                canvas.drawLine(cx + r * 0.88f, cy, cx + r * 0.88f, cy + r * 0.36f, strokePaint)
            }

            VectorIconType.SIM_CARD -> {
                path.moveTo(cx - r * 0.68f, cy - r * 0.90f)
                path.lineTo(cx + r * 0.28f, cy - r * 0.90f)
                path.lineTo(cx + r * 0.68f, cy - r * 0.48f)
                path.lineTo(cx + r * 0.68f, cy + r * 0.90f)
                path.lineTo(cx - r * 0.68f, cy + r * 0.90f)
                path.close()
                canvas.drawPath(path, strokePaint)
                rect.set(cx - r * 0.34f, cy - r * 0.18f, cx + r * 0.34f, cy + r * 0.52f)
                canvas.drawRoundRect(rect, dp(2f), dp(2f), strokePaint)
            }

            VectorIconType.MOON -> {
                rect.set(cx - r * 0.85f, cy - r * 0.85f, cx + r * 0.85f, cy + r * 0.85f)
                canvas.drawArc(rect, 45f, 250f, false, strokePaint)
            }

            VectorIconType.SUN -> {
                canvas.drawCircle(cx, cy, r * 0.44f, strokePaint)
                for (i in 0 until 8) {
                    val ang = Math.toRadians((i * 45).toDouble())
                    canvas.drawLine(
                        cx + (r * 0.65f) * cos(ang).toFloat(),
                        cy + (r * 0.65f) * sin(ang).toFloat(),
                        cx + (r * 0.95f) * cos(ang).toFloat(),
                        cy + (r * 0.95f) * sin(ang).toFloat(),
                        strokePaint
                    )
                }
            }

            VectorIconType.TRASH -> {
                canvas.drawLine(cx - r * 0.82f, cy - r * 0.55f, cx + r * 0.82f, cy - r * 0.55f, strokePaint)
                rect.set(cx - r * 0.58f, cy - r * 0.55f, cx + r * 0.58f, cy + r * 0.88f)
                canvas.drawRect(rect, strokePaint)
                canvas.drawLine(cx - r * 0.25f, cy - r * 0.85f, cx + r * 0.25f, cy - r * 0.85f, strokePaint)
            }

            VectorIconType.CHEVRON_DOWN -> {
                path.moveTo(cx - r * 0.65f, cy - r * 0.25f)
                path.lineTo(cx, cy + r * 0.38f)
                path.lineTo(cx + r * 0.65f, cy - r * 0.25f)
                canvas.drawPath(path, strokePaint)
            }

            VectorIconType.CHEVRON_UP -> {
                path.moveTo(cx - r * 0.65f, cy + r * 0.25f)
                path.lineTo(cx, cy - r * 0.38f)
                path.lineTo(cx + r * 0.65f, cy + r * 0.25f)
                canvas.drawPath(path, strokePaint)
            }

            VectorIconType.TROPHY -> {
                rect.set(cx - r * 0.55f, cy - r * 0.85f, cx + r * 0.55f, cy + r * 0.15f)
                canvas.drawArc(rect, 0f, 180f, false, strokePaint)
                canvas.drawLine(cx - r * 0.55f, cy - r * 0.85f, cx + r * 0.55f, cy - r * 0.85f, strokePaint)
                canvas.drawLine(cx, cy + r * 0.15f, cx, cy + r * 0.80f, strokePaint)
                canvas.drawLine(cx - r * 0.45f, cy + r * 0.80f, cx + r * 0.45f, cy + r * 0.80f, strokePaint)
            }

            VectorIconType.RADIO_TOWER -> {
                canvas.drawCircle(cx, cy - r * 0.25f, dp(2.2f), fillPaint)
                rect.set(cx - r * 0.55f, cy - r * 0.80f, cx + r * 0.55f, cy + r * 0.30f)
                canvas.drawArc(rect, -145f, 110f, false, strokePaint)
                rect.set(cx - r * 0.90f, cy - r * 1.15f, cx + r * 0.90f, cy + r * 0.65f)
                canvas.drawArc(rect, -145f, 110f, false, strokePaint)
                path.moveTo(cx - r * 0.42f, cy + r * 0.90f)
                path.lineTo(cx, cy - r * 0.20f)
                path.lineTo(cx + r * 0.42f, cy + r * 0.90f)
                canvas.drawPath(path, strokePaint)
            }
        }
    }
}

/**
 * Renders the authentic circular logos of Telecom Operators (blue, MTN, orange™, YooMee, SIM).
 */
class OperatorBrandLogoView(
    context: Context,
    var brandKey: String = "blue",
    sizeDp: Float = 44f
) : View(context) {

    private val density = resources.displayMetrics.density
    private fun dp(v: Float): Float = v * density
    private val targetPx = dp(sizeDp).roundToInt()

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val ovalRect = RectF()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(targetPx, targetPx)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val r = min(cx, cy) - dp(1f)
        val key = brandKey.lowercase()

        when {
            key.contains("mtn") -> {
                fillPaint.color = Color.parseColor("#FFCC00")
                canvas.drawCircle(cx, cy, r, fillPaint)
                strokePaint.color = Color.parseColor("#EAB308")
                strokePaint.strokeWidth = dp(1f)
                canvas.drawCircle(cx, cy, r - dp(0.5f), strokePaint)

                ovalRect.set(cx - r * 0.84f, cy - r * 0.44f, cx + r * 0.84f, cy + r * 0.44f)
                fillPaint.color = Color.parseColor("#004F71")
                canvas.drawOval(ovalRect, fillPaint)

                textPaint.color = Color.WHITE
                textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD_ITALIC)
                textPaint.textSize = r * 0.56f
                canvas.drawText("MTN", cx, cy + r * 0.19f, textPaint)

                fillPaint.color = Color.parseColor("#E30613")
                canvas.drawRoundRect(
                    cx - r * 0.16f,
                    cy + r * 0.24f,
                    cx + r * 0.16f,
                    cy + r * 0.33f,
                    dp(1.5f),
                    dp(1.5f),
                    fillPaint
                )
            }

            key.contains("orange") -> {
                fillPaint.color = Color.parseColor("#FF7900")
                canvas.drawCircle(cx, cy, r, fillPaint)
                textPaint.color = Color.WHITE
                textPaint.typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                textPaint.textSize = r * 0.48f
                canvas.drawText("orange", cx - dp(1.5f), cy + r * 0.16f, textPaint)
                textPaint.textSize = r * 0.20f
                canvas.drawText("TM", cx + r * 0.68f, cy - r * 0.08f, textPaint)
            }

            key.contains("yoomee") -> {
                fillPaint.color = Color.parseColor("#E30613")
                canvas.drawCircle(cx, cy, r, fillPaint)
                textPaint.color = Color.WHITE
                textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                textPaint.textSize = r * 0.42f
                canvas.drawText("YooMee", cx, cy + r * 0.15f, textPaint)
            }

            key.contains("blue") || key.contains("camtel") || key.contains("62405") -> {
                fillPaint.color = Color.parseColor("#0033AB")
                canvas.drawCircle(cx, cy, r, fillPaint)
                textPaint.color = Color.WHITE
                textPaint.typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                textPaint.textSize = r * 0.58f
                canvas.drawText("blue", cx, cy + r * 0.20f, textPaint)
                fillPaint.color = Color.parseColor("#38BDF8")
                canvas.drawCircle(cx + r * 0.48f, cy - r * 0.30f, r * 0.08f, fillPaint)
            }

            else -> {
                fillPaint.color = Color.parseColor("#0284C7")
                canvas.drawCircle(cx, cy, r, fillPaint)
                textPaint.color = Color.WHITE
                textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                textPaint.textSize = r * 0.50f
                canvas.drawText("SIM", cx, cy + r * 0.18f, textPaint)
            }
        }
    }
}

/**
 * Renders the genuine installed Android App Icon (via PackageManager.getApplicationIcon)
 * OR a crisp vector emblem for WhatsApp, ChatGPT, YouTube, Chrome, Hotspot, and Android OS.
 */
class AppIconOrBrandView(
    context: Context,
    private val appName: String,
    private val packageName: String,
    sizeDp: Float = 42f
) : View(context) {

    private val density = resources.displayMetrics.density
    private fun dp(v: Float): Float = v * density
    private val targetPx = dp(sizeDp).roundToInt()

    private var nativeIcon: Drawable? = null
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    init {
        try {
            if (packageName.isNotBlank() &&
                !packageName.startsWith("android.uid.") &&
                packageName != "android.system" &&
                !packageName.startsWith("uid.")
            ) {
                nativeIcon = context.packageManager.getApplicationIcon(packageName)
            } else {
                val lower = "$appName $packageName".lowercase()
                val candidatePkg = when {
                    lower.contains("whatsapp") -> "com.whatsapp"
                    lower.contains("chatgpt") || lower.contains("openai") -> "com.openai.chatgpt"
                    lower.contains("youtube") -> "com.google.android.youtube"
                    lower.contains("chrome") -> "com.android.chrome"
                    lower.contains("tiktok") || lower.contains("musical") -> "com.zhiliaoapp.musically"
                    lower.contains("instagram") -> "com.instagram.android"
                    lower.contains("telegram") -> "org.telegram.messenger"
                    lower.contains("facebook") -> "com.facebook.katana"
                    else -> null
                }
                if (candidatePkg != null) {
                    nativeIcon = context.packageManager.getApplicationIcon(candidatePkg)
                }
            }
        } catch (_: Exception) {
            nativeIcon = null
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(targetPx, targetPx)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val d = nativeIcon
        if (d != null) {
            val pad = dp(2f).roundToInt()
            d.setBounds(pad, pad, width - pad, height - pad)
            d.draw(canvas)
            return
        }

        val cx = width / 2f
        val cy = height / 2f
        val r = min(cx, cy) - dp(1.5f)
        val lower = "$appName $packageName".lowercase()

        when {
            lower.contains("whatsapp") -> {
                fillPaint.color = Color.parseColor("#25D366")
                canvas.drawCircle(cx, cy, r, fillPaint)
                strokePaint.color = Color.WHITE
                strokePaint.strokeWidth = dp(2.2f)
                canvas.drawCircle(cx, cy, r * 0.56f, strokePaint)
                textPaint.color = Color.WHITE
                textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                textPaint.textSize = r * 0.52f
                canvas.drawText("WA", cx, cy + r * 0.18f, textPaint)
            }

            lower.contains("chatgpt") || lower.contains("openai") -> {
                fillPaint.color = Color.parseColor("#10A37F")
                canvas.drawCircle(cx, cy, r, fillPaint)
                strokePaint.color = Color.WHITE
                strokePaint.strokeWidth = dp(1.8f)
                for (i in 0 until 6) {
                    val ang = Math.toRadians((i * 60).toDouble())
                    val ox = cx + (r * 0.22f) * cos(ang).toFloat()
                    val oy = cy + (r * 0.22f) * sin(ang).toFloat()
                    canvas.drawCircle(ox, oy, r * 0.28f, strokePaint)
                }
            }

            lower.contains("youtube") -> {
                fillPaint.color = Color.parseColor("#FF0000")
                canvas.drawCircle(cx, cy, r, fillPaint)
                val p = Path().apply {
                    moveTo(cx - r * 0.22f, cy - r * 0.30f)
                    lineTo(cx + r * 0.32f, cy)
                    lineTo(cx - r * 0.22f, cy + r * 0.30f)
                    close()
                }
                fillPaint.color = Color.WHITE
                canvas.drawPath(p, fillPaint)
            }

            lower.contains("hotspot") || lower.contains("partage") || lower.contains("tethering") -> {
                fillPaint.color = Color.parseColor("#0EA5E9")
                canvas.drawCircle(cx, cy, r, fillPaint)
                strokePaint.color = Color.WHITE
                strokePaint.strokeWidth = dp(2f)
                canvas.drawCircle(cx, cy, r * 0.22f, strokePaint)
                canvas.drawCircle(cx, cy, r * 0.52f, strokePaint)
            }

            else -> {
                fillPaint.color = Color.parseColor("#0284C7")
                canvas.drawCircle(cx, cy, r, fillPaint)
                val initials = appName.trim().take(2).uppercase().ifBlank { "OS" }
                textPaint.color = Color.WHITE
                textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                textPaint.textSize = r * 0.46f
                canvas.drawText(initials, cx, cy + r * 0.16f, textPaint)
            }
        }
    }
}

/**
 * 120 Hz "return to my spot" compass and real-time RF oscilloscope.
 *
 * The big arrow always points toward the selected saved spot relative to where the phone is
 * facing, so the user only has to keep it pointing up. The centre shows the distance left, the
 * rings are labelled in metres, saved spots and the walked trail are drawn at their real scaled
 * position, and the phone gives a haptic tick when aligned and a stronger one on arrival.
 * Adapts to Light (Blanc FinTech) and Dark (Obsidienne OLED) modes.
 */
class WayfinderCompassRadarView(
    context: Context,
    private val engine: NativeHardwareEngine
) : View(context), Choreographer.FrameCallback {

    private companion object {
        val BLUE = Color.parseColor("#0284C7")
        val GREEN = Color.parseColor("#10B981")
        val ORANGE = Color.parseColor("#F59E0B")
        val RED = Color.parseColor("#EF4444")
        const val LOCK_ENTER_DEG = 12.0
        const val LOCK_LEAVE_DEG = 20.0
    }

    private val density = resources.displayMetrics.density
    private fun dp(v: Float): Float = v * density

    private var isAttached = false
    private var displayedAzimuthDeg = 0.0
    private var displayedRelTurnDeg = 0.0
    private var displayedScaleM = 3.0
    private var pulse = 0f
    private var snap: NavSnapshot? = null
    private var frameSpots: List<HouseRadarSpot> = emptyList()
    private var isLocked = false
    private var wasArrived = false

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }
    private val labelHalo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
    }

    private val routePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }

    private val arrowPath = Path()
    private val trianglePath = Path()
    private val checkPath = Path()
    private val routePath = Path()
    private val arcRect = RectF()
    private val routeDashEffect = DashPathEffect(floatArrayOf(dp(5f), dp(4f)), 0f)
    private val directDashEffect = DashPathEffect(floatArrayOf(dp(3f), dp(5f)), 0f)

    private var dialShader: RadialGradient? = null
    private var dialShaderKey = 0f

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isAttached = true
        Choreographer.getInstance().postFrameCallback(this)
    }

    override fun onDetachedFromWindow() {
        isAttached = false
        Choreographer.getInstance().removeFrameCallback(this)
        super.onDetachedFromWindow()
    }

    private fun wrap180(deg: Double): Double = ((deg + 540.0) % 360.0) - 180.0

    private fun haptic(strong: Boolean) {
        val kind = when {
            strong && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> HapticFeedbackConstants.CONFIRM
            strong -> HapticFeedbackConstants.LONG_PRESS
            else -> HapticFeedbackConstants.CLOCK_TICK
        }
        performHapticFeedback(kind)
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!isAttached) return
        val st = engine.state

        displayedAzimuthDeg = (displayedAzimuthDeg + wrap180(st.azimuthDeg - displayedAzimuthDeg) * 0.42 + 360.0) % 360.0

        frameSpots = engine.navigableSpots()
        val s = engine.navSnapshot()
        snap = s
        if (s != null) {
            displayedRelTurnDeg = wrap180(s.bearingDeg - displayedAzimuthDeg)
            val absRel = abs(displayedRelTurnDeg)
            val lockedNow = if (isLocked) absRel <= LOCK_LEAVE_DEG else absRel <= LOCK_ENTER_DEG
            if (s.arrived && !wasArrived) {
                haptic(strong = true)
            } else if (lockedNow && !isLocked && !s.arrived) {
                haptic(strong = false)
            }
            isLocked = lockedNow
            wasArrived = s.arrived
        } else {
            isLocked = false
            wasArrived = false
        }

        // Ease the metre scale so rings and spots glide instead of jumping.
        var maxDist = 0.0
        for (sp in frameSpots) {
            maxDist = max(maxDist, hypot(sp.posX - st.posX, sp.posY - st.posY))
        }
        val targetScale = (maxDist * 1.15).coerceIn(3.0, 30.0)
        displayedScaleM += (targetScale - displayedScaleM) * 0.08

        pulse = ((frameTimeNanos / 1_000_000L) % 1600L) / 1600f

        invalidate()
        Choreographer.getInstance().postFrameCallback(this)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val dialSize = min(w, dp(236f).roundToInt())
        setMeasuredDimension(w, dialSize + dp(18f).roundToInt())
    }

    private fun radiusFor(distM: Double, rIn: Float, rMax: Float): Float {
        val t = (distM / displayedScaleM).coerceIn(0.0, 1.0).toFloat()
        return rIn + t * (rMax - rIn)
    }

    private fun formatMeters(m: Double): String =
        if (m >= 10.0) String.format(Locale.getDefault(), "%.0f m", m)
        else String.format(Locale.getDefault(), "%.1f m", m)

    private fun drawLabel(canvas: Canvas, text: String, x: Float, y: Float, size: Float, color: Int, haloColor: Int) {
        labelHalo.textSize = size
        labelHalo.strokeWidth = dp(3f)
        labelHalo.color = haloColor
        labelPaint.textSize = size
        labelPaint.color = color
        canvas.drawText(text, x, y, labelHalo)
        canvas.drawText(text, x, y, labelPaint)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val st = engine.state
        val dark = engine.settings.isDarkMode
        val s = snap
        val w = width.toFloat()
        val dialSize = min(w - dp(16f), dp(224f))
        val cx = w / 2f
        val cy = dialSize / 2f + dp(8f)
        val outerR = dialSize / 2f - dp(6f)
        val hubR = outerR * 0.39f
        val rIn = outerR * 0.44f
        val rMax = outerR * 0.80f

        val textMain = if (dark) Color.parseColor("#F8FAFC") else Color.parseColor("#0F172A")
        val textSub = if (dark) Color.parseColor("#94A3B8") else Color.parseColor("#475569")
        val faceBg = if (dark) Color.parseColor("#0F172A") else Color.parseColor("#FFFFFF")
        val ringCol = if (dark) Color.parseColor("#1E293B") else Color.parseColor("#E2E8F0")

        val stateColor = when {
            s == null -> BLUE
            s.arrived || isLocked -> GREEN
            abs(displayedRelTurnDeg) > 100.0 -> ORANGE
            else -> BLUE
        }

        // 1. Dial face (gradient cached: recreated only when size or theme changes)
        val shaderKey = outerR + if (dark) 10000f else 0f
        if (dialShader == null || dialShaderKey != shaderKey) {
            dialShaderKey = shaderKey
            dialShader = RadialGradient(
                cx, cy, outerR,
                if (dark) {
                    intArrayOf(Color.parseColor("#0F172A"), Color.parseColor("#1E293B"), Color.parseColor("#090F1D"))
                } else {
                    intArrayOf(Color.parseColor("#FFFFFF"), Color.parseColor("#F0F9FF"), Color.parseColor("#E0F2FE"))
                },
                floatArrayOf(0.0f, 0.72f, 1.0f),
                Shader.TileMode.CLAMP
            )
        }
        fillPaint.shader = dialShader
        canvas.drawCircle(cx, cy, outerR, fillPaint)
        fillPaint.shader = null

        strokePaint.color = if (dark) Color.parseColor("#334155") else Color.parseColor("#BAE6FD")
        strokePaint.strokeWidth = dp(2.2f)
        canvas.drawCircle(cx, cy, outerR, strokePaint)

        // 2. Alignment window on the rim: keep the arrow inside it to walk straight to the spot
        if (s != null && !s.arrived) {
            arcRect.set(cx - outerR, cy - outerR, cx + outerR, cy + outerR)
            strokePaint.strokeWidth = dp(5f)
            strokePaint.color = Color.argb(if (isLocked) 255 else 90, Color.red(stateColor), Color.green(stateColor), Color.blue(stateColor))
            canvas.drawArc(arcRect, (-90.0 - LOCK_ENTER_DEG).toFloat(), (2 * LOCK_ENTER_DEG).toFloat(), false, strokePaint)
        }

        // 3. Metre rings (fixed to the screen, labelled)
        val rMid = rIn + (rMax - rIn) * 0.5f
        strokePaint.strokeWidth = dp(1f)
        strokePaint.color = ringCol
        canvas.drawCircle(cx, cy, rIn, strokePaint)
        canvas.drawCircle(cx, cy, rMid, strokePaint)
        canvas.drawCircle(cx, cy, rMax, strokePaint)
        val labelAngle = Math.toRadians(50.0)
        drawLabel(
            canvas, formatMeters(displayedScaleM / 2.0),
            cx + rMid * cos(labelAngle).toFloat(), cy + rMid * sin(labelAngle).toFloat() + dp(3f),
            dp(8.5f), textSub, faceBg
        )
        drawLabel(
            canvas, formatMeters(displayedScaleM),
            cx + rMax * cos(labelAngle).toFloat(), cy + rMax * sin(labelAngle).toFloat() + dp(3f),
            dp(8.5f), textSub, faceBg
        )

        // 4. World layer: rotates with the phone heading so north, spots and trail stay true
        canvas.save()
        canvas.rotate((-displayedAzimuthDeg).toFloat(), cx, cy)

        for (deg in 0 until 360 step 15) {
            val rad = Math.toRadians((deg - 90).toDouble())
            val isCardinal = deg % 90 == 0
            val isMajor = deg % 45 == 0
            val len = when {
                isCardinal -> dp(13f)
                isMajor -> dp(8f)
                else -> dp(4.5f)
            }
            val rStart = outerR - dp(4f)
            val rEnd = rStart - len
            strokePaint.strokeWidth = if (isCardinal) dp(2.4f) else if (isMajor) dp(1.4f) else dp(0.9f)
            strokePaint.color = when {
                deg == 0 -> RED
                isCardinal -> textMain
                isMajor -> textSub
                else -> if (dark) Color.parseColor("#475569") else Color.parseColor("#94A3B8")
            }
            canvas.drawLine(
                cx + rStart * cos(rad).toFloat(), cy + rStart * sin(rad).toFloat(),
                cx + rEnd * cos(rad).toFloat(), cy + rEnd * sin(rad).toFloat(),
                strokePaint
            )
        }

        val cardinals = listOf(
            Triple(0, "N", RED),
            Triple(90, "E", textMain),
            Triple(180, "S", textMain),
            Triple(270, "O", textMain)
        )
        for ((deg, lbl, col) in cardinals) {
            val rad = Math.toRadians((deg - 90).toDouble())
            val rCard = outerR - dp(26f)
            val tx = cx + rCard * cos(rad).toFloat()
            val ty = cy + rCard * sin(rad).toFloat()
            canvas.save()
            canvas.rotate(displayedAzimuthDeg.toFloat(), tx, ty)
            textPaint.color = col
            textPaint.textSize = dp(13.5f)
            textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            canvas.drawText(lbl, tx, ty + dp(5f), textPaint)
            canvas.restore()
        }

        // Breadcrumb trail of the path walked so far
        val trail = engine.trail
        val trailCount = trail.size
        trail.forEachIndexed { i, p ->
            val dx = p[0] - st.posX
            val dy = p[1] - st.posY
            val d = hypot(dx, dy)
            if (d >= 0.3) {
                val r = radiusFor(d, rIn, rMax)
                fillPaint.color = Color.argb(35 + 120 * (i + 1) / max(trailCount, 1), 148, 163, 184)
                canvas.drawCircle(
                    cx + r * (dx / d).toFloat(), cy - r * (dy / d).toFloat(),
                    dp(2f), fillPaint
                )
            }
        }

        // A* topological corridor route projected into the compass dial
        if (s != null && s.routeSteps.size >= 2) {
            routePath.reset()
            s.routeSteps.forEachIndexed { idx, step ->
                val dx = step.x - st.posX
                val dy = step.y - st.posY
                val d = hypot(dx, dy)
                val sx: Float
                val sy: Float
                if (d > 0.08) {
                    val r = radiusFor(d, rIn, rMax)
                    sx = cx + r * (dx / d).toFloat()
                    sy = cy - r * (dy / d).toFloat()
                } else {
                    sx = cx
                    sy = cy
                }
                if (idx == 0) routePath.moveTo(sx, sy) else routePath.lineTo(sx, sy)
                if (idx in 1 until s.routeSteps.lastIndex && d > 0.25) {
                    fillPaint.color = if (s.isDirectFallback) ORANGE else GREEN
                    canvas.drawCircle(sx, sy, dp(2.8f), fillPaint)
                }
            }
            routePaint.color = if (s.isDirectFallback) ORANGE else GREEN
            routePaint.strokeWidth = dp(2.4f)
            routePaint.pathEffect = if (s.isDirectFallback) directDashEffect else routeDashEffect
            canvas.drawPath(routePath, routePaint)
        }

        // Saved spots of the current session at their true scaled position
        for (sp in frameSpots) {
            val isTarget = s != null && s.spot.id == sp.id
            val dx = sp.posX - st.posX
            val dy = sp.posY - st.posY
            val d = hypot(dx, dy)
            val brg = if (d > 0.05) atan2(dx, dy) else Math.toRadians(sp.azimuthDeg)
            val r = radiusFor(d, rIn, rMax)
            val nx = cx + r * sin(brg).toFloat()
            val ny = cy - r * cos(brg).toFloat()

            if (isTarget) {
                strokePaint.strokeWidth = dp(2f)
                strokePaint.color = Color.argb(((1f - pulse) * 190f).toInt(), 16, 185, 129)
                canvas.drawCircle(nx, ny, dp(8f) + pulse * dp(11f), strokePaint)
                strokePaint.strokeWidth = dp(2.2f)
                strokePaint.color = GREEN
                canvas.drawCircle(nx, ny, dp(9f), strokePaint)
            }
            fillPaint.color = if (isTarget) GREEN else BLUE
            canvas.drawCircle(nx, ny, if (isTarget) dp(5.5f) else dp(4.2f), fillPaint)

            canvas.save()
            canvas.rotate(displayedAzimuthDeg.toFloat(), nx, ny)
            drawLabel(
                canvas, sp.roomLabel.take(12), nx, ny + dp(20f), dp(9.5f),
                if (isTarget) GREEN else textMain, faceBg
            )
            canvas.restore()
        }
        canvas.restore()

        // 5. Direction arrow toward the target (keep it pointing up to walk straight)
        if (s != null && !s.arrived) {
            canvas.save()
            canvas.rotate(displayedRelTurnDeg.toFloat(), cx, cy)
            val tipY = cy - outerR + dp(30f)
            val headLen = dp(26f)
            val headHalf = dp(16f)
            val shaftHalf = dp(5.5f)
            val baseY = cy - hubR + dp(2f)
            arrowPath.reset()
            arrowPath.moveTo(cx, tipY)
            arrowPath.lineTo(cx + headHalf, tipY + headLen)
            arrowPath.lineTo(cx + shaftHalf, tipY + headLen)
            arrowPath.lineTo(cx + shaftHalf, baseY)
            arrowPath.lineTo(cx - shaftHalf, baseY)
            arrowPath.lineTo(cx - shaftHalf, tipY + headLen)
            arrowPath.lineTo(cx - headHalf, tipY + headLen)
            arrowPath.close()
            fillPaint.color = stateColor
            canvas.drawPath(arrowPath, fillPaint)
            strokePaint.color = faceBg
            strokePaint.strokeWidth = dp(1.6f)
            canvas.drawPath(arrowPath, strokePaint)
            canvas.restore()
        }

        // 6. Centre hub: distance left, or heading when there is nothing to guide to
        fillPaint.color = when {
            s != null && s.arrived -> if (dark) Color.parseColor("#062E22") else Color.parseColor("#DCFCE7")
            else -> faceBg
        }
        canvas.drawCircle(cx, cy, hubR, fillPaint)
        strokePaint.color = stateColor
        strokePaint.strokeWidth = dp(3f)
        canvas.drawCircle(cx, cy, hubR, strokePaint)

        textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        if (s == null) {
            textPaint.color = BLUE
            textPaint.textSize = dp(10f)
            canvas.drawText(st.cardinalLabel, cx, cy - dp(16f), textPaint)
            textPaint.color = textMain
            textPaint.textSize = dp(22f)
            canvas.drawText("${displayedAzimuthDeg.roundToInt()}°", cx, cy + dp(6f), textPaint)
            textPaint.color = textSub
            textPaint.textSize = dp(8.5f)
            textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            canvas.drawText(
                if (engine.radarSpots.isEmpty()) "Capturez un coin" else "Recalage requis",
                cx, cy + dp(20f), textPaint
            )
        } else if (s.arrived) {
            strokePaint.color = GREEN
            strokePaint.strokeWidth = dp(3.5f)
            checkPath.reset()
            checkPath.moveTo(cx - dp(10f), cy - dp(16f))
            checkPath.lineTo(cx - dp(3f), cy - dp(9f))
            checkPath.lineTo(cx + dp(11f), cy - dp(24f))
            canvas.drawPath(checkPath, strokePaint)

            strokePaint.strokeWidth = dp(2f)
            strokePaint.color = Color.argb(((1f - pulse) * 170f).toInt(), 16, 185, 129)
            canvas.drawCircle(cx, cy, hubR + pulse * dp(12f), strokePaint)

            textPaint.color = GREEN
            textPaint.textSize = dp(16.5f)
            canvas.drawText("Arrivé", cx, cy + dp(8f), textPaint)
            textPaint.color = textSub
            textPaint.textSize = dp(9f)
            canvas.drawText(s.spot.roomLabel.take(14), cx, cy + dp(22f), textPaint)
        } else {
            textPaint.color = textSub
            textPaint.textSize = dp(8f)
            canvas.drawText("VERS ${s.spot.roomLabel.uppercase().take(10)}", cx, cy - dp(17f), textPaint)
            textPaint.color = textMain
            textPaint.textSize = dp(22f)
            canvas.drawText(formatMeters(s.distanceM), cx, cy + dp(6f), textPaint)
            textPaint.color = textSub
            textPaint.textSize = dp(9.5f)
            textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            canvas.drawText("≈ ${s.approxSteps} pas", cx, cy + dp(20f), textPaint)
        }

        // Top lubber triangle (turns green when aligned)
        trianglePath.reset()
        trianglePath.moveTo(cx, cy - outerR + dp(2f))
        trianglePath.lineTo(cx - dp(6f), cy - outerR - dp(7f))
        trianglePath.lineTo(cx + dp(6f), cy - outerR - dp(7f))
        trianglePath.close()
        fillPaint.color = if (s != null && (isLocked || s.arrived)) GREEN else BLUE
        canvas.drawPath(trianglePath, fillPaint)
    }
}

/**
 * 120Hz top-down video-game tactical mini-map backed by [IndoorNavigationGraph].
 * Renders explored corridor edges, detected wall/obstacle detours, live A* shortest path,
 * saved signal spots, metric grid/scale bar, and real-time player FOV cone.
 */
class VideoGameMiniMapView(
    context: Context,
    private val engine: NativeHardwareEngine
) : View(context), Choreographer.FrameCallback {

    companion object {
        private val BLUE = Color.parseColor("#0284C7")
        private val SKY = Color.parseColor("#38BDF8")
        private val GREEN = Color.parseColor("#10B981")
        private val ORANGE = Color.parseColor("#F59E0B")
        private val RED = Color.parseColor("#EF4444")
    }

    private val density = resources.displayMetrics.density
    private fun dp(v: Float): Float = v * density

    private var isAttached = false
    private var displayedAzimuthDeg = 0.0
    private var camCenterX = 0.0
    private var camCenterY = 0.0
    private var camHalfSpanM = 5.5
    private var camInitialized = false
    private var pulse = 0f

    private var graphSnap = GraphRenderSnapshot(emptyList(), emptyList())
    private var frameSpots: List<HouseRadarSpot> = emptyList()
    private var navSnap: NavSnapshot? = null

    private val nodeMap = HashMap<Int, GraphNode>(64)

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val dashedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }
    private val labelHalo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
    }
    private val hudPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }

    private val clipPath = Path()
    private val trailPath = Path()
    private val routePath = Path()
    private val fovPath = Path()
    private val playerPath = Path()
    private val mapRect = RectF()
    private val badgeRect = RectF()

    private val obstacleDashEffect = DashPathEffect(floatArrayOf(dp(5f), dp(4f)), 0f)
    private val directRouteDashEffect = DashPathEffect(floatArrayOf(dp(4f), dp(5f)), 0f)

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isAttached = true
        Choreographer.getInstance().postFrameCallback(this)
    }

    override fun onDetachedFromWindow() {
        isAttached = false
        Choreographer.getInstance().removeFrameCallback(this)
        super.onDetachedFromWindow()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = dp(208f).roundToInt()
        setMeasuredDimension(w, h)
    }

    private fun wrap180(deg: Double): Double = ((deg + 540.0) % 360.0) - 180.0

    override fun doFrame(frameTimeNanos: Long) {
        if (!isAttached) return
        val st = engine.state
        displayedAzimuthDeg = (displayedAzimuthDeg + wrap180(st.azimuthDeg - displayedAzimuthDeg) * 0.42 + 360.0) % 360.0

        graphSnap = engine.navGraph.renderSnapshot()
        frameSpots = engine.navigableSpots()
        navSnap = engine.navSnapshot()

        // Bounding box across player, explored corridor nodes, and spot pins
        var minX = st.posX
        var maxX = st.posX
        var minY = st.posY
        var maxY = st.posY

        for (n in graphSnap.nodes) {
            if (n.x < minX) minX = n.x
            if (n.x > maxX) maxX = n.x
            if (n.y < minY) minY = n.y
            if (n.y > maxY) maxY = n.y
        }
        for (sp in frameSpots) {
            if (sp.posX < minX) minX = sp.posX
            if (sp.posX > maxX) maxX = sp.posX
            if (sp.posY < minY) minY = sp.posY
            if (sp.posY > maxY) maxY = sp.posY
        }

        val targetCx = (minX + maxX) * 0.5
        val targetCy = (minY + maxY) * 0.5
        val targetSpan = (max(maxX - minX, maxY - minY) * 0.55 + 1.8).coerceIn(5.0, 32.0)

        if (!camInitialized) {
            camCenterX = targetCx
            camCenterY = targetCy
            camHalfSpanM = targetSpan
            camInitialized = true
        } else {
            camCenterX += (targetCx - camCenterX) * 0.12
            camCenterY += (targetCy - camCenterY) * 0.12
            camHalfSpanM += (targetSpan - camHalfSpanM) * 0.10
        }

        pulse = ((frameTimeNanos / 1_000_000L) % 1600L) / 1600f
        invalidate()
        Choreographer.getInstance().postFrameCallback(this)
    }

    private fun drawHaloLabel(canvas: Canvas, text: String, x: Float, y: Float, size: Float, color: Int, halo: Int) {
        labelHalo.textSize = size
        labelHalo.strokeWidth = dp(3.2f)
        labelHalo.color = halo
        labelPaint.textSize = size
        labelPaint.color = color
        canvas.drawText(text, x, y, labelHalo)
        canvas.drawText(text, x, y, labelPaint)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val st = engine.state
        val dark = engine.settings.isDarkMode
        val bgCol = if (dark) Color.parseColor("#08101E") else Color.parseColor("#F1F5F9")
        val borderCol = if (dark) Color.parseColor("#1E293B") else Color.parseColor("#CBD5E1")
        val gridCol = if (dark) Color.parseColor("#152338") else Color.parseColor("#E2E8F0")
        val corridorOuter = if (dark) Color.parseColor("#1E293B") else Color.parseColor("#CBD5E1")
        val corridorInner = if (dark) Color.parseColor("#3B82F6") else Color.parseColor("#64748B")
        val textMain = if (dark) Color.parseColor("#F8FAFC") else Color.parseColor("#0F172A")
        val textSub = if (dark) Color.parseColor("#94A3B8") else Color.parseColor("#475569")
        val hudBg = if (dark) Color.argb(215, 15, 23, 42) else Color.argb(225, 255, 255, 255)

        val pad = dp(2f)
        mapRect.set(pad, pad, w - pad, h - pad)
        val cornerR = dp(16f)

        fillPaint.color = bgCol
        canvas.drawRoundRect(mapRect, cornerR, cornerR, fillPaint)

        clipPath.reset()
        clipPath.addRoundRect(mapRect, cornerR, cornerR, Path.Direction.CW)
        canvas.save()
        canvas.clipPath(clipPath)

        val innerW = mapRect.width() - dp(24f)
        val innerH = mapRect.height() - dp(38f)
        val mapCx = mapRect.centerX()
        val mapCy = mapRect.centerY() + dp(6f)
        val pxPerM = (min(innerW, innerH) / (2.0 * camHalfSpanM)).toFloat()

        fun wxToSx(wx: Double): Float = mapCx + ((wx - camCenterX) * pxPerM).toFloat()
        fun wyToSy(wy: Double): Float = mapCy - ((wy - camCenterY) * pxPerM).toFloat()

        // 1. Metric coordinate grid (2m or 5m step)
        val gridStepM = if (camHalfSpanM > 14.0) 5.0 else 2.0
        val worldLeft = camCenterX - (mapCx - mapRect.left) / pxPerM
        val worldRight = camCenterX + (mapRect.right - mapCx) / pxPerM
        val worldBottom = camCenterY - (mapRect.bottom - mapCy) / pxPerM
        val worldTop = camCenterY + (mapCy - mapRect.top) / pxPerM

        strokePaint.color = gridCol
        strokePaint.strokeWidth = dp(0.9f)
        var gx = kotlin.math.floor(worldLeft / gridStepM) * gridStepM
        while (gx <= worldRight) {
            val sx = wxToSx(gx)
            canvas.drawLine(sx, mapRect.top, sx, mapRect.bottom, strokePaint)
            gx += gridStepM
        }
        var gy = kotlin.math.floor(worldBottom / gridStepM) * gridStepM
        while (gy <= worldTop) {
            val sy = wyToSy(gy)
            canvas.drawLine(mapRect.left, sy, mapRect.right, sy, strokePaint)
            gy += gridStepM
        }

        // 2. Explored topological corridor edges
        nodeMap.clear()
        for (n in graphSnap.nodes) {
            nodeMap[n.id] = n
        }

        var blockedCount = 0
        for (edge in graphSnap.edges) {
            if (edge.isBlocked) {
                blockedCount++
                continue
            }
            val a = nodeMap[edge.a] ?: continue
            val b = nodeMap[edge.b] ?: continue
            val ax = wxToSx(a.x)
            val ay = wyToSy(a.y)
            val bx = wxToSx(b.x)
            val by = wyToSy(b.y)

            strokePaint.color = corridorOuter
            strokePaint.strokeWidth = dp(7.5f)
            canvas.drawLine(ax, ay, bx, by, strokePaint)

            strokePaint.color = Color.argb(if (dark) 115 else 145, Color.red(corridorInner), Color.green(corridorInner), Color.blue(corridorInner))
            strokePaint.strokeWidth = dp(2.2f)
            canvas.drawLine(ax, ay, bx, by, strokePaint)
        }

        // Blocked / obstacle edges (190/270 deg detour walls or user-flagged barriers)
        if (blockedCount > 0) {
            dashedPaint.color = RED
            dashedPaint.strokeWidth = dp(2.6f)
            dashedPaint.pathEffect = obstacleDashEffect
            strokePaint.color = RED
            strokePaint.strokeWidth = dp(2f)
            for (edge in graphSnap.edges) {
                if (!edge.isBlocked) continue
                val a = nodeMap[edge.a] ?: continue
                val b = nodeMap[edge.b] ?: continue
                val ax = wxToSx(a.x)
                val ay = wyToSy(a.y)
                val bx = wxToSx(b.x)
                val by = wyToSy(b.y)
                canvas.drawLine(ax, ay, bx, by, dashedPaint)

                val mx = (ax + bx) * 0.5f
                val my = (ay + by) * 0.5f
                val arm = dp(4.5f)
                canvas.drawLine(mx - arm, my - arm, mx + arm, my + arm, strokePaint)
                canvas.drawLine(mx - arm, my + arm, mx + arm, my - arm, strokePaint)
            }
        }

        // 3. Walked fine-grained breadcrumb trail
        val trail = engine.trail
        if (trail.size >= 2) {
            trailPath.reset()
            trail.forEachIndexed { idx, pt ->
                val sx = wxToSx(pt[0])
                val sy = wyToSy(pt[1])
                if (idx == 0) trailPath.moveTo(sx, sy) else trailPath.lineTo(sx, sy)
            }
            strokePaint.color = Color.argb(95, Color.red(SKY), Color.green(SKY), Color.blue(SKY))
            strokePaint.strokeWidth = dp(1.8f)
            canvas.drawPath(trailPath, strokePaint)
        }

        // 4. Shortest A* navigation route polyline + animated travel beacon
        val s = navSnap
        if (s != null && s.routeSteps.size >= 2) {
            val routeCol = if (s.isDirectFallback) ORANGE else GREEN
            routePath.reset()
            var totalPxLen = 0f
            var prevSx = 0f
            var prevSy = 0f
            s.routeSteps.forEachIndexed { idx, step ->
                val sx = wxToSx(step.x)
                val sy = wyToSy(step.y)
                if (idx == 0) {
                    routePath.moveTo(sx, sy)
                } else {
                    routePath.lineTo(sx, sy)
                    totalPxLen += hypot((sx - prevSx).toDouble(), (sy - prevSy).toDouble()).toFloat()
                }
                prevSx = sx
                prevSy = sy
            }

            // Route glow
            strokePaint.color = Color.argb(60, Color.red(routeCol), Color.green(routeCol), Color.blue(routeCol))
            strokePaint.strokeWidth = dp(8.5f)
            canvas.drawPath(routePath, strokePaint)

            if (s.isDirectFallback) {
                dashedPaint.color = routeCol
                dashedPaint.strokeWidth = dp(3f)
                dashedPaint.pathEffect = directRouteDashEffect
                canvas.drawPath(routePath, dashedPaint)
            } else {
                strokePaint.color = routeCol
                strokePaint.strokeWidth = dp(3.2f)
                canvas.drawPath(routePath, strokePaint)
            }

            // Intermediate turn waypoints
            for (i in 1 until s.routeSteps.lastIndex) {
                val wp = s.routeSteps[i]
                fillPaint.color = routeCol
                canvas.drawCircle(wxToSx(wp.x), wyToSy(wp.y), dp(3.4f), fillPaint)
            }

            // Gliding energy pulse dot heading from player to target along segments
            if (totalPxLen > dp(12f)) {
                val targetDistPx = pulse * totalPxLen
                var accum = 0f
                for (i in 0 until s.routeSteps.lastIndex) {
                    val ax = wxToSx(s.routeSteps[i].x)
                    val ay = wyToSy(s.routeSteps[i].y)
                    val bx = wxToSx(s.routeSteps[i + 1].x)
                    val by = wyToSy(s.routeSteps[i + 1].y)
                    val segLen = hypot((bx - ax).toDouble(), (by - ay).toDouble()).toFloat()
                    if (accum + segLen >= targetDistPx && segLen > 0.5f) {
                        val t = (targetDistPx - accum) / segLen
                        val dotX = ax + (bx - ax) * t
                        val dotY = ay + (by - ay) * t
                        fillPaint.color = Color.WHITE
                        canvas.drawCircle(dotX, dotY, dp(3.4f), fillPaint)
                        break
                    }
                    accum += segLen
                }
            }
        }

        // 5. Captured room markers
        for (sp in frameSpots) {
            val sx = wxToSx(sp.posX)
            val sy = wyToSy(sp.posY)
            val isTarget = s != null && s.spot.id == sp.id
            if (isTarget) {
                strokePaint.color = Color.argb(((1f - pulse) * 190f).toInt(), 16, 185, 129)
                strokePaint.strokeWidth = dp(2f)
                canvas.drawCircle(sx, sy, dp(7f) + pulse * dp(10f), strokePaint)
                strokePaint.color = GREEN
                strokePaint.strokeWidth = dp(2f)
                canvas.drawCircle(sx, sy, dp(8f), strokePaint)
            }
            fillPaint.color = if (isTarget) GREEN else BLUE
            canvas.drawCircle(sx, sy, if (isTarget) dp(5f) else dp(4f), fillPaint)
            drawHaloLabel(
                canvas,
                sp.roomLabel.take(10),
                sx,
                sy - dp(9f),
                dp(9f),
                if (isTarget) GREEN else textMain,
                bgCol
            )
        }

        // 6. Player chevron & ±32 deg FOV cone in real time (North-Up map)
        val px = wxToSx(st.posX)
        val py = wyToSy(st.posY)
        val coneCol = if (s != null && abs(wrap180(s.bearingDeg - displayedAzimuthDeg)) <= 12.0) GREEN else SKY
        val hdgRad = Math.toRadians(displayedAzimuthDeg - 90.0)
        val fovHalfRad = Math.toRadians(32.0)
        val fovR = dp(34f)
        fovPath.reset()
        fovPath.moveTo(px, py)
        fovPath.lineTo(px + fovR * cos(hdgRad - fovHalfRad).toFloat(), py + fovR * sin(hdgRad - fovHalfRad).toFloat())
        fovPath.lineTo(px + fovR * cos(hdgRad + fovHalfRad).toFloat(), py + fovR * sin(hdgRad + fovHalfRad).toFloat())
        fovPath.close()
        fillPaint.color = Color.argb(46, Color.red(coneCol), Color.green(coneCol), Color.blue(coneCol))
        canvas.drawPath(fovPath, fillPaint)

        canvas.save()
        canvas.translate(px, py)
        canvas.rotate(displayedAzimuthDeg.toFloat())
        playerPath.reset()
        playerPath.moveTo(0f, -dp(9f))
        playerPath.lineTo(dp(6.5f), dp(6.5f))
        playerPath.lineTo(0f, dp(3f))
        playerPath.lineTo(-dp(6.5f), dp(6.5f))
        playerPath.close()
        fillPaint.color = BLUE
        canvas.drawPath(playerPath, fillPaint)
        strokePaint.color = Color.WHITE
        strokePaint.strokeWidth = dp(1.6f)
        canvas.drawPath(playerPath, strokePaint)
        canvas.restore()

        // 7. HUD corner overlays
        val hudText = when {
            s != null && s.justRerouted -> "RECALCUL AUTO • ${graphSnap.nodes.size} nœuds"
            blockedCount > 0 -> "CARTE • ${graphSnap.nodes.size} nœuds • $blockedCount obst."
            else -> "CARTE • ${graphSnap.nodes.size} nœuds"
        }
        hudPaint.textAlign = Paint.Align.LEFT
        hudPaint.textSize = dp(9.5f)
        val badgeW = hudPaint.measureText(hudText) + dp(16f)
        badgeRect.set(mapRect.left + dp(8f), mapRect.top + dp(8f), mapRect.left + dp(8f) + badgeW, mapRect.top + dp(28f))
        fillPaint.color = hudBg
        canvas.drawRoundRect(badgeRect, dp(10f), dp(10f), fillPaint)
        strokePaint.color = if (s != null && s.justRerouted) ORANGE else borderCol
        strokePaint.strokeWidth = dp(1f)
        canvas.drawRoundRect(badgeRect, dp(10f), dp(10f), strokePaint)
        hudPaint.color = if (s != null && s.justRerouted) ORANGE else textMain
        canvas.drawText(hudText, badgeRect.left + dp(8f), badgeRect.centerY() + dp(3.5f), hudPaint)

        // North-up indicator top-right
        val nCx = mapRect.right - dp(20f)
        val nCy = mapRect.top + dp(20f)
        fillPaint.color = hudBg
        canvas.drawCircle(nCx, nCy, dp(11f), fillPaint)
        strokePaint.color = borderCol
        strokePaint.strokeWidth = dp(1f)
        canvas.drawCircle(nCx, nCy, dp(11f), strokePaint)
        hudPaint.textAlign = Paint.Align.CENTER
        hudPaint.textSize = dp(10f)
        hudPaint.color = RED
        canvas.drawText("N", nCx, nCy + dp(3.8f), hudPaint)

        // Dynamic metric scale bar bottom-left
        val scaleM = if (camHalfSpanM > 14.0) 5.0 else 2.0
        val scalePx = (scaleM * pxPerM).toFloat().coerceIn(dp(22f), dp(80f))
        val sbX = mapRect.left + dp(12f)
        val sbY = mapRect.bottom - dp(12f)
        strokePaint.color = textSub
        strokePaint.strokeWidth = dp(1.8f)
        canvas.drawLine(sbX, sbY, sbX + scalePx, sbY, strokePaint)
        canvas.drawLine(sbX, sbY - dp(3.5f), sbX, sbY, strokePaint)
        canvas.drawLine(sbX + scalePx, sbY - dp(3.5f), sbX + scalePx, sbY, strokePaint)
        hudPaint.textAlign = Paint.Align.LEFT
        hudPaint.color = textSub
        hudPaint.textSize = dp(9f)
        canvas.drawText("${scaleM.toInt()} m", sbX + scalePx + dp(5f), sbY + dp(1f), hudPaint)

        // Live signal tag bottom-right
        val dbmText = "${st.rsrpDbm} dBm"
        val dbmW = hudPaint.measureText(dbmText) + dp(12f)
        badgeRect.set(mapRect.right - dp(8f) - dbmW, mapRect.bottom - dp(26f), mapRect.right - dp(8f), mapRect.bottom - dp(8f))
        fillPaint.color = hudBg
        canvas.drawRoundRect(badgeRect, dp(8f), dp(8f), fillPaint)
        hudPaint.textAlign = Paint.Align.CENTER
        hudPaint.color = BLUE
        canvas.drawText(dbmText, badgeRect.centerX(), badgeRect.centerY() + dp(3.2f), hudPaint)

        canvas.restore()

        // Crisp outer frame stroke
        strokePaint.color = borderCol
        strokePaint.strokeWidth = dp(1.2f)
        canvas.drawRoundRect(mapRect, cornerR, cornerR, strokePaint)
    }
}

/**
 * Adaptive Radial Veracity & OS Data Counter Gauge (supports both SMS bundles & neutral 24h SIM monitor).
 */
class RadialVeracityGaugeView(
    context: Context,
    private val engine: NativeHardwareEngine
) : View(context) {

    private val density = resources.displayMetrics.density
    private fun dp(v: Float): Float = v * density

    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val arcRect = RectF()

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(w, dp(136f).roundToInt())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val session = engine.activeSession ?: return
        val dark = engine.settings.isDarkMode
        val isNeutral = session.isNeutralMonitor || session.promisedMb <= 0.0
        val rawPct = if (isNeutral) 100.0 else session.rawConsumedPct
        val arcPct = rawPct.coerceIn(0.0, 100.0)
        val accent = when {
            isNeutral -> Color.parseColor("#0284C7")
            session.isOverrun -> Color.parseColor("#F59E0B")
            session.isExhausted && rawPct < 85.0 -> Color.parseColor("#EF4444")
            rawPct >= 85.0 -> Color.parseColor("#10B981")
            else -> Color.parseColor("#0284C7")
        }

        val cx = dp(68f)
        val cy = height / 2f + dp(4f)
        val r = dp(48f)
        arcRect.set(cx - r, cy - r, cx + r, cy + r)

        arcPaint.strokeWidth = dp(10f)
        arcPaint.color = if (dark) Color.parseColor("#1E293B") else Color.parseColor("#E2E8F0")
        canvas.drawArc(arcRect, 135f, 270f, false, arcPaint)

        arcPaint.color = accent
        val sweep = if (isNeutral) 270f else (270f * (arcPct / 100.0)).toFloat().coerceAtLeast(6f)
        canvas.drawArc(arcRect, 135f, sweep, false, arcPaint)

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.color = if (dark) Color.parseColor("#94A3B8") else Color.parseColor("#64748B")
        textPaint.textSize = dp(9f)
        textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        canvas.drawText(if (isNeutral) "SONDE OS" else "MESURE OS", cx, cy - dp(10f), textPaint)

        textPaint.color = if (dark) Color.parseColor("#F8FAFC") else Color.parseColor("#0F172A")
        textPaint.textSize = dp(16f)
        canvas.drawText(
            if (isNeutral) "24h Live" else "${String.format("%.1f", rawPct)}%",
            cx,
            cy + dp(10f),
            textPaint
        )

        val rxX = dp(138f)
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.color = if (dark) Color.parseColor("#94A3B8") else Color.parseColor("#64748B")
        textPaint.textSize = dp(10f)
        textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        canvas.drawText("CONSOMMATION LOCALE ANDROID", rxX, cy - dp(26f), textPaint)

        textPaint.color = if (dark) Color.parseColor("#F8FAFC") else Color.parseColor("#0F172A")
        textPaint.textSize = dp(24f)
        canvas.drawText("${String.format("%.1f", session.measuredDecimalMb)} Mo", rxX, cy + dp(2f), textPaint)

        textPaint.color = if (dark) Color.parseColor("#CBD5E1") else Color.parseColor("#334155")
        textPaint.textSize = dp(12f)
        textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        val priceTxt = if (session.priceAmount > 0) " (${session.priceAmount} ${session.currencyLabel})" else ""
        canvas.drawText(
            if (isNeutral) {
                "Binaire : ${String.format("%.1f", session.measuredBinaryMib)} Mio (24h)"
            } else {
                val srcLabel = if (session.isManualAnchor) "Forfait manuel" else "Forfait SMS"
                "$srcLabel : ${session.promisedMb.roundToInt()} Mo$priceTxt"
            },
            rxX,
            cy + dp(22f),
            textPaint
        )

        textPaint.color = if (dark) Color.parseColor("#94A3B8") else Color.parseColor("#64748B")
        textPaint.textSize = dp(11f)
        textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        val footerLine = when {
            isNeutral -> "Rx ${String.format("%.1f", session.rxBytes / 1_000_000.0)} Mo • Tx ${String.format("%.1f", session.txBytes / 1_000_000.0)} Mo"
            session.isOverrun -> "Surplus OS : +${String.format("%.1f", session.overrunMb)} Mo"
            session.isExhausted -> "Écart épuisement : -${session.missingMb.roundToInt()} Mo"
            else -> "Reste estimé : ${String.format("%.1f", session.missingMb)} Mo"
        }
        canvas.drawText(footerLine, rxX, cy + dp(39f), textPaint)
    }
}
