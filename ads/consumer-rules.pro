# -----------------------------------------------------------------------------
# Ads Module ProGuard & R8 Consumer Rules
# (Automatically applied to any module depending on :ads)
# -----------------------------------------------------------------------------

# Keep all Ads module classes, data models, and members
-keep class com.daumo.ads.** { *; }
-keepclassmembers class com.daumo.ads.** { *; }
-keepclasseswithmembers class com.daumo.ads.** {
    <init>(...);
}

# Google Mobile Ads SDK
-keep class com.google.android.gms.ads.** { *; }
-keep interface com.google.android.gms.ads.** { *; }
-dontwarn com.google.android.gms.ads.**

# Google Play Billing Client
-keep class com.android.billingclient.** { *; }
-keep interface com.android.billingclient.** { *; }
-dontwarn com.android.billingclient.**

# Gson Serialization & Models
-keepattributes *Annotation*,Signature
-keep class com.google.gson.** { *; }
-keep interface com.google.gson.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keepclassmembers enum * { *; }

# Firebase Remote Config
-keep class com.google.firebase.remoteconfig.** { *; }
-dontwarn com.google.firebase.remoteconfig.**
