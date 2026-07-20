-keepattributes *Annotation*, Signature, InnerClasses
-keep class com.mobileagent.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keep,includedescriptorclasses class com.mobileagent.**$$serializer { *; }
-keepclassmembers class com.mobileagent.** {
    ** Companion;
    ** values(...);
    ** valueOf(...);
}