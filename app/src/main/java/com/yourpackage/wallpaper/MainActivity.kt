package com.yourpackage.wallpaper

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.FrameLayout

class MainActivity : Activity() {

    companion object {
        const val REQUEST_PICK_WALLPAPER = 1001
        const val PREFS_NAME    = "wallpaper_prefs"
        const val KEY_WALLPAPER_PATH  = "wallpaper_path"
        const val KEY_WALLPAPER_TYPE  = "wallpaper_type"
        const val KEY_BUILTIN_INDEX   = "builtin_index"
    }

    private lateinit var homeView: HomeView
    private var appDrawerVisible = false
    private var appDrawerView: AppGridView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val root = FrameLayout(this)

        homeView = HomeView(this)
        root.addView(homeView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        homeView.reloadWallpaper()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_PICK_WALLPAPER && resultCode == RESULT_OK) {
            homeView.reloadWallpaper()
        }
    }

    /** 从 HomeView 的 Dock "APP" 按钮触发，显示全部应用抽屉 */
    fun openAppDrawer() {
        if (appDrawerVisible) return
        val drawer = AppGridView(this)
        val root = (window.decorView as FrameLayout)
        val params = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        root.addView(drawer, params)
        appDrawerView = drawer
        appDrawerVisible = true

        // 点击抽屉外部（背景）关闭
        drawer.setOnCloseListener {
            root.removeView(drawer)
            appDrawerView = null
            appDrawerVisible = false
        }
    }

    override fun onBackPressed() {
        if (appDrawerVisible) {
            val root = window.decorView as FrameLayout
            appDrawerView?.let { root.removeView(it) }
            appDrawerView = null
            appDrawerVisible = false
        }
        // 桌面不退出
    }
}
