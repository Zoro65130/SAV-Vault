package com.zoro.savvault

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
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
        setContent {
            SAVVaultTheme {
                FileManagerApp()
            }
        }
    }
}

val DarkBackground = Color(0xFF0A0E17)
val SurfaceDark = Color(0xFF151B24)
val AccentBlue = Color(0xFF64B5F6)
val AccentPurple = Color(0xFFBA68C8)
val TextPrimary = Color(0xFFE2E8F0)
val TextSecondary = Color(0xFF94A3B8)

@Composable
fun SAVVaultTheme(content: @Composable () -> Unit) {
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

data class FileItem(val file: File, val isDir: Boolean)

// Text Editor State
data class EditorState(val file: File, val content: String)

@Composable
fun FileManagerApp() {
    val context = LocalContext.current
    var currentPath by remember { mutableStateOf(Environment.getExternalStorageDirectory().absolutePath) }
    var files by remember { mutableStateOf<List<FileItem>>(emptyList()) }
    var selectedFiles by remember { mutableStateOf<Set<FileItem>>(emptySet()) }
    var searchQuery by remember { mutableStateOf("") }
    
    // Dialog states
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var showCreateFileDialog by remember { mutableStateOf(false) }
    var showAddMenu by remember { mutableStateOf(false) }
    
    // Editor State
    var activeEditor by remember { mutableStateOf<EditorState?>(null) }
    
    var hasPermission by remember { mutableStateOf(false) }
    
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            hasPermission = Environment.isExternalStorageManager()
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                hasPermission = true
            } else {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:${context.packageName}")
                permissionLauncher.launch(intent)
            }
        } else {
            hasPermission = true
        }
    }

    fun loadFiles(path: String) {
        val dir = File(path)
        val fileList = dir.listFiles()?.map { FileItem(it, it.isDirectory) } ?: emptyList()
        files = fileList.sortedWith(compareBy({ !it.isDir }, { it.file.name.lowercase() }))
        currentPath = path
        selectedFiles = emptySet()
    }

    LaunchedEffect(hasPermission, currentPath) {
        if (hasPermission) loadFiles(currentPath)
    }

    val getContentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            try {
                val inputStream = context.contentResolver.openInputStream(it)
                val fileName = getFileName(context, it) ?: "imported_file"
                val destFile = File(currentPath, fileName)
                inputStream?.use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                loadFiles(currentPath)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // --- EDITOR OVERLAY ---
    if (activeEditor != null) {
        val editor = activeEditor!!
        var text by remember { mutableStateOf(editor.content) }
        
        Scaffold(
            containerColor = DarkBackground,
            topBar = {
                TopAppBar(
                    title = { Text(editor.file.name, color = TextPrimary, fontSize = 16.sp) },
                    navigationIcon = {
                        IconButton(onClick = { activeEditor = null }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            try {
                                editor.file.writeText(text)
                                activeEditor = null
                            } catch (e: Exception) { e.printStackTrace() }
                        }) {
                            Icon(Icons.Default.Save, contentDescription = "Save", tint = AccentBlue)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceDark)
                )
            }
        ) { padding ->
            TextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.padding(padding).fillMaxSize().padding(8.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = DarkBackground,
                    unfocusedContainerColor = DarkBackground,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    cursorColor = AccentBlue
                )
            )
        }
        
        BackHandler { activeEditor = null }
        return // Exit early to prevent file list rendering underneath
    }

    // --- FILE MANAGER UI ---
    BackHandler(enabled = currentPath != Environment.getExternalStorageDirectory().absolutePath) {
        val parent = File(currentPath).parent
        if (parent != null) loadFiles(parent)
    }

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            if (selectedFiles.isEmpty()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = AccentBlue)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("SAV Vault", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = AccentBlue)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search files...", color = TextSecondary) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary) },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = SurfaceDark,
                            focusedContainerColor = SurfaceDark,
                            unfocusedBorderColor = Color.Transparent,
                            focusedBorderColor = AccentBlue
                        )
                    )
                }
            } else {
                TopAppBar(
                    title = { Text("${selectedFiles.size} selected", color = TextPrimary) },
                    navigationIcon = {
                        IconButton(onClick = { selectedFiles = emptySet() }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = TextPrimary)
                        }
                    },
                    actions = {
                        IconButton(onClick = { selectedFiles.forEach { it.file.deleteRecursively() }; loadFiles(currentPath); selectedFiles = emptySet() }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceDark)
                )
            }
        },
        floatingActionButton = {
            if (selectedFiles.isEmpty() && hasPermission) {
                FloatingActionButton(
                    onClick = { showAddMenu = true },
                    containerColor = AccentBlue,
                    contentColor = Color.Black,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add")
                }
            }
        }
    ) { paddingValues ->
        if (!hasPermission) {
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                Text("Storage permission required.", color = TextPrimary)
            }
        } else {
            Column(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
                StorageCard()
                val filteredFiles = files.filter { it.file.name.contains(searchQuery, ignoreCase = true) }
                LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
                    items(filteredFiles) { item ->
                        val isSelected = selectedFiles.contains(item)
                        FileRow(item = item, isSelected = isSelected, onClick = {
                            if (selectedFiles.isNotEmpty()) {
                                selectedFiles = if (isSelected) selectedFiles - item else selectedFiles + item
                            } else {
                                if (item.isDir) {
                                    loadFiles(item.file.absolutePath)
                                } else {
                                    // Check if it's a text file
                                    val textExtensions = listOf("txt", "md", "json", "xml", "html", "css", "js", "kt", "java", "log", "csv")
                                    if (item.file.extension.lowercase() in textExtensions) {
                                        try {
                                            activeEditor = EditorState(item.file, item.file.readText())
                                        } catch (e: Exception) {
                                            // Fallback to external app if read fails
                                            openFile(context, item.file)
                                        }
                                    } else {
                                        openFile(context, item.file)
                                    }
                                }
                            }
                        }, onLongClick = {
                            selectedFiles = selectedFiles + item
                        })
                    }
                }
            }
        }
    }

    if (showAddMenu) {
        AlertDialog(
            onDismissRequest = { showAddMenu = false },
            title = { Text("Add New", color = TextPrimary) },
            text = {
                Column {
                    TextButton(onClick = { showAddMenu = false; showCreateFolderDialog = true }) {
                        Text("Create Folder", color = AccentBlue)
                    }
                    TextButton(onClick = { showAddMenu = false; showCreateFileDialog = true }) {
                        Text("Create Empty File", color = AccentBlue)
                    }
                    TextButton(onClick = { showAddMenu = false; getContentLauncher.launch("*/*") }) {
                        Text("Import File from Phone", color = AccentBlue)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAddMenu = false }) { Text("Cancel", color = TextSecondary) }
            },
            containerColor = SurfaceDark
        )
    }

    if (showCreateFolderDialog) {
        var folderName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateFolderDialog = false },
            title = { Text("New Folder", color = TextPrimary) },
            text = { TextField(value = folderName, onValueChange = { folderName = it }, placeholder = { Text("Folder name") }) },
            confirmButton = {
                TextButton(onClick = {
                    if (folderName.isNotEmpty()) {
                        File(currentPath, folderName).mkdirs()
                        loadFiles(currentPath)
                    }
                    showCreateFolderDialog = false
                }) { Text("Create", color = AccentBlue) }
            },
            containerColor = SurfaceDark
        )
    }

    if (showCreateFileDialog) {
        var fileName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateFileDialog = false },
            title = { Text("New File", color = TextPrimary) },
            text = { TextField(value = fileName, onValueChange = { fileName = it }, placeholder = { Text("File name (e.g. notes.txt)") }) },
            confirmButton = {
                TextButton(onClick = {
                    if (fileName.isNotEmpty()) {
                        try {
                            File(currentPath, fileName).createNewFile()
                            loadFiles(currentPath)
                        } catch (e: Exception) { e.printStackTrace() }
                    }
                    showCreateFileDialog = false
                }) { Text("Create", color = AccentBlue) }
            },
            containerColor = SurfaceDark
        )
    }
}

