package com.Shyam.xplayer

import android.app.Dialog
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
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
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
    private var isAudioOnly = false
    private var areViewsAttached = false

    private val sleepHandler = Handler(Looper.getMainLooper())
    private var sleepRunnable: Runnable? = null

    private val uiHideHandler = Handler(Looper.getMainLooper())
    private var uiHideRunnable: Runnable? = null
    private val isComposeUiVisible = mutableStateOf(true)

    private var playlistFiles = listOf<File>()
    private var currentFileIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Edge-to-edge & immersive full screen for modern Android & Xiaomi HyperOS
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
        
        // Ensure top control bar is padded correctly for status bar / camera punch hole on Xiaomi HyperOS
        val topBar = findViewById<View>(R.id.top_bar)
        topBar?.let { bar ->
            ViewCompat.setOnApplyWindowInsetsListener(bar) { v, insets ->
                val cutoutOrStatus = insets.getInsets(
                    WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout()
                )
                val density = resources.displayMetrics.density
                val padH = (16 * density).toInt()
                val padTop = cutoutOrStatus.top + (8 * density).toInt()
                val padBottom = (8 * density).toInt()
                v.setPadding(cutoutOrStatus.left + padH, padTop, cutoutOrStatus.right + padH, padBottom)
                insets
            }
        }

        setupPlayer()
        setupRealButtonLogic()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
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
        val rvSideButtons = customControlsRoot?.findViewById<RecyclerView>(R.id.rv_side_buttons) ?: findViewById<RecyclerView>(R.id.rv_side_buttons)
        val rvBottomButtons = customControlsRoot?.findViewById<RecyclerView>(R.id.rv_bottom_buttons) ?: findViewById<RecyclerView>(R.id.rv_bottom_buttons)
        val btnLock = customControlsRoot?.findViewById<ImageButton>(R.id.btnLockCustom) ?: findViewById<ImageButton>(R.id.btnLockCustom)
        val btnHeadphone = customControlsRoot?.findViewById<ImageButton>(R.id.btnHeadphone) ?: findViewById<ImageButton>(R.id.btnHeadphone)
        val btnBack = customControlsRoot?.findViewById<ImageButton>(R.id.btnBack) ?: findViewById<ImageButton>(R.id.btnBack)
        val tvVideoTitle = customControlsRoot?.findViewById<TextView>(R.id.tvVideoTitle) ?: findViewById<TextView>(R.id.tvVideoTitle)

        // 🟢 SETTINGS SE TIMEOUT READ KARNA
        val sharedPrefs = getSharedPreferences("XPlayerSettings", Context.MODE_PRIVATE)
        val timeoutMode = sharedPrefs.getInt("ui_timeout_mode", 1)
        val timeoutMs = when(timeoutMode) {
            0 -> 3000L  // 3 seconds
            1 -> 5000L  // 5 seconds
            else -> 0L  // Never hide
        }

        fun showCustomUi() {
            if (isLocked) return
            isComposeUiVisible.value = true
            topBar?.visibility = View.VISIBLE
            btnHeadphone?.visibility = View.VISIBLE
            rvBottomButtons?.visibility = View.VISIBLE
            
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
            btnHeadphone?.visibility = View.GONE
            rvBottomButtons?.visibility = View.GONE
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
                    if (currentFileIndex < playlistFiles.size - 1) {
                        currentFileIndex++
                        runOnUiThread { playVideo(playlistFiles[currentFileIndex].absolutePath) }
                    }
                }
            }
        }

        btnBack?.setOnClickListener { finish() }

        btnHeadphone?.setOnClickListener {
            uiHideRunnable?.let { uiHideHandler.removeCallbacks(it) }
            if (timeoutMs > 0L) uiHideRunnable?.let { uiHideHandler.postDelayed(it, timeoutMs) }
            isAudioOnly = !isAudioOnly
            if (isAudioOnly) {
                btnHeadphone.setColorFilter(Color.parseColor("#00FF00"))
                Toast.makeText(this, "Audio Mode ON", Toast.LENGTH_SHORT).show()
            } else {
                btnHeadphone.setColorFilter(Color.WHITE)
            }
        }

        val mySideButtons = listOf(
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
                    if (vlcPlayer?.scale == 0f) {
                        vlcPlayer?.scale = 1f 
                        Toast.makeText(this, "Crop", Toast.LENGTH_SHORT).show()
                    } else {
                        vlcPlayer?.scale = 0f 
                        Toast.makeText(this, "Fit", Toast.LENGTH_SHORT).show()
                    }
                }
                10 -> {
                    try {
                        val intent = Intent(this@PlayerActivity, SettingsActivity::class.java)
                        startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(this, "Settings screen not found!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        // 🟢 FIX: Ab koi Apple/Vertical style nahi. Hamesha bottom horizontal rahega.
        rvSideButtons?.visibility = View.GONE
        rvBottomButtons?.visibility = View.VISIBLE
        rvBottomButtons?.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        rvBottomButtons?.adapter = ControlsAdapter(mySideButtons, false, adapterClickLogic)

        btnLock?.setOnClickListener {
            isLocked = !isLocked
            isLockedState.value = isLocked
            if (isLocked) {
                btnLock.setColorFilter(Color.parseColor("#FF4C4C"))
                uiHideRunnable?.let { uiHideHandler.removeCallbacks(it) }
                isComposeUiVisible.value = false
                topBar?.visibility = View.GONE
                btnHeadphone?.visibility = View.GONE
                rvBottomButtons?.visibility = View.GONE 
                rvSideButtons?.visibility = View.GONE 
            } else {
                btnLock.setColorFilter(Color.WHITE)
                showCustomUi()
            }
        }

        showCustomUi()
    }

    // 🟢 MEMORY LOGIC: App band hone se pehle time save karo
    private fun saveCurrentProgress() {
        vlcPlayer?.let { player ->
            if (player.time > 0L && playlistFiles.isNotEmpty()) {
                val currentPath = playlistFiles.getOrNull(currentFileIndex)?.absolutePath
                if (currentPath != null) {
                    getSharedPreferences("VideoProgress", Context.MODE_PRIVATE)
                        .edit().putLong(currentPath, player.time).apply()
                }
            }
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

            // 🟢 SETTINGS SE HARDWARE DECODER READ KARNA
            val sharedPrefs = getSharedPreferences("XPlayerSettings", Context.MODE_PRIVATE)
            val hwMode = sharedPrefs.getInt("hw_decoder_mode", 0)
            media.setHWDecoderEnabled(hwMode == 0, false) 

            // 🟢 ASLI 23-MINUTE RESUME FIX YAHAN HAI
            val resumeMode = sharedPrefs.getInt("auto_resume_mode", 0) 
            val savedTime = getSharedPreferences("VideoProgress", Context.MODE_PRIVATE).getLong(path, 0L)

            if (savedTime > 0L && resumeMode == 0) {
                // VLC engine ko direct time bata diya video load hone se pehle
                media.addOption(":start-time=${savedTime / 1000}") 
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
        // Safely re-attach surface only if not already attached (prevents HyperOS/Android 14+ crash)
        safeAttachViews()

        val sharedPref = getSharedPreferences("XPlayerSettings", Context.MODE_PRIVATE)
        val speedIndex = sharedPref.getInt("default_speed_index", 2)
        val speeds = floatArrayOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
        vlcPlayer?.rate = speeds[speedIndex]
    }

    override fun onStop() {
        super.onStop()
        saveCurrentProgress()
        if (!isAudioOnly) {
            vlcPlayer?.pause()
        }
        safeDetachViews()
    }

    override fun onDestroy() {
        super.onDestroy()
        saveCurrentProgress() 
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

            com.bumptech.glide.Glide.with(holder.itemView.context)
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