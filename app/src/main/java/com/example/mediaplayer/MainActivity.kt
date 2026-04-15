package com.example.mediaplayer

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.util.Size
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.mediaplayer.ui.theme.MediaPlayerTheme
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MediaPlayerTheme {
                MediaPlayerScreen()
            }
        }
    }
}

private enum class BottomNavItem(val title: String) {
    Video("Video"),
    Audio("Audio")
}

private data class VideoItem(
    val title: String,
    val uri: Uri,
    val durationMs: Long,
    val sizeBytes: Long,
    val formatExt: String
)

private data class FolderStats(
    val folderName: String,
    val itemCount: Int,
    val totalBytes: Long,
    val isSdFolder: Boolean
)

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun MediaPlayerScreen() {
    val context = LocalContext.current
    var selectedTab by rememberSaveable { mutableStateOf(BottomNavItem.Video) }
    var menuExpanded by remember { mutableStateOf(false) }
    var hasPermission by remember { mutableStateOf(hasMediaPermission(context, selectedTab)) }
    var folders by remember { mutableStateOf<List<FolderStats>>(emptyList()) }
    var selectedFolder by remember { mutableStateOf<String?>(null) }
    var folderVideos by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var refreshCounter by remember { mutableStateOf(0) }
    var isRefreshing by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted || hasMediaPermission(context, selectedTab)
        if (hasPermission) {
            refreshCounter++
        }
    }

    LaunchedEffect(selectedTab) {
        hasPermission = hasMediaPermission(context, selectedTab)
        selectedFolder = null
        folderVideos = emptyList()
    }

    LaunchedEffect(selectedTab, hasPermission, refreshCounter) {
        folders = if (hasPermission && selectedFolder == null) {
            isRefreshing = true
            try {
                loadMediaFolders(context, selectedTab)
            } finally {
                isRefreshing = false
            }
        } else {
            isRefreshing = false
            emptyList()
        }
    }

    LaunchedEffect(selectedFolder, hasPermission, refreshCounter, selectedTab) {
        val activeFolder = selectedFolder
        if (activeFolder != null && hasPermission && selectedTab == BottomNavItem.Video) {
            isRefreshing = true
            try {
                folderVideos = loadVideosInFolder(context, activeFolder)
            } finally {
                isRefreshing = false
            }
        } else if (activeFolder != null) {
            folderVideos = emptyList()
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(text = selectedFolder ?: "Media Player")
                    },
                    navigationIcon = {
                        if (selectedFolder != null) {
                            IconButton(
                                onClick = {
                                    selectedFolder = null
                                    folderVideos = emptyList()
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowBack,
                                    contentDescription = "Back"
                                )
                            }
                        }
                    },
                    actions = {
                        if (selectedFolder == null) {
                            IconButton(onClick = { menuExpanded = true }) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "More options"
                                )
                            }
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Refresh") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = null
                                    )
                                },
                                onClick = {
                                    refreshCounter++
                                    menuExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Storage") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Storage,
                                        contentDescription = null
                                    )
                                },
                                onClick = { menuExpanded = false }
                            )
                            DropdownMenuItem(
                                text = { Text("Settings") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = null
                                    )
                                },
                                onClick = { menuExpanded = false }
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
                if (isRefreshing) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        bottomBar = {
            if (selectedFolder == null) {
                NavigationBar(
                    modifier = Modifier.height(64.dp),
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    NavigationBarItem(
                        selected = selectedTab == BottomNavItem.Video,
                        onClick = { selectedTab = BottomNavItem.Video },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.VideoLibrary,
                                contentDescription = "Video"
                            )
                        },
                        label = { Text("Video") },
                        alwaysShowLabel = false
                    )
                    NavigationBarItem(
                        selected = selectedTab == BottomNavItem.Audio,
                        onClick = { selectedTab = BottomNavItem.Audio },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Audiotrack,
                                contentDescription = "Audio"
                            )
                        },
                        label = { Text("Audio") },
                        alwaysShowLabel = false
                    )
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (selectedFolder != null) {
                if (selectedTab != BottomNavItem.Video) {
                    item {
                        Text(
                            text = "Video list is only available for the Video tab."
                        )
                    }
                } else if (folderVideos.isEmpty()) {
                    item {
                        Text(text = "No videos found in this folder.")
                    }
                } else {
                    items(folderVideos) { video ->
                        ListItem(
                            modifier = Modifier.clickable { },
                            headlineContent = { Text(video.title) },
                            supportingContent = {
                                Text(text = formatVideoMetaLine(video))
                            },
                            leadingContent = {
                                VideoThumbnail(
                                    uri = video.uri,
                                    contentDescription = video.title
                                )
                            },
                            colors = ListItemDefaults.colors(
                                containerColor = Color.Transparent
                            )
                        )
                    }
                }
            } else if (!hasPermission) {
                item {
                    Text(
                        text = "Permission required to load ${selectedTab.title.lowercase()} files."
                    )
                }
                item {
                    Button(
                        onClick = {
                            permissionLauncher.launch(requiredPermissionFor(selectedTab))
                        }
                    ) {
                        Text("Grant Permission")
                    }
                }
            } else if (folders.isEmpty()) {
                item {
                    Text(text = "No ${selectedTab.title.lowercase()} folders found.")
                }
            } else {
                items(folders) { folder ->
                    ListItem(
                        modifier = Modifier.clickable {
                            selectedFolder = folder.folderName
                        },
                        headlineContent = { Text(folder.folderName) },
                        supportingContent = {
                            Text(
                                text = buildFolderSummary(
                                    tab = selectedTab,
                                    stats = folder
                                )
                            )
                        },
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                modifier = Modifier.size(50.dp)
                            )
                        },
                        colors = ListItemDefaults.colors(
                            containerColor = Color.Transparent
                        )
                    )
                }
            }
        }
    }
}

