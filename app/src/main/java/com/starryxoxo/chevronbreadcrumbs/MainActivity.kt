package com.starryxoxo.chevronbreadcrumbs

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import com.starryxoxo.chevronbreadcrumbs.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val prefs by lazy { getSharedPreferences("ChevronPrefs", Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupPermissions()
        setupControls()
        setupColorButtons()
        
        binding.swLandscape.isChecked = prefs.getBoolean("disableLandscape", false)
        binding.swLandscape.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("disableLandscape", isChecked).apply()
        }

        binding.swSwipe.isChecked = prefs.getBoolean("enableSwipe", false)
        binding.swSwipe.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("enableSwipe", isChecked).apply()
        }

        binding.btnExclusions.setOnClickListener {
            startActivity(Intent(this, ExclusionActivity::class.java))
        }
    }

    private fun setupPermissions() {
        binding.btnPermissions.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                startActivity(intent)
            }
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        binding.tvServiceStatus.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    }

    private fun setupControls() {
        setupSeekBar(binding.sbXOffset, binding.tvXOffset, "offsetX", 20, R.string.horizontal_offset)
        setupSeekBar(binding.sbYOffset, binding.tvYOffset, "offsetY", 40, R.string.vertical_offset)
        setupSeekBar(binding.sbSize, binding.tvSize, "textSize", 14, R.string.chevron_size)
        setupSeekBar(binding.sbTransparency, binding.tvTransparency, "transparency", 100, R.string.transparency)
        setupFloatSeekBar(binding.sbStrokeSize, binding.tvStrokeSize, "strokeWidth", 20, R.string.stroke_size)
    }

    private fun setupFloatSeekBar(
        seekBar: SeekBar,
        textView: android.widget.TextView,
        prefKey: String,
        defaultValue: Int,
        stringRes: Int
    ) {
        val savedValue = prefs.getInt(prefKey, defaultValue)
        seekBar.progress = savedValue
        textView.text = getString(stringRes, savedValue / 10f)

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, f: Boolean) {
                textView.text = getString(stringRes, p / 10f)
                prefs.edit().putInt(prefKey, p).apply()
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
    }

    private fun setupSeekBar(
        seekBar: SeekBar,
        textView: android.widget.TextView,
        prefKey: String,
        defaultValue: Int,
        stringRes: Int
    ) {
        val savedValue = prefs.getInt(prefKey, defaultValue)
        seekBar.progress = savedValue
        textView.text = getString(stringRes, savedValue)

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, f: Boolean) {
                textView.text = getString(stringRes, p)
                prefs.edit().putInt(prefKey, p).apply()
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
    }

    private fun setupColorButtons() {
        binding.btnBlue.setOnClickListener { saveColor("#007AFF") }
        binding.btnWhite.setOnClickListener { saveColor("#FAFAFA") }
        binding.btnBlack.setOnClickListener { saveColor("#121212") }
        binding.btnGreen.setOnClickListener { saveColor("#34C759") }
    }

    private fun saveColor(hex: String) {
        prefs.edit().putString("textColor", hex).apply()
    }

    override fun onResume() {
        super.onResume()
        updateServiceStatus()
    }

    private fun updateServiceStatus() {
        if (isAccessibilityServiceEnabled()) {
            binding.tvServiceStatus.text = getString(R.string.service_status_enabled)
            binding.tvServiceStatus.setTextColor(Color.GREEN)
        } else {
            binding.tvServiceStatus.text = getString(R.string.service_status_disabled)
            binding.tvServiceStatus.setTextColor(Color.RED)
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC)
        return enabledServices.any { it.resolveInfo.serviceInfo.packageName == packageName }
    }
}
