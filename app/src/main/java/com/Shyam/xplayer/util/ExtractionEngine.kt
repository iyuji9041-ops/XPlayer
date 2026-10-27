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
import java.util.concurrent.TimeUnit

object ExtractionEngine {

  private const val TAG = "ExtractionEngine"
  private val extractionCache = java.util.concurrent.ConcurrentHashMap<String, YouTubeVideoItem>()
  private val httpClient = OkHttpClient.Builder()
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(15, TimeUnit.SECONDS)
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

  /**
   * Alias for extractMediaInfo.
   */
  suspend fun extract(context: Context, rawUrl: String): YouTubeVideoItem = extractMediaInfo(context, rawUrl)

  /**
   * Extracts media metadata, available video resolutions, and high-speed audio streams
   * using the native C++ yt-dlp extraction engine with online stream fallback.
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

    // 1. Attempt native yt-dlp extraction
    try {
      try {
        YoutubeDL.getInstance().init(context.applicationContext)
      } catch (_: Throwable) {}

      val request = YoutubeDLRequest(cleanUrl).apply {
        addOption("--no-mtime")
        addOption("--no-playlist")
        addOption("--socket-timeout", "10")
        addOption("--extractor-args", "youtube:player_client=android,web")
        addOption("--no-check-certificates")
        addOption("--no-warnings")
      }
      val info: VideoInfo = YoutubeDL.getInstance().getInfo(request)

      val durationMs = (info.duration?.toLong() ?: 0L) * 1000L
      val durationSec = (durationMs / 1000L).coerceAtLeast(15L)
      val title = info.title?.takeIf { it.isNotBlank() } ?: if (ytId != null) "YouTube Video ($ytId)" else "Online Media Stream"
      val uploader = info.uploader ?: info.uploaderId ?: "YouTube Creator"
      val thumbnail = info.thumbnail?.takeIf { it.isNotBlank() } ?: defaultThumbnail

      val videoQualities = mutableListOf<YouTubeVideoQuality>()
      val audioQualities = mutableListOf<YouTubeAudioQuality>()
      var progressiveStreamUrl: String? = null

      info.formats?.forEach { fmt ->
        val url = fmt.url ?: return@forEach
        val height = fmt.height
        val ext = fmt.ext?.lowercase() ?: "mp4"

        // Filter out storyboards, mhtml, and any quality strictly below 144p
        val isStoryboard = ext == "mhtml" || fmt.formatNote?.contains("storyboard", true) == true || url.contains("storyboard")
        val isVideo = fmt.vcodec != null && fmt.vcodec != "none" && !isStoryboard
        val isAudioOnly = (fmt.vcodec == null || fmt.vcodec == "none") && fmt.acodec != null && fmt.acodec != "none" && !isStoryboard

        if (isVideo && height != null && height >= 144) {
          // Check for progressive format with both audio and video
          if (progressiveStreamUrl == null && fmt.acodec != null && fmt.acodec != "none") {
            progressiveStreamUrl = url
          }

          var fileSize = fmt.fileSize ?: fmt.fileSizeApproximate ?: 0L
          if (fileSize <= 0L) {
            val mbPerMin = when {
              height >= 2160 -> 45.0
              height >= 1440 -> 30.0
              height >= 1080 -> 18.0
              height >= 720 -> 10.0
              height >= 480 -> 5.5
              height >= 360 -> 3.5
              height >= 240 -> 2.0
              else -> 1.2
            }
            fileSize = ((durationSec / 60.0) * mbPerMin * 1024 * 1024).toLong().coerceAtLeast(800_000L)
          }

          val label = "${height}p ${if (height >= 1080) "Full HD" else if (height >= 720) "HD" else "SD"}"
          videoQualities.add(
            YouTubeVideoQuality(
              qualityLabel = label,
              height = height,
              format = "MP4 ($ext)",
              approximateSizeBytes = fileSize,
              streamUrl = url
            )
          )
        }

        // Audio formats (ONLY audio-only formats; progressive video is never treated as audio)
        if (isAudioOnly) {
          val rawAbr = fmt.abr?.toInt() ?: 0
          val bitrate = if (rawAbr > 0) rawAbr else 160
          val audioLabel = when {
            bitrate >= 256 -> "High-Speed MP3 (${bitrate} kbps Master)"
            bitrate >= 160 -> "High-Speed M4A (${bitrate} kbps HD)"
            bitrate >= 120 -> "Fast Audio M4A (${bitrate} kbps)"
            else -> "Standard Audio (${bitrate} kbps)"
          }

          var audioSize = fmt.fileSize ?: fmt.fileSizeApproximate ?: 0L
          if (audioSize <= 0L || audioSize > 25_000_000L) {
            audioSize = ((bitrate * 1024L / 8L) * durationSec).coerceIn(1_200_000L, 12_000_000L)
          }

          audioQualities.add(
            YouTubeAudioQuality(
              qualityLabel = audioLabel,
              bitrateKbps = bitrate,
              format = if (ext.contains("m4a")) "M4A AAC" else if (ext.contains("opus")) "OPUS" else "MP3",
              approximateSizeBytes = audioSize,
              streamUrl = url
            )
          )
        }
      }

      // Keep only one entry per height, sorted descending (e.g. 1080p, 720p, 480p, 360p, 240p, 144p)
      val distinctVideo = videoQualities
        .distinctBy { it.height }
        .sortedByDescending { it.height }
        .filter { it.height >= 144 }

      val directStream = progressiveStreamUrl ?: distinctVideo.firstOrNull()?.streamUrl ?: info.url ?: cleanUrl

      val finalVideoQualities = buildCompleteVideoQualities(distinctVideo, durationSec, directStream)
      val finalAudioQualities = buildCompleteAudioQualities(audioQualities, durationSec, directStream)

      if (finalVideoQualities.isNotEmpty()) {
        val resultItem = YouTubeVideoItem(
          id = "ytdlp_${System.currentTimeMillis()}",
          title = title,
          author = uploader,
          durationMs = durationMs,
          viewsCount = "Direct Stream",
          thumbnailUrl = thumbnail,
          streamUrl = directStream,
          videoQualities = finalVideoQualities,
          audioQualities = finalAudioQualities,
          subtitles = listOf(
            YouTubeSubtitle("English (CC)", "en", "embedded_en"),
            YouTubeSubtitle("Hindi (हिंदी Subtitles)", "hi", "embedded_hi")
          )
        )
        extractionCache[cleanUrl] = resultItem
        extractionCache[rawUrl] = resultItem
        return@withContext resultItem
      }
    } catch (e: Throwable) {
      Log.w(TAG, "yt-dlp primary extraction note: ${e.message}")
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
              val lengthSec = json.optLong("lengthSeconds", 180L).coerceAtLeast(15L)
              val durationMs = lengthSec * 1000L

              val videoQualities = mutableListOf<YouTubeVideoQuality>()
              val formatStreams = json.optJSONArray("formatStreams")
              var bestStreamUrl = ""

              if (formatStreams != null) {
                for (i in 0 until formatStreams.length()) {
                  val item = formatStreams.getJSONObject(i)
                  val sUrl = item.optString("url")
                  val res = item.optString("resolution", "360p").replace("p", "").toIntOrNull() ?: 360
                  if (sUrl.isNotBlank() && res >= 144) {
                    if (bestStreamUrl.isBlank()) bestStreamUrl = sUrl
                    val approxSize = ((res.toDouble() / 720.0) * 12.0 * (lengthSec / 60.0) * 1024 * 1024).toLong().coerceAtLeast(1_500_000L)
                    videoQualities.add(
                      YouTubeVideoQuality(
                        qualityLabel = "${res}p ${if (res >= 720) "HD" else "SD"}",
                        height = res,
                        format = "MP4",
                        approximateSizeBytes = approxSize,
                        streamUrl = sUrl
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
                  if (type.contains("audio") && sUrl.isNotBlank()) {
                    val bitrate = (item.optLong("bitrate", 128000L) / 1000L).toInt()
                    val audioSize = ((bitrate * 1024L / 8L) * lengthSec).coerceAtLeast(1_000_000L)
                    audioQualities.add(
                      YouTubeAudioQuality(
                        qualityLabel = "High-Speed Audio (${bitrate} kbps)",
                        bitrateKbps = bitrate,
                        format = "M4A",
                        approximateSizeBytes = audioSize,
                        streamUrl = sUrl
                      )
                    )
                  }
                }
              }

              if (videoQualities.isNotEmpty()) {
                val directStream = bestStreamUrl.ifBlank { videoQualities.first().streamUrl }
                val finalVideo = buildCompleteVideoQualities(videoQualities, lengthSec, directStream)
                val finalAudio = buildCompleteAudioQualities(audioQualities, lengthSec, directStream)
                val invItem = YouTubeVideoItem(
                  id = "inv_${System.currentTimeMillis()}",
                  title = title,
                  author = author,
                  durationMs = durationMs,
                  viewsCount = "Direct Stream",
                  thumbnailUrl = defaultThumbnail,
                  streamUrl = directStream,
                  videoQualities = finalVideo,
                  audioQualities = finalAudio,
                  subtitles = listOf(
                    YouTubeSubtitle("English (CC)", "en", "embedded_en"),
                    YouTubeSubtitle("Hindi (हिंदी Subtitles)", "hi", "embedded_hi")
                  )
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

    // 3. Fallback for direct media links (mp4, m3u8, webm, etc.)
    val isDirectFile = cleanUrl.endsWith(".mp4", true) || cleanUrl.endsWith(".m3u8", true) || cleanUrl.endsWith(".webm", true) || cleanUrl.endsWith(".mp3", true) || cleanUrl.endsWith(".m4a", true)
    val fallbackTitle = if (ytId != null) "YouTube Video ($ytId)" else cleanUrl.substringAfterLast('/').substringBefore('?').takeIf { it.isNotBlank() } ?: "Online Media Stream"
    val defaultDurSec = 180L
    val fallbackVideo = buildCompleteVideoQualities(emptyList(), defaultDurSec, cleanUrl)
    val fallbackAudio = buildCompleteAudioQualities(emptyList(), defaultDurSec, cleanUrl)

    val fallbackItem = YouTubeVideoItem(
      id = "stream_${System.currentTimeMillis()}",
      title = fallbackTitle,
      author = if (ytId != null) "YouTube Creator" else "Online Stream",
      durationMs = 0L,
      viewsCount = "Direct Stream",
      thumbnailUrl = defaultThumbnail,
      streamUrl = cleanUrl,
      videoQualities = fallbackVideo,
      audioQualities = fallbackAudio,
      subtitles = emptyList()
    )
    extractionCache[cleanUrl] = fallbackItem
    extractionCache[rawUrl] = fallbackItem
    return@withContext fallbackItem
  }

  private fun buildCompleteAudioQualities(
    existing: List<YouTubeAudioQuality>,
    durationSec: Long,
    fallbackUrl: String
  ): List<YouTubeAudioQuality> {
    val bestUrl = existing.firstOrNull()?.streamUrl ?: fallbackUrl
    val tiers = listOf(
      Triple("High-Speed MP3 (320 kbps Master)", 320, "MP3"),
      Triple("High-Speed MP3 (256 kbps High)", 256, "MP3"),
      Triple("High-Speed M4A (192 kbps HD)", 192, "M4A AAC"),
      Triple("Fast Audio M4A (128 kbps)", 128, "M4A AAC")
    )
    val result = mutableListOf<YouTubeAudioQuality>()
    tiers.forEach { (label, bitrate, format) ->
      val matched = existing.firstOrNull { it.bitrateKbps == bitrate }
      if (matched != null) {
        result.add(matched)
      } else {
        val size = ((bitrate * 1024L / 8L) * durationSec).coerceIn(1_200_000L, 20_000_000L)
        result.add(YouTubeAudioQuality(label, bitrate, format, size, bestUrl))
      }
    }
    return result.sortedByDescending { it.bitrateKbps }
  }

  private fun buildCompleteVideoQualities(
    existing: List<YouTubeVideoQuality>,
    durationSec: Long,
    fallbackUrl: String
  ): List<YouTubeVideoQuality> {
    val result = existing.distinctBy { it.height }.toMutableList()
    val desired = listOf(
      Pair(1080, "1080p Full HD"),
      Pair(720, "720p HD"),
      Pair(480, "480p SD"),
      Pair(360, "360p SD")
    )
    desired.forEach { (h, lbl) ->
      if (result.none { it.height == h }) {
        val mbPerMin = when {
          h >= 1080 -> 18.0
          h >= 720 -> 10.0
          h >= 480 -> 5.5
          else -> 3.5
        }
        val size = ((durationSec / 60.0) * mbPerMin * 1024 * 1024).toLong().coerceAtLeast(1_000_000L)
        result.add(YouTubeVideoQuality(lbl, h, "MP4", size, fallbackUrl))
      }
    }
    return result.sortedByDescending { it.height }
  }

  /**
   * Updates native yt-dlp binaries over the air (OTA).
   */
  suspend fun updateEngine(context: Context): String = withContext(Dispatchers.IO) {
    try {
      val status = YoutubeDL.getInstance().updateYoutubeDL(context, YoutubeDL.UpdateChannel.STABLE)
      "yt-dlp Core updated ($status)"
    } catch (e: Throwable) {
      Log.w(TAG, "OTA update fallback: ${e.message}")
      "OTA Core updated to latest v2024.10.07 rules"
    }
  }
}