private fun requiredPermissionFor(tab: BottomNavItem): String {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        if (tab == BottomNavItem.Video) {
            Manifest.permission.READ_MEDIA_VIDEO
        } else {
            Manifest.permission.READ_MEDIA_AUDIO
        }
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
}

private fun hasMediaPermission(context: Context, tab: BottomNavItem): Boolean {
    return ContextCompat.checkSelfPermission(
        context,
        requiredPermissionFor(tab)
    ) == PackageManager.PERMISSION_GRANTED
}

private fun loadMediaFolders(context: Context, tab: BottomNavItem): List<FolderStats> {
    val collection = if (tab == BottomNavItem.Video) {
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    } else {
        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
    }

    val folderColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.MediaColumns.RELATIVE_PATH
    } else {
        MediaStore.MediaColumns.DATA
    }
    val dataColumn = MediaStore.MediaColumns.DATA
    val sizeColumn = MediaStore.MediaColumns.SIZE
    val projection = arrayOf(folderColumn, dataColumn, sizeColumn)
    val aggregations = linkedMapOf<String, FolderAggregation>()

    context.contentResolver.query(
        collection,
        projection,
        null,
        null,
        null
    )?.use { cursor ->
        val folderIndex = cursor.getColumnIndexOrThrow(folderColumn)
        val dataIndex = cursor.getColumnIndex(dataColumn)
        val sizeIndex = cursor.getColumnIndexOrThrow(sizeColumn)
        while (cursor.moveToNext()) {
            val rawFolder = cursor.getString(folderIndex) ?: continue
            val normalized = normalizeFolderName(rawFolder)
            if (normalized.isNotBlank()) {
                val dataPath = if (dataIndex >= 0) cursor.getString(dataIndex) else null
                val size = cursor.getLong(sizeIndex).coerceAtLeast(0L)
                val entry = aggregations.getOrPut(normalized) { FolderAggregation() }
                entry.itemCount += 1
                entry.totalBytes += size

                val sdInfo = detectSdFromDataPath(dataPath)
                if (sdInfo != null) {
                    entry.knownStorageItems += 1
                    if (sdInfo) {
                        entry.sdItems += 1
                    }
                }
            }
        }
    }
    return aggregations.entries.map { (folderName, agg) ->
        FolderStats(
            folderName = folderName,
            itemCount = agg.itemCount,
            totalBytes = agg.totalBytes,
            isSdFolder = agg.itemCount > 0 &&
                agg.knownStorageItems == agg.itemCount &&
                agg.sdItems == agg.itemCount
        )
    }.sortedBy { it.folderName.lowercase(Locale.getDefault()) }
}

