# Add project specific ProGuard rules here.

# NewPipe extractor (YouTube downloads): rhino/nanojson/jsoup are reached by reflection or optional.
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter
-dontwarn org.mozilla.javascript.JavaToJSONConverters
-dontwarn org.mozilla.javascript.tools.**
-dontwarn javax.script.**
-dontwarn jdk.dynalink.**
-dontwarn java.beans.**
-keep class org.schabi.newpipe.extractor.timeago.patterns.** { *; }
-keep class com.grack.nanojson.** { *; }
-keepclassmembers class org.schabi.newpipe.extractor.services.youtube.protos.** { *; }
