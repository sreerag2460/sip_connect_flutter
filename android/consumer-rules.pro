# Consumer ProGuard/R8 rules for sip_connect_flutter.
# Applied automatically to any app that depends on this plugin, so release
# (minified) builds keep the classes the native PJSIP engine reaches by name.
#
# Why this is required: libpjsua2.so calls back into the SWIG-generated Java
# binding by exact class/method name via JNI (e.g. the SwigDirector_* callbacks
# on org.pjsip.pjsua2.pjsua2JNI). R8 sees those methods as unused from Java and
# strips/renames them, which makes Endpoint init fail at runtime with
# "no static method ...SwigDirector_..." and the module never initializes
# ("SipConnect module has not initialized yet").

# Keep the entire pjsua2 SWIG binding — classes AND members — unobfuscated.
-keep class org.pjsip.pjsua2.** { *; }
-keepclassmembers class org.pjsip.pjsua2.** { *; }

# Director subclasses (Account/Call/Buddy/etc.) are instantiated from Java but
# their overridden callbacks are invoked from native — keep our engine classes
# and their members too.
-keep class com.sipconnect.core.** { *; }
-keepclassmembers class com.sipconnect.core.** { *; }

# JNI: keep any native method signatures intact.
-keepclasseswithmembernames class * {
    native <methods>;
}
