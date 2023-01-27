-keep class io.netty.**{*;}
# netty
# Get rid of warnings about unreachable but unused classes referred to by Netty
-dontwarn org.jboss.**
-dontwarn io.netty.**
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
-dontwarn javax.annotation.**
-dontwarn org.junit.**
-dontwarn java.lang.management.**
-dontwarn java.lang.invoke.**
-dontwarn org.codehaus.**
-dontwarn org.slf4j.**
# Needed by commons logging
-keep class org.apache.commons.logging.* {*;}
#Some Factory that seemed to be pruned
-keep class java.util.concurrent.atomic.AtomicReferenceFieldUpdater {*;}
#Some fields whose names need to be maintained because they are accessed using inflection
-keepclassmembernames class org.jboss.netty.util.internal.**{*;}