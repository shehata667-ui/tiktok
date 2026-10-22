package com.tiktokstudio.app

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(info: VideoInfo, batch: List<VideoInfo>, onBack: () -> Unit, onQueued: () -> Unit) {
    val ctx = LocalContext.current
    var s by remember(info.uri) { mutableStateOf(ExportSettings(trimEndMs = info.durationMs.coerceAtLeast(1))) }
    var tree by remember { mutableStateOf(Prefs.outTree(ctx)) }
    var wm by remember { mutableStateOf(WmStore.last(ctx)) }
    var showWm by remember { mutableStateOf(false) }
    var added by remember { mutableStateOf<String?>(null) }
    val plan = remember(s, info) { makePlan(info, s) }
    val others = batch.filter { it.uri != info.uri }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { u ->
        if (u != null) {
            ctx.contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            Prefs.setOutTree(ctx, u); tree = u
        }
    }
    fun enqueue(items: List<VideoInfo>, startNow: Boolean) {
        val t = tree ?: run { picker.launch(null); return }
        items.forEach { v ->
            // الفيديوهات التانية بتاخد نفس الإعدادات لكن بمدتها الكاملة (القص الزمني خاص بالفيديو ده بس)
            val sv = if (v.uri == info.uri) s else s.copy(trimStartMs = 0, trimEndMs = v.durationMs.coerceAtLeast(1))
            QueueManager.enqueue(v, sv, wm, t)
        }
        if (startNow) { ExportService.start(ctx); onQueued() } else added = "اتضاف ${items.size} للطابور ✓ (الإجمالي في الطابور: ${QueueManager.jobs.count { it.status == JobStatus.QUEUED }})"
    }

    BackHandler(enabled = showWm) { showWm = false }
    if (showWm) WatermarkScreen(info, s, wm, { wm = it; WmStore.saveLast(ctx, it) }, { showWm = false }) else Scaffold(topBar = {
        TopAppBar(
            title = { Text("تجهيز: ${info.name}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") } })
    }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("الأصل: ${info.resolution} • ${info.fpsText} • ${info.durationText} • ${info.sizeText}", style = MaterialTheme.typography.bodySmall)

            Sec("المقاس")
            Chips(Preset.entries, s.preset, { it.label }, true) { s = s.copy(preset = it) }
            Chips(FitMode.entries, s.mode, { it.label }, true) { s = s.copy(mode = it) }
            if (s.mode == FitMode.FILL) {
                Text("موضع القص", style = MaterialTheme.typography.bodySmall)
                Slider(value = s.cropPos, onValueChange = { s = s.copy(cropPos = it) })
            }
            OutlinedButton(onClick = { s = s.copy(rotation = (s.rotation + 90) % 360) }) {
                Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(6.dp)); Text("تدوير (${s.rotation}°)")
            }

            Sec("الجودة")
            Chips(Quality.entries, s.quality, { it.label }, true) { s = s.copy(quality = it) }
            if (s.quality == Quality.CUSTOM) {
                Text(String.format(Locale.US, "معدل بت الفيديو: %.1f Mbps", s.customMbps))
                Slider(value = s.customMbps, onValueChange = { s = s.copy(customMbps = it) }, valueRange = 1f..20f)
            }
            if (s.quality == Quality.TARGET) {
                Text("الحجم المستهدف: ${s.targetMb} MB (تقريبي)")
                Slider(value = s.targetMb.toFloat(), onValueChange = { s = s.copy(targetMb = it.toInt()) }, valueRange = 5f..500f)
            }
            val f = info.fps
            val fpsOptions = buildList { add(0); if (f != null && f > 31) add(30); if (f != null && f > 61) add(60) }
            if (fpsOptions.size > 1) {
                Text("معدل الإطارات (تخفيض فقط، مفيش تكرار إطارات)", style = MaterialTheme.typography.bodySmall)
                Chips(fpsOptions, s.fpsCap, { if (it == 0) "الأصلي" else "$it fps" }, true) { s = s.copy(fpsCap = it) }
            }

            Sec("القص الزمني")
            val total = info.durationMs.coerceAtLeast(1).toFloat()
            RangeSlider(
                value = s.trimStartMs.toFloat()..s.trimEndMs.toFloat(),
                onValueChange = { r ->
                    val a = r.start.toLong(); val b = r.endInclusive.toLong()
                    if (b - a >= 500) s = s.copy(trimStartMs = a, trimEndMs = b)
                },
                valueRange = 0f..total)
            Text("من ${formatDuration(s.trimStartMs)} إلى ${formatDuration(s.trimEndMs)}")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = s.mute, onCheckedChange = { s = s.copy(mute = it) })
                Spacer(Modifier.width(8.dp)); Text("كتم الصوت")
            }
            val ana = AnalysisCache.get(info.uri)
            val cfrTarget = Math.round(ana?.timing?.medianFps ?: info.fps ?: 30f).coerceIn(15, 60)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = s.cfrFps > 0, onCheckedChange = { s = s.copy(cfrFps = if (it) cfrTarget else 0) })
                Spacer(Modifier.width(8.dp)); Text("تثبيت معدل الإطارات CFR ($cfrTarget fps)")
            }
            if (ana?.timing?.isVfr == true) Text("الفحص لقى إن المصدر VFR؛ يُنصح بتفعيل CFR لتقليل احتمال التقطيع.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            Text("CFR بيسقط إطارات للوصول للمعدل ولا بيكرر إطارات، وممكن يغيّر الحجم شوية. التقرير بعد التصدير بيوضح النتيجة الفعلية.", style = MaterialTheme.typography.bodySmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = s.stripMeta, onCheckedChange = { s = s.copy(stripMeta = it) })
                Spacer(Modifier.width(8.dp)); Text("مسح بيانات الموقع من الناتج")
            }

            Sec("النص والعلامة المائية")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = wm.enabled, onCheckedChange = { wm = wm.copy(enabled = it); WmStore.saveLast(ctx, wm) })
                Spacer(Modifier.width(8.dp))
                Text(if (wm.hasContent) "هتتدمج في الفيديو" else "غير مفعّلة أو مفيش محتوى")
            }
            OutlinedButton(onClick = { showWm = true }) { Text("تعديل النص والشعار") }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("الناتج المتوقع", style = MaterialTheme.typography.titleSmall)
                    Text(String.format(Locale.US, "%d×%d • فيديو %.1f Mbps • الحجم التقريبي %s (الأصل %s)",
                        plan.outW, plan.outH, plan.videoBps / 1e6, formatSize(plan.estBytes), info.sizeText))
                    plan.notes.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                    Text("الترميز: H.264 + AAC. الحجم النهائي تقريبي.", style = MaterialTheme.typography.bodySmall)
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                val name = tree?.let { DocumentFile.fromTreeUri(ctx, it)?.name } ?: "لم يتم الاختيار"
                Text("مجلد الحفظ: $name", Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                TextButton(onClick = { picker.launch(null) }) { Text(if (tree == null) "اختيار" else "تغيير") }
            }

            val valid = s.trimEndMs - s.trimStartMs >= 500
            Button(onClick = { enqueue(listOf(info), true) }, enabled = valid, modifier = Modifier.fillMaxWidth()) {
                Text(if (tree == null) "اختر مجلد الحفظ" else "تصدير الآن")
            }
            OutlinedButton(onClick = { enqueue(listOf(info), false) }, enabled = valid && tree != null, modifier = Modifier.fillMaxWidth()) {
                Text("إضافة للطابور فقط")
            }
            if (others.isNotEmpty()) {
                OutlinedButton(onClick = { enqueue(listOf(info) + others, true) }, enabled = valid && tree != null, modifier = Modifier.fillMaxWidth()) {
                    Text("معالجة كل المحدد (${others.size + 1}) بنفس الإعدادات")
                }
                Text("الإعدادات (المقاس والجودة والعلامة) بتتطبق على كل فيديو، والقص الزمني للفيديو ده بس.", style = MaterialTheme.typography.bodySmall)
            }
            added?.let { Text(it) }
            Spacer(Modifier.height(24.dp))
        }
    }
}

fun openVideo(ctx: android.content.Context, uri: Uri) = runCatching {
    ctx.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "video/mp4")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
}

fun shareVideo(ctx: android.content.Context, uri: Uri) = runCatching {
    val i = Intent(Intent.ACTION_SEND).setType("video/mp4").putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    ctx.startActivity(Intent.createChooser(i, "مشاركة").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

@Composable
fun Sec(t: String) = Text(t, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> Chips(items: List<T>, selected: T, label: (T) -> String, enabled: Boolean, onSelect: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { FilterChip(selected = it == selected, onClick = { onSelect(it) }, label = { Text(label(it)) }, enabled = enabled) }
    }
}
