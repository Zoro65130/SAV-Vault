package com.zoro.savvault

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import java.io.File
import java.text.DecimalFormat

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SAVVaultTheme { FileManagerApp() } }
    }
}

private val DarkBackground = Color(0xFF0A0E17)
private val SurfaceDark = Color(0xFF151B24)
private val AccentBlue = Color(0xFF64B5F6)
private val AccentPurple = Color(0xFFBA68C8)
private val TextPrimary = Color(0xFFE2E8F0)
private val TextSecondary = Color(0xFF94A3B8)

@Composable
private fun SAVVaultTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = AccentBlue,
            secondary = AccentPurple,
            background = DarkBackground,
            surface = SurfaceDark,
            onPrimary = Color.Black,
            onSurface = TextPrimary
        ),
        content = content
    )
}

private data class FileItem(val file: File, val isDir: Boolean)

@Composable
private fun FileManagerApp() {
    val context = LocalContext.current
    val rootPath = remember { Environment.getExternalStorageDirectory().absolutePath }
    var currentPath by remember { mutableStateOf(rootPath) }
    var files by remember { mutableStateOf<List<FileItem>>(emptyList()) }
    var selectedPaths by remember { mutableStateOf<Set<String>>(emptySet()) }
    var searchQuery by remember { mutableStateOf("") }
    var hasPermission by remember { mutableStateOf(
        Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()
    ) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showRenameFor by remember { mutableStateOf<FileItem?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var clipboardPaths by remember { mutableStateOf<List<String>>(emptyList()) }
    var clipboardIsMove by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        hasPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.R ||
            Environment.isExternalStorageManager()
    }

    fun loadFiles(path: String) {
        val dir = File(path)
        val list = dir.listFiles()
        files = list?.map { FileItem(it, it.isDirectory) }
            ?.sortedWith(compareBy<FileItem>({ !it.isDir }, { it.file.name.lowercase() }))
            ?: emptyList()
        currentPath = path
        selectedPaths = emptySet()
    }

    fun requestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                permissionLauncher.launch(intent)
            } catch (_: Exception) {
                permissionLauncher.launch(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!hasPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) requestPermission()
    }

    LaunchedEffect(hasPermission, currentPath) {
        if (hasPermission) loadFiles(currentPath)
    }

    BackHandler(enabled = currentPath != rootPath) {
        File(currentPath).parent?.let { loadFiles(it) }
    }

    fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = AccentBlue)
                    Spacer(Modifier.width(8.dp))
                    Text("SAV Vault", fontSize = 23.sp, fontWeight = FontWeight.Bold, color = AccentBlue)
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Search, contentDescription = "Clear search", tint = TextPrimary)
                    }
                }
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search files and folders…", color = TextSecondary) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = SurfaceDark,
                        focusedContainerColor = SurfaceDark,
                        unfocusedBorderColor = Color.Transparent,
                        focusedBorderColor = AccentBlue
                    )
                )
                Text(
                    currentPath.removePrefix(rootPath).ifBlank { "Internal Storage" },
                    color = TextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        },
        floatingActionButton = {
            if (hasPermission) {
                FloatingActionButton(
                    onClick = { showCreateDialog = true },
                    containerColor = AccentBlue,
                    contentColor = Color.Black,
                    shape = RoundedCornerShape(16.dp)
                ) { Icon(Icons.Default.Add, contentDescription = "Create folder") }
            }
        },
        bottomBar = {
            NavigationBar(containerColor = SurfaceDark, tonalElevation = 0.dp) {
                NavigationBarItem(
                    selected = true,
                    onClick = { loadFiles(rootPath) },
                    icon = { Icon(Icons.Default.Home, contentDescription = null) },
                    label = { Text("Home") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { loadFiles(rootPath) },
                    icon = { Icon(Icons.Default.Folder, contentDescription = null) },
                    label = { Text("Files") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { toast("Recent files are not implemented yet.") },
                    icon = { Icon(Icons.Default.History, contentDescription = null) },
                    label = { Text("Recent") }
                )
            }
        }
    ) { padding ->
        if (!hasPermission) {
            Column(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Storage access is required to browse files.", color = TextPrimary)
                Spacer(Modifier.height(12.dp))
                Button(onClick = { requestPermission() }) { Text("Grant access") }
            }
        } else {
            Column(Modifier.padding(padding).fillMaxSize()) {
                StorageCard()
                if (selectedPaths.isNotEmpty()) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("${selectedPaths.size} selected", color = TextPrimary, modifier = Modifier.weight(1f).align(Alignment.CenterVertically))
                        IconButton(onClick = {
                            clipboardPaths = selectedPaths.toList()
                            clipboardIsMove = false
                            toast("Selected items copied. Open a destination folder and tap Paste.")
                        }) { Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = AccentBlue) }
                        IconButton(onClick = {
                            clipboardPaths = selectedPaths.toList()
                            clipboardIsMove = true
                            toast("Selected items ready to move. Open a destination folder and tap Paste.")
                        }) { Icon(Icons.Default.DriveFileMove, contentDescription = "Move", tint = AccentBlue) }
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFFF6B6B))
                        }
                        IconButton(onClick = { selectedPaths.firstOrNull()?.let { path ->
                            files.firstOrNull { it.file.absolutePath == path }?.let { showRenameFor = it }
                        } }) { Icon(Icons.Default.Edit, contentDescription = "Rename", tint = TextPrimary) }
                        IconButton(onClick = {
                            val first = selectedPaths.firstOrNull()?.let(::File)
                            if (first != null && first.exists()) shareFile(context, first)
                        }) { Icon(Icons.Default.Share, contentDescription = "Share", tint = TextPrimary) }
                    }
                    if (clipboardPaths.isNotEmpty()) {
                        OutlinedButton(
                            onClick = {
                                var success = 0
                                clipboardPaths.forEach { sourcePath ->
                                    val source = File(sourcePath)
                                    val destination = File(currentPath, source.name)
                                    if (source.exists() && source.absolutePath != destination.absolutePath && !destination.exists()) {
                                        try {
                                            if (clipboardIsMove) {
                                                if (source.renameTo(destination)) success++
                                                else toast("Could not move ${source.name}; cross-storage moves may require copying.")
                                            } else {
                                                if (copyRecursivelySafe(source, destination)) success++
                                            }
                                        } catch (_: Exception) { }
                                    }
                                }
                                clipboardPaths = emptyList()
                                loadFiles(currentPath)
                                toast("$success item(s) processed")
                            },
                            modifier = Modifier.padding(horizontal = 12.dp)
                        ) { Text("Paste here") }
                    }
                }
                if (currentPath != rootPath) {
                    TextButton(
                        onClick = { File(currentPath).parent?.let { loadFiles(it) } },
                        modifier = Modifier.padding(start = 8.dp)
                    ) { Text("↑ Parent folder", color = AccentBlue) }
                }
                val filtered = files.filter { it.file.name.contains(searchQuery, ignoreCase = true) }
                if (filtered.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No files found", color = TextSecondary)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                        contentPadding = PaddingValues(bottom = 88.dp)
                    ) {
                        items(filtered, key = { it.file.absolutePath }) { item ->
                            val selected = selectedPaths.contains(item.file.absolutePath)
                            FileRow(
                                item = item,
                                isSelected = selected,
                                onClick = {
                                    if (selectedPaths.isNotEmpty()) {
                                        selectedPaths = if (selected) selectedPaths - item.file.absolutePath
                                            else selectedPaths + item.file.absolutePath
                                    } else if (item.isDir) {
                                        loadFiles(item.file.absolutePath)
                                    } else {
                                        openFile(context, item.file)
                                    }
                                },
                                onLongClick = {
                                    selectedPaths = if (selected) selectedPaths - item.file.absolutePath
                                        else selectedPaths + item.file.absolutePath
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Create folder") },
            text = {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Folder name") }, singleLine = true)
            },
            confirmButton = {
                TextButton(onClick = {
                    val valid = name.isNotBlank() && name != "." && name != ".." && !name.contains("/")
                    val target = File(currentPath, name)
                    if (valid && target.mkdir()) {
                        loadFiles(currentPath)
                    } else {
                        toast("Could not create folder. Check the name and permissions.")
                    }
                    showCreateDialog = false
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showCreateDialog = false }) { Text("Cancel") } },
            containerColor = SurfaceDark
        )
    }

    showRenameFor?.let { item ->
        var newName by remember(item) { mutableStateOf(item.file.name) }
        AlertDialog(
            onDismissRequest = { showRenameFor = null },
            title = { Text("Rename") },
            text = { OutlinedTextField(value = newName, onValueChange = { newName = it }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    val valid = newName.isNotBlank() && newName != "." && newName != ".." && !newName.contains("/")
                    val target = File(item.file.parentFile, newName)
                    if (valid && !target.exists() && item.file.renameTo(target)) loadFiles(currentPath)
                    else toast("Could not rename this item.")
                    showRenameFor = null
                }) { Text("Rename") }
            },
            dismissButton = { TextButton(onClick = { showRenameFor = null }) { Text("Cancel") } },
            containerColor = SurfaceDark
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete selected items?") },
            text = { Text("This action cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    var failed = 0
                    selectedPaths.forEach { path ->
                        if (!File(path).deleteRecursively()) failed++
                    }
                    selectedPaths = emptySet()
                    loadFiles(currentPath)
                    toast(if (failed == 0) "Deleted selected items" else "Some items could not be deleted")
                    showDeleteConfirm = false
                }) { Text("Delete", color = Color(0xFFFF6B6B)) }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } },
            containerColor = SurfaceDark
        )
    }
}

