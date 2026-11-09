package com.openminis.app.browser

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.util.Base64
import com.mibot.cap.web.WebHost
import com.mibot.cap.web.WebScreen
import com.mibot.cap.web.WebTabPool
import com.mibot.core.cap.web.EvalJs
import com.mibot.core.cap.web.NavResult
import com.mibot.core.cap.web.PageText
import com.mibot.core.cap.web.WebPoint
import com.mibot.core.cap.web.WebPort
import com.mibot.core.uistate.ActionResult
import com.mibot.core.uistate.ScreenSnapshot
import com.mibot.core.uistate.UiElement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Bridge from the existing `browser_use` surface ([BrowserActionInput] /
 * [BrowserActionResult]) to the ported Mika browser
 * (`com.mibot.cap.web.WebHost` + `WebTabPool` + `WebScreen`, CDP real input via
 * `Input.dispatchMouseEvent` / `Input.dispatchKeyEvent`).
 *
 * Swap-in point: `ChatViewModel.browserTabPool`. Instead of
 * `BrowserTabPool(context)` keep the pool object but back its per-tab execution
 * with this adapter, or replace the pool field with an instance of this class
 * (it mirrors `BrowserTabPool.execute`'s signature, so callers are unchanged).
 *
 * NOTE (runtime prerequisites, not source blockers):
 *  - The `Application` must implement `com.mibot.cap.web.WebOwner` so
 *    `WebTabPool`'s defaults (`WebOwner.of(context).tabBudget` /
 *    `.webDevtools`) resolve. `WebDevtools` needs a `(String) ->
 *    ParcelFileDescriptor?` relay that opens the `webview_devtools_remote_<pid>`
 *    abstract socket (Mika gets it from its phone hub; Mibot must provide an
 *    equivalent, see the port report §"Runtime wiring").
 *  - `WebHost` needs a private virtual display (no PUBLIC flag). It is created
 *    lazily on the main thread; if `createVirtualDisplay` fails the browser
 *    degrades to a detached WebView (pages may not fully render).
 *
 * This class intentionally keeps the *execution* mapping thin and lets each
 * action delegate to the matching `WebPort` operation. Actions whose Mika
 * counterpart is not yet surfaced return a clear "not yet wired" error instead
 * of silently pretending to succeed.
 */
