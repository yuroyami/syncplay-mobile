# =============================================================================
# MPV (full build only) — JNI-accessed entry point. The native MPV layer looks
# up methods like eventProperty / event reflectively via JNI GetMethodID.
# =============================================================================
-keep class is.xyz.mpv.MPVLib { *; }
-keep class is.xyz.mpv.MPVLib$* { *; }

# =============================================================================
# libVLC (full build only) — every public class under org.videolan.libvlc is a
# JNI bridge. The native code resolves field/method IDs by name, so any rename
# or removal triggers a NoSuchFieldError / UnsatisfiedLinkError at runtime that
# does NOT show up at build time. Keep the whole tree wholesale.
# =============================================================================
-keep class org.videolan.libvlc.** { *; }
-keepclassmembers class org.videolan.libvlc.** { *; }
-dontwarn org.videolan.libvlc.**

# =============================================================================
# NewPipe Extractor — pulls in Mozilla Rhino for embedded JS (YouTube cipher
# extraction). Rhino's JavaToJSONConverters references java.beans.* APIs that
# don't exist on Android; they're only used when Rhino runs on a JVM with the
# beans package present. Suppress the warnings — the code path is dead on
# Android. Keep the Rhino runtime so reflection-based JS-to-Java bridging works.
# =============================================================================
-dontwarn java.beans.**
-dontwarn org.mozilla.javascript.**
-dontwarn org.mozilla.classfile.**
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.** { *; }
-keepclassmembers class org.mozilla.javascript.** { *; }

# NewPipe Extractor itself uses jackson + reflection on its model classes.
-keep class org.schabi.newpipe.extractor.** { *; }
-dontwarn org.schabi.newpipe.extractor.**
-dontwarn org.nibor.autolink.**
-dontwarn javax.annotation.**
-dontwarn javax.naming.**
-dontwarn org.checkerframework.**
-dontwarn nl.altindag.ssl.**

# =============================================================================
# Conscrypt — used as the TLS provider on Android. Native + reflective access.
# =============================================================================
-keep class org.conscrypt.** { *; }
-keepclassmembers class org.conscrypt.** { *; }
-dontwarn org.conscrypt.**

# =============================================================================
# Netty (full build TLS path) — large transitive surface, much of it dead code
# on Android. The keep rule preserves the parts we actually use; the dontwarn
# rules suppress the rest so R8 doesn't choke on JVM-only references.
# =============================================================================
-keep class io.netty.** { *; }
-keepclassmembernames class io.netty.util.internal.** { *; }
-dontwarn io.netty.**
-dontwarn org.jboss.**
-dontwarn org.xbill.**
-dontwarn org.apache.log4j.**
-dontwarn org.apache.commons.logging.**
-dontwarn sun.**
-dontwarn com.sun.**
-dontwarn javassist.**
-dontwarn gnu.io.**
-dontwarn com.barchart.**
-dontwarn com.jcraft.**
-dontwarn com.google.protobuf.**
-dontwarn org.eclipse.**
-dontwarn org.apache.tomcat.**
-dontwarn org.bouncycastle.**
-dontwarn java.nio.**
-dontwarn java.net.**
-dontwarn javax.net.**
-dontwarn android.app.Notification
-dontwarn com.puppycrawl.**
-dontwarn org.junit.**
-dontwarn java.lang.management.**
-dontwarn java.lang.invoke.**
-dontwarn org.codehaus.**
-dontwarn org.slf4j.**
-keep class org.apache.commons.logging.* { *; }
# AtomicReferenceFieldUpdater is looked up reflectively by Netty's internal
# pool — without this, R8's tree shaker removes it and Netty crashes on first
# allocation.
-keep class java.util.concurrent.atomic.AtomicReferenceFieldUpdater { *; }

# =============================================================================
# kotlinx.serialization — the compiler plugin generates synthetic $serializer
# classes per @Serializable type. Reflection in deserialize() looks them up by
# name. Without these rules, R8 happily removes them and the app NPEs at the
# first decodeFromString call.
# =============================================================================
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep generated serializers for every @Serializable class in our app — using
# a wildcard package (app.**) is fine because the rule is name-pattern-based
# and only fires for classes that actually have a $serializer.
-if @kotlinx.serialization.Serializable class app.**
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class app.**
-keepclasseswithmembers class app.**$Companion {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class app.**
-keep class app.**$$serializer { *; }

# =============================================================================
# Ktor / OkHttp / Coil — mostly handled by upstream consumer rules. Keep
# warnings quiet for optional dependencies that aren't on the runtime path.
# =============================================================================
-dontwarn org.slf4j.impl.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn okhttp3.internal.platform.**
# Coil 3 uses reflection to load image decoders by name.
-keep class coil3.** { *; }
-dontwarn coil3.**

# =============================================================================
# DataStore / Protobuf — DataStore-Preferences uses protobuf-lite reflectively
# for schema generation. Keep the proto-generated nested classes.
# =============================================================================
-keep class androidx.datastore.preferences.protobuf.** { *; }
-keep class * extends androidx.datastore.preferences.protobuf.GeneratedMessageLite { *; }

# =============================================================================
# Compose / AndroidX — Compose's IR-emitted lambdas are fine, but a few
# reflective lookups in Navigation3 + Activity result APIs need keeping.
# =============================================================================
-keep class * extends androidx.lifecycle.ViewModel { <init>(...); }
-keep class * extends androidx.activity.ComponentActivity { <init>(...); }

# =============================================================================
# kotlin.Metadata / Companion objects — required for kotlin-reflect lookups
# performed by serialization and a couple of our own reflective utilities.
# =============================================================================
-keepclassmembers class **$Companion { *; }
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}
