package com.Shyam.xplayer

import android.content.ContentUris
import android.content.Intent
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import android.graphics.Bitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import wseemann.media.FFmpegMediaMetadataRetriever
import kotlinx.coroutines.*

// Data class video detail ke liye
data class VideoFile(val id: Long, val name: String, val path: String)

class VideosListActivity : AppCompatActivity() {

    private val videosList = ArrayList<VideoFile>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_videos_list)

        val folderId = intent.getStringExtra("folder_id")
        val folderName = intent.getStringExtra("folder_name") ?: "Videos"

        val tvFolderTitle = findViewById<TextView>(R.id.tvFolderTitle)
        val tvVideoCount = findViewById<TextView>(R.id.tvVideoCount)
        val btnBackVideos = findViewById<ImageView>(R.id.btnBackVideos)
        val topHeader = findViewById<View>(R.id.top_header_videos)
        val rvVideos = findViewById<RecyclerView>(R.id.rvVideos)
        val rootLayout = findViewById<View>(R.id.videosListRoot)

        tvFolderTitle.text = folderName
        btnBackVideos.setOnClickListener { finish() }

        // Dynamic WindowInsets padding for Xiaomi HyperOS & Android 14/15 edge-to-edge
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { _, insets ->
            val statusBar = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val navBar = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val density = resources.displayMetrics.density
            topHeader.setPadding(
                (16 * density).toInt(),
                statusBar.top + (8 * density).toInt(),
                (16 * density).toInt(),
                (8 * density).toInt()
            )
            rvVideos.setPadding(
                (12 * density).toInt(),
                (4 * density).toInt(),
                (12 * density).toInt(),
                navBar.bottom + (16 * density).toInt()
            )
            insets
        }

        rvVideos.layoutManager = LinearLayoutManager(this)

        if (folderId != null) {
            loadVideos(folderId)
        }

        tvVideoCount.text = "${videosList.size} videos"

        rvVideos.adapter = VideoAdapter(videosList) { video ->
            val intent = Intent(this, PlayerActivity::class.java)
            intent.putExtra("video_uri", video.path)
            intent.putExtra("video_title", video.name) 
            startActivity(intent)
        }
    }

    private fun loadVideos(folderId: String) {
        val projection = arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME, MediaStore.Video.Media.DATA)
        val selection = "${MediaStore.Video.Media.BUCKET_ID} = ?"
        val selectionArgs = arrayOf(folderId)
        val uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI

        val cursor = contentResolver.query(uri, projection, selection, selectionArgs, MediaStore.Video.Media.DATE_ADDED + " DESC")

        cursor?.use {
            val idColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val dataColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)

            while (it.moveToNext()) {
                val id = it.getLong(idColumn)
                val name = it.getString(nameColumn)
                val path = it.getString(dataColumn)
                videosList.add(VideoFile(id, name, path))
            }
        }
    }
}

class VideoAdapter(private val videos: List<VideoFile>, private val onClick: (VideoFile) -> Unit) :
    RecyclerView.Adapter<VideoAdapter.VideoViewHolder>() {

    class VideoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvVideoName: TextView = view.findViewById(R.id.tvVideoName)
        val tvVideoFormat: TextView = view.findViewById(R.id.tvVideoFormat)
        val imgThumbnail: ImageView = view.findViewById(R.id.imgThumbnail)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_video_view, parent, false)
        return VideoViewHolder(view)
    }

    override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
        val video = videos[position]
        holder.tvVideoName.text = video.name

        val fileExt = video.name.substringAfterLast('.', "").uppercase()
        
        holder.imgThumbnail.setImageResource(android.R.color.darker_gray)

        // 🟢 NAYA LOGIC: MP4 ko bhi issi list mein daal diya taaki uski duration bhi nikal sake
        if (fileExt == "MKV" || fileExt == "HEVC" || fileExt == "MP4") {
            CoroutineScope(Dispatchers.IO).launch {
                var bitmap: android.graphics.Bitmap? = null
                var timeFormatted = "00:00"
                
                try {
                    val mmr = FFmpegMediaMetadataRetriever()
                    mmr.setDataSource(video.path)
                    
                    // 🟢 DURATION NIKALNA
                    val durationStr = mmr.extractMetadata(FFmpegMediaMetadataRetriever.METADATA_KEY_DURATION)
                    val durationMs = durationStr?.toLongOrNull() ?: 0L
                    
                    val totalSeconds = durationMs / 1000
                    val hours = totalSeconds / 3600
                    val minutes = (totalSeconds % 3600) / 60
                    val seconds = totalSeconds % 60
                    
                    timeFormatted = if (hours > 0) {
                        String.format("%02d:%02d:%02d", hours, minutes, seconds)
                    } else {
                        String.format("%02d:%02d", minutes, seconds)
                    }

                    bitmap = mmr.getFrameAtTime(2000000, FFmpegMediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    mmr.release()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                
                withContext(Dispatchers.Main) {
                    // 🟢 YAHAN TIME LAGA DIYA
                    holder.tvVideoFormat.text = "$fileExt • $timeFormatted"
                    
                    // FIX: Activity context ki jagah applicationContext — isse "destroyed activity"
                    // crash nahi aayega chahe user list se turant bahar chala jaye
                    if (bitmap != null) {
                        com.bumptech.glide.Glide.with(holder.itemView.context.applicationContext)
                            .load(bitmap)
                            .override(320, 180)
                            .into(holder.imgThumbnail)
                    } else {
                        holder.imgThumbnail.setImageResource(android.R.drawable.ic_media_play)
                    }
                }
            }
        } else {
            holder.tvVideoFormat.text = "$fileExt • Media"
            com.bumptech.glide.Glide.with(holder.itemView.context.applicationContext)
                .asBitmap()
                .load(video.path)
                .override(320, 180)
                .error(android.R.drawable.ic_media_play)
                .into(holder.imgThumbnail)
        }
        
        holder.itemView.setOnClickListener { onClick(video) }
    }

    override fun getItemCount() = videos.size
}