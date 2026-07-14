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
//EventListener

class EventListener: ISiprixModelListener {
  private var _channel: MethodChannel? = null
  private var _callTerminatedHandler : ICallTerminated? = null
  private var _triggerIncomingCallByNotifOnly = false

  fun subscribe(core : SiprixCore, channel : MethodChannel) {
    _channel = channel
    core.setModelListener(this)
  }

  fun unsubscribe(core : SiprixCore) {
    core.setModelListener(null)
    _channel = null
  }

  fun setCallTerminatedHandler(c : ICallTerminated?) {
    _callTerminatedHandler = c
  }

  fun setTriggerIncomingCall(t: Boolean?) {
    if(t!=null) _triggerIncomingCallByNotifOnly = t
  }

  override fun onTrialModeNotified() {
    val argsMap = HashMap<String, Any> ()
    _channel?.invokeMethod(kOnTrialModeNotif, argsMap)
  }

  override fun onDevicesAudioChanged() {
    val argsMap = HashMap<String, Any> ()
    _channel?.invokeMethod(kOnDevicesChanged, argsMap)
  }

  override fun onAccountRegState(accId: Int, regState: AccData.RegState, response: String?) {
    val argsMap = HashMap<String, Any?> ()
    argsMap[kArgAccId] = accId
    argsMap[kRegState] = regState.value
    argsMap[kResponse] = response
    _channel?.invokeMethod(kOnAccountRegState, argsMap)
  }

  override fun onSubscriptionState(subscrId: Int, state: SubscrData.SubscrState, response: String?) {
    val argsMap = HashMap<String, Any?> ()
    argsMap[kArgSubscrId] = subscrId
    argsMap[kSubscrState] = state.value
    argsMap[kResponse] = response
    _channel?.invokeMethod(kOnSubscriptionState, argsMap)
  }

  override fun onNetworkState(name: String?, state: SiprixCore.NetworkState?) {
    val argsMap = HashMap<String, Any?> ()
    argsMap[kArgName] = name
    argsMap[kNetState] = state?.value
    _channel?.invokeMethod(kOnNetworkState, argsMap)
  }

  override fun onPlayerState(playerId: Int, state: SiprixCore.PlayerState?) {
    val argsMap = HashMap<String, Any?> ()
    argsMap[kArgPlayerId] = playerId
    argsMap[kPlayerState] = state?.value
    _channel?.invokeMethod(kOnPlayerState, argsMap)
  }

  override fun onCallProceeding(callId: Int, response: String?) {
    val argsMap = HashMap<String, Any?> ()
    argsMap[kArgCallId] = callId
    argsMap[kResponse] = response
    _channel?.invokeMethod(kOnCallProceeding, argsMap)
  }

  override fun onCallTerminated(callId: Int, statusCode: Int) {
    val argsMap = HashMap<String, Any?> ()
    argsMap[kArgCallId] = callId
    argsMap[kArgStatusCode] = statusCode
    _channel?.invokeMethod(kOnCallTerminated, argsMap)

    Handler(Looper.getMainLooper()).post {
      _callTerminatedHandler?.onCallTerminated()
    }
  }

  override fun onCallConnected(callId: Int, hdrFrom: String?, hdrTo: String?, withVideo:Boolean) {
    val argsMap = HashMap<String, Any?> ()
    argsMap[kArgWithVideo] = withVideo
    argsMap[kArgCallId] = callId
    argsMap[kFrom] = hdrFrom
    argsMap[kTo] = hdrTo
    _channel?.invokeMethod(kOnCallConnected, argsMap)
  }

  override fun onCallIncoming(
    callId: Int, accId: Int, withVideo: Boolean,
    hdrFrom: String?, hdrTo: String?) {
    if(!_triggerIncomingCallByNotifOnly)
      onCallIncomingNotif(callId, accId, withVideo, hdrFrom, hdrTo)
  }

  fun onCallIncomingNotif(
    callId: Int, accId: Int, withVideo: Boolean,
    hdrFrom: String?, hdrTo: String?
  ) {
    val argsMap = HashMap<String, Any?> ()
    argsMap[kArgWithVideo] = withVideo
    argsMap[kArgCallId] = callId
    argsMap[kArgAccId] = accId
    argsMap[kFrom]  = hdrFrom
    argsMap[kTo] = hdrTo
    _channel?.invokeMethod(kOnCallIncoming, argsMap)
  }

