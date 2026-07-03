package com.yourpackage.wallpaper

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.view.View
import android.view.animation.DecelerateInterpolator
import java.util.*

/**
 * 翻页时钟 View
 *
 * 参照图片样式：每个数字一张卡片，翻页动画用 Camera 3D 旋转模拟。
 * 布局：HH : MM : SS 三组卡片横排，居中显示。
 * 卡片分上下两半：上半显示当前值，翻转时上半向下旋转露出下一个值。
 */
class FlipClockView(context: Context) : View(context) {

    // 每张卡片的设计尺寸（设计坐标，运行时按屏幕缩放）
    private val CARD_W = 200f
    private val CARD_H = 240f
    private val CARD_GAP = 20f       // 卡片间距
    private val GROUP_GAP = 60f      // HH:MM:SS 组间距（冒号位置）
    private val CORNER = 24f
    private val DIVIDER_H = 4f       // 上下半中间分割线高度

    private var sx = 1f
    private var sy = 1f
    private fun dw(v: Float) = v * sx
    private fun dh(v: Float) = v * sy

    // 当前显示的时分秒（各两位数字）
    private var curH1 = -1; private var curH2 = -1
    private var curM1 = -1; private var curM2 = -1
    private var curS1 = -1; private var curS2 = -1

    // 上一帧的值（用于触发翻转动画）
    private var prevH1 = -1; private var prevH2 = -1
    private var prevM1 = -1; private var prevM2 = -1
    private var prevS1 = -1; private var prevS2 = -1

    // 6张卡片各自的翻转进度 0f=静止 1f=翻转完成
    private val flipProgress = FloatArray(6) { 0f }
    private val animators = arrayOfNulls<ValueAnimator>(6)

    // Camera 用于 3D 旋转
    private val camera = Camera()
    private val matrix = Matrix()

