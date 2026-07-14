@file:Suppress("SpellCheckingInspection", "UNUSED_PARAMETER", "UNCHECKED_CAST", "DEPRECATION")
package com.sipconnect.voip_sdk

//import io.flutter.embedding.android.FlutterActivity

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.WindowManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.siprix.AccData
import com.siprix.DestData
import com.siprix.ISiprixModelListener
import com.siprix.IniData
import com.siprix.MsgData
import com.siprix.SiprixCore
import com.siprix.SiprixEglBase
import com.siprix.SubscrData
import com.siprix.VideoData
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.EventChannel.EventSink
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.PluginRegistry
import io.flutter.view.TextureRegistry
import io.flutter.view.TextureRegistry.SurfaceProducer
import org.webrtc.EglBase
import org.webrtc.EglRenderer
import org.webrtc.GlRectDrawer
import org.webrtc.RendererCommon
import org.webrtc.ThreadUtils
import java.util.concurrent.CountDownLatch


////////////////////////////////////////////////////////////////////////////////////////
/// SurfaceTextureRenderer - Displays the video stream on a Surface.

class SurfaceTextureRenderer
  (name: String?) : EglRenderer(name) {
  // Callback for reporting renderer events. Read-only after initilization so no lock required.
  private var rendererEvents: RendererCommon.RendererEvents? = null
  private val layoutLock = Any()
  private var isRenderingPaused = false
  private var isFirstFrameRendered = false
  private var rotatedFrameWidth = 0
  private var rotatedFrameHeight = 0
  private var frameRotation = 0

  private var surface: android.view.Surface? = null
  private var producer: SurfaceProducer? = null

  fun init(sharedContext: EglBase.Context?, rendererEvents: RendererCommon.RendererEvents?) {
    init(sharedContext, rendererEvents, EglBase.CONFIG_PLAIN, GlRectDrawer())
  }

  private fun init(sharedContext: EglBase.Context?,
    rendererEvents: RendererCommon.RendererEvents?, configAttributes: IntArray?,
    drawer: RendererCommon.GlDrawer?
  ) {
    ThreadUtils.checkIsOnMainThread()
    this.rendererEvents = rendererEvents
    synchronized(layoutLock) {
      isFirstFrameRendered = false
      rotatedFrameWidth = 0
      rotatedFrameHeight = 0
      frameRotation = -1
    }
    super.init(sharedContext, configAttributes, drawer)
  }

  override fun init(sharedContext: EglBase.Context?, configAttributes: IntArray?,
    drawer: RendererCommon.GlDrawer?
  ) {
    init(sharedContext, null,  /* rendererEvents */configAttributes, drawer)
  }

  override fun setFpsReduction(fps: Float) {
    synchronized(layoutLock) {
      isRenderingPaused = fps == 0f
    }
    super.setFpsReduction(fps)
  }

  override fun disableFpsReduction() {
    synchronized(layoutLock) {
      isRenderingPaused = false
    }
    super.disableFpsReduction()
  }

  override fun pauseVideo() {
    synchronized(layoutLock) {
      isRenderingPaused = true
    }
    super.pauseVideo()
  }

  // VideoSink interface.
  override fun onFrame(frame: org.webrtc.VideoFrame) {
    if(surface == null) {
      producer?.setSize(frame.getRotatedWidth(),frame.getRotatedHeight())
      surface = producer?.getSurface()
      createEglSurface(surface)
    }
    updateFrameDimensionsAndReportEvents(frame)
    super.onFrame(frame)
  }

  fun surfaceCreated(producer: SurfaceProducer) {
    ThreadUtils.checkIsOnMainThread()
    this.producer = producer
    this.producer!!.setCallback(
      object : SurfaceProducer.Callback {
        override fun onSurfaceAvailable() {}

        override fun onSurfaceCleanup() { surfaceDestroyed() }
      }
    )
  }

  fun surfaceDestroyed() {
    ThreadUtils.checkIsOnMainThread()
    val completionLatch = CountDownLatch(1)
    releaseEglSurface(completionLatch::countDown)
    ThreadUtils.awaitUninterruptibly(completionLatch)
    surface = null
  }

  // Update frame dimensions and report any changes to |rendererEvents|.
  private fun updateFrameDimensionsAndReportEvents(frame: org.webrtc.VideoFrame) {
    synchronized(layoutLock) {
      if (isRenderingPaused) return

      if (rotatedFrameWidth != frame.rotatedWidth ||
        rotatedFrameHeight != frame.rotatedHeight ||
        frameRotation != frame.rotation
      ) {
        rendererEvents?.onFrameResolutionChanged(
          frame.buffer.width, frame.buffer.height, frame.rotation
        )
        rotatedFrameWidth = frame.rotatedWidth
        rotatedFrameHeight = frame.rotatedHeight
        frameRotation = frame.rotation
      }
    }
  }
}//SurfaceTextureRenderer
