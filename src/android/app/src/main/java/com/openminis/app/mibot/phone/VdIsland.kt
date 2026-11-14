package com.openminis.app.mibot.phone

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.openminis.app.mibot.design.tc
import com.openminis.app.service.OverlayPosition
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 流云体悬浮胶囊 —— Mibot 虚拟屏的实时预览，做成灵动岛/流云体式悬浮窗。
 *
 * 替代原来写死的 VdOverlay（Gravity.TOP|CENTER, y=96, FLAG_NOT_TOUCHABLE）：
 *   - 可移动：整个窗口拖动，OverlayPosition 按方向持久化 + 转屏摆回（复用已验证系统）。
 *   - 可缩放：右下角拖角手柄改预览尺寸（scale 0.5x–1.5x，夹在安全区里）。
 *   - 可后台：点「后台」收起成小胶囊（只留状态文字），点胶囊展开；任务在后台继续跑
 *     （DisplayAwake 保活虚拟屏，不依赖本窗口）。
 *
 * 视觉：Mibot glass 毛玻璃风格（圆角 24dp + 半透明深底 + 细描边），与 App 本体统一。
 *
 * 使用：VdForegroundService 在虚拟屏运行期间 show()，停止时 hide()。多屏时每屏一个实例
 * （displayId 区分），帧订阅经 VdController.previewSource（第一版取活动屏流）。
 */
class VdIsland(private val context: Context, private val displayId: Int) {

    private val wm = context.getSystemService(WindowManager::class.java)
    private val prefs: SharedPreferences = context.getSharedPreferences("vd_island", Context.MODE_PRIVATE)

    // ---- 状态 ----
    private var expanded = prefs.getBoolean("expanded_$displayId", true)
    private var scale = prefs.getFloat("scale_$displayId", 1f)
    private var attached = false

    // ---- 视图 ----
    private var root: FrameLayout? = null
    private var preview: ImageView? = null
    private var statusText: TextView? = null
    private var handle: View? = null
    private var params: WindowManager.LayoutParams? = null

    // ---- 手势 ----
    private var downX = 0f; private var downY = 0f
    private var dragging = false
    private var handleDragging = false
    private var startW = 0; private var startH = 0
    private val slop = 8 * context.resources.displayMetrics.density

    // ---- 帧/状态订阅 ----
    private val handler = Handler(Looper.getMainLooper())

    /** 是否有悬浮窗权限。 */
    fun hasOverlayPermission(): Boolean =
        Settings.canDrawOverlays(context)

