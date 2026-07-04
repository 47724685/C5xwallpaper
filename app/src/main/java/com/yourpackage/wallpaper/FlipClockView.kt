package com.yourpackage.wallpaper

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.view.Gravity
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.util.*

class FlipClockView(context: Context) : LinearLayout(context) {

    private val cards = Array(3) { FlipCard(context) }
    private val curVal = intArrayOf(-1, -1, -1)

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER
        setBackgroundColor(Color.TRANSPARENT)
        cards.forEachIndexed { i, card ->
            addView(card)
            if (i < 2) addView(makeColon(context))
        }
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        val cardH = (h * 0.88f).toInt().coerceAtLeast(1)
        val cardW = (cardH * 1.04f).toInt().coerceAtLeast(1)
        val colonW = (cardW * 0.08f).toInt().coerceAtLeast(6)
        val margin = (cardW * 0.018f).toInt().coerceAtLeast(2)
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            when (child) {
                is FlipCard -> {
                    val lp = child.layoutParams as LayoutParams
                    lp.width = cardW; lp.height = cardH
                    lp.setMargins(margin, 0, margin, 0)
                    child.layoutParams = lp
                    child.onCardSizeSet(cardW, cardH)
                }
                is TextView -> {
                    val lp = child.layoutParams as LayoutParams
                    lp.width = colonW; lp.height = cardH
                    child.layoutParams = lp
                    child.textSize = cardH * 0.30f / resources.displayMetrics.scaledDensity
                }
            }
        }
    }

    fun tick() {
        val cal = Calendar.getInstance()
        val nv = intArrayOf(
            cal.get(Calendar.HOUR_OF_DAY),
            cal.get(Calendar.MINUTE),
            cal.get(Calendar.SECOND)
        )
        for (i in 0..2) {
            if (nv[i] != curVal[i]) {
                val prev = if (curVal[i] < 0) nv[i] else curVal[i]
                curVal[i] = nv[i]
                cards[i].flip(prev, nv[i])
            }
        }
    }

    private fun makeColon(ctx: Context) = TextView(ctx).apply {
        text = ":"
        setTextColor(Color.argb(170, 190, 190, 190))
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        gravity = Gravity.CENTER
    }

    // ── FlipCard ─────────────────────────────────────────────────────────────

    inner class FlipCard(ctx: Context) : FrameLayout(ctx) {

        private val C_TOP = Color.argb(195, 46, 46, 46)
        private val C_BOT = Color.argb(178, 32, 32, 32)
        private val C_DIV = Color.rgb(6, 6, 6)

        // 静态层：始终显示当前值
        private val topStatic = HalfView(ctx, isTop = true,  showShadow = false)
        private val botStatic = HalfView(ctx, isTop = false, showShadow = false)

        // 翻转片：覆盖上半，执行动画
        private val flipView  = HalfView(ctx, isTop = true,  showShadow = true)

        // 阴影层：翻走时覆盖在下半上方，模拟翻转片落下的阴影
        private val shadowView = ShadowView(ctx)

        private var isFlipping = false
        private var pendingFrom = -1
        private var pendingTo   = -1

        init {
            setBackgroundColor(Color.TRANSPARENT)
            addView(botStatic, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            addView(topStatic, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            addView(shadowView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            addView(flipView,  LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            flipView.visibility = View.GONE
            shadowView.visibility = View.GONE
        }

        fun onCardSizeSet(w: Int, h: Int) {
            topStatic.setCard(w, h, C_TOP, C_DIV)
            botStatic.setCard(w, h, C_BOT, Color.TRANSPARENT)
            flipView.setCard(w, h, C_TOP, Color.TRANSPARENT)
            shadowView.setCardSize(w, h)
            // pivot = 卡片上半底边中心（分割线处）
            flipView.pivotX = w / 2f
            flipView.pivotY = h / 2f   // HalfView 是整卡片高，上半底边 = h/2
        }

        fun flip(from: Int, to: Int) {
            if (isFlipping) { pendingFrom = from; pendingTo = to; return }
            startFlip("%02d".format(from), "%02d".format(to))
        }

        private fun startFlip(fromStr: String, toStr: String) {
            isFlipping = true

            // 静态层立刻显示新值
            topStatic.setNumber(toStr)
            botStatic.setNumber(toStr)

            // 翻转片显示旧值，从 0° 开始翻走
            flipView.setNumber(fromStr)
            flipView.rotationX = 0f
            flipView.visibility = View.VISIBLE

            // 阴影层
            shadowView.setAlpha(0f)
            shadowView.visibility = View.VISIBLE

            // 第一段：0° → -90°（翻走，加速）同时阴影逐渐加深
            val anim1 = ObjectAnimator.ofFloat(flipView, "rotationX", 0f, -90f).apply {
                duration = 220
                interpolator = AccelerateInterpolator(1.5f)
            }
            // 阴影同步加深
            val shadow1 = ValueAnimator.ofFloat(0f, 0.55f).apply {
                duration = 220
                addUpdateListener { shadowView.alpha = it.animatedValue as Float }
            }

            anim1.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    shadow1.cancel()
                    // 切换为新值，从背面翻进来
                    flipView.setNumber(toStr)
                    flipView.rotationX = 90f

                    // 第二段：90° → 0°（翻进来，减速），阴影逐渐消失
                    val anim2 = ObjectAnimator.ofFloat(flipView, "rotationX", 90f, 0f).apply {
                        duration = 220
                        interpolator = DecelerateInterpolator(1.5f)
                    }
                    val shadow2 = ValueAnimator.ofFloat(0.55f, 0f).apply {
                        duration = 220
                        addUpdateListener { shadowView.alpha = it.animatedValue as Float }
                    }
                    anim2.addListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            shadow2.cancel()
                            flipView.visibility = View.GONE
                            shadowView.visibility = View.GONE
                            isFlipping = false
                            if (pendingTo >= 0) {
                                val pf = pendingFrom; val pt = pendingTo
                                pendingFrom = -1; pendingTo = -1
                                startFlip("%02d".format(pf), "%02d".format(pt))
                            }
                        }
                    })
                    anim2.start(); shadow2.start()
                }
            })
            anim1.start(); shadow1.start()
        }
    }

    // ── ShadowView ────────────────────────────────────────────────────────────

    /**
     * 覆盖在下半上方的阴影层，翻转时模拟翻转片投下的阴影
     */
    inner class ShadowView(ctx: Context) : View(ctx) {
        private var cardW = 0
        private var cardH = 0
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        fun setCardSize(w: Int, h: Int) { cardW = w; cardH = h }

        override fun onDraw(canvas: Canvas) {
            if (cardW == 0) return
            val halfH = cardH / 2f
            // 只覆盖下半
            val shader = LinearGradient(
                0f, halfH, 0f, halfH + cardH * 0.25f,
                intArrayOf(Color.argb(180, 0,0,0), Color.TRANSPARENT),
                null, Shader.TileMode.CLAMP
            )
            paint.shader = shader
            canvas.drawRect(0f, halfH, cardW.toFloat(), cardH.toFloat(), paint)
        }
    }

    // ── HalfView ─────────────────────────────────────────────────────────────

    inner class HalfView(ctx: Context,
                         private val isTop: Boolean,
                         private val showShadow: Boolean) : View(ctx) {

        private var cardW = 0
        private var cardH = 0
        private var bgColor = Color.TRANSPARENT
        private var divColor = Color.TRANSPARENT
        private var label = "00"
        private var corner = 0f
        private var textBaseline = 0f

        private val bgPaint  = Paint(Paint.ANTI_ALIAS_FLAG)
        private val txtPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color     = Color.rgb(205, 205, 205)
            textAlign = Paint.Align.CENTER
            typeface  = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        private val divPaint = Paint()
        // 翻转片底部阴影（只在 flipView 上显示）
        private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)

        fun setCard(w: Int, h: Int, bg: Int, div: Int) {
            cardW = w; cardH = h
            bgColor = bg; divColor = div
            corner = h * 0.075f
            var sz = h * 0.60f
            txtPaint.textSize = sz
            val measured = txtPaint.measureText("00")
            if (measured > w * 0.88f) {
                sz *= (w * 0.88f / measured)
                txtPaint.textSize = sz
            }
            val fm = txtPaint.fontMetrics
            textBaseline = cardH / 2f - (fm.ascent + fm.descent) / 2f
            invalidate()
        }

        fun setNumber(s: String) {
            if (label != s) { label = s; invalidate() }
        }

        override fun onDraw(canvas: Canvas) {
            if (cardW == 0) return
            val w = cardW.toFloat()
            val h = cardH.toFloat()
            val halfH = h / 2f

            bgPaint.color = bgColor
            canvas.save()
            if (isTop) canvas.clipRect(0f, 0f, w, halfH)
            else       canvas.clipRect(0f, halfH, w, h)
            canvas.drawRoundRect(RectF(0f, 0f, w, h), corner, corner, bgPaint)
            canvas.restore()

            canvas.drawText(label, w / 2f, textBaseline, txtPaint)

            if (isTop && divColor != Color.TRANSPARENT) {
                divPaint.color = divColor
                canvas.drawRect(0f, halfH - 1.5f, w, halfH + 1.5f, divPaint)
            }

            // 翻转片底边阴影（让翻转片有厚度感）
            if (showShadow && isTop) {
                val shader = LinearGradient(
                    0f, halfH - h * 0.08f, 0f, halfH,
                    intArrayOf(Color.TRANSPARENT, Color.argb(100, 0, 0, 0)),
                    null, Shader.TileMode.CLAMP
                )
                shadowPaint.shader = shader
                canvas.drawRect(0f, halfH - h * 0.08f, w, halfH, shadowPaint)
            }
        }
    }
}
