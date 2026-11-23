package com.openminis.app.mibot.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openminis.app.ui.theme.SkinStore
import com.openminis.app.ui.theme.Skins

/**
 * [destiny/omega-skin] Skin picker: three swatches (background / accent /
 * surface) + name. Tapping applies immediately and persists.
 */
@Composable
fun SkinPickerDialog(show: Boolean, onDismiss: () -> Unit) {
    if (!show) return
    val ctx = LocalContext.current
    val current by SkinStore.current.collectAsState()
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("完成", color = MaterialTheme.colorScheme.primary)
            }
        },
        title = {
            Text("皮肤", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Skins.ALL.forEach { skin ->
                    val selected = skin.id == current.id
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { SkinStore.select(ctx, skin) }
                            .padding(horizontal = 6.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(26.dp).clip(CircleShape).background(skin.bg)
                                .border(1.dp, skin.text2.copy(alpha = 0.4f), CircleShape),
                        )
                        Spacer(Modifier.width(5.dp))
                        Box(Modifier.size(26.dp).clip(CircleShape).background(skin.accent))
                        Spacer(Modifier.width(5.dp))
                        Box(
                            Modifier.size(26.dp).clip(CircleShape).background(skin.surface)
                                .border(1.dp, skin.text2.copy(alpha = 0.25f), CircleShape),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            skin.name,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 15.sp,
                            modifier = Modifier.weight(1f),
                        )
                        if (selected) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                }
            }
        },
    )
}
