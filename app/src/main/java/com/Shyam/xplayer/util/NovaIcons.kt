package com.Shyam.xplayer.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

private fun buildVector(name: String, block: PathBuilder.() -> Unit): ImageVector {
    return ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(
        fill = SolidColor(Color.White)
    ) {
        block()
    }.build()
}

object NovaIcons {
    val ArrowBack: ImageVector by lazy {
        buildVector("ArrowBack") {
            moveTo(20f, 11f)
            horizontalLineTo(7.83f)
            lineToRelative(5.59f, -5.59f)
            lineTo(12f, 4f)
            lineToRelative(-8f, 8f)
            lineToRelative(8f, 8f)
            lineToRelative(1.41f, -1.41f)
            lineTo(7.83f, 13f)
            horizontalLineTo(20f)
            verticalLineToRelative(-2f)
            close()
        }
    }

    val Close: ImageVector by lazy {
        buildVector("Close") {
            moveTo(19f, 6.41f)
            lineTo(17.59f, 5f)
            lineTo(12f, 10.59f)
            lineTo(6.41f, 5f)
            lineTo(5f, 6.41f)
            lineTo(10.59f, 12f)
            lineTo(5f, 17.59f)
            lineTo(6.41f, 19f)
            lineTo(12f, 13.41f)
            lineTo(17.59f, 19f)
            lineTo(19f, 17.59f)
            lineTo(13.41f, 12f)
            close()
        }
    }

    val Search: ImageVector by lazy {
        buildVector("Search") {
            moveTo(15.5f, 14f)
            horizontalLineToRelative(-0.79f)
            lineToRelative(-0.28f, -0.27f)
            curveTo(15.41f, 12.59f, 16f, 11.11f, 16f, 9.5f)
            curveTo(16f, 5.91f, 13.09f, 3f, 9.5f, 3f)
            reflectiveCurveTo(3f, 5.91f, 3f, 9.5f)
            reflectiveCurveTo(5.91f, 16f, 9.5f, 16f)
            curveToRelative(1.61f, 0f, 3.09f, -0.59f, 4.23f, -1.57f)
            lineToRelative(0.27f, 0.28f)
            verticalLineToRelative(0.79f)
            lineToRelative(5f, 4.99f)
            lineTo(20.49f, 19f)
            lineToRelative(-4.99f, -5f)
            close()
            moveTo(9.5f, 14f)
            curveTo(7.01f, 14f, 5f, 11.99f, 5f, 9.5f)
            reflectiveCurveTo(7.01f, 5f, 9.5f, 5f)
            reflectiveCurveTo(14f, 7.01f, 14f, 9.5f)
            reflectiveCurveTo(11.99f, 14f, 9.5f, 14f)
            close()
        }
    }

    val Refresh: ImageVector by lazy {
        buildVector("Refresh") {
            moveTo(17.65f, 6.35f)
            curveTo(16.2f, 4.9f, 14.21f, 4f, 12f, 4f)
            curveToRelative(-4.42f, 0f, -7.99f, 3.58f, -7.99f, 8f)
            reflectiveCurveToRelative(3.57f, 8f, 7.99f, 8f)
            curveToRelative(3.73f, 0f, 6.84f, -2.55f, 7.73f, -6f)
            horizontalLineToRelative(-2.08f)
            curveToRelative(-0.82f, 2.33f, -3.04f, 4f, -5.65f, 4f)
            curveToRelative(-3.31f, 0f, -6f, -2.69f, -6f, -6f)
            reflectiveCurveToRelative(2.69f, -6f, 6f, -6f)
            curveToRelative(1.66f, 0f, 3.14f, 0.69f, 4.22f, 1.78f)
            lineTo(13f, 11f)
            horizontalLineToRelative(7f)
            verticalLineTo(4f)
            lineToRelative(-2.35f, 2.35f)
            close()
        }
    }

