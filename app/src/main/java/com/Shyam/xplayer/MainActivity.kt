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
import android.widget.ImageView // FIX: Yahan ImageView import kiya hai
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

// Video folder ka data class
data class VideoFolder(val id: String, val name: String)

class MainActivity : AppCompatActivity() {

    private lateinit var rvFolders: RecyclerView
    private lateinit var progressBar: ProgressBar
    
    private val foldersList = ArrayList<VideoFolder>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // FIX: targetSdk 36 par Android khud edge-to-edge force karta hai, jisse "Video Folders"
        // title status bar ke peeche chala jaata hai. Isse off karke purana safe behavior wapas laate hain.
        WindowCompat.setDecorFitsSystemWindows(window, true)
        setContentView(R.layout.activity_main)

        // UI IDs Map kar rahe hain
        rvFolders = findViewById(R.id.rvFolders)
        progressBar = findViewById(R.id.progressBar)
        
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
