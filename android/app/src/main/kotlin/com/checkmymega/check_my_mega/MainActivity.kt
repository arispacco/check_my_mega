package com.checkmymega.check_my_mega

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 100% Native Kotlin Activity — Adaptive Light (White FinTech) & Dark (Obsidian OLED) Design System.
 * Zero emojis: uses custom geometric VectorIconView, OperatorBrandLogoView, and AppIconOrBrandView.
 */
class MainActivity : Activity() {

    private lateinit var engine: NativeHardwareEngine

    private var selectedTabIndex: Int = 0
    private var selectedRoomLabel: String = "Salon"
    private var showHomeMoreInfo: Boolean = false
    private var showCompassMoreInfo: Boolean = false
    private var showAiMoreInfo: Boolean = false
    private var showAllSmsItems: Boolean = false

    private lateinit var rootLayout: LinearLayout
    private lateinit var navBarLayout: LinearLayout
    private lateinit var navDividerView: View
    private lateinit var headerSubtitleTv: TextView
    private lateinit var headerBadgeTv: TextView
    private lateinit var headerThemeBtn: VectorIconView
    private lateinit var tabContainers: Array<ScrollView>
    private lateinit var tabContentLayouts: Array<LinearLayout>
    private lateinit var navButtons: Array<LinearLayout>

    // Live fast-updated views
    private var compassRadarView: WayfinderCompassRadarView? = null
    private var radialGaugeView: RadialVeracityGaugeView? = null
    private var liveHudCarrierTv: TextView? = null
    private var liveHudBandTv: TextView? = null
    private var liveHudDistTv: TextView? = null
    private var liveGuidanceBannerTv: TextView? = null
    private var liveBentoRsrpValTv: TextView? = null
    private var liveBentoRsrpSubTv: TextView? = null
    private var liveBentoSinrValTv: TextView? = null
    private var liveBentoSinrSubTv: TextView? = null
    private var liveBentoSpeedValTv: TextView? = null
    private var liveBentoSpeedSubTv: TextView? = null
    private var liveBentoLocValTv: TextView? = null
    private var liveBentoLocSubTv: TextView? = null

    private var lastFullDomRefreshMs: Long = 0L
    private var wasPausedInBackground: Boolean = false
    private var lastManualPermRequestMs: Long = 0L

    // Theme Palette Helpers (Light Mode vs Dark Mode)
    private val isDark: Boolean get() = engine.settings.isDarkMode
    private val pageBgHex: String get() = if (isDark) "#0B1120" else "#F8FAFC"
    private val cardBgHex: String get() = if (isDark) "#111C33" else "#FFFFFF"
    private val subtleBgHex: String get() = if (isDark) "#0F172A" else "#F8FAFC"
    private val borderHex: String get() = if (isDark) "#1E293B" else "#E2E8F0"
    private val textPrimaryCol: Int get() = Color.parseColor(if (isDark) "#F8FAFC" else "#0F172A")
    private val textSecondaryCol: Int get() = Color.parseColor(if (isDark) "#94A3B8" else "#64748B")
    private val accentBlueCol: Int get() = Color.parseColor("#0284C7")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        enable120HzDisplayMode()

        engine = NativeHardwareEngine(this) {
            onEngineTelemetryTick()
        }
        if (engine.customRooms.isNotEmpty()) {
            selectedRoomLabel = engine.customRooms.first()
        }