    val Check: ImageVector by lazy {
        buildVector("Check") {
            moveTo(9f, 16.17f)
            lineTo(4.83f, 12f)
            lineToRelative(-1.42f, 1.41f)
            lineTo(9f, 19f)
            lineTo(21f, 7f)
            lineToRelative(-1.41f, -1.41f)
            close()
        }
    }

    val PlayArrow: ImageVector by lazy {
        buildVector("PlayArrow") {
            moveTo(8f, 5f)
            verticalLineToRelative(14f)
            lineToRelative(11f, -7f)
            close()
        }
    }

    val Download: ImageVector by lazy {
        buildVector("Download") {
            moveTo(19f, 9f)
            horizontalLineToRelative(-4f)
            verticalLineTo(3f)
            horizontalLineTo(9f)
            verticalLineToRelative(6f)
            horizontalLineTo(5f)
            lineToRelative(7f, 7f)
            lineToRelative(7f, -7f)
            close()
            moveTo(5f, 18f)
            verticalLineToRelative(2f)
            horizontalLineToRelative(14f)
            verticalLineToRelative(-2f)
            horizontalLineTo(5f)
            close()
        }
    }

    val DownloadDone: ImageVector by lazy {
        buildVector("DownloadDone") {
            moveTo(20.13f, 5.41f)
            lineTo(18.72f, 4f)
            lineToRelative(-9.19f, 9.19f)
            lineToRelative(-4.25f, -4.24f)
            lineToRelative(-1.41f, 1.41f)
            lineToRelative(5.66f, 5.66f)
            close()
            moveTo(5f, 18f)
            verticalLineToRelative(2f)
            horizontalLineToRelative(14f)
            verticalLineToRelative(-2f)
            horizontalLineTo(5f)
            close()
        }
    }

    val Folder: ImageVector by lazy {
        buildVector("Folder") {
            moveTo(10f, 4f)
            horizontalLineTo(4f)
            curveToRelative(-1.1f, 0f, -1.99f, 0.9f, -1.99f, 2f)
            lineTo(2f, 18f)
            curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
            horizontalLineToRelative(16f)
            curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
            verticalLineTo(8f)
            curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
            horizontalLineToRelative(-8f)
            lineToRelative(-2f, -2f)
            close()
        }
    }

    val ElectricBolt: ImageVector by lazy {
        buildVector("ElectricBolt") {
            moveTo(15f, 2f)
            lineTo(4f, 14f)
            horizontalLineToRelative(7f)
            verticalLineToRelative(8f)
            lineToRelative(11f, -12f)
            horizontalLineToRelative(-7f)
            close()
        }
    }

    val HighQuality: ImageVector by lazy {
        buildVector("HighQuality") {
            moveTo(19f, 4f)
            horizontalLineTo(5f)
            curveToRelative(-1.11f, 0f, -2f, 0.9f, -2f, 2f)
            verticalLineToRelative(12f)
            curveToRelative(0f, 1.1f, 0.89f, 2f, 2f, 2f)
            horizontalLineToRelative(14f)
            curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
            verticalLineTo(6f)
            curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
            close()
            moveTo(11f, 15f)
            horizontalLineTo(9.5f)
            verticalLineToRelative(-2f)
            horizontalLineToRelative(-2f)
            verticalLineToRelative(2f)
            horizontalLineTo(6f)
            verticalLineTo(9f)
            horizontalLineToRelative(1.5f)
            verticalLineToRelative(2.5f)
            horizontalLineToRelative(2f)
            verticalLineTo(9f)
            horizontalLineTo(11f)
            verticalLineToRelative(6f)
            close()
            moveTo(18f, 14f)
            curveToRelative(0f, 0.55f, -0.45f, 1f, -1f, 1f)
            horizontalLineToRelative(-4f)
            curveToRelative(-0.55f, 0f, -1f, -0.45f, -1f, -1f)
            verticalLineToRelative(-4f)
            curveToRelative(0f, -0.55f, 0.45f, -1f, 1f, -1f)
            horizontalLineToRelative(4f)
            curveToRelative(0.55f, 0f, 1f, 0.45f, 1f, 1f)
            verticalLineToRelative(4f)
            close()
        }
    }

