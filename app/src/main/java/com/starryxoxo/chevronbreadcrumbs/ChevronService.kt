package com.starryxoxo.chevronbreadcrumbs

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.util.TypedValue
import android.content.res.Configuration
import android.view.GestureDetector
import androidx.core.graphics.toColorInt
import com.starryxoxo.chevronbreadcrumbs.databinding.OverlayChevronBinding

class ChevronService : AccessibilityService(), SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var windowManager: WindowManager
    private var binding: OverlayChevronBinding? = null

    private val appStack = mutableListOf<String>()
    private var lastPackage: String? = null
    private var currentTarget: String? = null
    private var stackIndex = 0

    // Burn-in protection and Dimming
    private var pixelShiftX = 0
    private var pixelShiftY = 0
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isDimmed = false

    private val burnInRunnable = object : Runnable {
        override fun run() {
            pixelShiftX = (-2..2).random()
            pixelShiftY = (-2..2).random()
            if (binding?.root?.visibility == View.VISIBLE) {
                syncViewLayout()
            }
            mainHandler.postDelayed(this, 60000) // Shift every minute
        }
    }

    private val dimRunnable = Runnable {
        isDimmed = true
        applyDimming(true)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        setupPermanentOverlay()

        getSharedPreferences("ChevronPrefs", Context.MODE_PRIVATE)
            .registerOnSharedPreferenceChangeListener(this)

        mainHandler.post(burnInRunnable)
    }

    private fun resetDimming() {
        mainHandler.removeCallbacks(dimRunnable)
        isDimmed = false
        applyDimming(false)
        mainHandler.postDelayed(dimRunnable, 20000) // Dim after 20s
    }

    private fun applyDimming(dim: Boolean) {
        val rootBinding = binding ?: return
        val prefs = getSharedPreferences("ChevronPrefs", Context.MODE_PRIVATE)

        // 1. Transparency (Background)
        val baseTransparency = prefs.getInt("transparency", 100)
        val targetTransparency = if (dim) 100 else baseTransparency
        rootBinding.root.background?.mutate()?.alpha = ((100 - targetTransparency) * 2.55).toInt()

        // 2. Text Color
        val colorHex = prefs.getString("textColor", "#007AFF") ?: "#007AFF"
        val baseColor = try {
            colorHex.toColorInt()
        } catch (e: Exception) {
            "#007AFF".toColorInt()
        }

        rootBinding.chevronText.isStrokeEnabled = !dim
        rootBinding.chevronText.strokeWidthValue = prefs.getInt("strokeWidth", 20) / 10f
        val finalColor = if (dim) (baseColor and 0x00FFFFFF) or 0x44000000 else baseColor
        rootBinding.chevronText.setTextColor(finalColor)
    }

    private fun setupPermanentOverlay() {
        val themedContext = ContextThemeWrapper(this, R.style.Theme_ChevronBreadcrumbs)
        binding = OverlayChevronBinding.inflate(LayoutInflater.from(themedContext))

        val params = createLayoutParams()
        binding?.root?.visibility = View.GONE 

        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                currentTarget?.let { target ->
                    packageManager.getLaunchIntentForPackage(target)?.let { intent ->
                        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(intent)
                    }
                }
                return true
            }

            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                val prefs = getSharedPreferences("ChevronPrefs", Context.MODE_PRIVATE)
                if (!prefs.getBoolean("enableSwipe", false)) return false

                if (e1 != null && e1.x - e2.x > 50) { // Swipe Left
                    cycleStack()
                    return true
                }
                return false
            }
        })

        binding?.root?.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true
        }

        try {
            windowManager.addView(binding?.root, params)
        } catch (e: Exception) {}
    }

    private fun cycleStack() {
        val prefs = getSharedPreferences("ChevronPrefs", Context.MODE_PRIVATE)
        val excludedApps = prefs.getStringSet("excludedApps", emptySet()) ?: emptySet()
        val currentPackage = lastPackage ?: ""

        val available = appStack.filter { it != currentPackage && !excludedApps.contains(it) }.reversed()
        if (available.isEmpty()) return

        stackIndex = (stackIndex + 1) % available.size
        updateChevronContent(available[stackIndex])
        resetDimming()
    }

    private fun createLayoutParams(): WindowManager.LayoutParams {
        val prefs = getSharedPreferences("ChevronPrefs", Context.MODE_PRIVATE)
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = prefs.getInt("offsetX", 20) + pixelShiftX
            y = prefs.getInt("offsetY", 40) + pixelShiftY
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val newPackage = event.packageName?.toString() ?: return

        if (newPackage == lastPackage) return
        lastPackage = newPackage

        if (shouldAutoExclude(newPackage)) return

        val prefs = getSharedPreferences("ChevronPrefs", Context.MODE_PRIVATE)
        val excludedApps = prefs.getStringSet("excludedApps", emptySet()) ?: emptySet()

        if (newPackage == packageName) {
            updateChevronContent(packageName)
            updateVisibility(true)
            return
        }

        if (newPackage == "com.android.settings") {
            updateVisibility(false)
            return
        }

        if (excludedApps.contains(newPackage)) return

        appStack.removeAll { it == newPackage }
        appStack.add(newPackage)
        if (appStack.size > 4) appStack.removeAt(0)
        resetDimming()
        stackIndex = 0

        val target = appStack.reversed().firstOrNull { it != newPackage && !excludedApps.contains(it) }

        if (target != null) {
            updateChevronContent(target)
            updateVisibility(true)
        } else {
            updateVisibility(false)
        }
    }

    private fun shouldAutoExclude(pkg: String): Boolean {
        val p = pkg.lowercase()
        val isSystemComponent = p == "com.android.systemui" || 
                                p == "android" || 
                                p.contains("launcher") ||
                                p.contains("screenshot") ||
                                p.contains("screenshots") ||
                                p.contains("systemui") ||
                                p.contains("tranresolver") ||
                                p.contains("transhare") ||
                                p.contains("controlcenter") ||
                                p.contains("system.ui") ||
                                p.contains("android.ui") ||
                                p.contains("panel") ||
                                p.contains("statusbar") ||
                                p.contains("notification")

        val isGoogleOrSystemApp = p.contains("com.google.android.gms") ||
                                 p.contains("com.android.vending") ||
                                 p.contains("com.google.android.googlequicksearchbox") ||
                                 p.contains("com.google.android.as") ||
                                 p.contains("packageinstaller") ||
                                 p.contains("permissioncontroller") ||
                                 p.contains("setupwizard") ||
                                 p.contains("picker") ||
                                 p.contains("documentsui") ||
                                 p.contains("captiveportallogin") ||
                                 p.contains("settings.intelligence") ||
                                 p.contains("framework-res") ||
                                 p.startsWith("com.transsion")

        val isOverlayOrIME = p.contains("inputmethod") || 
                             p.contains("accessibility") ||
                             p.contains("overlay") ||
                             p.contains("keyboard") ||
                             p.contains("service") && (p.contains("system") || p.contains("android"))

        return isSystemComponent || isGoogleOrSystemApp || isOverlayOrIME
    }

    private fun updateChevronContent(targetPackage: String) {
        currentTarget = targetPackage
        val prefs = getSharedPreferences("ChevronPrefs", Context.MODE_PRIVATE)

        binding?.apply {
            chevronText.text = if (targetPackage == packageName) "◀ Preview" else "◀ ${getAppName(targetPackage) ?: "Back"}"
            chevronText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, prefs.getInt("textSize", 14).toFloat())
            applyDimming(isDimmed)
        }
        syncViewLayout()
    }

    private fun syncViewLayout() {
        val currentRoot = binding?.root ?: return
        if (!currentRoot.isAttachedToWindow) return
        try {
            windowManager.updateViewLayout(currentRoot, createLayoutParams())
        } catch (e: Exception) {}
    }

    private fun updateVisibility(visible: Boolean) {
        val prefs = getSharedPreferences("ChevronPrefs", Context.MODE_PRIVATE)
        val shouldHide = prefs.getBoolean("disableLandscape", false) && 
                        resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        binding?.root?.visibility = if (visible && !shouldHide) View.VISIBLE else View.GONE
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Decoupled sync using handler frame queue to allow screen transformations to settle safely
        mainHandler.post {
            if (currentTarget != null) {
                updateChevronContent(currentTarget!!)
                updateVisibility(true)
            }
        }
    }

    override fun onSharedPreferenceChanged(prefs: SharedPreferences?, key: String?) {
        // Enforce safe lifecycle evaluation if backing data gets synchronized on non-UI operations
        if (binding?.root?.visibility == View.VISIBLE && currentTarget != null) {
            mainHandler.post {
                updateChevronContent(currentTarget!!)
            }
        }
    }

    private fun getAppName(packageName: String): String? {
        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) { null }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        getSharedPreferences("ChevronPrefs", Context.MODE_PRIVATE)
            .unregisterOnSharedPreferenceChangeListener(this)
        mainHandler.removeCallbacksAndMessages(null)
        
        binding?.root?.let { view ->
            try {
                windowManager.removeViewImmediate(view)
            } catch (e: Exception) {}
        }
        binding = null
    }
}
