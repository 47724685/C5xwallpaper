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

class HomeView(context: Context) : View(context) {

    private val DESIGN_W = 1920f
    private val DESIGN_H = 720f
    private val DOCK_LEFT   = 190f
    private val DOCK_RIGHT  = 1730f
    private val DOCK_TOP    = 525f
    private val DOCK_BOTTOM = 710f
    private val DOCK_RADIUS = 38f
    private val STATUS_H    = 58f
    private val BTN_WALL_L  = 1780f
    private val BTN_WALL_T  = 650f
    private val BTN_WALL_R  = 1900f
    private val BTN_WALL_B  = 705f

    private var sx = 1f
    private var sy = 1f
    private fun dx(v: Float) = v * sx
    private fun dy(v: Float) = v * sy
    private fun rf(l: Float, t: Float, r: Float, b: Float) =
        RectF(dx(l), dy(t), dx(r), dy(b))

    data class DockApp(val label: String, val packageName: String, val icon: Drawable?)

    private val dockApps = mutableListOf<DockApp>()
    private val dockManager = DockManager(context)

    // ── 音乐/歌词 ─────────────────────────────────────────────────────────────
    private var lrcLines: List<LrcParser.LrcLine> = emptyList()
    private var lastLrcTitle = ""
    // 轮询间隔：歌词每200ms更新一次（跟随播放位置）
    private val lrcPollRunnable = object : Runnable {
        override fun run() {
            // 双通道：优先 NotificationListener，降级到 MediaSession 轮询
            if (MusicNotificationListener.currentTitle.isEmpty()) {
                MusicNotificationListener.pollMediaSession(context)
            }
            // 检查歌曲是否切换，切换则重新加载LRC
            val title = MusicNotificationListener.currentTitle
            if (title.isNotEmpty() && title != lastLrcTitle) {
                lastLrcTitle = title
                val artist = MusicNotificationListener.currentArtist
                Thread {
                    lrcLines = LrcParser.findAndParse(title, artist)
                }.start()
            }
            invalidate()
            handler.postDelayed(this, 200)
        }
    }""

