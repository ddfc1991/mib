package com.openminis.app.mibot.feed

import androidx.navigation.NavController
import com.openminis.app.share.PendingShare
import com.openminis.app.share.ShareCoordinator
import com.openminis.app.share.SharedShareStore
import com.openminis.app.ui.navigation.Routes
import com.openminis.app.ui.navigation.newDraftSessionId

/**
 * [fork/mibot-feed] 讨论 / 去对话: open a NEW chat with [text] already in the
 * composer (not sent). Rides the existing share → new-draft path: the text is
 * buffered as an inline-text share, addressed to a fresh `__new__` draft,
 * and ChatScreen injects it into the input on first composition. Nothing in
 * chat storage is written until the user sends.
 */
fun openChatWithDraft(nav: NavController, text: String) {
    val body = text.trim()
    if (body.isEmpty()) return
    val ctx = nav.context.applicationContext
    SharedShareStore.savePendingShare(
        ctx,
        PendingShare(listOf(PendingShare.Item(PendingShare.Item.Kind.INLINE_TEXT, body)), System.currentTimeMillis()),
    )
    ShareCoordinator.processPendingShare(ctx)
    val draft = newDraftSessionId()
    if (ShareCoordinator.addressTo(draft)) {
        nav.navigate(Routes.chat(draft)) {
            popUpTo(Routes.SESSION_LIST) { inclusive = false }
            launchSingleTop = true
        }
    }
}
