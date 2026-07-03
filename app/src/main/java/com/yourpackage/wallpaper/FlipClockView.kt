package com.yourpackage.wallpaper

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.view.View
import android.view.animation.DecelerateInterpolator
import java.util.*

/**
 * Split-flap 翻页时钟
 *
 * 三组卡片（HH / MM / SS），每组显示两位数字。
 * 每张卡片分上下两半，中间有分割线。
 *
 * 翻页动作：
 *   静止时：上半 = 当前数字上半，下半 = 当前数字下半
 *   翻转时：
 *     ① 下半立即切换为下一个数字的下半（静态）
 *     ② 上半执行 3D 翻转动画：
 *        - 前半程（0→0.5）：当前数字上半从 0° 翻到 90°（翻走）
 *        - 后半程（0.5→1）：下一个数字上半从 90° 翻回 0°（翻进来）
 */
class FlipClockView(context: Context) : View(context) {

    // 三组，每组一个两位数
    private val groups = intArrayOf(0, 0, 0)   // HH MM SS 当前值
    private val prevGroups = intArrayOf(-1, -1, -1)

    // 每组独立的翻转进度
    private val flipProg = FloatArray(3) { 0f }
    private val animators = arrayOfNulls<ValueAnimator>(3)

    private val camera = Camera()
    private val matrix = Matrix()

    // 尺寸（onSizeChanged 里计算）
    private var cardW = 0f
    private var cardH = 0f
    private var gap   = 0f    // 组间距
    private var r     = 0f    // 圆角
    private var divH  = 0f    // 分割线厚度

    private val bgPaint  = Paint(Paint.ANTI_ALIAS_FLAG)
    private val divPaint = Paint().apply { color = Color.argb(200, 0, 0, 0) }
    private val txtPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color     = Color.argb(220, 210, 210, 210)
        typeface  = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    // 卡片颜色（参照截图深灰）
    private val COLOR_TOP  = Color.rgb(42, 42, 42)
    private val COLOR_BOT  = Color.rgb(34, 34, 34)
    private val COLOR_FLIP = Color.rgb(50, 50, 50)

    init { tick() }

    /** 每秒调用，检查哪组数字变化了就触发翻转 */
    fun tick() {
        val cal = Calendar.getInstance()
        val newVals = intArrayOf(
            cal.get(Calendar.HOUR_OF_DAY),
            cal.get(Calendar.MINUTE),
            cal.get(Calendar.SECOND)
        )
        for (i in 0..2) {
            if (newVals[i] != groups[i]) {
                prevGroups[i] = groups[i]
                groups[i] = newVals[i]
                startFlip(i)
            }
        }
        // 首次初始化
        if (prevGroups[0] < 0) {
            for (i in 0..2) prevGroups[i] = groups[i]
        }
    }

    private fun startFlip(idx: Int) {
        animators[idx]?.cancel()
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 450
            interpolator = DecelerateInterpolator(1.2f)
            addUpdateListener {
                flipProg[idx] = it.animatedValue as Float
                invalidate()
            }
            animators[idx] = this
            start()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        // 三组卡片等宽，间距约卡片宽的 12%
        // 高度用屏幕高的 75%
        cardH = h * 0.75f
        cardW = cardH * 0.90f      // 宽比高略窄，接近截图比例
        gap   = cardW * 0.14f
        r     = cardH * 0.07f
        divH  = cardH * 0.008f

        txtPaint.textSize = cardH * 0.60f
        txtPaint.setShadowLayer(cardH * 0.01f, 0f, cardH * 0.005f, Color.BLACK)
    }

    override fun onDraw(canvas: Canvas) {
        if (cardH == 0f) return
        val totalW = cardW * 3 + gap * 2
        val startX = (width  - totalW) / 2f
        val startY = (height - cardH)  / 2f

        for (i in 0..2) {
            val x = startX + i * (cardW + gap)
            val cur  = groups[i].coerceIn(0, 59)
            val prev = prevGroups[i].coerceAtLeast(0)
            drawGroup(canvas, x, startY, cur, prev, flipProg[i])
        }
    }

