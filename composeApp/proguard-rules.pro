# NewPipe Extractor deciphers YouTube stream signatures by running YouTube's own JavaScript in
# Rhino, which wires up its interpreter classes reflectively. R8 cannot see that, so without this
# the shrinker drops half the engine and every stream resolution dies with NoClassDefFoundError -
# the app would still build, sign in and search, and then never play a note.
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter
-dontwarn org.mozilla.javascript.tools.**

# OkHttp (NewPipe's downloader and Ktor's Android engine) probes for optional TLS providers at
# runtime; none of them is on this classpath, and R8 warns about each. Silencing the warnings is
# correct here because the probes are guarded by class-presence checks.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Rhino also has an optional JSR-223 script-engine facade and an invokedynamic optimiser that lean on
# JDK-only APIs (javax.script, jdk.dynalink, java.beans). None of them exists on Android and NewPipe
# never enters those paths; R8 only needs telling the references are expected to be absent.
-dontwarn javax.script.**
-dontwarn jdk.dynalink.**
-dontwarn java.beans.**
