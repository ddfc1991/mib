package com.openminis.app.mibot.settings

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.exifinterface.media.ExifInterface
import com.openminis.app.mibot.design.ChipKind
import com.openminis.app.mibot.design.MibotButton
import com.openminis.app.mibot.design.MibotCard
import com.openminis.app.mibot.design.MibotFootnote
import com.openminis.app.mibot.design.MibotRow
import com.openminis.app.mibot.design.MibotScaffold
import com.openminis.app.mibot.design.MibotSectionHeader
import com.openminis.app.mibot.design.MibotTokens
import com.openminis.app.mibot.design.StatusChip
import com.openminis.app.mibot.design.t
import com.openminis.app.mibot.design.tc
import com.openminis.app.ui.glass.BackdropMode
import com.openminis.app.ui.glass.GlassPrefs
import com.openminis.app.ui.settings.KEY_THEME_MODE
import com.openminis.app.ui.settings.PREF_APPEARANCE
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * [fork/mibot-v2] 背景与主题: theme mode (system / light cloud-sky / dark
 * night), backdrop source (living sky / photo / solid colour), photo veil,
 * and the master switch for the Mibot interface. Replaces the separate
 * "背景与毛玻璃" page and the theme picker as the one place for both.
 */
@Composable
fun BackgroundThemeScreen(onBack: () -> Unit, onMoreAppearance: () -> Unit) {
    val ctx = LocalContext.current
    val settings by remember(ctx) { GlassPrefs.flow(ctx) }.collectAsState()
    val prefs = remember { ctx.getSharedPreferences(PREF_APPEARANCE, Context.MODE_PRIVATE) }
    var themeMode by remember { mutableIntStateOf(prefs.getInt(KEY_THEME_MODE, 0)) }
    var photoError by remember { mutableStateOf<String?>(null) }
    var importing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun importUri(uri: Uri) {
        importing = true
        scope.launch {
            val r = withContext(Dispatchers.IO) { MibotPhotoImport.import(ctx, uri) }
            importing = false
            if (r.path != null) {
                photoError = null
                GlassPrefs.update(ctx) { it.copy(mode = BackdropMode.PHOTO, photoPath = r.path) }
            } else {
                photoError = r.error
                Toast.makeText(ctx, tc(ctx, "无法加载图片：", "Couldn't load the image: ") + r.error, Toast.LENGTH_LONG).show()
            }
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) importUri(uri) }
    val docPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) importUri(uri) }
    fun pick() {
        runCatching { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
            .onFailure { docPicker.launch(arrayOf("image/*")) }
    }

    MibotScaffold(title = t("背景与主题", "Background & theme"), onBack = onBack) {
        MibotSectionHeader(t("主题", "Theme"))
        MibotCard {
            listOf(
                0 to (t("跟随系统", "Follow system") to t("白天云天，夜里星空", "Cloud sky by day, night sky by night")),
                1 to (t("浅色 · 云天", "Light · cloud sky") to t("浅色雾天背景，砖红强调色", "Light misty sky, brick-red accent")),
                2 to (t("深色 · 夜空", "Dark · night sky") to t("星空与云层，柔和橙红强调色", "Stars and clouds, soft salmon accent")),
            ).forEachIndexed { i, (mode, txt) ->
                MibotRow(
                    title = txt.first, subtitle = txt.second, chevron = false, showDivider = i < 2,
                    onClick = {
                        themeMode = mode
                        prefs.edit().putInt(KEY_THEME_MODE, mode).apply()
                    },
                    trailing = { if (themeMode == mode) StatusChip("✓", ChipKind.ACCENT) },
                )
            }
        }

        MibotSectionHeader(t("背景", "Background"))
        MibotCard {
            MibotRow(
                t("动态天空", "Living sky"),
                t("缓慢飘动的云雾；天气只影响色调。后台、熄屏、省电或关闭动画时暂停", "Slow drifting clouds and fog; weather only tints it. Pauses in background, screen off, battery saver or with animations off"),
                chevron = false, showDivider = true,
                onClick = { GlassPrefs.update(ctx) { it.copy(mode = BackdropMode.AMBIENT) } },
                trailing = { if (settings.mode == BackdropMode.AMBIENT) StatusChip("✓", ChipKind.ACCENT) },
            )
            MibotRow(
                t("照片", "Photo"),
                if (settings.photoPath != null) t("已选择 · 点此更换", "Chosen · tap to change") else t("从相册选择", "Pick from gallery"),
                chevron = false, showDivider = true,
                onClick = { if (settings.photoPath == null || settings.mode == BackdropMode.PHOTO) pick() else GlassPrefs.update(ctx) { it.copy(mode = BackdropMode.PHOTO) } },
                trailing = {
                    if (importing) StatusChip(t("处理中", "Working"), ChipKind.WARN)
                    else if (settings.mode == BackdropMode.PHOTO) StatusChip("✓", ChipKind.ACCENT)
                },
            )
            MibotRow(
                t("纯色", "Solid colour"), null, chevron = false,
                onClick = { GlassPrefs.update(ctx) { it.copy(mode = BackdropMode.PLAIN) } },
                trailing = { if (settings.mode == BackdropMode.PLAIN) StatusChip("✓", ChipKind.ACCENT) },
            )
        }
        photoError?.let {
            Text(t("无法加载图片：", "Couldn't load the image: ") + it, color = MaterialTheme.colorScheme.error, fontSize = com.openminis.app.mibot.design.MibotType.Secondary,
                modifier = Modifier.padding(start = 28.dp, end = 28.dp, top = 8.dp))
        }
        if (settings.mode == BackdropMode.PHOTO) {
            MibotSectionHeader(t("照片遮罩", "Photo veil"))
            MibotCard(padding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                Slider(value = settings.dim, onValueChange = { v -> GlassPrefs.update(ctx) { it.copy(dim = v) } }, valueRange = 0.15f..0.9f)
                Text(t("越高文字越清楚", "Higher = more legible text"), fontSize = com.openminis.app.mibot.design.MibotType.Secondary, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            MibotCard(Modifier.padding(top = 8.dp)) {
                MibotRow(
                    t("照片上叠加云雾", "Clouds over the photo"),
                    t("在照片上缓慢飘过一层柔和云雾", "A soft drifting cloud layer over your photo"),
                    chevron = false,
                    trailing = { Switch(checked = settings.fogOverPhoto, onCheckedChange = { on -> GlassPrefs.update(ctx) { it.copy(fogOverPhoto = on) } }) },
                )
            }
            Row(Modifier.padding(16.dp)) { MibotButton(t("更换照片", "Change photo"), tonal = true, onClick = { pick() }) }
        }
        if (settings.mode == BackdropMode.PLAIN) {
            MibotSectionHeader(t("颜色", "Colour"))
            val swatches = listOf(0, Color(0xFFEEF1F5).toArgb(), Color(0xFFF6EFE7).toArgb(), Color(0xFFE6EEF2).toArgb(),
                Color(0xFF0E121C).toArgb(), Color(0xFF1B1F2A).toArgb(), Color(0xFF2A1E1A).toArgb())
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                swatches.forEach { argb ->
                    val c = if (argb == 0) MibotTokens.accent().copy(alpha = 0.25f) else Color(argb)
                    Box(
                        Modifier.size(32.dp).clip(CircleShape).background(c)
                            .border(if (settings.solidColor == argb) 3.dp else 1.dp, if (settings.solidColor == argb) MibotTokens.accent() else MibotTokens.hairline(), CircleShape)
                            .clickable { GlassPrefs.update(ctx) { it.copy(solidColor = argb) } },
                    )
                }
            }
            MibotFootnote(t("第一个 = 跟随主题的默认底色。", "The first one follows the theme."))
        }

        MibotSectionHeader(t("界面", "Interface"))
        MibotCard {
            MibotRow(
                t("Mibot 界面", "Mibot interface"),
                t("统一设计、共享背景、底部标签栏。关闭则恢复原版布局", "Unified design, shared backdrop, bottom tabs. Off restores the stock layout"),
                chevron = false, showDivider = true,
                trailing = { Switch(checked = settings.enabled, onCheckedChange = { on -> GlassPrefs.update(ctx) { it.copy(enabled = on) } }) },
            )
            MibotRow(t("字体、语言与显示", "Fonts, language & display"), null, onClick = onMoreAppearance)
        }
        Box(Modifier.height(16.dp))
    }
}

/**
 * [fork/mibot-v2] Robust background-photo import. The picked image is
 * decoded straight from the content URI (no long-lived URI permission
 * needed), downsampled to ~screen size to avoid OOM on 50-200 MP photos,
 * EXIF-rotated, and saved as an app-private JPEG. ImageDecoder (API 28+)
 * handles HEIC/HEIF/AVIF/WebP and applies orientation itself; older devices
 * use BitmapFactory with inSampleSize. Failures return a readable reason.
 */
object MibotPhotoImport {
    class Result(val path: String?, val error: String?)

    fun import(ctx: Context, uri: Uri): Result {
        val dm = ctx.resources.displayMetrics
        val target = (maxOf(dm.widthPixels, dm.heightPixels) * 1.15f).toInt().coerceIn(1280, 2560)
        val decoded: Bitmap? = try {
            decode(ctx, uri, target)
        } catch (e: OutOfMemoryError) {
            return Result(null, tc(ctx, "图片太大（内存不足）", "image too large (out of memory)"))
        } catch (e: SecurityException) {
            return Result(null, tc(ctx, "没有读取权限，请换用相册选择", "no permission to read it — pick from the gallery"))
        } catch (e: Exception) {
            return Result(null, e.message ?: e.javaClass.simpleName)
        }
        val bitmap = decoded ?: return Result(null, tc(ctx, "不支持的图片格式", "unsupported image format"))
        return try {
            val dir = File(ctx.filesDir, "glass").apply { mkdirs() }
            val out = File(dir, "backdrop_${System.currentTimeMillis()}.jpg")
            out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            bitmap.recycle()
            if (out.length() == 0L) return Result(null, tc(ctx, "保存失败", "couldn't save"))
            dir.listFiles()?.filter { it != out }?.forEach { it.delete() }
            Result(out.absolutePath, null)
        } catch (e: Exception) {
            Result(null, e.message ?: "save failed")
        }
    }

    private fun decode(ctx: Context, uri: Uri, target: Int): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val src = ImageDecoder.createSource(ctx.contentResolver, uri)
            return ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
                val w = info.size.width
                val h = info.size.height
                val scale = minOf(1f, target.toFloat() / maxOf(w, h).coerceAtLeast(1))
                if (scale < 1f) decoder.setTargetSize((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1))
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = false
            }
        }
        val resolver = ctx.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= target) sample *= 2
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
        val rotation = runCatching {
            resolver.openInputStream(uri)?.use {
                when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            }
        }.getOrNull() ?: 0f
        return if (rotation == 0f) decoded
        else Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, Matrix().apply { postRotate(rotation) }, true)
    }
}