    /**
     * 绘制一组（两位数字）
     */
    private fun drawGroup(canvas: Canvas, x: Float, y: Float,
                          cur: Int, prev: Int, prog: Float) {
        val halfH = cardH / 2f
        val label = "%02d".format(cur)
        val prevLabel = "%02d".format(prev)

        // ── 1. 静态下半：prog >= 0.5 时已经是 cur 的下半，否则是 prev 的下半 ──
        val botLabel = if (prog >= 0.5f) label else prevLabel
        drawHalf(canvas, botLabel, x, y, false, COLOR_BOT)

        // ── 2. 静态上半：当前 cur ──────────────────────────────────────────────
        drawHalf(canvas, label, x, y, true, COLOR_TOP)

        // ── 3. 分割线 ──────────────────────────────────────────────────────────
        canvas.drawRect(x, y + halfH - divH, x + cardW, y + halfH + divH, divPaint)

        if (prog <= 0f) return

        // ── 4. 翻转动画层（覆盖在上半） ────────────────────────────────────────
        canvas.save()
        canvas.clipRect(x, y, x + cardW, y + halfH)

        when {
            prog < 0.5f -> {
                // 前半：prevLabel 上半从 0° 翻到 90°（翻走）
                val angle = prog * 2f * 90f
                drawFlipHalf(canvas, prevLabel, x, y, angle, COLOR_TOP)
            }
            else -> {
                // 后半：cur label 上半从 90° 翻回 0°（翻进来）
                val angle = (1f - prog) * 2f * 90f
                drawFlipHalf(canvas, label, x, y, angle, COLOR_FLIP)
            }
        }

        canvas.restore()
    }

    /**
     * 绘制静态的上半或下半
     */
    private fun drawHalf(canvas: Canvas, label: String,
                         x: Float, y: Float, isTop: Boolean, bgColor: Int) {
        val halfH = cardH / 2f
        canvas.save()

        bgPaint.color = bgColor
        if (isTop) {
            // 上半：只画整张卡片然后裁掉下半（保留圆角）
            canvas.clipRect(x, y, x + cardW, y + halfH)
            canvas.drawRoundRect(RectF(x, y, x + cardW, y + cardH), r, r, bgPaint)
        } else {
            // 下半：裁掉上半
            canvas.clipRect(x, y + halfH, x + cardW, y + cardH)
            canvas.drawRoundRect(RectF(x, y, x + cardW, y + cardH), r, r, bgPaint)
        }

        // 文字：基线在卡片纵向中心，上下两半各显示一部分
        val textBaseline = y + halfH + txtPaint.textSize * 0.36f
        canvas.drawText(label, x + cardW / 2f, textBaseline, txtPaint)
        canvas.restore()
    }

    /**
     * 绘制翻转中的上半（Camera 3D 旋转）
     * angle: 0° = 正面朝上，90° = 侧面（消失）
     */
    private fun drawFlipHalf(canvas: Canvas, label: String,
                              x: Float, y: Float, angle: Float, bgColor: Int) {
        val halfH = cardH / 2f
        val bmpW = cardW.toInt().coerceAtLeast(1)
        val bmpH = halfH.toInt().coerceAtLeast(1)

        // 离屏绘制这半张卡片
        val bmp = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
        val bc  = Canvas(bmp)

        // 背景（含圆角，只画上半所以裁掉底部）
        val bp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bgColor }
        bc.drawRoundRect(RectF(0f, 0f, cardW, cardH), r, r, bp)

        // 文字（基线相对于整张卡片高度 = halfH + offset）
        val textBaseline = halfH + txtPaint.textSize * 0.36f
        bc.drawText(label, cardW / 2f, textBaseline, txtPaint)

        // Camera 绕卡片底边（y + halfH）旋转
        val pivotX = x + cardW / 2f
        val pivotY = y + halfH

        camera.save()
        camera.rotateX(angle)
        camera.getMatrix(matrix)
        camera.restore()

        matrix.preTranslate(-cardW / 2f, -halfH)
        matrix.postTranslate(pivotX, pivotY - halfH)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        // 接近 90° 时淡出，增加立体感
        paint.alpha = (255 * (1f - angle / 90f * 0.6f)).toInt().coerceIn(0, 255)

        canvas.drawBitmap(bmp, matrix, paint)
        bmp.recycle()
    }
}
