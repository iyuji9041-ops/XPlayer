package com.Shyam.xplayer

import android.app.Dialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.media.audiofx.AudioEffect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.GestureDetector
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.bumptech.glide.Glide
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import java.io.File
import kotlin.math.abs

data class SideBtn(val id: Int, val iconResId: Int)

class PlayerActivity : AppCompatActivity() {

    private var libVlc: LibVLC? = null
    private var vlcPlayer: MediaPlayer? = null
    private lateinit var videoLayout: VLCVideoLayout

    private var isLocked = false
    private val isLockedState = mutableStateOf(false)
    private val isZoomingState = mutableStateOf(false)
    private var isAudioOnly = false
    private var areViewsAttached = false

    private val sleepHandler = Handler(Looper.getMainLooper())
    private var sleepRunnable: Runnable? = null

    private val uiHideHandler = Handler(Looper.getMainLooper())
    private var uiHideRunnable: Runnable? = null
    private val isComposeUiVisible = mutableStateOf(true)

    private var playlistFiles = listOf<File>()
    private var currentFileIndex = 0

    // Zoom and pan state
    private var currentScale = 1.0f
    private var panX = 0f
    private var panY = 0f
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private lateinit var scaleGestureDetector: ScaleGestureDetector
    private val zoomResetHandler = Handler(Looper.getMainLooper())

    // Aspect ratio cycling
    private var currentAspectIndex = 0
    private val aspectModes = arrayOf("Fit", "Fill Screen", "Crop / Zoom", "16:9", "4:3")

    private val NOTIF_CHANNEL_ID = "xplayer_bg_audio"
    private val NOTIF_ID = 9021

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        setContentView(R.layout.activity_player)

        videoLayout = findViewById(R.id.video_layout)
        
        val topBar = findViewById<View>(R.id.top_bar)
        topBar?.let { bar ->
            ViewCompat.setOnApplyWindowInsetsListener(bar) { v, insets ->
                val cutoutOrStatus = insets.getInsets(
                    WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout()
                )
                val density = resources.displayMetrics.density
                val padH = (24 * density).toInt()
                val padTop = cutoutOrStatus.top + (8 * density).toInt()
                val padBottom = (8 * density).toInt()
                v.setPadding(cutoutOrStatus.left + padH, padTop, cutoutOrStatus.right + padH, padBottom)
                insets
            }
        }

        setupPinchToZoom()
        setupPlayer()
        setupRealButtonLogic()

