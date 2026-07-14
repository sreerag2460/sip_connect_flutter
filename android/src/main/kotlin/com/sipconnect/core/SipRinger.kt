@file:Suppress("SpellCheckingInspection")
package com.sipconnect.core

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.util.Log

/**
 * Plays the device's default ringtone for incoming calls.
 * The engine raises ISipServiceListener.onRingerState(true/false); the
 * background service drives this ringer from that event.
 */
class SipRinger(private val context: Context) : ISipRinger {
  private var player: MediaPlayer? = null

  override fun start() {
    if (player != null) return
    try {
      val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE) ?: return
      player = MediaPlayer().apply {
        setDataSource(context, uri)
        if (Build.VERSION.SDK_INT >= 21) {
          setAudioAttributes(
            AudioAttributes.Builder()
              .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
              .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
              .build()
          )
        }
        isLooping = true
        prepare()
        start()
      }
    } catch (ex: Exception) {
      Log.e(TAG, "Can't start ringer: $ex")
      player = null
    }
  }

  override fun stop() {
    try {
      player?.stop()
      player?.release()
    } catch (ex: Exception) {
      Log.e(TAG, "Can't stop ringer: $ex")
    }
    player = null
  }

  companion object { private const val TAG = "SipRinger" }
}
