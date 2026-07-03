package com.yourpackage.wallpaper

import android.app.Service
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.IBinder
import android.view.*
import android.widget.TextView

/**
 * 悬浮球 Service
 *
 * 在所有 App 之上显示一个小圆球，点击返回桌面。
 * 需要 APK 放在 /system/priv-app/ 才能拿到 INJECT_EVENTS 权限。
 *
 * 手势：
 *   单击 → 返回桌面（Home）
 *   长按 → 发送 KEYCODE_BACK
 *   拖动 → 移动悬浮球位置，松手自动吸附到左右边缘
 */
class FloatBallService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var ballView: TextView
    private lateinit var params: WindowManager.LayoutParams

    private var startX = 0
    private var startY = 0
    private var touchStartRawX = 0f
    private var touchStartRawY = 0f
    private var isDragging = false
    private var longPressRunnable: Runnable? = null

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    companion object {
        const val BALL_SIZE_DP = 46
        const val LONG_PRESS_MS = 600L
        const val DRAG_THRESHOLD = 10f
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createBall()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun createBall() {
        ballView = TextView(this).apply {
            text = "⌂"
            textSize = 20f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.argb(200, 20, 20, 20))
                setStroke(dp(1), Color.argb(100, 255, 255, 255))
            }
        }

        val screenW = windowManager.defaultDisplay.width
        params = WindowManager.LayoutParams(
            dp(BALL_SIZE_DP), dp(BALL_SIZE_DP),
            WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = screenW - dp(BALL_SIZE_DP) - dp(8)  // 默认右边缘
            y = 280
        }

        ballView.setOnTouchListener { _, event -> handleTouch(event); true }
        windowManager.addView(ballView, params)
    }

    private fun handleTouch(event: MotionEvent) {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                isDragging = false
                startX = params.x
                startY = params.y
                touchStartRawX = event.rawX
                touchStartRawY = event.rawY

                // 长按检测
                val lp = Runnable { sendBack() }
                longPressRunnable = lp
                handler.postDelayed(lp, LONG_PRESS_MS)
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - touchStartRawX
                val dy = event.rawY - touchStartRawY

                if (!isDragging && (Math.abs(dx) > DRAG_THRESHOLD || Math.abs(dy) > DRAG_THRESHOLD)) {
                    isDragging = true
                    longPressRunnable?.let { handler.removeCallbacks(it) }
                }

                if (isDragging) {
                    params.x = (startX + dx).toInt()
                    params.y = (startY + dy).toInt()
                    windowManager.updateViewLayout(ballView, params)
                }
            }

            MotionEvent.ACTION_UP -> {
                longPressRunnable?.let { handler.removeCallbacks(it) }

                if (isDragging) {
                    snapToEdge()
                } else {
                    goHome()
                }
                isDragging = false
            }

            MotionEvent.ACTION_CANCEL -> {
                longPressRunnable?.let { handler.removeCallbacks(it) }
                isDragging = false
            }
        }
    }

    /** 单击：回到桌面 */
    private fun goHome() {
        try {
            // 优先用 shell 注入 Home 键（priv-app 有权限）
            Runtime.getRuntime().exec(arrayOf("input", "keyevent", "3"))
        } catch (e: Exception) {
            // 降级：发送 HOME Intent
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
        }
    }

    /** 长按：发送返回键 */
    private fun sendBack() {
        try {
            Runtime.getRuntime().exec(arrayOf("input", "keyevent", "4"))
        } catch (e: Exception) {
            // ignore
        }
    }

    /** 松手后吸附到左/右边缘 */
    private fun snapToEdge() {
        val screenW = windowManager.defaultDisplay.width
        val ballW   = dp(BALL_SIZE_DP)
        val midX    = screenW / 2

        val targetX = if (params.x + ballW / 2 < midX) dp(8)
                      else screenW - ballW - dp(8)

        // 简单动画：每16ms移动一步
        val startX = params.x
        val steps  = 12
        var step   = 0
        val anim = object : Runnable {
            override fun run() {
                step++
                val t = step.toFloat() / steps
                val ease = 1f - (1f - t) * (1f - t)  // ease-out
                params.x = (startX + (targetX - startX) * ease).toInt()
                try { windowManager.updateViewLayout(ballView, params) } catch (e: Exception) {}
                if (step < steps) handler.postDelayed(this, 16)
            }
        }
        handler.post(anim)
    }

    override fun onDestroy() {
        super.onDestroy()
        try { windowManager.removeView(ballView) } catch (e: Exception) {}
    }
}
