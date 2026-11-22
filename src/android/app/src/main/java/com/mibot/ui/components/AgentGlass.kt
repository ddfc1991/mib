package com.mibot.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.sp
import com.mibot.ui.theme.MikaTheme

/**
 * Solid Glass 智能体组件（对话屏用）：
 * - [StatusGlyph]：状态徽标，三点，运行中第一点绿色脉冲
 * - [ToolSteps]：工具步骤，时间线，每步标题+说明+状态
 * - [ContextRing]：上下文环，Canvas 画弧 + 百分比
 * - [GlassChip]：玻璃胶囊标签
 * 颜色一律走 [MikaTheme.colors]，不写死。
 */
@Composable
fun StatusGlyph(active: Boolean = true, modifier: Modifier = Modifier) {
    val c = MikaTheme.colors
    val infinite = rememberInfiniteTransition()
    val alpha by infinite.animateFloat(
        initialValue = 1f, targetValue = 0.3f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
    )
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(3) { i ->
            Box(
                Modifier.size(4.dp).background(
                    if (active && i == 0) c.ok.copy(alpha = alpha) else c.ink3.copy(alpha = 0.7f),
                    CircleShape,
                ),
            )
        }
    }
}

/** 一步工具：标题、说明、完成/进行中。 */
data class ToolStep(val title: String, val note: String, val done: Boolean, val running: Boolean = false)

@Composable
fun ToolSteps(steps: List<ToolStep>, modifier: Modifier = Modifier) {
    val c = MikaTheme.colors
    Column(modifier.fillMaxWidth().background(c.answer, RoundedCornerShape(14.dp)).padding(12.dp)) {
        Text("工具步骤", color = c.ink2, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        steps.forEach { s ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
                Box(Modifier.width(20.dp).padding(top = 4.dp)) {
                    Box(
                        Modifier.size(6.dp).align(Alignment.Center).background(
                            when { s.running -> c.ok; s.done -> c.accent; else -> c.ink3 },
                            CircleShape,
                        ),
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(s.title, color = c.ink, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Text(s.note, color = c.ink3, fontSize = 10.sp)
                }
                when {
                    s.running -> Text("进行中", color = c.ok, fontSize = 10.sp)
                    s.done -> Text("完成", color = c.ok, fontSize = 10.sp)
                }
            }
        }
    }
}

/** 上下文环：Canvas 画弧 + 百分比，[percent] 0..1。 */
@Composable
fun ContextRing(percent: Float, label: String, modifier: Modifier = Modifier) {
    val c = MikaTheme.colors
    Box(modifier.size(56.dp)) {
        Canvas(Modifier.matchParentSize()) {
            val stroke = 4.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(
                c.hair, 0f, 360f, false,
                topLeft = Offset(inset, inset), size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
            drawArc(
                c.ok, -90f, 360f * percent.coerceIn(0f, 1f), false,
                topLeft = Offset(inset, inset), size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${(percent * 100).toInt()}%", color = c.ink, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Text(label, color = c.ink3, fontSize = 8.sp)
        }
    }
}

/** 玻璃胶囊标签：[tint] 给底色/描边，不给就用中性。 */
@Composable
fun GlassChip(text: String, tint: Color? = null, modifier: Modifier = Modifier) {
    val c = MikaTheme.colors
    val bg = tint?.copy(alpha = 0.16f) ?: c.fill
    val fg = tint ?: c.ink2
    val edge = tint?.copy(alpha = 0.25f) ?: c.edge
    Text(
        text,
        modifier.background(bg, RoundedCornerShape(8.dp))
            .border(1.dp, edge, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        color = fg, fontSize = 10.sp, fontWeight = FontWeight.Medium,
    )
}

/**
 * 动态猫娘头像：会动（呼吸/眨眼/说话口型）+ 会发声（说话时口型动，配 TTS/语音）。
 * [speaking] 说话中（口型快速开合 + 声波环）；[blink] 眨眼帧。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CatGirlAvatar(
    speaking: Boolean,
    modifier: Modifier = Modifier,
    size: Int = 64,
    onLongClick: (() -> Unit)? = null,
    /** 状态文字（思考中/处理中/说话中…），显示在头像圆圈下方；null=不显示。 */
    status: String? = null,
) {
    val c = MikaTheme.colors
    val infinite = rememberInfiniteTransition()
    val context = LocalContext.current
    val ver by com.mibot.settings.AvatarStore.version.collectAsState()
    // 自定义头像（用户换过图）优先；否则默认 5934 绿眼猫娘
    val custom = remember(ver) {
        if (com.mibot.settings.AvatarStore.has(context)) {
            android.graphics.BitmapFactory.decodeFile(com.mibot.settings.AvatarStore.file(context).absolutePath)
        } else null
    }
    // 呼吸：整体明显缩放
    val breathe by infinite.animateFloat(
        initialValue = 1f, targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(2000), RepeatMode.Reverse),
    )
    // 上下浮动（悬浮感）
    val floatY by infinite.animateFloat(
        initialValue = -1.5f, targetValue = 1.5f,
        animationSpec = infiniteRepeatable(tween(2400), RepeatMode.Reverse),
    )
    // 眨眼：每 3.6s 一次快速压扁（模拟闭眼，任何头像通用）
    val blinkY by infinite.animateFloat(
        initialValue = 1f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            keyframes { durationMillis = 3600; 1f at 0; 0.93f at 3300; 1f at 3600 }
        ),
    )
    // 口型：说话时快速小幅压扁（模拟嘴动）
    val mouthY by infinite.animateFloat(
        initialValue = 1f, targetValue = 0.95f,
        animationSpec = infiniteRepeatable(tween(160), RepeatMode.Reverse),
    )
    val effMouth = if (speaking) mouthY else 1f
    Column(
        modifier.width(size.dp).then(
            if (onLongClick != null) Modifier.combinedClickable(onClick = {}, onLongClick = onLongClick) else Modifier
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.Center) {
            // 声波环（说话时）
            if (speaking) {
                val ringAlpha by infinite.animateFloat(
                    initialValue = 0.5f, targetValue = 0.1f,
                    animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
                )
                Box(Modifier.size((size * 1.2f).dp).border(2.dp, c.ok.copy(alpha = ringAlpha), CircleShape))
            }
            // 猫娘头像图（用户自定义 or 默认绿眼猫娘）
            Box(
                Modifier.size((size * 0.95f).dp).offset(y = floatY.dp).scale(breathe)
                    .clip(CircleShape).background(Color.White),
            ) {
                val imgModifier = Modifier.fillMaxSize().scale(1f, blinkY * effMouth)
                if (custom != null) {
                    Image(
                        bitmap = custom.asImageBitmap(),
                        contentDescription = null,
                        modifier = imgModifier,
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Image(
                        painter = painterResource(com.openminis.app.R.drawable.miga_catgirl_avatar),
                        contentDescription = null,
                        modifier = imgModifier,
                        contentScale = ContentScale.Crop,
                    )
                }
            }
        }
        // 状态文字：圆圈下方小胶囊（思考中/处理中/说话中）
        if (status != null) {
            Text(
                status,
                fontSize = (size * 0.24f).sp,
                color = c.ink,
                maxLines = 1,
                modifier = Modifier.padding(top = 3.dp)
                    .background(c.glass, RoundedCornerShape(50)).padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun CatGirlFace(blink: Float, mouth: Float, speaking: Boolean, c: com.mibot.ui.theme.MikaColors) {
    // 简笔猫娘（旧版，已用图替代）——保留占位
}