  fun onCallAcceptNotif(callId: Int, withVideo: Boolean) {
    val argsMap = HashMap<String, Any?> ()
    argsMap[kArgWithVideo] = withVideo
    argsMap[kArgCallId] = callId
    _channel?.invokeMethod(kOnCallAcceptNotif, argsMap)
  }

  fun onCallsSyncState(argsMap : HashMap<String, Any?>) {
    _channel?.invokeMethod(kOnCallsSyncState, argsMap)
  }

  override fun onCallDtmfReceived(callId: Int, tone: Int) {
    val argsMap = HashMap<String, Any?> ()
    argsMap[kArgCallId] = callId
    argsMap[kArgTone] = tone
    _channel?.invokeMethod(kOnCallDtmfReceived, argsMap)
  }

  override fun onCallTransferred(callId: Int, statusCode: Int) {
    val argsMap = HashMap<String, Any?> ()
    argsMap[kArgCallId] = callId
    argsMap[kArgStatusCode] = statusCode
    _channel?.invokeMethod(kOnCallTransferred, argsMap)
  }

  override fun onCallRedirected(origCallId: Int, relatedCallId: Int, referTo: String?) {
    val argsMap = HashMap<String, Any?> ()
    argsMap[kArgFromCallId] = origCallId
    argsMap[kArgToCallId] = relatedCallId
    argsMap[kArgToExt] = referTo
    _channel?.invokeMethod(kOnCallRedirected, argsMap)
  }

  override fun onCallVideoUpgraded(callId: Int, withVideo: Boolean) {
    val argsMap = HashMap<String, Any?> ()
    argsMap[kArgWithVideo] = withVideo
    argsMap[kArgCallId] = callId
    _channel?.invokeMethod(kOnCallVideoUpgraded, argsMap)
  }

  override fun onCallVideoUpgradeRequested(callId: Int) {
    val argsMap = HashMap<String, Any?> ()
    argsMap[kArgCallId] = callId
    _channel?.invokeMethod(kOnCallVideoUpgradeRequested, argsMap)
  }

  override fun onCallHeld(callId: Int, state: SiprixCore.HoldState?) {
    val argsMap = HashMap<String, Any?> ()
    argsMap[kArgCallId] = callId
    argsMap[kHoldState] = state?.value
    _channel?.invokeMethod(kOnCallHeld, argsMap)
  }

  override fun onCallSwitched(callId: Int) {
    val argsMap = HashMap<String, Any?> ()
    argsMap[kArgCallId] = callId
    _channel?.invokeMethod(kOnCallSwitched, argsMap)
  }

  override fun onMessageSentState(messageId: Int, success: Boolean, response: String?) {
    val argsMap = HashMap<String, Any?> ()
    argsMap[kArgMsgId] = messageId
    argsMap[kSuccess] = success
    argsMap[kResponse] = response
    _channel?.invokeMethod(kOnMessageSentState, argsMap)
}

  override fun onMessageIncoming(messageId: Int, accId: Int, hdrFrom: String?, body: String?) {
    val argsMap = HashMap<String, Any?> ()
    argsMap[kArgMsgId] = messageId
    argsMap[kArgAccId] = accId
    argsMap[kFrom] = hdrFrom
    argsMap[kBody] = body
    _channel?.invokeMethod(kOnMessageIncoming, argsMap)
  }

  override fun onSipNotify(accId: Int, hdrEvent: String?, body: String?) {
    val argsMap = HashMap<String, Any?> ()
    argsMap[kArgAccId] = accId
    argsMap[kEvent] = hdrEvent
    argsMap[kBody] = body
    _channel?.invokeMethod(kOnSipNotify, argsMap)
  }

  override fun onVuMeterLevel(micLevel: Int, spkLevel: Int) {
    val argsMap = HashMap<String, Any?> ()
    argsMap[kMicLevel] = micLevel
    argsMap[kSpkLevel] = spkLevel
    _channel?.invokeMethod(kOnVuMeterLevel, argsMap)
  }
}
