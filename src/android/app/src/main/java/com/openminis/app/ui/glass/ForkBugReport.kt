package com.openminis.app.ui.glass

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.openminis.app.BuildConfig
import com.openminis.app.R

/**
 * [fork/bug-report] Round-1 "one-tap bug report": opens a pre-filled GitHub
 * issue on the fork with app + device details, falling back to the share
 * sheet when no browser can take the URL. No logs are attached yet; the full
 * design (redacted logs, screenshot, in-app preview, token upload) is in
 * docs/fork/bug-report.md.
 */
object ForkBugReport {
    const val REPO = "dl1991/OpenMinis"

    fun deviceSummary(): String = buildString {
        appendLine("| Field | Value |")
        appendLine("|---|---|")
        appendLine("| App | ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}, ${BuildConfig.BUILD_TYPE}) |")
        appendLine("| Android | ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) |")
        appendLine("| Device | ${Build.MANUFACTURER} ${Build.MODEL} |")
        appendLine("| ROM | ${Build.DISPLAY} |")
        append("| ABI | ${Build.SUPPORTED_ABIS.joinToString()} |")
    }

    fun open(context: Context) {
        val title = context.getString(R.string.glass_bug_subject)
        val body = context.getString(R.string.glass_bug_body_hint) + "\n\n\n\n" + deviceSummary()
        val url = "https://github.com/$REPO/issues/new?labels=bug&title=" +
            Uri.encode("[Bug] ") + "&body=" + Uri.encode(body)
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, title)
                putExtra(Intent.EXTRA_TEXT, body)
            }
            context.startActivity(
                Intent.createChooser(send, context.getString(R.string.glass_bug_chooser))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}
