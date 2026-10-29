package com.Shyam.xplayer

import android.app.DownloadManager
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.Shyam.xplayer.model.YouTubeAudioQuality
import com.Shyam.xplayer.model.YouTubeVideoItem
import com.Shyam.xplayer.model.YouTubeVideoQuality
import com.Shyam.xplayer.util.ExtractionEngine
import com.Shyam.xplayer.util.NovaIcons
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OnlineStreamActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Edge-to-edge support for modern Android & Xiaomi HyperOS
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = Color(0xFF0A0B10),
                    surface = Color(0xFF14151F),
                    primary = Color(0xFFA855F7)
                )
            ) {
                OnlineStreamScreen(
                    onBackClick = { finish() },
                    onPlayStream = { url, title ->
                        val intent = Intent(this, PlayerActivity::class.java).apply {
                            putExtra("video_uri", url)
                            putExtra("video_title", title)
                        }
                        startActivity(intent)
                    }
                )
            }
        }
    }
}

// Download task representation in UI
data class RecentDownload(
    val title: String,
    val format: String,
    val status: String,
    val time: Long = System.currentTimeMillis()
)

@Composable
fun OnlineStreamScreen(
    onBackClick: () -> Unit,
    onPlayStream: (url: String, title: String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var urlInput by remember { mutableStateOf("") }
    var isAnalyzing by remember { mutableStateOf(false) }
    var analyzedItem by remember { mutableStateOf<YouTubeVideoItem?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // OTA Update Button state
    var isUpdatingEngine by remember { mutableStateOf(false) }
    var otaButtonLabel by remember { mutableStateOf("OTA Update") }

    // Filter category: "ALL", "VIDEO", "AUDIO"
    var selectedCategory by remember { mutableStateOf("ALL") }

    // Recent downloads list
    val recentDownloads = remember { mutableStateListOf<RecentDownload>() }

    fun pasteFromClipboard() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip
        if (clip != null && clip.itemCount > 0) {
            val text = clip.getItemAt(0).text?.toString()?.trim() ?: ""
            if (text.isNotEmpty()) {
                urlInput = text
                Toast.makeText(context, "Link pasted from clipboard", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
        }
    }

    fun analyzeUrl() {
        val targetUrl = urlInput.trim()
        if (targetUrl.isEmpty()) {
            Toast.makeText(context, "Please paste or enter a URL first", Toast.LENGTH_SHORT).show()
            return
        }

        isAnalyzing = true
        errorMessage = null
        analyzedItem = null

        coroutineScope.launch(Dispatchers.IO) {
            try {
                val item = ExtractionEngine.extract(context, targetUrl)
                withContext(Dispatchers.Main) {
                    isAnalyzing = false
                    analyzedItem = item
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isAnalyzing = false
                    errorMessage = "Analysis error: ${e.localizedMessage ?: "Could not extract stream. Please check link."}"
                }
            }
        }
    }

    fun triggerOtaUpdate() {
        if (isUpdatingEngine) return
        isUpdatingEngine = true
        otaButtonLabel = "Updating..."
        coroutineScope.launch(Dispatchers.IO) {
            val result = ExtractionEngine.updateEngine(context)
            withContext(Dispatchers.Main) {
                isUpdatingEngine = false
                otaButtonLabel = "Up to date"
                Toast.makeText(context, result, Toast.LENGTH_LONG).show()
            }
        }
    }

    fun startDownload(streamUrl: String, title: String, ext: String, label: String) {
        try {
            val cleanTitle = title.replace(Regex("[^a-zA-Z0-9._ -]"), "_").take(55)
            val fileName = "$cleanTitle.$ext"
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val request = DownloadManager.Request(Uri.parse(streamUrl))
                .setTitle(title)
                .setDescription("Downloading $label stream")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "XPlayer/$fileName")
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)
            downloadManager.enqueue(request)

            recentDownloads.add(0, RecentDownload(title = title, format = label, status = "Downloading to Downloads/XPlayer/"))
            Toast.makeText(context, "Download started: $fileName\nCheck notification bar", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Download initiation failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF14151F),
                        Color(0xFF0C0D12),
                        Color(0xFF07080B)
                    )
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header Bar with Back Button, Zaswix Pill, and OTA Update Pill
            item {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Back Button & Zaswix Brand Pill
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Glass Back Button
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0x331E293B))
                                .border(1.dp, Color(0x33FFFFFF), CircleShape)
                                .clickable { onBackClick() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = NovaIcons.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Zaswix / XPlayer Frosted Pill
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(18.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(Color(0x28FFFFFF), Color(0x12FFFFFF))
                                    )
                                )
                                .border(
                                    1.dp,
                                    Brush.linearGradient(listOf(Color(0x60FFFFFF), Color(0x20FFFFFF))),
                                    RoundedCornerShape(18.dp)
                                )
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = NovaIcons.ElectricBolt,
                                    contentDescription = null,
                                    tint = Color(0xFFA855F7),
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Zaswix Stream",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.6.sp
                                )
                            }
                        }
                    }

                    // Frosted OTA Update Pill Button
                    val isUpdated = otaButtonLabel.contains("Up to date")
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .background(
                                Brush.horizontalGradient(
                                    if (isUpdated) {
                                        listOf(Color(0x3300E676), Color(0x1800E676))
                                    } else {
                                        listOf(Color(0x28FFFFFF), Color(0x12FFFFFF))
                                    }
                                )
                            )
                            .border(
                                1.dp,
                                Brush.linearGradient(
                                    if (isUpdated) {
                                        listOf(Color(0x8000E676), Color(0x3000E676))
                                    } else {
                                        listOf(Color(0x60FFFFFF), Color(0x20FFFFFF))
                                    }
                                ),
                                RoundedCornerShape(18.dp)
                            )
                            .clickable(enabled = !isUpdatingEngine) { triggerOtaUpdate() }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isUpdatingEngine) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(13.dp),
                                    color = Color(0xFFA855F7),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = if (isUpdated) NovaIcons.Check else NovaIcons.Refresh,
                                    contentDescription = "OTA Update",
                                    tint = if (isUpdated) Color(0xFF00E676) else Color(0xFFA855F7),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = otaButtonLabel,
                                color = if (isUpdated) Color(0xFF00E676) else Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // Hero Title & Description
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Ultra-Fast Video Stream & Downloader",
                        fontSize = 20.sp,
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "Play direct 60fps LibVLC streams or download 4K, 1080p, 720p & Studio Audio",
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            // Stadium Capsule Search & Paste Bar (Exact NovaGet style)
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp)
                        .clip(RoundedCornerShape(32.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFF22232A), Color(0xFF191A20))
                            )
                        )
                        .border(
                            1.2.dp,
                            Brush.linearGradient(
                                listOf(Color(0x55FFFFFF), Color(0x1EFFFFFF))
                            ),
                            RoundedCornerShape(32.dp)
                        )
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Paste Icon Button on left
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .clickable { pasteFromClipboard() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = NovaIcons.ContentPaste,
                                contentDescription = "Paste",
                                tint = Color(0xFFA855F7),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Text Input in middle
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 6.dp)
                        ) {
                            if (urlInput.isEmpty()) {
                                Text(
                                    text = "Paste video or audio link (YouTube, Insta, X...)",
                                    color = Color(0xFF64748B),
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            BasicTextField(
                                value = urlInput,
                                onValueChange = { urlInput = it },
                                singleLine = true,
                                textStyle = TextStyle(
                                    color = Color.White,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Normal
                                ),
                                cursorBrush = SolidColor(Color(0xFFA855F7)),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Uri,
                                    imeAction = ImeAction.Search
                                ),
                                keyboardActions = KeyboardActions(
                                    onSearch = { analyzeUrl() }
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // Clear Button
                        if (urlInput.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .clickable { urlInput = "" },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = NovaIcons.Close,
                                    contentDescription = "Clear",
                                    tint = Color(0xFF94A3B8),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        // Glowing Analyze / Search Button
                        Box(
                            modifier = Modifier
                                .height(40.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(Color(0xFF9333EA), Color(0xFF7C3AED))
                                    )
                                )
                                .clickable(enabled = !isAnalyzing) { analyzeUrl() }
                                .padding(horizontal = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isAnalyzing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = NovaIcons.Search,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Text(
                                        text = "Analyze",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Platform Quick Pills
            item {
                Column {
                    Text(
                        text = "SUPPORTED PLATFORMS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF64748B),
                        letterSpacing = 0.8.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    val platforms = listOf(
                        Pair("YouTube", Color(0xFFEF4444)),
                        Pair("Instagram", Color(0xFFEC4899)),
                        Pair("Facebook", Color(0xFF3B82F6)),
                        Pair("TikTok", Color(0xFFA855F7)),
                        Pair("Twitter / X", Color(0xFFF59E0B)),
                        Pair("Direct Link", Color(0xFF10B981))
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(platforms) { (name, color) ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0x18FFFFFF))
                                    .border(1.dp, Color(0x30FFFFFF), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = name,
                                    color = color,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            // Analyzing Indicator State
            item {
                AnimatedVisibility(visible = isAnalyzing) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0x1EFFFFFF))
                            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(20.dp))
                            .padding(16.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = NovaIcons.Speed,
                                        contentDescription = null,
                                        tint = Color(0xFFA855F7),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "Extracting Video Metadata...",
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Text(
                                    text = "yt-dlp Core",
                                    color = Color(0xFFA855F7),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp)),
                                color = Color(0xFFA855F7),
                                trackColor = Color(0x33FFFFFF)
                            )
                            Text(
                                text = "Resolving adaptive stream formats (4K / 1080p / 720p / Studio Audio)",
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            // Error Message Card
            item {
                AnimatedVisibility(visible = errorMessage != null && !isAnalyzing) {
                    errorMessage?.let { err ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0x28F43F5E))
                                .border(1.dp, Color(0x66F43F5E), RoundedCornerShape(16.dp))
                                .padding(14.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = NovaIcons.Close,
                                    contentDescription = null,
                                    tint = Color(0xFFF43F5E),
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = err,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            // Analyzed Item Card with Qualities & Audio
            item {
                AnimatedVisibility(visible = analyzedItem != null && !isAnalyzing) {
                    analyzedItem?.let { item ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(22.dp))
                                .background(Color(0x1AFFFFFF))
                                .border(1.dp, Color(0x38FFFFFF), RoundedCornerShape(22.dp))
                                .padding(16.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                // Title and duration
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = item.author.ifEmpty { "STREAM LINK" }.uppercase(),
                                            color = Color(0xFFA855F7),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.6.sp
                                        )
                                        if (item.durationMs > 0L) {
                                            val m = item.durationMs / 1000 / 60
                                            val s = (item.durationMs / 1000) % 60
                                            Text(
                                                text = String.format("%02d:%02d", m, s),
                                                color = Color(0xFFCBD5E1),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = item.title,
                                        color = Color.White,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                // Stream category tabs: All, Video Only, Audio Only
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    val tabs = listOf(
                                        "ALL" to "All Streams",
                                        "VIDEO" to "Video Only",
                                        "AUDIO" to "Audio Only"
                                    )
                                    tabs.forEach { (catId, catLabel) ->
                                        val isSelected = selectedCategory == catId
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(
                                                    if (isSelected) Color(0x40A855F7) else Color(0x12FFFFFF)
                                                )
                                                .border(
                                                    1.dp,
                                                    if (isSelected) Color(0xFFA855F7) else Color(0x20FFFFFF),
                                                    RoundedCornerShape(12.dp)
                                                )
                                                .clickable { selectedCategory = catId }
                                                .padding(vertical = 8.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = catLabel,
                                                fontSize = 12.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isSelected) Color.White else Color(0xFF94A3B8)
                                            )
                                        }
                                    }
                                }

                                // Stream Qualities List
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    // 1. Video Options
                                    if (selectedCategory == "ALL" || selectedCategory == "VIDEO") {
                                        item.videoQualities.forEach { vq ->
                                            StreamOptionRow(
                                                label = vq.qualityLabel,
                                                format = vq.format,
                                                sizeBytes = vq.approximateSizeBytes,
                                                isAudio = false,
                                                onPlay = { onPlayStream(vq.streamUrl, item.title) },
                                                onDownload = {
                                                    startDownload(vq.streamUrl, item.title, "mp4", vq.qualityLabel)
                                                }
                                            )
                                        }
                                    }

                                    // 2. Audio Options
                                    if (selectedCategory == "ALL" || selectedCategory == "AUDIO") {
                                        item.audioQualities.forEach { aq ->
                                            val ext = if (aq.format.contains("m4a", true)) "m4a" else "mp3"
                                            StreamOptionRow(
                                                label = aq.qualityLabel,
                                                format = aq.format,
                                                sizeBytes = aq.approximateSizeBytes,
                                                isAudio = true,
                                                onPlay = { onPlayStream(aq.streamUrl, "${item.title} (Audio)") },
                                                onDownload = {
                                                    startDownload(aq.streamUrl, item.title, ext, aq.qualityLabel)
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Recent Downloads Card
            if (recentDownloads.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "RECENT DOWNLOADS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF64748B),
                            letterSpacing = 0.8.sp
                        )
                        recentDownloads.forEach { rd ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0x18FFFFFF))
                                    .border(1.dp, Color(0x28FFFFFF), RoundedCornerShape(14.dp))
                                    .padding(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = rd.title,
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${rd.format} • ${rd.status}",
                                            color = Color(0xFF38BDF8),
                                            fontSize = 11.sp
                                        )
                                    }
                                    Icon(
                                        imageVector = NovaIcons.DownloadDone,
                                        contentDescription = null,
                                        tint = Color(0xFF00E676),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun StreamOptionRow(
    label: String,
    format: String,
    sizeBytes: Long,
    isAudio: Boolean,
    onPlay: () -> Unit,
    onDownload: () -> Unit
) {
    val sizeMb = if (sizeBytes > 0) String.format("%.1f MB", sizeBytes / (1024.0 * 1024.0)) else "Direct"

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0x12FFFFFF))
            .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Quality details on left
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = label,
                        color = Color.White,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isAudio) Color(0x2806B6D4) else Color(0x28A855F7))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = format,
                            color = if (isAudio) Color(0xFF22D3EE) else Color(0xFFC084FC),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Approx. $sizeMb",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.5.sp
                )
            }

            // Two Action Buttons on right: [ ▶ Play ] and [ ⬇ Download ]
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Play Button
                Box(
                    modifier = Modifier
                        .height(34.dp)
                        .clip(RoundedCornerShape(17.dp))
                        .background(Color(0x256366F1))
                        .border(1.dp, Color(0x666366F1), RoundedCornerShape(17.dp))
                        .clickable { onPlay() }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = NovaIcons.PlayArrow,
                            contentDescription = "Play",
                            tint = Color(0xFF818CF8),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Play",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Download Button
                Box(
                    modifier = Modifier
                        .height(34.dp)
                        .clip(RoundedCornerShape(17.dp))
                        .background(Color(0x2510B981))
                        .border(1.dp, Color(0x6610B981), RoundedCornerShape(17.dp))
                        .clickable { onDownload() }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = NovaIcons.Download,
                            contentDescription = "Download",
                            tint = Color(0xFF34D399),
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "Download",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}