fun getFileName(context: Context, uri: Uri): String? {
    var result: String? = null
    if (uri.scheme == "content") {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        try {
            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index != -1) result = cursor.getString(index)
            }
        } finally {
            cursor?.close()
        }
    }
    if (result == null) {
        result = uri.path
        val cut = result?.lastIndexOf('/') ?: -1
        if (cut != -1) result = result?.substring(cut + 1)
    }
    return result
}

@Composable
fun StorageCard() {
    val stat = StatFs(Environment.getExternalStorageDirectory().path)
    val totalBytes = stat.blockCountLong * stat.blockSizeLong
    val availableBytes = stat.availableBlocksLong * stat.blockSizeLong
    val usedBytes = totalBytes - availableBytes
    val usedPercentage = (usedBytes.toFloat() / totalBytes.toFloat()) * 100
    val df = DecimalFormat("#.##")

    Card(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = usedPercentage / 100f,
                    modifier = Modifier.size(60.dp),
                    color = AccentBlue,
                    trackColor = DarkBackground,
                    strokeWidth = 6.dp
                )
                Text("${usedPercentage.toInt()}%", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text("Internal Storage", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("${df.format(usedBytes / (1024f * 1024f * 1024f))} GB / ${df.format(totalBytes / (1024f * 1024f * 1024f))} GB used", color = TextSecondary, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun FileRow(item: FileItem, isSelected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val bgColor = if (isSelected) AccentBlue.copy(alpha = 0.2f) else Color.Transparent
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
            item.file.extension.lowercase() in listOf("jpg", "jpeg", "png", "gif") -> Icons.Default.Image
            item.file.extension.lowercase() == "apk" -> Icons.Default.Android
            item.file.extension.lowercase() in listOf("mp4", "mkv", "avi") -> Icons.Default.PlayArrow
            else -> Icons.Default.InsertDriveFile
        }
        val tint = if (item.isDir) AccentBlue else AccentPurple
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(28.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(item.file.name, color = TextPrimary, fontSize = 15.sp, maxLines = 1)
            Text(if (item.isDir) "Folder" else "${item.file.length() / 1024} KB", color = TextSecondary, fontSize = 12.sp)
        }
        if (isSelected) {
            Icon(Icons.Default.CheckCircle, contentDescription = "Selected", tint = AccentBlue)
        }
    }
}

fun openFile(context: Context, file: File) {
    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, context.contentResolver.getType(uri) ?: "*/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
