package com.zoro.savvault

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.DecimalFormat
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val externalUri = intent?.data
        setContent {
            SAVVaultTheme {
                MainAppController(externalUri)
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
data class EditorState(val file: File? = null, val uri: Uri? = null, val content: String)

@Composable
fun MainAppController(initialUri: Uri?) {
    var showSplash by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(1500)
        showSplash = false
    }

    AnimatedContent(
        targetState = showSplash,
        transitionSpec = { fadeIn(tween(500, easing = FastOutSlowInEasing)) togetherWith fadeOut(tween(500, easing = FastOutSlowInEasing)) },
        label = "SplashTransition"
    ) { isSplash ->
        if (isSplash) SplashScreen() else FileManagerApp(initialUri)
    }
}

@Composable
fun SplashScreen() {
    val scale = remember { Animatable(0.5f) }
    val alpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        scale.animateTo(1f, animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessLow))
        alpha.animateTo(1f, animationSpec = tween(600))
    }

    Box(
        modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(DarkBackground, Color(0xFF101828)))),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Lock, null, tint = AccentBlue, modifier = Modifier.size(100.dp).scale(scale.value).alpha(alpha.value))
            Spacer(Modifier.height(24.dp))
            Text("SAV Vault", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = TextPrimary, modifier = Modifier.alpha(alpha.value))
            Spacer(Modifier.height(8.dp))
            Text("Your files. Your privacy.", fontSize = 14.sp, color = TextSecondary, modifier = Modifier.alpha(alpha.value))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileManagerApp(initialUri: Uri? = null) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    var currentPath by remember { mutableStateOf(Environment.getExternalStorageDirectory().absolutePath) }
    var files by remember { mutableStateOf<List<FileItem>>(emptyList()) }
    var selectedFiles by remember { mutableStateOf<Set<FileItem>>(emptySet()) }
    var searchQuery by remember { mutableStateOf("") }
    var showHiddenFiles by remember { mutableStateOf(false) }
    var showSettingsScreen by remember { mutableStateOf(false) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var showCreateFileDialog by remember { mutableStateOf(false) }
    var showAddMenu by remember { mutableStateOf(false) }
    var showCompressDialog by remember { mutableStateOf(false) }
    var bottomSheetFile by remember { mutableStateOf<FileItem?>(null) }
    var activeEditor by remember { mutableStateOf<EditorState?>(null) }
    var hasPermission by remember { mutableStateOf(false) }
    var isCompressing by remember { mutableStateOf(false) }

    fun loadFiles(path: String) {
        val dir = File(path)
        var fileList = dir.listFiles()?.map { FileItem(it, it.isDirectory) } ?: emptyList()
        if (!showHiddenFiles) fileList = fileList.filter { !it.file.name.startsWith(".") }
        files = fileList.sortedWith(compareBy({ !it.isDir }, { it.file.name.lowercase() }))
        currentPath = path
        selectedFiles = emptySet()
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            hasPermission = Environment.isExternalStorageManager()
        }
    }

    val importFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val inputStream = context.contentResolver.openInputStream(it)
                    val fileName = getFileName(context, it) ?: "imported_${System.currentTimeMillis()}"
                    val destFile = File(currentPath, fileName)
                    inputStream?.use { input ->
                        destFile.outputStream().use { output -> input.copyTo(output) }
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "File imported", Toast.LENGTH_SHORT).show()
                        loadFiles(currentPath) 
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Import failed", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        if (initialUri != null) {
            try {
                val content = context.contentResolver.openInputStream(initialUri)?.bufferedReader()?.use { it.readText() } ?: ""
                activeEditor = EditorState(file = null, uri = initialUri, content = content)
            } catch (e: Exception) { 
                Toast.makeText(context, "Cannot open file", Toast.LENGTH_SHORT).show()
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) hasPermission = true
            else {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:${context.packageName}")
                permissionLauncher.launch(intent)
            }
        } else hasPermission = true
    }

    LaunchedEffect(hasPermission, currentPath, showHiddenFiles) {
        if (hasPermission) loadFiles(currentPath)
    }

    if (showSettingsScreen) {
        SettingsScreen(showHiddenFiles, { showHiddenFiles = it }, { showSettingsScreen = false })
        BackHandler { showSettingsScreen = false }
        return
    }

    if (activeEditor != null) {
        val editor = activeEditor!!
        var text by remember { mutableStateOf(editor.content) }
        Scaffold(
            containerColor = DarkBackground,
            topBar = {
                TopAppBar(
                    title = { Text(editor.file?.name ?: "External File", color = TextPrimary, fontSize = 16.sp) },
                    navigationIcon = { IconButton(onClick = { activeEditor = null }) { Icon(Icons.Default.ArrowBack, null, tint = TextPrimary) } },
                    actions = {
                        IconButton(onClick = {
                            try {
                                if (editor.file != null) editor.file.writeText(text)
                                else if (editor.uri != null) context.contentResolver.openOutputStream(editor.uri)?.bufferedWriter()?.use { it.write(text) }
                                Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()
                                activeEditor = null
                            } catch (e: Exception) { 
                                Toast.makeText(context, "Save failed", Toast.LENGTH_SHORT).show()
                            }
                        }) { Icon(Icons.Default.Save, null, tint = AccentBlue) }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceDark)
                )
            }
        ) { padding ->
            TextField(
                value = text, onValueChange = { text = it },
                modifier = Modifier.padding(padding).fillMaxSize().padding(8.dp),
                colors = TextFieldDefaults.colors(focusedContainerColor = DarkBackground, unfocusedContainerColor = DarkBackground, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, cursorColor = AccentBlue)
            )
        }
        BackHandler { activeEditor = null }
        return
    }

    if (bottomSheetFile != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val fileItem = bottomSheetFile!!
        ModalBottomSheet(onDismissRequest = { bottomSheetFile = null }, sheetState = sheetState, containerColor = SurfaceDark) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(fileItem.file.name, color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 16.dp))
                Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).combinedClickable(onClick = {
                    bottomSheetFile = null
                    val textExtensions = listOf("txt", "md", "json", "xml", "html", "css", "js", "kt", "java", "log", "csv")
                    if (fileItem.file.extension.lowercase() in textExtensions) {
                        try { activeEditor = EditorState(file = fileItem.file, content = fileItem.file.readText()) }
                        catch (e: Exception) { openDefault(context, fileItem.file) }
                    } else openDefault(context, fileItem.file)
                }, onLongClick = {}).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.OpenInNew, null, tint = AccentBlue)
                    Spacer(Modifier.width(16.dp))
                    Text("Open", color = TextPrimary, fontSize = 16.sp)
                }
                Divider(color = Color.DarkGray, thickness = 1.dp)
                Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).combinedClickable(onClick = {
                    bottomSheetFile = null
                    openWith(context, fileItem.file)
                }, onLongClick = {}).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Launch, null, tint = AccentBlue)
                    Spacer(Modifier.width(16.dp))
                    Text("Open With", color = TextPrimary, fontSize = 16.sp)
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }

    BackHandler(enabled = currentPath != Environment.getExternalStorageDirectory().absolutePath) {
        val parent = File(currentPath).parent
        if (parent != null) loadFiles(parent)
    }

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            AnimatedContent(targetState = selectedFiles.isEmpty(), transitionSpec = { fadeIn(spring(stiffness = Spring.StiffnessLow)) togetherWith fadeOut(spring(stiffness = Spring.StiffnessLow)) }, label = "TopBar") { isSelectionEmpty ->
                if (isSelectionEmpty) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Lock, null, tint = AccentBlue)
                            Spacer(Modifier.width(8.dp))
                            Text("SAV Vault", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = AccentBlue)
                            Spacer(Modifier.weight(1f))
                            IconButton(onClick = { showSettingsScreen = true }) { Icon(Icons.Default.Settings, null, tint = TextPrimary) }
                        }
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(value = searchQuery, onValueChange = { searchQuery = it }, placeholder = { Text("Search files...", color = TextSecondary) }, leadingIcon = { Icon(Icons.Default.Search, null, tint = TextSecondary) }, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(12.dp), colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = SurfaceDark, focusedContainerColor = SurfaceDark, unfocusedBorderColor = Color.Transparent, focusedBorderColor = AccentBlue))
                    }
                } else {
                    TopAppBar(
                        title = { Text("${selectedFiles.size} selected", color = TextPrimary) },
                        navigationIcon = { IconButton(onClick = { selectedFiles = emptySet() }) { Icon(Icons.Default.Close, null, tint = TextPrimary) } },
                        actions = {
                            IconButton(onClick = { showCompressDialog = true }) { Icon(Icons.Default.Archive, null, tint = AccentBlue) }
                            IconButton(onClick = {
                                val filesToDelete = selectedFiles.map { it.file }
                                coroutineScope.launch(Dispatchers.IO) {
                                    var success = true
                                    filesToDelete.forEach { if (!it.deleteRecursively()) success = false }
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, if (success) "Deleted" else "Some files failed", Toast.LENGTH_SHORT).show()
                                        loadFiles(currentPath)
                                        selectedFiles = emptySet()
                                    }
                                }
                            }) { Icon(Icons.Default.Delete, null, tint = Color.Red) }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceDark)
                    )
                }
            }
        },
        floatingActionButton = {
            AnimatedVisibility(visible = selectedFiles.isEmpty() && hasPermission, enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)) + fadeIn(), exit = scaleOut(tween(150)) + fadeOut()) {
                FloatingActionButton(onClick = { showAddMenu = true }, containerColor = AccentBlue, contentColor = Color.Black, shape = RoundedCornerShape(16.dp)) { Icon(Icons.Default.Add, "Add") }
            }
        }
    ) { paddingValues ->
        if (hasPermission) {
            Column(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
                StorageCard()
                val filteredFiles = files.filter { it.file.name.contains(searchQuery, ignoreCase = true) }
                LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
                    items(items = filteredFiles, key = { it.file.absolutePath }) { item ->
                        val isSelected = selectedFiles.contains(item)
                        FileRow(
                            modifier = Modifier.animateItemPlacement(animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy)),
                            item = item, isSelected = isSelected,
                            onClick = {
                                if (selectedFiles.isNotEmpty()) selectedFiles = if (isSelected) selectedFiles - item else selectedFiles + item
                                else { if (item.isDir) loadFiles(item.file.absolutePath) else bottomSheetFile = item }
                            },
                            onLongClick = { selectedFiles = selectedFiles + item }
                        )
                    }
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) { Text("Storage permission required.", color = TextPrimary) }
        }
    }

    if (showAddMenu) {
        AlertDialog(
            onDismissRequest = { showAddMenu = false },
            title = { Text("Add New", color = TextPrimary) },
            text = {
                Column {
                    TextButton(onClick = { showAddMenu = false; showCreateFolderDialog = true }) { Text("Create Folder", color = AccentBlue) }
                    TextButton(onClick = { showAddMenu = false; showCreateFileDialog = true }) { Text("Create Empty File", color = AccentBlue) }
                    TextButton(onClick = { showAddMenu = false; importFileLauncher.launch("*/*") }) { Text("Import File", color = AccentBlue) }
                }
            },
            confirmButton = { TextButton(onClick = { showAddMenu = false }) { Text("Cancel", color = TextSecondary) } },
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
                    if (folderName.isNotEmpty()) { if (File(currentPath, folderName).mkdirs()) loadFiles(currentPath) else Toast.makeText(context, "Failed", Toast.LENGTH_SHORT).show() }
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
            text = { TextField(value = fileName, onValueChange = { fileName = it }, placeholder = { Text("File name") }) },
            confirmButton = {
                TextButton(onClick = {
                    if (fileName.isNotEmpty()) {
                        try { if (File(currentPath, fileName).createNewFile()) loadFiles(currentPath) } catch (e: Exception) { Toast.makeText(context, "Failed", Toast.LENGTH_SHORT).show() }
                    }
                    showCreateFileDialog = false
                }) { Text("Create", color = AccentBlue) }
            },
            containerColor = SurfaceDark
        )
    }

    if (showCompressDialog) {
        var archiveName by remember { mutableStateOf("archive") }
        AlertDialog(
            onDismissRequest = { showCompressDialog = false },
            title = { Text("Compress ${selectedFiles.size} items", color = TextPrimary) },
            text = {
                Column {
                    TextField(value = archiveName, onValueChange = { archiveName = it }, label = { Text("Archive name") }, colors = TextFieldDefaults.colors(focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary))
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            showCompressDialog = false; isCompressing = true
                            coroutineScope.launch(Dispatchers.IO) {
                                val result = compressToZip(File(currentPath, "$archiveName.zip"), selectedFiles.map { it.file })
                                withContext(Dispatchers.Main) { isCompressing = false; selectedFiles = emptySet(); Toast.makeText(context, if (result) "ZIP Created" else "Failed", Toast.LENGTH_SHORT).show(); loadFiles(currentPath) }
                            }
                        }, colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)) { Text("ZIP", color = Color.Black) }
                        
                        Button(onClick = {
                            showCompressDialog = false; isCompressing = true
                            coroutineScope.launch(Dispatchers.IO) {
                                val result = compressTo7z(File(currentPath, "$archiveName.7z"), selectedFiles.map { it.file })
                                withContext(Dispatchers.Main) { isCompressing = false; selectedFiles = emptySet(); Toast.makeText(context, if (result) "7z Created" else "Failed", Toast.LENGTH_SHORT).show(); loadFiles(currentPath) }
                            }
                        }, colors = ButtonDefaults.buttonColors(containerColor = AccentPurple)) { Text("7z", color = Color.Black) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showCompressDialog = false }) { Text("Cancel", color = TextSecondary) } },
            containerColor = SurfaceDark
        )
    }

    if (isCompressing) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = AccentBlue)
                Spacer(Modifier.height(16.dp))
                Text("Compressing...", color = TextPrimary)
            }
        }
    }
}

