package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Nightlife
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.ui.theme.AlaktraBorder
import com.example.ui.theme.AlaktraCyan
import com.example.ui.theme.AlaktraMint
import com.example.ui.theme.AlaktraSurface

data class AvatarPreset(
    val id: String,
    val name: String,
    val colors: List<Color>,
    val icon: ImageVector
)

val AVATAR_PRESETS = listOf(
    AvatarPreset("mint_cyan", "Neon Wave", listOf(AlaktraMint, AlaktraCyan), Icons.Default.MusicNote),
    AvatarPreset("violet_pink", "Synth Wave", listOf(Color(0xFF8B5CF6), Color(0xFFEC4899)), Icons.Default.Headset),
    AvatarPreset("amber_orange", "Golden Bass", listOf(Color(0xFFF59E0B), Color(0xFFEF4444)), Icons.Default.GraphicEq),
    AvatarPreset("blue_indigo", "Cosmic Chill", listOf(Color(0xFF3B82F6), Color(0xFF6366F1)), Icons.Default.Nightlife),
    AvatarPreset("emerald_teal", "Emerald Beat", listOf(Color(0xFF10B981), Color(0xFF14B8A6)), Icons.Default.Album),
    AvatarPreset("ruby_red", "Rock Pulse", listOf(Color(0xFFF43F5E), Color(0xFF991B1B)), Icons.Default.Audiotrack)
)

fun isRemoteOrFileAvatar(avatarUrl: String?): Boolean {
    val raw = avatarUrl?.trim().orEmpty()
    return raw.startsWith("http://", ignoreCase = true) ||
        raw.startsWith("https://", ignoreCase = true) ||
        raw.startsWith("content://", ignoreCase = true) ||
        raw.startsWith("file://", ignoreCase = true) ||
        raw.startsWith("/")
}

@Composable
fun UserAvatar(
    avatarUrl: String?,
    size: Dp,
    modifier: Modifier = Modifier,
    iconSize: Dp = (size * 0.5f),
    borderWidth: Dp = 1.dp,
    defaultFallbackToFirstPreset: Boolean = false,
    contentDescription: String = "Profile Avatar"
) {
    val context = LocalContext.current
    val preset = remember(avatarUrl, defaultFallbackToFirstPreset) {
        AVATAR_PRESETS.firstOrNull { it.id == avatarUrl }
            ?: if (defaultFallbackToFirstPreset) AVATAR_PRESETS.first() else null
    }
    val hasImageUrl = remember(avatarUrl) { isRemoteOrFileAvatar(avatarUrl) }
    var imageFailed by remember(avatarUrl) { mutableStateOf(false) }

    val bgBrush = remember(preset) {
        if (preset != null) {
            Brush.linearGradient(preset.colors)
        } else {
            Brush.linearGradient(listOf(AlaktraSurface, AlaktraSurface))
        }
    }
    val borderColor = if (hasImageUrl || preset != null) {
        Color.White.copy(alpha = 0.28f)
    } else {
        AlaktraBorder
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(bgBrush)
            .border(borderWidth, borderColor, CircleShape)
    ) {
        if (hasImageUrl && !imageFailed && !avatarUrl.isNullOrBlank()) {
            AsyncImage(
                model = remember(avatarUrl) {
                    ImageRequest.Builder(context)
                        .data(avatarUrl)
                        .crossfade(true)
                        .build()
                },
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                onError = { imageFailed = true },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                imageVector = preset?.icon ?: Icons.Default.Person,
                contentDescription = contentDescription,
                tint = if (preset != null) Color.White else AlaktraMint,
                modifier = Modifier.size(iconSize)
            )
        }
    }
}
