package com.mibot.confirm

import android.content.Context
import android.content.pm.PackageManager
import com.mibot.core.cap.root.ROOT_CONFIRM_APP
import com.mibot.i18n.tr
import com.openminis.app.R

/**
 * 确认通知器最小实现(proactive 模块用):只提供 [appLabel]。
 * 完整版(Miga confirm/ConfirmationNotifier)含通知按钮/卡片/服务,后续接线时整搬。
 */
object ConfirmationNotifier {
    /** App 的显示名;读不到(没装、包不可见)时用包名。 */
    fun appLabel(context: Context, packageName: String): String =
        if (packageName == ROOT_CONFIRM_APP) tr(R.string.confirm_root_where) else try {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString().ifBlank { packageName }
        } catch (_: PackageManager.NameNotFoundException) {
            packageName
        }
}