    /** 显示/更新流云体。任务运行期间调用；无权限时静默（通知已兜底）。 */
    @SuppressLint("ClickableViewAccessibility")
    fun show() {
        if (!hasOverlayPermission()) return
        if (attached) { updateWindow(); return }
        val app = context.applicationContext

        val frame = FrameLayout(app).apply {
            setBackgroundColor(Color.TRANSPARENT)
        }
        // 预览区：虚拟屏画面（ImageView，圆角裁切）
        val previewView = ImageView(app).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(Color.BLACK)
        }
        val status = TextView(app).apply {
            text = tc(app, "Mibot 后台运行中", "Mibot working")
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 11f
        }
        // 右下角缩放手柄
        val scaleHandle = View(app).apply {
            setBackgroundColor(0x88FFFFFF.toInt())
        }
        frame.addView(previewView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        ))
        frame.addView(status, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM
        ).apply { bottomMargin = (4 * density()).roundToInt() })
        frame.addView(scaleHandle, FrameLayout.LayoutParams(
            (24 * density()).roundToInt(), (24 * density()).roundToInt(), Gravity.BOTTOM or Gravity.END
        ))

        // 窗口参数：不抢焦点（不挡输入），可触摸（拖动/按钮），悬浮层
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0; y = 0
        }
        wm.addView(frame, lp)
        root = frame; preview = previewView; statusText = status; handle = scaleHandle
        params = lp
        attached = true

        attachDrag(frame, lp)
        attachScaleHandle(scaleHandle, lp)
        attachToggle(frame, lp)
        observeFrames(previewView)
        observeState(status)
        applyExpanded()
        place(lp)
    }

    /** 收起/展开。后台 = 只留小胶囊。 */
    fun setExpanded(value: Boolean) {
        expanded = value
        prefs.edit().putBoolean("expanded_$displayId", value).apply()
        applyExpanded()
    }

    fun hide() {
        handler.post {
            root?.let { runCatching { wm.removeView(it) } }
            root = null; attached = false
        }
    }

    // ---- 布局 ----

    private fun density(): Float = context.resources.displayMetrics.density

    private fun previewWidth(): Int = ((context.resources.displayMetrics.widthPixels * 0.32f) * scale).roundToInt()
    private fun previewHeight(): Int = (previewWidth() * 19.5f / 9f).roundToInt()

    private fun place(lp: WindowManager.LayoutParams) {
        val dm = context.resources.displayMetrics
        val pos = OverlayPosition.displayed(
            positionStore(), defaultPosition(lp),
            dm.widthPixels, dm.heightPixels,
            lp.width.coerceAtLeast(1), lp.height.coerceAtLeast(1),
            safeArea(),
        )
        lp.x = pos.x; lp.y = pos.y
        if (attached) root?.let { wm.updateViewLayout(it, lp) }
    }

    private fun updateWindow() {
        val lp = params ?: return
        lp.width = if (expanded) previewWidth() else (150 * density()).roundToInt()
        lp.height = if (expanded) previewHeight() else (40 * density()).roundToInt()
        root?.let { wm.updateViewLayout(it, lp) }
        // 尺寸变了，把位置夹回安全区
        place(lp)
    }

    private fun applyExpanded() {
        val lp = params ?: return
        root?.let { if (expanded) it.visibility = View.VISIBLE }
        if (expanded) {
            preview?.visibility = View.VISIBLE
            statusText?.visibility = View.VISIBLE
            handle?.visibility = View.VISIBLE
        } else {
            preview?.visibility = View.GONE
            statusText?.visibility = View.VISIBLE
            handle?.visibility = View.GONE
        }
        updateWindow()
    }

    // ---- 手势 ----

    /** 拖动整个窗口（移动流云体）。 */
    @SuppressLint("ClickableViewAccessibility")
    private fun attachDrag(frame: View, lp: WindowManager.LayoutParams) {
        frame.setOnTouchListener { v, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = ev.rawX; downY = ev.rawY; dragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (ev.rawX - downX).toInt(); val dy = (ev.rawY - downY).toInt()
                    if (!dragging && (abs(dx) > slop || abs(dy) > slop)) dragging = true
                    if (dragging) {
                        val nx = lp.x + dx; val ny = lp.y + dy
                        lp.x = nx; lp.y = ny
                        downX = ev.rawX; downY = ev.rawY
                        runCatching { wm.updateViewLayout(frame, lp) }
                        // 拖动不夹（贴边半藏是特性），松手才按方向持久化
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (dragging) {
                        val dm = context.resources.displayMetrics
                        OverlayPosition.dragEnded(positionStore(), dm.widthPixels, dm.heightPixels, OverlayPosition.Point(lp.x, lp.y))
                        dragging = false
                    } else {
                        v.performClick()
                    }
                    true
                }
                else -> false
            }
        }
    }

    /** 右下角拖角缩放。 */
    @SuppressLint("ClickableViewAccessibility")
    private fun attachScaleHandle(handleView: View, lp: WindowManager.LayoutParams) {
        handleView.setOnTouchListener { v, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> { handleDragging = true; startW = lp.width; startH = lp.height; downX = ev.rawX; downY = ev.rawY; true }
                MotionEvent.ACTION_MOVE -> {
                    if (handleDragging) {
                        val dx = (ev.rawX - downX).toInt(); val dy = (ev.rawY - downY).toInt()
                        val newW = (startW + dx).coerceIn(minW(), maxW())
                        val newH = (newW * 19.5f / 9f).roundToInt()
                        lp.width = newW; lp.height = newH
                        scale = newW / (context.resources.displayMetrics.widthPixels * 0.32f)
                        prefs.edit().putFloat("scale_$displayId", scale).apply()
                        root?.let { wm.updateViewLayout(it, lp) }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> { handleDragging = false; place(lp); true }
                else -> false
            }
        }
    }

    /** 点击（收起态）展开。 */
    @SuppressLint("ClickableViewAccessibility")
    private fun attachToggle(frame: View, lp: WindowManager.LayoutParams) {
        frame.setOnClickListener { if (!expanded) setExpanded(true) }
    }

    // ---- 订阅（主线程轮询，轻量；避免 suspend collect 的嵌套作用域管理） ----

    private fun observeFrames(view: ImageView) {
        val poll = object : Runnable {
            override fun run() {
                if (!attached) return
                val frame = runCatching { VdController.previewSource.value.value }.getOrNull()
                if (frame != null && !frame.isRecycled) view.setImageBitmap(frame)
                handler.postDelayed(this, FRAME_POLL_MS)
            }
        }
        handler.post(poll)
    }

    private fun observeState(view: TextView) {
        val poll = object : Runnable {
            override fun run() {
                if (!attached) return
                val s = VdController.state.value
                val appName = s.app ?: ""
                view.text = tc(context,
                    "Mibot 后台运行中${if (appName.isNotEmpty()) " · $appName" else ""}",
                    "Mibot working${if (appName.isNotEmpty()) " · $appName" else ""}")
                handler.postDelayed(this, STATE_POLL_MS)
            }
        }
        handler.post(poll)
    }

    // ---- 几何/持久化 ----

    private fun safeArea(): OverlayPosition.Avoid {
        val dm = context.resources.displayMetrics
        return OverlayPosition.avoidFor(
            OverlayPosition.Orientation.of(dm.widthPixels, dm.heightPixels), 0, 0, 0,
            top = statusBarHeight(),
        )
    }

    private fun statusBarHeight(): Int {
        val id = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) context.resources.getDimensionPixelSize(id) else 0
    }

    private fun minW(): Int = (context.resources.displayMetrics.widthPixels * 0.16f).roundToInt()
    private fun maxW(): Int = (context.resources.displayMetrics.widthPixels * 0.5f).roundToInt()

    private fun defaultPosition(lp: WindowManager.LayoutParams): OverlayPosition.Point {
        val dm = context.resources.displayMetrics
        return OverlayPosition.Point(dm.widthPixels - lp.width - (12 * density()).roundToInt(), statusBarHeight() + (12 * density()).roundToInt())
    }

    private fun positionStore(): OverlayPosition.Store = object : OverlayPosition.Store {
        override fun get(orientation: OverlayPosition.Orientation): OverlayPosition.Point? {
            val x = prefs.getInt("x_${orientation.name}_$displayId", Int.MIN_VALUE)
            val y = prefs.getInt("y_${orientation.name}_$displayId", Int.MIN_VALUE)
            return if (x == Int.MIN_VALUE || y == Int.MIN_VALUE) null else OverlayPosition.Point(x, y)
        }
        override fun set(orientation: OverlayPosition.Orientation, point: OverlayPosition.Point) {
            prefs.edit().putInt("x_${orientation.name}_$displayId", point.x)
                .putInt("y_${orientation.name}_$displayId", point.y).apply()
        }
    }

    companion object {
        private const val TAG = "VdIsland"
        private const val FRAME_POLL_MS = 300L
        private const val STATE_POLL_MS = 1000L

        // 每屏一个实例（displayId 区分），由 VdForegroundService 在运行期间管理。
        private val instances = LinkedHashMap<Int, VdIsland>()

        /** 为 [displayId] 显示/更新流云体（无权限时静默，通知已兜底）。 */
        fun showFor(context: Context, displayId: Int): VdIsland =
            instances.getOrPut(displayId) { VdIsland(context.applicationContext, displayId) }.also { it.show() }

        /** 隐藏并释放全部流云体（虚拟屏全部关闭时）。 */
        fun hideAll() {
            synchronized(instances) {
                instances.values.forEach { it.hide() }
                instances.clear()
            }
        }

        /** 隐藏单个屏的流云体。 */
        fun hideFor(displayId: Int) {
            synchronized(instances) {
                instances.remove(displayId)?.hide()
            }
        }
    }
}
