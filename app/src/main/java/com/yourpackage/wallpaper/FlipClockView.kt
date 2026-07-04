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
 * 三张卡片（HH / MM / SS），每张显示两位数字，上下各一半。
 *
 * 翻页逻辑：
 *   前半程(0→0.5)：旧数字的上半从 0°旋转到 90°（翻走）
 *   后半程(0.5→1)：新数字的上半从 90°旋转回 0°（翻进来）
 *   下半始终显示：前半程=旧数字下半，后半程=新数字下半
 */
class FlipClockView(context: Context) : View(context) {

    private val curVal  = intArrayOf(0, 0, 0)   // HH MM SS 当前值
    private val prevVal = intArrayOf(0, 0, 0)   // 上一个值
    private val flipProg = FloatArray(3) { 0f }
    private val animators = arrayOfNulls<ValueAnimator>(3)
    private var initialized = false

    // 尺寸（onSizeChanged里赋值）
    private var cardW = 0f
    private var cardH = 0f
    private var gap   = 0f
    private var cr    = 0f   // 圆角半径

    // 颜色
    private val C_TOP  = Color.rgb(44, 44, 44)
    private val C_BOT  = Color.rgb(33, 33, 33)
    private val C_DIV  = Color.rgb(10, 10, 10)

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val txtPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color     = Color.rgb(200, 200, 200)
        textAlign = Paint.Align.CENTER
        typeface  = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }
    private val divPaint = Paint().apply { color = C_DIV }
    private val camera   = Camera()
    private val mtx      = Matrix()

    // ── 公开接口 ──────────────────────────────────────────────────────────────
    fun tick() {
        val cal = Calendar.getInstance()
        val newVals = intArrayOf(
            cal.get(Calendar.HOUR_OF_DAY),
            cal.get(Calendar.MINUTE),
            cal.get(Calendar.SECOND)
        )
        if (!initialized) {
            for (i in 0..2) { curVal[i] = newVals[i]; prevVal[i] = newVals[i] }
            initialized = true
            invalidate()
            return
        }
        for (i in 0..2) {
            if (newVals[i] != curVal[i]) {
                prevVal[i] = curVal[i]
                curVal[i]  = newVals[i]
                startFlip(i)
            }
        }
    }

    private fun startFlip(i: Int) {
        animators[i]?.cancel()
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 500
            interpolator = DecelerateInterpolator(1.5f)
            addUpdateListener { flipProg[i] = it.animatedValue as Float; invalidate() }
            animators[i] = this
            start()
        }
    }

    // ── 尺寸计算 ──────────────────────────────────────────────────────────────
    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        // 卡片高占 View 高 80%，宽=高*0.88，间距=高*5%
        cardH = h * 0.80f
        cardW = cardH * 0.88f
        gap   = h * 0.05f
        cr    = cardH * 0.08f
        txtPaint.textSize = cardH * 0.58f
    }

    // ── 绘制 ──────────────────────────────────────────────────────────────────
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
        val prog = flipProg[idx]
        val cur  = "%02d".format(curVal[idx])
        val prev = "%02d".format(prevVal[idx])

        val halfH = cardH / 2f

        // 决定下半显示哪个数字
        val botLabel = if (prog < 0.5f) prev else cur

        // 1. 下半（静态）
        drawHalfBitmap(canvas, botLabel, x, y, isTop = false, color = C_BOT)

        // 2. 上半（静态，始终是当前值）
        drawHalfBitmap(canvas, cur, x, y, isTop = true, color = C_TOP)

        // 3. 分割线
        canvas.drawRect(x, y + halfH - 2f, x + cardW, y + halfH + 2f, divPaint)

        if (prog <= 0f) return

        // 4. 翻转层（覆盖在上半区域）
        canvas.save()
        canvas.clipRect(x, y, x + cardW, y + halfH)
        if (prog < 0.5f) {
            // 旧数字上半翻走：0° → 90°
            val angle = prog / 0.5f * 90f
            drawFlippingHalf(canvas, prev, x, y, angle, C_TOP)
        } else {
            // 新数字上半翻进来：90° → 0°
            val angle = (1f - prog) / 0.5f * 90f
            drawFlippingHalf(canvas, cur, x, y, angle, C_TOP)
        }
        canvas.restore()
    }

    /**
     * 用 Bitmap 离屏绘制上半或下半，确保数字精确裁剪
     */
    private fun drawHalfBitmap(canvas: Canvas, label: String,
                                x: Float, y: Float,
                                isTop: Boolean, color: Int) {
        val halfH = cardH / 2f
        val bw = cardW.toInt().coerceAtLeast(1)
        val bh = halfH.toInt().coerceAtLeast(1)

        val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        val bc  = Canvas(bmp)

        // 背景（整张卡片画进 bitmap，再裁剪）
        bgPaint.color = color
        if (isTop) {
            // 上半：圆角在上，下边直角
            bc.drawRoundRect(RectF(0f, 0f, cardW, cardH), cr, cr, bgPaint)
        } else {
            // 下半：圆角在下，上边直角
            bc.drawRoundRect(RectF(0f, -halfH, cardW, halfH), cr, cr, bgPaint)
        }

        // 数字文字
        // 文字绘制在整张卡片的垂直中心，但只露出上半或下半
        val textY = if (isTop) {
            // 上半 bitmap：文字基线 = halfH + descent（让下半数字紧贴分割线）
            halfH + txtPaint.textSize * 0.36f
        } else {
            // 下半 bitmap：文字基线 = halfH + descent，但相对于下半偏移
            // 下半 bitmap 的 y=0 对应卡片的 y=halfH
            // 所以文字 Y = 卡片中心相对于下半顶部的偏移 = txtPaint.textSize * 0.36f
            txtPaint.textSize * 0.36f
        }
        bc.drawText(label, cardW / 2f, textY, txtPaint)

        val destY = if (isTop) y else y + halfH
        canvas.drawBitmap(bmp, x, destY, null)
        bmp.recycle()
    }

    /**
     * 绘制 3D 翻转中的上半
     * angle: 0°=正面朝上，90°=侧面（消失）
     */
    private fun drawFlippingHalf(canvas: Canvas, label: String,
                                  x: Float, y: Float,
                                  angle: Float, color: Int) {
        val halfH = cardH / 2f
        val bw = cardW.toInt().coerceAtLeast(1)
        val bh = halfH.toInt().coerceAtLeast(1)

        // 离屏绘制上半内容
        val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        val bc  = Canvas(bmp)
        bgPaint.color = color
        bc.drawRoundRect(RectF(0f, 0f, cardW, cardH), cr, cr, bgPaint)
        val textY = halfH + txtPaint.textSize * 0.36f
        bc.drawText(label, cardW / 2f, textY, txtPaint)

        // Camera 绕卡片下边缘（pivot = 上半底部）旋转
        camera.save()
        camera.rotateX(angle)
        camera.getMatrix(mtx)
        camera.restore()

        // pivot 点是 bitmap 的底边中心 (cardW/2, halfH)
        mtx.preTranslate(-cardW / 2f, -halfH)
        mtx.postTranslate(x + cardW / 2f, y + halfH)

        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        // 翻转到 90° 附近时淡出，增加真实感
        p.alpha = (255 * (1f - angle / 90f * 0.5f)).toInt().coerceIn(0, 255)

        canvas.drawBitmap(bmp, mtx, p)
        bmp.recycle()
    }
}
