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

    private var cardW  = 0f
    private var cardH  = 0f
    private var halfH  = 0f
    private var gap    = 0f
    private var cr     = 0f

    private val C_TOP = Color.rgb(44, 44, 44)
    private val C_BOT = Color.rgb(33, 33, 33)
    private val C_DIV = Color.rgb(10, 10, 10)
    private val C_TXT = Color.rgb(200, 200, 200)

    private val bgPaint  = Paint(Paint.ANTI_ALIAS_FLAG)
    private val divPaint = Paint().apply { color = Color.rgb(10,10,10) }
    private val txtPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color     = Color.rgb(200, 200, 200)
        textAlign = Paint.Align.CENTER
        typeface  = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }
    private val camera = Camera()
    private val mtx    = Matrix()

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
                prevVal[i] = curVal[i]; curVal[i] = nv[i]; startFlip(i)
            }
        }
    }

    private fun startFlip(i: Int) {
        animators[i]?.cancel()
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 500
            interpolator = DecelerateInterpolator(1.5f)
            addUpdateListener { flipProg[i] = it.animatedValue as Float; invalidate() }
            animators[i] = this; start()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        cardH = h * 0.80f
        cardW = cardH * 0.88f
        halfH = cardH / 2f
        gap   = h * 0.05f
        cr    = cardH * 0.08f
        txtPaint.textSize = cardH * 0.58f
    }

    override fun onDraw(canvas: Canvas) {
        if (cardH == 0f) return
        val totalW = cardW * 3 + gap * 2
        val ox = (width  - totalW) / 2f
        val oy = (height - cardH)  / 2f
        for (i in 0..2) drawCard(canvas, ox + i * (cardW + gap), oy, i)
    }

    private fun drawCard(canvas: Canvas, x: Float, y: Float, idx: Int) {
        val prog = flipProg[idx]
        val cur  = "%02d".format(curVal[idx])
        val prev = "%02d".format(prevVal[idx])

        // 1. 下半静态（前半程=prev，后半程=cur）
        val botLabel = if (prog < 0.5f) prev else cur
        drawStaticHalf(canvas, botLabel, x, y, isTop = false, C_BOT)

        // 2. 上半静态（始终=cur）
        drawStaticHalf(canvas, cur, x, y, isTop = true, C_TOP)

        // 3. 分割线
        canvas.drawRect(x, y+halfH-2f, x+cardW, y+halfH+2f, divPaint)

        if (prog <= 0f) return

        // 4. 翻转层（只影响上半区域）
        canvas.save()
        canvas.clipRect(x, y, x+cardW, y+halfH)
        if (prog < 0.5f) {
            // prev 上半翻走：0°→90°
            drawFlipHalf(canvas, prev, x, y, prog / 0.5f * 90f)
        } else {
            // cur 上半翻进来：90°→0°
            drawFlipHalf(canvas, cur, x, y, (1f - prog) / 0.5f * 90f)
        }
        canvas.restore()
    }

    /**
     * 绘制静态的上半或下半
     * 关键：文字在整张卡片垂直居中，上下各显示一半
     */
    private fun drawStaticHalf(canvas: Canvas, label: String,
                                x: Float, y: Float, isTop: Boolean, bgColor: Int) {
        val bw = cardW.toInt().coerceAtLeast(1)
        val bh = halfH.toInt().coerceAtLeast(1)

        // 用整张卡片高度绘制 bitmap，再 crop 上半或下半
        val fullBmp = Bitmap.createBitmap(bw, cardH.toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val fc = Canvas(fullBmp)

        // 背景
        bgPaint.color = bgColor
        fc.drawRoundRect(RectF(0f, 0f, cardW, cardH), cr, cr, bgPaint)

        // 文字垂直居中在整张卡片
        val bounds = Rect()
        txtPaint.getTextBounds(label, 0, label.length, bounds)
        val textY = cardH / 2f - bounds.exactCenterY()
        fc.drawText(label, cardW / 2f, textY, txtPaint)

        // crop 上半或下半
        val srcY = if (isTop) 0 else bh
        val cropped = Bitmap.createBitmap(fullBmp, 0, srcY, bw, bh)
        fullBmp.recycle()

        val destY = if (isTop) y else y + halfH
        canvas.drawBitmap(cropped, x, destY, null)
        cropped.recycle()
    }

    /**
     * 3D 翻转中的上半
     * pivot = 卡片上半底边中心（即分割线处）
     * angle 0°=正面 90°=侧面消失
     */
    private fun drawFlipHalf(canvas: Canvas, label: String,
                              x: Float, y: Float, angle: Float) {
        val bw = cardW.toInt().coerceAtLeast(1)
        val bh = halfH.toInt().coerceAtLeast(1)

        // 同样用整张卡片绘制再 crop 上半
        val fullBmp = Bitmap.createBitmap(bw, cardH.toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val fc = Canvas(fullBmp)
        bgPaint.color = C_TOP
        fc.drawRoundRect(RectF(0f, 0f, cardW, cardH), cr, cr, bgPaint)
        val bounds = Rect()
        txtPaint.getTextBounds(label, 0, label.length, bounds)
        val textY = cardH / 2f - bounds.exactCenterY()
        fc.drawText(label, cardW / 2f, textY, txtPaint)
        val halfBmp = Bitmap.createBitmap(fullBmp, 0, 0, bw, bh)
        fullBmp.recycle()

        // Camera 绕 bitmap 底边（pivot = 底边中心）旋转
        camera.save()
        camera.rotateX(angle)
        camera.getMatrix(mtx)
        camera.restore()

        // preTranslate：把 pivot(bw/2, bh) 移到原点
        // postTranslate：把原点移回屏幕上的 pivot 位置(x+cardW/2, y+halfH)
        mtx.preTranslate(-cardW / 2f, -halfH)
        mtx.postTranslate(x + cardW / 2f, y + halfH)

        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        p.alpha = (255 * (1f - angle / 90f * 0.4f)).toInt().coerceIn(0, 255)
        canvas.drawBitmap(halfBmp, mtx, p)
        halfBmp.recycle()
    }
}
