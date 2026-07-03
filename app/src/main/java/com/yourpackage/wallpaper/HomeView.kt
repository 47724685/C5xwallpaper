package com.yourpackage.wallpaper

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * 参照 C5XHomeView 布局重建的车机桌面 View
 *
 * 设计坐标系 1920×720（与原版一致），运行时用 screenToDesign 系数缩放。
 *
 * 布局（从上到下）：
 *   顶部状态栏 (y=0..60)：时间、日期、星期
 *   中央区域 (y=60..520)：壁纸全屏，大时钟居中显示
 *   底部 Dock (y=525..710)：6个应用快捷按钮
 *   右下角：换壁纸按钮
 */
class HomeView(context: Context) : View(context) {

    // ── 设计坐标系（原版 1920×720）────────────────────────────────────────────
    private val DESIGN_W = 1920f
    private val DESIGN_H = 720f

    // Dock 区域（原版坐标）
    private val DOCK_LEFT   = 190f
    private val DOCK_RIGHT  = 1730f
    private val DOCK_TOP    = 525f
    private val DOCK_BOTTOM = 710f
    private val DOCK_RADIUS = 38f

    // 状态栏高度
    private val STATUS_H = 58f

    // 换壁纸按钮（原版坐标右下角）
    private val BTN_WALL_L = 1780f
    private val BTN_WALL_T = 650f
    private val BTN_WALL_R = 1900f
    private val BTN_WALL_B = 705f

    // ── 运行时缩放 ─────────────────────────────────────────────────────────────
    private var sx = 1f  // scaleX = realWidth / DESIGN_W
    private var sy = 1f  // scaleY = realHeight / DESIGN_H

    private fun dx(v: Float) = v * sx   // design → screen X
    private fun dy(v: Float) = v * sy   // design → screen Y
    private fun rf(l: Float, t: Float, r: Float, b: Float) =
        RectF(dx(l), dy(t), dx(r), dy(b))

    // ── 数据 ───────────────────────────────────────────────────────────────────
    data class DockApp(
        val label: String,
        val packageName: String,
        val icon: Drawable?
    )

    private val dockApps = mutableListOf<DockApp>()
    private var wallpaperBitmap: Bitmap? = null
    private var pressedDockIdx = -1
    private var wallBtnPressed = false

