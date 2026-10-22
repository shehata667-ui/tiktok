package com.tiktokstudio.app

import android.content.Context
import android.net.Uri
import androidx.media3.common.Effect
import androidx.media3.common.util.UnstableApi
import android.graphics.Bitmap
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.Crop
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.OverlaySettings
import androidx.media3.effect.TextureOverlay
import com.google.common.collect.ImmutableList
import androidx.media3.effect.Presentation
import androidx.media3.effect.ScaleAndRotateTransformation
import java.io.File
import java.util.Date
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

enum class Preset(val w: Int, val h: Int, val label: String) { P1080(1080, 1920, "1080×1920"), P720(720, 1280, "720×1280") }
enum class FitMode(val label: String) { FILL("ملء الشاشة (قص)"), FIT("احتواء (أشرطة سوداء)") }
enum class Quality(val label: String) { LOW("منخفضة"), MEDIUM("متوسطة"), HIGH("مرتفعة"), CUSTOM("مخصصة"), TARGET("حجم مستهدف") }

data class ExportSettings(
    val preset: Preset = Preset.P1080,
    val mode: FitMode = FitMode.FILL,
    val cropPos: Float = 0.5f,
    val rotation: Int = 0,
    val quality: Quality = Quality.HIGH,
    val customMbps: Float = 8f,
    val targetMb: Int = 30,
    val fpsCap: Int = 0,            // 0 = نفس معدل المصدر
    val trimStartMs: Long = 0,
    val trimEndMs: Long = 0,
    val mute: Boolean = false,
    val cfrFps: Int = 0,             // > 0 = تثبيت معدل الإطارات على القيمة دي
    val stripMeta: Boolean = true,   // مسح بيانات الموقع من الناتج
)

data class Plan(
    val outW: Int, val outH: Int, val videoBps: Int, val audioBps: Int,
    val estBytes: Long, val durationMs: Long, val notes: List<String>,
)

fun effSize(info: VideoInfo, rot: Int) = if (rot == 90 || rot == 270) info.height to info.width else info.width to info.height

fun makePlan(info: VideoInfo, s: ExportSettings): Plan {
    val (sw, sh) = effSize(info, s.rotation)
    val notes = mutableListOf<String>()
    fun up(p: Preset): Double {
        val a = p.w.toDouble() / sw; val b = p.h.toDouble() / sh
        return if (s.mode == FitMode.FILL) max(a, b) else min(a, b)
    }
    var p = s.preset
    if (p == Preset.P1080 && up(Preset.P1080) > 1.001 && up(Preset.P720) < up(Preset.P1080)) {
        p = Preset.P720
        notes += "دقة الفيديو أقل من 1080، فاستخدمنا 720×1280 لتجنّب التكبير."
    }
    if (up(p) > 1.001) notes += "الفيديو هيتكبّر لمقاس أكبر من الأصلي، وده مش بيحسّن التفاصيل."
    val dur = (s.trimEndMs - s.trimStartMs).coerceAtLeast(1)
    val audio = if (s.mute) 0 else 128_000
    val hi = p == Preset.P1080
    var v = when (s.quality) {
        Quality.LOW -> if (hi) 4e6 else 2.5e6
        Quality.MEDIUM -> if (hi) 8e6 else 5e6
        Quality.HIGH -> if (hi) 12e6 else 8e6
        Quality.CUSTOM -> s.customMbps * 1e6
        Quality.TARGET -> s.targetMb * 8.0 * 1024 * 1024 / (dur / 1000.0) - audio
    }
    // مفيش داعي نعدّي معدل بت المصدر (إلا لو المستخدم حدد يدويًا)
    if (s.quality == Quality.LOW || s.quality == Quality.MEDIUM || s.quality == Quality.HIGH)
        info.bitrateBps?.takeIf { it > 0 }?.let { v = min(v, it.toDouble()) }
    v = v.coerceIn(300_000.0, 40e6)
    val est = ((v + audio) * dur / 1000.0 / 8).toLong()
    return Plan(p.w, p.h, v.toInt(), audio, est, dur, notes)
}

@UnstableApi
fun buildVideoEffects(info: VideoInfo, s: ExportSettings, plan: Plan, overlay: Bitmap? = null): List<Effect> {
    val list = mutableListOf<Effect>()
    if (s.rotation != 0) list += ScaleAndRotateTransformation.Builder().setRotationDegrees(s.rotation.toFloat()).build()
    val (sw, sh) = effSize(info, s.rotation)
    if (s.mode == FitMode.FILL) {
        val src = sw.toFloat() / sh
        val tgt = plan.outW.toFloat() / plan.outH
        if (abs(src - tgt) > 0.001f) {
            if (src > tgt) {            // أعرض من 9:16: قص أفقي
                val f = tgt / src
                val l = -1f + 2f * (1f - f) * s.cropPos
                list += Crop(l, l + 2f * f, -1f, 1f)
            } else {                    // أطول من 9:16: قص رأسي
                val f = src / tgt
                val t = 1f - 2f * (1f - f) * s.cropPos
                list += Crop(-1f, 1f, t - 2f * f, t)
            }
        }
    }
    list += Presentation.createForWidthAndHeight(plan.outW, plan.outH, Presentation.LAYOUT_SCALE_TO_FIT)
    // الطبقة بنفس مقاس الإطار الناتج بالظبط، وبتتحط بعد التحجيم عشان تتدمج في الفيديو نفسه
    if (overlay != null) list += OverlayEffect(ImmutableList.of<TextureOverlay>(
        BitmapOverlay.createStaticBitmapOverlay(overlay, OverlaySettings.Builder().build())))
    return list
}

object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("settings", Context.MODE_PRIVATE)
    fun outTree(c: Context): Uri? = sp(c).getString("out_tree", null)?.let(Uri::parse)
    fun setOutTree(c: Context, u: Uri) = sp(c).edit().putString("out_tree", u.toString()).apply()
}

object AppLog {
    fun e(c: Context, msg: String, t: Throwable? = null) {
        runCatching { File(c.filesDir, "errors.log").appendText("${Date()} $msg ${t?.stackTraceToString() ?: ""}\n") }
    }
}
