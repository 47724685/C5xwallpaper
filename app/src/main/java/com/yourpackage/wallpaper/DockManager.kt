package com.yourpackage.wallpaper

import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.*
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.*

/**
 * Dock 应用管理弹窗
 *
 * 长按 Dock 某个槽位触发，弹出全部已安装 App 列表，
 * 点选后替换该槽位的应用，保存到 SharedPreferences。
 */
class DockManager(private val context: Context) {

    companion object {
        const val PREFS_NAME  = "dock_prefs"
        // 6个槽位的包名，key = "slot_0" .. "slot_5"
        fun slotKey(idx: Int) = "slot_$idx"
        fun slotLabel(idx: Int) = "label_$idx"
    }

    // 保存自定义配置
    fun saveSlot(idx: Int, pkg: String, label: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().apply {
            putString(slotKey(idx), pkg)
            putString(slotLabel(idx), label)
            apply()
        }
    }

    fun loadSlot(idx: Int): Pair<String, String>? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val pkg   = prefs.getString(slotKey(idx), null) ?: return null
        val label = prefs.getString(slotLabel(idx), "") ?: ""
        return Pair(pkg, label)
    }

    /**
     * 弹出应用选择弹窗
     * @param slotIdx  被长按的槽位索引
     * @param onPicked 用户选了某个App后回调
     */
    fun showPicker(slotIdx: Int, onPicked: (pkg: String, label: String, icon: Drawable) -> Unit) {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
        val allApps: List<ResolveInfo> = pm.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != context.packageName }
            .sortedBy { it.loadLabel(pm).toString() }

        // 弹窗容器
        val overlay = FrameLayout(context).apply {
            setBackgroundColor(Color.argb(200, 0, 0, 0))
        }

        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.argb(230, 20, 22, 30))
            setPadding(0, 0, 0, 20)
        }

        // 标题栏
        val titleBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(40, 20, 40, 20)
            setBackgroundColor(Color.argb(255, 30, 32, 42))
        }
        val titleTxt = TextView(context).apply {
            text = "选择应用（槽位 ${slotIdx + 1}）"
            textSize = 20f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val closeBtn = TextView(context).apply {
            text = "✕"
            textSize = 22f
            setTextColor(Color.argb(200, 200, 200, 200))
            setPadding(20, 0, 0, 0)
        }
        titleBar.addView(titleTxt)
        titleBar.addView(closeBtn)
        panel.addView(titleBar, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        // 应用网格
        val grid = GridView(context).apply {
            numColumns = 6
            horizontalSpacing = 16
            verticalSpacing = 20
            setPadding(30, 20, 30, 20)
            setBackgroundColor(Color.TRANSPARENT)
            stretchMode = GridView.STRETCH_COLUMN_WIDTH
        }

        val adapter = AppPickerAdapter(context, allApps)
        grid.adapter = adapter

        panel.addView(grid, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        // 面板居中放入overlay
        val panelParams = FrameLayout.LayoutParams(
            (context.resources.displayMetrics.widthPixels * 0.85f).toInt(),
            (context.resources.displayMetrics.heightPixels * 0.75f).toInt()
        ).apply { gravity = Gravity.CENTER }
        overlay.addView(panel, panelParams)

        // 把overlay加到 WindowManager
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
        val params = android.view.WindowManager.LayoutParams(
            android.view.WindowManager.LayoutParams.MATCH_PARENT,
            android.view.WindowManager.LayoutParams.MATCH_PARENT,
            android.view.WindowManager.LayoutParams.TYPE_PHONE,
            android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    android.view.WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            android.graphics.PixelFormat.TRANSLUCENT
        )
        wm.addView(overlay, params)

        fun dismiss() {
            try { wm.removeView(overlay) } catch (e: Exception) {}
        }

        // 点击外部关闭
        overlay.setOnTouchListener { _, ev ->
            if (ev.action == MotionEvent.ACTION_UP) {
                // 判断是否点在 panel 外部
                val loc = IntArray(2)
                panel.getLocationOnScreen(loc)
                val inPanel = ev.rawX >= loc[0] && ev.rawX <= loc[0] + panel.width &&
                              ev.rawY >= loc[1] && ev.rawY <= loc[1] + panel.height
                if (!inPanel) dismiss()
            }
            true
        }

        closeBtn.setOnClickListener { dismiss() }

        grid.setOnItemClickListener { _, _, pos, _ ->
            val info = allApps[pos]
            val pkg   = info.activityInfo.packageName
            val label = info.loadLabel(pm).toString().take(6)
            val icon  = info.loadIcon(pm)
            saveSlot(slotIdx, pkg, label)
            dismiss()
            onPicked(pkg, label, icon)
        }
    }

    // ── 应用选择适配器 ────────────────────────────────────────────────────────

    private inner class AppPickerAdapter(
        ctx: Context,
        private val apps: List<ResolveInfo>
    ) : BaseAdapter() {

        private val pm = ctx.packageManager
        private val inflater = android.view.LayoutInflater.from(ctx)

        override fun getCount() = apps.size
        override fun getItem(pos: Int) = apps[pos]
        override fun getItemId(pos: Int) = pos.toLong()

        override fun getView(pos: Int, convertView: View?, parent: android.view.ViewGroup?): View {
            val cell = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(8, 12, 8, 8)
            }

            val info = apps[pos]
            val icon = ImageView(context).apply {
                setImageDrawable(info.loadIcon(pm))
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            val label = TextView(context).apply {
                text = info.loadLabel(pm).toString().take(6)
                textSize = 11f
                setTextColor(Color.argb(210, 210, 210, 210))
                gravity = Gravity.CENTER
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }

            val iconSize = (context.resources.displayMetrics.heightPixels * 0.07f).toInt()
            cell.addView(icon, LinearLayout.LayoutParams(iconSize, iconSize))
            cell.addView(label, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = 6 })

            return cell
        }
    }
}
