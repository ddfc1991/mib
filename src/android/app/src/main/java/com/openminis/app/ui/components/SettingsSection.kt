package com.openminis.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.openminis.app.i18n.uppercaseForDisplay

/**
 * iOS Settings-style "inset grouped" section. Renders an uppercase
 * teal header above a 16dp rounded surface card containing the
 * caller's [content] rows. Pair with [SettingsRowDivider] between
 * rows so the dividers stop short of the card's curved edges.
 *
 * Apply this to any settings detail screen that wants the
 * SF-Symbols Settings.app look — initially adopted by
 * RootfsManagementScreen (T168).
 */
@Composable
fun SettingsSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val mibotUi = com.openminis.app.ui.theme.LocalMibotActive.current
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = if (mibotUi) 14.dp else 16.dp)) {
        Text(
            text = if (mibotUi) title else title.uppercaseForDisplay(),
            style = if (mibotUi) MaterialTheme.typography.titleSmall else MaterialTheme.typography.labelSmall,
            color = if (mibotUi) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = if (mibotUi) 12.dp else 4.dp, vertical = 8.dp),
        )
        Surface(
            shape = if (mibotUi) com.openminis.app.mibot.design.MibotRadius.CardShape else RoundedCornerShape(16.dp),
            color = if (mibotUi) com.openminis.app.mibot.design.MibotTokens.card() else MaterialTheme.colorScheme.surface,
            border = if (mibotUi) androidx.compose.foundation.BorderStroke(1.dp, com.openminis.app.mibot.design.MibotTokens.hairline()) else null,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 0.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(content = content)
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

/**
 * Thin divider for rows inside a [SettingsSection]. Pads in 16dp on
 * either side so the line stops short of the rounded card edge,
 * matching iOS Settings' inset divider style.
 */
@Composable
fun SettingsRowDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier.padding(horizontal = 16.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    )
}