    val Movie: ImageVector by lazy {
        buildVector("Movie") {
            moveTo(18f, 4f)
            lineToRelative(2f, 4f)
            horizontalLineToRelative(-3f)
            lineToRelative(-2f, -4f)
            horizontalLineToRelative(-2f)
            lineToRelative(2f, 4f)
            horizontalLineToRelative(-3f)
            lineToRelative(-2f, -4f)
            horizontalLineTo(8f)
            lineToRelative(2f, 4f)
            horizontalLineTo(7f)
            lineTo(5f, 4f)
            horizontalLineTo(4f)
            curveToRelative(-1.1f, 0f, -1.99f, 0.9f, -1.99f, 2f)
            lineTo(2f, 18f)
            curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
            horizontalLineToRelative(16f)
            curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
            verticalLineTo(4f)
            horizontalLineToRelative(-4f)
            close()
        }
    }

    val MusicNote: ImageVector by lazy {
        buildVector("MusicNote") {
            moveTo(12f, 3f)
            verticalLineToRelative(10.55f)
            curveToRelative(-0.59f, -0.34f, -1.27f, -0.55f, -2f, -0.55f)
            curveToRelative(-2.21f, 0f, -4f, 1.79f, -4f, 4f)
            reflectiveCurveToRelative(1.79f, 4f, 4f, 4f)
            reflectiveCurveToRelative(4f, -1.79f, 4f, -4f)
            verticalLineTo(7f)
            horizontalLineToRelative(4f)
            verticalLineTo(3f)
            horizontalLineToRelative(-6f)
            close()
        }
    }

    val Speed: ImageVector by lazy {
        buildVector("Speed") {
            moveTo(20.38f, 8.57f)
            lineToRelative(-1.23f, 1.85f)
            arcToRelative(8f, 8f, 0f, isMoreThanHalf = false, isPositiveArc = false, -12.3f, 0f)
            lineTo(5.62f, 8.57f)
            arcTo(10f, 10f, 0f, isMoreThanHalf = false, isPositiveArc = true, 21.6f, 12f)
            arcToRelative(9.98f, 9.98f, 0f, isMoreThanHalf = false, isPositiveArc = true, -1.22f, -3.43f)
            close()
            moveTo(12f, 13f)
            arcToRelative(2f, 2f, 0f, isMoreThanHalf = true, isPositiveArc = false, 2f, 2f)
            lineToRelative(3.5f, -3.5f)
            lineTo(12f, 13f)
            close()
        }
    }

    val CloudDownload: ImageVector by lazy {
        buildVector("CloudDownload") {
            moveTo(19.35f, 10.04f)
            arcTo(7.49f, 7.49f, 0f, isMoreThanHalf = false, isPositiveArc = false, 12f, 4f)
            curveTo(9.11f, 4f, 6.6f, 5.64f, 5.35f, 8.04f)
            curveTo(2.34f, 8.36f, 0f, 10.91f, 0f, 14f)
            curveToRelative(0f, 3.31f, 2.69f, 6f, 6f, 6f)
            horizontalLineToRelative(13f)
            curveToRelative(2.76f, 0f, 5f, -2.24f, 5f, -5f)
            curveToRelative(0f, -2.64f, -2.05f, -4.78f, -4.65f, -4.96f)
            close()
            moveTo(17f, 13f)
            lineToRelative(-5f, 5f)
            lineToRelative(-5f, -5f)
            horizontalLineToRelative(3f)
            verticalLineTo(9f)
            horizontalLineToRelative(4f)
            verticalLineToRelative(4f)
            horizontalLineToRelative(3f)
            close()
        }
    }

