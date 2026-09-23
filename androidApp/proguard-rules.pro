# =============================================================================
# NewPipe Extractor pulls in Mozilla Rhino to run the YouTube cipher script.
# Rhino's JavaToJSONConverters references java.beans.* APIs, which Android does
# not have. Rhino uses them only on a JVM that has the beans package, so the
# warnings are suppressed. Keep the Rhino runtime, because its JS-to-Java
# bridge uses reflection.
# =============================================================================
-dontwarn java.beans.**
-dontwarn org.mozilla.javascript.**
-dontwarn org.mozilla.classfile.**
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.** { *; }
-keepclassmembers class org.mozilla.javascript.** { *; }

# NewPipe Extractor uses Jackson and reflection on its model classes.
-keep class org.schabi.newpipe.extractor.** { *; }
-dontwarn org.schabi.newpipe.extractor.**
-dontwarn org.nibor.autolink.**
-dontwarn javax.annotation.**
-dontwarn javax.naming.**
-dontwarn org.checkerframework.**
-dontwarn nl.altindag.ssl.**

# =============================================================================
# Conscrypt is the TLS provider on Android. It uses native and reflective access.
# =============================================================================
-keep class org.conscrypt.** { *; }
-keepclassmembers class org.conscrypt.** { *; }
-dontwarn org.conscrypt.**

# =============================================================================
# Netty is the default network engine on Android. Much of its code is unused on
# Android. The keep rule keeps the parts the app uses. The dontwarn rules stop
# R8 from failing on references to JVM-only classes.
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
# Netty's internal pool looks up AtomicReferenceFieldUpdater by reflection.
# Without this rule, R8 removes it and Netty crashes on its first allocation.
-keep class java.util.concurrent.atomic.AtomicReferenceFieldUpdater { *; }

# =============================================================================
# kotlinx.serialization: the compiler plugin generates a $serializer class for
# each @Serializable type, and deserialize() looks it up by name. Without these
# rules, R8 removes those classes and the first decodeFromString call throws a
# NullPointerException.
# =============================================================================
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep the generated serializer of every @Serializable class in the app. The
# wildcard package (app.**) is safe: the rule matches by name, so it applies
# only to classes that have a $serializer.
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
# Ktor, OkHttp and Coil: their own consumer rules cover most cases. These lines
# silence warnings for optional dependencies that the app never loads.
# =============================================================================
-dontwarn org.slf4j.impl.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn okhttp3.internal.platform.**
# Coil 3 loads image decoders by name, through reflection.
-keep class coil3.** { *; }
-dontwarn coil3.**

# =============================================================================
# DataStore and Protobuf: DataStore Preferences uses protobuf-lite through
# reflection. Keep the nested classes that protobuf generates.
# =============================================================================
-keep class androidx.datastore.preferences.protobuf.** { *; }
-keep class * extends androidx.datastore.preferences.protobuf.GeneratedMessageLite { *; }

# =============================================================================
# Compose and AndroidX: Compose's generated lambdas need no rules. A few
# reflective lookups in Navigation3 and the Activity result APIs do.
# =============================================================================
-keep class * extends androidx.lifecycle.ViewModel { <init>(...); }
-keep class * extends androidx.activity.ComponentActivity { <init>(...); }

# =============================================================================
# kotlin.Metadata and companion objects: serialization and a few of the app's
# own reflective helpers look them up through kotlin-reflect.
# =============================================================================
-keepclassmembers class **$Companion { *; }
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}
