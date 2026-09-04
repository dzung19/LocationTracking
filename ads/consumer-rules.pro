# Keep Ads Module classes & models
-keep class com.daumo.ads.** { *; }
-keepclassmembers class com.daumo.ads.** { *; }

# Keep Google Play Services Ads
-keep class com.google.android.gms.ads.** { *; }
-keep interface com.google.android.gms.ads.** { *; }

# Keep Gson serialization classes
-keep class com.google.gson.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
