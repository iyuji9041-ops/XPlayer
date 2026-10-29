package com.Shyam.xplayer.vault

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
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
import com.Shyam.xplayer.PlayerActivity
import com.Shyam.xplayer.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class VaultVideoItem(val file: File, val originalName: String, val sizeBytes: Long)

class VaultGalleryActivity : AppCompatActivity() {

    private val vaultItems = ArrayList<VaultVideoItem>()
    private lateinit var rvVaultVideos: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var tvCount: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var adapter: VaultAdapter

    companion object {
        private const val REQUEST_PICK_VIDEO = 2001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_vault_gallery)

        val root = findViewById<View>(R.id.vaultGalleryRoot)
        val header = findViewById<View>(R.id.vaultGalleryHeader)
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val statusBar = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val navBar = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val density = resources.displayMetrics.density
            header.setPadding((16 * density).toInt(), statusBar.top + (8 * density).toInt(), (16 * density).toInt(), (12 * density).toInt())
            root.setPadding(0, 0, 0, navBar.bottom)
            insets
        }

        findViewById<ImageButton>(R.id.btnBackGallery).setOnClickListener { finish() }

        rvVaultVideos = findViewById(R.id.rvVaultVideos)
        tvEmpty = findViewById(R.id.tvVaultEmpty)
        tvCount = findViewById(R.id.tvVaultCount)
        progressBar = findViewById(R.id.vaultProgressBar)

        adapter = VaultAdapter(vaultItems,
            onPlay = { item -> playVaultVideo(item) },
            onRestore = { item -> confirmRestoreVideo(item) },
            onDelete = { item -> confirmDeleteVideo(item) }
        )

        rvVaultVideos.layoutManager = LinearLayoutManager(this)
        rvVaultVideos.adapter = adapter

        findViewById<ImageButton>(R.id.btnAddVaultVideo).setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "video/*"
                addCategory(Intent.CATEGORY_OPENABLE)
            }
            startActivityForResult(Intent.createChooser(intent, "Select Video to Secure"), REQUEST_PICK_VIDEO)
        }

        loadVaultVideos()
    }

    private fun loadVaultVideos() {
        vaultItems.clear()
        val dir = VaultSecurityManager.getVaultDirectory(this)
        val files = dir.listFiles { f -> f.isFile && !f.name.startsWith(".") } ?: emptyArray()

        for (f in files) {
            val decodedName = f.name.replace(".enc", "")
            vaultItems.add(VaultVideoItem(f, decodedName, f.length()))
        }

        adapter.notifyDataSetChanged()
        tvCount.text = "${vaultItems.size} files encrypted"
        tvEmpty.visibility = if (vaultItems.isEmpty()) View.VISIBLE else View.GONE
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_PICK_VIDEO && resultCode == Activity.RESULT_OK && data?.data != null) {
            encryptAndImportVideo(data.data!!)
        }
    }

    private fun encryptAndImportVideo(uri: Uri) {
        progressBar.visibility = View.VISIBLE
        CoroutineScope(Dispatchers.IO).launch {
            var originalName = "video_${System.currentTimeMillis()}.mp4"
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIndex != -1) {
                    originalName = cursor.getString(nameIndex) ?: originalName
                }
            }

            val vaultDir = VaultSecurityManager.getVaultDirectory(this@VaultGalleryActivity)
            val destEncryptedFile = File(vaultDir, "${originalName}.enc")

            try {
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    VaultSecurityManager.encryptStreamToFile(inputStream, destEncryptedFile)
                }

                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(this@VaultGalleryActivity, "Video securely encrypted to Vault!", Toast.LENGTH_SHORT).show()
                    loadVaultVideos()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(this@VaultGalleryActivity, "Failed to encrypt: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun playVaultVideo(item: VaultVideoItem) {
        progressBar.visibility = View.VISIBLE
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val tempCache = File(cacheDir, "vault_stream_${System.currentTimeMillis()}_${item.originalName}")
                VaultSecurityManager.decryptFile(item.file, tempCache)
                tempCache.deleteOnExit()

                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    val intent = Intent(this@VaultGalleryActivity, PlayerActivity::class.java).apply {
                        putExtra("video_uri", tempCache.absolutePath)
                        putExtra("video_title", "[Private] ${item.originalName}")
                    }
                    startActivity(intent)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(this@VaultGalleryActivity, "Decryption error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun confirmRestoreVideo(item: VaultVideoItem) {
        AlertDialog.Builder(this)
            .setTitle("Restore to Gallery?")
            .setMessage("This will decrypt the video and make it visible in your regular gallery.")
            .setPositiveButton("Restore") { _, _ ->
                restoreVideo(item)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun restoreVideo(item: VaultVideoItem) {
        progressBar.visibility = View.VISIBLE
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
                val targetDir = File(moviesDir, "XPlayer_Restored")
                if (!targetDir.exists()) targetDir.mkdirs()

                val restoredFile = File(targetDir, item.originalName)
                VaultSecurityManager.decryptFile(item.file, restoredFile)
                item.file.delete()

                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(this@VaultGalleryActivity, "Restored to Movies/XPlayer_Restored", Toast.LENGTH_LONG).show()
                    loadVaultVideos()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(this@VaultGalleryActivity, "Restore failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun confirmDeleteVideo(item: VaultVideoItem) {
        AlertDialog.Builder(this)
            .setTitle("Delete Video Permanently?")
            .setMessage("This encrypted video will be permanently deleted.")
            .setPositiveButton("Delete") { _, _ ->
                item.file.delete()
                Toast.makeText(this, "Deleted from vault", Toast.LENGTH_SHORT).show()
                loadVaultVideos()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}

class VaultAdapter(
    private val items: List<VaultVideoItem>,
    private val onPlay: (VaultVideoItem) -> Unit,
    private val onRestore: (VaultVideoItem) -> Unit,
    private val onDelete: (VaultVideoItem) -> Unit
) : RecyclerView.Adapter<VaultAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvVaultVideoName)
        val tvSize: TextView = view.findViewById(R.id.tvVaultVideoSize)
        val btnRestore: ImageButton = view.findViewById(R.id.btnRestoreVideo)
        val btnDelete: ImageButton = view.findViewById(R.id.btnDeleteVaultVideo)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_vault_video, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.tvName.text = item.originalName
        val mb = item.sizeBytes / (1024 * 1024f)
        holder.tvSize.text = String.format("%.2f MB - AES Encrypted", mb)

        holder.itemView.setOnClickListener { onPlay(item) }
        holder.btnRestore.setOnClickListener { onRestore(item) }
        holder.btnDelete.setOnClickListener { onDelete(item) }
    }

    override fun getItemCount() = items.size
}