@Composable
fun SettingsScreen(showHiddenFiles: Boolean, onShowHiddenFilesChange: (Boolean) -> Unit, onBack: () -> Unit) {
    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            TopAppBar(
                title = { Text("Settings", color = TextPrimary) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = TextPrimary) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceDark)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            Text("Appearance", color = AccentBlue, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
            Card(colors = CardDefaults.cardColors(containerColor = SurfaceDark), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("Show hidden files", color = TextPrimary, fontSize = 16.sp)
                        Text("Show files starting with a dot (.)", color = TextSecondary, fontSize = 12.sp)
                    }
                    Switch(checked = showHiddenFiles, onCheckedChange = onShowHiddenFilesChange, colors = SwitchDefaults.colors(checkedThumbColor = AccentBlue, checkedTrackColor = AccentBlue.copy(alpha = 0.5f)))
                }
            }
        }
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
        } finally { cursor?.close() }
    }
    if (result == null) {
        result = uri.path
        val cut = result?.lastIndexOf('/') ?: -1
        if (cut != -1) result = result?.substring(cut + 1)
    }
    return result
}

fun openWith(context: Context, file: File) {
    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, context.contentResolver.getType(uri) ?: "*/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Open With"))
    } catch (e: Exception) { 
        Toast.makeText(context, "No app found to open file", Toast.LENGTH_SHORT).show()
    }
}

