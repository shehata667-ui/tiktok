package com.tiktokstudio.app

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        QueueManager.init(applicationContext)
        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                AppTheme {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { AppRoot() }
                }
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
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().background(cs.background).navigationBarsPadding().verticalScroll(rememberScrollState())) {
        Box(
            Modifier.fillMaxWidth()
                .background(Brush.verticalGradient(listOf(cs.primaryContainer, cs.background)))
                .statusBarsPadding().padding(start = 24.dp, end = 24.dp, top = 32.dp, bottom = 28.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier.size(68.dp).clip(RoundedCornerShape(22.dp))
                        .background(Brush.linearGradient(listOf(cs.primary, cs.secondary))),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Default.VideoLibrary, null, Modifier.size(36.dp), tint = Color.White) }
                Spacer(Modifier.height(4.dp))
                Text("TikTok Video Studio", style = MaterialTheme.typography.headlineMedium)
                Text("جهّز فيديوهاتك للنشر بجودة عالية وبدون تقطيع", style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant)
            }
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ActionCard(Icons.Default.VideoLibrary, "الفيديوهات",
                if (videoCount == 0) "اختار فيديوهات من هاتفك وابدأ" else "$videoCount فيديو في القائمة", cs.primary, 0, onVideos)
            ActionCard(Icons.Default.History, "المعالجة والسجل",
                if (activeJobs > 0) "$activeJobs قيد المعالجة أو الانتظار" else "الطابور والنتائج الجاهزة", cs.secondary, activeJobs, onQueue)
            ActionCard(Icons.Default.Settings, "الإعدادات", "مجلد الحفظ وسجل الأخطاء", cs.tertiary, 0, onSettings)
            Panel("إزاي تبدأ؟", Icons.Default.Info) {
                Text("١. اختار فيديو أو أكتر من هاتفك.\n٢. اضغط «فحص» لتشوف مشاكل الفيديو، ثم «تجهيز» لضبط المقاس والجودة والعلامة المائية.\n٣. صدّر، وهتلاقي النتيجة في «المعالجة والسجل».",
                    style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            }
        }
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
                text = { Text("إضافة فيديو") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        },
        snackbarHost = { SnackbarHost(snack) }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            if (vm.videos.isEmpty() && !vm.loading) {
                EmptyState(Icons.Default.VideoLibrary, "لسه مفيش فيديوهات", "اضغط «إضافة فيديو» واختار من هاتفك. تقدر تختار أكتر من فيديو مرة واحدة.",
                    Modifier.align(Alignment.Center))
            }
            LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 112.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
    val cs = MaterialTheme.colorScheme
    Card(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).clickable(onClick = onToggle),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = if (checked) cs.primaryContainer.copy(alpha = 0.5f) else cs.surface),
        border = if (checked) BorderStroke(2.dp, cs.primary) else BorderStroke(1.dp, cs.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box {
                    Thumb(v.uri, 88.dp)
                    Box(
                        Modifier.align(Alignment.BottomStart).padding(4.dp).clip(RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = 0.65f)).padding(horizontal = 6.dp, vertical = 1.dp)
                    ) { Text(v.durationText, color = Color.White, style = MaterialTheme.typography.labelSmall) }
                    if (checked) Box(
                        Modifier.align(Alignment.TopStart).padding(4.dp).size(22.dp).clip(CircleShape).background(cs.primary),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Default.Check, null, Modifier.size(15.dp), tint = cs.onPrimary) }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(v.name, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                    Text("${v.resolution} • ${v.fpsText}", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Pill(v.sizeText, cs.surfaceVariant, cs.onSurfaceVariant)
                        Pill(if (v.isPortrait) "عمودي" else "أفقي", cs.secondaryContainer, cs.onSecondaryContainer)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onAnalyze, Modifier.weight(1f)) {
                    Icon(Icons.Default.Search, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("فحص")
                }
                Button(onClick = onEdit, Modifier.weight(1f)) {
                    Icon(Icons.Default.Tune, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("تجهيز")
                }
            }
        }
    }
}

@Composable
fun Thumb(uri: Uri, size: Dp = 72.dp) {
    val ctx = LocalContext.current
    val bmp by produceState<Bitmap?>(null, uri) {
        value = withContext(Dispatchers.IO) {
            val r = MediaMetadataRetriever()
            try {
                r.setDataSource(ctx, uri)
                r.getFrameAtTime(500_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.let { b ->
                    val k = 300f / maxOf(b.width, b.height)
                    if (k < 1f) Bitmap.createScaledBitmap(b, (b.width * k).toInt().coerceAtLeast(1), (b.height * k).toInt().coerceAtLeast(1), true) else b
                }
            } catch (e: Exception) { null } finally { r.release() }
        }
    }
    Box(Modifier.size(size).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant), Alignment.Center) {
        bmp?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            ?: Icon(Icons.Default.Movie, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