    // ── Paint ─────────────────────────────────────────────────────────────────
    private val bmpPaint    = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val dockBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dockBtnPaint= Paint(Paint.ANTI_ALIAS_FLAG)
    private val dockLblPaint= Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val statusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val clockPaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val datePaint   = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val wallBtnPaint= Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(160,20,20,20) }
    private val wallBtnTxtP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textAlign = Paint.Align.CENTER
    }
    private val hlPaint     = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(80, 255,255,255)
    }

    // ── 刷新 handler ──────────────────────────────────────────────────────────
    private val handler = Handler(Looper.getMainLooper())
    private val clockTick = object : Runnable {
        override fun run() {
            invalidate()
            handler.postDelayed(this, 1000)
        }
    }

    init {
        loadDockApps()
        handler.post(clockTick)

        setOnTouchListener { _, ev -> handleTouch(ev); true }
    }

    // ── 应用加载 ──────────────────────────────────────────────────────────────
    /**
     * 参照原版 Dock：NAV / MUSIC / A/C / CAR / SET / MOOD
     * 对应航盛车机包名，找不到的用系统 LAUNCHER 列表填充。
     */
    private fun loadDockApps() {
        dockApps.clear()
        val pm = context.packageManager

        // 原版6个固定位置及对应包名（根据航盛 d531mc 实际包名）
        val slots = listOf(
            "NAV"   to listOf("com.autonavi.amapauto","com.baidu.BaiduMap","com.autonavi.miniinternational"),
            "MUSIC" to listOf("com.hsae.d531mc.music","com.android.music","com.netease.cloudmusic"),
            "A/C"   to listOf("com.hsae.d531mc.ac","com.hsae.car.ac","com.hsae.auto.ac"),
            "CAR"   to listOf("com.hsae.d531mc.carinfo","com.hsae.car","com.hsae.auto"),
            "SET"   to listOf("com.android.settings","com.hsae.d531mc.settings"),
            "APP"   to emptyList()  // 全部应用
        )

        for ((label, pkgs) in slots) {
            if (label == "APP") {
                dockApps.add(DockApp("APP", "__all__", null))
                continue
            }
            val found = pkgs.firstOrNull { pkg ->
                try { pm.getApplicationInfo(pkg, 0); true } catch (e: Exception) { false }
            }
            val icon = found?.let {
                try { pm.getApplicationIcon(it) } catch (e: Exception) { null }
            }
            dockApps.add(DockApp(label, found ?: "", icon))
        }
    }

    fun reloadWallpaper() {
        wallpaperBitmap?.recycle()
        val prefs = context.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
        val type  = prefs.getString(MainActivity.KEY_WALLPAPER_TYPE, "builtin") ?: "builtin"
        wallpaperBitmap = if (type == "custom") {
            val path = prefs.getString(MainActivity.KEY_WALLPAPER_PATH, null)
            if (path != null && File(path).exists()) decodeSafe(path) else loadBuiltin(0)
        } else {
            loadBuiltin(prefs.getInt(MainActivity.KEY_BUILTIN_INDEX, 0))
        }
        invalidate()
    }

    private fun decodeSafe(path: String): Bitmap? = try {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, o)
        o.inSampleSize = calcSample(o, 1920, 720)
        o.inJustDecodeBounds = false
        o.inPreferredConfig = Bitmap.Config.RGB_565
        BitmapFactory.decodeFile(path, o)
    } catch (e: Exception) { null }

    private fun loadBuiltin(idx: Int): Bitmap? {
        val ids = BuiltinWallpapers.resIds(context)
        if (ids.isEmpty()) return null
        return try {
            val o = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 }
            BitmapFactory.decodeResource(context.resources, ids[idx.coerceIn(0, ids.size-1)], o)
        } catch (e: Exception) { null }
    }

    private fun calcSample(o: BitmapFactory.Options, rw: Int, rh: Int): Int {
        var s = 1
        if (o.outHeight > rh || o.outWidth > rw) {
            val hh = o.outHeight / 2; val hw = o.outWidth / 2
            while (hh / s >= rh && hw / s >= rw) s *= 2
        }
        return s
    }

    // ── 触摸 ──────────────────────────────────────────────────────────────────
    private fun handleTouch(ev: MotionEvent) {
        val x = ev.x; val y = ev.y
        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                pressedDockIdx = getDockIdx(x, y)
                wallBtnPressed = rf(BTN_WALL_L, BTN_WALL_T, BTN_WALL_R, BTN_WALL_B).contains(x, y)
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                val di = getDockIdx(x, y)
                if (di >= 0 && di == pressedDockIdx) launchDockApp(di)
                val wb = rf(BTN_WALL_L, BTN_WALL_T, BTN_WALL_R, BTN_WALL_B).contains(x, y)
                if (wb && wallBtnPressed) openWallpaperPicker()
                pressedDockIdx = -1; wallBtnPressed = false
                invalidate()
            }
            MotionEvent.ACTION_CANCEL -> { pressedDockIdx = -1; wallBtnPressed = false; invalidate() }
        }
    }

    private fun getDockIdx(x: Float, y: Float): Int {
        val dockRect = rf(DOCK_LEFT, DOCK_TOP, DOCK_RIGHT, DOCK_BOTTOM)
        if (!dockRect.contains(x, y)) return -1
        val count = dockApps.size
        val slotW = (dx(DOCK_RIGHT) - dx(DOCK_LEFT)) / count
        return ((x - dx(DOCK_LEFT)) / slotW).toInt().coerceIn(0, count - 1)
    }

    private fun launchDockApp(idx: Int) {
        val app = dockApps.getOrNull(idx) ?: return
        if (app.packageName == "__all__") {
            // 打开全部应用列表
            (context as? MainActivity)?.openAppDrawer()
            return
        }
        if (app.packageName.isBlank()) return
        try {
            val intent = context.packageManager.getLaunchIntentForPackage(app.packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
        } catch (e: Exception) { /* ignore */ }
    }

    private fun openWallpaperPicker() {
        val intent = Intent(context, WallpaperPickerActivity::class.java)
        (context as? MainActivity)?.startActivityForResult(intent, MainActivity.REQUEST_PICK_WALLPAPER)
    }

    // ── 绘制 ──────────────────────────────────────────────────────────────────
    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        sx = w / DESIGN_W
        sy = h / DESIGN_H
        updatePaintSizes()
        reloadWallpaper()
    }

    private fun updatePaintSizes() {
        dockLblPaint.textSize = dy(20f)
        statusPaint.textSize  = dy(24f)
        clockPaint.textSize   = dy(88f)
        datePaint.textSize    = dy(22f)
        wallBtnTxtP.textSize  = dy(20f)
    }

    override fun onDraw(canvas: Canvas) {
        drawBackground(canvas)
        drawStatusBar(canvas)
        drawClock(canvas)
        drawDock(canvas)
        drawWallpaperButton(canvas)
    }

    /** 壁纸背景 */
    private fun drawBackground(canvas: Canvas) {
        val bmp = wallpaperBitmap
        if (bmp != null && !bmp.isRecycled) {
            canvas.drawBitmap(bmp, null,
                RectF(0f, 0f, width.toFloat(), height.toFloat()), bmpPaint)
        } else {
            canvas.drawColor(Color.rgb(18, 22, 30))
        }
    }

    /** 顶部状态栏：时间 + 日期星期 */
    private fun drawStatusBar(canvas: Canvas) {
        // 半透明背景条
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(120, 0, 0, 0)
        }
        canvas.drawRect(0f, 0f, width.toFloat(), dy(STATUS_H), bgPaint)

        val now = Calendar.getInstance()
        val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        val dateFmt = SimpleDateFormat("MM月dd日 EEE", Locale.CHINESE)

        statusPaint.color = Color.WHITE
        statusPaint.isFakeBoldText = true
        canvas.drawText(timeFmt.format(now.time), dx(960f), dy(38f), statusPaint)

        statusPaint.textSize = dy(18f)
        statusPaint.isFakeBoldText = false
        statusPaint.color = Color.argb(200, 220, 220, 220)
        canvas.drawText(dateFmt.format(now.time), dx(960f), dy(54f), statusPaint)
        statusPaint.textSize = dy(24f)
    }

    /** 中央大时钟 */
    private fun drawClock(canvas: Canvas) {
        val now = Calendar.getInstance()
        val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())

        // 时钟阴影
        clockPaint.setShadowLayer(dy(6f), 0f, dy(3f), Color.argb(180, 0, 0, 0))
        clockPaint.color = Color.WHITE
        clockPaint.isFakeBoldText = true
        canvas.drawText(timeFmt.format(now.time), dx(760f), dy(340f), clockPaint)

        // 日期副标题
        val dateFmt = SimpleDateFormat("yyyy年MM月dd日  EEEE", Locale.CHINESE)
        datePaint.color = Color.argb(210, 240, 240, 240)
        datePaint.setShadowLayer(dy(3f), 0f, dy(2f), Color.argb(150, 0, 0, 0))
        canvas.drawText(dateFmt.format(now.time), dx(760f), dy(400f), datePaint)
    }

    /** 底部 Dock */
    private fun drawDock(canvas: Canvas) {
        val dockRect = rf(DOCK_LEFT, DOCK_TOP, DOCK_RIGHT, DOCK_BOTTOM)
        val count = dockApps.size
        val slotW = (dx(DOCK_RIGHT) - dx(DOCK_LEFT)) / count

        // Dock 背景
        dockBgPaint.color = Color.argb(160, 10, 10, 10)
        canvas.drawRoundRect(dockRect, dy(DOCK_RADIUS), dy(DOCK_RADIUS), dockBgPaint)

        // 边框
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = dy(1.5f)
            color = Color.argb(80, 255, 255, 255)
        }
        canvas.drawRoundRect(dockRect, dy(DOCK_RADIUS), dy(DOCK_RADIUS), strokePaint)

        dockApps.forEachIndexed { idx, app ->
            val slotLeft  = dx(DOCK_LEFT) + idx * slotW
            val slotRight = slotLeft + slotW
            val slotCX    = (slotLeft + slotRight) / 2f
            val slotTop   = dy(DOCK_TOP)
            val slotBot   = dy(DOCK_BOTTOM)

            // 按下高亮
            if (idx == pressedDockIdx) {
                val hlRect = RectF(slotLeft + dx(4f), slotTop + dy(4f),
                                   slotRight - dx(4f), slotBot - dy(4f))
                canvas.drawRoundRect(hlRect, dy(16f), dy(16f), hlPaint)
            }

            // 分隔线
            if (idx > 0) {
                val divPaint = Paint().apply {
                    color = Color.argb(50, 255, 255, 255)
                    strokeWidth = dy(1f)
                }
                canvas.drawLine(slotLeft, slotTop + dy(10f),
                                slotLeft, slotBot - dy(10f), divPaint)
            }

            val iconSize  = dy(64f)
            val iconTop   = slotTop + dy(12f)
            val iconLeft  = slotCX - iconSize / 2f

            // 图标
            val icon = app.icon
            if (icon != null) {
                icon.setBounds(iconLeft.toInt(), iconTop.toInt(),
                               (iconLeft + iconSize).toInt(), (iconTop + iconSize).toInt())
                icon.draw(canvas)
            } else {
                // 无图标时画圆形占位
                val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.argb(120, 100, 150, 200)
                }
                canvas.drawCircle(slotCX, iconTop + iconSize/2f, iconSize/2f, circlePaint)
            }

            // 标签
            dockLblPaint.color = Color.WHITE
            dockLblPaint.setShadowLayer(dy(2f), 0f, dy(1f), Color.argb(180,0,0,0))
            canvas.drawText(app.label, slotCX, slotBot - dy(8f), dockLblPaint)
        }
    }

    /** 换壁纸按钮（右下角，Dock右侧空白处） */
    private fun drawWallpaperButton(canvas: Canvas) {
        val r = rf(BTN_WALL_L, BTN_WALL_T, BTN_WALL_R, BTN_WALL_B)
        wallBtnPaint.color = if (wallBtnPressed)
            Color.argb(200, 60, 60, 60) else Color.argb(140, 20, 20, 20)
        canvas.drawRoundRect(r, dy(10f), dy(10f), wallBtnPaint)
        canvas.drawText("换壁纸", r.centerX(), r.centerY() + dy(7f), wallBtnTxtP)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        handler.removeCallbacks(clockTick)
        wallpaperBitmap?.recycle()
    }
}
