package com.Shyam.xplayer

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.Shyam.xplayer.music.MusicPlayerManager
import com.Shyam.xplayer.music.MusicTrack
import com.bumptech.glide.Glide
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class VideoFolder(
    val id: String,
    val name: String,
    var videoCount: Int = 0,
    var hasUnfinishedVideos: Boolean = false
)

data class DownloadedVideo(
    val file: File,
    val name: String,
    val sizeBytes: Long
)

class MainActivity : AppCompatActivity() {

    private enum class Tab { HOME, DOWNLOADS, MUSIC }
    private var currentTab = Tab.HOME

    private val foldersList = ArrayList<VideoFolder>()
    private val filteredFoldersList = ArrayList<VideoFolder>()

    private val downloadsList = ArrayList<DownloadedVideo>()
    private val filteredDownloadsList = ArrayList<DownloadedVideo>()

    private val musicList = ArrayList<MusicTrack>()
    private val filteredMusicList = ArrayList<MusicTrack>()

    private lateinit var rvFolders: RecyclerView
    private lateinit var rvDownloads: RecyclerView
    private lateinit var rvMusic: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvTitle: TextView
    private lateinit var tvEmptyTab: TextView

    private lateinit var folderAdapter: FolderAdapter
    private lateinit var downloadAdapter: DownloadAdapter
    private lateinit var musicAdapter: MusicAdapter

    private lateinit var layoutSearch: LinearLayout
    private lateinit var etSearch: EditText

    // Mini Player Views
    private lateinit var layoutMiniPlayer: LinearLayout
    private lateinit var tvMiniTitle: TextView
    private lateinit var tvMiniArtist: TextView
    private lateinit var btnMiniPlayPause: ImageButton

