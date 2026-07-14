#
# To learn more about a Podspec see http://guides.cocoapods.org/syntax/podspec.html.
# Run `pod lib lint sip_connect_flutter.podspec` to validate before publishing.
#
Pod::Spec.new do |s|
  s.name             = 'sip_connect_flutter'
  s.version          = '1.0.0'
  s.summary          = 'SipConnect Flutter plugin for embedding voice and video communication.'
  s.description      = <<-DESC
SipConnect Flutter plugin for embedding voice and video communication (based on SIP/RTP protocols) into Flutter applications.
                       DESC
  s.homepage         = 'https://iocod.com'
  s.license          = { :type => 'Proprietary' }
  s.author           = { 'iocod' => 'basil@iocod.com' }
  s.source           = { :path => '.' }
  s.source_files = 'Classes/**/*'
  s.public_header_files = 'Classes/SipCoreModule.h'
  s.dependency 'Flutter'
  s.platform = :ios, '14.0'
  s.swift_version = '5.0'

  # PJSIP engine: static libs cross-compiled by native/build-ios.sh into
  # pjsip.xcframework; the pjsua2 C++ API is consumed by SipCoreModule.mm
  # (headers vendored under pjsip-headers/).
  s.vendored_frameworks = 'pjsip.xcframework'
  s.preserve_paths = 'pjsip.xcframework', 'pjsip-headers'
  s.pod_target_xcconfig = {
    'DEFINES_MODULE' => 'YES',
    # Flutter.framework does not contain a i386 slice.
    'EXCLUDED_ARCHS[sdk=iphonesimulator*]' => 'i386',
    'HEADER_SEARCH_PATHS' => '"${PODS_TARGET_SRCROOT}/pjsip-headers"',
    'GCC_PREPROCESSOR_DEFINITIONS' => '$(inherited) PJ_AUTOCONF=1',
    'CLANG_CXX_LANGUAGE_STANDARD' => 'c++17',
  }
  s.frameworks = 'AVFoundation', 'AudioToolbox', 'CoreAudio', 'CoreMedia',
                 'CoreVideo', 'VideoToolbox', 'CFNetwork', 'UIKit', 'Network',
                 'Metal', 'MetalKit'
  s.libraries = 'c++', 'resolv'
end
