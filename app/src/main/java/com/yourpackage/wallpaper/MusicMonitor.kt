package com.yourpackage.wallpaper

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper

/**
 * 监听系统 MediaSession，获取当前播放的歌曲信息。
 *
 * Android 4.4（API 19）就有 MediaSessionManager，不需要 NotificationListener。
 * 轮询方式：每500ms检查一次活跃的 MediaSession。
 *
 * 需要在 Manifest 声明：
 *   <uses-permission android:name="android.permission.MEDIA_CONTENT_CONTROL" />
 *   （priv-app才能拿到，正好我们的APK会放进 /system/priv-app/）
 */
class MusicMonitor(private val context: Context) {

    data class MusicInfo(
        val title: String,
        val artist: String,
        val album: String,
        val isPlaying: Boolean,
        val positionMs: Long    // 当前播放位置（毫秒），用于LRC对时
    )

    private var onChanged: ((MusicInfo?) -> Unit)? = null
    private var lastInfo: MusicInfo? = null
    private val handler = Handler(Looper.getMainLooper())

    private val pollRunnable = object : Runnable {
        override fun run() {
            poll()
            handler.postDelayed(this, 500)
        }
    }

    fun setOnMusicChanged(cb: (MusicInfo?) -> Unit) {
        onChanged = cb
    }

    fun start() {
        handler.post(pollRunnable)
    }

    fun stop() {
        handler.removeCallbacks(pollRunnable)
    }

    private fun poll() {
        val info = getCurrentMusic()
        if (info?.title != lastInfo?.title ||
            info?.isPlaying != lastInfo?.isPlaying) {
            lastInfo = info
            onChanged?.invoke(info)
        }
    }

    /** 获取当前播放位置（毫秒），每次调用实时获取 */
    fun getCurrentPosition(): Long {
        return try {
            val mgr = context.getSystemService(Context.MEDIA_SESSION_SERVICE)
                    as? MediaSessionManager ?: return 0L
            val cn = ComponentName(context, BootReceiver::class.java)
            val sessions = mgr.getActiveSessions(cn)
            sessions.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
                ?.playbackState?.position ?: 0L
        } catch (e: Exception) { 0L }
    }

    private fun getCurrentMusic(): MusicInfo? {
        return try {
            val mgr = context.getSystemService(Context.MEDIA_SESSION_SERVICE)
                    as? MediaSessionManager ?: return null
            val cn = ComponentName(context, BootReceiver::class.java)
            val sessions: List<MediaController> = mgr.getActiveSessions(cn)

            // 优先取正在播放的
            val active = sessions.firstOrNull {
                it.playbackState?.state == PlaybackState.STATE_PLAYING
            } ?: sessions.firstOrNull() ?: return null

            val meta = active.metadata ?: return null
            val title  = meta.getString(MediaMetadata.METADATA_KEY_TITLE)  ?: return null
            val artist = meta.getString(MediaMetadata.METADATA_KEY_ARTIST)
                      ?: meta.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
                      ?: "未知歌手"
            val album  = meta.getString(MediaMetadata.METADATA_KEY_ALBUM)  ?: ""
            val state  = active.playbackState
            val playing = state?.state == PlaybackState.STATE_PLAYING
            val pos     = state?.position ?: 0L

            MusicInfo(title, artist, album, playing, pos)
        } catch (e: Exception) { null }
    }
}
