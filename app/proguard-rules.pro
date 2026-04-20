-keep class com.keepalive.cloudphone.hook.** { *; }
-keep class com.keepalive.cloudphone.session.** { *; }
-keep class com.keepalive.cloudphone.service.** { *; }
-keepclassmembers class com.keepalive.cloudphone.hook.** {
    public static void hook*(**);
}
-dontwarn de.robv.android.xposed.**
