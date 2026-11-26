package com.example.ui.screens.profile

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.audio.AudioCache
import com.example.audio.AudioController
import com.example.data.repository.MusicRepository
import com.example.ui.components.AVATAR_PRESETS
import com.example.ui.components.CacheCapSettingsDialog
import com.example.ui.components.UserAvatar
import com.example.ui.components.isRemoteOrFileAvatar
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    repository: MusicRepository,
    audioController: AudioController,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onNavigateToSoundCapsule: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val user by repository.currentUser.collectAsStateWithLifecycle()
    val likedCount = remember { repository.authPreferences.getLikedSongIds().size }

    // Dialog state
    var showEditProfileDialog by remember { mutableStateOf(false) }
    var showAvatarPickerDialog by remember { mutableStateOf(false) }
    var showChangePasswordDialog by remember { mutableStateOf(false) }
    var showLogoutConfirm by remember { mutableStateOf(false) }
    var showDeleteAccountConfirm by remember { mutableStateOf(false) }

    // Avatar Upload State
    var isUpdatingAvatar by remember { mutableStateOf(false) }
    var avatarDialogError by remember { mutableStateOf<String?>(null) }

    val avatarPhotoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                isUpdatingAvatar = true
                avatarDialogError = null
                val result = repository.uploadUserAvatar(uri)
                isUpdatingAvatar = false
                result.onSuccess {
                    showAvatarPickerDialog = false
                    Toast.makeText(context, "Profile picture updated", Toast.LENGTH_SHORT).show()
                }.onFailure { err ->
                    avatarDialogError = err.message ?: "Failed to upload profile picture"
                }
            }
        }
    }

    // Server & Cache State
    var serverUrl by remember { mutableStateOf(repository.authPreferences.getServerBaseUrl()) }
    var showServerConfigDialog by remember { mutableStateOf(false) }
    var isConnected by remember { mutableStateOf<Boolean?>(null) }
    var isCheckingConnection by remember { mutableStateOf(false) }
    var downloadedCount by remember { mutableIntStateOf(0) }
    var audioCacheBytes by remember { mutableLongStateOf(AudioCache.getCacheSizeBytes(context)) }
    var cacheCapMb by remember { mutableIntStateOf(AudioCache.getCacheCapMb(context)) }
    var showCacheCapDialog by remember { mutableStateOf(false) }
    var imageAndHttpCacheBytes by remember {
        mutableLongStateOf(AudioCache.getImageCacheSizeBytes(context) + AudioCache.getHttpCacheSizeBytes(context))
    }

    LaunchedEffect(Unit) {
        repository.getCachedUser()
        repository.refreshUserProfile()
        isCheckingConnection = true
        isConnected = repository.testConnection()
        isCheckingConnection = false
        val songs = repository.getSongs()
        downloadedCount = songs.count { it.isDownloaded || it.localPath != null }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
            TopAppBar(
                title = { Text("Profile & Account", color = AlaktraTextPrimary, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = AlaktraTextPrimary
                        )
                    }
                },
                expandedHeight = 52.dp,
                windowInsets = WindowInsets(top = 8.dp),
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AlaktraBackground)
            )
        },
        containerColor = AlaktraBackground,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding()),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth()
                    .widthIn(max = 720.dp)
                    .padding(horizontal = 20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Spacer(modifier = Modifier.height(6.dp))

                // Profile Picture with Edit Overlay Badge
                Box(
                    contentAlignment = Alignment.BottomEnd,
                    modifier = Modifier.padding(bottom = 6.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable(enabled = !isUpdatingAvatar) {
                                avatarDialogError = null
                                showAvatarPickerDialog = true
                            }
                            .testTag("profile_avatar_button")
                    ) {
                        UserAvatar(
                            avatarUrl = user?.avatarUrl,
                            size = 96.dp,
                            iconSize = 48.dp,
                            borderWidth = 2.dp,
                            defaultFallbackToFirstPreset = true,
                            contentDescription = "Avatar"
                        )
                        if (isUpdatingAvatar) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(96.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.55f))
                            ) {
                                CircularProgressIndicator(
                                    color = AlaktraMint,
                                    modifier = Modifier.size(30.dp),
                                    strokeWidth = 2.5.dp
                                )
                            }
                        }
                    }

                    // Camera/Edit badge
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(AlaktraMint)
                            .border(2.dp, AlaktraBackground, CircleShape)
                            .clickable(enabled = !isUpdatingAvatar) {
                                avatarDialogError = null
                                showAvatarPickerDialog = true
                            }
                            .testTag("profile_avatar_edit_badge")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = "Change Profile Picture",
                            tint = Color(0xFF041C12),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Full Name
                Text(
                    text = user?.name?.ifBlank { user?.username } ?: "Music Enthusiast",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 24.sp
                    ),
                    color = AlaktraTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(3.dp))

                // Username & Email
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "@${user?.username ?: "listener"}",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = AlaktraMint
                    )
                    val emailText = user?.email?.takeIf { it.isNotBlank() }
                    if (emailText != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodyMedium,
                            color = AlaktraTextMuted
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = emailText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = AlaktraTextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Edit Profile Chip Button
                OutlinedButton(
                    onClick = { showEditProfileDialog = true },
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AlaktraTextPrimary),
                    border = ButtonDefaults.outlinedButtonBorder.copy(brush = Brush.linearGradient(listOf(AlaktraBorder, AlaktraBorder))),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = AlaktraMint
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Edit Profile", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Your Sound Capsule Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(onClick = onNavigateToSoundCapsule)
                        .testTag("profile_sound_capsule_card"),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1F1F1F)),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color.Black)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        listOf(Color(0xFF1ED760), Color(0xFF15803D))
                                    )
                                )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Equalizer,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.sound_capsule_title),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 16.sp
                                ),
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stringResource(R.string.sound_capsule_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFA7A7A7)
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = stringResource(R.string.sound_capsule_title),
                            tint = Color(0xFF1ED760)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Account & Security Section
                Text(
                    text = "Account & Security",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = AlaktraTextPrimary,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = AlaktraCard),
                    shape = RoundedCornerShape(16.dp),
                    border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(AlaktraBorder, AlaktraBorder)))
                ) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        // Change Password Row
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showChangePasswordDialog = true }
                                .padding(horizontal = 16.dp, vertical = 14.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(AlaktraMint.copy(alpha = 0.15f))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = AlaktraMint,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Change Password",
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                    color = AlaktraTextPrimary
                                )
                                Text(
                                    text = "Update your account login password",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AlaktraTextSecondary
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = AlaktraTextMuted
                            )
                        }

                        Divider(color = AlaktraBorder, modifier = Modifier.padding(horizontal = 16.dp))

                        // Sign Out Row
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showLogoutConfirm = true }
                                .padding(horizontal = 16.dp, vertical = 14.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFEF4444).copy(alpha = 0.15f))
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Logout,
                                    contentDescription = null,
                                    tint = Color(0xFFEF4444),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Log Out",
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                    color = Color(0xFFEF4444)
                                )
                                Text(
                                    text = "Sign out from this device",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AlaktraTextSecondary
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = AlaktraTextMuted
                            )
                        }

                        Divider(color = AlaktraBorder, modifier = Modifier.padding(horizontal = 16.dp))

                        // Delete Account Row
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showDeleteAccountConfirm = true }
                                .padding(horizontal = 16.dp, vertical = 14.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFDC2626).copy(alpha = 0.2f))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteForever,
                                    contentDescription = null,
                                    tint = Color(0xFFEF4444),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Delete Account",
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                    color = Color(0xFFEF4444)
                                )
                                Text(
                                    text = "Permanently remove your account and all data",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFEF4444).copy(alpha = 0.8f)
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = Color(0xFFEF4444)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Music Server Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = AlaktraCard),
                    shape = RoundedCornerShape(16.dp),
                    border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(AlaktraBorder, AlaktraBorder)))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(
                                            when (isConnected) {
                                                true -> Color(0xFF10B981)
                                                false -> Color(0xFFEF4444)
                                                null -> Color(0xFFF59E0B)
                                            }
                                        )
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Music Server / Domain",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = AlaktraTextPrimary
                                )
                            }

                            IconButton(onClick = { showServerConfigDialog = true }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit Server", tint = AlaktraMint)
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = serverUrl,
                            style = MaterialTheme.typography.bodySmall,
                            color = AlaktraMint
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = when {
                                isCheckingConnection -> "Testing server connection..."
                                isConnected == true -> "Connected to FastAPI music server"
                                isConnected == false -> "Server not reachable (verify Cloudflare tunnel is running)"
                                else -> "Status unknown"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isConnected == true) AlaktraTextSecondary else Color(0xFFEF4444)
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    isCheckingConnection = true
                                    isConnected = repository.testConnection()
                                    isCheckingConnection = false
                                }
                            },
                            enabled = !isCheckingConnection,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AlaktraTextPrimary),
                            border = ButtonDefaults.outlinedButtonBorder.copy(brush = Brush.linearGradient(listOf(AlaktraBorder, AlaktraBorder))),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isCheckingConnection) {
                                CircularProgressIndicator(color = AlaktraMint, modifier = Modifier.size(18.dp))
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp), tint = AlaktraMint)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Ping Server")
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Storage & Caching Management Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = AlaktraCard),
                    shape = RoundedCornerShape(16.dp),
                    border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(AlaktraBorder, AlaktraBorder)))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Storage & Caching",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = AlaktraTextPrimary
                            )
                            Icon(
                                imageVector = Icons.Default.Storage,
                                contentDescription = null,
                                tint = AlaktraMint,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Row 1: Streaming Audio Cache
                        val currentMb = audioCacheBytes / (1024f * 1024f)
                        val activeCapMb = cacheCapMb
                        val activeCapStr = AudioCache.formatCapMb(activeCapMb)
                        val totalCapBytes = AudioCache.getCacheCapBytes(context)
                        val usagePercent = if (totalCapBytes > 0L) {
                            ((audioCacheBytes.toFloat() / totalCapBytes.toFloat()) * 100f).roundToInt().coerceIn(0, 100)
                        } else 0

                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Streaming Audio Cache",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                        color = AlaktraTextPrimary
                                    )
                                    Text(
                                        text = String.format(Locale.US, "%.1f MB / %s (%d%% used)", currentMb, activeCapStr, usagePercent),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = AlaktraTextSecondary
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    TextButton(
                                        onClick = {
                                            AudioCache.clearCache(context)
                                            audioCacheBytes = AudioCache.getCacheSizeBytes(context)
                                            Toast.makeText(context, "Streaming audio cache cleared", Toast.LENGTH_SHORT).show()
                                        }
                                    ) {
                                        Text("Clear", color = AlaktraTextSecondary, fontSize = 13.sp)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // Cache fullness bar
                            LinearProgressIndicator(
                                progress = { if (totalCapBytes > 0L) (audioCacheBytes.toFloat() / totalCapBytes.toFloat()).coerceIn(0f, 1f) else 0f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp)),
                                color = AlaktraMint,
                                trackColor = AlaktraBorder
                            )
                        }

                        Divider(modifier = Modifier.padding(vertical = 10.dp), color = AlaktraBorder)

                        // Row 1b: Dedicated Song Cache Memory Cap Setting
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { showCacheCapDialog = true }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Song Cache Memory Cap",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                    color = AlaktraTextPrimary
                                )
                                Text(
                                    text = "Limit: $activeCapStr • Holds ~${AudioCache.estimateSongsForCap(activeCapMb)} tracks offline",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AlaktraCyan
                                )
                            }

                            OutlinedButton(
                                onClick = { showCacheCapDialog = true },
                                modifier = Modifier.testTag("change_cache_cap_button"),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = AlaktraMint),
                                border = BorderStroke(1.dp, AlaktraMint.copy(alpha = 0.5f)),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = "Adjust Cache Cap",
                                    tint = AlaktraMint,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = activeCapStr,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }

                        Divider(modifier = Modifier.padding(vertical = 8.dp), color = AlaktraBorder)

                        // Row 2: Artwork & API Response Cache
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Artwork & Network Cache",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                    color = AlaktraTextPrimary
                                )
                                val mb = imageAndHttpCacheBytes / (1024f * 1024f)
                                Text(
                                    text = String.format("%.1f MB (Coil & OkHttp)", mb),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AlaktraTextSecondary
                                )
                            }
                            TextButton(
                                onClick = {
                                    AudioCache.clearArtworkAndHttpCache(context)
                                    repository.apiCacheManager.clearAll()
                                    imageAndHttpCacheBytes = AudioCache.getImageCacheSizeBytes(context) + AudioCache.getHttpCacheSizeBytes(context)
                                    Toast.makeText(context, "Temporary artwork and API cache cleared", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Text("Clear", color = AlaktraMint, fontSize = 13.sp)
                            }
                        }

                        Divider(modifier = Modifier.padding(vertical = 8.dp), color = AlaktraBorder)

                        // Row 3: Offline Downloads
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Permanent Offline Downloads",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                    color = AlaktraTextPrimary
                                )
                                Text(
                                    text = "$downloadedCount songs stored in app storage",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AlaktraTextSecondary
                                )
                            }
                            Text(
                                text = "Protected",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = AlaktraCyan,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(160.dp))
            }
        }
    }

    // 1. Edit Profile Dialog (Name & Username)
    if (showEditProfileDialog) {
        var newName by remember { mutableStateOf(user?.name ?: "") }
        var newUsername by remember { mutableStateOf(user?.username ?: "") }
        var isSavingProfile by remember { mutableStateOf(false) }
        var profileError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { if (!isSavingProfile) showEditProfileDialog = false },
            containerColor = AlaktraCard,
            title = { Text("Edit Profile", color = AlaktraTextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Update your display name and unique username:", style = MaterialTheme.typography.bodySmall, color = AlaktraTextSecondary)
                    Spacer(modifier = Modifier.height(14.dp))
                    OutlinedTextField(
                        value = newName,
                        onValueChange = {
                            newName = it
                            profileError = null
                        },
                        label = { Text("Full Name") },
                        singleLine = true,
                        enabled = !isSavingProfile,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = AlaktraTextPrimary,
                            unfocusedTextColor = AlaktraTextPrimary,
                            focusedBorderColor = AlaktraMint,
                            unfocusedBorderColor = AlaktraBorder,
                            focusedLabelColor = AlaktraMint,
                            unfocusedLabelColor = AlaktraTextSecondary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_profile_name_input")
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = newUsername,
                        onValueChange = {
                            newUsername = it.filter { ch -> !ch.isWhitespace() }
                            profileError = null
                        },
                        label = { Text("Username") },
                        prefix = { Text("@", color = AlaktraMint) },
                        singleLine = true,
                        enabled = !isSavingProfile,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = AlaktraTextPrimary,
                            unfocusedTextColor = AlaktraTextPrimary,
                            focusedBorderColor = AlaktraMint,
                            unfocusedBorderColor = AlaktraBorder,
                            focusedLabelColor = AlaktraMint,
                            unfocusedLabelColor = AlaktraTextSecondary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_profile_username_input")
                    )
                    if (profileError != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = profileError!!,
                            color = Color(0xFFEF4444),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val validUsername = newUsername.trim()
                        val validName = newName.trim().ifBlank { validUsername }
                        when {
                            validUsername.length < 2 || validUsername.length > 32 -> {
                                profileError = "Username must be between 2 and 32 characters"
                            }
                            else -> {
                                scope.launch {
                                    isSavingProfile = true
                                    profileError = null
                                    val result = repository.updateUserProfile(validName, validUsername)
                                    isSavingProfile = false
                                    result.onSuccess {
                                        showEditProfileDialog = false
                                        Toast.makeText(context, "Profile updated", Toast.LENGTH_SHORT).show()
                                    }.onFailure { err ->
                                        profileError = err.message ?: "Failed to update profile"
                                    }
                                }
                            }
                        }
                    },
                    enabled = !isSavingProfile,
                    colors = ButtonDefaults.buttonColors(containerColor = AlaktraMint, contentColor = Color(0xFF041C12)),
                    modifier = Modifier.testTag("edit_profile_save_button")
                ) {
                    if (isSavingProfile) {
                        CircularProgressIndicator(
                            color = Color(0xFF041C12),
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Save", fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showEditProfileDialog = false },
                    enabled = !isSavingProfile
                ) {
                    Text("Cancel", color = AlaktraTextSecondary)
                }
            }
        )
    }

    // 2. Avatar Picker Dialog (Device Photo Upload + Music Persona Icons)
    if (showAvatarPickerDialog) {
        val hasCustomPhoto = isRemoteOrFileAvatar(user?.avatarUrl)
        AlertDialog(
            onDismissRequest = { if (!isUpdatingAvatar) showAvatarPickerDialog = false },
            containerColor = AlaktraCard,
            title = { Text("Profile Picture", color = AlaktraTextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Button(
                        onClick = {
                            avatarDialogError = null
                            avatarPhotoPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        enabled = !isUpdatingAvatar,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AlaktraMint,
                            contentColor = Color(0xFF041C12)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("upload_avatar_photo_button")
                    ) {
                        if (isUpdatingAvatar) {
                            CircularProgressIndicator(
                                color = Color(0xFF041C12),
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Updating...", fontWeight = FontWeight.Bold)
                        } else {
                            Icon(
                                imageVector = Icons.Default.PhotoLibrary,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Upload Photo from Device", fontWeight = FontWeight.Bold)
                        }
                    }

                    if (hasCustomPhoto) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    isUpdatingAvatar = true
                                    avatarDialogError = null
                                    val result = repository.removeUserAvatar()
                                    isUpdatingAvatar = false
                                    result.onSuccess {
                                        showAvatarPickerDialog = false
                                        Toast.makeText(context, "Profile picture removed", Toast.LENGTH_SHORT).show()
                                    }.onFailure { err ->
                                        avatarDialogError = err.message ?: "Could not remove photo"
                                    }
                                }
                            },
                            enabled = !isUpdatingAvatar,
                            border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("remove_avatar_photo_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Remove Uploaded Photo", fontWeight = FontWeight.SemiBold)
                        }
                    }

                    if (avatarDialogError != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = avatarDialogError!!,
                            color = Color(0xFFEF4444),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Or choose a music persona icon:",
                        style = MaterialTheme.typography.bodySmall,
                        color = AlaktraTextSecondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.height(200.dp)
                    ) {
                        items(AVATAR_PRESETS) { preset ->
                            val isSelected = preset.id == user?.avatarUrl
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSelected) AlaktraSurface else Color.Transparent)
                                    .border(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) AlaktraMint else AlaktraBorder,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .clickable(enabled = !isUpdatingAvatar) {
                                        scope.launch {
                                            isUpdatingAvatar = true
                                            avatarDialogError = null
                                            val result = repository.selectAvatarPreset(preset.id)
                                            isUpdatingAvatar = false
                                            result.onSuccess {
                                                showAvatarPickerDialog = false
                                                Toast.makeText(context, "Avatar updated", Toast.LENGTH_SHORT).show()
                                            }.onFailure { err ->
                                                avatarDialogError = err.message ?: "Failed to update avatar"
                                            }
                                        }
                                    }
                                    .padding(8.dp)
                                    .testTag("avatar_preset_${preset.id}")
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(Brush.linearGradient(preset.colors))
                                ) {
                                    Icon(
                                        imageVector = preset.icon,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = preset.name,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = if (isSelected) AlaktraMint else AlaktraTextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(
                    onClick = { showAvatarPickerDialog = false },
                    enabled = !isUpdatingAvatar
                ) {
                    Text("Close", color = AlaktraTextSecondary)
                }
            }
        )
    }

    // 3. Change Password Dialog
    if (showChangePasswordDialog) {
        var currentPass by remember { mutableStateOf("") }
        var newPass by remember { mutableStateOf("") }
        var confirmPass by remember { mutableStateOf("") }
        var currentPassVisible by remember { mutableStateOf(false) }
        var newPassVisible by remember { mutableStateOf(false) }
        var confirmPassVisible by remember { mutableStateOf(false) }
        var isUpdatingPassword by remember { mutableStateOf(false) }
        var errorMessage by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { if (!isUpdatingPassword) showChangePasswordDialog = false },
            containerColor = AlaktraCard,
            title = { Text("Change Password", color = AlaktraTextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        text = "Enter your current password and choose a new one:",
                        style = MaterialTheme.typography.bodySmall,
                        color = AlaktraTextSecondary
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    // Current Password
                    OutlinedTextField(
                        value = currentPass,
                        onValueChange = {
                            currentPass = it
                            errorMessage = null
                        },
                        label = { Text("Current Password") },
                        singleLine = true,
                        enabled = !isUpdatingPassword,
                        visualTransformation = if (currentPassVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { currentPassVisible = !currentPassVisible }) {
                                Icon(
                                    imageVector = if (currentPassVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (currentPassVisible) "Hide password" else "Show password",
                                    tint = AlaktraTextMuted
                                )
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = AlaktraTextPrimary,
                            unfocusedTextColor = AlaktraTextPrimary,
                            focusedBorderColor = AlaktraMint,
                            unfocusedBorderColor = AlaktraBorder
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("change_password_current_input")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // New Password
                    OutlinedTextField(
                        value = newPass,
                        onValueChange = {
                            newPass = it
                            errorMessage = null
                        },
                        label = { Text("New Password (min 6 characters)") },
                        singleLine = true,
                        enabled = !isUpdatingPassword,
                        visualTransformation = if (newPassVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { newPassVisible = !newPassVisible }) {
                                Icon(
                                    imageVector = if (newPassVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (newPassVisible) "Hide password" else "Show password",
                                    tint = AlaktraTextMuted
                                )
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = AlaktraTextPrimary,
                            unfocusedTextColor = AlaktraTextPrimary,
                            focusedBorderColor = AlaktraMint,
                            unfocusedBorderColor = AlaktraBorder
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("change_password_new_input")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Confirm New Password
                    OutlinedTextField(
                        value = confirmPass,
                        onValueChange = {
                            confirmPass = it
                            errorMessage = null
                        },
                        label = { Text("Confirm New Password") },
                        singleLine = true,
                        enabled = !isUpdatingPassword,
                        visualTransformation = if (confirmPassVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { confirmPassVisible = !confirmPassVisible }) {
                                Icon(
                                    imageVector = if (confirmPassVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (confirmPassVisible) "Hide password" else "Show password",
                                    tint = AlaktraTextMuted
                                )
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = AlaktraTextPrimary,
                            unfocusedTextColor = AlaktraTextPrimary,
                            focusedBorderColor = AlaktraMint,
                            unfocusedBorderColor = AlaktraBorder
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("change_password_confirm_input")
                    )

                    if (errorMessage != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = errorMessage!!,
                            color = Color(0xFFEF4444),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        when {
                            currentPass.isBlank() -> errorMessage = "Please enter your current password"
                            newPass.length < 6 -> errorMessage = "New password must be at least 6 characters"
                            newPass != confirmPass -> errorMessage = "New passwords do not match"
                            newPass == currentPass -> errorMessage = "New password must be different from your current password"
                            else -> {
                                scope.launch {
                                    isUpdatingPassword = true
                                    errorMessage = null
                                    val result = repository.changePassword(currentPass, newPass, confirmPass)
                                    isUpdatingPassword = false
                                    result.onSuccess { msg ->
                                        showChangePasswordDialog = false
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    }.onFailure { err ->
                                        errorMessage = err.message ?: "Could not update password"
                                    }
                                }
                            }
                        }
                    },
                    enabled = !isUpdatingPassword,
                    colors = ButtonDefaults.buttonColors(containerColor = AlaktraMint, contentColor = Color(0xFF041C12)),
                    modifier = Modifier.testTag("change_password_submit_button")
                ) {
                    if (isUpdatingPassword) {
                        CircularProgressIndicator(
                            color = Color(0xFF041C12),
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Update", fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showChangePasswordDialog = false },
                    enabled = !isUpdatingPassword
                ) {
                    Text("Cancel", color = AlaktraTextSecondary)
                }
            }
        )
    }

    // 4. Logout Confirmation Dialog
    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            containerColor = AlaktraCard,
            title = { Text("Sign Out of Alaktra?", color = AlaktraTextPrimary, fontWeight = FontWeight.Bold) },
            text = { Text("You will need to sign in again to access your music streams.", color = AlaktraTextSecondary) },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutConfirm = false
                        scope.launch {
                            repository.clearUserData()
                            audioController.resetForLogout()
                            onLogout()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444), contentColor = Color.White)
                ) {
                    Text("Sign Out", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirm = false }) {
                    Text("Cancel", color = AlaktraTextSecondary)
                }
            }
        )
    }

    // 5. Delete Account Confirmation Dialog
    if (showDeleteAccountConfirm) {
        var confirmPassword by remember { mutableStateOf("") }
        var confirmPasswordVisible by remember { mutableStateOf(false) }
        var isDeletingAccount by remember { mutableStateOf(false) }
        var deleteError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { if (!isDeletingAccount) showDeleteAccountConfirm = false },
            containerColor = AlaktraCard,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFEF4444))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Delete Account?", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column {
                    Text(
                        text = "This action is permanent and cannot be undone.",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = AlaktraTextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Your account, playlists, listening history, profile picture, and synced data will be permanently deleted from the server and this device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = AlaktraTextSecondary
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = {
                            confirmPassword = it
                            deleteError = null
                        },
                        label = { Text("Confirm Password") },
                        singleLine = true,
                        enabled = !isDeletingAccount,
                        visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                                Icon(
                                    imageVector = if (confirmPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (confirmPasswordVisible) "Hide password" else "Show password",
                                    tint = AlaktraTextMuted
                                )
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = AlaktraTextPrimary,
                            unfocusedTextColor = AlaktraTextPrimary,
                            focusedBorderColor = Color(0xFFEF4444),
                            unfocusedBorderColor = AlaktraBorder
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("delete_account_password_input")
                    )
                    if (deleteError != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = deleteError!!,
                            color = Color(0xFFEF4444),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (confirmPassword.isBlank()) {
                            deleteError = "Please enter your password to confirm"
                            return@Button
                        }
                        scope.launch {
                            isDeletingAccount = true
                            deleteError = null
                            val result = repository.deleteAccount(confirmPassword)
                            isDeletingAccount = false
                            result.onSuccess { msg ->
                                showDeleteAccountConfirm = false
                                runCatching { AudioCache.clearCache(context) }
                                runCatching { AudioCache.clearArtworkAndHttpCache(context) }
                                audioController.resetForLogout()
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                onLogout()
                            }.onFailure { err ->
                                deleteError = err.message ?: "Failed to delete account"
                            }
                        }
                    },
                    enabled = !isDeletingAccount,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626), contentColor = Color.White),
                    modifier = Modifier.testTag("confirm_delete_account_button")
                ) {
                    if (isDeletingAccount) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Delete Everything", fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteAccountConfirm = false },
                    enabled = !isDeletingAccount
                ) {
                    Text("Cancel", color = AlaktraTextSecondary)
                }
            }
        )
    }

    // 6. Server Config Dialog
    if (showServerConfigDialog) {
        var tempUrl by remember { mutableStateOf(serverUrl) }
        var pingResult by remember { mutableStateOf<String?>(null) }
        var isPinging by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showServerConfigDialog = false },
            containerColor = AlaktraCard,
            title = { Text("Server URL / Domain", color = AlaktraTextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        text = "Enter the server domain or URL (e.g. https://api.alaktracare.in):",
                        style = MaterialTheme.typography.bodySmall,
                        color = AlaktraTextSecondary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = tempUrl,
                        onValueChange = {
                            tempUrl = it
                            pingResult = null
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = AlaktraTextPrimary,
                            unfocusedTextColor = AlaktraTextPrimary,
                            focusedBorderColor = AlaktraMint,
                            unfocusedBorderColor = AlaktraBorder
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                tempUrl = com.example.data.preferences.AuthPreferences.DEFAULT_SERVER_URL
                                pingResult = null
                            }
                        ) {
                            Text("Reset Default", color = AlaktraTextSecondary, fontSize = 12.sp)
                        }

                        Button(
                            onClick = {
                                scope.launch {
                                    isPinging = true
                                    val ok = repository.testUrl(tempUrl)
                                    isPinging = false
                                    pingResult = if (ok) "Success! Server is reachable." else "Failed. Cannot reach $tempUrl"
                                }
                            },
                            enabled = !isPinging,
                            colors = ButtonDefaults.buttonColors(containerColor = AlaktraSurface),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            if (isPinging) {
                                CircularProgressIndicator(color = AlaktraMint, modifier = Modifier.size(14.dp))
                            } else {
                                Text("Test Connection", color = AlaktraMint, fontSize = 12.sp)
                            }
                        }
                    }

                    if (pingResult != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = pingResult!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (pingResult!!.startsWith("Success")) Color(0xFF10B981) else Color(0xFFEF4444)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = tempUrl.trim()
                        if (trimmed.isNotBlank()) {
                            repository.updateServerUrl(trimmed)
                            serverUrl = repository.authPreferences.getServerBaseUrl()
                            showServerConfigDialog = false
                            scope.launch {
                                isCheckingConnection = true
                                isConnected = repository.testConnection()
                                isCheckingConnection = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AlaktraMint, contentColor = Color(0xFF041C12))
                ) {
                    Text("Save", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showServerConfigDialog = false }) {
                    Text("Cancel", color = AlaktraTextSecondary)
                }
            }
        )
    }

    // 6. Song Cache Memory Cap Settings Dialog
    if (showCacheCapDialog) {
        CacheCapSettingsDialog(
            currentCapMb = cacheCapMb,
            currentUsageBytes = audioCacheBytes,
            onDismissRequest = { showCacheCapDialog = false },
            onApplyCap = { newCapMb ->
                val applied = AudioCache.setCacheCapMb(context, newCapMb)
                cacheCapMb = applied
                audioCacheBytes = AudioCache.getCacheSizeBytes(context)
                showCacheCapDialog = false
                Toast.makeText(
                    context,
                    "Song cache limit set to ${AudioCache.formatCapMb(applied)}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        )
    }
    }
}
