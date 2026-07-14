@file:Suppress("SpellCheckingInspection", "UNUSED_PARAMETER")
package com.sipconnect.voip_sdk

import android.os.Handler
import android.os.Looper
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.EventChannel.EventSink
import io.flutter.view.TextureRegistry
import io.flutter.view.TextureRegistry.SurfaceProducer


////////////////////////////////////////////////////////////////////////////////////////
/// FlutterRendererAdapter

class FlutterRendererAdapter(texturesRegistry: TextureRegistry,
                             messenger: BinaryMessenger) : EventChannel.StreamHandler {
  private val surfaceTextureRenderer: SurfaceTextureRenderer
  private val rendererEvents: VideoRendererEvents
  private val producer: SurfaceProducer
  private val eventChannel: EventChannel
  private var eventSink: EventSink? = null
  var srcCallId: Int = -1

  init {
    producer = texturesRegistry.createSurfaceProducer()
    rendererEvents = RendererEventsListener(this)

    surfaceTextureRenderer = SurfaceTextureRenderer("")
    surfaceTextureRenderer.init(rendererEvents)
    surfaceTextureRenderer.surfaceCreated(producer)

    this.eventChannel = EventChannel(messenger, "SipConnect/Texture" + producer.id())
    this.eventChannel.setStreamHandler(this)
  }

  fun getRenderer(): SurfaceTextureRenderer {
    return surfaceTextureRenderer
  }

  fun getTextureId(): Long {
    return producer.id()
  }

  fun dispose() {
    surfaceTextureRenderer.surfaceDestroyed()
    surfaceTextureRenderer.release()
    eventChannel.setStreamHandler(null)

    eventSink = null
    producer.release()
  }

  override fun onListen(o: Any?, sink: EventSink?) {
    eventSink = if(sink != null) AnyThreadSink(sink) else null
  }

  override fun onCancel(o: Any?) {
    eventSink = null
  }

  class RendererEventsListener(private val adapter: FlutterRendererAdapter) : VideoRendererEvents {
    private var _rotation = -1
    private var _width = 0
    private var _height = 0

    override fun onFrameResolutionChanged(videoWidth: Int, videoHeight: Int, rotation: Int) {
      if (adapter.eventSink != null) {
        if (_width != videoWidth || _height != videoHeight) {
          val params = HashMap<String, Any?>()
          params["event"] = "didTextureChangeVideoSize"
          params["id"] = adapter.getTextureId()
          params["width"] = videoWidth.toDouble()
          params["height"] = videoHeight.toDouble()
          _width = videoWidth
          _height = videoHeight
          adapter.eventSink!!.success(params.toMap())
        }

        if (_rotation != rotation) {
          val params2 = HashMap<String, Any?>()
          params2["event"] = "didTextureChangeRotation"
          params2["id"] = adapter.getTextureId()
          params2["rotation"] = rotation
          _rotation = rotation
          adapter.eventSink!!.success(params2.toMap())
        }
      }
    }//onFrameResolutionChanged

    override fun onFirstFrameRendered() {
    }
  }//RendererEventsListener

  class AnyThreadSink(private val eventSink: EventSink) : EventSink {
    private val handler: Handler = Handler(Looper.getMainLooper())
    override fun success(o: Any) {
      post { eventSink.success(o) }
    }
    override fun error(s: String, s1: String, o: Any) {
      post { eventSink.error(s, s1, o) }
    }
    override fun endOfStream() {
      post { eventSink.endOfStream() }
    }
    private fun post(r: Runnable) {
      if (Looper.getMainLooper() == Looper.myLooper()) {
        r.run()
      } else {
        handler.post(r)
      }
    }
  }

}//FlutterVideoRenderer
