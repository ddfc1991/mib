package com.openminis.app.ui.glass

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openminis.app.ui.theme.AppTokens
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * [fork/opencode-ui] App-level "灵动岛" status capsule.
 *
 * This is not a system Dynamic Island. It is a small frosted capsule anchored
 * under the status bar, used for quiet, reversible feedback: archived,
 * restored, deleted, connected. Motion is deliberately product-sized
 * (180–220ms) — it explains a state change without making the user wait.
 */
enum class GlassIslandKind {
    Info,
    Success,
    Archive,
    Delete,
}

data class GlassIslandState(
    val id: Long = 0L,
    val title: String = "",
    val detail: String? = null,
    val kind: GlassIslandKind = GlassIslandKind.Info,
    val visible: Boolean = false,
)

object GlassIsland {
    private val sequence = AtomicLong(0L)
    private val scope = MainScope()
    private val _state = MutableStateFlow(GlassIslandState())
    val state: StateFlow<GlassIslandState> = _state.asStateFlow()
    private var dismissJob: Job? = null

    fun show(
        title: String,
        detail: String? = null,
        kind: GlassIslandKind = GlassIslandKind.Info,
        durationMs: Long = 2200L,
    ) {
        val id = sequence.incrementAndGet()
        dismissJob?.cancel()
        _state.value = GlassIslandState(id, title, detail, kind, visible = true)
        dismissJob = scope.launch {
            delay(durationMs)
            dismiss(id)
        }
    }

    fun dismiss(id: Long) {
        if (_state.value.id == id) {
            _state.value = _state.value.copy(visible = false)
        }
    }
}

@Composable
fun GlassIslandHost() {
    val state by GlassIsland.state.collectAsState()
    val p = AppTokens.current
    val dark = frostIsDark()
    val expanded = !state.detail.isNullOrBlank()
    val width = animateDpAsState(
        targetValue = if (expanded) 304.dp else 220.dp,
        animationSpec = tween(220, easing = FastOutSlowInEasing),
    ).value
    val height = animateDpAsState(
        targetValue = if (expanded) 56.dp else 40.dp,
        animationSpec = tween(220, easing = FastOutSlowInEasing),
    ).value

    Box(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(top = 10.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        AnimatedVisibility(
            visible = state.visible && state.id != 0L,
            enter = fadeIn(animationSpec = tween(180, easing = FastOutSlowInEasing)) +
                slideInVertically(animationSpec = tween(200, easing = FastOutSlowInEasing)) { -it / 2 },
            exit = fadeOut(animationSpec = tween(150, easing = FastOutSlowInEasing)) +
                slideOutVertically(animationSpec = tween(180, easing = FastOutSlowInEasing)) { -it / 2 },
        ) {
            val shape = RoundedCornerShape(20.dp)
            val icon = when (state.kind) {
                GlassIslandKind.Archive -> Icons.Filled.Archive
                GlassIslandKind.Delete -> Icons.Filled.Delete
                GlassIslandKind.Success -> Icons.Filled.CheckCircle
                GlassIslandKind.Info -> Icons.Filled.Info
            }
            val iconTint: Color = if (state.kind == GlassIslandKind.Delete) p.danger else p.accent
            Row(
                Modifier
                    .width(width)
                    .height(height)
                    .frosted(shape = shape, level = FrostLevel.Bar, border = false, elevation = 10.dp)
                    .border(1.dp, FrostTokens.rim(dark), shape)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(26.dp)
                        .background(iconTint.copy(alpha = 0.16f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        state.title,
                        color = p.ink,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (expanded) {
                        Text(
                            state.detail.orEmpty(),
                            color = p.ink2,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
