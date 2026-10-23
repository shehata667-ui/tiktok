package com.tiktokstudio.app

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import java.io.File

private fun statusText(s: JobStatus) = when (s) {
    JobStatus.QUEUED -> "في الانتظار"; JobStatus.RUNNING -> "جاري المعالجة"; JobStatus.DONE -> "تمت ✓"
    JobStatus.FAILED -> "فشلت"; JobStatus.CANCELLED -> "اتلغت"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueScreen(onBack: () -> Unit, onCompare: (ExportJob) -> Unit) {
    val ctx = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
    val active = QueueManager.jobs.filter { it.status == JobStatus.QUEUED || it.status == JobStatus.RUNNING }
    val history = QueueManager.jobs.filter { it.status != JobStatus.QUEUED && it.status != JobStatus.RUNNING }.reversed()
    Scaffold(topBar = {
        TopAppBar(title = { Text("المعالجة والسجل" ) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") } },
            actions = {
                if (tab == 0 && active.isNotEmpty()) TextButton(onClick = { QueueManager.cancelAll() }) { Text("إلغاء الكل") }
                if (tab == 1 && history.isNotEmpty()) TextButton(onClick = { QueueManager.clearFinished() }) { Text("مسح السجل") }
            })
    }) { pad ->
        Column(Modifier.padding(pad)) {
            TabRow(selectedTabIndex = tab) {
                Tab(tab == 0, { tab = 0 }, text = { Text("الطابور (${active.size})") })
                Tab(tab == 1, { tab = 1 }, text = { Text("السجل (${history.size})") })
            }
            if (tab == 0) {
                if (QueueManager.interruptedCount > 0 && !QueueManager.running)
                    Text("فيه ${QueueManager.interruptedCount} مهمة اتقطعت بعد غلق التطبيق. الاستئناف هيبدأ كل مهمة من الأول.",
                        Modifier.padding(12.dp), color = MaterialTheme.colorScheme.error)
                if (active.any { it.status == JobStatus.QUEUED } && !QueueManager.running)
                    Button(onClick = { ExportService.start(ctx) }, Modifier.fillMaxWidth().padding(12.dp)) {
                        Text(if (QueueManager.interruptedCount > 0) "استئناف المهام" else "بدء المعالجة")
                    }
            }
            val list = if (tab == 0) active else history
            if (list.isEmpty()) EmptyState(Icons.Default.History, if (tab == 0) "الطابور فاضي" else "السجل فاضي",
                if (tab == 0) "أضف فيديوهات من شاشة التجهيز وهتظهر هنا." else "نتايج المعالجة هتظهر هنا.")
            LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(list, key = { it.id }) { JobCard(it, onCompare) }
            }
        }
    }
}

@Composable
fun JobCard(j: ExportJob, onCompare: (ExportJob) -> Unit) {
    val ctx = LocalContext.current
    val err = MaterialTheme.colorScheme.error
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(j.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            StatusPill(j.status)
            when (j.status) {
                JobStatus.RUNNING -> {
                    LinearProgressIndicator(progress = { j.progress }, Modifier.fillMaxWidth())
                    Text("${(j.progress * 100).toInt()}% • المنقضي ${formatDuration(j.elapsedMs)}" + (j.etaMs?.let { " • المتبقي ~${formatDuration(it)}" } ?: ""))
                    j.message?.let { Text(it) }
                    j.warning?.let { Text("⚠ $it", color = err) }
                }
                JobStatus.QUEUED -> j.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                JobStatus.DONE -> {
                    j.summary?.let { Text(it) }
                    j.warnings.forEach { Text("⚠ $it", color = err) }
                    if (j.warnings.isEmpty()) Text("اجتاز الفحص الأساسي (فتح الملف، المدة، الإطارات، الصوت). جرّبه على أكتر من مشغل.", style = MaterialTheme.typography.bodySmall)
                    j.outUri?.let { u ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { openVideo(ctx, Uri.parse(u)) }) { Text("تشغيل") }
                            OutlinedButton(onClick = { shareVideo(ctx, Uri.parse(u)) }) { Text("مشاركة") }
                            OutlinedButton(onClick = { onCompare(j) }) { Text("مقارنة") }
                        }
                    }
                }
                JobStatus.FAILED -> j.message?.let { Text(it, color = err) }
                JobStatus.CANCELLED -> {}
            }
            Row {
                if (j.status == JobStatus.QUEUED || j.status == JobStatus.RUNNING) TextButton(onClick = { QueueManager.cancel(j.id) }) { Text("إلغاء") }
                if (j.status == JobStatus.FAILED || j.status == JobStatus.CANCELLED)
                    TextButton(onClick = { QueueManager.retry(j.id); ExportService.start(ctx) }) { Text("إعادة المحاولة") }
                if (j.status != JobStatus.RUNNING && j.status != JobStatus.QUEUED) TextButton(onClick = { QueueManager.remove(j.id) }) { Text("إزالة من السجل") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var tree by remember { mutableStateOf(Prefs.outTree(ctx)) }
    var cleaned by remember { mutableStateOf<String?>(null) }
    var logText by remember { mutableStateOf(File(ctx.filesDir, "errors.log").takeIf { it.exists() }?.readText()?.takeLast(4000) ?: "") }
    val clip = LocalClipboardManager.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { u ->
        if (u != null) {
            ctx.contentResolver.takePersistableUriPermission(u, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            Prefs.setOutTree(ctx, u); tree = u
        }
    }
    Scaffold(topBar = {
        TopAppBar(title = { Text("الإعدادات" ) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") } })
    }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Sec("مكان حفظ الملفات")
            Text("المجلد الحالي: " + (tree?.let { DocumentFile.fromTreeUri(ctx, it)?.name } ?: "لم يتم الاختيار"))
            Button(onClick = { picker.launch(null) }) { Text("اختيار / تغيير المجلد") }
            Text("الملفات الأصلية مبتتغيّرش ولا بتتحذف أبدًا.", style = MaterialTheme.typography.bodySmall)

            Sec("الملفات المؤقتة")
            OutlinedButton(onClick = {
                var n = 0L
                ctx.cacheDir.listFiles()?.filter { it.name.startsWith("job_") }?.forEach { n += it.length(); it.delete() }
                cleaned = "اتمسح ${formatSize(n)}"
            }, enabled = !QueueManager.running) { Text("مسح الملفات المؤقتة") }
            cleaned?.let { Text(it, style = MaterialTheme.typography.bodySmall) }

            Sec("سجل الأخطاء")
            if (logText.isBlank()) Text("مفيش أخطاء مسجّلة.", style = MaterialTheme.typography.bodySmall)
            else {
                Text(logText, style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { clip.setText(AnnotatedString(logText)) }) { Text("نسخ") }
                    OutlinedButton(onClick = { File(ctx.filesDir, "errors.log").delete(); logText = "" }) { Text("مسح") }
                }
            }

            Sec("حدود معروفة")
            Text("• المعالجة بتكمل في الخلفية بخدمة أمامية، لكن لو التطبيق اتقفل بالقوة المهمة الجارية بتبدأ من الأول.\n" +
                "• مفيش تحقق مضمون من جودة التشغيل على كل الأجهزة؛ جرّب الناتج على جهازك.\n" +
                "• الفيديو HDR والـ VFR لسه ملهمش معالجة خاصة.\n" +
                "• قرار التوصية في TikTok بيرجع للمنصة، ومفيش أداة بتضمنه.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun StatusPill(s: JobStatus) {
    val cs = MaterialTheme.colorScheme
    val (bg, fg) = when (s) {
        JobStatus.QUEUED -> cs.surfaceVariant to cs.onSurfaceVariant
        JobStatus.RUNNING -> cs.primaryContainer to cs.onPrimaryContainer
        JobStatus.DONE -> cs.tertiaryContainer to cs.onTertiaryContainer
        JobStatus.FAILED -> cs.errorContainer to cs.onErrorContainer
        JobStatus.CANCELLED -> cs.secondaryContainer to cs.onSecondaryContainer
    }
    Pill(statusText(s), bg, fg)
}
