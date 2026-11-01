package com.openminis.app.ui.glass

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.exifinterface.media.ExifInterface
import com.openminis.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** [fork/glass-ui] Settings › Backdrop & Glass. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackdropSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings by remember(context) { GlassPrefs.flow(context) }.collectAsState()
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val path = withContext(Dispatchers.IO) { BackdropPhotoStore.importPhoto(context, uri) }
            if (path == null) {
                Toast.makeText(context, R.string.glass_photo_failed, Toast.LENGTH_SHORT).show()
            } else {
                GlassPrefs.update(context) { it.copy(mode = BackdropMode.PHOTO, photoPath = path) }
            }
        }
    }
    val pickPhoto = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.glass_backdrop_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            // Live preview of the backdrop.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(24.dp)),
            ) {
                GlassStyleTheme { GlassScreenBackdrop() }
            }
            Spacer(Modifier.height(8.dp))
            val itemColors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background)
            ListItem(
                headlineContent = { Text(stringResource(R.string.glass_enable)) },
                supportingContent = { Text(stringResource(R.string.glass_enable_desc)) },
                trailingContent = {
                    Switch(checked = settings.enabled, onCheckedChange = { on -> GlassPrefs.update(context) { it.copy(enabled = on) } })
                },
                colors = itemColors,
            )
            Text(
                stringResource(R.string.glass_section_style),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
            )
            ChoiceRow(settings.style == GlassStyle.DUSK, R.string.glass_style_dusk, R.string.glass_style_dusk_desc) {
                GlassPrefs.update(context) { s -> s.copy(style = GlassStyle.DUSK) }
            }
            ChoiceRow(settings.style == GlassStyle.WARM, R.string.glass_style_warm, R.string.glass_style_warm_desc) {
                GlassPrefs.update(context) { s -> s.copy(style = GlassStyle.WARM) }
            }
            Text(
                stringResource(R.string.glass_section_backdrop),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
            )
            ModeRow(BackdropMode.AMBIENT, R.string.glass_mode_ambient, R.string.glass_mode_ambient_desc, settings.mode) {
                GlassPrefs.update(context) { s -> s.copy(mode = BackdropMode.AMBIENT) }
            }
            ModeRow(BackdropMode.PHOTO, R.string.glass_mode_photo, R.string.glass_mode_photo_desc, settings.mode) {
                if (settings.photoPath == null) pickPhoto() else GlassPrefs.update(context) { s -> s.copy(mode = BackdropMode.PHOTO) }
            }
            ModeRow(BackdropMode.PLAIN, R.string.glass_mode_plain, R.string.glass_mode_plain_desc, settings.mode) {
                GlassPrefs.update(context) { s -> s.copy(mode = BackdropMode.PLAIN) }
            }
            FilledTonalButton(onClick = pickPhoto, modifier = Modifier.padding(start = 16.dp, top = 4.dp)) {
                Icon(Icons.Outlined.Image, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.glass_pick_photo))
            }
            Text(
                stringResource(R.string.glass_dim),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, top = 20.dp),
            )
            Slider(
                value = settings.dim,
                onValueChange = { v -> GlassPrefs.update(context) { it.copy(dim = v) } },
                valueRange = 0f..0.9f,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun ModeRow(mode: BackdropMode, title: Int, desc: Int, current: BackdropMode, onClick: () -> Unit) =
    ChoiceRow(mode == current, title, desc, onClick)

@Composable
private fun ChoiceRow(selected: Boolean, title: Int, desc: Int, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(4.dp))
        Column {
            Text(stringResource(title), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(desc), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Copies a picked image into app storage (downscaled, EXIF-upright). */
object BackdropPhotoStore {
    private const val MAX_EDGE = 2048

    // [fork/mibot-v2] Delegates to the robust importer (ImageDecoder, downsampling, HEIC/WebP).
    fun importPhoto(context: Context, uri: Uri): String? =
        com.openminis.app.mibot.settings.MibotPhotoImport.import(context, uri).path
}