        if (intent.getBooleanExtra("audio_only_start", false)) {
            enableBackgroundAudioMode()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun setupPinchToZoom() {
        val osdFeedback = findViewById<TextView>(R.id.osd_feedback_text)

        scaleGestureDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                currentScale *= detector.scaleFactor
                currentScale = currentScale.coerceIn(0.5f, 4.0f)

                videoLayout.scaleX = currentScale
                videoLayout.scaleY = currentScale

                osdFeedback?.visibility = View.VISIBLE
                osdFeedback?.text = "Zoom: ${(currentScale * 100).toInt()}%"
                return true
            }

            override fun onScaleEnd(detector: ScaleGestureDetector) {
                zoomResetHandler.postDelayed({
                    osdFeedback?.visibility = View.GONE
                }, 1000)
            }
        })
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        // Detect 2-finger multi-touch for pinch-to-zoom
        if (ev.pointerCount >= 2) {
            isZoomingState.value = true
            scaleGestureDetector.onTouchEvent(ev)

            // Panning when zoomed in
            when (ev.actionMasked) {
                MotionEvent.ACTION_MOVE -> {
                    if (currentScale > 1.0f) {
                        val dx = ev.x - lastTouchX
                        val dy = ev.y - lastTouchY
                        panX += dx * 0.5f
                        panY += dy * 0.5f
                        videoLayout.translationX = panX
                        videoLayout.translationY = panY
                    }
                }
            }
            lastTouchX = ev.x
            lastTouchY = ev.y
            // Suppress all player controls during 2-finger touch
            return true
        } else {
            if (isZoomingState.value) {
                // Short grace period after 2-finger release so tap doesn't trigger
                zoomResetHandler.postDelayed({
                    isZoomingState.value = false
                }, 200)
            }
            lastTouchX = ev.x
            lastTouchY = ev.y
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun applyFlatGlassStyle(dialog: AlertDialog) {
        dialog.window?.let { window ->
            val params = window.attributes
            params.gravity = Gravity.END or Gravity.CENTER_VERTICAL
            params.x = 40
            params.width = (Resources.getSystem().displayMetrics.widthPixels * 0.45).toInt()
            window.attributes = params
            
            val glassDrawable = GradientDrawable().apply {
                setColor(Color.parseColor("#99000000"))
                cornerRadius = 48f
                setStroke(3, Color.parseColor("#33FFFFFF"))
            }
            window.setBackgroundDrawable(glassDrawable)
        }
    }

    private fun startSleepTimer(mins: Long) {
        sleepRunnable?.let { sleepHandler.removeCallbacks(it) }
        if (mins > 0) {
            sleepRunnable = Runnable { 
                vlcPlayer?.pause()
                finishAndRemoveTask()
            }
            sleepHandler.postDelayed(sleepRunnable!!, mins * 60 * 1000L)
            Toast.makeText(this, "Timer Set: $mins Mins", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showCustomTimerDialog() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = "Minutes (e.g. 45)"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#80FFFFFF"))
            setPadding(50, 50, 50, 50)
            background = null
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("Custom Sleep Timer")
            .setView(input)
            .setPositiveButton("Start") { _, _ ->
                val mins = input.text.toString().toLongOrNull() ?: 0L
                if (mins > 0) startSleepTimer(mins)
            }
            .setNegativeButton("Cancel", null)
            .create()
        dialog.show()
        applyFlatGlassStyle(dialog)
    }

    private fun showPlaylistDialog() {
        if (playlistFiles.isEmpty()) return
        
        val recyclerView = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@PlayerActivity)
            setPadding(24, 24, 24, 24)
            clipToPadding = false
        }
        
        val dialog = AlertDialog.Builder(this)
            .setTitle("Folder Videos")
            .setView(recyclerView)
            .create()
            
        recyclerView.adapter = PlaylistAdapter(playlistFiles, currentFileIndex) { index ->
            currentFileIndex = index
            playVideo(playlistFiles[index].absolutePath)
            dialog.dismiss()
        }
        
        dialog.show()
        
        dialog.window?.let { window ->
            val params = window.attributes
            params.gravity = Gravity.END or Gravity.CENTER_VERTICAL
            params.x = 40
            params.width = (Resources.getSystem().displayMetrics.widthPixels * 0.55).toInt()
            params.height = (Resources.getSystem().displayMetrics.heightPixels * 0.80).toInt()
            window.attributes = params
            
            val glassDrawable = GradientDrawable().apply {
                setColor(Color.parseColor("#E6000000"))
                cornerRadius = 48f
                setStroke(3, Color.parseColor("#33FFFFFF"))
            }
            window.setBackgroundDrawable(glassDrawable)
        }
    }

    private fun getProperFileName(path: String?): String {
        if (path.isNullOrEmpty()) return "Unknown Video"
        return try {
            File(Uri.parse(path).path ?: path).name
        } catch (e: Exception) {
            "Unknown Video"
        }
    }

    private fun setupRealButtonLogic() {
        val customControlsRoot = findViewById<View>(R.id.custom_controls_root)
        val topBar = customControlsRoot?.findViewById<View>(R.id.top_bar) ?: findViewById<View>(R.id.top_bar)
        val rvBottomButtons = customControlsRoot?.findViewById<RecyclerView>(R.id.rv_bottom_buttons) ?: findViewById<RecyclerView>(R.id.rv_bottom_buttons)
        val layoutSideLeftButtons = customControlsRoot?.findViewById<View>(R.id.layoutSideLeftButtons) ?: findViewById<View>(R.id.layoutSideLeftButtons)
        val btnLock = customControlsRoot?.findViewById<ImageButton>(R.id.btnLockCustom) ?: findViewById<ImageButton>(R.id.btnLockCustom)
        val btnLockTopLeft = customControlsRoot?.findViewById<ImageButton>(R.id.btnLockTopLeft) ?: findViewById<ImageButton>(R.id.btnLockTopLeft)
        val btnHeadphone = customControlsRoot?.findViewById<ImageButton>(R.id.btnHeadphone) ?: findViewById<ImageButton>(R.id.btnHeadphone)
        val btnBack = customControlsRoot?.findViewById<ImageButton>(R.id.btnBack) ?: findViewById<ImageButton>(R.id.btnBack)
        val tvVideoTitle = customControlsRoot?.findViewById<TextView>(R.id.tvVideoTitle) ?: findViewById<TextView>(R.id.tvVideoTitle)

        // Read UI hide timeout setting
        val sharedPrefs = getSharedPreferences("XPlayerSettings", Context.MODE_PRIVATE)
        val timeoutMode = sharedPrefs.getInt("ui_timeout_mode", 1)
        val timeoutMs = when(timeoutMode) {
            0 -> 3000L
            1 -> 5000L
            else -> 0L
        }

        fun showCustomUi() {
            if (isLocked) return
            isComposeUiVisible.value = true
            topBar?.visibility = View.VISIBLE
            layoutSideLeftButtons?.visibility = View.VISIBLE
            rvBottomButtons?.visibility = View.VISIBLE
            btnLockTopLeft?.visibility = View.GONE
            
            uiHideRunnable?.let { uiHideHandler.removeCallbacks(it) }
            if (timeoutMs > 0L) {
                uiHideRunnable?.let { uiHideHandler.postDelayed(it, timeoutMs) }
            }
        }

        fun hideCustomUi() {
            if (isLocked) return
            uiHideRunnable?.let { uiHideHandler.removeCallbacks(it) }
            isComposeUiVisible.value = false
            topBar?.visibility = View.GONE
            layoutSideLeftButtons?.visibility = View.GONE
            rvBottomButtons?.visibility = View.GONE
            btnLockTopLeft?.visibility = View.GONE
        }

        uiHideRunnable = Runnable {
            if (!isLocked) hideCustomUi()
        }

        val composeTimelineGestures = customControlsRoot?.findViewById<ComposeView>(R.id.compose_timeline_gestures) ?: findViewById<ComposeView>(R.id.compose_timeline_gestures)
        composeTimelineGestures?.setContent {
            XPlayerComposeControls(
                player = vlcPlayer,
                showUi = isComposeUiVisible.value,
                isLocked = isLockedState.value,
                isZooming = isZoomingState.value,
                onSwipe = { hideCustomUi() },
                onTapScreen = {
                    if (isComposeUiVisible.value) hideCustomUi() else showCustomUi()
                },
                onInteract = { 
                    uiHideRunnable?.let { uiHideHandler.removeCallbacks(it) }
                    if (timeoutMs > 0L) {
                        uiHideRunnable?.let { uiHideHandler.postDelayed(it, timeoutMs) }
                    }
                },
                onNextClick = {
                    if (playlistFiles.isNotEmpty() && currentFileIndex < playlistFiles.size - 1) {
                        currentFileIndex++
                        playVideo(playlistFiles[currentFileIndex].absolutePath)
                    }
                },
                onPrevClick = {
                    if (playlistFiles.isNotEmpty() && currentFileIndex > 0) {
                        currentFileIndex--
                        playVideo(playlistFiles[currentFileIndex].absolutePath)
                    }
                }
            )
        }

        var initialTitle = intent.getStringExtra("video_title")
        if (initialTitle.isNullOrEmpty()) {
            initialTitle = getProperFileName(intent.getStringExtra("video_uri"))
        }
        tvVideoTitle?.text = initialTitle

        vlcPlayer?.setEventListener { event ->
            when (event.type) {
                MediaPlayer.Event.MediaChanged -> {
                    val title = getProperFileName(playlistFiles.getOrNull(currentFileIndex)?.absolutePath)
                    runOnUiThread { tvVideoTitle?.text = title }
                }
                MediaPlayer.Event.EndReached -> {
                    // Video finished: clear unfinished progress
                    clearCurrentProgress()
                    if (currentFileIndex < playlistFiles.size - 1) {
                        currentFileIndex++
                        runOnUiThread { playVideo(playlistFiles[currentFileIndex].absolutePath) }
                    }
                }
            }
        }

        btnBack?.setOnClickListener { finish() }

        // Background Audio mode toggle via earphone icon
        btnHeadphone?.setOnClickListener {
            uiHideRunnable?.let { uiHideHandler.removeCallbacks(it) }
            if (timeoutMs > 0L) uiHideRunnable?.let { uiHideHandler.postDelayed(it, timeoutMs) }

            if (!isAudioOnly) {
                enableBackgroundAudioMode()
            } else {
                disableBackgroundAudioMode()
            }
        }

        // Lock button click
        btnLock?.setOnClickListener {
            lockScreen()
        }

        // Top-left unlock button click
        btnLockTopLeft?.setOnClickListener {
            unlockScreen()
        }

        // Corner Quick Settings Gear icon click
        val btnQuickSettingsCorner = customControlsRoot?.findViewById<ImageButton>(R.id.btnQuickSettingsCorner) ?: findViewById<ImageButton>(R.id.btnQuickSettingsCorner)
        btnQuickSettingsCorner?.setOnClickListener {
            uiHideRunnable?.let { uiHideHandler.removeCallbacks(it) }
            val quickPanelEnabled = sharedPrefs.getBoolean("quick_panel_enabled", true)
            if (quickPanelEnabled) {
                showQuickSettingsPanel()
            } else {
                startActivity(Intent(this, SettingsActivity::class.java))
            }
        }

        val myButtons = listOf(
            SideBtn(10, R.drawable.ic_settings_gear),
            SideBtn(1, R.drawable.ic_rotate),
            SideBtn(2, R.drawable.ic_playlist),
            SideBtn(3, R.drawable.ic_speed),
            SideBtn(4, R.drawable.ic_music),
            SideBtn(5, R.drawable.ic_subtitle),
            SideBtn(6, R.drawable.ic_eq),
            SideBtn(7, R.drawable.ic_timer),
            SideBtn(8, R.drawable.ic_pip),
            SideBtn(9, R.drawable.ic_aspect_ratio)
        )

        val adapterClickLogic: (Int) -> Unit = { buttonId ->
            uiHideRunnable?.let { uiHideHandler.removeCallbacks(it) }
            if (timeoutMs > 0L) uiHideRunnable?.let { uiHideHandler.postDelayed(it, timeoutMs) }
            when (buttonId) {
                1 -> {
                    requestedOrientation = if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    } else ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                }
                2 -> showPlaylistDialog()
                3 -> {
                    val speeds = arrayOf("0.5x", "0.75x", "1.0x", "1.25x", "1.5x", "2.0x")
                    val speedValues = floatArrayOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
                    val dialog = AlertDialog.Builder(this)
                        .setTitle("Speed")
                        .setItems(speeds) { _, which -> vlcPlayer?.rate = speedValues[which] }
                        .create()
                    dialog.show()
                    applyFlatGlassStyle(dialog)
                }
                4 -> {
                    vlcPlayer?.let { player ->
                        val tracks = player.audioTracks
                        if (tracks != null && tracks.isNotEmpty()) {
                            val trackNames = tracks.map { it.name }.toTypedArray()
                            val dialog = AlertDialog.Builder(this)
                                .setTitle("Select Audio Track")
                                .setItems(trackNames) { _, which -> player.setAudioTrack(tracks[which].id) }
                                .create()
                            dialog.show()
                            applyFlatGlassStyle(dialog)
                        } else {
                            Toast.makeText(this, "No audio tracks found", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                5 -> {
                    vlcPlayer?.let { player ->
                        val tracks = player.spuTracks
                        if (tracks != null && tracks.isNotEmpty()) {
                            val trackNames = tracks.map { it.name }.toTypedArray()
                            val dialog = AlertDialog.Builder(this)
                                .setTitle("Select Subtitle")
                                .setItems(trackNames) { _, which -> player.setSpuTrack(tracks[which].id) }
                                .create()
                            dialog.show()
                            applyFlatGlassStyle(dialog)
                        } else {
                            Toast.makeText(this, "No subtitles found", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                6 -> {
                    val intent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL)
                    intent.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, 0)
                    intent.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
                    intent.putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MOVIE)
                    try { startActivityForResult(intent, 0) } catch (e: Exception) { Toast.makeText(this, "Equalizer not supported.", Toast.LENGTH_SHORT).show() }
                }
                7 -> {
                    val timerOptions = arrayOf("15 Mins", "30 Mins", "60 Mins", "Custom", "Turn Off")
                    val dialog = AlertDialog.Builder(this)
                        .setTitle("Sleep Timer")
                        .setItems(timerOptions) { _, which ->
                            when (which) {
                                0 -> startSleepTimer(15)
                                1 -> startSleepTimer(30)
                                2 -> startSleepTimer(60)
                                3 -> showCustomTimerDialog()
                                4 -> {
                                    sleepRunnable?.let { sleepHandler.removeCallbacks(it) }
                                    Toast.makeText(this, "Timer Off", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }.create()
                    dialog.show()
                    applyFlatGlassStyle(dialog)
                }
                8 -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        try { enterPictureInPictureMode(PictureInPictureParams.Builder().build()) } catch (e: Exception) {}
                    }
                }
                9 -> {
                    cycleAspectRatio()
                }
                10 -> {
                    val quickPanelEnabled = sharedPrefs.getBoolean("quick_panel_enabled", true)
                    if (quickPanelEnabled) {
                        showQuickSettingsPanel()
                    } else {
                        try {
                            startActivity(Intent(this@PlayerActivity, SettingsActivity::class.java))
                        } catch (e: Exception) {
                            Toast.makeText(this, "Settings screen not found!", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }

        rvBottomButtons?.visibility = View.VISIBLE
        rvBottomButtons?.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        rvBottomButtons?.adapter = ControlsAdapter(myButtons, false, adapterClickLogic)

        showCustomUi()
    }

    private fun showQuickSettingsPanel() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_quick_settings_panel, null)
        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .create()

        val sharedPrefs = getSharedPreferences("XPlayerSettings", Context.MODE_PRIVATE)

        // 1. Audio Boost Switch
        val switchBoost = view.findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.switchQuickBoost)
        switchBoost?.isChecked = sharedPrefs.getBoolean("audio_boost", false)
        switchBoost?.setOnCheckedChangeListener { _, isChecked ->
            sharedPrefs.edit().putBoolean("audio_boost", isChecked).apply()
            vlcPlayer?.volume = if (isChecked) 200 else 100
            Toast.makeText(this, if (isChecked) "Audio Boost 200% Enabled" else "Normal Audio", Toast.LENGTH_SHORT).show()
        }

        // 2. Audio Track
        val tvAudioValue = view.findViewById<TextView>(R.id.tvQuickAudioTrackValue)
        val tvAudioLabel = view.findViewById<TextView>(R.id.tvAudioTrackLabel)
        val currentTracks = vlcPlayer?.audioTracks
        val trackCount = currentTracks?.size ?: 0
        tvAudioLabel?.text = "Audio track ($trackCount)"
        val currentTrackId = vlcPlayer?.audioTrack ?: -1
        val selectedTrack = currentTracks?.firstOrNull { it.id == currentTrackId }
        tvAudioValue?.text = selectedTrack?.name ?: "Default"

        view.findViewById<View>(R.id.rowAudioTrack)?.setOnClickListener {
            if (currentTracks != null && currentTracks.isNotEmpty()) {
                val trackNames = currentTracks.map { it.name }.toTypedArray()
                AlertDialog.Builder(this)
                    .setTitle("Select Audio Track")
                    .setItems(trackNames) { _, which ->
                        vlcPlayer?.setAudioTrack(currentTracks[which].id)
                        tvAudioValue?.text = currentTracks[which].name
                    }
                    .show()
            } else {
                Toast.makeText(this, "No alternate audio tracks", Toast.LENGTH_SHORT).show()
            }
        }

        // 3. Subtitles / CC
        val tvSubValue = view.findViewById<TextView>(R.id.tvQuickSubtitlesValue)
        val tvSubLabel = view.findViewById<TextView>(R.id.tvSubtitlesLabel)
        val currentSubs = vlcPlayer?.spuTracks
        val subCount = currentSubs?.size ?: 0
        tvSubLabel?.text = "Subtitles / CC ($subCount)"
        val currentSubId = vlcPlayer?.spuTrack ?: -1
        val selectedSub = currentSubs?.firstOrNull { it.id == currentSubId }
        tvSubValue?.text = selectedSub?.name ?: "Off"

        view.findViewById<View>(R.id.rowSubtitles)?.setOnClickListener {
            if (currentSubs != null && currentSubs.isNotEmpty()) {
                val names = currentSubs.map { it.name }.toTypedArray()
                AlertDialog.Builder(this)
                    .setTitle("Select Subtitle")
                    .setItems(names) { _, which ->
                        vlcPlayer?.setSpuTrack(currentSubs[which].id)
                        tvSubValue?.text = currentSubs[which].name
                    }
                    .show()
            } else {
                Toast.makeText(this, "No subtitles found", Toast.LENGTH_SHORT).show()
            }
        }

        // 4. Sleep Timer
        val tvTimerValue = view.findViewById<TextView>(R.id.tvQuickTimerValue)
        tvTimerValue?.text = if (sleepRunnable != null) "Active" else "Off"

        view.findViewById<View>(R.id.rowSleepTimer)?.setOnClickListener {
            val timerOptions = arrayOf("15 Mins", "30 Mins", "60 Mins", "Custom", "Turn Off")
            AlertDialog.Builder(this)
                .setTitle("Sleep Timer")
                .setItems(timerOptions) { _, which ->
                    when (which) {
                        0 -> { startSleepTimer(15); tvTimerValue?.text = "15m" }
                        1 -> { startSleepTimer(30); tvTimerValue?.text = "30m" }
                        2 -> { startSleepTimer(60); tvTimerValue?.text = "60m" }
                        3 -> showCustomTimerDialog()
                        4 -> {
                            sleepRunnable?.let { sleepHandler.removeCallbacks(it) }
                            sleepRunnable = null
                            tvTimerValue?.text = "Off"
                            Toast.makeText(this, "Timer Off", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                .show()
        }

        // 5. Playback Speed
        val tvSpeedValue = view.findViewById<TextView>(R.id.tvQuickSpeedValue)
        val currentRate = vlcPlayer?.rate ?: 1.0f
        tvSpeedValue?.text = if (currentRate == 1.0f) "Normal (1.0x)" else "${currentRate}x"

        view.findViewById<View>(R.id.rowPlaybackSpeed)?.setOnClickListener {
            val speeds = arrayOf("0.5x", "0.75x", "Normal (1.0x)", "1.25x", "1.5x", "2.0x")
            val speedValues = floatArrayOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
            AlertDialog.Builder(this)
                .setTitle("Playback Speed")
                .setItems(speeds) { _, which ->
                    vlcPlayer?.rate = speedValues[which]
                    tvSpeedValue?.text = speeds[which]
                }
                .show()
        }

        // 6. Aspect Ratio
        val tvAspectValue = view.findViewById<TextView>(R.id.tvQuickAspectValue)
        tvAspectValue?.text = aspectModes[currentAspectIndex]

        view.findViewById<View>(R.id.rowAspectRatio)?.setOnClickListener {
            cycleAspectRatio()
            tvAspectValue?.text = aspectModes[currentAspectIndex]
        }

        // 7. Background Audio Switch
        val switchBg = view.findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.switchQuickBgAudio)
        switchBg?.isChecked = isAudioOnly
        switchBg?.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) enableBackgroundAudioMode() else disableBackgroundAudioMode()
        }

        dialog.show()

        dialog.window?.let { w ->
            val lp = w.attributes
            val displayMetrics = resources.displayMetrics
            val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            if (isLandscape) {
                lp.gravity = Gravity.END or Gravity.CENTER_VERTICAL
                lp.x = 48
                lp.width = (displayMetrics.widthPixels * 0.44).toInt().coerceAtLeast(320)
            } else {
                lp.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                lp.y = 36
                lp.width = (displayMetrics.widthPixels * 0.92).toInt()
            }
            w.attributes = lp
            val glassDrawable = GradientDrawable().apply {
                setColor(Color.parseColor("#E6181818"))
                cornerRadius = 36f
                setStroke(2, Color.parseColor("#26FFFFFF"))
            }
            w.setBackgroundDrawable(glassDrawable)
        }
    }

    private fun cycleAspectRatio() {
        currentAspectIndex = (currentAspectIndex + 1) % aspectModes.size
        val mode = aspectModes[currentAspectIndex]

        when (currentAspectIndex) {
            0 -> { // Fit (Default)
                vlcPlayer?.aspectRatio = null
                vlcPlayer?.scale = 0f
            }
            1 -> { // Fill (Stretch to Screen)
                val display = resources.displayMetrics
                vlcPlayer?.aspectRatio = "${display.widthPixels}:${display.heightPixels}"
                vlcPlayer?.scale = 0f
            }
            2 -> { // Crop / Zoom (Full screen)
                vlcPlayer?.aspectRatio = null
                vlcPlayer?.scale = 1.35f
            }
            3 -> { // 16:9
                vlcPlayer?.aspectRatio = "16:9"
                vlcPlayer?.scale = 0f
            }
            4 -> { // 4:3
                vlcPlayer?.aspectRatio = "4:3"
                vlcPlayer?.scale = 0f
            }
        }

        val osdFeedback = findViewById<TextView>(R.id.osd_feedback_text)
        osdFeedback?.visibility = View.VISIBLE
        osdFeedback?.text = "Aspect: $mode"
        zoomResetHandler.postDelayed({ osdFeedback?.visibility = View.GONE }, 1200)
    }

    private fun lockScreen() {
        isLocked = true
        isLockedState.value = true
        uiHideRunnable?.let { uiHideHandler.removeCallbacks(it) }
        isComposeUiVisible.value = false

        findViewById<View>(R.id.top_bar)?.visibility = View.GONE
        findViewById<View>(R.id.layoutSideLeftButtons)?.visibility = View.GONE
        findViewById<View>(R.id.rv_bottom_buttons)?.visibility = View.GONE

        // Show unlock button at top-left corner
        val btnLockTopLeft = findViewById<ImageButton>(R.id.btnLockTopLeft)
        btnLockTopLeft?.visibility = View.VISIBLE
        Toast.makeText(this, "Screen Locked. Tap top-left to unlock.", Toast.LENGTH_SHORT).show()
    }

    private fun unlockScreen() {
        isLocked = false
        isLockedState.value = false
        findViewById<ImageButton>(R.id.btnLockTopLeft)?.visibility = View.GONE
        setupRealButtonLogic()
        Toast.makeText(this, "Screen Unlocked", Toast.LENGTH_SHORT).show()
    }

    private fun enableBackgroundAudioMode() {
        isAudioOnly = true
        val btnHeadphone = findViewById<ImageButton>(R.id.btnHeadphone)
        btnHeadphone?.setColorFilter(Color.parseColor("#818CF8"))
        showBackgroundNotification()
        Toast.makeText(this, "Background Audio ON - Playback continues when app is minimized", Toast.LENGTH_SHORT).show()
    }

    private fun disableBackgroundAudioMode() {
        isAudioOnly = false
        val btnHeadphone = findViewById<ImageButton>(R.id.btnHeadphone)
        btnHeadphone?.setColorFilter(Color.WHITE)
        dismissBackgroundNotification()
        Toast.makeText(this, "Background Audio OFF", Toast.LENGTH_SHORT).show()
    }

    private fun showBackgroundNotification() {
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    NOTIF_CHANNEL_ID,
                    "XPlayer Background Playback",
                    NotificationManager.IMPORTANCE_LOW
                )
                nm.createNotificationChannel(channel)
            }

            val currentTitle = playlistFiles.getOrNull(currentFileIndex)?.name ?: intent.getStringExtra("video_title") ?: "Video"
            val intent = Intent(this, PlayerActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

            val notif = NotificationCompat.Builder(this, NOTIF_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_earbud)
                .setContentTitle(currentTitle)
                .setContentText("Playing audio in background")
                .setContentIntent(pi)
                .setOngoing(true)
                .build()

            nm.notify(NOTIF_ID, notif)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun dismissBackgroundNotification() {
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(NOTIF_ID)
        } catch (e: Exception) {
            // Ignore
        }
    }

    private fun saveCurrentProgress() {
        vlcPlayer?.let { player ->
            if (player.time > 0L) {
                val currentPath = playlistFiles.getOrNull(currentFileIndex)?.absolutePath ?: intent.getStringExtra("video_uri")
                if (currentPath != null) {
                    val prefs = getSharedPreferences("VideoProgress", Context.MODE_PRIVATE).edit()
                    if (player.length > 0L && player.time >= player.length - 5000L) {
                        prefs.remove(currentPath).remove("${currentPath}_total").apply()
                    } else {
                        prefs.putLong(currentPath, player.time)
                        if (player.length > 0L) prefs.putLong("${currentPath}_total", player.length)
                        prefs.apply()
                    }
                }
            }
        }
    }

    private fun clearCurrentProgress() {
        val currentPath = playlistFiles.getOrNull(currentFileIndex)?.absolutePath ?: intent.getStringExtra("video_uri")
        if (currentPath != null) {
            getSharedPreferences("VideoProgress", Context.MODE_PRIVATE)
                .edit().remove(currentPath).remove("${currentPath}_total").apply()
        }
    }

    private fun loadFolderFiles(currentPath: String) {
        try {
            var file = File(currentPath)
            if (!file.exists()) {
                val uri = Uri.parse(currentPath)
                if (uri.path != null) file = File(uri.path!!)
            }
            
            val parent = file.parentFile
            if (parent != null && parent.isDirectory) {
                val videos = parent.listFiles { f ->
                    f.isFile && f.name.matches(Regex(".*\\.(mp4|mkv|avi|webm|mov)$", RegexOption.IGNORE_CASE))
                }?.sortedBy { it.name } ?: emptyList()
                
                playlistFiles = videos
                currentFileIndex = playlistFiles.indexOfFirst { it.absolutePath == file.absolutePath }.coerceAtLeast(0)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun playVideo(path: String) {
        try {
            saveCurrentProgress() 
            
            vlcPlayer?.stop()
            val media = if (path.startsWith("content://") || path.startsWith("http")) {
                Media(libVlc, Uri.parse(path))
            } else {
                Media(libVlc, path) 
            }

            // Read HW decoder setting
            val sharedPrefs = getSharedPreferences("XPlayerSettings", Context.MODE_PRIVATE)
            val hwMode = sharedPrefs.getInt("hw_decoder_mode", 0)
            media.setHWDecoderEnabled(hwMode == 0, false) 

            // Auto-resume check
            val resumeMode = sharedPrefs.getInt("auto_resume_mode", 0) 
            val savedTime = getSharedPreferences("VideoProgress", Context.MODE_PRIVATE).getLong(path, 0L)

            if (savedTime > 0L && resumeMode == 0) {
                media.addOption(":start-time=${savedTime / 1000}") 
            }

            // Audio Delay setting
            val audioDelayMs = sharedPrefs.getLong("audio_delay_ms", 0L)
            if (audioDelayMs != 0L) {
                vlcPlayer?.setAudioDelay(audioDelayMs * 1000L)
            }

            vlcPlayer?.media = media
            media.release()
            vlcPlayer?.play()

            if (savedTime > 0L && resumeMode == 1) {
                val min = (savedTime / 1000) / 60
                val sec = (savedTime / 1000) % 60
                val timeStr = String.format("%02d:%02d", min, sec)

                val dialog = AlertDialog.Builder(this, androidx.appcompat.R.style.Theme_AppCompat_Dialog_Alert)
                    .setTitle("Resume Video?")
                    .setMessage("Do you want to resume from $timeStr?")
                    .setPositiveButton("Resume") { d, _ ->
                        vlcPlayer?.time = savedTime 
                        d.dismiss()
                    }
                    .setNegativeButton("Start Over", null)
                    .show()
                applyFlatGlassStyle(dialog)
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Video Play Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun setupPlayer() {
        try {
            val args = ArrayList<String>()
            args.add("--vout=android-display") 
            args.add("--drop-late-frames")
            args.add("--skip-frames")
            
            libVlc = LibVLC(this, args)
            vlcPlayer = MediaPlayer(libVlc)
            
            safeAttachViews()

            val videoPath = intent.getStringExtra("video_uri")
            if (videoPath != null) {
                loadFolderFiles(videoPath)
                playVideo(videoPath)
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Engine Load Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun safeAttachViews() {
        try {
            if (!areViewsAttached && vlcPlayer != null) {
                vlcPlayer?.attachViews(videoLayout, null, false, false)
                areViewsAttached = true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun safeDetachViews() {
        try {
            if (areViewsAttached && vlcPlayer != null) {
                vlcPlayer?.detachViews()
                areViewsAttached = false
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onResume() {
        super.onResume()
        safeAttachViews()

        val sharedPref = getSharedPreferences("XPlayerSettings", Context.MODE_PRIVATE)
        val speedIndex = sharedPref.getInt("default_speed_index", 2)
        val speeds = floatArrayOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
        vlcPlayer?.rate = speeds[speedIndex]

        if (!isAudioOnly) {
            dismissBackgroundNotification()
        }
    }

    override fun onStop() {
        super.onStop()
        saveCurrentProgress()
        if (!isAudioOnly) {
            vlcPlayer?.pause()
            safeDetachViews()
        } else {
            // Keep playing audio in background!
            safeDetachViews()
            showBackgroundNotification()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        saveCurrentProgress() 
        dismissBackgroundNotification()
        sleepRunnable?.let { sleepHandler.removeCallbacks(it) }
        uiHideRunnable?.let { uiHideHandler.removeCallbacks(it) }
        safeDetachViews()
        try {
            vlcPlayer?.stop()
            vlcPlayer?.release()
            libVlc?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    inner class PlaylistAdapter(
        private val files: List<File>,
        private val currentPlayingIndex: Int,
        private val onItemClick: (Int) -> Unit
    ) : RecyclerView.Adapter<PlaylistAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val root = view as LinearLayout
            val imgThumbnail = ImageView(view.context)
            val tvTitle = TextView(view.context)

            init {
                root.orientation = LinearLayout.HORIZONTAL
                root.setPadding(0, 16, 0, 16)
                root.gravity = Gravity.CENTER_VERTICAL

                imgThumbnail.layoutParams = LinearLayout.LayoutParams(240, 135).apply {
                    marginEnd = 32
                }
                imgThumbnail.scaleType = ImageView.ScaleType.CENTER_CROP
                imgThumbnail.setBackgroundColor(Color.parseColor("#333333"))

                tvTitle.layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                tvTitle.textSize = 15f
                tvTitle.maxLines = 2
                tvTitle.ellipsize = android.text.TextUtils.TruncateAt.END

                root.addView(imgThumbnail)
                root.addView(tvTitle)
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val layout = LinearLayout(parent.context).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                val typedValue = android.util.TypedValue()
                context.theme.resolveAttribute(android.R.attr.selectableItemBackground, typedValue, true)
                background = androidx.core.content.ContextCompat.getDrawable(context, typedValue.resourceId)
                isClickable = true
                isFocusable = true
            }
            return ViewHolder(layout)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val file = files[position]
            holder.tvTitle.text = file.name
            
            if (currentPlayingIndex == position) {
                holder.tvTitle.setTextColor(Color.parseColor("#4CAF50"))
                holder.tvTitle.setTypeface(null, android.graphics.Typeface.BOLD)
            } else {
                holder.tvTitle.setTextColor(Color.WHITE)
                holder.tvTitle.setTypeface(null, android.graphics.Typeface.NORMAL)
            }

            Glide.with(holder.itemView.context)
                .load(Uri.fromFile(file))
                .override(240, 135)
                .into(holder.imgThumbnail)
            
            holder.root.setOnClickListener { onItemClick(position) }
        }

        override fun getItemCount(): Int = files.size
    }
}

class ControlsAdapter(
    private val buttons: List<SideBtn>,
    private val isInfinite: Boolean,
    private val onClick: (Int) -> Unit
) : RecyclerView.Adapter<ControlsAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val btnIcon: ImageButton = view.findViewById(R.id.btnSideIcon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_side_button, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val actualPosition = if (isInfinite) position % buttons.size else position
        val buttonItem = buttons[actualPosition]
        
        holder.btnIcon.setImageResource(buttonItem.iconResId)
        holder.btnIcon.setOnClickListener { onClick(buttonItem.id) }
    }

    override fun getItemCount(): Int = if (isInfinite) Int.MAX_VALUE else buttons.size
}
