package com.mibot.settings

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

/**
 * 助手头像：用户可随时自定义换图（长按顶栏头像 → 相册选图）。
 * 只换图，动画（呼吸/眨眼/口型/状态文字）和语言（TTS/语音）逻辑不变。
 * 自定义图存 filesDir/avatar.png；[version] 变化时组件重载。
 */
object AvatarStore {
    private const val FILE = "avatar.png"

    /** 头像版本号：换图/清除时自增，Compose 里 collectAsState 触发重组。 */
    val version = MutableStateFlow(0)

    fun file(ctx: Context) = File(ctx.filesDir, FILE)

    fun has(ctx: Context) = file(ctx).exists()

    /** 从相册 URI 读取 → 主体放大裁剪 → 存 filesDir/avatar.png；成功返回 true 并 bump 版本。 */
    fun set(ctx: Context, uri: Uri): Boolean {
        val ok = runCatching {
            val bmp = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                ?: return false
            val cropped = subjectCrop(bmp)
            file(ctx).outputStream().use { cropped.compress(Bitmap.CompressFormat.PNG, 100, it) }
            true
        }.getOrDefault(false)
        if (ok) version.value++
        return ok
    }

    /** 恢复默认头像（删自定义图）。 */
    fun clear(ctx: Context) {
        file(ctx).delete()
        version.value++
    }

    /**
     * 主体放大裁剪：检测与四角背景色差异大的区域（主体），裁到主体+边距并方形化。
     * 让任何换的图都「圆圈铺满」——主体填满圆，不留四周背景边。
     */
    private fun subjectCrop(bmp: Bitmap): Bitmap {
        val w = bmp.width; val h = bmp.height
        if (w < 40 || h < 40) return bmp
        fun px(cx: Int, cy: Int): Int = bmp.getPixel(cx * (w - 1) / 100, cy * (h - 1) / 100)
        val cs = listOf(px(2, 2), px(98, 2), px(2, 98), px(98, 98))
        val br = cs.map { android.graphics.Color.red(it) }.average().toInt()
        val bg = cs.map { android.graphics.Color.green(it) }.average().toInt()
        val bb = cs.map { android.graphics.Color.blue(it) }.average().toInt()
        var x0 = w; var y0 = h; var x1 = 0; var y1 = 0
        for (y in 0 until h step 2) for (x in 0 until w step 2) {
            val p = bmp.getPixel(x, y)
            val d = Math.abs(android.graphics.Color.red(p) - br) +
                Math.abs(android.graphics.Color.green(p) - bg) +
                Math.abs(android.graphics.Color.blue(p) - bb)
            if (d > 80) {
                if (x < x0) x0 = x; if (x > x1) x1 = x
                if (y < y0) y0 = y; if (y > y1) y1 = y
            }
        }
        // 保护：检测失败或主体几乎占满 → 原图
        if (x1 <= x0 || y1 <= y0) return bmp
        if ((x1 - x0) > w * 0.92f || (y1 - y0) > h * 0.92f) return bmp
        val mx = ((x1 - x0) * 0.05f).toInt(); val my = ((y1 - y0) * 0.05f).toInt()
        var sx0 = maxOf(0, x0 - mx); var sy0 = maxOf(0, y0 - my)
        var sx1 = minOf(w, x1 + mx); var sy1 = minOf(h, y1 + my)
        val side = maxOf(sx1 - sx0, sy1 - sy0)
        val cx = (sx0 + sx1) / 2; val cy = (sy0 + sy1) / 2
        sx0 = maxOf(0, minOf(w - side, cx - side / 2)); sy0 = maxOf(0, minOf(h - side, cy - side / 2))
        return Bitmap.createBitmap(bmp, sx0, sy0, side, side)
    }
}
