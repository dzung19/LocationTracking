# =============================================================================
# ProGuard & R8 Configuration Rules
# =============================================================================

# Preserve general reflection metadata and stack trace line numbers
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,Exceptions,SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# -----------------------------------------------------------------------------
# 1. AndroidX @Keep Annotation
# Ensures any class, field, or method annotated with @Keep is fully preserved
# -----------------------------------------------------------------------------
-keep @androidx.annotation.Keep class * { *; }
-keepclassmembers class * {
    @androidx.annotation.Keep *;
}
-keepclasseswithmembers class * {
    @androidx.annotation.Keep <init>(...);
}
-keepclasseswithmembers class * {
    @androidx.annotation.Keep <fields>;
}
-keepclasseswithmembers class * {
    @androidx.annotation.Keep <methods>;
}

# -----------------------------------------------------------------------------
# 2. Room Database & SQLite
# -----------------------------------------------------------------------------
-keep class * extends androidx.room.RoomDatabase
-keep class **_Impl { *; }
-keepclassmembers class * {
    @androidx.room.Dao *;
    @androidx.room.Entity *;
    @androidx.room.Database *;
}
-dontwarn androidx.room.paging.**

# -----------------------------------------------------------------------------
# 3. Moshi (JSON Serialization & Deserialization)
# -----------------------------------------------------------------------------
-keepclassmembers class * {
    @com.squareup.moshi.Json <fields>;
}
-keep @com.squareup.moshi.JsonQualifier interface * { *; }
-keep class com.squareup.moshi.** { *; }
-keep interface com.squareup.moshi.** { *; }
-keepclassmembers class * {
    @com.squareup.moshi.JsonClass *;
}
-keep class *JsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
    public <init>(com.squareup.moshi.Moshi, java.lang.reflect.Type[]);
}
-keepclassmembers class **$GeneratedJsonAdapter {
    <init>(...);
}
-dontwarn com.squareup.moshi.**

# -----------------------------------------------------------------------------
# 4. Retrofit 2 & OkHttp 3
# -----------------------------------------------------------------------------
-keepclasseswithmembers interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn retrofit2.**

-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# -----------------------------------------------------------------------------
# 5. KotlinX Serialization
# -----------------------------------------------------------------------------
-dontnote kotlinx.serialization.SerializationKt
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}
-keepclassmembers class * {
    companion object;
}
-keepclassmembers class * {
    *** Companion;
}
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer();
}
-keep class *$$serializer { *; }
-keepclassmembers class * extends kotlinx.serialization.internal.GeneratedSerializer {
    <init>(...);
}
-dontwarn kotlinx.serialization.**

# -----------------------------------------------------------------------------
# 6. Koin Dependency Injection
# -----------------------------------------------------------------------------
-keep class org.koin.** { *; }
-keep interface org.koin.** { *; }
-dontwarn org.koin.**

# -----------------------------------------------------------------------------
# 7. Kotlin Coroutines
# -----------------------------------------------------------------------------
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.coroutines.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**

# -----------------------------------------------------------------------------
# 8. Google Play Services (Location, Maps & IPC Callbacks)
# -----------------------------------------------------------------------------
# Preserve LocationCallback and its callbacks dispatched by Google Play Services via IPC
-keep class * extends com.google.android.gms.location.LocationCallback { *; }
-keepclassmembers class * extends com.google.android.gms.location.LocationCallback {
    public void onLocationResult(com.google.android.gms.location.LocationResult);
    public void onLocationAvailability(com.google.android.gms.location.LocationAvailability);
}

# Keep Google Play Services Location, Maps, and internal IPC classes
-keep class com.google.android.gms.location.** { *; }
-keep class com.google.android.gms.internal.location.** { *; }
-keep class com.google.android.gms.common.** { *; }
-keep class com.google.android.gms.tasks.** { *; }
-keep class com.google.android.gms.maps.** { *; }
-keep interface com.google.android.gms.maps.** { *; }
-keep class com.google.maps.android.** { *; }
-dontwarn com.google.android.gms.**

# Parcelable CREATORs (required for LocationResult, LatLng, and IPC parcel unmarshalling)
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# -----------------------------------------------------------------------------
# 9. ViewModels, Services, Binders & Android Components
# -----------------------------------------------------------------------------
-keep class * extends androidx.lifecycle.ViewModel { *; }
-keep class * extends android.app.Service { *; }
-keep class * extends android.app.Activity { *; }

# Keep LocationTrackingService and all inner classes (including LocalBinder)
-keep class com.dzung.runner.locationtracker.LocationTrackingService { *; }
-keep class com.dzung.runner.locationtracker.LocationTrackingService$* { *; }
-keep class * extends android.os.Binder { *; }
