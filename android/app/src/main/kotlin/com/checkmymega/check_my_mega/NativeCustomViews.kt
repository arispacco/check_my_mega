package com.checkmymega.check_my_mega

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.view.Choreographer
import android.view.View
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
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
 * 120 Hz Hardware-Accelerated Wayfinder Compass & Real-Time RF Oscilloscope.
 * Adapts automatically to both Light Mode (Blanc FinTech) and Dark Mode (Obsidienne OLED).
 */
class WayfinderCompassRadarView(
    context: Context,
    private val engine: NativeHardwareEngine
) : View(context), Choreographer.FrameCallback {

    private val density = resources.displayMetrics.density
    private fun dp(v: Float): Float = v * density

    private var isAttached = false
    private var displayedAzimuthDeg = 0.0

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    private val needlePath = Path()
    private val wavePath = Path()
    private val arcRect = RectF()
    private val oscRect = RectF()

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

    override fun doFrame(frameTimeNanos: Long) {
        if (!isAttached) return
        val target = engine.state.azimuthDeg
        var diff = target - displayedAzimuthDeg
        if (diff > 180.0) diff -= 360.0
        if (diff < -180.0) diff += 360.0
        displayedAzimuthDeg = (displayedAzimuthDeg + diff * 0.35 + 360.0) % 360.0

        invalidate()
        Choreographer.getInstance().postFrameCallback(this)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val desiredH = (min(w, dp(336f).roundToInt()) + dp(94f)).roundToInt()
        setMeasuredDimension(w, desiredH)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val st = engine.state
        val dark = engine.settings.isDarkMode
        val w = width.toFloat()
        val dialSize = min(w - dp(16f), dp(296f))
        val cx = w / 2f
        val cy = dialSize / 2f + dp(6f)
        val outerR = dialSize / 2f - dp(6f)

        val bestSpot = engine.bestSpot()
        val bestBearing = engine.bearingToBestSpotDeg()

        // 1. Dial Face (Light vs Dark)
        fillPaint.shader = RadialGradient(
            cx,
            cy,
            outerR,
            if (dark) {
                intArrayOf(
                    Color.parseColor("#0F172A"),
                    Color.parseColor("#1E293B"),
                    Color.parseColor("#090F1D")
                )
            } else {
                intArrayOf(
                    Color.parseColor("#FFFFFF"),
                    Color.parseColor("#F0F9FF"),
                    Color.parseColor("#E0F2FE")
                )
            },
            floatArrayOf(0.0f, 0.72f, 1.0f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, outerR, fillPaint)
        fillPaint.shader = null

        strokePaint.color = if (dark) Color.parseColor("#334155") else Color.parseColor("#BAE6FD")
        strokePaint.strokeWidth = dp(2.2f)
        canvas.drawCircle(cx, cy, outerR, strokePaint)

        strokePaint.color = if (dark) Color.parseColor("#1E293B") else Color.parseColor("#E2E8F0")
        strokePaint.strokeWidth = dp(1f)
        canvas.drawCircle(cx, cy, outerR * 0.72f, strokePaint)
        canvas.drawCircle(cx, cy, outerR * 0.46f, strokePaint)

        // 2. Rotating Compass Rose with Upright Degree Numerals
        canvas.save()
        canvas.rotate((-displayedAzimuthDeg).toFloat(), cx, cy)

        for (deg in 0 until 360 step 5) {
            val rad = Math.toRadians((deg - 90).toDouble())
            val isCardinal = deg % 90 == 0
            val isMajor = deg % 30 == 0
            val len = when {
                isCardinal -> dp(13f)
                isMajor -> dp(8f)
                else -> dp(4.5f)
            }
            val rStart = outerR - dp(4f)
            val rEnd = rStart - len
            val x1 = cx + rStart * cos(rad).toFloat()
            val y1 = cy + rStart * sin(rad).toFloat()
            val x2 = cx + rEnd * cos(rad).toFloat()
            val y2 = cy + rEnd * sin(rad).toFloat()

            strokePaint.strokeWidth = if (isCardinal) dp(2.4f) else if (isMajor) dp(1.4f) else dp(0.9f)
            strokePaint.color = when {
                deg == 0 -> Color.parseColor("#EF4444")
                isCardinal -> if (dark) Color.parseColor("#F8FAFC") else Color.parseColor("#0F172A")
                isMajor -> if (dark) Color.parseColor("#94A3B8") else Color.parseColor("#475569")
                else -> if (dark) Color.parseColor("#475569") else Color.parseColor("#94A3B8")
            }
            canvas.drawLine(x1, y1, x2, y2, strokePaint)

            if (isMajor && !isCardinal) {
                val rText = outerR - dp(22f)
                val tx = cx + rText * cos(rad).toFloat()
                val ty = cy + rText * sin(rad).toFloat()
                canvas.save()
                canvas.rotate(displayedAzimuthDeg.toFloat(), tx, ty)
                textPaint.color = if (dark) Color.parseColor("#94A3B8") else Color.parseColor("#64748B")
                textPaint.textSize = dp(9.5f)
                textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                canvas.drawText("$deg°", tx, ty + dp(3.5f), textPaint)
                canvas.restore()
            }
        }

        val cardCol = if (dark) Color.parseColor("#F8FAFC") else Color.parseColor("#0F172A")
        val cardinals = listOf(
            Triple(0, "N", Color.parseColor("#EF4444")),
            Triple(90, "E", cardCol),
            Triple(180, "S", cardCol),
            Triple(270, "O", cardCol)
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

        if (bestBearing != null) {
            arcRect.set(cx - outerR * 0.72f, cy - outerR * 0.72f, cx + outerR * 0.72f, cy + outerR * 0.72f)
            fillPaint.color = Color.argb(42, 16, 185, 129)
            canvas.drawArc(arcRect, (bestBearing - 90.0 - 18.0).toFloat(), 36f, true, fillPaint)
        }

        for (spot in engine.radarSpots) {
            val isBest = bestSpot?.id == spot.id
            val dx = spot.posX - st.posX
            val dy = spot.posY - st.posY
            val distM = sqrt(dx * dx + dy * dy)
            val spotBrg = if (distM > 0.25) {
                (Math.toDegrees(atan2(dx, dy)) + 360.0) % 360.0
            } else {
                spot.azimuthDeg
            }
            val rad = Math.toRadians(spotBrg - 90.0)
            val normDist = (distM / 6.0).coerceIn(0.25, 0.85).toFloat()
            val rNode = outerR * 0.68f * normDist
            val nx = cx + rNode * cos(rad).toFloat()
            val ny = cy + rNode * sin(rad).toFloat()

            fillPaint.color = if (isBest) Color.parseColor("#10B981") else Color.parseColor("#0284C7")
            canvas.drawCircle(nx, ny, if (isBest) dp(6.5f) else dp(4.5f), fillPaint)
        }

        canvas.restore()

        // 3. Sculpted Wayfinder Arrow
        val targetRelDeg = if (bestBearing != null) {
            ((bestBearing - displayedAzimuthDeg + 540.0) % 360.0) - 180.0
        } else {
            ((-displayedAzimuthDeg + 540.0) % 360.0) - 180.0
        }
        val isLocked = abs(targetRelDeg) <= 15.0
        val arrowColor = if (bestBearing != null) {
            if (isLocked) Color.parseColor("#10B981") else Color.parseColor("#0284C7")
        } else {
            Color.parseColor("#0284C7")
        }

        canvas.save()
        canvas.rotate(targetRelDeg.toFloat(), cx, cy)
        val tipY = cy - outerR + dp(34f)
        val hubOuter = outerR * 0.36f
        needlePath.reset()
        needlePath.moveTo(cx, tipY)
        needlePath.lineTo(cx + dp(8.5f), cy - hubOuter + dp(6f))
        needlePath.lineTo(cx, cy - hubOuter - dp(3f))
        needlePath.lineTo(cx - dp(8.5f), cy - hubOuter + dp(6f))
        needlePath.close()
        fillPaint.color = arrowColor
        canvas.drawPath(needlePath, fillPaint)
        canvas.restore()

        // 4. Center Telemetry Hub
        val hubR = outerR * 0.35f
        fillPaint.color = if (dark) Color.parseColor("#0F172A") else Color.WHITE
        canvas.drawCircle(cx, cy, hubR, fillPaint)

        strokePaint.color = arrowColor
        strokePaint.strokeWidth = dp(2.2f)
        canvas.drawCircle(cx, cy, hubR, strokePaint)

        textPaint.color = Color.parseColor("#0284C7")
        textPaint.textSize = dp(10.5f)
        textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        canvas.drawText(st.cardinalLabel, cx, cy - dp(16f), textPaint)

        textPaint.color = if (dark) Color.parseColor("#F8FAFC") else Color.parseColor("#0F172A")
        textPaint.textSize = dp(22f)
        canvas.drawText("${displayedAzimuthDeg.roundToInt()}°", cx, cy + dp(5f), textPaint)

        textPaint.color = if (dark) Color.parseColor("#94A3B8") else Color.parseColor("#475569")
        textPaint.textSize = dp(9.5f)
        textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        canvas.drawText("${st.rsrpDbm} dBm • SINR ${st.sinrDb} dB", cx, cy + dp(20f), textPaint)

        // Top lubber triangle
        needlePath.reset()
        needlePath.moveTo(cx, cy - outerR + dp(2f))
        needlePath.lineTo(cx - dp(6f), cy - outerR - dp(7f))
        needlePath.lineTo(cx + dp(6f), cy - outerR - dp(7f))
        needlePath.close()
        fillPaint.color = Color.parseColor("#0284C7")
        canvas.drawPath(needlePath, fillPaint)

        // 5. Live RSRP Oscilloscope Strip
        val oscTop = cy + outerR + dp(14f)
        val oscBottom = height.toFloat() - dp(4f)
        val oscLeft = dp(8f)
        val oscRight = w - dp(8f)
        oscRect.set(oscLeft, oscTop, oscRight, oscBottom)

        fillPaint.color = if (dark) Color.parseColor("#0F172A") else Color.parseColor("#F8FAFC")
        canvas.drawRoundRect(oscRect, dp(12f), dp(12f), fillPaint)
        strokePaint.color = if (dark) Color.parseColor("#1E293B") else Color.parseColor("#E2E8F0")
        strokePaint.strokeWidth = dp(1f)
        canvas.drawRoundRect(oscRect, dp(12f), dp(12f), strokePaint)

        textPaint.textAlign = Paint.Align.LEFT
        textPaint.color = if (dark) Color.parseColor("#94A3B8") else Color.parseColor("#475569")
        textPaint.textSize = dp(9.5f)
        textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        canvas.drawText(
            "Signal 4G SIM en direct (${st.rsrpDbm} dBm • ${st.signalQualityLabel})",
            oscLeft + dp(10f),
            oscTop + dp(15f),
            textPaint
        )
        textPaint.textAlign = Paint.Align.CENTER

        val graphTop = oscTop + dp(22f)
        val graphBottom = oscBottom - dp(7f)
        val graphLeft = oscLeft + dp(10f)
        val graphRight = oscRight - dp(10f)
        val count = st.rsrpTrace.size
        val head = st.rsrpTraceIndex

        wavePath.reset()
        for (i in 0 until count) {
            val sampleIdx = (head + 1 + i) % count
            val dbm = st.rsrpTrace[sampleIdx]
            val norm = ((dbm + 125f) / 65f).coerceIn(0f, 1f)
            val px = graphLeft + (i.toFloat() / (count - 1).toFloat()) * (graphRight - graphLeft)
            val py = graphBottom - norm * (graphBottom - graphTop)
            if (i == 0) wavePath.moveTo(px, py) else wavePath.lineTo(px, py)
        }
        strokePaint.color = Color.parseColor("#0284C7")
        strokePaint.strokeWidth = dp(2.2f)
        canvas.drawPath(wavePath, strokePaint)
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
        val pct = if (isNeutral) 100.0 else session.veracityScorePct.coerceIn(0.0, 100.0)
        val accent = when {
            isNeutral -> Color.parseColor("#0284C7")
            pct >= 90.0 -> Color.parseColor("#10B981")
            pct >= 70.0 -> Color.parseColor("#0284C7")
            else -> Color.parseColor("#F59E0B")
        }

        val cx = dp(68f)
        val cy = height / 2f + dp(4f)
        val r = dp(48f)
        arcRect.set(cx - r, cy - r, cx + r, cy + r)

        arcPaint.strokeWidth = dp(10f)
        arcPaint.color = if (dark) Color.parseColor("#1E293B") else Color.parseColor("#E2E8F0")
        canvas.drawArc(arcRect, 135f, 270f, false, arcPaint)

        arcPaint.color = accent
        val sweep = if (isNeutral) 270f else (270f * (pct / 100.0)).toFloat().coerceAtLeast(6f)
        canvas.drawArc(arcRect, 135f, sweep, false, arcPaint)

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.color = if (dark) Color.parseColor("#94A3B8") else Color.parseColor("#64748B")
        textPaint.textSize = dp(9f)
        textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        canvas.drawText(if (isNeutral) "SONDE SIM" else "REÇU OS", cx, cy - dp(10f), textPaint)

        textPaint.color = if (dark) Color.parseColor("#F8FAFC") else Color.parseColor("#0F172A")
        textPaint.textSize = dp(16.5f)
        canvas.drawText(
            if (isNeutral) "24h Live" else "${String.format("%.1f", pct)}%",
            cx,
            cy + dp(10f),
            textPaint
        )

        val rxX = dp(138f)
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.color = if (dark) Color.parseColor("#94A3B8") else Color.parseColor("#64748B")
        textPaint.textSize = dp(10.5f)
        textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        canvas.drawText("VOLUME RÉEL MESURÉ PAR ANDROID", rxX, cy - dp(26f), textPaint)

        textPaint.color = if (dark) Color.parseColor("#F8FAFC") else Color.parseColor("#0F172A")
        textPaint.textSize = dp(25f)
        canvas.drawText("${String.format("%.1f", session.measuredDecimalMb)} Mo", rxX, cy + dp(2f), textPaint)

        textPaint.color = if (dark) Color.parseColor("#CBD5E1") else Color.parseColor("#334155")
        textPaint.textSize = dp(12f)
        textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        canvas.drawText(
            if (isNeutral) {
                "Binaire : ${String.format("%.1f", session.measuredBinaryMib)} Mio (24h)"
            } else {
                "Forfait SMS : ${session.promisedMb.roundToInt()} Mo (${session.priceAmount} ${session.currencyLabel})"
            },
            rxX,
            cy + dp(22f),
            textPaint
        )

        textPaint.color = if (dark) Color.parseColor("#94A3B8") else Color.parseColor("#64748B")
        textPaint.textSize = dp(11f)
        textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        canvas.drawText(
            if (isNeutral) {
                "Rx ${String.format("%.1f", session.rxBytes / 1_000_000.0)} Mo • Tx ${String.format("%.1f", session.txBytes / 1_000_000.0)} Mo"
            } else {
                "Coût réel : ${session.realCostPerGb.roundToInt()} ${session.currencyLabel} / Go"
            },
            rxX,
            cy + dp(39f),
            textPaint
        )
    }
}