    val ContentPaste: ImageVector by lazy {
        buildVector("ContentPaste") {
            moveTo(19f, 2f)
            horizontalLineToRelative(-4.18f)
            curveTo(14.4f, 0.84f, 13.3f, 0f, 12f, 0f)
            curveToRelative(-1.3f, 0f, -2.4f, 0.84f, -2.82f, 2f)
            horizontalLineTo(5f)
            curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
            verticalLineToRelative(16f)
            curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
            horizontalLineToRelative(14f)
            curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
            verticalLineTo(4f)
            curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
            close()
            moveTo(12f, 2f)
            curveToRelative(0.55f, 0f, 1f, 0.45f, 1f, 1f)
            reflectiveCurveToRelative(-0.45f, 1f, -1f, 1f)
            reflectiveCurveToRelative(-1f, -0.45f, -1f, -1f)
            reflectiveCurveToRelative(0.45f, -1f, 1f, -1f)
            close()
        }
    }

    val FastForward: ImageVector by lazy {
        buildVector("FastForward") {
            moveTo(4f, 18f)
            lineToRelative(8.5f, -6f)
            lineTo(4f, 6f)
            verticalLineToRelative(12f)
            close()
            moveTo(13f, 6f)
            verticalLineToRelative(12f)
            lineToRelative(8.5f, -6f)
            lineTo(13f, 6f)
            close()
        }
    }

    val FastRewind: ImageVector by lazy {
        buildVector("FastRewind") {
            moveTo(11f, 18f)
            verticalLineTo(6f)
            lineToRelative(-8.5f, 6f)
            lineTo(11f, 18f)
            close()
            moveTo(11.5f, 12f)
            lineTo(20f, 18f)
            verticalLineTo(6f)
            lineToRelative(-8.5f, 6f)
            close()
        }
    }

    val Pause: ImageVector by lazy {
        buildVector("Pause") {
            moveTo(6f, 19f)
            horizontalLineToRelative(4f)
            verticalLineTo(5f)
            horizontalLineTo(6f)
            verticalLineToRelative(14f)
            close()
            moveTo(14f, 5f)
            verticalLineToRelative(14f)
            horizontalLineToRelative(4f)
            verticalLineTo(5f)
            horizontalLineToRelative(-4f)
            close()
        }
    }

    val Replay: ImageVector by lazy {
        buildVector("Replay") {
            moveTo(12f, 5f)
            verticalLineTo(1f)
            lineTo(7f, 6f)
            lineToRelative(5f, 5f)
            verticalLineTo(7f)
            curveToRelative(3.31f, 0f, 6f, 2.69f, 6f, 6f)
            reflectiveCurveToRelative(-2.69f, 6f, -6f, 6f)
            reflectiveCurveToRelative(-6f, -2.69f, -6f, -6f)
            horizontalLineTo(4f)
            curveToRelative(0f, 4.42f, 3.58f, 8f, 8f, 8f)
            reflectiveCurveToRelative(8f, -3.58f, 8f, -8f)
            reflectiveCurveToRelative(-3.58f, -8f, -8f, -8f)
            close()
        }
    }

    val VolumeDown: ImageVector by lazy {
        buildVector("VolumeDown") {
            moveTo(18.5f, 12f)
            curveToRelative(0f, -1.77f, -1.02f, -3.29f, -2.5f, -4.03f)
            verticalLineToRelative(8.05f)
            curveToRelative(1.48f, -0.73f, 2.5f, -2.25f, 2.5f, -4.02f)
            close()
            moveTo(5f, 9f)
            verticalLineToRelative(6f)
            horizontalLineToRelative(4f)
            lineToRelative(5f, 5f)
            verticalLineTo(4f)
            lineTo(9f, 9f)
            horizontalLineTo(5f)
            close()
        }
    }

