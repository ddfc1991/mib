package com.openminis.app.ui.glass

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import com.openminis.app.data.repository.ChatRepository
import com.openminis.app.ui.navigation.Routes
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.launch

/** [fork/glass-ui] Routes owned by the fork's glass UI. */
object GlassRoutes {
    const val FEED = "glass_feed"
    const val IDEAS = "glass_ideas"
    const val BOTS = "glass_bots"
    const val BACKDROP = "glass_backdrop"
}

@Composable
fun glassEnabled(): Boolean {
    val context = LocalContext.current
    val s by remember(context) { GlassPrefs.flow(context) }.collectAsState()
    return s.enabled
}

/**
 * Switch top-level tab. Tabs sit one level above the chat list so Back from
 * any tab returns to Chats, and each tab keeps its state (save/restore).
 */
fun NavHostController.navigateToGlassTab(tab: GlassTab) {
    val route = when (tab) {
        GlassTab.CHATS -> Routes.SESSION_LIST
        GlassTab.FEED -> GlassRoutes.FEED
        GlassTab.IDEAS -> GlassRoutes.IDEAS
        GlassTab.BOTS -> GlassRoutes.BOTS
        GlassTab.SETTINGS -> Routes.SETTINGS
    }
    if (currentDestination?.route == route) return
    if (tab == GlassTab.CHATS) {
        if (!popBackStack(Routes.SESSION_LIST, inclusive = false)) {
            navigate(Routes.SESSION_LIST) { launchSingleTop = true }
        }
        return
    }
    navigate(route) {
        popUpTo(Routes.SESSION_LIST) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * Wraps the chat list/detail pair in a modal drawer whose only job is the
 * recent list (ChatGPT-style). The pair is the Haze source the frosted sheet
 * blurs.
 */
@Composable
fun GlassDrawerHost(
    navController: NavHostController,
    chatRepository: ChatRepository,
    content: @Composable (openDrawer: () -> Unit) -> Unit,
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val haze = rememberHazeState()
    val sessions by remember(chatRepository) { chatRepository.observeSessions() }
        .collectAsState(initial = emptyList())
    val items = remember(sessions) {
        if (GlassDemo.enabled) GlassDemo.items() else RecentItems.fromSessions(sessions)
    }
    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerState.isOpen,
        scrimColor = Color.Black.copy(alpha = 0.16f),
        drawerContent = {
          GlassStyleTheme {
            GlassSidebar(
                items = items,
                haze = haze,
                onOpen = { item ->
                    scope.launch { drawerState.close() }
                    if (item is RecentItem.Chat && !item.id.startsWith("demo-")) {
                        navController.navigate(Routes.chat(item.id)) {
                            popUpTo(Routes.SESSION_LIST) { inclusive = false }
                            launchSingleTop = true
                        }
                    }
                    // Group chats open once feat/bots provides them.
                },
                onNewChat = {
                    scope.launch { drawerState.close() }
                    navController.navigate(Routes.chat("__new__${java.util.UUID.randomUUID()}")) {
                        popUpTo(Routes.SESSION_LIST) { inclusive = false }
                    }
                },
            )
          }
        },
    ) {
        Box(Modifier.fillMaxSize().hazeSource(haze)) {
            content { scope.launch { drawerState.open() } }
        }
    }
    BackHandler(enabled = drawerState.isOpen) { scope.launch { drawerState.close() } }
}