@Composable
private fun StorageCard() {
    val stat = remember { runCatching { android.os.StatFs(Environment.getExternalStorageDirectory().path) }.getOrNull() }
    val totalBytes = (stat?.blockCountLong ?: 0L) * (stat?.blockSizeLong ?: 0L)
    val availableBytes = (stat?.availableBlocksLong ?: 0L) * (stat?.blockSizeLong ?: 0L)
    val usedBytes = (totalBytes - availableBytes).coerceAtLeast(0L)
    val usedPercentage = if (totalBytes > 0L) (usedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f
    val df = DecimalFormat("#.##")
    val usedStr = df.format(usedBytes / (1024.0 * 1024.0 * 1024.0))
    val totalStr = df.format(totalBytes / (1024.0 * 1024.0 * 1024.0))

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { usedPercentage },
                    modifier = Modifier.size(60.dp),
                    color = AccentBlue,
                    trackColor = DarkBackground,
                    strokeWidth = 6.dp
                )
                Text("${(usedPercentage * 100).toInt()}%", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text("Internal Storage", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("$usedStr GB / $totalStr GB used", color = TextSecondary, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun FileRow(
    item: FileItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val bgColor = if (isSelected) AccentBlue.copy(alpha = 0.16f) else Color.Transparent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val icon = when {
            item.isDir -> Icons.Default.Folder
            item.file.extension.lowercase() in listOf("jpg", "jpeg", "png", "gif", "webp") -> Icons.Default.Image
            item.file.extension.lowercase() == "apk" -> Icons.Default.Android
            item.file.extension.lowercase() in listOf("mp4", "mkv", "avi", "webm") -> Icons.Default.PlayArrow
            item.file.extension.lowercase() in listOf("mp3", "wav", "m4a", "ogg") -> Icons.Default.MusicNote
            else -> Icons.Default.InsertDriveFile
        }
        Icon(icon, contentDescription = null, tint = if (item.isDir) AccentBlue else AccentPurple, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(item.file.name, color = TextPrimary, fontSize = 15.sp, maxLines = 1)
            Text(
                if (item.isDir) "Folder" else "${item.file.length() / 1024} KB",
                color = TextSecondary,
                fontSize = 12.sp
            )
        }
        if (isSelected) Icon(Icons.Default.CheckCircle, contentDescription = "Selected", tint = AccentBlue)
        else Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextSecondary)
    }
}

private fun openFile(context: Context, file: File) {
    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val mimeType = context.contentResolver.getType(uri) ?: mimeFromExtension(file.extension)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        Toast.makeText(context, "No compatible app found or file cannot be opened.", Toast.LENGTH_SHORT).show()
    }
}

private fun shareFile(context: Context, file: File) {
    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeFromExtension(file.extension)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share file"))
    } catch (_: Exception) {
        Toast.makeText(context, "Could not share this file.", Toast.LENGTH_SHORT).show()
    }
}

private fun mimeFromExtension(extension: String): String = when (extension.lowercase()) {
    "jpg", "jpeg" -> "image/jpeg"
    "png" -> "image/png"
    "gif" -> "image/gif"
    "webp" -> "image/webp"
    "mp4" -> "video/mp4"
    "mp3" -> "audio/mpeg"
    "pdf" -> "application/pdf"
    "apk" -> "application/vnd.android.package-archive"
    "txt", "log" -> "text/plain"
    else -> "*/*"
}

private fun copyRecursivelySafe(source: File, destination: File): Boolean {
    if (source.absolutePath == destination.absolutePath) return false
    return try {
        if (source.isDirectory) {
            if (!destination.mkdir()) return false
            source.listFiles()?.all { child ->
                copyRecursivelySafe(child, File(destination, child.name))
            } ?: true
        } else {
            source.copyTo(destination, overwrite = false)
            true
        }
    } catch (_: Exception) {
        destination.deleteRecursively()
        false
    }
}