    // Nav Views
    private lateinit var navTabHome: View
    private lateinit var navTabDownloads: View
    private lateinit var navTabMusic: View
    private lateinit var iconNavHome: ImageView
    private lateinit var iconNavDownloads: ImageView
    private lateinit var iconNavMusic: ImageView
    private lateinit var tvNavHome: TextView
    private lateinit var tvNavDownloads: TextView
    private lateinit var tvNavMusic: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)

        initViews()
        setupWindowInsets()
        setupAdapters()
        setupNavigation()
        setupSearch()
        setupMiniPlayer()

        if (checkPermissions()) {
            loadCurrentTabData()
        } else {
            requestPermissions()
        }
    }

    override fun onResume() {
        super.onResume()
        // Highlight updates when coming back from watching video
        if (currentTab == Tab.HOME && foldersList.isNotEmpty()) {
            checkFolderHighlights()
        }
        updateMiniPlayerState()
    }

    private fun initViews() {
        rvFolders = findViewById(R.id.rvFolders)
        rvDownloads = findViewById(R.id.rvDownloads)
        rvMusic = findViewById(R.id.rvMusic)
        progressBar = findViewById(R.id.progressBar)
        tvTitle = findViewById(R.id.tvTitle)
        tvEmptyTab = findViewById(R.id.tvEmptyTab)

        layoutSearch = findViewById(R.id.layoutMainSearch)
        etSearch = findViewById(R.id.etMainSearch)

        layoutMiniPlayer = findViewById(R.id.layoutMiniMusicPlayer)
        tvMiniTitle = findViewById(R.id.tvMiniTrackTitle)
        tvMiniArtist = findViewById(R.id.tvMiniArtist)
        btnMiniPlayPause = findViewById(R.id.btnMiniPlayPause)

        navTabHome = findViewById(R.id.navTabHome)
        navTabDownloads = findViewById(R.id.navTabDownloads)
        navTabMusic = findViewById(R.id.navTabMusic)
        iconNavHome = findViewById(R.id.iconNavHome)
        iconNavDownloads = findViewById(R.id.iconNavDownloads)
        iconNavMusic = findViewById(R.id.iconNavMusic)
        tvNavHome = findViewById(R.id.tvNavHome)
        tvNavDownloads = findViewById(R.id.tvNavDownloads)
        tvNavMusic = findViewById(R.id.tvNavMusic)

        findViewById<ImageView>(R.id.btnPlayLink).setOnClickListener {
            startActivity(Intent(this, OnlineStreamActivity::class.java))
        }

        findViewById<ImageView>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        findViewById<ImageView>(R.id.btnMainSearch).setOnClickListener {
            layoutSearch.visibility = if (layoutSearch.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            if (layoutSearch.visibility == View.VISIBLE) {
                etSearch.requestFocus()
            } else {
                etSearch.setText("")
            }
        }

        findViewById<ImageButton>(R.id.btnCloseMainSearch).setOnClickListener {
            layoutSearch.visibility = View.GONE
            etSearch.setText("")
        }
    }

    private fun setupWindowInsets() {
        val mainRoot = findViewById<View>(R.id.mainRoot)
        val topHeader = findViewById<View>(R.id.top_header)
        val bottomNav = findViewById<View>(R.id.bottomPlayerAndNavContainer)

        ViewCompat.setOnApplyWindowInsetsListener(mainRoot) { _, insets ->
            val statusBar = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val navBar = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val density = resources.displayMetrics.density

            topHeader.setPadding(
                (20 * density).toInt(),
                statusBar.top + (10 * density).toInt(),
                (20 * density).toInt(),
                (10 * density).toInt()
            )
            bottomNav.setPadding(0, 0, 0, navBar.bottom)
            insets
        }
    }

    private fun setupAdapters() {
        rvFolders.layoutManager = LinearLayoutManager(this)
        folderAdapter = FolderAdapter(
            folders = filteredFoldersList,
            onClick = { folder ->
                val intent = Intent(this, VideosListActivity::class.java)
                intent.putExtra("folder_id", folder.id)
                intent.putExtra("folder_name", folder.name)
                startActivity(intent)
            },
            onOptionsClick = { view, folder ->
                showFolderOptions(view, folder)
            }
        )
        rvFolders.adapter = folderAdapter

        rvDownloads.layoutManager = LinearLayoutManager(this)
        downloadAdapter = DownloadAdapter(
            downloads = filteredDownloadsList,
            onClick = { item ->
                val intent = Intent(this, PlayerActivity::class.java).apply {
                    putExtra("video_uri", item.file.absolutePath)
                    putExtra("video_title", item.name)
                }
                startActivity(intent)
            },
            onOptionsClick = { view, item ->
                showDownloadOptions(view, item)
            }
        )
        rvDownloads.adapter = downloadAdapter

        rvMusic.layoutManager = LinearLayoutManager(this)
        musicAdapter = MusicAdapter(
            tracks = filteredMusicList,
            onClick = { track, position ->
                MusicPlayerManager.playTrack(this, filteredMusicList, position)
            }
        )
        rvMusic.adapter = musicAdapter
    }

    private fun setupNavigation() {
        navTabHome.setOnClickListener { switchTab(Tab.HOME) }
        navTabDownloads.setOnClickListener { switchTab(Tab.DOWNLOADS) }
        navTabMusic.setOnClickListener { switchTab(Tab.MUSIC) }
    }

    private fun switchTab(tab: Tab) {
        currentTab = tab
        val activeColor = Color.parseColor("#818CF8")
        val inactiveColor = Color.parseColor("#80FFFFFF")

        iconNavHome.setColorFilter(if (tab == Tab.HOME) activeColor else inactiveColor)
        tvNavHome.setTextColor(if (tab == Tab.HOME) activeColor else inactiveColor)

        iconNavDownloads.setColorFilter(if (tab == Tab.DOWNLOADS) activeColor else inactiveColor)
        tvNavDownloads.setTextColor(if (tab == Tab.DOWNLOADS) activeColor else inactiveColor)

        iconNavMusic.setColorFilter(if (tab == Tab.MUSIC) activeColor else inactiveColor)
        tvNavMusic.setTextColor(if (tab == Tab.MUSIC) activeColor else inactiveColor)

        rvFolders.visibility = if (tab == Tab.HOME) View.VISIBLE else View.GONE
        rvDownloads.visibility = if (tab == Tab.DOWNLOADS) View.VISIBLE else View.GONE
        rvMusic.visibility = if (tab == Tab.MUSIC) View.VISIBLE else View.GONE

        tvTitle.text = when (tab) {
            Tab.HOME -> "Video Folders"
            Tab.DOWNLOADS -> "Downloads"
            Tab.MUSIC -> "Music Player"
        }

        loadCurrentTabData()
    }

    private fun setupSearch() {
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterCurrentTab(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun filterCurrentTab(query: String) {
        val q = query.trim().lowercase()
        when (currentTab) {
            Tab.HOME -> {
                filteredFoldersList.clear()
                if (q.isEmpty()) {
                    filteredFoldersList.addAll(foldersList)
                } else {
                    for (f in foldersList) {
                        if (f.name.lowercase().contains(q)) filteredFoldersList.add(f)
                    }
                }
                folderAdapter.notifyDataSetChanged()
                tvEmptyTab.visibility = if (filteredFoldersList.isEmpty() && q.isNotEmpty()) View.VISIBLE else View.GONE
                tvEmptyTab.text = "No folders matching '$query'"
            }
            Tab.DOWNLOADS -> {
                filteredDownloadsList.clear()
                if (q.isEmpty()) {
                    filteredDownloadsList.addAll(downloadsList)
                } else {
                    for (d in downloadsList) {
                        if (d.name.lowercase().contains(q)) filteredDownloadsList.add(d)
                    }
                }
                downloadAdapter.notifyDataSetChanged()
                tvEmptyTab.visibility = if (filteredDownloadsList.isEmpty() && q.isNotEmpty()) View.VISIBLE else View.GONE
                tvEmptyTab.text = "No downloads matching '$query'"
            }
            Tab.MUSIC -> {
                filteredMusicList.clear()
                if (q.isEmpty()) {
                    filteredMusicList.addAll(musicList)
                } else {
                    for (m in musicList) {
                        if (m.title.lowercase().contains(q) || m.artist.lowercase().contains(q)) {
                            filteredMusicList.add(m)
                        }
                    }
                }
                musicAdapter.notifyDataSetChanged()
                tvEmptyTab.visibility = if (filteredMusicList.isEmpty() && q.isNotEmpty()) View.VISIBLE else View.GONE
                tvEmptyTab.text = "No songs matching '$query'"
            }
        }
    }

    private fun setupMiniPlayer() {
        btnMiniPlayPause.setOnClickListener {
            MusicPlayerManager.togglePlayPause(this)
        }

        findViewById<ImageButton>(R.id.btnMiniNext).setOnClickListener {
            MusicPlayerManager.playNext(this)
        }

        findViewById<ImageButton>(R.id.btnMiniClose).setOnClickListener {
            MusicPlayerManager.stop()
            layoutMiniPlayer.visibility = View.GONE
        }

        MusicPlayerManager.onTrackChanged = { track ->
            runOnUiThread {
                if (track != null) {
                    layoutMiniPlayer.visibility = View.VISIBLE
                    tvMiniTitle.text = track.title
                    tvMiniArtist.text = track.artist
                } else {
                    layoutMiniPlayer.visibility = View.GONE
                }
            }
        }

        MusicPlayerManager.onPlaybackStateChanged = { isPlaying ->
            runOnUiThread {
                btnMiniPlayPause.setImageResource(
                    if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
                )
            }
        }
    }

    private fun updateMiniPlayerState() {
        val currentTrack = MusicPlayerManager.getCurrentTrack()
        if (currentTrack != null) {
            layoutMiniPlayer.visibility = View.VISIBLE
            tvMiniTitle.text = currentTrack.title
            tvMiniArtist.text = currentTrack.artist
            btnMiniPlayPause.setImageResource(
                if (MusicPlayerManager.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
            )
        } else {
            layoutMiniPlayer.visibility = View.GONE
        }
    }

    private fun loadCurrentTabData() {
        when (currentTab) {
            Tab.HOME -> if (foldersList.isEmpty()) loadFolders()
            Tab.DOWNLOADS -> loadDownloads()
            Tab.MUSIC -> if (musicList.isEmpty()) loadMusic()
        }
    }

    private fun loadFolders() {
        progressBar.visibility = View.VISIBLE
        foldersList.clear()
        filteredFoldersList.clear()

        CoroutineScope(Dispatchers.IO).launch {
            val projection = arrayOf(
                MediaStore.Video.Media.BUCKET_ID,
                MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
                MediaStore.Video.Media.DATA
            )
            val uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            val cursor = contentResolver.query(uri, projection, null, null, MediaStore.Video.Media.DATE_ADDED + " DESC")

            val folderMap = LinkedHashMap<String, VideoFolder>()
            val progressPrefs = getSharedPreferences("VideoProgress", Context.MODE_PRIVATE)

            cursor?.use {
                val bucketIdCol = it.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_ID)
                val bucketNameCol = it.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
                val dataCol = it.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)

                while (it.moveToNext()) {
                    val bucketId = it.getString(bucketIdCol)
                    val bucketName = it.getString(bucketNameCol) ?: "Unknown"
                    val path = it.getString(dataCol) ?: ""

                    val folder = folderMap.getOrPut(bucketId) { VideoFolder(bucketId, bucketName) }
                    folder.videoCount++

                    if (!folder.hasUnfinishedVideos && path.isNotEmpty()) {
                        val savedProgress = progressPrefs.getLong(path, 0L)
                        val totalDuration = progressPrefs.getLong("${path}_total", 0L)
                        if (savedProgress > 5000L && (totalDuration == 0L || savedProgress < totalDuration - 5000L)) {
                            folder.hasUnfinishedVideos = true
                        }
                    }
                }
            }

            withContext(Dispatchers.Main) {
                progressBar.visibility = View.GONE
                foldersList.addAll(folderMap.values)
                filteredFoldersList.addAll(foldersList)
                folderAdapter.notifyDataSetChanged()
                tvEmptyTab.visibility = if (foldersList.isEmpty()) View.VISIBLE else View.GONE
                tvEmptyTab.text = "No video folders found on device"
            }
        }
    }

    private fun checkFolderHighlights() {
        CoroutineScope(Dispatchers.IO).launch {
            val progressPrefs = getSharedPreferences("VideoProgress", Context.MODE_PRIVATE)
            val projection = arrayOf(MediaStore.Video.Media.BUCKET_ID, MediaStore.Video.Media.DATA)
            val uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            val cursor = contentResolver.query(uri, projection, null, null, null)

            val unfinishedFolderIds = HashSet<String>()
            cursor?.use {
                val bucketCol = it.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_ID)
                val dataCol = it.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
                while (it.moveToNext()) {
                    val bucketId = it.getString(bucketCol)
                    val path = it.getString(dataCol) ?: ""
                    if (path.isNotEmpty()) {
                        val saved = progressPrefs.getLong(path, 0L)
                        val total = progressPrefs.getLong("${path}_total", 0L)
                        if (saved > 5000L && (total == 0L || saved < total - 5000L)) {
                            unfinishedFolderIds.add(bucketId)
                        }
                    }
                }
            }

            withContext(Dispatchers.Main) {
                for (folder in foldersList) {
                    folder.hasUnfinishedVideos = unfinishedFolderIds.contains(folder.id)
                }
                folderAdapter.notifyDataSetChanged()
            }
        }
    }

    private fun showFolderOptions(view: View, folder: VideoFolder) {
        val popup = PopupMenu(this, view)
        popup.menu.add(0, 1, 0, "Open Folder")
        popup.menu.add(0, 2, 1, "Delete Folder")

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> {
                    val intent = Intent(this, VideosListActivity::class.java)
                    intent.putExtra("folder_id", folder.id)
                    intent.putExtra("folder_name", folder.name)
                    startActivity(intent)
                }
                2 -> {
                    AlertDialog.Builder(this)
                        .setTitle("Delete Folder '${folder.name}'?")
                        .setMessage("This will delete all ${folder.videoCount} videos in this folder from your device storage.")
                        .setPositiveButton("Delete") { _, _ ->
                            deleteFolderVideos(folder)
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            }
            true
        }
        popup.show()
    }

    private fun deleteFolderVideos(folder: VideoFolder) {
        progressBar.visibility = View.VISIBLE
        CoroutineScope(Dispatchers.IO).launch {
            val selection = "${MediaStore.Video.Media.BUCKET_ID} = ?"
            val selectionArgs = arrayOf(folder.id)
            val rows = try {
                contentResolver.delete(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, selection, selectionArgs)
            } catch (e: Exception) {
                0
            }

            withContext(Dispatchers.Main) {
                progressBar.visibility = View.GONE
                foldersList.remove(folder)
                filteredFoldersList.remove(folder)
                folderAdapter.notifyDataSetChanged()
                Toast.makeText(this@MainActivity, "Folder deleted ($rows videos removed)", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadDownloads() {
        progressBar.visibility = View.VISIBLE
        downloadsList.clear()
        filteredDownloadsList.clear()

        CoroutineScope(Dispatchers.IO).launch {
            val candidateDirs = listOf(
                File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "NovaGet"),
                File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "XPlayer"),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "NovaGet")
            )

            val videoExtensions = setOf("mp4", "mkv", "webm", "avi", "mov")
            val foundFiles = ArrayList<DownloadedVideo>()

            for (dir in candidateDirs) {
                if (dir.exists() && dir.isDirectory) {
                    val files = dir.listFiles { f ->
                        f.isFile && videoExtensions.contains(f.extension.lowercase())
                    }
                    if (files != null) {
                        for (f in files) {
                            foundFiles.add(DownloadedVideo(f, f.name, f.length()))
                        }
                    }
                }
            }

            foundFiles.sortByDescending { it.file.lastModified() }

            withContext(Dispatchers.Main) {
                progressBar.visibility = View.GONE
                downloadsList.addAll(foundFiles)
                filteredDownloadsList.addAll(downloadsList)
                downloadAdapter.notifyDataSetChanged()
                tvEmptyTab.visibility = if (downloadsList.isEmpty()) View.VISIBLE else View.GONE
                tvEmptyTab.text = "No downloaded videos found.\nTap link button above to download videos with yt-dlp."
            }
        }
    }

    private fun showDownloadOptions(view: View, item: DownloadedVideo) {
        val popup = PopupMenu(this, view)
        popup.menu.add(0, 1, 0, "Play")
        popup.menu.add(0, 2, 1, "Delete")

        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                1 -> {
                    val intent = Intent(this, PlayerActivity::class.java).apply {
                        putExtra("video_uri", item.file.absolutePath)
                        putExtra("video_title", item.name)
                    }
                    startActivity(intent)
                }
                2 -> {
                    AlertDialog.Builder(this)
                        .setTitle("Delete Download?")
                        .setMessage("Delete '${item.name}' from storage?")
                        .setPositiveButton("Delete") { _, _ ->
                            if (item.file.delete()) {
                                downloadsList.remove(item)
                                filteredDownloadsList.remove(item)
                                downloadAdapter.notifyDataSetChanged()
                                Toast.makeText(this, "File deleted", Toast.LENGTH_SHORT).show()
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

    private fun loadMusic() {
        progressBar.visibility = View.VISIBLE
        musicList.clear()
        filteredMusicList.clear()

        CoroutineScope(Dispatchers.IO).launch {
            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.DATA
            )
            val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
            val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            val cursor = contentResolver.query(uri, projection, selection, null, MediaStore.Audio.Media.TITLE + " ASC")

            val tracks = ArrayList<MusicTrack>()
            cursor?.use {
                val idCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val dataCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)

                while (it.moveToNext()) {
                    val id = it.getLong(idCol)
                    val title = it.getString(titleCol) ?: "Audio"
                    val artist = it.getString(artistCol) ?: "Unknown Artist"
                    val album = it.getString(albumCol) ?: "Unknown Album"
                    val dur = it.getLong(durCol)
                    val path = it.getString(dataCol) ?: ""
                    val trackUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)

                    tracks.add(MusicTrack(id, title, artist, album, dur, path, trackUri))
                }
            }

            withContext(Dispatchers.Main) {
                progressBar.visibility = View.GONE
                musicList.addAll(tracks)
                filteredMusicList.addAll(musicList)
                musicAdapter.notifyDataSetChanged()
                tvEmptyTab.visibility = if (musicList.isEmpty()) View.VISIBLE else View.GONE
                tvEmptyTab.text = "No music files found on device"
            }
        }
    }

    private fun checkPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO),
                100
            )
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                100
            )
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            loadCurrentTabData()
        }
    }
}