    // 歌词显示 Paint
    private val lrcBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(100, 10, 10, 10)
    }
    private val lrcTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val lrcLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(220, 240, 240, 240)
        textAlign = Paint.Align.CENTER
    }
    private val lrcDimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(140, 180, 180, 180)
        textAlign = Paint.Align.CENTER
    }
    private var longPressIdx = -1
    private var longPressRunnable: Runnable? = null
    private var wallpaperBitmap: Bitmap? = null
    private var pressedDockIdx = -1
    private var wallBtnPressed = false

    // 翻页时钟
    val flipClock = FlipClockView(context)

    private val bmpPaint     = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val dockBgPaint  = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dockLblPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val statusPaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val datePaint    = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val wallBtnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(160,20,20,20) }
    private val wallBtnTxtP  = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textAlign = Paint.Align.CENTER
    }
    private val hlPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(80, 255,255,255)
    }

    private val handler = Handler(Looper.getMainLooper())
    private val clockTick = object : Runnable {
        override fun run() {
            flipClock.tick()
            invalidate()
            handler.postDelayed(this, 100)   // 100ms轮询，保证秒变化时动画不晚于100ms
        }
    }

    init {
        loadDockApps()
        handler.post(clockTick)
        setOnTouchListener { _, ev -> handleTouch(ev); true }

        // 启动歌词轮询（200ms更新）
        MusicNotificationListener.onChanged = {
            val title = MusicNotificationListener.currentTitle
            if (title != lastLrcTitle) {
                lastLrcTitle = title
                if (title.isEmpty()) {
                    lrcLines = emptyList()
                } else {
                    Thread {
                        lrcLines = LrcParser.findAndParse(
                            title, MusicNotificationListener.currentArtist)
                    }.start()
                }
            }
            invalidate()
        }
        handler.postDelayed(lrcPollRunnable, 1000)
    }

    private fun loadDockApps() {
        dockApps.clear()
        val pm = context.packageManager

        // 优先匹配的包名列表
        val slots = listOf(
            "导航"  to listOf("com.autonavi.amapauto","com.baidu.BaiduMap","com.autonavi.miniinternational","com.autonavi.map"),
            "音乐"  to listOf("com.hsae.d531mc.music","com.android.music","com.netease.cloudmusic","com.kugou.android"),
            "空调"  to listOf("com.hsae.d531mc.ac","com.hsae.car.ac","com.hsae.auto.ac","com.hsae.ac"),
            "车辆"  to listOf("com.hsae.d531mc.carinfo","com.hsae.car","com.hsae.auto","com.hsae.carinfo"),
            "设置"  to listOf("com.android.settings","com.hsae.d531mc.settings"),
            "应用"  to emptyList()
        )

        // 获取系统所有可启动App（用于兜底）
        val launchIntent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
            addCategory(android.content.Intent.CATEGORY_LAUNCHER)
        }
        val allApps = pm.queryIntentActivities(launchIntent, 0)
            .filter { it.activityInfo.packageName != context.packageName }
            .sortedBy { it.loadLabel(pm).toString() }

        var fallbackIdx = 0  // 兜底App轮流填充

        slots.forEachIndexed { slotIdx, pair ->
            val (label, pkgs) = pair
            if (label == "应用") { dockApps.add(DockApp("应用","__all__",null)); return@forEachIndexed }

            // 优先读取用户自定义配置
            val custom = dockManager.loadSlot(slotIdx)
            if (custom != null) {
                val (pkg, lbl) = custom
                val icon = try { pm.getApplicationIcon(pkg) } catch (e: Exception) { null }
                dockApps.add(DockApp(lbl.ifBlank { label }, pkg, icon))
                return@forEachIndexed
            }

            // 无自定义时用默认包名
            val found = pkgs.firstOrNull { pkg ->
                try { pm.getApplicationInfo(pkg, 0); true } catch (e: Exception) { false }
            }
            if (found != null) {
                val icon = try { pm.getApplicationIcon(found) } catch (e: Exception) { null }
                dockApps.add(DockApp(label, found, icon))
            } else {
                val fallback = allApps.getOrNull(fallbackIdx++)
                if (fallback != null) {
                    val pkg  = fallback.activityInfo.packageName
                    val lbl  = fallback.loadLabel(pm).toString().take(4)
                    val icon = try { pm.getApplicationIcon(pkg) } catch (e: Exception) { null }
                    dockApps.add(DockApp(lbl, pkg, icon))
                } else {
                    dockApps.add(DockApp(label, "", null))
                }
            }
        }
    }

    fun reloadWallpaper() {
        wallpaperBitmap?.recycle()
        val prefs = context.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
        val type = prefs.getString(MainActivity.KEY_WALLPAPER_TYPE, "builtin") ?: "builtin"
        wallpaperBitmap = if (type == "custom") {
            val path = prefs.getString(MainActivity.KEY_WALLPAPER_PATH, null)
            if (path != null && File(path).exists()) decodeSafe(path) else loadBuiltin(0)
        } else loadBuiltin(prefs.getInt(MainActivity.KEY_BUILTIN_INDEX, 0))
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


    private fun handleTouch(ev: MotionEvent) {
        val x = ev.x; val y = ev.y


        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                pressedDockIdx = getDockIdx(x, y)
                wallBtnPressed = rf(BTN_WALL_L, BTN_WALL_T, BTN_WALL_R, BTN_WALL_B).contains(x, y)
                // 长按检测（600ms）
                if (pressedDockIdx >= 0) {
                    val idx = pressedDockIdx
                    val lp = Runnable {
                        longPressIdx = idx
                        pressedDockIdx = -1
                        invalidate()
                        showDockPicker(idx)
                    }
                    longPressRunnable = lp
                    handler.postDelayed(lp, 600)
                }
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                // 移动超过阈值取消长按
                val di = getDockIdx(x, y)
                if (di != pressedDockIdx) {
                    longPressRunnable?.let { handler.removeCallbacks(it) }
                    longPressRunnable = null
                }
            }
            MotionEvent.ACTION_UP -> {
                longPressRunnable?.let { handler.removeCallbacks(it) }
                longPressRunnable = null
                val di = getDockIdx(x, y)
                if (di >= 0 && di == pressedDockIdx) launchDockApp(di)
                if (rf(BTN_WALL_L, BTN_WALL_T, BTN_WALL_R, BTN_WALL_B).contains(x, y) && wallBtnPressed)
                    openWallpaperPicker()
                pressedDockIdx = -1; wallBtnPressed = false; invalidate()
            }
            MotionEvent.ACTION_CANCEL -> {
                longPressRunnable?.let { handler.removeCallbacks(it) }
                longPressRunnable = null
                pressedDockIdx = -1; wallBtnPressed = false; invalidate()
            }
        }
    }

    private fun showDockPicker(slotIdx: Int) {
        dockManager.showPicker(slotIdx) { pkg, label, icon ->
            // 更新对应槽位
            if (slotIdx < dockApps.size) {
                dockApps[slotIdx] = DockApp(label, pkg, icon)
                invalidate()
            }
        }
    }

    private fun getDockIdx(x: Float, y: Float): Int {
        val dockRect = rf(DOCK_LEFT, DOCK_TOP, DOCK_RIGHT, DOCK_BOTTOM)
        if (!dockRect.contains(x, y)) return -1
        val slotW = (dx(DOCK_RIGHT) - dx(DOCK_LEFT)) / dockApps.size
        return ((x - dx(DOCK_LEFT)) / slotW).toInt().coerceIn(0, dockApps.size - 1)
    }

    private fun launchDockApp(idx: Int) {
        val app = dockApps.getOrNull(idx) ?: return
        if (app.packageName == "__all__") { (context as? MainActivity)?.openAppDrawer(); return }
        if (app.packageName.isBlank()) return
        try {
            val intent = context.packageManager.getLaunchIntentForPackage(app.packageName)
            intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (intent != null) context.startActivity(intent)
        } catch (e: Exception) {}
    }

    private fun openWallpaperPicker() {
        val intent = Intent(context, WallpaperPickerActivity::class.java)
        (context as? MainActivity)?.startActivityForResult(intent, MainActivity.REQUEST_PICK_WALLPAPER)
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        sx = w / DESIGN_W
        sy = h / DESIGN_H
        dockLblPaint.textSize = dy(20f)
        statusPaint.textSize  = dy(24f)
        datePaint.textSize    = dy(22f)
        wallBtnTxtP.textSize  = dy(20f)
        lrcTitlePaint.textSize = dy(18f)
        lrcLinePaint.textSize  = dy(20f)
        lrcDimPaint.textSize   = dy(18f)
        // 翻页时钟铺满中央区域（状态栏下方到Dock上方）
        flipClock.layout(0, dy(STATUS_H).toInt(), w, dy(DOCK_TOP).toInt())
        reloadWallpaper()
    }

    override fun onDraw(canvas: Canvas) {
        drawBackground(canvas)
        drawStatusBar(canvas)
        drawFlipClock(canvas)
        drawDateBelow(canvas)
        drawLyricBar(canvas)
        drawDock(canvas)
        drawWallpaperButton(canvas)
    }

    private fun drawBackground(canvas: Canvas) {
        val bmp = wallpaperBitmap
        if (bmp != null && !bmp.isRecycled)
            canvas.drawBitmap(bmp, null, RectF(0f, 0f, width.toFloat(), height.toFloat()), bmpPaint)
        else canvas.drawColor(Color.rgb(18, 22, 30))
    }

    private fun drawStatusBar(canvas: Canvas) {
        val bgPaint = Paint().apply { color = Color.argb(120, 0, 0, 0) }
        canvas.drawRect(0f, 0f, width.toFloat(), dy(STATUS_H), bgPaint)
        val now = Calendar.getInstance()
        val dateFmt = SimpleDateFormat("MM月dd日 EEE", Locale.CHINESE)
        statusPaint.color = Color.WHITE
        statusPaint.isFakeBoldText = true
        canvas.drawText(SimpleDateFormat("HH:mm", Locale.getDefault()).format(now.time),
            dx(960f), dy(38f), statusPaint)
        statusPaint.textSize = dy(18f)
        statusPaint.isFakeBoldText = false
        statusPaint.color = Color.argb(200, 220, 220, 220)
        canvas.drawText(dateFmt.format(now.time), dx(960f), dy(54f), statusPaint)
        statusPaint.textSize = dy(24f)
    }

    /** 把 FlipClockView 的内容绘制到全宽中央区域 */
    private fun drawFlipClock(canvas: Canvas) {
        val clockTop = dy(STATUS_H)
        val clockBot = dy(DOCK_TOP) - dy(36f)
        val w = width
        val h = (clockBot - clockTop).toInt().coerceAtLeast(1)
        canvas.save()
        canvas.translate(0f, clockTop)
        if (flipClock.width != w || flipClock.height != h) {
            flipClock.measure(
                MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY)
            )
            flipClock.layout(0, 0, w, h)
        }
        flipClock.draw(canvas)
        canvas.restore()
    }


    /** 翻页时钟下方日期文字 */
    private fun drawDateBelow(canvas: Canvas) {
        val now = Calendar.getInstance()
        val dateFmt = SimpleDateFormat("yyyy年MM月dd日  EEEE", Locale.CHINESE)
        datePaint.color = Color.argb(200, 240, 240, 240)
        datePaint.setShadowLayer(dy(3f), 0f, dy(2f), Color.argb(150, 0, 0, 0))
        // 日期显示在翻页时钟区域底部
        val dateY = dy(DOCK_TOP) - dy(18f)
        canvas.drawText(dateFmt.format(now.time), width / 2f, dateY, datePaint)
    }

    /**
     * 歌词条：显示在 Dock 栏上方
     * 有 LRC 时显示当前行+上下相邻行（3行滚动效果）
     * 无 LRC 时只显示歌曲名+歌手
     */
    private fun drawLyricBar(canvas: Canvas) {
        // 没有播放中的音乐，不显示
        val title = MusicNotificationListener.currentTitle
        if (title.isEmpty()) return
        val artist = MusicNotificationListener.currentArtist

        // 歌词条高度和位置：Dock 上方，紧贴 Dock
        val barH   = dy(52f)
        val barTop = dy(DOCK_TOP) - barH - dy(4f)
        val barL   = dx(DOCK_LEFT)
        val barR   = dx(DOCK_RIGHT)
        val barW   = barR - barL

        // 背景
        canvas.drawRoundRect(
            RectF(barL, barTop, barR, barTop + barH),
            dy(12f), dy(12f), lrcBgPaint
        )

        val centerX = (barL + barR) / 2f
        val centerY = barTop + barH / 2f

        if (lrcLines.isEmpty()) {
            // 无 LRC：显示 "歌曲名 - 歌手"
            val info = "$title  —  $artist"
            lrcTitlePaint.textSize = dy(19f)
            canvas.drawText(info, centerX, centerY + dy(7f), lrcTitlePaint)
        } else {
            // 有 LRC：显示当前行±1行
            val pos = MusicNotificationListener.positionMs
            val idx = LrcParser.getCurrentLine(lrcLines, pos)

            val prevText = if (idx > 0) lrcLines[idx - 1].text else ""
            val curText  = if (idx >= 0) lrcLines[idx].text else ""
            val nextText = if (idx < lrcLines.size - 1) lrcLines[idx + 1].text else ""

            // 三行：上（暗）/ 中（亮）/ 下（暗）
            // 因为条高只有52dp，只显示当前行和歌曲名
            lrcLinePaint.textSize  = dy(20f)
            lrcDimPaint.textSize   = dy(15f)
            lrcTitlePaint.textSize = dy(14f)

            // 上方小字：歌曲名 - 歌手（作为标题）
            lrcDimPaint.color = Color.argb(140, 180, 180, 180)
            canvas.drawText(
                "$title · $artist",
                centerX, barTop + dy(15f), lrcDimPaint
            )
            // 中间大字：当前歌词行
            lrcLinePaint.color = Color.WHITE
            canvas.drawText(curText, centerX, barTop + dy(38f), lrcLinePaint)
        }
    }

    private fun drawDock(canvas: Canvas) {
        val dockRect = rf(DOCK_LEFT, DOCK_TOP, DOCK_RIGHT, DOCK_BOTTOM)
        dockBgPaint.color = Color.argb(100, 10, 10, 10)
        canvas.drawRoundRect(dockRect, dy(DOCK_RADIUS), dy(DOCK_RADIUS), dockBgPaint)
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = dy(1.5f)
            color = Color.argb(80, 255, 255, 255)
        }
        canvas.drawRoundRect(dockRect, dy(DOCK_RADIUS), dy(DOCK_RADIUS), strokePaint)

        val count = dockApps.size
        val slotW = (dx(DOCK_RIGHT) - dx(DOCK_LEFT)) / count
        dockApps.forEachIndexed { idx, app ->
            val slotLeft = dx(DOCK_LEFT) + idx * slotW
            val slotRight = slotLeft + slotW
            val slotCX = (slotLeft + slotRight) / 2f
            val slotTop = dy(DOCK_TOP); val slotBot = dy(DOCK_BOTTOM)
            if (idx == pressedDockIdx)
                canvas.drawRoundRect(RectF(slotLeft+dx(4f), slotTop+dy(4f),
                    slotRight-dx(4f), slotBot-dy(4f)), dy(16f), dy(16f), hlPaint)
            if (idx > 0) {
                val dp = Paint().apply { color = Color.argb(50,255,255,255); strokeWidth = dy(1f) }
                canvas.drawLine(slotLeft, slotTop+dy(10f), slotLeft, slotBot-dy(10f), dp)
            }
            val iconSize = dy(52f)
            val slotH    = slotBot - slotTop
            val iconTop  = slotTop + (slotH - iconSize - dy(16f)) / 2f
            val iconLeft = slotCX - iconSize / 2f
            val icon = app.icon
            if (icon != null) {
                icon.setBounds(iconLeft.toInt(), iconTop.toInt(),
                    (iconLeft+iconSize).toInt(), (iconTop+iconSize).toInt())
                icon.draw(canvas)
            } else {
                val cp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(120,100,150,200) }
                canvas.drawCircle(slotCX, iconTop+iconSize/2f, iconSize/2f, cp)
            }
            dockLblPaint.color = Color.WHITE
            dockLblPaint.setShadowLayer(dy(2f), 0f, dy(1f), Color.argb(180,0,0,0))
            canvas.drawText(app.label, slotCX, slotBot - dy(8f), dockLblPaint)
        }
    }

    private fun drawWallpaperButton(canvas: Canvas) {
        val r = rf(BTN_WALL_L, BTN_WALL_T, BTN_WALL_R, BTN_WALL_B)
        wallBtnPaint.color = if (wallBtnPressed) Color.argb(200,60,60,60) else Color.argb(140,20,20,20)
        canvas.drawRoundRect(r, dy(10f), dy(10f), wallBtnPaint)
        canvas.drawText("换壁纸", r.centerX(), r.centerY() + dy(7f), wallBtnTxtP)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        handler.removeCallbacks(clockTick)
        handler.removeCallbacks(lrcPollRunnable)
        wallpaperBitmap?.recycle()
    }
}
