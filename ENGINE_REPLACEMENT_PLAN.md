# Replacing the Siprix engine with an open-source SIP stack

> **Status (2026-07-14):** Engine chosen: **PJSIP 2.15.1** (GPLv2 — see §2 for
> the licensing obligation). P0 is DONE on both platforms (jniLibs for 4 ABIs +
> pjsip.xcframework, Siprix binaries removed). P1–P3 code paths are implemented:
> Android `com.sipconnect.core.SipCore` (Kotlin on the pjsua2 Java binding) and
> iOS `SipCoreModule.mm` (ObjC++ on the pjsua2 C++ API). All 26 wire-protocol
> tests pass; the example app builds on Android and iOS. Still pending:
> on-device call verification (P1/P2 gates), P4 video frames→Flutter texture,
> P5 OpenSSL (TLS/DTLS-SRTP) + CallKit/PushKit re-verification, P6 BLF
> (dialog-event SUBSCRIBE needs a pjsua2 extension; presence works), P3 Opus.

Goal: remove the closed-source `sip_connect_core.aar` (Android) and
`siprix.xcframework` / `siprixMedia.xcframework` (iOS) — and with them the trial
60-second cap — while keeping the **exact same Dart API, channel protocol,
models, widgets, and example app** we already have. Only the native bridge
behind the `sip_connect_flutter` MethodChannel gets rewritten.

---

## 1. Reality check (please read)

This is a large, multi-week effort, not a one-commit change. A SIP client is a
real protocol stack: registration, SDP/offer-answer, RTP/SRTP media, ICE/STUN/
TURN, DTMF, transfer, hold, codecs, echo cancellation, plus platform glue
(CallKit/PushKit on iOS, foreground service on Android, video texture bridging).
The closed engine we're removing is ~110 MB of compiled C++ for exactly this.

What makes it *tractable* is the architecture we just built: the Dart layer, the
~60-method / 25-event wire contract, the models, and the example app all stay
frozen. We re-implement only the native side, one vertical slice at a time, with
the mock-channel tests + example app as the regression harness at every step.

Two hard constraints I can't do inside this sandbox, so they'll be scripted for
you to run on your machine:
- **Cross-compiling the native SIP library** (NDK for 4 Android ABIs; Xcode for
  iOS device+simulator). That's a long toolchain build needing network access to
  fetch sources (the engine, OpenSSL, Opus). I'll provide the build scripts.
