## 1.0.3

* Replaced deprecated `package:flutter/material.dart` imports in the plugin's
  models with `package:flutter/widgets.dart` (deprecated since Flutter 3.47),
  restoring full static-analysis score on pub.dev.
* Added a GitHub Actions workflow for automated publishing to pub.dev.

## 1.0.2

* Android 15+ (16 KB page size) support. The bundled native libraries are
  now 16 KB aligned, as required by Google Play for apps targeting Android 15+.
  Without this the SIP engine could fail to load on 16 KB page devices.
* Fixed analysis warnings and some deprecated Flutter API usages.

## 1.0.1

* iOS: fixed PushKit wake-up — a VoIP push now restarts SIP transports and
  refreshes registrations (`handleIpChange`), so the INVITE reaches a
  suspended/killed app.
* Added iOS Call Setup Guide and iOS Backend Configuration Guide.
* Replaced the license file with the plugin's MIT license (bundled PJSIP
  binaries remain GPLv2/commercial — see README).
* Fixed static-analysis lints (dangling doc comments, non-constant
  identifier names).

## 1.0.0

Initial release.

* SIP registration over UDP/TCP/TLS 1.3 with automatic re-register on network
  change (PJSIP 2.15.1 engine, prebuilt for Android and iOS).
* Audio calls: place/receive, hold, blind & attended transfer, mute, DTMF,
  call recording, file/tone playback, conference mixer.
* SDES-SRTP media encryption and ICE.
* SIP MESSAGE instant messaging and presence subscriptions.
* iOS CallKit integration; Android foreground service with incoming-call
  notifications.
* `ChangeNotifier` models for accounts, calls, messages, devices, network
  state, CDRs, and logs.
* iOS builds via both CocoaPods and Swift Package Manager.
