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
  s.dependency 'Flutter'
  s.platform = :ios, '14.0'

  # Flutter.framework does not contain a i386 slice.
  s.pod_target_xcconfig = { 'DEFINES_MODULE' => 'YES', 'EXCLUDED_ARCHS[sdk=iphonesimulator*]' => 'i386' }
  s.swift_version = '5.0'

  # NOTE: the vendored xcframeworks below are the prebuilt closed-source SIP/media
  # engine. Their names are baked into the signed binaries and must NOT be renamed.
  s.preserve_paths = 'siprix.xcframework', 'siprixMedia.xcframework'
  s.xcconfig = { 'OTHER_LDFLAGS' => '-framework siprix -framework siprixMedia' }
  s.vendored_frameworks = 'siprix.xcframework', 'siprixMedia.xcframework'
  s.frameworks = 'siprix', 'siprixMedia'
  s.library = 'c++'
end
