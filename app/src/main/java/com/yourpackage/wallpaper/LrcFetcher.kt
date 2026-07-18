package com.yourpackage.wallpaper

import android.os.Handler
import android.os.Looper
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * 在线歌词获取
 *
 * 优先级：
 * 1. 酷我音乐搜索接口（搜歌曲ID → 取歌词）
 * 2. 网易云音乐接口（备用）
 * 3. 本地 LRC 文件（兜底）
 */
object LrcFetcher {

    private val mainHandler = Handler(Looper.getMainLooper())

    // 缓存：避免重复请求
    private val cache = mutableMapOf<String, List<LrcParser.LrcLine>>()

    fun fetch(title: String, artist: String,
              onResult: (List<LrcParser.LrcLine>) -> Unit) {
        val key = "$title|$artist"
        cache[key]?.let { onResult(it); return }

        Thread {
            val lines = fetchKuwo(title, artist)
                ?: fetchWangYiYun(title, artist)
                ?: LrcParser.findAndParse(title, artist)

            cache[key] = lines
            mainHandler.post { onResult(lines) }
        }.start()
    }

    // ── 酷我接口 ──────────────────────────────────────────────────────────────

    private fun fetchKuwo(title: String, artist: String): List<LrcParser.LrcLine>? {
        return try {
            // Step1: 搜索歌曲ID
            val query = URLEncoder.encode("$title $artist", "UTF-8")
            val searchUrl = "http://search.kuwo.cn/r.s?all=$query&ft=music&" +
                    "rformat=json&encoding=utf8&ver=mbox&vipver=MUSIC_8.7.7.0_BCS37&" +
                    "cluster=0&itemset=web_2013&news=0&pn=0&rn=1&uid=0&devid=0&" +
                    "plat=pc&format=json"
            val searchResp = get(searchUrl) ?: return null

            // 提取 musicrid（酷我歌曲ID）
            val ridMatch = Regex(""""MUSICRID":"MUSIC_(\d+)"""").find(searchResp)
                ?: Regex(""""id":(\d+)""").find(searchResp)
            val rid = ridMatch?.groupValues?.get(1) ?: return null

            // Step2: 获取歌词
            val lrcUrl = "http://m.kuwo.cn/newh5/singles/songinfoandlrc?" +
                    "musicId=$rid&httpsStatus=1&reqId=0"
            val lrcResp = get(lrcUrl) ?: return null

            // 提取 lrclist 里的内容
            parseLrcFromKuwoJson(lrcResp)
        } catch (e: Exception) { null }
    }

    private fun parseLrcFromKuwoJson(json: String): List<LrcParser.LrcLine>? {
        return try {
            // 从 JSON 里提取 lrclist 数组，格式：
            // "lrclist":[{"time":"0.64","lineLyric":"..."},...]
            val pattern = Regex(""""lrclist"\s*:\s*\[(.*?)\]""", RegexOption.DOT_MATCHES_ALL)
            val listStr = pattern.find(json)?.groupValues?.get(1) ?: return null

            val lines = mutableListOf<LrcParser.LrcLine>()
            val itemPattern = Regex(""""time"\s*:\s*"([^"]+)"[^}]*?"lineLyric"\s*:\s*"([^"]*)"")
            itemPattern.findAll(listStr).forEach { m ->
                val timeSec = m.groupValues[1].toDoubleOrNull() ?: return@forEach
                val text    = m.groupValues[2].trim()
                if (text.isNotEmpty()) {
                    lines.add(LrcParser.LrcLine((timeSec * 1000).toLong(), text))
                }
            }
            if (lines.isEmpty()) null else lines.sortedBy { it.timeMs }
        } catch (e: Exception) { null }
    }

    // ── 网易云备用接口 ─────────────────────────────────────────────────────────

    private fun fetchWangYiYun(title: String, artist: String): List<LrcParser.LrcLine>? {
        return try {
            val query = URLEncoder.encode("$title $artist", "UTF-8")
            // 网易云搜索（非官方但稳定）
            val searchUrl = "http://music.163.com/api/search/get?" +
                    "s=$query&type=1&offset=0&total=true&limit=1"
            val searchResp = get(searchUrl, headers = mapOf(
                "Referer" to "http://music.163.com/",
                "Cookie" to "os=pc"
            )) ?: return null

            val idMatch = Regex(""""id":(\d+),"name":""").find(searchResp)
            val id = idMatch?.groupValues?.get(1) ?: return null

            val lrcUrl = "http://music.163.com/api/song/lyric?id=$id&lv=1&kv=1&tv=-1"
            val lrcResp = get(lrcUrl, headers = mapOf(
                "Referer" to "http://music.163.com/"
            )) ?: return null

            // 提取 lrc.lyric 字段
            val lrcMatch = Regex(""""lyric"\s*:\s*"(.*?)"""",
                setOf(RegexOption.DOT_MATCHES_ALL)).find(lrcResp)
            val lrcContent = lrcMatch?.groupValues?.get(1)
                ?.replace("\\n", "\n")
                ?.replace("\\r", "")
                ?: return null

            val lines = LrcParser.parseFromString(lrcContent)
            if (lines.isEmpty()) null else lines
        } catch (e: Exception) { null }
    }

    // ── HTTP 工具 ─────────────────────────────────────────────────────────────

    private fun get(url: String,
                    headers: Map<String, String> = emptyMap()): String? {
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 5000
            conn.readTimeout    = 5000
            conn.setRequestProperty("User-Agent",
                "Mozilla/5.0 (Android 4.4; Mobile) AppleWebKit/537.36")
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            conn.connect()
            if (conn.responseCode != 200) return null
            BufferedReader(InputStreamReader(conn.inputStream, "UTF-8")).use {
                it.readText()
            }
        } catch (e: Exception) { null }
    }
}
