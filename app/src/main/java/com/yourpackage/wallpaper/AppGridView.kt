package com.yourpackage.wallpaper

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.Drawable
import android.view.MotionEvent
import android.view.View

/**
 * 全屏应用抽屉：显示所有已安装的可启动 App，5列网格排列。
 * 点击背景空白处关闭，点击图标启动 App。
 */
class AppGridView(context: Context) : View(context) {

    data class AppInfo(
        val label: String,
        val packageName: String,
        val activityName: String,
        val icon: Drawable
    )

    private val apps = mutableListOf<AppInfo>()
    private var onClose: (() -> Unit)? = null
    private var pressedIdx = -1

    private val COLS = 6
    private val ROW_H = 140f
    private val ICON_S = 72f
    private val TOP_PAD = 60f
    private val SIDE_PAD = 80f

    private val bgPaint  = Paint().apply { color = Color.argb(220,10,12,18) }
    private val lblPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textAlign = Paint.Align.CENTER
    }
    private val hlPaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(80, 255,255,255)
    }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(180,200,200,200); textAlign = Paint.Align.LEFT
    }

    init {
        loadApps()
        setOnTouchListener { _, ev -> handleTouch(ev); true }
    }

    fun setOnCloseListener(l: () -> Unit) { onClose = l }

    private fun loadApps() {
        apps.clear()
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
        pm.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != context.packageName }
            .sortedBy { it.loadLabel(pm).toString() }
            .forEach { info ->
                apps.add(AppInfo(
                    label        = info.loadLabel(pm).toString().take(8),
                    packageName  = info.activityInfo.packageName,
                    activityName = info.activityInfo.name,
                    icon         = info.loadIcon(pm)
                ))
            }
    }

    private fun handleTouch(ev: MotionEvent) {
        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                pressedIdx = getIdxAt(ev.x, ev.y)
                // 点在背景空白处关闭
                if (pressedIdx < 0) onClose?.invoke()
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                val idx = getIdxAt(ev.x, ev.y)
                if (idx >= 0 && idx == pressedIdx) launchApp(apps[idx])
                pressedIdx = -1; invalidate()
            }
            MotionEvent.ACTION_CANCEL -> { pressedIdx = -1; invalidate() }
        }
    }

    private fun getIdxAt(x: Float, y: Float): Int {
        val usableW = width - SIDE_PAD * 2
        val cellW = usableW / COLS
        val col = ((x - SIDE_PAD) / cellW).toInt()
        val row = ((y - TOP_PAD) / ROW_H).toInt()
        if (col < 0 || col >= COLS || row < 0) return -1
        val idx = row * COLS + col
        return if (idx in apps.indices) idx else -1
    }

    private fun launchApp(app: AppInfo) {
        try {
            val intent = Intent().apply {
                setClassName(app.packageName, app.activityName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                val fb = context.packageManager.getLaunchIntentForPackage(app.packageName)
                fb?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (fb != null) context.startActivity(fb)
            } catch (_: Exception) {}
        }
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        lblPaint.textSize   = h / 720f * 18f
        titlePaint.textSize = h / 720f * 22f
    }

    override fun onDraw(canvas: Canvas) {
        // 半透明深色背景
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // 标题
        canvas.drawText("全部应用", SIDE_PAD, TOP_PAD - 16f, titlePaint)

        val usableW = width - SIDE_PAD * 2
        val cellW = usableW / COLS

        apps.forEachIndexed { idx, app ->
            val col = idx % COLS
            val row = idx / COLS
            val cx = SIDE_PAD + col * cellW + cellW / 2f
            val rowTop = TOP_PAD + row * ROW_H

            // 按下高亮
            if (idx == pressedIdx) {
                canvas.drawRoundRect(
                    RectF(cx - cellW/2f + 8f, rowTop, cx + cellW/2f - 8f, rowTop + ROW_H - 4f),
                    14f, 14f, hlPaint
                )
            }

            // 图标
            val iconL = (cx - ICON_S / 2f).toInt()
            val iconT = (rowTop + 10f).toInt()
            app.icon.setBounds(iconL, iconT, (iconL + ICON_S).toInt(), (iconT + ICON_S).toInt())
            app.icon.draw(canvas)

            // 标签
            canvas.drawText(app.label, cx, rowTop + ICON_S + 30f, lblPaint)
        }
    }
}
