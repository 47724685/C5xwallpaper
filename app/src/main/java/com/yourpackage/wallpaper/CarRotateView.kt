package com.yourpackage.wallpaper

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.*

/**
 * 汽车360°旋转展示 View
 *
 * 左右滑动旋转车身，用透视压缩模拟3D效果。
 * 车身用 Canvas 实时绘制，不依赖外部图片。
 *
 * 旋转原理：
 *   angle=0   → 正右侧面（最宽）
 *   angle=90  → 正前方（最窄）
 *   angle=180 → 正左侧面（最宽，镜像）
 *   cosA 决定透视压缩比，sinA 决定面朝方向
 */
class CarRotateView(context: Context) : View(context) {

    var rotAngle = 0f
        private set

    private var flingAnimator: ValueAnimator? = null
    private var showHint = true

    private val gestureDetector = GestureDetector(context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onScroll(e1: MotionEvent?, e2: MotionEvent,
                                  distanceX: Float, distanceY: Float): Boolean {
                flingAnimator?.cancel()
                rotAngle = (rotAngle - distanceX * 0.35f + 360f) % 360f
                showHint = false
                invalidate()
                return true
            }
            override fun onFling(e1: MotionEvent?, e2: MotionEvent,
                                 velocityX: Float, velocityY: Float): Boolean {
                startFling(-velocityX * 0.12f)
                return true
            }
        })

    private fun startFling(delta: Float) {
        flingAnimator?.cancel()
        val start = rotAngle
        flingAnimator = ValueAnimator.ofFloat(0f, delta).apply {
            duration = 900
            interpolator = DecelerateInterpolator(2.2f)
            addUpdateListener {
                rotAngle = (start + it.animatedValue as Float + 360f) % 360f
                invalidate()
            }
            start()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)
        return true
    }

    // ── Paint ────────────────────────────────────────────────────────────────
    private val bodyPaint   = Paint(Paint.ANTI_ALIAS_FLAG)
    private val darkPaint   = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glassPaint  = Paint(Paint.ANTI_ALIAS_FLAG)
    private val wheelPaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(22,22,24) }
    private val hubPaint    = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(155,155,160) }
    private val spokePaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(70,70,75)
        strokeWidth = 3.5f; style = Paint.Style.STROKE
    }
    private val lightPaint  = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tailPaint   = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(230,215,25,25) }
    private val grillPaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(18,18,20) }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hintPaint   = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(130, 200, 200, 200)
        textAlign = Paint.Align.CENTER
    }
    private val stripePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(40, 255,255,255)
        strokeWidth = 2f; style = Paint.Style.STROKE
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f + height * 0.04f
        drawCar(canvas, cx, cy, rotAngle)

        if (showHint) {
            hintPaint.textSize = height * 0.045f
            canvas.drawText("← 滑动旋转 →", cx, cy + height * 0.40f, hintPaint)
        }
    }

    private fun drawCar(canvas: Canvas, cx: Float, cy: Float, angle: Float) {
        val rad  = Math.toRadians(angle.toDouble())
        val cosA = cos(rad).toFloat()
        val sinA = sin(rad).toFloat()
        val facingRight = angle < 180f

        // 透视压缩
        val compress = abs(cosA)   // 1=侧面全宽, 0=正前/后最窄
        val maxCarW  = width * 0.74f
        val carW     = maxCarW * (0.28f + 0.72f * compress)
        val carX     = cx - carW / 2f

        val bodyH    = height * 0.21f
        val roofH    = height * 0.165f
        val baseY    = cy + height * 0.21f
        val bodyTop  = baseY - bodyH

        // 光影：正面/背面时稍暗，侧面时最亮
        val lf = (compress * 0.35f + 0.65f).coerceIn(0.6f, 1.0f)
        val r  = (238 * lf).toInt(); val g = (238 * lf).toInt(); val b = (242 * lf).toInt()

        // ── 地面阴影 ──────────────────────────────────────────
        val sGrad = RadialGradient(cx, baseY + 6f, carW * 0.52f,
            intArrayOf(Color.argb(110,0,0,0), Color.TRANSPARENT),
            floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
        shadowPaint.shader = sGrad
        canvas.drawOval(RectF(cx - carW*0.52f, baseY, cx + carW*0.52f, baseY + height*0.038f), shadowPaint)

        // ── 车身主体 ──────────────────────────────────────────
        bodyPaint.color = Color.rgb(r, g, b)
        val skirtTop = bodyTop + bodyH * 0.22f
        val bodyPath = Path().apply {
            moveTo(carX, skirtTop + bodyH * 0.18f)
            lineTo(carX + carW * 0.055f, bodyTop)
            lineTo(carX + carW * 0.945f, bodyTop)
            lineTo(carX + carW, skirtTop + bodyH * 0.18f)
            lineTo(carX + carW, baseY - height * 0.068f)
            lineTo(carX, baseY - height * 0.068f)
            close()
        }
        canvas.drawPath(bodyPath, bodyPaint)

        // 车身高光线
        canvas.drawLine(carX + carW*0.06f, bodyTop + 3f,
            carX + carW*0.94f, bodyTop + 3f, stripePaint)

        // 侧裙（稍暗）
        darkPaint.color = Color.rgb((r*0.77f).toInt(), (g*0.77f).toInt(), (b*0.78f).toInt())
        canvas.drawRoundRect(RectF(carX + 2f, baseY - height*0.105f,
            carX + carW - 2f, baseY - height*0.065f), 4f, 4f, darkPaint)

        // ── 车顶 ─────────────────────────────────────────────
        val rf_off = if (facingRight) 0.21f else 0.09f
        val rr_off = if (facingRight) 0.79f else 0.91f
        val roofFront = carX + carW * rf_off
        val roofRear  = carX + carW * rr_off
        val roofTopY  = bodyTop - roofH
        bodyPaint.color = Color.rgb((r*0.97f).toInt(), (g*0.97f).toInt(), (b*0.97f).toInt())
        val roofPath = Path().apply {
            val slantF = carW * (if (facingRight) 0.055f else -0.055f)
            val slantR = carW * (if (facingRight) -0.055f else 0.055f)
            moveTo(roofFront, bodyTop)
            lineTo(roofRear, bodyTop)
            lineTo(roofRear + slantR, roofTopY)
            lineTo(roofFront + slantF, roofTopY)
            close()
        }
        canvas.drawPath(roofPath, bodyPaint)

        // ── 车窗 ─────────────────────────────────────────────
        if (compress > 0.25f) {
            val alpha = ((compress - 0.25f) / 0.75f * 175).toInt()
            glassPaint.color = Color.argb(alpha, 110, 165, 215)
            val midX = (roofFront + roofRear) / 2f + carW * 0.025f * (if (facingRight) 1f else -1f)
            val wTop = roofTopY + roofH * 0.22f
            val slF  = carW * (if (facingRight) 0.055f else -0.055f)

            // 前窗
            val fw = Path().apply {
                moveTo(roofFront + slF + carW*0.04f, bodyTop - 3f)
                lineTo(midX - carW*0.01f, bodyTop - 3f)
                lineTo(midX - carW*0.04f + slF*0.5f, wTop)
                lineTo(roofFront + slF + carW*0.06f, wTop)
                close()
            }
            canvas.drawPath(fw, glassPaint)
            // 后窗
            val rw = Path().apply {
                val slR = carW * (if (facingRight) -0.055f else 0.055f)
                moveTo(midX + carW*0.01f, bodyTop - 3f)
                lineTo(roofRear + slR - carW*0.04f, bodyTop - 3f)
                lineTo(roofRear + slR - carW*0.09f + slR*0.5f, wTop)
                lineTo(midX + carW*0.04f, wTop)
                close()
            }
            canvas.drawPath(rw, glassPaint)
        }

        // ── 前脸/后尾 ─────────────────────────────────────────
        if (facingRight) {
            // 前大灯（右侧）
            lightPaint.color = Color.argb(235, 255, 248, 190)
            canvas.drawRoundRect(RectF(
                carX + carW - carW*0.065f, bodyTop + bodyH*0.09f,
                carX + carW,               bodyTop + bodyH*0.34f),
                4f, 4f, lightPaint)
            // 下进气
            canvas.drawRoundRect(RectF(
                carX + carW - carW*0.06f, baseY - height*0.125f,
                carX + carW,              baseY - height*0.082f),
                3f, 3f, grillPaint)
        } else {
            // 尾灯（左侧）
            canvas.drawRoundRect(RectF(
                carX, bodyTop + bodyH*0.08f,
                carX + carW*0.065f, bodyTop + bodyH*0.32f),
                4f, 4f, tailPaint)
            // 排气管
            grillPaint.color = Color.rgb(18,18,20)
            canvas.drawOval(RectF(carX+carW*0.01f, baseY-height*0.115f,
                carX+carW*0.04f, baseY-height*0.083f), grillPaint)
            canvas.drawOval(RectF(carX+carW*0.05f, baseY-height*0.115f,
                carX+carW*0.08f, baseY-height*0.083f), grillPaint)
        }

        // ── 车轮 ─────────────────────────────────────────────
        val wr = (height * 0.10f * (0.45f + 0.55f * compress)).coerceIn(10f, height * 0.105f)
        val wx1 = carX + carW * (if (facingRight) 0.79f else 0.21f)
        val wx2 = carX + carW * (if (facingRight) 0.21f else 0.79f)
        val wy  = baseY - wr

        for (wx in listOf(wx1, wx2)) {
            canvas.drawCircle(wx, wy, wr, wheelPaint)
            val hr = wr * 0.60f
            canvas.drawCircle(wx, wy, hr, hubPaint)
            for (k in 0..4) {
                val a = Math.toRadians((k * 72 + 18).toDouble())
                canvas.drawLine(wx, wy,
                    wx + (hr * 0.88f * cos(a)).toFloat(),
                    wy + (hr * 0.88f * sin(a)).toFloat(), spokePaint)
            }
            canvas.drawCircle(wx, wy, wr * 0.13f, grillPaint)
        }
    }
}
