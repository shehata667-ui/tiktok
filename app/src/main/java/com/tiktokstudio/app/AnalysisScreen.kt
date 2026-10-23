package com.tiktokstudio.app

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalysisScreen(info: VideoInfo, onBack: () -> Unit, onEdit: () -> Unit) {
    val ctx = LocalContext.current
    val a by produceState<Analysis?>(AnalysisCache.get(info.uri), info.uri) {
        if (value == null) {
            value = withContext(Dispatchers.IO) { runCatching { analyzeVideo(ctx, info) }.onFailure { AppLog.e(ctx, "analyze failed", it) }.getOrNull() }
            value?.let { AnalysisCache.put(info.uri, it) }
        }
    }
    var done by remember { mutableStateOf(false) }
    LaunchedEffect(a) { if (a != null) done = true }
    val err = MaterialTheme.colorScheme.error
    Scaffold(topBar = {
        TopAppBar(title = { Text("فحص: ${info.name}", maxLines = 1) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") } })
    }) { pad ->
        val r = a
        if (r == null) {
            Column(Modifier.padding(pad).fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text(if (done) "تعذّر فحص الفيديو. شوف سجل الأخطاء في الإعدادات." else "بيتحلل الفيديو... ممكن ياخد ثواني للفيديوهات الطويلة.", textAlign = TextAlign.Center)
            }
        } else Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            r.frame?.let { fr ->
                val shown = remember(r) { drawBoxes(fr, r.overlays, r.qr) }
                Image(shown.asImageBitmap(), "إطار من الفيديو", Modifier.fillMaxWidth().heightIn(max = 300.dp), contentScale = ContentScale.Fit)
                Text("الأحمر: موضع محتمل لشعار أو علامة (تقديري) • الأزرق: رمز QR", style = MaterialTheme.typography.bodySmall)
            }
            Sec("المعلومات الفنية")
            val i = r.info
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("الدقة: ${i.resolution} (${if (i.isPortrait) "عمودي" else "أفقي"}) • النسبة ${String.format(Locale.US, "%.2f", i.width.toFloat() / i.height)}")
                    Text("الترميز: ${r.codec}${if (r.hdr) " • HDR" else ""} • الصيغة ${i.mime ?: "-"}")
                    Text("المدة: ${i.durationText} • الحجم: ${i.sizeText}")
                    Text("معدل البت: " + (i.bitrateBps?.let { String.format(Locale.US, "%.1f Mbps", it / 1e6) } ?: "غير معروف") + " • الصوت: ${if (i.hasAudioGuess) "موجود" else "غير موجود"}")
                    Text("معدل الإطارات المعلن: ${i.fpsText} • التدوير: ${i.rotation}°")
                }
            }
            r.timing?.let { t ->
                Sec("انتظام الإطارات")
                AppCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(String.format(Locale.US, "عدد الإطارات: %d • المتوسط %.2f fps • الوسيط %.2f fps", t.frames, t.avgFps, t.medianFps))
                        Text(String.format(Locale.US, "أقصر فاصل %.1f ms • أطول فاصل %.1f ms • غير منتظم %.1f%%", t.minIntervalMs, t.maxIntervalMs, t.irregularPct))
                        Text(if (t.isVfr) "النوع: VFR (متغير)" else "النوع: قريب من CFR (ثابت)")
                    }
                }
            }
            if (r.meta.isNotEmpty()) {
                Sec("البيانات الوصفية")
                AppCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        r.meta.forEach { (k, v) -> Text("$k: $v", style = MaterialTheme.typography.bodySmall, color = if (k.startsWith("الموقع")) err else MaterialTheme.colorScheme.onSurface) }
                    }
                }
            }

            Sec("التقرير: المشكلة • الإمكانية • الإجراء")
            r.findings.forEach { f ->
                AppCard(Modifier.fillMaxWidth(), border = BorderStroke(1.dp, when (f.level) {
                    Level.WARN -> err.copy(alpha = 0.55f); Level.OK -> MaterialTheme.colorScheme.tertiary; else -> MaterialTheme.colorScheme.outlineVariant })) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        val mark = when (f.level) { Level.OK -> "✓"; Level.INFO -> "ℹ"; Level.WARN -> "⚠" }
                        Text("$mark ${f.title}", style = MaterialTheme.typography.titleSmall, color = if (f.level == Level.WARN) err else MaterialTheme.colorScheme.onSurface)
                        Text(f.detail, style = MaterialTheme.typography.bodySmall)
                        if (f.fix != Fix.NONE) Text("التصنيف: ${f.fix.label}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        Text("الإجراء المقترح: ${f.action}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Text("الفحص البصري (الشعارات، الأشرطة، QR) تقديري من 5 إلى 8 إطارات بس، وممكن يفوّت حاجات أو يغلط.", style = MaterialTheme.typography.bodySmall)
            Button(onClick = onEdit, Modifier.fillMaxWidth()) { Text("تجهيز الفيديو") }
            OriginalityCard(r)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun OriginalityCard(a: Analysis) {
    val ctx = LocalContext.current
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("مساعد الأصالة وأهلية النشر", style = MaterialTheme.typography.titleMedium)
            Text("ده إرشاد مبني على الفحص الفني بس. قرار التوصية بيرجع لـ TikTok، ومفيش أداة تقدر تعرفه من الملف. التطبيق مبيضمنش إن الفيديو «مؤهل للتوصية».",
                color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            Text("أفكار تضيف قيمة حقيقية:")
            Text("• تعليق صوتي أصلي بصوتك\n• شرح أو تحليل خاص بيك للمحتوى\n• معلومات مفيدة أو سياق جديد\n• مقدمة وخاتمة أصليتين\n• راجع حقوق استخدام الفيديو والموسيقى قبل النشر", style = MaterialTheme.typography.bodyMedium)
            if (a.overlays.isNotEmpty() || !a.qr.isNullOrEmpty())
                Text("الفحص لقى عناصر ممكن تخص مصدر تاني (شعار أو QR). التطبيق مبيشيلها ومبيضيفش تشويش أو تغييرات شكلية للتحايل على اكتشاف إعادة النشر. لو الفيديو مش بتاعك، خد إذن صاحبه أو استخدم محتواك.",
                    style = MaterialTheme.typography.bodySmall)
            Text("المتطلبات بتتغير، فراجعها من المصدر الرسمي (محتاج إنترنت).", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = {
                runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.tiktok.com/community-guidelines")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }) { Text("إرشادات TikTok الرسمية") }
        }
    }
}

private data class Side(val info: VideoInfo?, val timing: TimingStats?, val thumb: Bitmap?, val loc: Boolean)

private fun sideOf(ctx: Context, uri: Uri): Side {
    val info = runCatching { readVideoInfo(ctx, uri) }.getOrNull() ?: return Side(null, null, null, false)
    val timing = runCatching { timingStats(ctx, uri) }.getOrNull()
    var thumb: Bitmap? = null; var loc = false
    val r = MediaMetadataRetriever()
    try {
        r.setDataSource(ctx, uri)
        loc = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION) != null
        val h = (360f * info.height / info.width).toInt().coerceAtLeast(1)
        thumb = r.getScaledFrameAtTime(minOf(1_000_000L, info.durationMs * 500), MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 360, h)
    } catch (_: Exception) {} finally { r.release() }
    return Side(info, timing, thumb, loc)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompareScreen(job: ExportJob, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val data by produceState<Pair<Side, Side>?>(null, job.id) {
        value = withContext(Dispatchers.IO) { sideOf(ctx, Uri.parse(job.srcUri)) to sideOf(ctx, Uri.parse(job.outUri ?: "")) }
    }
    Scaffold(topBar = {
        TopAppBar(title = { Text("مقارنة: ${job.name}", maxLines = 1) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") } })
    }) { pad ->
        val d = data
        if (d == null) { Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }; return@Scaffold }
        val (s, o) = d
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("الأصل" to s, "الناتج" to o).forEach { (label, side) ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(label, style = MaterialTheme.typography.titleSmall)
                        side.thumb?.let { Image(it.asImageBitmap(), label, Modifier.fillMaxWidth().height(220.dp), contentScale = ContentScale.Fit) }
                            ?: Text("غير متاح", Modifier.height(220.dp))
                    }
                }
            }
            fun v(x: Side, f: (VideoInfo) -> String) = x.info?.let(f) ?: "غير متاح"
            CmpRow("", "الأصل", "الناتج")
            CmpRow("الدقة", v(s) { it.resolution }, v(o) { it.resolution })
            CmpRow("المدة", v(s) { it.durationText }, v(o) { it.durationText })
            CmpRow("الحجم", v(s) { it.sizeText }, v(o) { it.sizeText })
            CmpRow("معدل البت", v(s) { i -> i.bitrateBps?.let { String.format(Locale.US, "%.1f Mbps", it / 1e6) } ?: "-" },
                v(o) { i -> i.bitrateBps?.let { String.format(Locale.US, "%.1f Mbps", it / 1e6) } ?: "-" })
            CmpRow("FPS (مقاس)", s.timing?.let { String.format(Locale.US, "%.2f", it.avgFps) } ?: "-", o.timing?.let { String.format(Locale.US, "%.2f", it.avgFps) } ?: "-")
            CmpRow("الإطارات", s.timing?.frames?.toString() ?: "-", o.timing?.frames?.toString() ?: "-")
            CmpRow("النوع", s.timing?.let { if (it.isVfr) "VFR" else "CFR" } ?: "-", o.timing?.let { if (it.isVfr) "VFR" else "CFR" } ?: "-")
            CmpRow("الترميز", v(s) { codecName(it.mime) }, v(o) { codecName(it.mime) })
            CmpRow("الموقع", if (s.loc) "موجود" else "لا", if (o.loc) "موجود" else "لا")
            Text("المقارنة معلوماتية. عدد الإطارات بيختلف لو قصّيت أو خفّضت الـ FPS. التقطيع الحقيقي بيتأكد منه بالمشاهدة على جهازك.", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { openVideo(ctx, Uri.parse(job.srcUri)) }) { Text("تشغيل الأصل") }
                job.outUri?.let { u -> Button(onClick = { openVideo(ctx, Uri.parse(u)) }) { Text("تشغيل الناتج") } }
            }
        }
    }
}

@Composable
private fun CmpRow(label: String, a: String, b: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(0.8f), style = MaterialTheme.typography.bodySmall)
        Text(a, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
        Text(b, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
    }
}
