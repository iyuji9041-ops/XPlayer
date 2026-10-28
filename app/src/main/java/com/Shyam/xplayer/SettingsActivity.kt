package com.Shyam.xplayer

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_settings)

        val settingsRoot = findViewById<View>(R.id.settingsRoot)
        val settingsHeader = findViewById<View>(R.id.settingsHeader)

        ViewCompat.setOnApplyWindowInsetsListener(settingsRoot) { _, insets ->
            val statusBar = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val navBar = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val density = resources.displayMetrics.density
            settingsHeader.setPadding(
                (16 * density).toInt(),
                statusBar.top + (8 * density).toInt(),
                (16 * density).toInt(),
                (12 * density).toInt()
            )
            settingsRoot.setPadding(0, 0, 0, navBar.bottom)
            insets
        }

        val btnBack = findViewById<ImageButton>(R.id.btnBackSettings)
        btnBack.setOnClickListener { finish() }

        // ==========================================
        // SHAREDPREFERENCES (App Ki Memory)
        // ==========================================
        val sharedPref = getSharedPreferences("XPlayerSettings", Context.MODE_PRIVATE)
        val editor = sharedPref.edit()

        // 🟢 1. Playback Speed
        val speedOptions = arrayOf("0.5x", "0.75x", "1.0x", "1.25x", "1.5x", "2.0x")
        var currentSpeedIndex = sharedPref.getInt("default_speed_index", 2)
        val tvSpeedSubtitle = findViewById<TextView>(R.id.tvSpeedSubtitle)
        tvSpeedSubtitle.text = "Currently set to ${speedOptions[currentSpeedIndex]}"
        
        findViewById<RelativeLayout>(R.id.panelSpeed).setOnClickListener {
            AlertDialog.Builder(this, androidx.appcompat.R.style.Theme_AppCompat_Dialog_Alert)
                .setTitle("Default Playback Speed")
                .setSingleChoiceItems(speedOptions, currentSpeedIndex) { dialog, which ->
                    currentSpeedIndex = which
                    editor.putInt("default_speed_index", which).apply()
                    tvSpeedSubtitle.text = "Currently set to ${speedOptions[which]}"
                    dialog.dismiss()
                }.show()
        }

        // 🟢 2. Auto-Resume Control
        val resumeOptions = arrayOf("Always resume from last position", "Ask before resuming", "Start from beginning")
        var currentResumeIndex = sharedPref.getInt("auto_resume_mode", 0)
        val tvResumeSubtitle = findViewById<TextView>(R.id.tvResumeSubtitle)
        tvResumeSubtitle.text = resumeOptions[currentResumeIndex]

        findViewById<RelativeLayout>(R.id.panelResume).setOnClickListener {
            AlertDialog.Builder(this, androidx.appcompat.R.style.Theme_AppCompat_Dialog_Alert)
                .setTitle("Auto-Resume Control")
                .setSingleChoiceItems(resumeOptions, currentResumeIndex) { dialog, which ->
                    currentResumeIndex = which
                    editor.putInt("auto_resume_mode", which).apply()
                    tvResumeSubtitle.text = resumeOptions[which]
                    dialog.dismiss()
                }.show()
        }

        // 🟢 3. Hardware Decoder
        val hwOptions = arrayOf("Hardware Acceleration (HW)", "Software Decoding (SW)")
        var currentHwIndex = sharedPref.getInt("hw_decoder_mode", 0)
        val tvHwSubtitle = findViewById<TextView>(R.id.tvHwSubtitle)
        tvHwSubtitle.text = hwOptions[currentHwIndex]

        findViewById<RelativeLayout>(R.id.panelHwDecoder).setOnClickListener {
            AlertDialog.Builder(this, androidx.appcompat.R.style.Theme_AppCompat_Dialog_Alert)
                .setTitle("Hardware Decoder")
                .setSingleChoiceItems(hwOptions, currentHwIndex) { dialog, which ->
                    currentHwIndex = which
                    editor.putInt("hw_decoder_mode", which).apply()
                    tvHwSubtitle.text = hwOptions[which]
                    dialog.dismiss()
                }.show()
        }

        // 🟢 4. UI Timeout
        val timeoutOptions = arrayOf("Auto-hide after 3 seconds", "Auto-hide after 5 seconds", "Never hide (Always visible)")
        var currentTimeoutIndex = sharedPref.getInt("ui_timeout_mode", 1)
        val tvTimeoutSubtitle = findViewById<TextView>(R.id.tvTimeoutSubtitle)
        tvTimeoutSubtitle.text = timeoutOptions[currentTimeoutIndex]

        findViewById<RelativeLayout>(R.id.panelTimeout).setOnClickListener {
            AlertDialog.Builder(this, androidx.appcompat.R.style.Theme_AppCompat_Dialog_Alert)
                .setTitle("UI Timeout Duration")
                .setSingleChoiceItems(timeoutOptions, currentTimeoutIndex) { dialog, which ->
                    currentTimeoutIndex = which
                    editor.putInt("ui_timeout_mode", which).apply()
                    tvTimeoutSubtitle.text = timeoutOptions[which]
                    dialog.dismiss()
                }.show()
        }

        // 🟢 5. Double Tap Time
        val tapOptions = arrayOf("5 seconds", "10 seconds", "15 seconds")
        var currentTapIndex = sharedPref.getInt("double_tap_time", 1)
        val tvDoubleTapSubtitle = findViewById<TextView>(R.id.tvDoubleTapSubtitle)
        tvDoubleTapSubtitle.text = tapOptions[currentTapIndex]

        findViewById<RelativeLayout>(R.id.panelDoubleTap).setOnClickListener {
            AlertDialog.Builder(this, androidx.appcompat.R.style.Theme_AppCompat_Dialog_Alert)
                .setTitle("Double Tap Seek Timing")
                .setSingleChoiceItems(tapOptions, currentTapIndex) { dialog, which ->
                    currentTapIndex = which
                    editor.putInt("double_tap_time", which).apply()
                    tvDoubleTapSubtitle.text = tapOptions[which]
                    dialog.dismiss()
                }.show()
        }

        // 🟢 6. Themes
        val themeOptions = arrayOf("Deep AMOLED Dark", "Material Dark", "Light Mode (Beta)")
        var currentThemeIndex = sharedPref.getInt("app_theme_mode", 0)
        val tvThemeSubtitle = findViewById<TextView>(R.id.tvThemeSubtitle)
        tvThemeSubtitle.text = themeOptions[currentThemeIndex]

        findViewById<RelativeLayout>(R.id.panelTheme).setOnClickListener {
            AlertDialog.Builder(this, androidx.appcompat.R.style.Theme_AppCompat_Dialog_Alert)
                .setTitle("App Theme Mode")
                .setSingleChoiceItems(themeOptions, currentThemeIndex) { dialog, which ->
                    currentThemeIndex = which
                    editor.putInt("app_theme_mode", which).apply()
                    tvThemeSubtitle.text = themeOptions[which]
                    Toast.makeText(this, "Theme applied! Restart app to see changes.", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }.show()
        }

        // 🟢 7. Simple Popups (Coming Soon / Simple Actions)
        findViewById<RelativeLayout>(R.id.panelSubtitle).setOnClickListener {
            Toast.makeText(this, "Subtitle Geometry Editor opening...", Toast.LENGTH_SHORT).show()
        }
        
        findViewById<RelativeLayout>(R.id.panelSync).setOnClickListener {
            Toast.makeText(this, "Audio Sync Dialog coming soon!", Toast.LENGTH_SHORT).show()
        }

        findViewById<RelativeLayout>(R.id.panelSwipe).setOnClickListener {
            Toast.makeText(this, "Swipe Gestures are currently locked to ON", Toast.LENGTH_SHORT).show()
        }

        findViewById<RelativeLayout>(R.id.panelVault).setOnClickListener {
            Toast.makeText(this, "Vault Setup: Please create a PIN first.", Toast.LENGTH_SHORT).show()
        }

        findViewById<RelativeLayout>(R.id.panelExclude).setOnClickListener {
            Toast.makeText(this, "No excluded folders found.", Toast.LENGTH_SHORT).show()
        }

        // 🟢 8. WIPE PLAYBACK CACHE (Ye asali function hai jo Video History delete karega)
        findViewById<RelativeLayout>(R.id.panelWipe).setOnClickListener {
            AlertDialog.Builder(this, androidx.appcompat.R.style.Theme_AppCompat_Dialog_Alert)
                .setTitle("Wipe Playback Cache?")
                .setMessage("This will delete all your watched video history and resume positions. This cannot be undone.")
                .setPositiveButton("Wipe") { _, _ ->
                    // VideoProgress naam ki history ko memory se delete karna
                    getSharedPreferences("VideoProgress", Context.MODE_PRIVATE).edit().clear().apply()
                    Toast.makeText(this, "All history and cache deleted successfully!", Toast.LENGTH_LONG).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        // 🟢 9. Switches
        val switchBgAudio = findViewById<SwitchCompat>(R.id.switchBgAudio)
        val switchBoost = findViewById<SwitchCompat>(R.id.switchBoost)

        switchBgAudio.isChecked = sharedPref.getBoolean("bg_audio", false)
        switchBoost.isChecked = sharedPref.getBoolean("audio_boost", false)

        switchBgAudio.setOnCheckedChangeListener { _, isChecked ->
            editor.putBoolean("bg_audio", isChecked).apply()
        }
        
        switchBoost.setOnCheckedChangeListener { _, isChecked ->
            editor.putBoolean("audio_boost", isChecked).apply()
        }
    }
}