// =====================================
// FOLDER ADAPTER
// =====================================
class FolderAdapter(
    private val folders: List<VideoFolder>,
    private val onClick: (VideoFolder) -> Unit,
    private val onOptionsClick: (View, VideoFolder) -> Unit
) : RecyclerView.Adapter<FolderAdapter.FolderViewHolder>() {

    class FolderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val root: View = view.findViewById(R.id.folderCardRoot)
        val tvFolderName: TextView = view.findViewById(R.id.tvFolderName)
        val tvFolderCount: TextView = view.findViewById(R.id.tvFolderCount)
        val tvResumeTag: TextView = view.findViewById(R.id.tvFolderResumeTag)
        val viewDot: View = view.findViewById(R.id.viewFolderUnfinishedDot)
        val btnOptions: ImageButton = view.findViewById(R.id.btnFolderOptions)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FolderViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_folder_view, parent, false)
        return FolderViewHolder(view)
    }

    override fun onBindViewHolder(holder: FolderViewHolder, position: Int) {
        val folder = folders[position]
        holder.tvFolderName.text = folder.name
        holder.tvFolderCount.text = "${folder.videoCount} videos"

        if (folder.hasUnfinishedVideos) {
            holder.tvResumeTag.visibility = View.VISIBLE
            holder.viewDot.visibility = View.VISIBLE
        } else {
            holder.tvResumeTag.visibility = View.GONE
            holder.viewDot.visibility = View.GONE
        }

        holder.root.setOnClickListener { onClick(folder) }
        holder.btnOptions.setOnClickListener { onOptionsClick(it, folder) }
    }

    override fun getItemCount() = folders.size
}