    val VolumeOff: ImageVector by lazy {
        buildVector("VolumeOff") {
            moveTo(1.65f, 1.65f)
            lineTo(0.38f, 2.92f)
            lineTo(5.46f, 8f)
            horizontalLineTo(3f)
            verticalLineToRelative(8f)
            horizontalLineToRelative(4f)
            lineToRelative(5f, 5f)
            verticalLineToRelative(-6.54f)
            lineToRelative(4.15f, 4.15f)
            curveTo(15.42f, 19.11f, 14.53f, 19.5f, 13.5f, 19.72f)
            verticalLineToRelative(2.06f)
            curveToRelative(1.58f, -0.27f, 3.01f, -0.95f, 4.2f, -1.89f)
            lineToRelative(2.38f, 2.38f)
            lineToRelative(1.27f, -1.27f)
            lineTo(1.65f, 1.65f)
            close()
            moveTo(12f, 4f)
            lineTo(9.91f, 6.09f)
            lineTo(12f, 8.18f)
            verticalLineTo(4f)
            close()
        }
    }

    val VolumeUp: ImageVector by lazy {
        buildVector("VolumeUp") {
            moveTo(3f, 9f)
            verticalLineToRelative(6f)
            horizontalLineToRelative(4f)
            lineToRelative(5f, 5f)
            verticalLineTo(4f)
            lineTo(7f, 9f)
            horizontalLineTo(3f)
            close()
            moveTo(16.5f, 12f)
            curveToRelative(0f, -1.77f, -1.02f, -3.29f, -2.5f, -4.03f)
            verticalLineToRelative(8.05f)
            curveToRelative(1.48f, -0.73f, 2.5f, -2.25f, 2.5f, -4.02f)
            close()
            moveTo(14f, 3.23f)
            verticalLineToRelative(2.06f)
            curveToRelative(2.89f, 0.86f, 5f, 3.54f, 5f, 6.71f)
            reflectiveCurveToRelative(-2.11f, 5.85f, -5f, 6.71f)
            verticalLineToRelative(2.06f)
            curveToRelative(4.01f, -0.91f, 7f, -4.49f, 7f, -8.77f)
            reflectiveCurveToRelative(-2.99f, -7.86f, -7f, -8.77f)
            close()
        }
    }

    val Videocam: ImageVector by lazy {
        buildVector("Videocam") {
            moveTo(17f, 10.5f)
            verticalLineTo(7f)
            curveToRelative(0f, -0.55f, -0.45f, -1f, -1f, -1f)
            horizontalLineTo(4f)
            curveToRelative(-0.55f, 0f, -1f, 0.45f, -1f, 1f)
            verticalLineToRelative(10f)
            curveToRelative(0f, 0.55f, 0.45f, 1f, 1f, 1f)
            horizontalLineToRelative(12f)
            curveToRelative(0.55f, 0f, 1f, -0.45f, 1f, -1f)
            verticalLineToRelative(-3.5f)
            lineToRelative(4f, 4f)
            verticalLineToRelative(-11f)
            lineToRelative(-4f, 4f)
            close()
        }
    }

    val Language: ImageVector by lazy {
        buildVector("Language") {
            moveTo(11.99f, 2f)
            curveTo(6.47f, 2f, 2f, 6.48f, 2f, 12f)
            reflectiveCurveToRelative(4.47f, 10f, 9.99f, 10f)
            curveTo(17.52f, 22f, 22f, 17.52f, 22f, 12f)
            reflectiveCurveTo(17.52f, 2f, 11.99f, 2f)
            close()
            moveTo(12f, 20f)
            curveToRelative(-4.42f, 0f, -8f, -3.58f, -8f, -8f)
            reflectiveCurveToRelative(3.58f, -8f, 8f, -8f)
            reflectiveCurveToRelative(8f, 3.58f, 8f, 8f)
            reflectiveCurveToRelative(-3.58f, 8f, -8f, 8f)
            close()
        }
    }

