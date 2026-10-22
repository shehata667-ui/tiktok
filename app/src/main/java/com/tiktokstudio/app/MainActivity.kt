package com.tiktokstudio.app

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        QueueManager.init(applicationContext)
        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                MaterialTheme { AppRoot() }
            }
        }
    }
}

enum class Screen { Home, Videos, Editor, Queue, Settings, Analysis, Compare }

@Composable
fun AppRoot(vm: VideosViewModel = viewModel()) {
    val ctx = LocalContext.current
    var screen by remember { mutableStateOf(Screen.Home) }
    var editing by remember { mutableStateOf<VideoInfo?>(null) }
    var analyzing by remember { mutableStateOf<VideoInfo?>(null) }
    var comparing by remember { mutableStateOf<ExportJob?>(null) }
    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
            notifPerm.launch(android.Manifest.permission.POST_NOTIFICATIONS)
    }
    BackHandler(enabled = screen != Screen.Home) {
        screen = when (screen) { Screen.Editor, Screen.Analysis -> Screen.Videos; Screen.Compare -> Screen.Queue; else -> Screen.Home }
    }
    when (screen) {
        Screen.Home -> HomeScreen(vm.videos.size,
            QueueManager.jobs.count { it.status == JobStatus.QUEUED || it.status == JobStatus.RUNNING },
            { screen = Screen.Videos }, { screen = Screen.Queue }, { screen = Screen.Settings })
        Screen.Videos -> VideosScreen(vm, { screen = Screen.Home }, { analyzing = it; screen = Screen.Analysis }) { editing = it; screen = Screen.Editor }
        Screen.Editor -> editing?.let { e ->
            EditorScreen(e, vm.videos.filter { it.uri in vm.selected }, { screen = Screen.Videos }, { screen = Screen.Queue })
        }
        Screen.Queue -> QueueScreen({ screen = Screen.Home }) { comparing = it; screen = Screen.Compare }
        Screen.Analysis -> analyzing?.let { a -> AnalysisScreen(a, { screen = Screen.Videos }) { editing = a; screen = Screen.Editor } }
        Screen.Compare -> comparing?.let { c -> CompareScreen(c) { screen = Screen.Queue } }
        Screen.Settings -> SettingsScreen { screen = Screen.Home }
    }
}

@Composable
fun HomeScreen(videoCount: Int, activeJobs: Int, onVideos: () -> Unit, onQueue: () -> Unit, onSettings: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.VideoLibrary, null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text("TikTok Video Studio", style = MaterialTheme.typography.headlineMedium)
        Text("جهّز فيديوهاتك للنشر", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(32.dp))
        Button(onClick = onVideos, Modifier.fillMaxWidth()) {
            Text(if (videoCount == 0) "اختيار الفيديوهات" else "الفيديوهات ($videoCount)")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onQueue, Modifier.fillMaxWidth()) {
            Text("المعالجة والسجل" + if (activeJobs > 0) " ($activeJobs)" else "")
        }
        TextButton(onClick = onSettings) { Text("الإعدادات") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideosScreen(vm: VideosViewModel, onBack: () -> Unit, onAnalyze: (VideoInfo) -> Unit, onEdit: (VideoInfo) -> Unit) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { vm.addVideos(it) }
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(vm.message) { vm.message?.let { snack.showSnackbar(it); vm.message = null } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("الفيديوهات (${vm.selected.size}/${vm.videos.size})") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") } },
                actions = {
                    if (vm.videos.isNotEmpty()) {
                        IconButton(onClick = { if (vm.selected.size == vm.videos.size) vm.clearSelection() else vm.selectAll() }) {
                            Icon(Icons.Default.SelectAll, "تحديد الكل")
                        }
                        IconButton(onClick = { vm.removeSelected() }, enabled = vm.selected.isNotEmpty()) {
                            Icon(Icons.Default.Delete, "إزالة من القائمة")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { picker.launch(arrayOf("video/*")) },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("إضافة فيديو") }
            )
        },
        snackbarHost = { SnackbarHost(snack) }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            if (vm.videos.isEmpty() && !vm.loading) {
                Text("لسه مفيش فيديوهات. اضغط «إضافة فيديو».", Modifier.align(Alignment.Center))
            }
            LazyColumn(contentPadding = PaddingValues(12.dp, 12.dp, 12.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(vm.videos, key = { it.uri.toString() }) { v ->
                    VideoRow(v, v.uri in vm.selected, { onAnalyze(v) }, { onEdit(v) }) { vm.toggle(v.uri) }
                }
            }
            if (vm.loading) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
        }
    }
}

@Composable
fun VideoRow(v: VideoInfo, checked: Boolean, onAnalyze: () -> Unit, onEdit: () -> Unit, onToggle: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onToggle)) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked, { onToggle() })
            Thumb(v.uri)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(v.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                Text("${v.durationText} • ${v.sizeText}", style = MaterialTheme.typography.bodySmall)
                Text("${v.resolution} • ${v.fpsText} • ${if (v.isPortrait) "عمودي" else "أفقي"}", style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onAnalyze) { Icon(Icons.Default.Search, "فحص") }
            IconButton(onClick = onEdit) { Icon(Icons.Default.Tune, "تجهيز") }
        }
    }
}

@Composable
fun Thumb(uri: Uri) {
    val ctx = LocalContext.current
    val bmp by produceState<Bitmap?>(null, uri) {
        value = withContext(Dispatchers.IO) {
            val r = MediaMetadataRetriever()
            try {
                r.setDataSource(ctx, uri)
                r.getScaledFrameAtTime(500_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 200, 200)
            } catch (e: Exception) { null } finally { r.release() }
        }
    }
    Box(Modifier.size(72.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant), Alignment.Center) {
        bmp?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            ?: Icon(Icons.Default.Movie, null)
    }
}
