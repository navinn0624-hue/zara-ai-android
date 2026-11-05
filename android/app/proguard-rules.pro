# Proguard rules for Nova Assistant
-keepattributes *Annotation*
-keepclassmembers class * {
    @androidx.room.* *;
}
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class com.assistant.nova.data.remote.dto.** { *; }
-keep class com.assistant.nova.domain.model.** { *; }
