package com.example.mediaplayer

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Build
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.mediaplayer.ui.theme.MediaPlayerTheme

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

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun MediaPlayerScreen() {
    val context = LocalContext.current
    var selectedTab by rememberSaveable { mutableStateOf(BottomNavItem.Video) }
    var menuExpanded by remember { mutableStateOf(false) }
    var hasPermission by remember { mutableStateOf(hasMediaPermission(context, selectedTab)) }
    var folders by remember { mutableStateOf<List<String>>(emptyList()) }
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
    }

    LaunchedEffect(selectedTab, hasPermission, refreshCounter) {
        folders = if (hasPermission) {
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

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(text = "Media Player") },
                    actions = {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More options"
                            )
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
                    colors = TopAppBarDefaults.topAppBarColors()
                )
                if (isRefreshing) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        bottomBar = {
            NavigationBar(
                modifier = Modifier.height(64.dp)
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
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            if (!hasPermission) {
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
                        headlineContent = { Text(folder) },
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                modifier = Modifier.size(50.dp)
                            )
                        }
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

private fun loadMediaFolders(context: Context, tab: BottomNavItem): List<String> {
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

    val projection = arrayOf(folderColumn)
    val result = linkedSetOf<String>()

    context.contentResolver.query(
        collection,
        projection,
        null,
        null,
        null
    )?.use { cursor ->
        val columnIndex = cursor.getColumnIndexOrThrow(folderColumn)
        while (cursor.moveToNext()) {
            val rawFolder = cursor.getString(columnIndex) ?: continue
            val normalized = normalizeFolderName(rawFolder)
            if (normalized.isNotBlank()) {
                result.add(normalized)
            }
        }
    }
    return result.toList()
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