    val CleaningServices: ImageVector by lazy {
        buildVector("CleaningServices") {
            moveTo(16f, 11f)
            horizontalLineToRelative(-1f)
            verticalLineTo(3f)
            curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
            horizontalLineToRelative(-2f)
            curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
            verticalLineToRelative(8f)
            horizontalLineTo(8f)
            curveToRelative(-2.76f, 0f, -5f, 2.24f, -5f, 5f)
            verticalLineToRelative(7f)
            horizontalLineToRelative(18f)
            verticalLineToRelative(-7f)
            curveToRelative(0f, -2.76f, -2.24f, -5f, -5f, -5f)
            close()
        }
    }

    val Code: ImageVector by lazy {
        buildVector("Code") {
            moveTo(9.4f, 16.6f)
            lineTo(4.8f, 12f)
            lineToRelative(4.6f, -4.6f)
            lineTo(8f, 6f)
            lineToRelative(-6f, 6f)
            lineToRelative(6f, 6f)
            lineToRelative(1.4f, -1.4f)
            close()
            moveTo(14.6f, 16.6f)
            lineToRelative(4.6f, -4.6f)
            lineToRelative(-4.6f, -4.6f)
            lineTo(16f, 6f)
            lineToRelative(6f, 6f)
            lineToRelative(-6f, 6f)
            lineToRelative(-1.4f, -1.4f)
            close()
        }
    }

    val NetworkCheck: ImageVector by lazy {
        buildVector("NetworkCheck") {
            moveTo(15.9f, 5f)
            curveToRelative(-0.17f, 0f, -0.32f, 0.09f, -0.41f, 0.23f)
            lineToRelative(-9f, 15f)
            curveTo(6.34f, 20.48f, 6.55f, 21f, 7f, 21f)
            horizontalLineToRelative(10f)
            curveToRelative(0.55f, 0f, 1f, -0.45f, 1f, -1f)
            verticalLineTo(6f)
            curveToRelative(0f, -0.55f, -0.45f, -1f, -1f, -1f)
            horizontalLineToRelative(-1.1f)
            close()
        }
    }

    val Palette: ImageVector by lazy {
        buildVector("Palette") {
            moveTo(12f, 3f)
            curveToRelative(-4.97f, 0f, -9f, 4.03f, -9f, 9f)
            reflectiveCurveToRelative(4.03f, 9f, 9f, 9f)
            curveToRelative(0.83f, 0f, 1.5f, -0.67f, 1.5f, -1.5f)
            curveToRelative(0f, -0.39f, -0.15f, -0.74f, -0.39f, -1.01f)
            curveToRelative(-0.23f, -0.26f, -0.38f, -0.61f, -0.38f, -0.99f)
            curveToRelative(0f, -0.83f, 0.67f, -1.5f, 1.5f, -1.5f)
            horizontalLineTo(16f)
            curveToRelative(2.76f, 0f, 5f, -2.24f, 5f, -5f)
            curveToRelative(0f, -4.42f, -4.03f, -8f, -9f, -8f)
            close()
        }
    }

    val Security: ImageVector by lazy {
        buildVector("Security") {
            moveTo(12f, 1f)
            lineTo(3f, 5f)
            verticalLineToRelative(6f)
            curveToRelative(0f, 5.55f, 3.84f, 10.74f, 9f, 12f)
            curveToRelative(5.16f, -1.26f, 9f, -6.45f, 9f, -12f)
            verticalLineTo(5f)
            lineToRelative(-9f, -4f)
            close()
        }
    }

    val Tune: ImageVector by lazy {
        buildVector("Tune") {
            moveTo(3f, 17f)
            verticalLineToRelative(2f)
            horizontalLineToRelative(6f)
            verticalLineToRelative(-2f)
            horizontalLineTo(3f)
            close()
            moveTo(3f, 5f)
            verticalLineToRelative(2f)
            horizontalLineToRelative(10f)
            verticalLineTo(5f)
            horizontalLineTo(3f)
            close()
            moveTo(13f, 21f)
            verticalLineToRelative(-2f)
            horizontalLineToRelative(8f)
            verticalLineToRelative(-2f)
            horizontalLineToRelative(-8f)
            verticalLineToRelative(-2f)
            horizontalLineToRelative(-2f)
            verticalLineToRelative(6f)
            horizontalLineToRelative(2f)
            close()
        }
    }

