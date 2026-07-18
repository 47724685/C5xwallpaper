package com.yourpackage.wallpaper

import android.os.Environment
import java.io.File

/**
 * LRC 歌词解析器
 *
 * 支持标准 LRC 格式：[mm:ss.xx]歌词
 * 自动在以下目录搜索匹配的 .lrc 文件：
 *   /sdcard/Music/
 *   /sdcard/
 *   /storage/sdcard1/Music/
 *   /storage/sdcard1/
 */
object LrcParser {

    data class LrcLine(
        val timeMs: Long,   // 时间戳（毫秒）
        val text: String    // 歌词文本
    )

    // 搜索 LRC 文件的目录
    private val SEARCH_DIRS = listOf(
        "${Environment.getExternalStorageDirectory()}/Music",
        "${Environment.getExternalStorageDirectory()}",
        "/storage/sdcard1/Music",
        "/storage/sdcard1",
        "/sdcard/Music",
        "/sdcard"
    )

    /**
     * 根据歌曲名/歌手查找并解析 LRC 文件
     * 匹配策略：文件名包含歌曲名（不区分大小写）
     */
    fun findAndParse(title: String, artist: String): List<LrcLine> {
        val lrcFile = findLrcFile(title, artist) ?: return emptyList()
        return parse(lrcFile)
    }

    private fun findLrcFile(title: String, artist: String): File? {
        val titleClean = title.trim().lowercase()
        val artistClean = artist.trim().lowercase()

        for (dir in SEARCH_DIRS) {
            val d = File(dir)
            if (!d.exists() || !d.isDirectory) continue

            // 递归搜索（最多2层）
            val lrcFiles = d.walkTopDown().maxDepth(2)
                .filter { it.isFile && it.extension.lowercase() == "lrc" }
                .toList()

            // 优先：文件名同时包含歌曲名和歌手名
            lrcFiles.firstOrNull { f ->
                val name = f.nameWithoutExtension.lowercase()
                name.contains(titleClean) && name.contains(artistClean)
            }?.let { return it }

            // 次选：文件名包含歌曲名
            lrcFiles.firstOrNull { f ->
                f.nameWithoutExtension.lowercase().contains(titleClean)
            }?.let { return it }
        }
        return null
    }

    fun parse(file: File): List<LrcLine> {
        return try {
            val lines = mutableListOf<LrcLine>()
            file.readLines(Charsets.UTF_8).forEach { line ->
                parseTimeTags(line).forEach { timeMs ->
                    val text = line.substringAfterLast("]").trim()
                    if (text.isNotEmpty()) {
                        lines.add(LrcLine(timeMs, text))
                    }
                }
            }
            lines.sortBy { it.timeMs }
            lines
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** 提取一行里所有时间标签（一行可能有多个，如 [00:10.00][00:30.00]歌词） */
    private fun parseTimeTags(line: String): List<Long> {
        val pattern = Regex("""\[(\d{1,2}):(\d{2})[.:](\d{1,3})\]""")
        return pattern.findAll(line).map { m ->
            val min = m.groupValues[1].toLongOrNull() ?: 0L
            val sec = m.groupValues[2].toLongOrNull() ?: 0L
            val ms  = m.groupValues[3].padEnd(3, '0').take(3).toLongOrNull() ?: 0L
            min * 60_000L + sec * 1_000L + ms
        }.toList()
    }

    /** 从字符串内容解析 LRC（供在线歌词使用）*/
    fun parseFromString(content: String): List<LrcLine> {
        return try {
            val lines = mutableListOf<LrcLine>()
            content.lines().forEach { line ->
                parseTimeTags(line).forEach { timeMs ->
                    val text = line.substringAfterLast("]").trim()
                    if (text.isNotEmpty()) lines.add(LrcLine(timeMs, text))
                }
            }
            lines.sortBy { it.timeMs }
            lines
        } catch (e: Exception) { emptyList() }
    }

    /**
     * 根据当前播放位置找到对应的歌词行索引
     * 返回当前行和下一行（用于滚动过渡）
     */
    fun getCurrentLine(lines: List<LrcLine>, posMs: Long): Int {
        if (lines.isEmpty()) return -1
        var idx = lines.indexOfLast { it.timeMs <= posMs }
        return if (idx < 0) 0 else idx
    }
}
