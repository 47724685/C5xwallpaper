package com.yourpackage.wallpaper

import android.os.Handler
import android.os.Looper
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object LrcFetcher {

    private val mainHandler = Handler(Looper.getMainLooper())
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

    private fun fetchKuwo(title: String, artist: String): List<LrcParser.LrcLine>? {
        return try {
            val q = URLEncoder.encode("$title $artist", "UTF-8")
            val searchUrl = "http://search.kuwo.cn/r.s?all=$q&ft=music&" +
                "rformat=json&encoding=utf8&ver=mbox&vipver=MUSIC_8.7.7.0_BCS37&" +
                "cluster=0&itemset=web_2013&news=0&pn=0&rn=1&uid=0&devid=0&" +
                "plat=pc&format=json"
            val searchResp = httpGet(searchUrl) ?: return null

            // 提取歌曲ID
            val ridRegex = Regex(""""MUSICRID":"MUSIC_(\d+)"""")
            val idRegex  = Regex(""""id":(\d+)""")
            val rid = (ridRegex.find(searchResp) ?: idRegex.find(searchResp))
                ?.groupValues?.get(1) ?: return null

            val lrcUrl = "http://m.kuwo.cn/newh5/singles/songinfoandlrc?" +
                "musicId=$rid&httpsStatus=1&reqId=0"
            val lrcResp = httpGet(lrcUrl) ?: return null
            parseLrcFromKuwoJson(lrcResp)
        } catch (e: Exception) { null }
    }

    private fun parseLrcFromKuwoJson(json: String): List<LrcParser.LrcLine>? {
        return try {
            val listRegex = Regex(""""lrclist"\s*:\s*\[(.*?)\]""", RegexOption.DOT_MATCHES_ALL)
            val listStr = listRegex.find(json)?.groupValues?.get(1) ?: return null

            // 每个 item: {"time":"0.64","lineLyric":"xxx"}
            val timeRegex = Regex(""""time"\s*:\s*"([^"]+)"""")
            val lyricRegex = Regex(""""lineLyric"\s*:\s*"([^"]*)"""")

            // 按 { } 分割每个条目
            val items = listStr.split("},").filter { it.contains("time") }
            val result = mutableListOf<LrcParser.LrcLine>()
            for (item in items) {
                val t = timeRegex.find(item)?.groupValues?.get(1)?.toDoubleOrNull() ?: continue
                val text = lyricRegex.find(item)?.groupValues?.get(1)?.trim() ?: continue
                if (text.isNotEmpty()) {
                    result.add(LrcParser.LrcLine((t * 1000).toLong(), text))
                }
            }
            if (result.isEmpty()) null else result.sortedBy { it.timeMs }
        } catch (e: Exception) { null }
    }

    private fun fetchWangYiYun(title: String, artist: String): List<LrcParser.LrcLine>? {
        return try {
            val q = URLEncoder.encode("$title $artist", "UTF-8")
            val searchUrl = "http://music.163.com/api/search/get?" +
                "s=$q&type=1&offset=0&total=true&limit=1"
            val searchResp = httpGet(searchUrl, mapOf(
                "Referer" to "http://music.163.com/",
                "Cookie" to "os=pc"
            )) ?: return null

            // 提取歌曲ID
            val songId = Regex(""""id":(\d+),"name":""")
                .find(searchResp)?.groupValues?.get(1) ?: return null

            val lrcUrl = "http://music.163.com/api/song/lyric?id=$songId&lv=1&kv=1&tv=-1"
            val lrcResp = httpGet(lrcUrl, mapOf(
                "Referer" to "http://music.163.com/"
            )) ?: return null

            // 提取 lyric 字段（避免三引号正则问题，用普通字符串）
            val lyricStart = lrcResp.indexOf("\"lyric\":\"")
            if (lyricStart < 0) return null
            val contentStart = lyricStart + 9
            // 找到结束引号（处理转义）
            val sb = StringBuilder()
            var i = contentStart
            while (i < lrcResp.length) {
                val c = lrcResp[i]
                if (c == '\\' && i + 1 < lrcResp.length) {
                    when (lrcResp[i + 1]) {
                        'n'  -> { sb.append('\n'); i += 2; continue }
                        'r'  -> { i += 2; continue }
                        '"'  -> { sb.append('"'); i += 2; continue }
                        '\\' -> { sb.append('\\'); i += 2; continue }
                        else -> { sb.append(c); i++; continue }
                    }
                }
                if (c == '"') break
                sb.append(c)
                i++
            }
            val lrcContent = sb.toString()
            val parsed = LrcParser.parseFromString(lrcContent)
            if (parsed.isEmpty()) null else parsed
        } catch (e: Exception) { null }
    }

    private fun httpGet(url: String,
                        headers: Map<String, String> = emptyMap()): String? {
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.setRequestProperty("User-Agent",
                "Mozilla/5.0 (Android 4.4; Mobile) AppleWebKit/537.36")
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            conn.connect()
            if (conn.responseCode != 200) return null
            BufferedReader(InputStreamReader(conn.inputStream, "UTF-8")).use { it.readText() }
        } catch (e: Exception) { null }
    }
}
