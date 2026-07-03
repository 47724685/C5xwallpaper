package com.yourpackage.wallpaper

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.Window
import android.view.WindowManager
import android.widget.AbsListView
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

class WallpaperPickerActivity : Activity() {

    companion object {
        private const val REQUEST_PICK_IMAGE = 2001
        private val SD_PATHS = listOf(
            "/storage/sdcard1", "/storage/extSdCard",
            "/mnt/external_sd", "/mnt/sdcard1", "/sdcard1"
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
        setContentView(buildLayout())
        val prefs = getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE)
        if (prefs.getString(MainActivity.KEY_WALLPAPER_TYPE, "builtin") == "builtin") {
            selectedBuiltinIndex = prefs.getInt(MainActivity.KEY_BUILTIN_INDEX, 0)
        }
        adapter.notifyDataSetChanged()
    }

    private fun buildLayout(): LinearLayout {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.argb(230, 20, 20, 20))
        }
        root.addView(TextView(this).apply {
            text = "选择壁纸"; textSize = 22f
            setTextColor(Color.WHITE); gravity = Gravity.CENTER
            setPadding(0, 20, 0, 20)
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 80))

        val resIds = BuiltinWallpapers.resIds(this)
        adapter = WallpaperGridAdapter(resIds)
        gridView = GridView(this).apply {
            numColumns = 5; horizontalSpacing = 12; verticalSpacing = 12
            setPadding(20, 0, 20, 0); setAdapter(adapter)
            setBackgroundColor(Color.TRANSPARENT)
            onItemClickListener = AdapterView.OnItemClickListener { _, _, pos, _ ->
                selectedBuiltinIndex = pos; pendingCustomPath = null
                adapter.notifyDataSetChanged(); confirmBtn.isEnabled = true
            }
        }
        root.addView(gridView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        val btnBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER; setPadding(40, 16, 40, 16)
        }
        val sdBtn = Button(this).apply {
            text = "从SD卡选图"; textSize = 18f
            setOnClickListener { pickFromSD() }
        }
        val cancelBtn = Button(this).apply {
            text = "取消"; textSize = 18f; setOnClickListener { finish() }
        }
        confirmBtn = Button(this).apply {
            text = "确定"; textSize = 18f; isEnabled = false
            setOnClickListener { applyWallpaper() }
        }
        fun lp() = LinearLayout.LayoutParams(0, 80, 1f).apply { setMargins(12, 0, 12, 0) }
        btnBar.addView(sdBtn, lp()); btnBar.addView(cancelBtn, lp()); btnBar.addView(confirmBtn, lp())
        root.addView(btnBar, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        return root
    }

    private fun pickFromSD() {
        try {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "image/*"; addCategory(Intent.CATEGORY_OPENABLE)
            }
            startActivityForResult(Intent.createChooser(intent, "选择图片"), REQUEST_PICK_IMAGE)
        } catch (e: Exception) { scanSDCard() }
    }

    private fun scanSDCard() {
        val sdPath = SD_PATHS.firstOrNull { File(it).exists() && File(it).canRead() }
        if (sdPath == null) { Toast.makeText(this, "未找到SD卡", Toast.LENGTH_SHORT).show(); return }
        val file = File("$sdPath/wallpaper.jpg")
        if (file.exists()) applyCustomFile(file.absolutePath)
        else Toast.makeText(this, "请将图片命名为 wallpaper.jpg 放入SD卡根目录",
            Toast.LENGTH_LONG).show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_PICK_IMAGE && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            val path = copyUriToPrivate(uri)
            if (path != null) {
                pendingCustomPath = path; selectedBuiltinIndex = -1
                confirmBtn.isEnabled = true
                adapter.setCustomPreview(path); adapter.notifyDataSetChanged()
                Toast.makeText(this, "图片已加载，点击确定应用", Toast.LENGTH_SHORT).show()
            } else Toast.makeText(this, "读取图片失败", Toast.LENGTH_SHORT).show()
        }
    }

    private fun copyUriToPrivate(uri: Uri): String? {
        return try {
            val input = contentResolver.openInputStream(uri) ?: return null
            val outFile = File(filesDir, "custom_wallpaper.jpg")
            val output = FileOutputStream(outFile)
            input.copyTo(output); input.close(); output.close()
            outFile.absolutePath
        } catch (e: Exception) { null }
    }

    private fun applyCustomFile(path: String) {
        pendingCustomPath = path; selectedBuiltinIndex = -1
        confirmBtn.isEnabled = true
        adapter.setCustomPreview(path); adapter.notifyDataSetChanged()
    }

    private fun applyWallpaper() {
        val editor = getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE).edit()
        when {
            pendingCustomPath != null -> {
                editor.putString(MainActivity.KEY_WALLPAPER_TYPE, "custom")
                editor.putString(MainActivity.KEY_WALLPAPER_PATH, pendingCustomPath)
            }
            selectedBuiltinIndex >= 0 -> {
                editor.putString(MainActivity.KEY_WALLPAPER_TYPE, "builtin")
                editor.putInt(MainActivity.KEY_BUILTIN_INDEX, selectedBuiltinIndex)
            }
            else -> { finish(); return }
        }
        editor.apply(); setResult(RESULT_OK); finish()
    }

    inner class WallpaperGridAdapter(private val resIds: List<Int>) : BaseAdapter() {
        private var customPreviewPath: String? = null
        fun setCustomPreview(path: String) { customPreviewPath = path }
        override fun getCount() = resIds.size + (if (customPreviewPath != null) 1 else 0)
        override fun getItem(pos: Int): Any = pos
        override fun getItemId(pos: Int) = pos.toLong()

        override fun getView(pos: Int, convertView: android.view.View?,
                             parent: android.view.ViewGroup?): android.view.View {
            val imgView = (convertView as? ImageView) ?: ImageView(this@WallpaperPickerActivity).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                layoutParams = AbsListView.LayoutParams(300, 140)
            }
            val isCustom = customPreviewPath != null && pos == 0
            val actualPos = if (customPreviewPath != null) pos - 1 else pos
            if (isCustom) {
                val bmp = try {
                    BitmapFactory.decodeFile(customPreviewPath,
                        BitmapFactory.Options().apply { inSampleSize = 4 })
                } catch (e: Exception) { null }
                imgView.setImageBitmap(bmp)
                imgView.setBackgroundColor(
                    if (pendingCustomPath != null) Color.argb(180,0,180,80) else Color.TRANSPARENT)
            } else {
                val resId = resIds.getOrNull(actualPos)
                if (resId != null) {
                    val opts = BitmapFactory.Options().apply {
                        inSampleSize = 4; inPreferredConfig = Bitmap.Config.RGB_565
                    }
                    imgView.setImageBitmap(BitmapFactory.decodeResource(resources, resId, opts))
                }
                imgView.setBackgroundColor(
                    if (actualPos == selectedBuiltinIndex) Color.argb(180,0,180,80)
                    else Color.TRANSPARENT)
            }
            return imgView
        }
    }
}
