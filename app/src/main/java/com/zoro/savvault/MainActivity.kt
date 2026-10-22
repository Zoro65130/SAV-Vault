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

    // 1. FIX: Define loadFiles before the launchers that use it
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
                        loadFiles(currentPath) // Now resolves correctly
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
        // ... (Editor code remains the same) ...
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
                            } catch (e: Exception) { Toast.makeText(context, "Save failed", Toast.LENGTH_SHORT).show() }
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
        // ... (Bottom sheet code remains the same) ...
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
                    // ... (Top bar UI remains the same) ...
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
                            // 2. FIX: Replaced animateItem with animateItemPlacement
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

    // ... (Dialogs remain the same) ...
    if (showAddMenu) { AlertDialog(
        onDismissRequest = { showAddMenu = false },
        title = { Text("Add New", color = TextPrimary) },
        text = { Column {
            TextButton(onClick = { showAddMenu = false; showCreateFolderDialog = true }) { Text("Create Folder", color = AccentBlue) }
            TextButton(onClick = { showAddMenu = false; showCreateFileDialog = true }) { Text("Create Empty File", color = AccentBlue) }
            TextButton(onClick = { showAddMenu = false; importFileLauncher.launch("*/*") }) { Text("Import File", color = AccentBlue) }
        }},
        confirmButton = { TextButton(onClick = { showAddMenu = false }) { Text("Cancel", color = TextSecondary) } },
        containerColor = SurfaceDark
    )}
    if (showCreateFolderDialog) { var folderName by remember { mutableStateOf("") }; AlertDialog(
        onDismissRequest = { showCreateFolderDialog = false },
        title = { Text("New Folder", color = TextPrimary) },
        text = { TextField(value = folderName, onValueChange = { folderName = it }, placeholder = { Text("Folder name") }) },
        confirmButton = { TextButton(onClick = {
            if (folderName.isNotEmpty()) { if (File(currentPath, folderName).mkdirs()) loadFiles(currentPath) else Toast.makeText(context, "Failed", Toast.LENGTH_SHORT).show() }
            showCreateFolderDialog = false
        }) { Text("Create", color = AccentBlue) } },
        containerColor = SurfaceDark
    )}
    if (showCreateFileDialog) { var fileName by remember { mutableStateOf("") }; AlertDialog(
        onDismissRequest = { showCreateFileDialog = false },
        title = { Text("New File", color = TextPrimary) },
        text = { TextField(value = fileName, onValueChange = { fileName = it }, placeholder = { Text("File name") }) },
        confirmButton = { TextButton(onClick = {
            if (fileName.isNotEmpty()) { try { if (File(currentPath, fileName).createNewFile()) loadFiles(currentPath) } catch (e: Exception) { Toast.makeText(context, "Failed", Toast.LENGTH_SHORT).show() } }
            showCreateFileDialog = false
        }) { Text("Create", color = AccentBlue) } },
        containerColor = SurfaceDark
    )}
    if (showCompressDialog) { var archiveName by remember { mutableStateOf("archive") }; AlertDialog(
        onDismissRequest = { showCompressDialog = false },
        title = { Text("Compress ${selectedFiles.size} items", color = TextPrimary) },
        text = { Column {
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
        }},
        confirmButton = { TextButton(onClick = { showCompressDialog = false }) { Text("Cancel", color = TextSecondary) } },
        containerColor = SurfaceDark
    )}
    if (isCompressing) { Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { CircularProgressIndicator(color = AccentBlue); Spacer(Modifier.height(16.dp)); Text("Compressing...", color = TextPrimary) } } }
}
