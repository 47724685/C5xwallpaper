package com.yourpackage.wallpaper

import android.content.Context

/**
 * 内置壁纸资源列表
 *
 * 把你想要的壁纸图片放入 app/src/main/res/drawable/ 目录，
 * 文件名建议用 bg_01.jpg, bg_02.jpg ... bg_10.jpg，
 * 分辨率建议 1920×720（与这台车机屏幕一致），JPG 格式，
 * 文件大小控制在 200KB 以内（车机内存有限）。
 *
 * 把对应的 R.drawable.bg_01 加到下面列表即可。
 */
object BuiltinWallpapers {

    fun resIds(context: Context): List<Int> {
        // 从资源目录中查找所有 bg_xx 图片，动态加载
        // 这样添加图片只需要放文件，不需要改代码
        val result = mutableListOf<Int>()
        for (i in 1..20) {
            val name = "bg_%02d".format(i)
            val resId = context.resources.getIdentifier(name, "drawable", context.packageName)
            if (resId != 0) {
                result.add(resId)
            }
        }
        // 如果没有放任何图片，加一个兜底占位（纯色）
        if (result.isEmpty()) {
            val fallback = context.resources.getIdentifier(
                "ic_launcher", "drawable", context.packageName
            )
            if (fallback != 0) result.add(fallback)
        }
        return result
    }
}
