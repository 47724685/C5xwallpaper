package com.yourpackage.wallpaper

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream

/**
 * 壁纸选择界面
 *
 * 布局（1920×720横屏）：
 * ┌─────────────────────────────────┐
 * │  标题栏：内置壁纸 / 从SD卡选择      │
 * │  GridView：内置壁纸缩略图          │
 * │  底部：从SD卡选图 | 取消 | 确定    │
 * └─────────────────────────────────┘
 *
 * 从SD卡选图用 Intent.ACTION_GET_CONTENT，让系统文件管理器处理，
 * 选完后复制到 App 私有目录避免路径权限问题。
 */
class WallpaperPickerActivity : Activity() {

    companion object {
        private const val REQUEST_PICK_IMAGE = 2001
        // SD卡常见挂载路径（航盛车机 Android 4.4.3）
        private val SD_PATHS = listOf(
            "/storage/sdcard1",
            "/storage/extSdCard",
            "/mnt/external_sd",
            "/mnt/sdcard1",
            "/sdcard1"
        )
    }

    private var selectedBuiltinIndex: Int = -1
    private var pendingCustomPath: String? = null

    private lateinit var gridView: GridView
    private lateinit var adapter: WallpaperGridAdapter
    private lateinit var confirmBtn: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )

        val root = buildLayout()
        setContentView(root)

        // 读取当前选中
        val prefs = getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE)
        val type = prefs.getString(MainActivity.KEY_WALLPAPER_TYPE, "builtin")
        if (type == "builtin") {
            selectedBuiltinIndex = prefs.getInt(MainActivity.KEY_BUILTIN_INDEX, 0)
        }
        adapter.notifyDataSetChanged()
    }

    private fun buildLayout(): LinearLayout {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.argb(230, 20, 20, 20))
        }

        // 标题
        val title = TextView(this).apply {
            text = "选择壁纸"
            textSize = 22f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 20, 0, 20)
        }
        root.addView(title, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 80
        ))

        // GridView 内置壁纸
        val resIds = BuiltinWallpapers.resIds(this)
        adapter = WallpaperGridAdapter(resIds)
        gridView = GridView(this).apply {
            numColumns = 5
            horizontalSpacing = 12
            verticalSpacing = 12
            setPadding(20, 0, 20, 0)
            setAdapter(adapter)
            setBackgroundColor(Color.TRANSPARENT)
            onItemClickListener = AdapterView.OnItemClickListener { _, _, pos, _ ->
                selectedBuiltinIndex = pos
                pendingCustomPath = null
                adapter.notifyDataSetChanged()
                confirmBtn.isEnabled = true
            }
        }
        root.addView(gridView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        // 底部按钮栏
        val btnBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(40, 16, 40, 16)
        }

        val sdBtn = Button(this).apply {
            text = "📁 从SD卡选图"
            textSize = 18f
            setOnClickListener { pickFromSD() }
        }
        val cancelBtn = Button(this).apply {
            text = "取消"
            textSize = 18f
            setOnClickListener { finish() }
        }
        confirmBtn = Button(this).apply {
            text = "✓ 确定"
            textSize = 18f
            isEnabled = false
            setOnClickListener { applyWallpaper() }
        }

        btnBar.addView(sdBtn, btnParams())
        btnBar.addView(cancelBtn, btnParams())
        btnBar.addView(confirmBtn, btnParams())
        root.addView(btnBar, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        return root
    }

    private fun btnParams(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(0, 80, 1f).apply {
            setMargins(12, 0, 12, 0)
        }
    }

    private fun pickFromSD() {
        // 优先用系统图片选择器
        try {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "image/*"
                addCategory(Intent.CATEGORY_OPENABLE)
            }
            startActivityForResult(Intent.createChooser(intent, "选择图片"), REQUEST_PICK_IMAGE)
        } catch (e: Exception) {
            // 如果车机没有文件管理器，fallback 到直接扫描 SD 卡
            scanSDCard()
        }
    }

    private fun scanSDCard() {
        val sdPath = SD_PATHS.firstOrNull { File(it).exists() && File(it).canRead() }
        if (sdPath == null) {
            Toast.makeText(this, "未找到SD卡", Toast.LENGTH_SHORT).show()
            return
        }
        // 简单 Toast 提示路径，实际场景可做完整文件浏览器
        Toast.makeText(this, "SD卡路径: $sdPath\n请将图片命名为 wallpaper.jpg 放入根目录", Toast.LENGTH_LONG).show()
        val file = File("$sdPath/wallpaper.jpg")
        if (file.exists()) {
            applyCustomFile(file.absolutePath)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_PICK_IMAGE && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            val path = copyUriToPrivate(uri)
            if (path != null) {
                pendingCustomPath = path
                selectedBuiltinIndex = -1
                confirmBtn.isEnabled = true
                // 预览：在 Grid 顶部显示自定义图片
                adapter.setCustomPreview(path)
                adapter.notifyDataSetChanged()
                Toast.makeText(this, "图片已加载，点击确定应用", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "读取图片失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * 将 Uri 复制到 App 私有目录（规避 Android 4.4 文件权限差异）
     */
    private fun copyUriToPrivate(uri: Uri): String? {
        return try {
            val input = contentResolver.openInputStream(uri) ?: return null
            val dir = getExternalFilesDir(null) ?: filesDir
            val outFile = File(dir, "custom_wallpaper.jpg")
            val output = FileOutputStream(outFile)
            input.copyTo(output)
            input.close()
            output.close()
            outFile.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    private fun applyCustomFile(path: String) {
        pendingCustomPath = path
        selectedBuiltinIndex = -1
        confirmBtn.isEnabled = true
        adapter.setCustomPreview(path)
        adapter.notifyDataSetChanged()
    }

    private fun applyWallpaper() {
        val prefs = getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE).edit()
        val customPath = pendingCustomPath

        if (customPath != null) {
            prefs.putString(MainActivity.KEY_WALLPAPER_TYPE, "custom")
            prefs.putString(MainActivity.KEY_WALLPAPER_PATH, customPath)
        } else if (selectedBuiltinIndex >= 0) {
            prefs.putString(MainActivity.KEY_WALLPAPER_TYPE, "builtin")
            prefs.putInt(MainActivity.KEY_BUILTIN_INDEX, selectedBuiltinIndex)
        } else {
            finish()
            return
        }
        prefs.apply()
        setResult(RESULT_OK)
        finish()
    }

    // ---- Adapter ----

    inner class WallpaperGridAdapter(
        private val resIds: List<Int>
    ) : BaseAdapter() {

        private var customPreviewPath: String? = null

        fun setCustomPreview(path: String) {
            customPreviewPath = path
        }

        override fun getCount(): Int = resIds.size + (if (customPreviewPath != null) 1 else 0)
        override fun getItem(pos: Int): Any = pos
        override fun getItemId(pos: Int): Long = pos.toLong()

        override fun getView(pos: Int, convertView: View?, parent: android.view.ViewGroup?): View {
            val imgView = (convertView as? ImageView) ?: ImageView(this@WallpaperPickerActivity).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                layoutParams = android.widget.AbsListView.LayoutParams(300, 140)
            }

            val isCustomSlot = customPreviewPath != null && pos == 0
            val actualPos = if (customPreviewPath != null) pos - 1 else pos

            if (isCustomSlot) {
                // 自定义图预览
                val bmp = try {
                    val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
                    BitmapFactory.decodeFile(customPreviewPath, opts)
                } catch (e: Exception) { null }
                imgView.setImageBitmap(bmp)
                imgView.setBackgroundColor(
                    if (pendingCustomPath != null) Color.argb(180, 0, 180, 80)
                    else Color.TRANSPARENT
                )
            } else {
                val resId = resIds.getOrNull(actualPos)
                if (resId != null) {
                    val opts = BitmapFactory.Options().apply {
                        inSampleSize = 4
                        inPreferredConfig = Bitmap.Config.RGB_565
                    }
                    val bmp = BitmapFactory.decodeResource(resources, resId, opts)
                    imgView.setImageBitmap(bmp)
                }
                imgView.setBackgroundColor(
                    if (actualPos == selectedBuiltinIndex) Color.argb(180, 0, 180, 80)
                    else Color.TRANSPARENT
                )
            }

            return imgView
        }
    }
}