class MikaBrowserAdapter(
    context: Context,
    /** Task id the pool tracks tabs under; defaults to "mika". */
    private val taskId: String = "mika",
    /** Optional pre-built screen (tests / shared stack). Lazy when null. */
    private val screenOverride: WebPort? = null,
    private val screenBuilder: (() -> WebPort)? = null,
) {
    private val appContext = context.applicationContext

    @Volatile private var screen: WebPort? = screenOverride
    private val building = AtomicBoolean(false)

    /** Build (or reuse) the Mika web stack on the main thread. */
    private suspend fun ensureScreen(): WebPort {
        screen?.let { return it }
        return withContext(Dispatchers.Main) {
            if (building.compareAndSet(false, true)) {
                try {
                    screen = screenBuilder?.invoke() ?: defaultStack()
                } finally {
                    building.set(false)
                }
            }
            screen ?: error("Mika browser stack not built")
        }
    }

    /** Default stack: one host on the device's primary display metrics. */
    private fun defaultStack(): WebPort {
        val dm = Resources.getSystem().displayMetrics
        val host = WebHost(appContext, dm.widthPixels, dm.heightPixels, dm.densityDpi)
        val pool = WebTabPool(appContext, host)
        return WebScreen(pool, taskId, host, clock = System::currentTimeMillis)
    }

    /**
     * Mirrors [BrowserTabPool.execute] so it can drop into the same call site.
     * `singleTab` / `owner` are accepted for signature compatibility; the Mika
     * stack does its own tab budgeting and the per-owner split is not modelled
     * here (single shared task id).
     */
    suspend fun execute(
        input: BrowserActionInput,
        singleTab: Boolean = false,
        owner: String? = null,
    ): BrowserActionResult {
        val port = try {
            ensureScreen()
        } catch (e: Throwable) {
            return BrowserActionResult.error("Mika browser unavailable: ${e.message ?: e.javaClass.simpleName}")
        }
        return dispatch(port, input)
    }

    private suspend fun dispatch(port: WebPort, input: BrowserActionInput): BrowserActionResult =
        when (input.action) {
            BrowserAction.NAVIGATE -> navigate(port, input)
            BrowserAction.GET_TEXT -> getText(port)
            BrowserAction.GET_READABLE -> getReadable(port)
            BrowserAction.GET_PAGE_INFO -> getPageInfo(port)
            BrowserAction.CLICK -> click(port, input)
            BrowserAction.TYPE -> type(port, input)
            BrowserAction.SCROLL -> scroll(port, input)
            BrowserAction.HOVER -> hover(port, input)
            BrowserAction.EXECUTE_JS -> executeJs(port, input)
            BrowserAction.SCREENSHOT -> screenshot(port)
            BrowserAction.NEW_TAB -> newTab(port, input)
            BrowserAction.CLOSE_TAB -> closeTab(port, input)
            BrowserAction.LIST_TABS -> listTabs(port)
            BrowserAction.FIND_ELEMENTS -> findElements(port, input)
            BrowserAction.GET_BACKBONE -> getBackbone(port, input)
            else -> BrowserActionResult.error(
                "Mika browser: action '${input.action.value}' is not wired in this bridge yet " +
                    "(pending = set_user_agent, set_viewport, get_cookies, set_cookies, fetch, scroll_and_collect, wait_for_dom_stable)"
            )
        }

    // ---------- navigation ----------

    private suspend fun navigate(port: WebPort, input: BrowserActionInput): BrowserActionResult {
        val url = input.url ?: return BrowserActionResult.error("navigate needs a url")
        val r: NavResult = port.navigate(url, newTab = false)
        val detail = buildString {
            append(if (r.loaded) "Loaded" else "Navigating")
            append(": ${r.tab.url}")
            r.httpStatus?.let { append(" (HTTP $it)") }
            r.netError?.let { append(" netError=$it") }
            r.upgradedFrom?.let { append(" upgraded from $it") }
            r.download?.let { append(" download=${it.file}") }
            r.settled?.let { append(" settled=$it") }
        }
        return BrowserActionResult(text = detail, success = r.loaded || r.httpStatus != null, pageURL = r.tab.url)
    }

    private suspend fun getText(port: WebPort): BrowserActionResult {
        val snap = port.snapshot()
        val text = snap.elements.joinToString("\n") { e ->
            if (e.name.isBlank()) e.role else "${e.role}: ${e.name}"
        }.ifBlank { "(no text)" }
        return BrowserActionResult(text = text.take(MAX_TEXT), pageURL = snap.web?.url)
    }

    private suspend fun getReadable(port: WebPort): BrowserActionResult {
        val page: PageText = port.readPage(MAX_TEXT, 0)
        val body = page.text.ifBlank { "(empty page)" }
        return BrowserActionResult(
            text = body,
            pageURL = page.url,
        )
    }

    private suspend fun getPageInfo(port: WebPort): BrowserActionResult {
        val snap = port.snapshot()
        val url = snap.web?.url ?: ""
        val title = snap.web?.title ?: ""
        val w = snap.width
        val h = snap.height
        val elems = snap.elements.size
        return BrowserActionResult(
            text = "URL: $url\nTitle: $title\nViewport: ${w}x$h\nElements: $elems",
            pageURL = url,
        )
    }

    // ---------- interaction ----------

    private suspend fun click(port: WebPort, input: BrowserActionInput): BrowserActionResult {
        // Prefer a snapshot-ref click when the model supplied a selector: resolve
        // the first matching element to a ref, else fall back to coordinates.
        if (input.selector != null) {
            val snap = port.snapshot()
            val el = snap.elements.firstOrNull { it.name == input.selector || it.role == input.selector }
            if (el != null) {
                val r = port.tapRef(el.ref, snap.snapshotId)
                return resultOf(r, "click '${el.name.ifBlank { el.role }}'")
            }
        }
        val x = input.coordinateX ?: return BrowserActionResult.error("click needs a selector or coordinate_x/y")
        val y = input.coordinateY ?: return BrowserActionResult.error("click needs a coordinate_y")
        val r = port.tapXY(x, y)
        return resultOf(r, "click @($x,$y)")
    }

    private suspend fun type(port: WebPort, input: BrowserActionInput): BrowserActionResult {
        val text = input.text ?: return BrowserActionResult.error("type needs text")
        val snap = port.snapshot()
        val el = input.selector?.let { s -> snap.elements.firstOrNull { it.name == s || it.role == s } }
        val r = if (el != null) {
            port.typeText(el.ref, snap.snapshotId, text)
        } else {
            port.typeText(null, null, text)
        }
        return resultOf(r, "type '${text.take(40)}'")
    }

    private suspend fun scroll(port: WebPort, input: BrowserActionInput): BrowserActionResult {
        val down = input.direction != ScrollDirection.UP
        val snap = port.snapshot()
        val el = input.selector?.let { s -> snap.elements.firstOrNull { it.name == s || it.role == s } }
        val r = if (el != null) port.scroll(el.ref, snap.snapshotId, down)
        else port.scroll(null, null, down)
        return resultOf(r, "scroll ${if (down) "down" else "up"}")
    }

    private suspend fun hover(port: WebPort, input: BrowserActionInput): BrowserActionResult {
        val x = input.coordinateX ?: return BrowserActionResult.error("hover needs coordinate_x/y")
        val y = input.coordinateY ?: return BrowserActionResult.error("hover needs coordinate_y")
        val r = port.hover(WebPoint.Xy(x, y))
        return resultOf(r, "hover @($x,$y)")
    }

    private suspend fun executeJs(port: WebPort, input: BrowserActionInput): BrowserActionResult {
        val script = input.script ?: return BrowserActionResult.error("execute_js needs a script")
        val r: EvalJs = port.evaluate(script, timeoutMs = input.timeoutMs?.toLong() ?: 10_000L)
        return when (r) {
            is EvalJs.Ok -> BrowserActionResult(text = r.json.take(MAX_TEXT))
            is EvalJs.Failed -> BrowserActionResult.error("${r.error}: ${r.message}")
        }
    }

    private suspend fun screenshot(port: WebPort): BrowserActionResult {
        val png = port.screenshotPng()
            ?: return BrowserActionResult.error("no frame available (host may be detached)")
        val jpg = toJpeg(png)
        return BrowserActionResult(
            text = "Screenshot captured (${png.width}x${png.height})",
            base64Image = Base64.encodeToString(jpg, Base64.NO_WRAP),
        )
    }

    // ---------- tabs ----------

    private suspend fun newTab(port: WebPort, input: BrowserActionInput): BrowserActionResult {
        val url = input.url
        val r: NavResult = port.navigate(url ?: "about:blank", newTab = true)
        return BrowserActionResult(
            text = "New tab ${r.tab.tabId}: ${r.tab.url}",
            pageURL = r.tab.url,
        )
    }

    private suspend fun closeTab(port: WebPort, input: BrowserActionInput): BrowserActionResult {
        val tabId = input.tabId?.toString() ?: return BrowserActionResult.error("close_tab needs tab_id")
        val ok = port.closeTab(tabId)
        return BrowserActionResult(text = if (ok) "Closed tab $tabId" else "No such tab $tabId", success = ok)
    }

    private suspend fun listTabs(port: WebPort): BrowserActionResult {
        val tabs = port.tabs()
        if (tabs.isEmpty()) return BrowserActionResult(text = "(no tabs)")
        val text = tabs.joinToString("\n") { t ->
            val cur = if (t.current) "*" else " "
            "$cur${t.tabId}  ${t.title.ifBlank { t.url }}"
        }
        return BrowserActionResult(text = text)
    }

    // ---------- reads ----------

    private suspend fun findElements(port: WebPort, input: BrowserActionInput): BrowserActionResult {
        val snap = port.snapshot()
        val sel = input.selector ?: ""
        val matched = snap.elements.filter { e ->
            sel.isBlank() || e.name.contains(sel, true) || e.role.contains(sel, true)
        }
        if (matched.isEmpty()) return BrowserActionResult(text = "(no elements match '$sel')")
        val text = matched.take(50).joinToString("\n") { e ->
            "ref=${e.ref} role=${e.role} name=${e.name} @(${e.centerX},${e.centerY})"
        }
        return BrowserActionResult(text = text, pageURL = snap.web?.url)
    }

    private suspend fun getBackbone(port: WebPort, input: BrowserActionInput): BrowserActionResult {
        val snap = port.snapshot()
        val maxDepth = input.maxDepth ?: 5
        val sb = StringBuilder()
        fun walk(e: UiElement, depth: Int) {
            if (depth > maxDepth) return
            sb.append("  ".repeat(depth)).append(e.role).append(' ')
                .append(e.name.take(60)).append(" @").append(e.ref).append('\n')
        }
        snap.elements.take(200).forEach { walk(it, 0) }
        return BrowserActionResult(text = sb.toString().ifBlank { "(empty)" }, pageURL = snap.web?.url)
    }

    // ---------- helpers ----------

    private fun resultOf(r: ActionResult, label: String): BrowserActionResult = when (r) {
        is ActionResult.Ok -> BrowserActionResult(text = "$label: ok", success = true)
        is ActionResult.Failed ->
            BrowserActionResult.error("$label failed: ${r.reason}${r.hint?.let { " ($it)" } ?: ""}")
    }

    private fun toJpeg(bitmap: Bitmap): ByteArray {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
        return out.toByteArray()
    }

    private companion object {
        const val MAX_TEXT = 20_000
    }
}
