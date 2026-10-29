package com.Shyam.xplayer

import android.app.Activity
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.Shyam.xplayer.vault.VaultActivity
import com.Shyam.xplayer.vault.VaultSecurityManager
import com.bumptech.glide.Glide
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import wseemann.media.FFmpegMediaMetadataRetriever
import java.io.File

data class VideoFile(
    val id: Long,
    val name: String,
    val path: String,
    var durationMs: Long = 0L,
    var isSelected: Boolean = false
)

class VideosListActivity : AppCompatActivity() {

    private val allVideosList = ArrayList<VideoFile>()
    private val filteredVideosList = ArrayList<VideoFile>()
    private val selectedItems = HashSet<VideoFile>()

    private lateinit var rvVideos: RecyclerView
    private lateinit var videoAdapter: VideoAdapter
    private lateinit var tvVideoCount: TextView
    private lateinit var layoutSelectionBar: LinearLayout
    private lateinit var tvSelectionCount: TextView
    private lateinit var topHeaderVideos: LinearLayout
    private lateinit var layoutSearchBar: LinearLayout
    private lateinit var etSearchQuery: EditText
    private lateinit var tvEmptySearch: TextView

    private var isSelectionMode = false
    private var folderId: String? = null
    private var folderName: String = "Videos"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_videos_list)

        folderId = intent.getStringExtra("folder_id")
        folderName = intent.getStringExtra("folder_name") ?: "Videos"

        val tvFolderTitle = findViewById<TextView>(R.id.tvFolderTitle)
        tvVideoCount = findViewById(R.id.tvVideoCount)
        val btnBackVideos = findViewById<ImageButton>(R.id.btnBackVideos)
        topHeaderVideos = findViewById(R.id.top_header_videos)
        layoutSelectionBar = findViewById(R.id.layoutSelectionBar)
        tvSelectionCount = findViewById(R.id.tvSelectionCount)
        layoutSearchBar = findViewById(R.id.layoutSearchBar)
        etSearchQuery = findViewById(R.id.etSearchQuery)
        tvEmptySearch = findViewById(R.id.tvEmptySearch)
        rvVideos = findViewById(R.id.rvVideos)
        val rootLayout = findViewById<View>(R.id.videosListRoot)

        tvFolderTitle.text = folderName
        btnBackVideos.setOnClickListener { finish() }

        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { _, insets ->
            val statusBar = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val navBar = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val density = resources.displayMetrics.density
            topHeaderVideos.setPadding((16 * density).toInt(), statusBar.top + (8 * density).toInt(), (16 * density).toInt(), (8 * density).toInt())
            layoutSelectionBar.setPadding((16 * density).toInt(), statusBar.top + (8 * density).toInt(), (16 * density).toInt(), (8 * density).toInt())
            rvVideos.setPadding((10 * density).toInt(), (4 * density).toInt(), (10 * density).toInt(), navBar.bottom + (16 * density).toInt())
            insets
        }

        rvVideos.layoutManager = LinearLayoutManager(this)

        videoAdapter = VideoAdapter(
            videos = filteredVideosList,
            isSelectionMode = { isSelectionMode },
            onItemClick = { video ->
                if (isSelectionMode) {
                    toggleSelection(video)
                } else {
                    val intent = Intent(this, PlayerActivity::class.java).apply {
                        putExtra("video_uri", video.path)
                        putExtra("video_title", video.name)
                    }
                    startActivity(intent)
                }
            },
            onItemLongClick = { video ->
                if (!isSelectionMode) {
                    enterSelectionMode()
                    toggleSelection(video)
                }
            },
            onOptionsClick = { view, video ->
                showVideoPopupMenu(view, video)
            }
        )
        rvVideos.adapter = videoAdapter

        setupSearch()
        setupSelectionActions()

        if (folderId != null) {
            loadVideos(folderId!!)
        }
    }

    override fun onResume() {
        super.onResume()
        videoAdapter.notifyDataSetChanged()
    }

    private fun setupSearch() {
        findViewById<ImageButton>(R.id.btnSearchVideo).setOnClickListener {
            layoutSearchBar.visibility = if (layoutSearchBar.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            if (layoutSearchBar.visibility == View.VISIBLE) {
                etSearchQuery.requestFocus()
            } else {
                etSearchQuery.setText("")
            }
        }

        findViewById<ImageButton>(R.id.btnCloseSearch).setOnClickListener {
            layoutSearchBar.visibility = View.GONE
            etSearchQuery.setText("")
        }

        etSearchQuery.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterVideos(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun filterVideos(query: String) {
        val q = query.trim().lowercase()
        filteredVideosList.clear()
        if (q.isEmpty()) {
            filteredVideosList.addAll(allVideosList)
        } else {
            for (v in allVideosList) {
                if (v.name.lowercase().contains(q)) {
                    filteredVideosList.add(v)
                }
            }
        }
        tvEmptySearch.visibility = if (filteredVideosList.isEmpty() && q.isNotEmpty()) View.VISIBLE else View.GONE
        videoAdapter.notifyDataSetChanged()
    }

    private fun setupSelectionActions() {
        findViewById<ImageButton>(R.id.btnCloseSelection).setOnClickListener {
            exitSelectionMode()
        }

        findViewById<Button>(R.id.btnSelectAll).setOnClickListener {
            val allSelected = selectedItems.size == filteredVideosList.size
            selectedItems.clear()
            for (v in filteredVideosList) {
                v.isSelected = !allSelected
                if (!allSelected) selectedItems.add(v)
            }
            updateSelectionBar()
            videoAdapter.notifyDataSetChanged()
        }

        findViewById<ImageButton>(R.id.btnDeleteSelected).setOnClickListener {
            if (selectedItems.isNotEmpty()) {
                confirmDeleteSelectedVideos()
            }
        }
    }

    private fun enterSelectionMode() {
        isSelectionMode = true
        selectedItems.clear()
        topHeaderVideos.visibility = View.GONE
        layoutSelectionBar.visibility = View.VISIBLE
        updateSelectionBar()
        videoAdapter.notifyDataSetChanged()
    }

    private fun exitSelectionMode() {
        isSelectionMode = false
        for (v in allVideosList) v.isSelected = false
        selectedItems.clear()
        layoutSelectionBar.visibility = View.GONE
        topHeaderVideos.visibility = View.VISIBLE
        videoAdapter.notifyDataSetChanged()
    }

    private fun toggleSelection(video: VideoFile) {
        video.isSelected = !video.isSelected
        if (video.isSelected) selectedItems.add(video) else selectedItems.remove(video)
        if (selectedItems.isEmpty()) {
            exitSelectionMode()
        } else {
            updateSelectionBar()
            videoAdapter.notifyDataSetChanged()
        }
    }

    private fun updateSelectionBar() {
        tvSelectionCount.text = "${selectedItems.size} Selected"
    }

    private fun confirmDeleteSelectedVideos() {
        AlertDialog.Builder(this)
            .setTitle("Delete ${selectedItems.size} Videos?")
            .setMessage("These videos will be permanently deleted from your device storage.")
            .setPositiveButton("Delete") { _, _ ->
                deleteSelectedVideos()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteSelectedVideos() {
        val toDelete = ArrayList(selectedItems)
        var deletedCount = 0

        for (video in toDelete) {
            val deleted = deleteVideoFile(video)
            if (deleted) {
                deletedCount++
                allVideosList.remove(video)
                filteredVideosList.remove(video)
            }
        }

        exitSelectionMode()
        tvVideoCount.text = "${allVideosList.size} videos"
        videoAdapter.notifyDataSetChanged()
        Toast.makeText(this, "Deleted $deletedCount videos", Toast.LENGTH_SHORT).show()
    }

    private fun deleteVideoFile(video: VideoFile): Boolean {
        var deleted = false
        try {
            val contentUri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, video.id)
            val rows = contentResolver.delete(contentUri, null, null)
            if (rows > 0) deleted = true
        } catch (e: Exception) {
            // Fallback to direct file deletion
        }
        if (!deleted) {
            try {
                val f = File(video.path)
                if (f.exists() && f.delete()) deleted = true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return deleted
    }

    private fun showVideoPopupMenu(view: View, video: VideoFile) {
        val popup = PopupMenu(this, view)
        popup.menu.add(0, 1, 0, "Play Video")
        popup.menu.add(0, 2, 1, "Play Audio in Background")
        popup.menu.add(0, 3, 2, "Move to Private Vault")
        popup.menu.add(0, 4, 3, "Delete Video")

        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                1 -> {
                    val intent = Intent(this, PlayerActivity::class.java).apply {
                        putExtra("video_uri", video.path)
                        putExtra("video_title", video.name)
                    }
                    startActivity(intent)
                }
                2 -> {
                    val intent = Intent(this, PlayerActivity::class.java).apply {
                        putExtra("video_uri", video.path)
                        putExtra("video_title", video.name)
                        putExtra("audio_only_start", true)
                    }
                    startActivity(intent)
                }
                3 -> {
                    moveVideoToVault(video)
                }
                4 -> {
                    AlertDialog.Builder(this)
                        .setTitle("Delete Video?")
                        .setMessage("Are you sure you want to delete '${video.name}'?")
                        .setPositiveButton("Delete") { _, _ ->
                            if (deleteVideoFile(video)) {
                                allVideosList.remove(video)
                                filteredVideosList.remove(video)
                                videoAdapter.notifyDataSetChanged()
                                tvVideoCount.text = "${allVideosList.size} videos"
                                Toast.makeText(this, "Video deleted", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(this, "Could not delete video", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            }
            true
        }
        popup.show()
    }

    private fun moveVideoToVault(video: VideoFile) {
        if (!VaultSecurityManager.isVaultSetup(this)) {
            Toast.makeText(this, "Please set up Vault PIN first", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, VaultActivity::class.java))
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            val srcFile = File(video.path)
            if (!srcFile.exists()) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@VideosListActivity, "File not accessible directly", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }

            val vaultDir = VaultSecurityManager.getVaultDirectory(this@VideosListActivity)
            val destFile = File(vaultDir, "${video.name}.enc")
            try {
                VaultSecurityManager.encryptFile(srcFile, destFile)
                deleteVideoFile(video)
                withContext(Dispatchers.Main) {
                    allVideosList.remove(video)
                    filteredVideosList.remove(video)
                    videoAdapter.notifyDataSetChanged()
                    tvVideoCount.text = "${allVideosList.size} videos"
                    Toast.makeText(this@VideosListActivity, "Video secured inside Private Vault", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@VideosListActivity, "Failed encrypting: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun loadVideos(folderId: String) {
        allVideosList.clear()
        filteredVideosList.clear()

        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.DURATION
        )
        val selection = "${MediaStore.Video.Media.BUCKET_ID} = ?"
        val selectionArgs = arrayOf(folderId)
        val uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI

        val cursor = contentResolver.query(uri, projection, selection, selectionArgs, MediaStore.Video.Media.DATE_ADDED + " DESC")

        cursor?.use {
            val idCol = it.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameCol = it.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val dataCol = it.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
            val durationCol = it.getColumnIndex(MediaStore.Video.Media.DURATION)

            while (it.moveToNext()) {
                val id = it.getLong(idCol)
                val name = it.getString(nameCol) ?: "Video"
                val path = it.getString(dataCol) ?: ""
                val dur = if (durationCol != -1) it.getLong(durationCol) else 0L
                allVideosList.add(VideoFile(id, name, path, dur))
            }
        }

        filteredVideosList.addAll(allVideosList)
        tvVideoCount.text = "${allVideosList.size} videos"
        videoAdapter.notifyDataSetChanged()
    }
}

class VideoAdapter(
    private val videos: List<VideoFile>,
    private val isSelectionMode: () -> Boolean,
    private val onItemClick: (VideoFile) -> Unit,
    private val onItemLongClick: (VideoFile) -> Unit,
    private val onOptionsClick: (View, VideoFile) -> Unit
) : RecyclerView.Adapter<VideoAdapter.VideoViewHolder>() {

    class VideoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val root: View = view.findViewById(R.id.videoItemRoot)
        val cbSelect: CheckBox = view.findViewById(R.id.cbSelectVideo)
        val tvVideoName: TextView = view.findViewById(R.id.tvVideoName)
        val tvVideoFormat: TextView = view.findViewById(R.id.tvVideoFormat)
        val imgThumbnail: ImageView = view.findViewById(R.id.imgThumbnail)
        val tvDuration: TextView = view.findViewById(R.id.tvThumbnailDuration)
        val progressWatched: ProgressBar = view.findViewById(R.id.progressVideoWatched)
        val tvResumeBadge: TextView = view.findViewById(R.id.tvResumeBadge)
        val btnOptions: ImageButton = view.findViewById(R.id.btnVideoOptions)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_video_view, parent, false)
        return VideoViewHolder(view)
    }

    override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
        val video = videos[position]
        holder.tvVideoName.text = video.name

        val selectionActive = isSelectionMode()
        holder.cbSelect.visibility = if (selectionActive) View.VISIBLE else View.GONE
        holder.cbSelect.isChecked = video.isSelected
        holder.btnOptions.visibility = if (selectionActive) View.GONE else View.VISIBLE

        val fileExt = video.name.substringAfterLast('.', "").uppercase()
        holder.tvVideoFormat.text = fileExt

        // Check watched progress
        val prefs = holder.itemView.context.getSharedPreferences("VideoProgress", Context.MODE_PRIVATE)
        val savedTime = prefs.getLong(video.path, 0L)
        val savedTotal = prefs.getLong("${video.path}_total", video.durationMs)
        val effectiveTotal = if (savedTotal > 0L) savedTotal else video.durationMs

        if (savedTime > 5000L && effectiveTotal > 10000L && savedTime < effectiveTotal - 5000L) {
            val progressPercent = ((savedTime.toFloat() / effectiveTotal.toFloat()) * 100).toInt().coerceIn(1, 99)
            holder.progressWatched.visibility = View.VISIBLE
            holder.progressWatched.progress = progressPercent
            holder.tvResumeBadge.visibility = View.VISIBLE
            val min = (savedTime / 1000) / 60
            val sec = (savedTime / 1000) % 60
            holder.tvResumeBadge.text = String.format("Resume %02d:%02d", min, sec)
        } else {
            holder.progressWatched.visibility = View.GONE
            holder.tvResumeBadge.visibility = View.GONE
        }

        // Duration formatting
        if (video.durationMs > 0L) {
            val totalSec = video.durationMs / 1000
            val h = totalSec / 3600
            val m = (totalSec % 3600) / 60
            val s = totalSec % 60
            holder.tvDuration.text = if (h > 0) String.format("%02d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
            holder.tvDuration.visibility = View.VISIBLE
        } else {
            holder.tvDuration.visibility = View.GONE
        }

        holder.imgThumbnail.setImageResource(android.R.color.darker_gray)

        if (fileExt == "MKV" || fileExt == "HEVC") {
            CoroutineScope(Dispatchers.IO).launch {
                var bitmap: Bitmap? = null
                var durMs = video.durationMs
                try {
                    val mmr = FFmpegMediaMetadataRetriever()
                    mmr.setDataSource(video.path)
                    val durStr = mmr.extractMetadata(FFmpegMediaMetadataRetriever.METADATA_KEY_DURATION)
                    val extracted = durStr?.toLongOrNull() ?: 0L
                    if (extracted > 0L) durMs = extracted
                    bitmap = mmr.getFrameAtTime(2000000, FFmpegMediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    mmr.release()
                } catch (e: Exception) {
                    // Ignore
                }
                withContext(Dispatchers.Main) {
                    video.durationMs = durMs
                    if (durMs > 0L) {
                        val totalSec = durMs / 1000
                        val h = totalSec / 3600
                        val m = (totalSec % 3600) / 60
                        val s = totalSec % 60
                        holder.tvDuration.text = if (h > 0) String.format("%02d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
                        holder.tvDuration.visibility = View.VISIBLE
                    }
                    if (bitmap != null) {
                        Glide.with(holder.itemView.context.applicationContext)
                            .load(bitmap)
                            .override(320, 180)
                            .into(holder.imgThumbnail)
                    } else {
                        holder.imgThumbnail.setImageResource(android.R.drawable.ic_media_play)
                    }
                }
            }
        } else {
            Glide.with(holder.itemView.context.applicationContext)
                .asBitmap()
                .load(video.path)
                .override(320, 180)
                .error(android.R.drawable.ic_media_play)
                .into(holder.imgThumbnail)
        }

        holder.root.setOnClickListener { onItemClick(video) }
        holder.root.setOnLongClickListener {
            onItemLongClick(video)
            true
        }
        holder.btnOptions.setOnClickListener { onOptionsClick(it, video) }
    }

    override fun getItemCount() = videos.size
}
