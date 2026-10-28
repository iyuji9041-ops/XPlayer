package com.Shyam.xplayer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.graphics.Color
import android.widget.EditText
import android.widget.ImageView // FIX: Yahan ImageView import kiya hai
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
import com.Shyam.xplayer.util.ExtractionEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Video folder ka data class
data class VideoFolder(val id: String, val name: String)

class MainActivity : AppCompatActivity() {

    private lateinit var rvFolders: RecyclerView
    private lateinit var progressBar: ProgressBar
    
    private val foldersList = ArrayList<VideoFolder>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Modern edge-to-edge support for Xiaomi HyperOS & Android 14/15
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)

        // UI IDs Map kar rahe hain
        rvFolders = findViewById(R.id.rvFolders)
        progressBar = findViewById(R.id.progressBar)
        val mainRoot = findViewById<View>(R.id.mainRoot)
        val topHeader = findViewById<View>(R.id.top_header)

        // Dynamic status bar and navigation bar insets padding (No more status bar overlap on HyperOS!)
        ViewCompat.setOnApplyWindowInsetsListener(mainRoot) { _, insets ->
            val statusBar = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val navBar = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val density = resources.displayMetrics.density
            topHeader.setPadding(
                (24 * density).toInt(),
                statusBar.top + (12 * density).toInt(),
                (24 * density).toInt(),
                (12 * density).toInt()
            )
            rvFolders.setPadding(
                rvFolders.paddingLeft,
                rvFolders.paddingTop,
                rvFolders.paddingRight,
                navBar.bottom + (16 * density).toInt()
            )
            insets
        }
        
        val btnPlayLink = findViewById<ImageView>(R.id.btnPlayLink)
        btnPlayLink?.setOnClickListener {
            // Launch beautiful NovaGet-style Online Stream & Downloader Activity!
            startActivity(Intent(this, OnlineStreamActivity::class.java))
        }

        // FIX: Yahan ImageButton ki jagah ImageView lagaya hai taaki crash na ho
        val btnSettings = findViewById<ImageView>(R.id.btnSettings) 

        // Settings Button ka Click Logic
        btnSettings.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }

        rvFolders.layoutManager = LinearLayoutManager(this)

        // Permission check karke folders load karna
        if (checkPermissions()) {
            loadFolders()
        } else {
            requestPermissions()
        }
    }

    private fun showPlayLinkDialog() {
        val input = EditText(this).apply {
            hint = "Paste video, YouTube, or web link"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#80FFFFFF"))
            setPadding(40, 40, 40, 40)
            background = null
        }
        AlertDialog.Builder(this)
            .setTitle("Play Online Stream")
            .setView(input)
            .setPositiveButton("Play") { _, _ ->
                val url = input.text.toString().trim()
                if (url.isNotEmpty()) {
                    playOnlineStream(url)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun playOnlineStream(url: String) {
        progressBar.visibility = View.VISIBLE
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val item = ExtractionEngine.extract(this@MainActivity, url)
                val streamUrl = item.streamUrl.ifEmpty { url }
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    val intent = Intent(this@MainActivity, PlayerActivity::class.java).apply {
                        putExtra("video_uri", streamUrl)
                        putExtra("video_title", item.title.ifEmpty { "Online Stream" })
                    }
                    startActivity(intent)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    val intent = Intent(this@MainActivity, PlayerActivity::class.java).apply {
                        putExtra("video_uri", url)
                        putExtra("video_title", "Online Stream")
                    }
                    startActivity(intent)
                }
            }
        }
    }

    private fun checkPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_MEDIA_VIDEO), 100)
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 100)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            loadFolders()
        }
    }

    private fun loadFolders() {
        progressBar.visibility = View.VISIBLE
        foldersList.clear()

        val projection = arrayOf(MediaStore.Video.Media.BUCKET_ID, MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
        val uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        
        val cursor = contentResolver.query(uri, projection, null, null, MediaStore.Video.Media.DATE_ADDED + " DESC")
        
        val folderMap = LinkedHashMap<String, VideoFolder>()

        cursor?.use {
            val bucketIdColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_ID)
            val bucketNameColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)

            while (it.moveToNext()) {
                val bucketId = it.getString(bucketIdColumn)
                val bucketName = it.getString(bucketNameColumn) ?: "Unknown"

                if (!folderMap.containsKey(bucketId)) {
                    folderMap[bucketId] = VideoFolder(bucketId, bucketName)
                }
            }
        }

        foldersList.addAll(folderMap.values)
        
        // Adapter set karna aur click hone par folder open karna
        rvFolders.adapter = FolderAdapter(foldersList) { folder ->
            val intent = Intent(this, VideosListActivity::class.java)
            intent.putExtra("folder_id", folder.id)
            intent.putExtra("folder_name", folder.name)
            startActivity(intent)
        }
        progressBar.visibility = View.GONE
    }
}

// =====================================
// FOLDER ADAPTER (List design ke liye)
// =====================================
class FolderAdapter(private val folders: List<VideoFolder>, private val onClick: (VideoFolder) -> Unit) :
    RecyclerView.Adapter<FolderAdapter.FolderViewHolder>() {

    class FolderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvFolderName: TextView = view.findViewById(R.id.tvFolderName)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FolderViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_folder_view, parent, false)
        return FolderViewHolder(view)
    }

    override fun onBindViewHolder(holder: FolderViewHolder, position: Int) {
        val folder = folders[position]
        holder.tvFolderName.text = folder.name
        holder.itemView.setOnClickListener { onClick(folder) }
    }

    override fun getItemCount() = folders.size
}