    val Wifi: ImageVector by lazy {
        buildVector("Wifi") {
            moveTo(12.01f, 21.49f)
            lineTo(23.64f, 7f)
            curveToRelative(-0.45f, -0.34f, -4.93f, -4f, -11.64f, -4f)
            curveTo(5.28f, 3f, 0.81f, 6.66f, 0.36f, 7f)
            lineToRelative(11.63f, 14.49f)
            curveToRelative(0.01f, 0.01f, 0.01f, 0.01f, 0.02f, 0f)
            close()
        }
    }

    val OpenInNew: ImageVector by lazy {
        buildVector("OpenInNew") {
            moveTo(19f, 19f)
            horizontalLineTo(5f)
            verticalLineTo(5f)
            horizontalLineToRelative(7f)
            verticalLineTo(3f)
            horizontalLineTo(5f)
            curveToRelative(-1.11f, 0f, -2f, 0.9f, -2f, 2f)
            verticalLineToRelative(14f)
            curveToRelative(0f, 1.1f, 0.89f, 2f, 2f, 2f)
            horizontalLineToRelative(14f)
            curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
            verticalLineToRelative(-7f)
            horizontalLineToRelative(-2f)
            verticalLineToRelative(7f)
            close()
            moveTo(14f, 3f)
            verticalLineToRelative(2f)
            horizontalLineToRelative(3.59f)
            lineToRelative(-9.83f, 9.83f)
            lineToRelative(1.41f, 1.41f)
            lineTo(19f, 6.41f)
            verticalLineTo(10f)
            horizontalLineToRelative(2f)
            verticalLineTo(3f)
            horizontalLineToRelative(-7f)
            close()
        }
    }

    val AudioFile: ImageVector by lazy {
        buildVector("AudioFile") {
            moveTo(14f, 2f)
            horizontalLineTo(6f)
            curveToRelative(-1.1f, 0f, -1.99f, 0.9f, -1.99f, 2f)
            lineTo(4f, 20f)
            curveToRelative(0f, 1.1f, 0.89f, 2f, 1.99f, 2f)
            horizontalLineTo(18f)
            curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
            verticalLineTo(8f)
            lineToRelative(-6f, -6f)
            close()
            moveTo(13f, 9f)
            verticalLineTo(3.5f)
            lineTo(18.5f, 9f)
            horizontalLineTo(13f)
            close()
        }
    }

    val Save: ImageVector by lazy {
        buildVector("Save") {
            moveTo(17f, 3f)
            horizontalLineTo(5f)
            curveToRelative(-1.11f, 0f, -2f, 0.9f, -2f, 2f)
            verticalLineToRelative(14f)
            curveToRelative(0f, 1.1f, 0.89f, 2f, 2f, 2f)
            horizontalLineToRelative(14f)
            curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
            verticalLineTo(7f)
            lineToRelative(-4f, -4f)
            close()
            moveTo(12f, 19f)
            curveToRelative(-1.66f, 0f, -3f, -1.34f, -3f, -3f)
            reflectiveCurveToRelative(1.34f, -3f, 3f, -3f)
            reflectiveCurveToRelative(3f, 1.34f, 3f, 3f)
            reflectiveCurveToRelative(-1.34f, 3f, -3f, 3f)
            close()
            moveTo(15f, 9f)
            horizontalLineTo(5f)
            verticalLineTo(5f)
            horizontalLineToRelative(10f)
            verticalLineToRelative(4f)
            close()
        }
    }
}
