package com.Shyam.xplayer.util

import android.content.Context
import android.util.Log
import com.Shyam.xplayer.model.YouTubeAudioQuality
import com.Shyam.xplayer.model.YouTubeSubtitle
import com.Shyam.xplayer.model.YouTubeVideoItem
import com.Shyam.xplayer.model.YouTubeVideoQuality
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.mapper.VideoInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object ExtractionEngine {

  private const val TAG = "ExtractionEngine"
  private val extractionCache = ConcurrentHashMap<String, YouTubeVideoItem>()
  private val httpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(20, TimeUnit.SECONDS)
    .build()

  fun getCached(url: String): YouTubeVideoItem? {
    val clean = sanitizeUrl(url)
    return extractionCache[clean] ?: extractionCache[url]
  }

  /**
   * Cleans and sanitizes links from YouTube, TikTok, Instagram, Twitter/X, and other platforms.
   */
  fun sanitizeUrl(input: String): String {
    var s = input.trim()
    s = s.trim('\'', '"', '<', '>', '`', ' ', '\t', '\n')

    if (s.contains("http://") || s.contains("https://")) {
      val url = if (s.startsWith("http")) s else "https://" + s.substringAfter("://")
      if (url.contains("youtu.be/")) {
        val id = url.substringAfter("youtu.be/").substringBefore("?").substringBefore("&").substringBefore("/")
        return "https://www.youtube.com/watch?v=$id"
      }
      if (url.contains("youtube.com/shorts/")) {
        val id = url.substringAfter("youtube.com/shorts/").substringBefore("?").substringBefore("&").substringBefore("/")
        return "https://www.youtube.com/watch?v=$id"
      }
      return url
    }

    if (s.contains("youtu.be/")) {
      val id = s.substringAfter("youtu.be/").substringBefore("?").substringBefore("&")
      return "https://www.youtube.com/watch?v=$id"
    }
    if (s.contains("shorts/")) {
      val id = s.substringAfter("shorts/").substringBefore("?").substringBefore("&")
      return "https://www.youtube.com/watch?v=$id"
    }

    val idCandidate = s.substringBefore("?").substringBefore("&").trim('-', '_', '\'')
    if (idCandidate.length in 10..12 && idCandidate.matches(Regex("[a-zA-Z0-9_-]+"))) {
      return "https://www.youtube.com/watch?v=$idCandidate"
    }

    if (s.startsWith("www.") || s.contains(".com") || s.contains(".org") || s.contains(".net")) {
      return "https://$s"
    }

    return s
  }

  suspend fun extract(context: Context, rawUrl: String): YouTubeVideoItem = extractMediaInfo(context, rawUrl)

  /**
   * Extracts media metadata, available video resolutions, and audio streams
   * using the native yt-dlp extraction engine with online stream fallback.
   */
  suspend fun extractMediaInfo(context: Context, rawUrl: String): YouTubeVideoItem = withContext(Dispatchers.IO) {
    val cleanUrl = sanitizeUrl(rawUrl)
    val cached = extractionCache[cleanUrl] ?: extractionCache[rawUrl]
    if (cached != null) {
      return@withContext cached
    }

    val ytId = when {
      cleanUrl.contains("v=") -> cleanUrl.substringAfter("v=").substringBefore("&")
      cleanUrl.contains("youtu.be/") -> cleanUrl.substringAfter("youtu.be/").substringBefore("?").substringBefore("&")
      cleanUrl.contains("shorts/") -> cleanUrl.substringAfter("shorts/").substringBefore("?").substringBefore("&")
      else -> null
    }
    val defaultThumbnail = if (ytId != null) "https://img.youtube.com/vi/$ytId/hqdefault.jpg" else ""

    var lastException: Throwable? = null

    // 1. Attempt native yt-dlp extraction
    try {
      try {
        YoutubeDL.getInstance().init(context.applicationContext)
      } catch (_: Throwable) {}

      val request = YoutubeDLRequest(cleanUrl).apply {
        addOption("--no-mtime")
        addOption("--no-playlist")
        addOption("--socket-timeout", "15")
        addOption("--user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36")
        addOption("--no-check-certificates")
        addOption("--no-warnings")
        addOption("--geo-bypass")
      }
      val info: VideoInfo = YoutubeDL.getInstance().getInfo(request)

      val durationMs = (info.duration?.toLong() ?: 0L) * 1000L
      val durationSec = (durationMs / 1000L).coerceAtLeast(1L)
      val title = info.title?.takeIf { it.isNotBlank() }
        ?: if (ytId != null) "YouTube Video ($ytId)"
        else "Online Media Video"
      val uploader = info.uploader ?: info.uploaderId ?: "Online Creator"
      val thumbnail = info.thumbnail?.takeIf { it.isNotBlank() } ?: defaultThumbnail

      val videoQualities = mutableListOf<YouTubeVideoQuality>()
      val audioQualities = mutableListOf<YouTubeAudioQuality>()
      var progressiveStreamUrl: String? = null

      info.formats?.forEach { fmt ->
        val url = fmt.url ?: return@forEach
        val height = fmt.height
        val ext = fmt.ext?.lowercase() ?: "mp4"

        val isStoryboard = ext == "mhtml" || fmt.formatNote?.contains("storyboard", true) == true || url.contains("storyboard")
        val isVideo = !fmt.vcodec.isNullOrEmpty() && fmt.vcodec != "none" && !isStoryboard
        val isAudioOnly = (fmt.vcodec.isNullOrEmpty() || fmt.vcodec == "none") &&
                          ((!fmt.acodec.isNullOrEmpty() && fmt.acodec != "none") || ext == "m4a" || ext == "mp3" || ext == "opus") &&
                          !isStoryboard

        if (isVideo && height != null && height >= 144) {
          if (progressiveStreamUrl == null && !fmt.acodec.isNullOrEmpty() && fmt.acodec != "none") {
            progressiveStreamUrl = url
          }

          var fileSize = fmt.fileSize ?: fmt.fileSizeApproximate ?: 0L
          if (fileSize <= 0L) {
            val tbr = fmt.tbr?.toDouble() ?: 0.0
            if (tbr > 0.0 && durationSec > 0L) {
              fileSize = ((tbr * 1024.0 / 8.0) * durationSec).toLong()
            }
          }
          if (fileSize <= 0L && durationSec > 0L) {
            val estBitrateKbps = when {
              height >= 2160 -> 18000.0
              height >= 1440 -> 9000.0
              height >= 1080 -> 4000.0
              height >= 720  -> 2200.0
              height >= 480  -> 1100.0
              height >= 360  -> 650.0
              else           -> 350.0
            }
            fileSize = ((estBitrateKbps * 1024.0 / 8.0) * durationSec).toLong()
          }

          val label = "${height}p ${if (height >= 2160) "4K UHD" else if (height >= 1440) "2K QHD" else if (height >= 1080) "Full HD" else if (height >= 720) "HD" else "SD"}"
          videoQualities.add(
            YouTubeVideoQuality(
              qualityLabel = label,
              height = height,
              format = "MP4 ($ext)",
              approximateSizeBytes = fileSize,
              streamUrl = url,
              formatId = fmt.formatId ?: ""
            )
          )
        }

        if (isAudioOnly) {
          val rawAbr = fmt.abr?.toInt() ?: fmt.tbr?.toInt() ?: 128
          val formatName = if (ext.contains("m4a") || ext.contains("aac")) "M4A" else if (ext.contains("opus")) "OPUS" else "MP3"
          val audioLabel = "${rawAbr} kbps ${formatName}"

          var audioSize = fmt.fileSize ?: fmt.fileSizeApproximate ?: 0L
          if (audioSize <= 0L && rawAbr > 0 && durationSec > 0L) {
            audioSize = ((rawAbr * 1024L / 8L) * durationSec)
          }
          if (audioSize <= 0L && durationSec > 0L) {
            audioSize = (128L * 1024L / 8L) * durationSec
          }

          audioQualities.add(
            YouTubeAudioQuality(
              qualityLabel = audioLabel,
              bitrateKbps = rawAbr,
              format = formatName,
              approximateSizeBytes = audioSize,
              streamUrl = url,
              formatId = fmt.formatId ?: ""
            )
          )
        }
      }

      val distinctVideo = videoQualities
        .distinctBy { it.height }
        .sortedByDescending { it.height }
        .filter { it.height >= 144 }

      val distinctAudio = audioQualities
        .distinctBy { it.bitrateKbps }
        .sortedByDescending { it.bitrateKbps }

      val directStream = progressiveStreamUrl ?: distinctVideo.firstOrNull()?.streamUrl ?: info.url ?: cleanUrl

      // If video has audio inside progressive stream but no separate audio formats (e.g., Instagram Reels)
      val finalAudioQualities = if (distinctAudio.isNotEmpty()) {
        distinctAudio
      } else {
        val approxAudioSize = if (durationSec > 0L) ((160 * 1024L / 8L) * durationSec) else 0L
        listOf(
          YouTubeAudioQuality(
            qualityLabel = "Original Studio Audio (M4A)",
            bitrateKbps = 160,
            format = "M4A",
            approximateSizeBytes = approxAudioSize,
            streamUrl = directStream,
            formatId = "m4a"
          ),
          YouTubeAudioQuality(
            qualityLabel = "High Quality Audio (MP3)",
            bitrateKbps = 192,
            format = "MP3",
            approximateSizeBytes = if (durationSec > 0L) ((192 * 1024L / 8L) * durationSec) else 0L,
            streamUrl = directStream,
            formatId = "mp3"
          )
        )
      }

      if (distinctVideo.isNotEmpty()) {
        val resultItem = YouTubeVideoItem(
          id = "ytdlp_${System.currentTimeMillis()}",
          title = title,
          author = uploader,
          durationMs = durationMs,
          viewsCount = "Direct Stream",
          thumbnailUrl = thumbnail,
          streamUrl = directStream,
          videoQualities = distinctVideo,
          audioQualities = finalAudioQualities,
          subtitles = listOf(
            YouTubeSubtitle("English (CC)", "en", "embedded_en"),
            YouTubeSubtitle("Hindi (हिंदी Subtitles)", "hi", "embedded_hi")
          ),
          sourceUrl = cleanUrl
        )
        extractionCache[cleanUrl] = resultItem
        extractionCache[rawUrl] = resultItem
        return@withContext resultItem
      }
    } catch (e: Throwable) {
      Log.w(TAG, "yt-dlp primary extraction note: ${e.message}")
      lastException = e
    }

    // 2. Invidious API fallback for YouTube links
    if (ytId != null) {
      try {
        val invidiousUrls = listOf(
          "https://inv.tux.pizza/api/v1/videos/$ytId",
          "https://invidious.nerdvpn.de/api/v1/videos/$ytId"
        )
        for (apiUrl in invidiousUrls) {
          try {
            val req = Request.Builder().url(apiUrl).build()
            val resp = httpClient.newCall(req).execute()
            if (resp.isSuccessful && resp.body != null) {
              val bodyStr = resp.body!!.string()
              val json = JSONObject(bodyStr)
              val title = json.optString("title", "YouTube Video ($ytId)")
              val author = json.optString("author", "YouTube Creator")
              val lengthSec = json.optLong("lengthSeconds", 180L).coerceAtLeast(1L)
              val durationMs = lengthSec * 1000L

              val videoQualities = mutableListOf<YouTubeVideoQuality>()
              val formatStreams = json.optJSONArray("formatStreams")
              var bestStreamUrl = ""

              if (formatStreams != null) {
                for (i in 0 until formatStreams.length()) {
                  val item = formatStreams.getJSONObject(i)
                  val sUrl = item.optString("url")
                  val res = item.optString("resolution", "360p").replace("p", "").toIntOrNull() ?: 360
                  val itag = item.optString("itag", "")
                  if (sUrl.isNotBlank() && res >= 144) {
                    if (bestStreamUrl.isBlank()) bestStreamUrl = sUrl
                    val approxSize = ((res.toDouble() / 720.0) * 10.0 * (lengthSec / 60.0) * 1024 * 1024).toLong().coerceAtLeast(800_000L)
                    videoQualities.add(
                      YouTubeVideoQuality(
                        qualityLabel = "${res}p ${if (res >= 720) "HD" else "SD"}",
                        height = res,
                        format = "MP4",
                        approximateSizeBytes = approxSize,
                        streamUrl = sUrl,
                        formatId = itag
                      )
                    )
                  }
                }
              }

              val audioQualities = mutableListOf<YouTubeAudioQuality>()
              val adaptiveFormats = json.optJSONArray("adaptiveFormats")
              if (adaptiveFormats != null) {
                for (i in 0 until adaptiveFormats.length()) {
                  val item = adaptiveFormats.getJSONObject(i)
                  val type = item.optString("type")
                  val sUrl = item.optString("url")
                  val itag = item.optString("itag", "")
                  if (type.contains("audio") && sUrl.isNotBlank()) {
                    val bitrate = (item.optLong("bitrate", 128000L) / 1000L).toInt()
                    val audioSize = ((bitrate * 1024L / 8L) * lengthSec).coerceAtLeast(500_000L)
                    audioQualities.add(
                      YouTubeAudioQuality(
                        qualityLabel = "High-Speed Audio (${bitrate} kbps)",
                        bitrateKbps = bitrate,
                        format = "M4A",
                        approximateSizeBytes = audioSize,
                        streamUrl = sUrl,
                        formatId = itag
                      )
                    )
                  }
                }
              }

              val distinctVid = videoQualities.distinctBy { it.height }.sortedByDescending { it.height }
              val distinctAud = audioQualities.distinctBy { it.bitrateKbps }.sortedByDescending { it.bitrateKbps }

              if (distinctVid.isNotEmpty()) {
                val directStream = bestStreamUrl.ifBlank { distinctVid.first().streamUrl }
                val invItem = YouTubeVideoItem(
                  id = "inv_${System.currentTimeMillis()}",
                  title = title,
                  author = author,
                  durationMs = durationMs,
                  viewsCount = "Direct Stream",
                  thumbnailUrl = defaultThumbnail,
                  streamUrl = directStream,
                  videoQualities = distinctVid,
                  audioQualities = distinctAud,
                  subtitles = emptyList(),
                  sourceUrl = cleanUrl
                )
                extractionCache[cleanUrl] = invItem
                extractionCache[rawUrl] = invItem
                return@withContext invItem
              }
            }
          } catch (e: Exception) {
            Log.w(TAG, "Invidious instance note: ${e.message}")
          }
        }
      } catch (e: Throwable) {
        Log.w(TAG, "Invidious fallback note: ${e.message}")
      }
    }

    // 3. Fallback for direct media links (mp4, m3u8, webm, mp3, m4a, etc.)
    val isDirectFile = cleanUrl.endsWith(".mp4", true) || cleanUrl.endsWith(".m3u8", true) ||
                       cleanUrl.endsWith(".webm", true) || cleanUrl.endsWith(".mp3", true) ||
                       cleanUrl.endsWith(".m4a", true) || cleanUrl.contains(".mp4?") ||
                       cleanUrl.contains(".m3u8?")
    if (isDirectFile) {
      val fallbackTitle = cleanUrl.substringAfterLast('/').substringBefore('?').takeIf { it.isNotBlank() } ?: "Direct Media Stream"
      val isAudioFile = cleanUrl.endsWith(".mp3", true) || cleanUrl.endsWith(".m4a", true)
      val fallbackItem = YouTubeVideoItem(
        id = "stream_${System.currentTimeMillis()}",
        title = fallbackTitle,
        author = "Direct Link",
        durationMs = 0L,
        viewsCount = "Direct Stream",
        thumbnailUrl = defaultThumbnail,
        streamUrl = cleanUrl,
        videoQualities = if (!isAudioFile) listOf(
          YouTubeVideoQuality(
            qualityLabel = "Direct Video Stream",
            height = 720,
            format = "MP4",
            approximateSizeBytes = 0L,
            streamUrl = cleanUrl
          )
        ) else emptyList(),
        audioQualities = if (isAudioFile) listOf(
          YouTubeAudioQuality(
            qualityLabel = "Direct Audio Stream",
            bitrateKbps = 160,
            format = "Audio",
            approximateSizeBytes = 0L,
            streamUrl = cleanUrl
          )
        ) else emptyList(),
        subtitles = emptyList(),
        sourceUrl = cleanUrl
      )
      extractionCache[cleanUrl] = fallbackItem
      extractionCache[rawUrl] = fallbackItem
      return@withContext fallbackItem
    }

    // If all extraction methods failed, throw clear actionable exception
    val errMsg = lastException?.localizedMessage ?: lastException?.message ?: "Could not extract stream. Please check link or update yt-dlp via OTA Update."
    throw IllegalStateException(errMsg)
  }

  /**
   * Updates native yt-dlp binaries over the air (OTA).
   */
  suspend fun updateEngine(context: Context): String = withContext(Dispatchers.IO) {
    try {
      val status = YoutubeDL.getInstance().updateYoutubeDL(context.applicationContext, YoutubeDL.UpdateChannel.STABLE)
      when (status) {
        YoutubeDL.UpdateStatus.DONE -> "yt-dlp Core updated to latest release successfully!"
        YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE -> "yt-dlp Core is already up to date"
        else -> "yt-dlp Core update status: $status"
      }
    } catch (e: Throwable) {
      Log.w(TAG, "OTA update error: ${e.message}")
      "OTA update error: ${e.localizedMessage ?: e.message ?: "Failed to connect to update server"}"
    }
  }
}
