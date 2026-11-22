# Keep media session and WebView
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class com.theone.media.MainActivity$MediaBridge { *; }