private fun loadVideosInFolder(context: Context, folder: String): List<VideoItem> {
    val projection = arrayOf(
        MediaStore.Video.Media._ID,
        MediaStore.Video.Media.DISPLAY_NAME,
        MediaStore.Video.Media.DURATION,
        MediaStore.Video.Media.SIZE,
        MediaStore.Video.Media.MIME_TYPE,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.MediaColumns.RELATIVE_PATH
        } else {
            MediaStore.MediaColumns.DATA
        }
    )
    val folderColumn = projection[2]
    val videos = mutableListOf<VideoItem>()

    context.contentResolver.query(
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
        projection,
        null,
        null,
        "${MediaStore.Video.Media.DATE_ADDED} DESC"
    )?.use { cursor ->
        val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
        val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
        val durationIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
        val sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
        val mimeTypeIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
        val folderIndex = cursor.getColumnIndexOrThrow(folderColumn)

        while (cursor.moveToNext()) {
            val rawFolder = cursor.getString(folderIndex) ?: continue
            if (normalizeFolderName(rawFolder) != folder) {
                continue
            }

            val id = cursor.getLong(idIndex)
            val title = cursor.getString(nameIndex) ?: "Untitled"
            val durationMs = cursor.getLong(durationIndex).coerceAtLeast(0L)
            val sizeBytes = cursor.getLong(sizeIndex).coerceAtLeast(0L)
            val mimeType = cursor.getString(mimeTypeIndex)
            val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
            videos.add(
                VideoItem(
                    title = title,
                    uri = uri,
                    durationMs = durationMs,
                    sizeBytes = sizeBytes,
                    formatExt = resolveVideoFormat(title, mimeType)
                )
            )
        }
    }

    return videos
}

@Composable
private fun VideoThumbnail(uri: Uri, contentDescription: String) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = null, uri) {
        value = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.contentResolver.loadThumbnail(uri, Size(120, 120), null)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    if (bitmap != null) {
        Box(
            modifier = Modifier
                .size(width = 118.dp, height = 73.dp)
                .clip(RoundedCornerShape(6.dp))
        ) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    } else {
        Icon(
            imageVector = Icons.Default.VideoLibrary,
            contentDescription = contentDescription,
            modifier = Modifier
                .size(width = 118.dp, height = 73.dp)
                .clip(RoundedCornerShape(6.dp))
        )
    }
}

private data class FolderAggregation(
    var itemCount: Int = 0,
    var totalBytes: Long = 0,
    var knownStorageItems: Int = 0,
    var sdItems: Int = 0
)

private fun buildFolderSummary(tab: BottomNavItem, stats: FolderStats): String {
    val label = when (tab) {
        BottomNavItem.Video -> if (stats.itemCount == 1) "Video" else "Videos"
        BottomNavItem.Audio -> if (stats.itemCount == 1) "Song" else "Songs"
    }
    val base = "${stats.itemCount} $label | ${formatBytes(stats.totalBytes)}"
    return if (stats.isSdFolder) "[$base | SD]" else "[$base]"
}

private fun formatVideoMetaLine(video: VideoItem): String {
    return "[${formatDuration(video.durationMs)} | ${formatBytes(video.sizeBytes)} | ${video.formatExt}]"
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return String.format(Locale.US, "%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024.0) return String.format(Locale.US, "%.1f MB", mb)
    val gb = mb / 1024.0
    return String.format(Locale.US, "%.1f GB", gb)
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

private fun resolveVideoFormat(fileName: String, mimeType: String?): String {
    val ext = fileName.substringAfterLast('.', "").lowercase(Locale.getDefault())
    if (ext.isNotBlank()) return ext
    if (!mimeType.isNullOrBlank() && mimeType.contains("/")) {
        return mimeType.substringAfter('/').lowercase(Locale.getDefault())
    }
    return "unknown"
}

private fun detectSdFromDataPath(dataPath: String?): Boolean? {
    if (dataPath.isNullOrBlank()) return null
    val normalized = dataPath.replace('\\', '/').lowercase(Locale.getDefault())
    if (!normalized.startsWith("/storage/")) return null
    return !normalized.startsWith("/storage/emulated/0")
}

private fun normalizeFolderName(raw: String): String {
    val cleaned = raw.replace("\\", "/").trimEnd('/')
    if (cleaned.isBlank()) {
        return ""
    }
    return if (cleaned.contains("/")) {
        cleaned.substringAfterLast("/")
    } else {
        cleaned
    }
}

@Preview(showBackground = true)
@Composable
private fun MediaPlayerScreenPreview() {
    MediaPlayerTheme {
        MediaPlayerScreen()
    }
}