// =====================================
// DOWNLOAD ADAPTER
// =====================================
class DownloadAdapter(
    private val downloads: List<DownloadedVideo>,
    private val onClick: (DownloadedVideo) -> Unit,
    private val onOptionsClick: (View, DownloadedVideo) -> Unit
) : RecyclerView.Adapter<DownloadAdapter.DownloadViewHolder>() {

    class DownloadViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView = view.findViewById(R.id.tvDownloadTitle)
        val tvMeta: TextView = view.findViewById(R.id.tvDownloadMeta)
        val imgThumb: ImageView = view.findViewById(R.id.imgDownloadThumb)
        val btnOptions: ImageButton = view.findViewById(R.id.btnDownloadOptions)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DownloadViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_download_video, parent, false)
        return DownloadViewHolder(view)
    }

    override fun onBindViewHolder(holder: DownloadViewHolder, position: Int) {
        val item = downloads[position]
        holder.tvTitle.text = item.name
        val mb = item.sizeBytes / (1024 * 1024f)
        val ext = item.file.extension.uppercase()
        holder.tvMeta.text = String.format("%s • %.1f MB", ext, mb)

        Glide.with(holder.itemView.context.applicationContext)
            .asBitmap()
            .load(item.file.absolutePath)
            .override(240, 140)
            .into(holder.imgThumb)

        holder.itemView.setOnClickListener { onClick(item) }
        holder.btnOptions.setOnClickListener { onOptionsClick(it, item) }
    }

    override fun getItemCount() = downloads.size
}

// =====================================
// MUSIC ADAPTER
// =====================================
class MusicAdapter(
    private val tracks: List<MusicTrack>,
    private val onClick: (MusicTrack, Int) -> Unit
) : RecyclerView.Adapter<MusicAdapter.MusicViewHolder>() {

    class MusicViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView = view.findViewById(R.id.tvMusicTitle)
        val tvArtist: TextView = view.findViewById(R.id.tvMusicArtist)
        val tvDuration: TextView = view.findViewById(R.id.tvMusicDuration)
        val btnPlayAction: ImageButton = view.findViewById(R.id.btnMusicPlayAction)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MusicViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_music_track, parent, false)
        return MusicViewHolder(view)
    }

    override fun onBindViewHolder(holder: MusicViewHolder, position: Int) {
        val track = tracks[position]
        holder.tvTitle.text = track.title
        holder.tvArtist.text = track.artist

        val totalSec = track.durationMs / 1000
        val m = totalSec / 60
        val s = totalSec % 60
        holder.tvDuration.text = String.format("%02d:%02d", m, s)

        holder.itemView.setOnClickListener { onClick(track, position) }
        holder.btnPlayAction.setOnClickListener { onClick(track, position) }
    }

    override fun getItemCount() = tracks.size
}