    // Paint
    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        color = Color.WHITE
    }
    private val dividerPaint = Paint().apply {
        color = Color.argb(80, 0, 0, 0)
    }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        maskFilter = BlurMaskFilter(12f, BlurMaskFilter.Blur.NORMAL)
        color = Color.argb(100, 0, 0, 0)
    }

    // 卡片颜色（深色半透明，与壁纸融合）
    private val CARD_COLOR_TOP    = Color.argb(210, 30, 30, 40)
    private val CARD_COLOR_BOTTOM = Color.argb(210, 22, 22, 32)
    private val FLIP_COLOR        = Color.argb(210, 40, 40, 55)

    init {
        tick()
    }

    /** 外部每秒调用一次 */
    fun tick() {
        val cal = Calendar.getInstance()
        val h = cal.get(Calendar.HOUR_OF_DAY)
        val m = cal.get(Calendar.MINUTE)
        val s = cal.get(Calendar.SECOND)

        val newH1 = h / 10; val newH2 = h % 10
        val newM1 = m / 10; val newM2 = m % 10
        val newS1 = s / 10; val newS2 = s % 10

        checkAndFlip(0, prevH1, newH1) { prevH1 = curH1; curH1 = newH1 }
        checkAndFlip(1, prevH2, newH2) { prevH2 = curH2; curH2 = newH2 }
        checkAndFlip(2, prevM1, newM1) { prevM1 = curM1; curM1 = newM1 }
        checkAndFlip(3, prevM2, newM2) { prevM2 = curM2; curM2 = newM2 }
        checkAndFlip(4, prevS1, newS1) { prevS1 = curS1; curS1 = newS1 }
        checkAndFlip(5, prevS2, newS2) { prevS2 = curS2; curS2 = newS2 }

        invalidate()
    }

    private inline fun checkAndFlip(idx: Int, prev: Int, new: Int, update: () -> Unit) {
        if (new != prev) {
            update()
            startFlip(idx)
        }
    }

    private fun startFlip(idx: Int) {
        animators[idx]?.cancel()
        val anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 350
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                flipProgress[idx] = it.animatedValue as Float
                invalidate()
            }
        }
        animators[idx] = anim
        anim.start()
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        // 以高度为基准缩放（横屏 720 高）
        sy = h / 720f
        sx = sy  // 保持正方形卡片
        textPaint.textSize = dh(CARD_H * 0.62f)
        shadowPaint.maskFilter = BlurMaskFilter(dh(8f), BlurMaskFilter.Blur.NORMAL)
    }

    override fun onDraw(canvas: Canvas) {
        val totalW = dw(CARD_W) * 6 + dw(CARD_GAP) * 5 + dw(GROUP_GAP) * 2
        val startX = (width - totalW) / 2f
        val startY = (height - dh(CARD_H)) / 2f - dh(20f)  // 略微上移，给日期留空间

        // 绘制6张卡片：HH MM SS
        for (i in 0..5) {
            val groupOffset = (i / 2) * dw(GROUP_GAP)
            val x = startX + i * (dw(CARD_W) + dw(CARD_GAP)) + groupOffset
            val y = startY

            val cur = getDigit(i, false)
            val prev = getDigit(i, true)
            val prog = flipProgress[i]

            drawCard(canvas, x, y, cur, prev, prog)
        }

        // 冒号
        val colonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(200, 255, 255, 255)
            textSize = dh(80f)
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setShadowLayer(dh(4f), 0f, dh(2f), Color.argb(150, 0, 0, 0))
        }
        val cy = startY + dh(CARD_H) / 2f + dh(28f)
        // 第一个冒号（HH 和 MM 之间）
        val colon1X = startX + dw(CARD_W) * 2 + dw(CARD_GAP) * 1.5f + dw(GROUP_GAP) * 0.5f
        // 第二个冒号（MM 和 SS 之间）
        val colon2X = startX + dw(CARD_W) * 4 + dw(CARD_GAP) * 3.5f + dw(GROUP_GAP) * 1.5f
        canvas.drawText(":", colon1X, cy, colonPaint)
        canvas.drawText(":", colon2X, cy, colonPaint)
    }

    /**
     * 绘制单张翻页卡片
     * @param x, y 卡片左上角
     * @param cur  当前数字
     * @param prev 上一个数字（翻转动画用）
     * @param prog 翻转进度 0..1
     */
    private fun drawCard(canvas: Canvas, x: Float, y: Float,
                         cur: Int, prev: Int, prog: Float) {
        val w = dw(CARD_W)
        val h = dh(CARD_H)
        val halfH = h / 2f
        val r = dh(CORNER)

        val rect = RectF(x, y, x + w, y + h)
        val topRect = RectF(x, y, x + w, y + halfH)
        val botRect = RectF(x, y + halfH, x + w, y + h)

        // ── 静态底层：下半（cur）+ 上半（cur）──────────────────────────────
        // 下半卡片背景（当前数字下半部分）
        cardPaint.color = CARD_COLOR_BOTTOM
        canvas.drawRoundRect(rect, r, r, cardPaint)
        // 覆盖上半让下半颜色稍亮
        cardPaint.color = CARD_COLOR_TOP
        canvas.drawRoundRect(topRect, r, r, cardPaint)
        // 修复底部圆角被上半盖住
        cardPaint.color = CARD_COLOR_BOTTOM
        canvas.drawRect(x, y + halfH - r, x + w, y + halfH, cardPaint)

        // 上半文字（cur）
        drawDigitClipped(canvas, cur, x, y, w, h, true)
        // 下半文字（cur）
        drawDigitClipped(canvas, cur, x, y, w, h, false)

        // 分割线
        canvas.drawRect(x, y + halfH - dh(DIVIDER_H / 2),
            x + w, y + halfH + dh(DIVIDER_H / 2), dividerPaint)

        if (prog <= 0f) return

        // ── 翻转动画层 ────────────────────────────────────────────────────────
        // 翻转分两阶段：
        // prog 0..0.5：上半从水平旋转到垂直（prev的上半翻下去）
        // prog 0.5..1：从垂直旋转到水平（cur的上半翻出来）

        canvas.save()
        canvas.clipRect(x, y, x + w, y + halfH)  // 只影响上半区域

        if (prog < 0.5f) {
            // 前半段：prev 的上半翻转
            val angle = prog * 2f * 90f  // 0→90°
            drawFlipHalf(canvas, prev, x, y, w, h, angle, true)
        } else {
            // 后半段：cur 的上半从 90° 翻回 0°
            val angle = (1f - prog) * 2f * 90f  // 90°→0°
            drawFlipHalf(canvas, cur, x, y, w, h, angle, false)
        }

        canvas.restore()
    }

    /**
     * 绘制翻转中的上半卡片（用 Camera 做 3D 旋转）
     * @param angle 旋转角度 0=正面朝上 90=侧面
     * @param isPrev true=prev数字 false=cur数字
     */
    private fun drawFlipHalf(canvas: Canvas, digit: Int,
                              x: Float, y: Float, w: Float, h: Float,
                              angle: Float, isPrev: Boolean) {
        val halfH = h / 2f
        val cx = x + w / 2f
        val cy = y + halfH

        // 用 Bitmap 离屏绘制上半内容
        val bmp = Bitmap.createBitmap(w.toInt().coerceAtLeast(1),
            halfH.toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val bmpCanvas = Canvas(bmp)

        // 背景
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = if (isPrev) CARD_COLOR_TOP else FLIP_COLOR
        bmpCanvas.drawRoundRect(RectF(0f, 0f, w, halfH),
            dh(CORNER), dh(CORNER), p)

        // 数字（上半部分）
        textPaint.color = Color.WHITE
        bmpCanvas.drawText(digit.toString(), w / 2f, halfH * 1.05f, textPaint)

        // Camera 3D 旋转
        camera.save()
        camera.rotateX(angle)
        camera.getMatrix(matrix)
        camera.restore()

        matrix.preTranslate(-cx, -cy)
        matrix.postTranslate(cx, cy - halfH)

        val bmpPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        // 翻转后半段加渐变阴影增加立体感
        if (!isPrev) {
            bmpPaint.alpha = ((1f - angle / 90f) * 255).toInt().coerceIn(0, 255)
        }
        canvas.drawBitmap(bmp, matrix, bmpPaint)
        bmp.recycle()
    }

    /** 裁剪绘制数字的上半或下半 */
    private fun drawDigitClipped(canvas: Canvas, digit: Int,
                                  x: Float, y: Float, w: Float, h: Float,
                                  isTop: Boolean) {
        val halfH = h / 2f
        canvas.save()
        if (isTop) {
            canvas.clipRect(x, y, x + w, y + halfH)
            // 文字基线在卡片垂直中心
            canvas.drawText(digit.toString(), x + w / 2f, y + halfH * 1.05f, textPaint)
        } else {
            canvas.clipRect(x, y + halfH, x + w, y + h)
            canvas.drawText(digit.toString(), x + w / 2f, y + halfH * 1.05f, textPaint)
        }
        canvas.restore()
    }

    private fun getDigit(idx: Int, prev: Boolean): Int {
        return when (idx) {
            0 -> if (prev) prevH1 else curH1
            1 -> if (prev) prevH2 else curH2
            2 -> if (prev) prevM1 else curM1
            3 -> if (prev) prevM2 else curM2
            4 -> if (prev) prevS1 else curS1
            5 -> if (prev) prevS2 else curS2
            else -> 0
        }.coerceAtLeast(0)
    }
}