        setContentView(buildRootShell())
        applyWindowSystemColors()
        requestAllPermissionsIfNeeded()
        engine.start()
        rebuildAllTabs()
    }

    override fun onPause() {
        wasPausedInBackground = true
        engine.onPauseLifecycle()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        engine.onResumeLifecycle()
        if (wasPausedInBackground) {
            wasPausedInBackground = false
            engine.onPermissionsUpdated()
            if (::rootLayout.isInitialized) {
                rebuildAllTabs()
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        engine.onPermissionsUpdated()
        if (::rootLayout.isInitialized) {
            rebuildAllTabs()
        }
        if (requestCode == 4202) {
            val missing = missingCriticalRuntimePermissions()
            if (missing.isNotEmpty()) {
                val elapsed = System.currentTimeMillis() - lastManualPermRequestMs
                val canShowDialog = missing.any { shouldShowRequestPermissionRationale(it) }
                if (elapsed < 450L || !canShowDialog) {
                    openAppDetailsSettings(showRestrictedHint = true)
                }
            }
        }
    }

    override fun onDestroy() {
        engine.stop()
        super.onDestroy()
    }

    private fun applyWindowSystemColors() {
        @Suppress("DEPRECATION")
        window.statusBarColor = Color.parseColor(if (isDark) "#070D19" else "#0891B2")
        @Suppress("DEPRECATION")
        window.navigationBarColor = Color.parseColor(if (isDark) "#0F172A" else "#FFFFFF")
        if (::rootLayout.isInitialized) {
            rootLayout.setBackgroundColor(Color.parseColor(pageBgHex))
            navBarLayout.setBackgroundColor(Color.parseColor(if (isDark) "#0F172A" else "#FFFFFF"))
            navDividerView.setBackgroundColor(Color.parseColor(borderHex))
            headerThemeBtn.iconType = if (isDark) VectorIconType.SUN else VectorIconType.MOON
            headerThemeBtn.invalidate()
        }
    }

    private fun enable120HzDisplayMode() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                display?.supportedModes?.maxByOrNull { it.refreshRate }?.let { bestMode ->
                    val params = window.attributes
                    params.preferredDisplayModeId = bestMode.modeId
                    params.preferredRefreshRate = bestMode.refreshRate
                    window.attributes = params
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                @Suppress("DEPRECATION")
                windowManager?.defaultDisplay?.supportedModes?.maxByOrNull { it.refreshRate }?.let { bestMode ->
                    val params = window.attributes
                    params.preferredDisplayModeId = bestMode.modeId
                    window.attributes = params
                }
            }
        } catch (_: Exception) {}
    }

    private fun allRuntimePermissionsList(): List<String> {
        val perms = mutableListOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.READ_SMS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            perms.add(Manifest.permission.ACTIVITY_RECOGNITION)
        }
        return perms
    }

    private fun missingCriticalRuntimePermissions(): List<String> {
        return allRuntimePermissionsList().filter {
            checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }
    }

    private fun hasReadSmsPermission(): Boolean =
        checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

    private fun hasPhoneStatePermission(): Boolean =
        checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED

    private fun hasLocationPermission(): Boolean =
        checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun hasAnyMissingSetupPermission(): Boolean =
        !engine.hasUsageStatsPermission() ||
            !hasReadSmsPermission() ||
            !hasPhoneStatePermission() ||
            !hasLocationPermission()

    private fun requestAllPermissionsIfNeeded(requestCode: Int = 4201) {
        val missing = missingCriticalRuntimePermissions()
        if (missing.isNotEmpty()) {
            if (requestCode == 4202) {
                lastManualPermRequestMs = System.currentTimeMillis()
            }
            requestPermissions(missing.toTypedArray(), requestCode)
        }
    }

    private fun openUsageAccessSettingsSafe() {
        val withPackage = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            data = Uri.parse("package:$packageName")
        }
        try {
            startActivity(withPackage)
            Toast.makeText(
                this,
                "Si l'interrupteur est grisé, passez d'abord par « Infos appli > ⋮ > Autoriser les paramètres restreints ».",
                Toast.LENGTH_LONG
            ).show()
        } catch (_: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            } catch (_: Exception) {
                openAppDetailsSettings(showRestrictedHint = true)
            }
        }
    }

    private fun openAppDetailsSettings(showRestrictedHint: Boolean = true) {
        if (showRestrictedHint) {
            Toast.makeText(
                this,
                "Appuyez sur ⋮ en haut à droite (ou en bas sur Xiaomi) > « Autoriser les paramètres restreints », puis activez SMS et Données d'utilisation.",
                Toast.LENGTH_LONG
            ).show()
        }
        try {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
            })
        } catch (_: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS))
            } catch (_: Exception) {}
        }
    }

    private fun dp(v: Float): Int = (v * resources.displayMetrics.density).roundToInt()

    private fun isWifiCurrentlyActive(): Boolean {
        return try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val caps = cm?.getNetworkCapabilities(cm.activeNetwork)
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        } catch (_: Exception) {
            false
        }
    }

    private fun cardDrawable(
        bgHex: String = cardBgHex,
        bHex: String = borderHex,
        cornerDp: Float = 18f,
        strokeDp: Float = 1f
    ): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(cornerDp).toFloat()
            setColor(Color.parseColor(bgHex))
            setStroke(dp(strokeDp), Color.parseColor(bHex))
        }
    }

    private fun pillDrawable(
        fillHex: String,
        strokeHex: String,
        cornerDp: Float = 20f
    ): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(cornerDp).toFloat()
            setColor(Color.parseColor(fillHex))
            setStroke(dp(1f), Color.parseColor(strokeHex))
        }
    }

    private fun buildRootShell(): View {
        rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor(pageBgHex))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val statusBarResId = resources.getIdentifier("status_bar_height", "dimen", "android")
        val statusBarPx = if (statusBarResId > 0) resources.getDimensionPixelSize(statusBarResId) else dp(38f)

        val headerBg = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(Color.parseColor("#06B6D4"), Color.parseColor("#0284C7"))
        )
        val appBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16f), statusBarPx + dp(10f), dp(16f), dp(14f))
            background = headerBg
        }

        // Vector Menu Icon (opens quick navigation / settings)
        val menuBtn = VectorIconView(this, VectorIconType.MENU, Color.WHITE, 24f).apply {
            setOnClickListener { selectTab(3) }
        }
        appBar.addView(menuBtn)

        val titleCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14f), 0, dp(8f), 0)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val titleTv = TextView(this).apply {
            text = "CheckMyMega"
            setTextColor(Color.WHITE)
            textSize = 18.5f
            setTypeface(typeface, Typeface.BOLD)
        }
        headerSubtitleTv = TextView(this).apply {
            text = "SIM : ${engine.state.operatorName}"
            setTextColor(Color.parseColor("#E0F2FE"))
            textSize = 11.5f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        titleCol.addView(titleTv)
        titleCol.addView(headerSubtitleTv)
        appBar.addView(titleCol)

        headerBadgeTv = TextView(this).apply {
            text = "4G • ${engine.state.rsrpDbm} dBm"
            setTextColor(Color.parseColor("#0369A1"))
            textSize = 11f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(10f), dp(5f), dp(10f), dp(5f))
            background = pillDrawable("#FFFFFF", "#BAE6FD", 16f)
        }
        appBar.addView(headerBadgeTv)

        // Quick Dark / Light Mode Toggle Icon in Header
        headerThemeBtn = VectorIconView(
            this,
            if (isDark) VectorIconType.SUN else VectorIconType.MOON,
            Color.WHITE,
            22f
        ).apply {
            layoutParams = LinearLayout.LayoutParams(dp(34f), dp(34f)).apply { leftMargin = dp(10f) }
            setOnClickListener {
                engine.settings.isDarkMode = !engine.settings.isDarkMode
                engine.saveSettings()
                applyWindowSystemColors()
                updateNavBarSelection()
                rebuildAllTabs()
            }
        }
        appBar.addView(headerThemeBtn)
        rootLayout.addView(appBar)

        val contentFrame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }

        tabContentLayouts = Array(4) {
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(18f), dp(18f), dp(18f), dp(48f))
            }
        }
        tabContainers = Array(4) { idx ->
            ScrollView(this).apply {
                isFillViewport = true
                isVerticalScrollBarEnabled = false
                visibility = if (idx == selectedTabIndex) View.VISIBLE else View.GONE
                addView(tabContentLayouts[idx])
            }
        }
        for (sc in tabContainers) contentFrame.addView(sc)
        rootLayout.addView(contentFrame)

        navDividerView = View(this).apply {
            setBackgroundColor(Color.parseColor(borderHex))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1f))
        }
        rootLayout.addView(navDividerView)

        navBarLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor(if (isDark) "#0F172A" else "#FFFFFF"))
            setPadding(dp(8f), dp(8f), dp(8f), dp(22f))
        }
        navButtons = Array(4) { idx ->
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                setPadding(dp(4f), dp(6f), dp(4f), dp(6f))
                setOnClickListener { selectTab(idx) }
            }
        }
        for (i in 0..3) navBarLayout.addView(navButtons[i])
        rootLayout.addView(navBarLayout)
        updateNavBarSelection()

        return rootLayout
    }

    private fun selectTab(idx: Int) {
        selectedTabIndex = idx.coerceIn(0, 3)
        for (i in 0..3) {
            tabContainers[i].visibility = if (i == selectedTabIndex) View.VISIBLE else View.GONE
        }
        updateNavBarSelection()
        rebuildAllTabs()
    }

    private fun updateNavBarSelection() {
        val navSpec = listOf(
            VectorIconType.HOME to "Accueil & Apps",
            VectorIconType.COMPASS to "Boussole 120Hz",
            VectorIconType.AI_SPARK to "Audit IA Gemma",
            VectorIconType.SETTINGS to "Paramètres"
        )
        for (i in navSpec.indices) {
            val btn = navButtons[i]
            btn.removeAllViews()
            val selected = i == selectedTabIndex
            val activeCol = Color.parseColor("#0284C7")
            val inactiveCol = Color.parseColor(if (isDark) "#94A3B8" else "#64748B")

            val iconHolder = LinearLayout(this).apply {
                gravity = Gravity.CENTER
                setPadding(dp(16f), dp(4f), dp(16f), dp(4f))
                if (selected) {
                    background = pillDrawable(
                        if (isDark) "#1E293B" else "#E0F2FE",
                        if (isDark) "#0284C7" else "#7DD3FC",
                        16f
                    )
                }
                addView(VectorIconView(this@MainActivity, navSpec[i].first, if (selected) activeCol else inactiveCol, 20f))
            }
            val label = TextView(this).apply {
                text = navSpec[i].second
                textSize = 11f
                setPadding(0, dp(4f), 0, 0)
                setTextColor(if (selected) activeCol else inactiveCol)
                setTypeface(typeface, if (selected) Typeface.BOLD else Typeface.NORMAL)
            }
            btn.addView(iconHolder)
            btn.addView(label)
        }
    }

    private fun showAddCustomRoomDialog() {
        val input = EditText(this).apply {
            hint = "Ex: Cuisine, Coin Lit, Terrasse, Couloir..."
            setTextColor(Color.parseColor("#0F172A"))
            setHintTextColor(Color.parseColor("#94A3B8"))
            textSize = 15f
            setPadding(dp(18f), dp(14f), dp(18f), dp(14f))
        }
        val wrapper = FrameLayout(this).apply {
            setPadding(dp(20f), dp(10f), dp(20f), dp(4f))
            addView(input)
        }
        @Suppress("DEPRECATION")
        AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_LIGHT)
            .setTitle("Ajouter un coin de la maison")
            .setMessage("Entrez le nom de la pièce ou de l'endroit exact à tester :")
            .setView(wrapper)
            .setPositiveButton("Ajouter") { _, _ ->
                val name = input.text?.toString()?.trim().orEmpty()
                if (name.isNotEmpty()) {
                    engine.addCustomRoom(name)
                    selectedRoomLabel = name
                    rebuildAllTabs()
                    Toast.makeText(this, "Coin « $name » ajouté et sauvegardé !", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun onEngineTelemetryTick() {
        val st = engine.state
        headerSubtitleTv.text = "SIM : ${st.operatorName} • ${st.rsrpDbm} dBm"
        headerBadgeTv.text = "${st.networkType} • ${st.rsrpDbm} dBm"

        radialGaugeView?.invalidate()
        compassRadarView?.invalidate()

        liveHudCarrierTv?.text = "SIM : ${st.operatorName}"
        liveHudBandTv?.text = st.bandLabel
        liveHudDistTv?.text = if (st.estimatedDistanceMeters > 0) {
            "Antenne ~${st.estimatedDistanceMeters}m"
        } else {
            "Cell ID ${st.cellId}"
        }

        liveBentoRsrpValTv?.text = "${st.rsrpDbm} dBm"
        liveBentoRsrpSubTv?.text = st.signalQualityLabel

        liveBentoSinrValTv?.text = "${st.sinrDb} dB"
        liveBentoSinrSubTv?.text = "RSRQ ${st.rsrqDb} dB • CQI ${st.cqi}/15"

        val displayMbps = if (st.lastActiveSpeedMbps > 0.05) st.lastActiveSpeedMbps else st.passiveRxMbps
        liveBentoSpeedValTv?.text = "${String.format("%.2f", displayMbps)} Mbps"
        liveBentoSpeedSubTv?.text = st.lastSpeedTestTransportLabel

        liveBentoLocValTv?.text = "Hauteur Z : ${String.format("%+.1f", st.relativeAltitudeMeters)}m"
        liveBentoLocSubTv?.text = "${st.stepCount} pas • ${st.pressureHpa.roundToInt()} hPa"

        liveGuidanceBannerTv?.text = guidanceText()

        val now = System.currentTimeMillis()
        val needsInitialSessionRender = (engine.activeSession != null && radialGaugeView == null)
        if (needsInitialSessionRender || now - lastFullDomRefreshMs > 3500L) {
            lastFullDomRefreshMs = now
            if (needsInitialSessionRender || (selectedTabIndex != 1 && selectedTabIndex != 3)) {
                rebuildAllTabs()
            }
        }
    }

    /** Plain-language instruction that mirrors what the compass arrow shows. */
    private fun guidanceText(): String {
        val snap = engine.navSnapshot()
        if (snap == null) {
            return if (engine.radarSpots.isEmpty()) {
                "Choisissez un coin ci-dessous puis appuyez sur « Capturer ». La flèche vous y ramènera ensuite."
            } else {
                "Ces points datent d'une autre session. Placez-vous sur l'un d'eux et appuyez sur « Je suis ici » pour recaler la boussole."
            }
        }
        val spot = snap.spot
        val meta = "${spot.rsrpDbm} dBm • ~${String.format("%.1f", spot.speedMbps)} Mbps"
        val dist = String.format("%.1f", snap.distanceM)
        val rel = snap.relTurnDeg
        return when {
            snap.arrived -> "Vous êtes arrivé à « ${spot.roomLabel} » ($meta). Posez votre téléphone ici."
            abs(rel) > 135.0 -> "Faites demi-tour — « ${spot.roomLabel} » est à $dist m derrière vous."
            abs(rel) <= 12.0 -> "Tout droit : « ${spot.roomLabel} » à $dist m (≈ ${snap.approxSteps} pas) • $meta"
            rel > 0 -> "Tournez à droite de ${abs(rel).roundToInt()}° — « ${spot.roomLabel} » à $dist m • $meta"
            else -> "Tournez à gauche de ${abs(rel).roundToInt()}° — « ${spot.roomLabel} » à $dist m • $meta"
        }
    }

    private fun buildMiniPill(label: String, filled: Boolean, accentHex: String, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            text = label
            gravity = Gravity.CENTER
            textSize = 11.5f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(if (filled) Color.WHITE else Color.parseColor(accentHex))
            setPadding(dp(12f), dp(6f), dp(12f), dp(6f))
            background = if (filled) {
                pillDrawable(accentHex, accentHex, 16f)
            } else {
                pillDrawable(if (isDark) "#0F172A" else "#FFFFFF", accentHex, 16f)
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { rightMargin = dp(8f) }
            setOnClickListener { onClick() }
        }
    }

    private fun rebuildAllTabs() {
        lastFullDomRefreshMs = System.currentTimeMillis()
        buildTab0HomeAndApps(tabContentLayouts[0])
        buildTab1CompassAndSpots(tabContentLayouts[1])
        buildTab2AiAuditGemma(tabContentLayouts[2])
        buildTab3Settings(tabContentLayouts[3])
    }

    // =========================================================================
    // TAB 0: ACCUEIL ÉPURÉ + DÉTAILS EXPANSIBLES
    // =========================================================================
    private fun buildTab0HomeAndApps(container: LinearLayout) {
        container.removeAllViews()
        val session = engine.activeSession
        val st = engine.state
        val activeIsp = IspBrand.fromText("${st.simOperatorCode} ${st.operatorName}")

        // Slim setup notice only when critical permissions are missing and details are collapsed
        if (hasAnyMissingSetupPermission() && !showHomeMoreInfo) {
            container.addView(
                buildIconActionButton(
                    VectorIconType.SHIELD_CHECK,
                    "Autorisations requises • Touchez pour configurer",
                    if (isDark) "#2A1F0A" else "#FFFBEB",
                    "#D97706",
                    "#F59E0B"
                ) {
                    showHomeMoreInfo = true
                    rebuildAllTabs()
                }
            )
            container.addView(spaceView(14f))
        }

        // Primary focus: ONLY global data consumption card
        if (session != null) {
            val gaugeCard = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(18f), dp(18f), dp(18f), dp(18f))
                background = cardDrawable()
            }

            val topRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            topRow.addView(OperatorBrandLogoView(this, session.isp.logoKey, 40f))
            val topCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12f), 0, 0, 0)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            topCol.addView(TextView(this).apply {
                text = session.planName
                setTextColor(textPrimaryCol)
                textSize = 16f
                setTypeface(typeface, Typeface.BOLD)
            })
            topCol.addView(TextView(this).apply {
                text = "${activeIsp.shortName} • ${session.workloadTag}"
                setTextColor(textSecondaryCol)
                textSize = 12f
            })
            topRow.addView(topCol)
            gaugeCard.addView(topRow)
            gaugeCard.addView(spaceView(10f))

            val gView = radialGaugeView ?: RadialVeracityGaugeView(this, engine).also {
                radialGaugeView = it
            }
            (gView.parent as? ViewGroup)?.removeView(gView)
            gaugeCard.addView(gView)

            if (session.includesPreActivationBucket) {
                gaugeCard.addView(spaceView(6f))
                gaugeCard.addView(TextView(this).apply {
                    text = "Android regroupe l'historique OS par tranches de 2h : le trafic juste avant le SMS dans cette tranche est inclus."
                    setTextColor(Color.parseColor("#D97706"))
                    textSize = 11.5f
                })
            }

            gaugeCard.addView(spaceView(14f))
            gaugeCard.addView(
                buildMoreInfoToggleButton(showHomeMoreInfo) {
                    showHomeMoreInfo = !showHomeMoreInfo
                    rebuildAllTabs()
                }
            )
            container.addView(gaugeCard)
        }

        if (showHomeMoreInfo) {
            container.addView(spaceView(16f))

            val quickActionsCard = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16f), dp(16f), dp(16f), dp(16f))
                background = cardDrawable()
            }
            val btnRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            btnRow.addView(
                buildIconActionButton(
                    VectorIconType.REFRESH,
                    "Actualiser SMS",
                    if (isDark) "#1E293B" else "#F0F9FF",
                    "#0284C7",
                    if (isDark) "#334155" else "#BAE6FD"
                ) {
                    engine.syncSmsAndBuildSessions()
                }.apply {
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                        rightMargin = dp(8f)
                    }
                }
            )
            btnRow.addView(
                buildIconActionButton(
                    VectorIconType.AI_SPARK,
                    "Audit IA Gemma",
                    "#0284C7",
                    "#FFFFFF",
                    "#0284C7"
                ) {
                    selectTab(2)
                    engine.runGemmaAiAudit()
                }.apply {
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }
            )
            quickActionsCard.addView(btnRow)
            quickActionsCard.addView(spaceView(10f))
            quickActionsCard.addView(
                buildIconActionButton(
                    VectorIconType.BOLT,
                    "Définir un forfait manuel / Remettre à 0 Mo",
                    subtleBgHex,
                    if (isDark) "#F8FAFC" else "#0F172A",
                    borderHex
                ) {
                    showManualBundleDialog()
                }
            )
            if (engine.manualStartMs > 0L) {
                quickActionsCard.addView(spaceView(8f))
                quickActionsCard.addView(
                    buildIconActionButton(
                        VectorIconType.REFRESH,
                        "Revenir à la détection SMS automatique",
                        subtleBgHex,
                        "#EF4444",
                        borderHex
                    ) {
                        engine.clearManualBundle()
                        rebuildAllTabs()
                    }
                )
            }
            container.addView(quickActionsCard)
            container.addView(spaceView(16f))

            if (hasAnyMissingSetupPermission()) {
                container.addView(buildPermissionsDiagnosticCard(showAllGrantedNotice = false))
                container.addView(spaceView(16f))
            }

            container.addView(buildHomeSimCard(activeIsp, st))
            container.addView(spaceView(16f))
            container.addView(buildHomeAppsCard(session))
            container.addView(spaceView(16f))
            container.addView(buildHomeSmsCard())
        }
    }

    private fun buildHomeSimCard(activeIsp: IspBrand, st: LiveRadioAndSensorState): LinearLayout {
        val simCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16f), dp(14f), dp(16f), dp(14f))
            background = cardDrawable(cardBgHex, if (isDark) "#0284C7" else "#BAE6FD", 18f, 1.2f)
        }
        simCard.addView(OperatorBrandLogoView(this, activeIsp.logoKey, 46f))

        val simCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14f), 0, dp(8f), 0)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        simCol.addView(TextView(this).apply {
            text = "SIM active : ${st.operatorName}"
            setTextColor(textPrimaryCol)
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
        })
        val netRouteTxt = if (isWifiCurrentlyActive()) {
            "Signal 4G SIM (${st.rsrpDbm} dBm) • Internet sur Wi-Fi"
        } else {
            "Signal 4G SIM (${st.rsrpDbm} dBm) • Données mobiles SIM"
        }
        simCol.addView(TextView(this).apply {
            text = "$netRouteTxt • Pays ${st.simCountryIso.uppercase()} (${st.currencyLabel})"
            setTextColor(textSecondaryCol)
            textSize = 12f
        })
        simCard.addView(simCol)
        return simCard
    }

    private fun buildHomeAppsCard(session: BundleAuditSession?): LinearLayout {
        val appsCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18f), dp(18f), dp(18f), dp(18f))
            background = cardDrawable()
        }
        appsCard.addView(TextView(this).apply {
            text = "Consommation par application"
            setTextColor(textPrimaryCol)
            textSize = 16.5f
            setTypeface(typeface, Typeface.BOLD)
        })
        appsCard.addView(TextView(this).apply {
            text = "Applications ayant consommé vos mégaoctets (icônes officielles installées)"
            setTextColor(textSecondaryCol)
            textSize = 12f
            setPadding(0, dp(2f), 0, dp(14f))
        })

        val realApps = session?.appUsages.orEmpty()
        val displayApps = if (realApps.isNotEmpty()) {
            realApps.take(8)
        } else {
            listOf(
                AppUidUsage(1001, "WhatsApp", "com.whatsapp", 0L, 0L),
                AppUidUsage(1002, "ChatGPT", "com.openai.chatgpt", 0L, 0L),
                AppUidUsage(1003, "YouTube", "com.google.android.youtube", 0L, 0L),
                AppUidUsage(1004, "Chrome", "com.android.chrome", 0L, 0L),
                AppUidUsage(-5, "Partage de Connexion (Hotspot)", "android.uid.tethering", 0L, 0L)
            )
        }

        val totalMeasuredMb = (session?.measuredDecimalMb ?: 0.0).coerceAtLeast(0.1)
        for ((index, app) in displayApps.withIndex()) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(10f), 0, dp(10f))
            }
            row.addView(AppIconOrBrandView(this, app.appName, app.packageName, 44f))

            val midCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14f), 0, dp(10f), 0)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            midCol.addView(TextView(this).apply {
                text = app.appName
                setTextColor(textPrimaryCol)
                textSize = 14.5f
                setTypeface(typeface, Typeface.BOLD)
            })

            val appSharePct = if (realApps.isNotEmpty()) {
                ((app.totalMbDecimal / totalMeasuredMb) * 100.0).coerceIn(0.0, 100.0)
            } else {
                0.0
            }
            midCol.addView(TextView(this).apply {
                text = if (realApps.isNotEmpty()) {
                    "Rx ${(app.rxBytes / 1_000_000.0).roundToInt()} Mo • Tx ${(app.txBytes / 1_000_000.0).roundToInt()} Mo (${appSharePct.roundToInt()}%)"
                } else {
                    "0,0 Mo sur cette fenêtre • Prêt à auditer"
                }
                setTextColor(textSecondaryCol)
                textSize = 12f
            })

            val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 100
                progress = if (realApps.isNotEmpty()) appSharePct.roundToInt().coerceAtLeast(4) else 0
                progressDrawable = GradientDrawable().apply {
                    cornerRadius = dp(4f).toFloat()
                    setColor(Color.parseColor("#0284C7"))
                }
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(5f)
                ).apply { topMargin = dp(6f) }
            }
            midCol.addView(bar)
            row.addView(midCol)

            row.addView(TextView(this).apply {
                text = "${String.format("%.1f", app.totalMbDecimal)} Mo"
                setTextColor(textPrimaryCol)
                textSize = 14f
                setTypeface(typeface, Typeface.BOLD)
            })

            appsCard.addView(row)
            if (index < displayApps.size - 1) {
                appsCard.addView(View(this).apply {
                    setBackgroundColor(Color.parseColor(borderHex))
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1f))
                })
            }
        }
        return appsCard
    }

    private fun buildHomeSmsCard(): LinearLayout {
        val smsCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18f), dp(18f), dp(18f), dp(18f))
            background = cardDrawable()
        }
        smsCard.addView(TextView(this).apply {
            text = "SMS Opérateurs récents (${engine.smsRecords.size})"
            setTextColor(textPrimaryCol)
            textSize = 16.5f
            setTypeface(typeface, Typeface.BOLD)
        })
        smsCard.addView(TextView(this).apply {
            text = "Messages détectés automatiquement dans votre boîte SMS"
            setTextColor(textSecondaryCol)
            textSize = 12f
            setPadding(0, dp(2f), 0, dp(12f))
        })

        val fmt = SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.FRANCE)
        val smsToDisplay = if (showAllSmsItems) engine.smsRecords.take(15) else engine.smsRecords.take(4)

        for ((idx, sms) in smsToDisplay.withIndex()) {
            val itemRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(12f), 0, dp(12f))
            }
            itemRow.addView(OperatorBrandLogoView(this, sms.isp.logoKey, 42f))

            val bodyCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14f), 0, dp(8f), 0)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            bodyCol.addView(TextView(this).apply {
                text = sms.planName
                setTextColor(textPrimaryCol)
                textSize = 14.5f
                setTypeface(typeface, Typeface.BOLD)
            })
            bodyCol.addView(TextView(this).apply {
                text = sms.body
                setTextColor(textSecondaryCol)
                textSize = 12f
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
            })
            itemRow.addView(bodyCol)

            val rightCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.END
            }
            val statusTxt = when {
                sms.isExhaustion -> "épuisé"
                sms.isActivation -> "activé"
                else -> "info"
            }
            val statusCol = when {
                sms.isExhaustion -> Color.parseColor("#EF4444")
                sms.isActivation -> Color.parseColor("#10B981")
                else -> textSecondaryCol
            }
            rightCol.addView(TextView(this).apply {
                text = statusTxt
                setTextColor(statusCol)
                textSize = 12f
                setTypeface(typeface, Typeface.BOLD)
            })
            rightCol.addView(TextView(this).apply {
                text = fmt.format(sms.dateMs)
                setTextColor(textSecondaryCol)
                textSize = 10.5f
            })
            itemRow.addView(rightCol)

            smsCard.addView(itemRow)
            if (idx < smsToDisplay.size - 1) {
                smsCard.addView(View(this).apply {
                    setBackgroundColor(Color.parseColor(borderHex))
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1f))
                })
            }
        }

        if (engine.smsRecords.size > 4) {
            smsCard.addView(spaceView(10f))
            smsCard.addView(
                buildIconActionButton(
                    if (showAllSmsItems) VectorIconType.CHEVRON_UP else VectorIconType.CHEVRON_DOWN,
                    if (showAllSmsItems) "Masquer les anciens SMS" else "Voir plus de SMS (${engine.smsRecords.size})",
                    subtleBgHex,
                    "#0284C7",
                    borderHex
                ) {
                    showAllSmsItems = !showAllSmsItems
                    rebuildAllTabs()
                }
            )
        }
        return smsCard
    }

    // =========================================================================
    // TAB 1: BOUSSOLE ÉPURÉE + DÉTAILS RADIO & COINS EXPANSIBLES
    // =========================================================================
    private fun buildTab1CompassAndSpots(container: LinearLayout) {
        container.removeAllViews()
        liveHudCarrierTv = null
        liveHudBandTv = null
        liveHudDistTv = null
        liveBentoRsrpValTv = null
        liveBentoRsrpSubTv = null
        liveBentoSinrValTv = null
        liveBentoSinrSubTv = null
        liveBentoSpeedValTv = null
        liveBentoSpeedSubTv = null
        liveBentoLocValTv = null
        liveBentoLocSubTv = null

        val st = engine.state
        val activeIsp = IspBrand.fromText("${st.simOperatorCode} ${st.operatorName}")

        // Primary focus: ONLY compass radar + guidance + capture CTA + "Plus d'informations"
        val compassCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18f), dp(18f), dp(18f), dp(18f))
            background = cardDrawable()
        }

        val headRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        headRow.addView(OperatorBrandLogoView(this, activeIsp.logoKey, 38f))
        val headCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12f), 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        headCol.addView(TextView(this).apply {
            text = "Boussole Signal (${activeIsp.shortName})"
            setTextColor(textPrimaryCol)
            textSize = 16.5f
            setTypeface(typeface, Typeface.BOLD)
        })
        headCol.addView(TextView(this).apply {
            text = "Suivez la flèche vers le meilleur coin ou capturez ici"
            setTextColor(textSecondaryCol)
            textSize = 12f
        })
        headRow.addView(headCol)
        compassCard.addView(headRow)
        compassCard.addView(spaceView(10f))

        val cView = compassRadarView ?: WayfinderCompassRadarView(this, engine).also {
            compassRadarView = it
        }
        (cView.parent as? ViewGroup)?.removeView(cView)
        compassCard.addView(cView)
        compassCard.addView(spaceView(10f))

        liveGuidanceBannerTv = TextView(this).apply {
            text = guidanceText()
            setTextColor(Color.parseColor("#0284C7"))
            textSize = 13f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(14f), dp(12f), dp(14f), dp(12f))
            background = pillDrawable(if (isDark) "#0F172A" else "#F0F9FF", if (isDark) "#1E293B" else "#BAE6FD", 14f)
        }
        compassCard.addView(liveGuidanceBannerTv)
        compassCard.addView(spaceView(12f))

        compassCard.addView(
            buildIconActionButton(
                VectorIconType.LOCATION_PIN,
                "Capturer « $selectedRoomLabel » (0 Mo)",
                "#0284C7",
                "#FFFFFF",
                "#0284C7"
            ) {
                engine.recordInstantSpot(selectedRoomLabel)
                rebuildAllTabs()
            }
        )
        compassCard.addView(spaceView(10f))
        compassCard.addView(
            buildMoreInfoToggleButton(showCompassMoreInfo) {
                showCompassMoreInfo = !showCompassMoreInfo
                rebuildAllTabs()
            }
        )
        container.addView(compassCard)

        if (showCompassMoreInfo) {
            container.addView(spaceView(16f))
            container.addView(buildCompassControlsCard(st))
            container.addView(spaceView(16f))
            container.addView(buildCompassSpotsCard())
            container.addView(spaceView(16f))
            container.addView(buildCompassTechBentoCard(st))
        }
    }

    private fun buildCompassControlsCard(st: LiveRadioAndSensorState): LinearLayout {
        val ctrlCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18f), dp(16f), dp(18f), dp(16f))
            background = cardDrawable()
        }

        val hudRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12f), dp(8f), dp(12f), dp(8f))
            background = cardDrawable(subtleBgHex, borderHex, 12f, 1f)
        }
        liveHudCarrierTv = TextView(this).apply {
            text = "SIM : ${st.operatorName}"
            setTextColor(accentBlueCol)
            textSize = 11.5f
            setTypeface(typeface, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        liveHudBandTv = TextView(this).apply {
            text = st.bandLabel
            setTextColor(textSecondaryCol)
            textSize = 11f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        liveHudDistTv = TextView(this).apply {
            text = if (st.estimatedDistanceMeters > 0) "Antenne ~${st.estimatedDistanceMeters}m" else "4G Active"
            setTextColor(textPrimaryCol)
            textSize = 11f
            setTypeface(typeface, Typeface.BOLD)
        }
        hudRow.addView(liveHudCarrierTv)
        hudRow.addView(liveHudBandTv)
        hudRow.addView(liveHudDistTv)
        ctrlCard.addView(hudRow)
        ctrlCard.addView(spaceView(12f))

        val navSpots = engine.navigableSpots().sortedByDescending { it.compositeScore }
        if (navSpots.isNotEmpty()) {
            ctrlCard.addView(TextView(this).apply {
                text = "Aller vers :"
                setTextColor(textSecondaryCol)
                textSize = 12.5f
                setTypeface(typeface, Typeface.BOLD)
                setPadding(0, 0, 0, dp(8f))
            })
            val targetScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
            val targetRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            val activeTargetId = engine.navTargetSpot()?.id
            for (sp in navSpots) {
                val selected = sp.id == activeTargetId
                targetRow.addView(
                    buildMiniPill("${sp.roomLabel} • ${sp.compositeScore.roundToInt()}", selected, "#10B981") {
                        engine.setNavTarget(sp.id)
                        rebuildAllTabs()
                    }
                )
            }
            targetScroll.addView(targetRow)
            ctrlCard.addView(targetScroll)
            ctrlCard.addView(spaceView(12f))
        }

        ctrlCard.addView(TextView(this).apply {
            text = "Sélectionnez le coin où vous vous trouvez :"
            setTextColor(textSecondaryCol)
            textSize = 12.5f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, 0, 0, dp(8f))
        })

        val roomScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val roomRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val addRoomChip = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12f), dp(8f), dp(14f), dp(8f))
            background = pillDrawable("#0284C7", "#0284C7", 20f)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { rightMargin = dp(8f) }
            addView(VectorIconView(this@MainActivity, VectorIconType.PLUS, Color.WHITE, 15f))
            addView(TextView(this@MainActivity).apply {
                text = "Ajouter un coin"
                setTextColor(Color.WHITE)
                textSize = 12.5f
                setTypeface(typeface, Typeface.BOLD)
                setPadding(dp(6f), 0, 0, 0)
            })
            setOnClickListener { showAddCustomRoomDialog() }
        }
        roomRow.addView(addRoomChip)

        for (room in engine.customRooms) {
            val isSel = room == selectedRoomLabel
            roomRow.addView(TextView(this).apply {
                text = room
                setTextColor(if (isSel) Color.parseColor("#0284C7") else textSecondaryCol)
                textSize = 12.5f
                setTypeface(typeface, if (isSel) Typeface.BOLD else Typeface.NORMAL)
                setPadding(dp(14f), dp(8f), dp(14f), dp(8f))
                background = if (isSel) {
                    pillDrawable(if (isDark) "#1E293B" else "#E0F2FE", "#0284C7", 20f)
                } else {
                    pillDrawable(subtleBgHex, borderHex, 20f)
                }
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { rightMargin = dp(8f) }
                setOnClickListener {
                    selectedRoomLabel = room
                    rebuildAllTabs()
                }
            })
        }
        roomScroll.addView(roomRow)
        ctrlCard.addView(roomScroll)
        ctrlCard.addView(spaceView(12f))

        ctrlCard.addView(
            buildIconActionButton(
                VectorIconType.BOLT,
                if (st.isRunningSpeedTest) "Test 4G..." else "Tester le débit réel SIM 4G",
                "#0284C7",
                "#FFFFFF",
                "#0284C7"
            ) {
                engine.runActiveSpeedProbeAndRecord(selectedRoomLabel)
            }
        )
        return ctrlCard
    }

    private fun buildCompassSpotsCard(): LinearLayout {
        val spotsCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18f), dp(18f), dp(18f), dp(18f))
            background = cardDrawable()
        }
        val spotsHead = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        spotsHead.addView(VectorIconView(this, VectorIconType.TROPHY, Color.parseColor("#10B981"), 22f))
        spotsHead.addView(TextView(this).apply {
            text = "Classement des coins de votre maison"
            setTextColor(textPrimaryCol)
            textSize = 16.5f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(10f), 0, 0, 0)
        })
        spotsCard.addView(spotsHead)
        spotsCard.addView(TextView(this).apply {
            text = "Sauvegardé automatiquement • Posez votre téléphone au #1 pour vos téléchargements"
            setTextColor(textSecondaryCol)
            textSize = 12f
            setPadding(0, dp(4f), 0, dp(12f))
        })

        val sortedSpots = engine.radarSpots.sortedByDescending { it.compositeScore }
        if (sortedSpots.isEmpty()) {
            spotsCard.addView(TextView(this).apply {
                text = "Aucun coin enregistré. Déplacez-vous dans 2 ou 3 coins et appuyez sur « Capturer »."
                setTextColor(textSecondaryCol)
                textSize = 13f
            })
        } else {
            for ((idx, sp) in sortedSpots.withIndex()) {
                val isFirst = idx == 0
                val box = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(14f), dp(12f), dp(14f), dp(12f))
                    background = cardDrawable(
                        if (isFirst) (if (isDark) "#062E22" else "#F0FDF4") else subtleBgHex,
                        if (isFirst) "#10B981" else borderHex,
                        14f,
                        1f
                    )
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { bottomMargin = dp(8f) }
                }
                val col = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }
                val inFrame = sp.frameId == engine.currentFrameId
                val isActiveTarget = engine.navTargetSpot()?.id == sp.id
                col.addView(TextView(this).apply {
                    text = "#${idx + 1} ${sp.roomLabel}${if (inFrame) "" else " • autre session"}"
                    setTextColor(textPrimaryCol)
                    textSize = 14.5f
                    setTypeface(typeface, Typeface.BOLD)
                })
                val modeTag = if (sp.isPureRfMeasurement) "Signal RF SIM" else "Speedtest 4G"
                col.addView(TextView(this).apply {
                    text = "${sp.rsrpDbm} dBm • SINR ${sp.sinrDb} dB • ~${String.format("%.1f", sp.speedMbps)} Mbps ($modeTag)"
                    setTextColor(textSecondaryCol)
                    textSize = 12f
                })
                if (!inFrame) {
                    col.addView(TextView(this).apply {
                        text = "Allez à ce point puis appuyez sur « Je suis ici » pour pouvoir y être guidé."
                        setTextColor(textSecondaryCol)
                        textSize = 11.5f
                        setPadding(0, dp(4f), 0, 0)
                    })
                }
                val actionRowSpot = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, dp(8f), 0, 0)
                }
                if (inFrame) {
                    actionRowSpot.addView(
                        buildMiniPill(if (isActiveTarget) "Cible active" else "Guider", isActiveTarget, "#10B981") {
                            engine.setNavTarget(sp.id)
                            rebuildAllTabs()
                        }
                    )
                }
                actionRowSpot.addView(
                    buildMiniPill("Je suis ici", false, "#0284C7") {
                        engine.recalibrateAtSpot(sp.id)
                        Toast.makeText(this@MainActivity, "Position recalée sur « ${sp.roomLabel} »", Toast.LENGTH_SHORT).show()
                        rebuildAllTabs()
                    }
                )
                col.addView(actionRowSpot)
                box.addView(col)

                box.addView(TextView(this).apply {
                    text = "${sp.compositeScore.roundToInt()}/100"
                    setTextColor(if (isFirst) Color.parseColor("#10B981") else accentBlueCol)
                    textSize = 14f
                    setTypeface(typeface, Typeface.BOLD)
                })
                spotsCard.addView(box)
            }
            spotsCard.addView(spaceView(6f))
            spotsCard.addView(
                buildIconActionButton(VectorIconType.TRASH, "Effacer les coins enregistrés", subtleBgHex, "#EF4444", borderHex) {
                    engine.clearRadarSpots()
                    rebuildAllTabs()
                }
            )
        }
        return spotsCard
    }

    private fun buildCompassTechBentoCard(st: LiveRadioAndSensorState): LinearLayout {
        val bentoCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16f), dp(16f), dp(16f), dp(16f))
            background = cardDrawable()
        }
        val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val tRsrp = buildLiveBentoTile("PUISSANCE SIM (RSRP)", "${st.rsrpDbm} dBm", st.signalQualityLabel)
        liveBentoRsrpValTv = tRsrp.second
        liveBentoRsrpSubTv = tRsrp.third

        val tSinr = buildLiveBentoTile("QUALITÉ SINR & CQI", "${st.sinrDb} dB", "RSRQ ${st.rsrqDb} dB • CQI ${st.cqi}/15")
        liveBentoSinrValTv = tSinr.second
        liveBentoSinrSubTv = tSinr.third

        row1.addView(tRsrp.first, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = dp(8f) })
        row1.addView(tSinr.first, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        bentoCard.addView(row1)
        bentoCard.addView(spaceView(8f))

        val row2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val tSpeed = buildLiveBentoTile("TRANSPORT MESURE", "${String.format("%.2f", st.lastActiveSpeedMbps)} Mbps", st.lastSpeedTestTransportLabel)
        liveBentoSpeedValTv = tSpeed.second
        liveBentoSpeedSubTv = tSpeed.third

        val tLoc = buildLiveBentoTile("BAROMÈTRE & PAS 3D", "Hauteur Z : ${String.format("%+.1f", st.relativeAltitudeMeters)}m", "${st.stepCount} pas • ${st.pressureHpa.roundToInt()} hPa")
        liveBentoLocValTv = tLoc.second
        liveBentoLocSubTv = tLoc.third

        row2.addView(tSpeed.first, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = dp(8f) })
        row2.addView(tLoc.first, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        bentoCard.addView(row2)
        return bentoCard
    }

    // =========================================================================
    // TAB 2: AUDIT IA ÉPURÉ + CERTIFICAT TECHNIQUE EXPANSIBLE
    // =========================================================================
    private fun buildTab2AiAuditGemma(container: LinearLayout) {
        container.removeAllViews()
        val session = engine.selectedReportSession ?: engine.activeSession
        val st = engine.state
        val activeIsp = IspBrand.fromText("${st.simOperatorCode} ${st.operatorName}")

        // Primary focus: ONLY AI diagnostic verdict & launch action
        val aiCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20f), dp(20f), dp(20f), dp(20f))
            background = cardDrawable()
        }

        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        topRow.addView(VectorIconView(this, VectorIconType.AI_SPARK, Color.parseColor("#0284C7"), 28f))
        val topCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12f), 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        topCol.addView(TextView(this).apply {
            text = "Diagnostic IA (${engine.settings.aiModelName})"
            setTextColor(textPrimaryCol)
            textSize = 16.5f
            setTypeface(typeface, Typeface.BOLD)
        })
        topCol.addView(TextView(this).apply {
            text = if (engine.settings.aiApiKey.isNotBlank() || engine.settings.aiProvider == "ollama_local") {
                "Connecté à ${engine.settings.aiProvider} • Analyse directe"
            } else {
                "Mode local actif • Clé API optionnelle dans Paramètres"
            }
            setTextColor(textSecondaryCol)
            textSize = 12f
        })
        topRow.addView(topCol)
        aiCard.addView(topRow)
        aiCard.addView(spaceView(14f))

        aiCard.addView(
            buildIconActionButton(
                VectorIconType.AI_SPARK,
                if (st.isRunningAiAudit) "Analyse Gemma en cours..." else "Générer le Diagnostic IA Gemma",
                "#0284C7",
                "#FFFFFF",
                "#0284C7"
            ) {
                engine.runGemmaAiAudit()
            }
        )
        aiCard.addView(spaceView(14f))

        val reportBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14f), dp(14f), dp(14f), dp(14f))
            background = cardDrawable(subtleBgHex, borderHex, 14f, 1f)
        }
        reportBox.addView(TextView(this).apply {
            text = if (st.lastGemmaReport.isNotBlank()) {
                st.lastGemmaReport
            } else {
                "Appuyez sur « Générer le Diagnostic IA Gemma » pour obtenir une synthèse claire de votre forfait et du signal ${st.operatorName}."
            }
            setTextColor(textPrimaryCol)
            textSize = 13.5f
        })
        aiCard.addView(reportBox)
        aiCard.addView(spaceView(14f))
        aiCard.addView(
            buildMoreInfoToggleButton(showAiMoreInfo) {
                showAiMoreInfo = !showAiMoreInfo
                rebuildAllTabs()
            }
        )
        container.addView(aiCard)

        if (showAiMoreInfo && session != null) {
            container.addView(spaceView(16f))
            val certCard = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(20f), dp(20f), dp(20f), dp(20f))
                background = cardDrawable()
            }
            val certHead = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            certHead.addView(OperatorBrandLogoView(this, activeIsp.logoKey, 42f))
            val certCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12f), 0, 0, 0)
            }
            certCol.addView(TextView(this).apply {
                text = "Preuve Technique & Certificat Citoyen"
                setTextColor(textPrimaryCol)
                textSize = 16f
                setTypeface(typeface, Typeface.BOLD)
            })
            certCol.addView(TextView(this).apply {
                text = "${st.operatorName} • ${session.planName}"
                setTextColor(textSecondaryCol)
                textSize = 12f
            })
            certHead.addView(certCol)
            certCard.addView(certHead)
            certCard.addView(spaceView(14f))

            certCard.addView(TextView(this).apply {
                text = session.buildCitizenSummary()
                setTextColor(textPrimaryCol)
                textSize = 13.5f
                setPadding(0, 0, 0, dp(10f))
            })
            certCard.addView(TextView(this).apply {
                text = session.buildTechnicalProof()
                setTextColor(textSecondaryCol)
                textSize = 12.5f
                setPadding(0, 0, 0, dp(16f))
            })

            certCard.addView(
                buildIconActionButton(VectorIconType.COPY, "Copier le Certificat d'Audit", "#0284C7", "#FFFFFF", "#0284C7") {
                    val fullCert = buildString {
                        appendLine("=== CERTIFICAT CHECKMYMEGA ===")
                        appendLine("Opérateur SIM : ${st.operatorName} (${st.simOperatorCode})")
                        appendLine("Signal 4G : ${st.rsrpDbm} dBm | SINR ${st.sinrDb} dB | ${st.bandLabel}")
                        appendLine()
                        appendLine(session.buildCitizenSummary())
                        appendLine()
                        if (st.lastGemmaReport.isNotBlank()) {
                            appendLine("--- DIAGNOSTIC IA GEMMA ---")
                            appendLine(st.lastGemmaReport)
                            appendLine()
                        }
                        appendLine(session.buildTechnicalProof())
                    }
                    val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("Certificat CheckMyMega", fullCert))
                    Toast.makeText(this@MainActivity, "Certificat copié dans le presse-papiers !", Toast.LENGTH_LONG).show()
                }
            )
            container.addView(certCard)
        }
    }

    // =========================================================================
    // TAB 3: PARAMÈTRES (MODE SOMBRE, CLÉ API GEMMA, MULTI-SIM, SPEEDTEST 4G)
    // =========================================================================
    private fun buildTab3Settings(container: LinearLayout) {
        container.removeAllViews()
        val s = engine.settings

        // 1. Appearance & Network Preferences Card
        val prefCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18f), dp(18f), dp(18f), dp(18f))
            background = cardDrawable()
        }

        val prefHead = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        prefHead.addView(VectorIconView(this, VectorIconType.SETTINGS, accentBlueCol, 22f))
        prefHead.addView(TextView(this).apply {
            text = "Apparence & Réseau Cellulaire"
            setTextColor(textPrimaryCol)
            textSize = 16.5f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(10f), 0, 0, 0)
        })
        prefCard.addView(prefHead)
        prefCard.addView(spaceView(14f))

        // Dark Mode Switch Row
        val darkRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8f), 0, dp(8f))
        }
        darkRow.addView(VectorIconView(this, if (isDark) VectorIconType.MOON else VectorIconType.SUN, accentBlueCol, 20f))
        val darkCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12f), 0, dp(8f), 0)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        darkCol.addView(TextView(this).apply {
            text = "Mode Sombre (Thème OLED)"
            setTextColor(textPrimaryCol)
            textSize = 14.5f
            setTypeface(typeface, Typeface.BOLD)
        })
        darkCol.addView(TextView(this).apply {
            text = "Basculer entre le design Blanc FinTech et le mode Sombre"
            setTextColor(textSecondaryCol)
            textSize = 12f
        })
        darkRow.addView(darkCol)
        val darkSwitch = Switch(this).apply {
            isChecked = s.isDarkMode
            setOnCheckedChangeListener { _, checked ->
                s.isDarkMode = checked
                engine.saveSettings()
                applyWindowSystemColors()
                updateNavBarSelection()
                rebuildAllTabs()
            }
        }
        darkRow.addView(darkSwitch)
        prefCard.addView(darkRow)

        prefCard.addView(View(this).apply {
            setBackgroundColor(Color.parseColor(borderHex))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1f)).apply {
                topMargin = dp(8f)
                bottomMargin = dp(8f)
            }
        })

        // Force Cellular Speedtest Switch Row
        val cellRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8f), 0, dp(8f))
        }
        cellRow.addView(VectorIconView(this, VectorIconType.RADIO_TOWER, accentBlueCol, 20f))
        val cellCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12f), 0, dp(8f), 0)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        cellCol.addView(TextView(this).apply {
            text = "Forcer les tests de débit sur la SIM 4G"
            setTextColor(textPrimaryCol)
            textSize = 14.5f
            setTypeface(typeface, Typeface.BOLD)
        })
        cellCol.addView(TextView(this).apply {
            text = "Ignore le Wi-Fi (TRANSPORT_CELLULAR) pour ne jamais fausser l'audit SIM"
            setTextColor(textSecondaryCol)
            textSize = 12f
        })
        cellRow.addView(cellCol)
        val cellSwitch = Switch(this).apply {
            isChecked = s.forceCellularSpeedtest
            setOnCheckedChangeListener { _, checked ->
                s.forceCellularSpeedtest = checked
                engine.saveSettings()
            }
        }
        cellRow.addView(cellSwitch)
        prefCard.addView(cellRow)
        container.addView(prefCard)
        container.addView(spaceView(20f))

        // 2. Gemma / AI Model & API Key Configuration Card
        val gemmaCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18f), dp(18f), dp(18f), dp(18f))
            background = cardDrawable()
        }
        val gemmaHead = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        gemmaHead.addView(VectorIconView(this, VectorIconType.KEY, accentBlueCol, 22f))
        gemmaHead.addView(TextView(this).apply {
            text = "Configuration IA Gemma & Clé API"
            setTextColor(textPrimaryCol)
            textSize = 16.5f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(10f), 0, 0, 0)
        })
        gemmaCard.addView(gemmaHead)
        gemmaCard.addView(TextView(this).apply {
            text = "Utilisez Gemma 4 (Google AI Studio / OpenRouter) ou un serveur Ollama local (gemma4:e4b)"
            setTextColor(textSecondaryCol)
            textSize = 12f
            setPadding(0, dp(4f), 0, dp(12f))
        })

        // Provider Pills
        val provRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val providers = listOf(
            "google_gemma" to "Google AI (Gemma 4)",
            "openrouter" to "OpenRouter",
            "ollama_local" to "Ollama Local"
        )
        for ((pKey, pLabel) in providers) {
            val isSel = s.aiProvider == pKey
            val chip = TextView(this).apply {
                text = pLabel
                gravity = Gravity.CENTER
                setTextColor(if (isSel) Color.WHITE else textSecondaryCol)
                textSize = 11.5f
                setTypeface(typeface, if (isSel) Typeface.BOLD else Typeface.NORMAL)
                setPadding(dp(10f), dp(8f), dp(10f), dp(8f))
                background = pillDrawable(if (isSel) "#0284C7" else subtleBgHex, if (isSel) "#0284C7" else borderHex, 16f)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    rightMargin = dp(6f)
                }
                setOnClickListener {
                    s.aiProvider = pKey
                    if (pKey == "google_gemma" && !s.aiModelName.startsWith("gemma") && !s.aiModelName.startsWith("gemini")) {
                        s.aiModelName = "gemma-4-27b-it"
                    } else if (pKey == "ollama_local") {
                        s.aiModelName = "gemma4:e4b"
                    }
                    engine.saveSettings()
                    rebuildAllTabs()
                }
            }
            provRow.addView(chip)
        }
        gemmaCard.addView(provRow)
        gemmaCard.addView(spaceView(12f))

        // Model Selector Chips
        gemmaCard.addView(TextView(this).apply {
            text = "Modèle IA sélectionné :"
            setTextColor(textSecondaryCol)
            textSize = 12f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, 0, 0, dp(6f))
        })
        val modelScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val modelRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val modelPresets = listOf("gemma-4-27b-it", "gemma-4-12b-it", "gemma4:e4b", "gemma-3-27b-it", "gemini-2.5-flash")
        for (m in modelPresets) {
            val isSel = s.aiModelName == m
            modelRow.addView(TextView(this).apply {
                text = m
                setTextColor(if (isSel) Color.parseColor("#0284C7") else textSecondaryCol)
                textSize = 12f
                setTypeface(typeface, if (isSel) Typeface.BOLD else Typeface.NORMAL)
                setPadding(dp(12f), dp(7f), dp(12f), dp(7f))
                background = pillDrawable(if (isSel) (if (isDark) "#1E293B" else "#E0F2FE") else subtleBgHex, if (isSel) "#0284C7" else borderHex, 16f)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    rightMargin = dp(8f)
                }
                setOnClickListener {
                    s.aiModelName = m
                    engine.saveSettings()
                    rebuildAllTabs()
                }
            })
        }
        modelScroll.addView(modelRow)
        gemmaCard.addView(modelScroll)
        gemmaCard.addView(spaceView(12f))

        // API Key or Ollama URL Input
        val apiInput = EditText(this).apply {
            if (s.aiProvider == "ollama_local") {
                hint = "URL Ollama (ex: http://192.168.1.15:11434)"
                setText(s.ollamaBaseUrl)
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            } else {
                hint = "Collez votre clé API Google AI Studio (AIzaSy...) ou OpenRouter"
                setText(s.aiApiKey)
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            setTextColor(textPrimaryCol)
            setHintTextColor(textSecondaryCol)
            textSize = 13.5f
            setPadding(dp(14f), dp(12f), dp(14f), dp(12f))
            background = cardDrawable(subtleBgHex, borderHex, 12f, 1f)
        }
        gemmaCard.addView(apiInput)
        gemmaCard.addView(spaceView(12f))

        gemmaCard.addView(
            buildIconActionButton(VectorIconType.AI_SPARK, "Enregistrer la Clé & Tester Gemma", "#0284C7", "#FFFFFF", "#0284C7") {
                val entered = apiInput.text?.toString()?.trim().orEmpty()
                if (s.aiProvider == "ollama_local") {
                    s.ollamaBaseUrl = entered.ifBlank { "http://127.0.0.1:11434" }
                } else {
                    s.aiApiKey = entered
                }
                engine.saveSettings()
                Toast.makeText(this, "Configuration Gemma enregistrée ! Lancement du test...", Toast.LENGTH_SHORT).show()
                selectTab(2)
                engine.runGemmaAiAudit()
            }
        )
        container.addView(gemmaCard)
        container.addView(spaceView(20f))

        // 3. Multi-SIM & International Carrier Detection Card
        val simSelectCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18f), dp(18f), dp(18f), dp(18f))
            background = cardDrawable()
        }
        val simHead = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        simHead.addView(VectorIconView(this, VectorIconType.SIM_CARD, accentBlueCol, 22f))
        simHead.addView(TextView(this).apply {
            text = "Sélection de la Carte SIM (Multi-Pays / Dual-SIM)"
            setTextColor(textPrimaryCol)
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(10f), 0, 0, 0)
        })
        simSelectCard.addView(simHead)
        simSelectCard.addView(spaceView(10f))

        engine.refreshAvailableSimSlots()
        val slots = engine.simSlots
        if (slots.isEmpty()) {
            simSelectCard.addView(TextView(this).apply {
                text = "SIM détectée : ${engine.state.operatorName} (${engine.state.simOperatorCode}) — Pays : ${engine.state.simCountryIso.uppercase()}"
                setTextColor(textSecondaryCol)
                textSize = 13f
            })
        } else {
            for (slot in slots) {
                val isSelected = (s.selectedSimSubId == -1 && slot.isDefaultData) || s.selectedSimSubId == slot.subId
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(12f), dp(10f), dp(12f), dp(10f))
                    background = cardDrawable(
                        if (isSelected) (if (isDark) "#1E293B" else "#F0F9FF") else subtleBgHex,
                        if (isSelected) "#0284C7" else borderHex,
                        12f,
                        1f
                    )
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { bottomMargin = dp(8f) }
                    setOnClickListener {
                        engine.selectSimSubscription(slot.subId)
                        rebuildAllTabs()
                        Toast.makeText(this@MainActivity, "SIM sélectionnée : ${slot.carrierName}", Toast.LENGTH_SHORT).show()
                    }
                }
                val isp = IspBrand.fromText("${slot.mccMnc} ${slot.carrierName}")
                row.addView(OperatorBrandLogoView(this, isp.logoKey, 36f))
                val col = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(12f), 0, 0, 0)
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }
                col.addView(TextView(this).apply {
                    text = "SIM ${slot.slotIndex + 1} : ${slot.carrierName.ifBlank { slot.displayName }}"
                    setTextColor(textPrimaryCol)
                    textSize = 14f
                    setTypeface(typeface, Typeface.BOLD)
                })
                col.addView(TextView(this).apply {
                    text = "MCC/MNC ${slot.mccMnc} • Pays ${slot.countryIso.uppercase()}${if (slot.isDefaultData) " • SIM Data par défaut" else ""}"
                    setTextColor(textSecondaryCol)
                    textSize = 11.5f
                })
                row.addView(col)
                simSelectCard.addView(row)
            }
        }
        container.addView(simSelectCard)
        container.addView(spaceView(20f))

        // 4. Permissions & Android Restricted Settings Helper Card
        container.addView(buildPermissionsDiagnosticCard(showAllGrantedNotice = true))
    }

    private fun buildPermissionsDiagnosticCard(showAllGrantedNotice: Boolean): LinearLayout {
        val missingUsage = !engine.hasUsageStatsPermission()
        val missingSms = !hasReadSmsPermission()
        val missingPhone = !hasPhoneStatePermission()
        val missingLoc = !hasLocationPermission()
        val hasMissing = missingUsage || missingSms || missingPhone || missingLoc

        val cardBg = if (hasMissing) (if (isDark) "#2A1F0A" else "#FFFBEB") else cardBgHex
        val cardBorder = if (hasMissing) "#F59E0B" else borderHex

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18f), dp(16f), dp(18f), dp(16f))
            background = cardDrawable(cardBg, cardBorder, 18f, 1.2f)
        }

        val head = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val headColor = Color.parseColor(if (hasMissing) "#F59E0B" else "#10B981")
        head.addView(VectorIconView(this, VectorIconType.SHIELD_CHECK, headColor, 22f))
        head.addView(TextView(this).apply {
            text = if (hasMissing) {
                "Autorisations requises & Déblocage Android"
            } else {
                "Autorisations système actives"
            }
            setTextColor(headColor)
            textSize = 15.5f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(10f), 0, 0, 0)
        })
        card.addView(head)
        card.addView(spaceView(10f))

        val checks = listOf(
            "Données d'utilisation OS (Mo par app)" to !missingUsage,
            "SMS opérateur (forfaits & alertes)" to !missingSms,
            "État SIM & Réseau mobile" to !missingPhone,
            "Localisation & Cellule 4G" to !missingLoc
        )
        for ((label, ok) in checks) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(3f), 0, dp(3f))
            }
            row.addView(TextView(this).apply {
                text = label
                setTextColor(textPrimaryCol)
                textSize = 12.5f
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            row.addView(TextView(this).apply {
                text = if (ok) "Actif" else "Refusé / Requis"
                setTextColor(Color.parseColor(if (ok) "#10B981" else "#EF4444"))
                textSize = 11.5f
                setTypeface(typeface, Typeface.BOLD)
            })
            card.addView(row)
        }

        if (hasMissing) {
            card.addView(spaceView(10f))
            card.addView(TextView(this).apply {
                text = "Si Android 13–16 ou Xiaomi affiche « Paramètre restreint » ou refuse l'activation (normal sur une APK hors Play Store) :\n" +
                    "1. Touchez « 1. Infos appli (Débloquer) » ci-dessous.\n" +
                    "2. Menu ⋮ en haut à droite (ou en bas sur Xiaomi HyperOS/MIUI) > « Autoriser les paramètres restreints ».\n" +
                    "3. Revenez activer Données d'utilisation et SMS."
                setTextColor(textSecondaryCol)
                textSize = 12f
            })
            card.addView(spaceView(12f))
            card.addView(
                buildIconActionButton(
                    VectorIconType.SETTINGS,
                    "1. Infos appli (Débloquer Paramètres restreints)",
                    if (isDark) "#1E293B" else "#FFFFFF",
                    if (isDark) "#F8FAFC" else "#0F172A",
                    "#F59E0B"
                ) {
                    openAppDetailsSettings(showRestrictedHint = true)
                }
            )
            if (missingUsage) {
                card.addView(spaceView(8f))
                card.addView(
                    buildIconActionButton(
                        VectorIconType.SHIELD_CHECK,
                        "2. Activer Données d'utilisation",
                        "#F59E0B",
                        "#FFFFFF",
                        "#F59E0B"
                    ) {
                        openUsageAccessSettingsSafe()
                    }
                )
            }
            if (missingCriticalRuntimePermissions().isNotEmpty()) {
                card.addView(spaceView(8f))
                card.addView(
                    buildIconActionButton(
                        VectorIconType.SIM_CARD,
                        "Autoriser SMS / SIM / GPS",
                        "#0284C7",
                        "#FFFFFF",
                        "#0284C7"
                    ) {
                        requestAllPermissionsIfNeeded(requestCode = 4202)
                    }
                )
            }
        } else if (showAllGrantedNotice) {
            card.addView(spaceView(8f))
            card.addView(TextView(this).apply {
                text = "Toutes les sondes matérielles (NetworkStats, SMS, SIM, RF 4G) ont l'accès complet."
                setTextColor(textSecondaryCol)
                textSize = 12f
            })
        }

        return card
    }

    private fun buildLiveBentoTile(
        title: String,
        value: String,
        sub: String
    ): Triple<LinearLayout, TextView, TextView> {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12f), dp(12f), dp(12f), dp(12f))
            background = cardDrawable(subtleBgHex, borderHex, 14f, 1f)
        }
        val titleTv = TextView(this).apply {
            text = title
            setTextColor(textSecondaryCol)
            textSize = 10f
            setTypeface(typeface, Typeface.BOLD)
        }
        val valTv = TextView(this).apply {
            text = value
            setTextColor(textPrimaryCol)
            textSize = 17f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(4f), 0, dp(2f))
        }
        val subTv = TextView(this).apply {
            text = sub
            setTextColor(textSecondaryCol)
            textSize = 11f
        }
        box.addView(titleTv)
        box.addView(valTv)
        box.addView(subTv)
        return Triple(box, valTv, subTv)
    }

    private fun showManualBundleDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20f), dp(10f), dp(20f), dp(4f))
        }
        val mbInput = EditText(this).apply {
            hint = "Volume du forfait en Mo (ex: 3000)"
            setText("3000")
            inputType = InputType.TYPE_CLASS_NUMBER
            setTextColor(Color.parseColor("#0F172A"))
            setHintTextColor(Color.parseColor("#94A3B8"))
            textSize = 15f
        }
        val priceInput = EditText(this).apply {
            hint = "Prix optionnel (${engine.state.currencyLabel}, ex: 1000)"
            inputType = InputType.TYPE_CLASS_NUMBER
            setTextColor(Color.parseColor("#0F172A"))
            setHintTextColor(Color.parseColor("#94A3B8"))
            textSize = 14f
        }
        layout.addView(mbInput)
        layout.addView(spaceView(8f))
        layout.addView(priceInput)

        @Suppress("DEPRECATION")
        AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_LIGHT)
            .setTitle("Suivi manuel à partir de 0 Mo")
            .setMessage("Utile sans permission SMS ou pour démarrer un compteur exact dès maintenant :")
            .setView(layout)
            .setPositiveButton("Démarrer à 0 Mo") { _, _ ->
                val mb = mbInput.text?.toString()?.trim()?.toDoubleOrNull() ?: 0.0
                val price = priceInput.text?.toString()?.trim()?.toIntOrNull() ?: 0
                if (mb > 0.0) {
                    engine.setManualBundle(mb, price)
                    rebuildAllTabs()
                    Toast.makeText(this, "Suivi démarré à 0,0 Mo pour ${mb.roundToInt()} Mo", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun buildMoreInfoToggleButton(expanded: Boolean, onToggle: () -> Unit): LinearLayout {
        val label = if (expanded) "Masquer les détails" else "Plus d'informations"
        val fgHex = if (expanded) (if (isDark) "#CBD5E1" else "#334155") else "#0284C7"
        val bgHex = if (expanded) subtleBgHex else (if (isDark) "#0F172A" else "#F0F9FF")
        val bdHex = if (expanded) borderHex else "#0284C7"
        return buildIconActionButton(
            if (expanded) VectorIconType.CHEVRON_UP else VectorIconType.CHEVRON_DOWN,
            label,
            bgHex,
            fgHex,
            bdHex,
            onToggle
        )
    }

    private fun buildIconActionButton(
        iconType: VectorIconType,
        label: String,
        bgHex: String,
        fgHex: String,
        bHex: String,
        onClick: () -> Unit
    ): LinearLayout {
        val fgCol = Color.parseColor(fgHex)
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(14f), dp(12f), dp(14f), dp(12f))
            background = cardDrawable(bgHex, bHex, 14f, 1.2f)
            addView(VectorIconView(this@MainActivity, iconType, fgCol, 17f))
            addView(TextView(this@MainActivity).apply {
                text = label
                setTextColor(fgCol)
                textSize = 13f
                setTypeface(typeface, Typeface.BOLD)
                setPadding(dp(8f), 0, 0, 0)
            })
            setOnClickListener { onClick() }
        }
    }

    private fun spaceView(heightDp: Float): View {
        return View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(heightDp)
            )
        }
    }
}