- **On-device verification** of real calls / CallKit / push (simulators and CI
  can't place a real SIP call end-to-end).

---

## 2. Engine choice — licensing is the deciding factor

| Engine | License | Proprietary app OK? | API shape | Notes |
|---|---|---|---|---|
| **PJSIP (pjsua2)** | GPLv2 **or** paid commercial | Only if you buy commercial, or GPL your app | C++/Java/Swift-friendly `pjsua2` | Most mature, best docs, huge install base |
| **baresip / libre** | **BSD-3 (permissive)** | **Yes, no strings** | C API, modular | Lighter, less hand-holding; good codec/RTP support |
| **Linphone (liblinphone)** | GPLv3 or commercial | Only if commercial / GPL app | C/C++ with ObjC+Java wrappers | Batteries-included (CallKit helpers exist) |
| **Sofia-SIP + custom media** | LGPL-2.1 | Yes (dynamic link) | Signaling only — you add media | Most work: SIP only, no RTP/media engine |

**Recommendation for a closed-source product: baresip (BSD).** It's the only
option that gives "no runtime limit AND no obligation to open-source your app AND
includes media." PJSIP is the safe pick *if* you're comfortable buying its
commercial license (or open-sourcing the app).

The rest of this plan is written to be engine-agnostic; the per-method mapping in
§5 notes where PJSIP (`pjsua2`) and baresip differ.

---

## 3. What stays vs. what changes

**Frozen (no changes):**
- `lib/**` — entire Dart API, models, widgets, platform interface.
- The channel protocol: `sip_connect_flutter` channel, all method/event strings,
  arg keys, and the `SipConnect/Texture{id}` video event channel.
- `example/**` and all `test/**` — they become the acceptance harness.

**Replaced:**
- Android: `android/libs/sip_connect_core.aar` → cross-compiled `libengine.so`
  per ABI, plus rewritten bodies of the handler methods in
  `SipConnectFlutterPlugin.kt` / `EventListener.kt` (the class structure and
  method names stay; only the calls into `SiprixCore` change).
- iOS: the two `.xcframework`s → cross-compiled static libs, plus rewritten
  bodies in `SipConnectFlutterPlugin.swift` + helper classes.
- `SurfaceTextureRenderer` / `FlutterVideoRenderer` keep their Flutter-texture
  plumbing; only the frame *source* changes from Siprix's renderer to the new
  engine's video callback.

---

## 4. Phased delivery (each phase keeps example+tests green)

- **P0 — Build toolchain.** Scripts that fetch + cross-compile the chosen engine
  (+OpenSSL/Opus) into `android/libs/<abi>/` and `ios/*.a`. Wire them into
  `build.gradle` (jniLibs) and the podspec (vendored libs). Deliverable: a
  no-op native lib that links and loads on both platforms.
- **P1 — Module + accounts.** `Module_Initialize/UnInitialize/Version`,
  `Account_Add/Register/Unregister/Delete`, and the `OnAccountRegState` event.
  Gate: example registers against a real SIP server; reg state shows in UI.
- **P2 — Audio call core.** `Call_Invite/Accept/Bye/Reject/Hold/MuteMic`,
  `Mixer_SwitchToCall`, events `OnCallProceeding/Incoming/Connected/Terminated/
  Held`. Gate: place & receive an audio call with **no duration limit**.
- **P3 — Call features.** DTMF (send/recv), blind+attended transfer, play/record
  file, tones, stats, SIP headers, conference.
- **P4 — Video.** `Call_UpgradeToVideo`, `Video_Renderer*`, camera device mgmt,
  frame → texture bridge on both platforms.
- **P5 — Platform integration.** Android foreground service + incoming-call
  notifications; iOS CallKit + PushKit (VoIP push) rewired to the new engine.
- **P6 — Messaging + subscriptions.** `Message_Send`, `Subscription_Add/Delete`,
  `OnMessageIncoming/SentState`, `OnSubscriptionState`, `OnSipNotify`.
- **P7 — Devices + misc.** Audio routing, VU meter, network monitor.

Order matches value: after P2 you already have unlimited audio calls.

---

## 5. Native surface to re-implement (the contract, grouped)

Every string below is fixed by the Dart layer and must be answered identically.

- **Module**: `Module_Initialize` (parse InitData json → engine config),
  `Module_UnInitialize`, `Module_Version`, `Module_VersionCode`,
  `Module_HomeFolder`.
- **Account** (6): Add/Update/Register/Unregister/Delete/GenInstId →
  pjsua2 `Account` / baresip `ua`.
- **Call** (21): Invite, Reject, Accept, Hold, GetHoldState, GetSipHeader,
  GetStats, MuteMic, MuteCam, SendDtmf, PlayFile, PlayTone, StopPlayFile,
  RecordFile, StopRecordFile, TransferBlind, TransferAttended, UpgradeToVideo,
  AcceptVideoUpgrade, StopRingtone, Bye.
- **Mixer** (2): SwitchToCall, MakeConference (audio bridge / conf port).
- **Message** (1): Send (SIP MESSAGE).
- **Subscription** (2): Add/Delete (SUBSCRIBE/NOTIFY, BLF/presence).
- **Devices** (17): playout/recording/video enumerate+get+set, SetVideoParams,
  SwitchCamera; iOS CallKit/PushKit methods; Android foreground-mode methods.
- **Video** (3): RendererCreate/SetSrc/Dispose.
- **Events back to Dart** (25): OnAccountRegState, OnCall{Proceeding,Incoming,
  Connected,Terminated,Held,DtmfReceived,Transferred,Redirected,VideoUpgraded,
  VideoUpgradeRequested,Switched}, OnMessage{Incoming,SentState},
  OnSubscriptionState, OnSipNotify, OnNetworkState, OnPlayerState,
  OnDevicesChanged, OnVuMeterLevel, OnPushIncoming (iOS), OnCallKitMuted (iOS),
  OnCallsSyncState / OnCallAcceptNotif (Android), OnTrialModeNotif (drop — no
  trial anymore).

The exhaustive per-method payload shapes are already pinned in
`test/wire_protocol_contract_test.dart`.

---

## 6. Build toolchain approach (P0 detail)

- One `native/` dir with `build-android.sh` (NDK standalone toolchains, per-ABI)
  and `build-ios.sh` (device + simulator slices, `lipo`/xcframework assembly).
- Android consumes results via `sourceSets { main.jniLibs.srcDirs }` instead of
  the flatDir AAR.
- iOS consumes results via `vendored_libraries` in the podspec (or a rebuilt
  xcframework).
- Pin exact engine + OpenSSL + Opus versions; document them in
  `native/VERSIONS.md` so the binaries are reproducible.
