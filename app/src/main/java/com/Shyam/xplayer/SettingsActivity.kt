package com.Shyam.xplayer

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.EditText
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
import com.Shyam.xplayer.vault.VaultActivity
import com.Shyam.xplayer.vault.VaultSecurityManager

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

        val sharedPref = getSharedPreferences("XPlayerSettings", Context.MODE_PRIVATE)
        val editor = sharedPref.edit()

        // 1. Playback Speed
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

        // 2. Auto-Resume Control
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

        // 3. Hardware Decoder
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

        // 4. UI Timeout
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

        // 5. Double Tap Time
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

        // 6. Themes
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

        // 7. Subtitle Customization
        val subSizes = arrayOf("Small (14sp)", "Medium (18sp)", "Large (22sp)", "Extra Large (26sp)")
        val tvSubSubtitle = findViewById<TextView>(R.id.tvSubtitleDesc)
        val currentSubSize = sharedPref.getInt("subtitle_size", 1)
        tvSubSubtitle?.text = "Font size: ${subSizes[currentSubSize]}"

        findViewById<RelativeLayout>(R.id.panelSubtitle).setOnClickListener {
            AlertDialog.Builder(this, androidx.appcompat.R.style.Theme_AppCompat_Dialog_Alert)
                .setTitle("Subtitle Font Size")
                .setSingleChoiceItems(subSizes, sharedPref.getInt("subtitle_size", 1)) { dialog, which ->
                    editor.putInt("subtitle_size", which).apply()
                    tvSubSubtitle?.text = "Font size: ${subSizes[which]}"
                    Toast.makeText(this, "Subtitle size updated", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }.show()
        }
        
        // 8. Audio Sync Delay
        val syncOptions = arrayOf("-500 ms", "-250 ms", "0 ms (Normal)", "+250 ms", "+500 ms")
        val syncValues = longArrayOf(-500L, -250L, 0L, 250L, 500L)
        val currentDelayMs = sharedPref.getLong("audio_delay_ms", 0L)
        val tvSyncSubtitle = findViewById<TextView>(R.id.tvSyncSubtitle)
        tvSyncSubtitle?.text = "Current delay: ${currentDelayMs}ms"

        findViewById<RelativeLayout>(R.id.panelSync).setOnClickListener {
            val currentIndex = syncValues.indexOfFirst { it == currentDelayMs }.coerceAtLeast(2)
            AlertDialog.Builder(this, androidx.appcompat.R.style.Theme_AppCompat_Dialog_Alert)
                .setTitle("Audio Delay / Sync")
                .setSingleChoiceItems(syncOptions, currentIndex) { dialog, which ->
                    val selectedDelay = syncValues[which]
                    editor.putLong("audio_delay_ms", selectedDelay).apply()
                    tvSyncSubtitle?.text = "Current delay: ${selectedDelay}ms"
                    Toast.makeText(this, "Audio sync adjusted to ${selectedDelay}ms", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }.show()
        }

        // 9. Swipe Gestures Toggle
        val swipeEnabled = sharedPref.getBoolean("swipe_gestures", true)
        val tvSwipeSubtitle = findViewById<TextView>(R.id.tvSwipeSubtitle)
        tvSwipeSubtitle?.text = if (swipeEnabled) "Swipe for brightness & volume enabled" else "Swipe gestures disabled"

        findViewById<RelativeLayout>(R.id.panelSwipe).setOnClickListener {
            val newState = !sharedPref.getBoolean("swipe_gestures", true)
            editor.putBoolean("swipe_gestures", newState).apply()
            tvSwipeSubtitle?.text = if (newState) "Swipe for brightness & volume enabled" else "Swipe gestures disabled"
            Toast.makeText(this, if (newState) "Swipe gestures enabled" else "Swipe gestures disabled", Toast.LENGTH_SHORT).show()
        }

        // 10. Private Vault
        val tvVaultSubtitle = findViewById<TextView>(R.id.tvVaultSubtitle)
        val isVaultReady = VaultSecurityManager.isVaultSetup(this)
        tvVaultSubtitle?.text = if (isVaultReady) "AES-256 PIN Protected" else "Tap to set up secure vault"

        findViewById<RelativeLayout>(R.id.panelVault).setOnClickListener {
            startActivity(Intent(this, VaultActivity::class.java))
        }

        // 11. Excluded Folders
        findViewById<RelativeLayout>(R.id.panelExclude).setOnClickListener {
            val excludedSet = sharedPref.getStringSet("excluded_folders", emptySet()) ?: emptySet()
            if (excludedSet.isEmpty()) {
                AlertDialog.Builder(this, androidx.appcompat.R.style.Theme_AppCompat_Dialog_Alert)
                    .setTitle("Excluded Folders")
                    .setMessage("No folders are currently excluded. Folders can be hidden to keep them out of your media lists.")
                    .setPositiveButton("OK", null)
                    .show()
            } else {
                val list = excludedSet.toTypedArray()
                AlertDialog.Builder(this, androidx.appcompat.R.style.Theme_AppCompat_Dialog_Alert)
                    .setTitle("Excluded Folders (${list.size})")
                    .setItems(list) { _, which ->
                        AlertDialog.Builder(this)
                            .setTitle("Unhide folder?")
                            .setMessage("Remove '${list[which]}' from excluded list?")
                            .setPositiveButton("Remove") { _, _ ->
                                val newSet = HashSet(excludedSet)
                                newSet.remove(list[which])
                                editor.putStringSet("excluded_folders", newSet).apply()
                                Toast.makeText(this, "Folder unhidden", Toast.LENGTH_SHORT).show()
                            }
                            .setNegativeButton("Cancel", null)
                            .show()
                    }
                    .setNegativeButton("Close", null)
                    .show()
            }
        }

        // 12. Wipe Playback Cache
        findViewById<RelativeLayout>(R.id.panelWipe).setOnClickListener {
            AlertDialog.Builder(this, androidx.appcompat.R.style.Theme_AppCompat_Dialog_Alert)
                .setTitle("Wipe Playback Cache?")
                .setMessage("This will delete all your watched video history and resume positions. This cannot be undone.")
                .setPositiveButton("Wipe") { _, _ ->
                    getSharedPreferences("VideoProgress", Context.MODE_PRIVATE).edit().clear().apply()
                    Toast.makeText(this, "All history and cache deleted successfully!", Toast.LENGTH_LONG).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        // 13. Switches
        val switchBgAudio = findViewById<SwitchCompat>(R.id.switchBgAudio)
        val switchBoost = findViewById<SwitchCompat>(R.id.switchBoost)
        val switchQuickPanel = findViewById<SwitchCompat>(R.id.switchQuickPanel)

        switchBgAudio.isChecked = sharedPref.getBoolean("bg_audio", false)
        switchBoost.isChecked = sharedPref.getBoolean("audio_boost", false)
        switchQuickPanel?.isChecked = sharedPref.getBoolean("quick_panel_enabled", true)

        switchBgAudio.setOnCheckedChangeListener { _, isChecked ->
            editor.putBoolean("bg_audio", isChecked).apply()
        }
        
        switchBoost.setOnCheckedChangeListener { _, isChecked ->
            editor.putBoolean("audio_boost", isChecked).apply()
            Toast.makeText(this, if (isChecked) "Audio boost up to 200% enabled" else "Audio boost disabled", Toast.LENGTH_SHORT).show()
        }

        switchQuickPanel?.setOnCheckedChangeListener { _, isChecked ->
            editor.putBoolean("quick_panel_enabled", isChecked).apply()
            Toast.makeText(this, if (isChecked) "YouTube Quick Panel enabled" else "Default dialog mode", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        val tvVaultSubtitle = findViewById<TextView>(R.id.tvVaultSubtitle)
        val isVaultReady = VaultSecurityManager.isVaultSetup(this)
        tvVaultSubtitle?.text = if (isVaultReady) "AES-256 PIN Protected" else "Tap to set up secure vault"
    }
}