fun openDefault(context: Context, file: File) {
    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, context.contentResolver.getType(uri) ?: "*/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: Exception) { 
        Toast.makeText(context, "No app found to open file", Toast.LENGTH_SHORT).show()
    }
}

fun compressToZip(zipFile: File, filesToCompress: List<File>): Boolean {
    return try {
        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            filesToCompress.forEach { file ->
                if (file.absolutePath == zipFile.absolutePath) return@forEach
                
                if (file.isDirectory) {
                    file.walkTopDown().forEach { child ->
                        if (child.isFile && child.absolutePath != zipFile.absolutePath) {
                            val entryName = "${file.name}/${child.relativeTo(file).path}"
                            zos.putNextEntry(ZipEntry(entryName))
                            FileInputStream(child).use { it.copyTo(zos) }
                            zos.closeEntry()
                        }
                    }
                } else {
                    zos.putNextEntry(ZipEntry(file.name))
                    FileInputStream(file).use { it.copyTo(zos) }
                    zos.closeEntry()
                }
            }
        }
        true
    } catch (e: Exception) { 
        e.printStackTrace()
        false
    }
}

fun compressTo7z(sevenZFile: File, filesToCompress: List<File>): Boolean {
    return try {
        SevenZOutputFile(sevenZFile).use { sevenZOutput ->
            val buffer = ByteArray(8192)
            filesToCompress.forEach { file ->
                if (file.absolutePath == sevenZFile.absolutePath) return@forEach
                
                if (file.isDirectory) {
                    file.walkTopDown().forEach { child ->
                        if (child.isFile && child.absolutePath != sevenZFile.absolutePath) {
                            val entry = SevenZArchiveEntry()
                            entry.name = "${file.name}/${child.relativeTo(file).path}"
                            sevenZOutput.putArchiveEntry(entry)
                            FileInputStream(child).use { input ->
                                var len: Int
                                while (input.read(buffer).also { len = it } > 0) {
                                    sevenZOutput.write(buffer, 0, len)
                                }
                            }
                            sevenZOutput.closeArchiveEntry()
                        }
                    }
                } else {
                    val entry = SevenZArchiveEntry()
                    entry.name = file.name
                    sevenZOutput.putArchiveEntry(entry)
                    FileInputStream(file).use { input ->
                        var len: Int
                        while (input.read(buffer).also { len = it } > 0) {
                            sevenZOutput.write(buffer, 0, len)
                        }
                    }
                    sevenZOutput.closeArchiveEntry()
                }
            }
        }
        true
    } catch (e: Exception) { 
        e.printStackTrace()
        false
    }
}

