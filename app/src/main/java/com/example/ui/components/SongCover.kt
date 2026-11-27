package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import com.example.ui.theme.AlaktraCard
import com.example.ui.theme.AlaktraMint
import kotlin.math.ceil

private val SUPPORTED_THUMB_BUCKETS = intArrayOf(128, 256, 400, 800)

/**
 * Maps a remote `/covers/<file>` URL to a density-matched `/cover-thumb/<bucket>/<file>` URL
 * when the rendered target size in pixels is small (< 500px), keeping full resolution for
 * large artwork surfaces (>= 500px or unspecified) and local file:// URIs.
 */
fun resolveAdaptiveCoverUrl(imageUrl: String, targetPx: Int?): String {
    if (imageUrl.isBlank()) return imageUrl
    if (targetPx == null || targetPx <= 0 || targetPx >= 500) return imageUrl
    if (!imageUrl.startsWith("http://") && !imageUrl.startsWith("https://")) return imageUrl
    val coversIdx = imageUrl.indexOf("/covers/")
    if (coversIdx < 0) return imageUrl

    val bucket = SUPPORTED_THUMB_BUCKETS.firstOrNull { it >= targetPx } ?: return imageUrl
    val prefix = imageUrl.substring(0, coversIdx)
    val suffix = imageUrl.substring(coversIdx + "/covers/".length)
    if (suffix.isBlank() || suffix.contains("/")) return imageUrl
    return "$prefix/cover-thumb/$bucket/$suffix"
}

@Composable
fun SongCover(
    imageUrl: String,
    size: Dp = 56.dp,
    cornerRadius: Dp = 8.dp,
    modifier: Modifier = Modifier,
    onArtworkLoaded: ((android.graphics.drawable.Drawable) -> Unit)? = null
) {
    val shape = RoundedCornerShape(cornerRadius)
    val baseModifier = if (size != Dp.Unspecified) modifier.size(size) else modifier.aspectRatio(1f)
    val iconFallbackSize = if (size != Dp.Unspecified) size / 2.2f else 28.dp
    val context = LocalContext.current
    val density = LocalDensity.current
    val targetPx = if (size != Dp.Unspecified) {
        with(density) { ceil(size.toPx().toDouble()).toInt().coerceAtLeast(1) }
    } else {
        null
    }

    // Preserve original full-res URL for color extraction / hero / large surfaces
    val useThumbnail = onArtworkLoaded == null && targetPx != null && targetPx < 500
    val adaptiveUrl = remember(imageUrl, targetPx, useThumbnail) {
        if (useThumbnail) resolveAdaptiveCoverUrl(imageUrl, targetPx) else imageUrl
    }
    var fallbackToOriginal by remember(imageUrl, adaptiveUrl) { mutableStateOf(false) }
    val effectiveUrl = if (fallbackToOriginal) imageUrl else adaptiveUrl

    Box(
        modifier = baseModifier
            .clip(shape)
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF222433), AlaktraCard)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // Fast background placeholder icon visible during load or on failure
        Icon(
            imageVector = Icons.Default.MusicNote,
            contentDescription = null,
            tint = AlaktraMint.copy(alpha = 0.5f),
            modifier = Modifier.size(iconFallbackSize)
        )

        if (effectiveUrl.isNotBlank()) {
            val needsSoftwareBitmap = onArtworkLoaded != null
            val requestPx = if (!fallbackToOriginal && targetPx != null && targetPx < 500) targetPx else null
            AsyncImage(
                model = remember(effectiveUrl, needsSoftwareBitmap, requestPx) {
                    ImageRequest.Builder(context)
                        .data(effectiveUrl)
                        .apply {
                            if (requestPx != null) {
                                size(requestPx, requestPx)
                                precision(Precision.INEXACT)
                            }
                            if (needsSoftwareBitmap) {
                                allowHardware(false)
                            }
                        }
                        .crossfade(true)
                        .build()
                },
                onSuccess = if (onArtworkLoaded != null) {
                    { state -> onArtworkLoaded(state.result.drawable) }
                } else {
                    null
                },
                onError = if (adaptiveUrl != imageUrl && !fallbackToOriginal) {
                    { fallbackToOriginal = true }
                } else {
                    null
                },
                contentDescription = "Song Artwork",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
