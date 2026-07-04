package com.yourpackage.wallpaper

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.view.View
import android.view.animation.DecelerateInterpolator
import java.util.*

class FlipClockView(context: Context) : View(context) {

    private val curVal   = intArrayOf(0, 0, 0)
    private val prevVal  = intArrayOf(0, 0, 0)
    private val flipProg = FloatArray(3) { 0f }
    private val animators = arrayOfNulls<ValueAnimator>(3)
    private var initialized = false

    // 运行时计算的尺寸
    private var cardW = 0f
    private var cardH = 0f
    private var gap   = 0f
    private var cr    = 0f

    private val bgPaint  = Paint(Paint.ANTI_ALIAS_FLAG)
    private val txtPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color     = Color.rgb(200, 200, 200)
        textAlign = Paint.Align.CENTER
        typeface  = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }
    private val divPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
    }

    private val C_TOP = Color.argb(180, 48, 48, 48)
    private val C_BOT = Color.argb(160, 36, 36, 36)

    // 预计算文字基线（相对于卡片顶部）
    private var textBaseline = 0f

    fun tick() {
        val cal = Calendar.getInstance()
        val nv = intArrayOf(
            cal.get(Calendar.HOUR_OF_DAY),
            cal.get(Calendar.MINUTE),
            cal.get(Calendar.SECOND)
        )
        if (!initialized) {
            for (i in 0..2) { curVal[i] = nv[i]; prevVal[i] = nv[i] }
            initialized = true; invalidate(); return
        }
        for (i in 0..2) {
            if (nv[i] != curVal[i]) {
                prevVal[i] = curVal[i]
                curVal[i]  = nv[i]
                startFlip(i)
            }
        }
    }

    private fun startFlip(i: Int) {
        animators[i]?.cancel()
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 500
            interpolator = DecelerateInterpolator(1.2f)
            addUpdateListener { flipProg[i] = it.animatedValue as Float; invalidate() }
            animators[i] = this; start()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        cardH = h * 0.80f
        cardW = cardH * 0.88f
        gap   = h * 0.05f
        cr    = cardH * 0.08f
        txtPaint.textSize = cardH * 0.56f

        // 预计算：文字垂直居中于整张卡片的基线
        // baseline = cardH/2 + 文字高度/2（精确用FontMetrics）
        val fm = txtPaint.fontMetrics
        textBaseline = cardH / 2f - (fm.ascent + fm.descent) / 2f
    }

    override fun onDraw(canvas: Canvas) {
        if (cardH == 0f) return
        val totalW = cardW * 3 + gap * 2
        val ox = (width  - totalW) / 2f
        val oy = (height - cardH)  / 2f
        for (i in 0..2) {
            drawCard(canvas, ox + i * (cardW + gap), oy, i)
        }
    }

    private fun drawCard(canvas: Canvas, x: Float, y: Float, idx: Int) {
        val prog  = flipProg[idx]
        val cur   = "%02d".format(curVal[idx])
        val prev  = "%02d".format(prevVal[idx])
        val halfH = cardH / 2f

        // ─── 下半（静态）───────────────────────────────────────────────────────
        // 前半程显示 prev 的下半，后半程显示 cur 的下半
        val botLabel = if (prog < 0.5f) prev else cur
        drawBottom(canvas, botLabel, x, y)

        // ─── 上半（静态，始终是 cur）──────────────────────────────────────────
        drawTop(canvas, cur, x, y)

        // ─── 分割线 ───────────────────────────────────────────────────────────
        canvas.drawRect(x, y + halfH - 3f, x + cardW, y + halfH + 3f, divPaint)

        if (prog <= 0f) return

        // ─── 翻转层 ───────────────────────────────────────────────────────────
        if (prog < 0.5f) {
            // 前半程：prev 的上半从 0°→90° 翻走
            val angle = (prog / 0.5f) * 90f
            drawFlippingTop(canvas, prev, x, y, angle)
        } else {
            // 后半程：cur 的上半从 90°→0° 翻进来
            val angle = ((1f - prog) / 0.5f) * 90f
            drawFlippingTop(canvas, cur, x, y, angle)
        }
    }

    /** 绘制卡片上半（裁剪到上半区域） */
    private fun drawTop(canvas: Canvas, label: String, x: Float, y: Float) {
        val halfH = cardH / 2f
        canvas.save()
        canvas.clipRect(x, y, x + cardW, y + halfH)
        // 整张卡片背景（圆角在上）
        bgPaint.color = C_TOP
        canvas.drawRoundRect(RectF(x, y, x + cardW, y + cardH), cr, cr, bgPaint)
        // 文字：基线是相对于卡片顶部的 textBaseline
        canvas.drawText(label, x + cardW / 2f, y + textBaseline, txtPaint)
        canvas.restore()
    }

    /** 绘制卡片下半（裁剪到下半区域） */
    private fun drawBottom(canvas: Canvas, label: String, x: Float, y: Float) {
        val halfH = cardH / 2f
        canvas.save()
        canvas.clipRect(x, y + halfH, x + cardW, y + cardH)
        // 整张卡片背景（圆角在下）
        bgPaint.color = C_BOT
        canvas.drawRoundRect(RectF(x, y, x + cardW, y + cardH), cr, cr, bgPaint)
        // 文字：同样基线，与上半对齐
        canvas.drawText(label, x + cardW / 2f, y + textBaseline, txtPaint)
        canvas.restore()
    }

    /**
     * 绘制翻转中的上半
     * 原理：先把上半内容画到离屏 Bitmap，再用 Camera 做 3D 旋转后贴回
     * pivot 点 = 卡片分割线中心（x + cardW/2, y + halfH）
     */
    private fun drawFlippingTop(canvas: Canvas, label: String,
                                 x: Float, y: Float, angle: Float) {
        val halfH = cardH / 2f
        val bw = cardW.toInt().coerceAtLeast(1)
        val bh = halfH.toInt().coerceAtLeast(1)

        // 离屏绘制上半内容（坐标系原点在上半左上角）
        val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        val bc  = Canvas(bmp)

        // 背景：画整张卡片（origin 偏移到 bitmap 坐标）再裁上半
        bc.save()
        bc.clipRect(0f, 0f, cardW, halfH)
        bgPaint.color = C_TOP
        bc.drawRoundRect(RectF(0f, 0f, cardW, cardH), cr, cr, bgPaint)
        bc.restore()

        // 文字基线相对于 bitmap 顶部（= textBaseline，因为 bitmap 顶部对应卡片顶部）
        bc.drawText(label, cardW / 2f, textBaseline, txtPaint)

        // Camera 3D 旋转，pivot = bitmap 底边中心 (cardW/2, halfH)
        val cam = Camera()
        cam.save()
        cam.rotateX(angle)
        val m = Matrix()
        cam.getMatrix(m)
        cam.restore()

        // 把 pivot 从 bitmap 底边中心变换到屏幕坐标
        m.preTranslate(-cardW / 2f, -halfH)   // 平移到原点
        m.postTranslate(x + cardW / 2f, y + halfH)  // 平移到屏幕 pivot

        // 只在上半区域内绘制（防止翻转时溢出到下半）
        canvas.save()
        canvas.clipRect(x, y, x + cardW, y + halfH)

        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        // 接近 90° 时略微淡出增加立体感
        p.alpha = (255 * (1f - angle / 90f * 0.35f)).toInt().coerceIn(0, 255)
        canvas.drawBitmap(bmp, m, p)
        canvas.restore()

        bmp.recycle()
    }
}