@Composable
fun StorageCard() {
    val stat = StatFs(Environment.getExternalStorageDirectory().path)
    val totalBytes = stat.blockCountLong * stat.blockSizeLong
    val availableBytes = stat.availableBlocksLong * stat.blockSizeLong
    val usedBytes = totalBytes - availableBytes
    val usedPercentage = (usedBytes.toFloat() / totalBytes.toFloat()) * 100
    val df = DecimalFormat("#.##")
    Card(modifier = Modifier.fillMaxWidth().padding(8.dp), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = SurfaceDark)) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(progress = usedPercentage / 100f, modifier = Modifier.size(60.dp), color = AccentBlue, trackColor = DarkBackground, strokeWidth = 6.dp)
                Text("${usedPercentage.toInt()}%", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text("Internal Storage", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("${df.format(usedBytes / (1024f * 1024f * 1024f))} GB / ${df.format(totalBytes / (1024f * 1024f * 1024f))} GB used", color = TextSecondary, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun FileRow(modifier: Modifier = Modifier, item: FileItem, isSelected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val bgColor = if (isSelected) AccentBlue.copy(alpha = 0.2f) else Color.Transparent
    val animatedBgColor by animateColorAsState(targetValue = bgColor, animationSpec = tween(200), label = "RowBackground")
    val iconScale by animateFloatAsState(targetValue = if (isSelected) 1.2f else 1.0f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "IconScale")

    Row(
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(animatedBgColor).combinedClickable(onClick = onClick, onLongClick = onLongClick).padding(horizontal = 12.dp, vertical = 12.dp).animateContentSize(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val isImage = item.file.extension.lowercase() in listOf("jpg", "jpeg", "png", "gif", "webp")
        if (isImage && !item.isDir) {
            AsyncImage(model = item.file, contentDescription = "Thumbnail", modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).scale(iconScale), contentScale = ContentScale.Crop)
        } else {
            val icon = when {
                item.isDir -> Icons.Default.Folder
                item.file.extension.lowercase() == "apk" -> Icons.Default.Android
                item.file.extension.lowercase() in listOf("mp4", "mkv", "avi") -> Icons.Default.PlayArrow
                else -> Icons.Default.InsertDriveFile
            }
            val tint = if (item.isDir) AccentBlue else AccentPurple
            Icon(icon, null, tint = tint, modifier = Modifier.size(28.dp).scale(iconScale))
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(item.file.name, color = TextPrimary, fontSize = 15.sp, maxLines = 1)
            Text(if (item.isDir) "Folder" else "${item.file.length() / 1024} KB", color = TextSecondary, fontSize = 12.sp)
        }
        AnimatedVisibility(visible = isSelected, enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
            Icon(Icons.Default.CheckCircle, "Selected", tint = AccentBlue)
        }
    }
}
