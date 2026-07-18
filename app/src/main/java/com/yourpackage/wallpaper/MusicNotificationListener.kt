package com.yourpackage.wallpaper

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * 通知监听服务：监听音乐播放通知，提取歌曲信息。
 *
 * 不需要 MEDIA_CONTENT_CONTROL 权限，只需要"通知访问"授权：
 *   放进 /system/priv-app/ 后系统自动授予，或者
 *   设置 → 通知访问 → 手动开启
 */
class MusicNotificationListener : NotificationListenerService() {

    companion object {
        var instance: MusicNotificationListener? = null
            private set

        var currentInfo: MusicMonitor.MusicInfo? = null
            private set

        private val listeners = mutableListOf<(MusicMonitor.MusicInfo?) -> Unit>()

        fun addListener(cb: (MusicMonitor.MusicInfo?) -> Unit) { listeners.add(cb) }
        fun removeListener(cb: (MusicMonitor.MusicInfo?) -> Unit) { listeners.remove(cb) }

        private fun notifyAll(info: MusicMonitor.MusicInfo?) {
            currentInfo = info
            listeners.toList().forEach { it(info) }
        }

        fun getCurrentPositionMs(context: Context): Long {
            return try {
                val mgr = context.getSystemService(Context.MEDIA_SESSION_SERVICE)
                        as? MediaSessionManager ?: return 0L
                val cn = ComponentName(context, MusicNotificationListener::class.java)
                val sessions: List<MediaController> = mgr.getActiveSessions(cn)
                sessions.firstOrNull {
                    it.playbackState?.state == PlaybackState.STATE_PLAYING
                }?.playbackState?.position ?: 0L
            } catch (e: Exception) { 0L }
        }
    }

    override fun onCreate() { super.onCreate(); instance = this }
    override fun onDestroy() { super.onDestroy(); if (instance === this) instance = null }
    override fun onListenerConnected() { super.onListenerConnected(); instance = this; refresh() }
    override fun onNotificationPosted(sbn: StatusBarNotification) { refresh() }
    override fun onNotificationRemoved(sbn: StatusBarNotification) { refresh() }

    private fun refresh() {
        try {
            val mgr = getSystemService(Context.MEDIA_SESSION_SERVICE)
                    as? MediaSessionManager ?: return
            val cn = ComponentName(this, MusicNotificationListener::class.java)
            val sessions: List<MediaController> = mgr.getActiveSessions(cn)

            val active = sessions.firstOrNull {
                it.playbackState?.state == PlaybackState.STATE_PLAYING
            } ?: sessions.firstOrNull()

            val meta  = active?.metadata
            val title = meta?.getString(MediaMetadata.METADATA_KEY_TITLE)
            if (title.isNullOrBlank()) { notifyAll(null); return }

            val artist  = meta.getString(MediaMetadata.METADATA_KEY_ARTIST)
                       ?: meta.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
                       ?: "未知歌手"
            val album   = meta.getString(MediaMetadata.METADATA_KEY_ALBUM) ?: ""
            val playing = active?.playbackState?.state == PlaybackState.STATE_PLAYING
            val pos     = active?.playbackState?.position ?: 0L
            notifyAll(MusicMonitor.MusicInfo(title, artist, album, playing, pos))
        } catch (e: Exception) { notifyAll(null) }
    }
}
